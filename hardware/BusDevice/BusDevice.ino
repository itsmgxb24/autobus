#include <Arduino.h>
#include <HTTPClient.h>
#include <NetworkClientSecure.h>
#include <WiFi.h>
#include <time.h>
#include <utility>

#include "src/BusDeviceRadio.h"
#include "src/MyBusData.h"
#include "src/NebularDisplay.h"
#include "src/NebularPage.h"

// Ustawienia użytkownika. ESP32-S3-Zero łączy się z siecią Wi-Fi 2,4 GHz.
#if __has_include("WifiLocal.h")
#include "WifiLocal.h"
#else
constexpr char WIFI_SSID[] = "";
constexpr char WIFI_PASSWORD[] = "";
#endif

// Połączenie SWS z Nebularem Pro.
constexpr uint8_t PIN_NRST = 6;
constexpr uint8_t PIN_SWS = 5;

constexpr int INITIAL_TOKEN = 60;
constexpr unsigned long WIFI_RETRY_MS = 15000;
constexpr unsigned long MYBUS_RETRY_MS = 15000;
constexpr size_t MAX_XML_BYTES = 16 * 1024;

// HTTPClient dekoduje odpowiedź, również gdy serwer wysyła ją w kawałkach.
class XmlReply : public Stream {
 public:
  int available() override { return 0; }
  int read() override { return -1; }
  int peek() override { return -1; }
  size_t write(uint8_t byte) override { return write(&byte, 1); }

  size_t write(const uint8_t *bytes, size_t count) override {
    if (count > MAX_XML_BYTES - size) {
      overflow = true;
      return 0;
    }
    memcpy(data + size, bytes, count);
    size += count;
    return count;
  }

  char data[MAX_XML_BYTES];
  size_t size = 0;
  bool overflow = false;
};

XmlReply reply;
BusDeviceConfig settings;
BusDeviceConfig incoming;
const BusCity *city = nullptr;
int sessionToken = 0;
bool wifiConfigured = false;
bool wasOnline = false;
unsigned long lastWifiTry = 0;
unsigned long lastPoll = 0;
unsigned long pollDelay = 0;
NebularDisplay display(PIN_NRST, PIN_SWS);
uint8_t frame[NEBULAR_FRAME_BYTES];
uint32_t wantedPage = 0, sentPage = 0, visiblePage = 0;
bool havePage = false, haveVisiblePage = false, retryDisplay = false;
bool displayNewSettings = false;
unsigned long displayErrorAt = 0;
unsigned long sentPageAt = 0, visiblePageAt = 0;

unsigned long updateInterval() { return settings.refreshMinutes * 60000UL; }

int getXml(const char *path, int token) {
  reply.size = 0;
  reply.overflow = false;
  WiFiClient client;
  NetworkClientSecure tls;
  HTTPClient http;
  const String url = String(city->baseUrl) + path;
  const bool https = url.startsWith("https://");
  if (https) {
    if (time(nullptr) < 1700000000) {
      Serial.println("MyBus: czekam na synchronizację czasu do HTTPS.");
      return 0;
    }
    tls.useBuiltinCACertBundle();
    tls.setHandshakeTimeout(7);
  }
  NetworkClient &connection = https ? static_cast<NetworkClient &>(tls) : client;
  if (!http.begin(connection, url)) {
    Serial.println("MyBus: nie udało się przygotować połączenia.");
    return 0;
  }
  http.setConnectTimeout(5000);
  http.setTimeout(7000);
  http.setUserAgent("myBusOnline");
  http.addHeader("Age", String(static_cast<unsigned>(token) + city->offset));
  http.addHeader("Accept-Encoding", "identity");

  int status = http.GET();
  if (status == HTTP_CODE_OK) {
    const int expected = http.getSize();
    const int received = expected > static_cast<int>(MAX_XML_BYTES) ? -1 : http.writeToStream(&reply);
    if (reply.overflow || expected > static_cast<int>(MAX_XML_BYTES)) {
      Serial.println("MyBus: odpowiedź przekracza 16 KiB.");
      status = 0;
    } else if (received < 0 || (expected >= 0 && received != expected)) {
      Serial.println("MyBus: nie udało się odebrać całej odpowiedzi.");
      status = 0;
    }
  } else if (status < 0) {
    Serial.printf("MyBus: %s\n", HTTPClient::errorToString(status).c_str());
  } else {
    Serial.printf("MyBus: HTTP %d\n", status);
  }
  http.end();
  return status;
}

bool openSession() {
  if (getXml("PingService", INITIAL_TOKEN) != HTTP_CODE_OK) return false;
  if (parseSessionToken(reply.data, reply.size, sessionToken)) return true;
  Serial.println("MyBus: nieprawidłowy token w odpowiedzi PingService.");
  return false;
}

bool fetchDepartures(DepartureBoard &board) {
  if (sessionToken == 0 && !openSession()) return false;
  char path[72];
  snprintf(path, sizeof(path), "GetTimeTableReal?nBusStopId=%u&nBusStopGroupId=0", unsigned(settings.stopId));
  int status = getXml(path, sessionToken);

  if (status == 400 || status == 401 || status == 403) {
    sessionToken = 0;
    Serial.println("MyBus: odświeżam sesję.");
    if (!openSession()) return false;
    status = getXml(path, sessionToken);
    if (status == 400 || status == 401 || status == 403) sessionToken = 0;
  }
  if (status != HTTP_CODE_OK) return false;
  if (parseDepartures(reply.data, reply.size, settings.stopId, board)) return true;
  Serial.println("MyBus: nieprawidłowa odpowiedź z odjazdami.");
  return false;
}

void printDepartures(const DepartureBoard &board) {
  Serial.printf("\nMyBus %s | przystanek %u | odjazdy: %u\n",
                board.serverTime.c_str(), unsigned(settings.stopId), static_cast<unsigned>(board.rows.size()));
  if (!board.notice.empty()) Serial.printf("Komunikat: %s\n", board.notice.c_str());
  if (board.rows.empty()) {
    Serial.println("Brak bieżących odjazdów.");
    return;
  }
  Serial.println("Linia | Kierunek | Wskazanie MyBus | Rozkład");
  for (const auto &departure : board.rows) {
    char scheduled[6] = "--:--";
    const int seconds = departure.scheduledSeconds;
    if (seconds >= 0 && seconds < 24 * 60 * 60) {
      snprintf(scheduled, sizeof(scheduled), "%02d:%02d", seconds / 3600, seconds / 60 % 60);
    }
    Serial.printf("%s | %s | %s | %s\n", departure.line.c_str(), departure.direction.c_str(),
                  departure.displayValue.c_str(), scheduled);
  }
}

void updateNebular() {
  const auto result = display.poll();
  if (result == NebularDisplay::Result::Busy) return;
  if (result == NebularDisplay::Result::Done) {
    visiblePage = sentPage;
    visiblePageAt = sentPageAt;
    haveVisiblePage = true;
    retryDisplay = false;
  } else if (result == NebularDisplay::Result::Failed) {
    retryDisplay = true;
    displayErrorAt = millis();
  }

  const auto now = millis();
  if (!havePage) return;
  if (!displayNewSettings && haveVisiblePage &&
      (wantedPage == visiblePage || now - visiblePageAt < updateInterval())) return;
  if (retryDisplay && now - displayErrorAt < 15000) return;
  if (display.send(frame)) {
    sentPage = wantedPage;
    sentPageAt = now;
    displayNewSettings = false;
  } else {
    retryDisplay = true;
    displayErrorAt = millis();
  }
}

void applyRadioSettings() {
  if (!pollBusDeviceRadio(incoming)) return;
  if (incoming.cityId != settings.cityId) sessionToken = 0;
  const bool newPage = incoming.cityId != settings.cityId || incoming.stopId != settings.stopId ||
                       incoming.stopName != settings.stopName || incoming.theme != settings.theme;
  settings = std::move(incoming);
  city = getBusCity(settings.cityId);
  if (newPage) {
    havePage = false;
    displayNewSettings = true;
    pollDelay = 0;
  } else if (pollDelay != MYBUS_RETRY_MS && pollDelay != 0) {
    pollDelay = updateInterval();
  }
  Serial.printf("BusDevice: zapisano miasto %u, przystanek %u, motyw %u, cykl %u min.\n",
                unsigned(settings.cityId), unsigned(settings.stopId), unsigned(settings.theme), unsigned(settings.refreshMinutes));
}

void setup() {
  pinMode(PIN_NRST, INPUT);
  pinMode(PIN_SWS, INPUT);
  Serial.begin(115200);
  startBusDeviceRadio(settings);
  city = getBusCity(settings.cityId);
  wifiConfigured = WIFI_SSID[0] != '\0';
  if (!wifiConfigured) {
    Serial.println("Uzupełnij WIFI_SSID i WIFI_PASSWORD w szkicu albo w WifiLocal.h.");
    return;
  }

  WiFi.mode(WIFI_STA);
  WiFi.setAutoReconnect(false);  // Ponawiamy połączenie co WIFI_RETRY_MS w loop().
  Serial.printf("MyBus: cykl aktualizacji co %u min.\n", unsigned(settings.refreshMinutes));
  Serial.println("Wi-Fi: łączenie…");
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  lastWifiTry = millis();
}

void loop() {
  applyRadioSettings();
  if (!wifiConfigured) {
    delay(50);
    return;
  }

  const unsigned long now = millis();
  if (WiFi.status() != WL_CONNECTED) {
    if (wasOnline) {
      Serial.println("Wi-Fi: utracono połączenie.");
      wasOnline = false;
    }
    if (now - lastWifiTry >= WIFI_RETRY_MS) {
      Serial.println("Wi-Fi: ponawiam połączenie…");
      WiFi.reconnect();
      lastWifiTry = now;
    }
  } else {
    if (!wasOnline) {
      Serial.print("Wi-Fi: połączono, adres IP ");
      Serial.println(WiFi.localIP());
      wasOnline = true;
      sessionToken = 0;
      pollDelay = 0;
      configTime(0, 0, "pool.ntp.org", "time.google.com");
    }
    if (now - lastPoll >= pollDelay) {
      DepartureBoard board;
      if (fetchDepartures(board)) {
        printDepartures(board);
        wantedPage = renderNebularPage(board, *city, settings, frame);
        havePage = true;
        pollDelay = updateInterval();
      } else {
        pollDelay = MYBUS_RETRY_MS;
      }
      lastPoll = millis();
    }
  }
  updateNebular();
  delay(100);
}
