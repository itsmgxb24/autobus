#include "BusDeviceRadio.h"

#include <Arduino.h>
#include <NimBLEDevice.h>
#include <Preferences.h>
#include <algorithm>
#include <atomic>
#include <cstring>
#include <mutex>

namespace {
constexpr char serviceId[] = "add0b001-24a0-4d80-9f70-ab0000000001";
constexpr char requestId[] = "add0b001-24a0-4d80-9f70-ab0000000002";
constexpr char responseId[] = "add0b001-24a0-4d80-9f70-ab0000000003";
constexpr uint8_t opRead = 1, opStage = 2, opCommit = 3;
constexpr size_t header = 4, chunk = 16;
constexpr unsigned long pairWindow = 120000;
constexpr uint8_t bootPin = 0;

std::mutex settingsLock;
BusDeviceConfig current;
bool changed = false;
Preferences storage;
bool storageAvailable = false;
std::atomic<bool> pairing = false;
std::atomic<unsigned long> pairOpenedAt = 0;
bool buttonPressed = false;
uint16_t accepted = BLE_HS_CONN_HANDLE_NONE;
NimBLECharacteristic *response = nullptr;

uint8_t staged[busDeviceMaxConfig];
uint8_t stageTransaction = 0;
size_t stagedSize = 0;
uint8_t snapshot[busDeviceMaxConfig];
uint8_t readTransaction = 0;
size_t snapshotSize = 0;

bool pairingOpen() { return pairing.load() && millis() - pairOpenedAt.load() < pairWindow; }

void openPairing() {
  pairOpenedAt = millis();
  pairing = true;
  Serial.println("BusDevice: parowanie dostępne przez 2 minuty.");
}

class ServerEvents : public NimBLEServerCallbacks {
  void onConnect(NimBLEServer *server, NimBLEConnInfo &info) override {
    if (accepted != BLE_HS_CONN_HANDLE_NONE ||
        (!pairingOpen() && !NimBLEDevice::isBonded(info.getIdAddress()))) {
      server->disconnect(info.getConnHandle());
      return;
    }
    accepted = info.getConnHandle();
  }

  void onAuthenticationComplete(NimBLEConnInfo &info) override {
    if (info.getConnHandle() != accepted) return;
    if (!info.isEncrypted() || !info.isBonded()) {
      NimBLEDevice::getServer()->disconnect(info.getConnHandle());
      return;
    }
    pairing = false;
  }

  void onDisconnect(NimBLEServer *, NimBLEConnInfo &info, int) override {
    if (info.getConnHandle() != accepted) return;
    accepted = BLE_HS_CONN_HANDLE_NONE;
    stagedSize = snapshotSize = 0;
    stageTransaction = readTransaction = 0;
  }
};

class Requests : public NimBLECharacteristicCallbacks {
  void onWrite(NimBLECharacteristic *characteristic, NimBLEConnInfo &info) override {
    if (info.getConnHandle() != accepted || !info.isBonded() || !info.isEncrypted()) return;
    const auto value = characteristic->getValue();
    const auto *bytes = value.data();
    const size_t length = value.size();
    if (length < header || length > header + chunk || bytes[1] == 0) {
      const uint8_t invalid[header] = {1, 0, 0, 0};
      response->setValue(invalid, sizeof(invalid));
      return;
    }
    const uint8_t tx = bytes[1];
    const size_t offset = bytes[2] | (uint16_t(bytes[3]) << 8);
    const size_t dataSize = length - header;
    uint8_t result[header + chunk] = {1, tx, bytes[2], bytes[3]};
    size_t returned = 0;

    if (bytes[0] == opRead && !dataSize) {
      if (offset == 0) {
        std::lock_guard<std::mutex> lock(settingsLock);
        snapshotSize = encodeBusConfig(current, snapshot);
        readTransaction = tx;
      }
      if (tx == readTransaction && offset <= snapshotSize) {
        returned = std::min(chunk, snapshotSize - offset);
        std::memcpy(result + header, snapshot + offset, returned);
        result[0] = 0;
      }
    } else if (bytes[0] == opStage && dataSize) {
      if (!offset) {
        stageTransaction = tx;
        stagedSize = 0;
      }
      if (tx == stageTransaction && offset == stagedSize && dataSize <= sizeof(staged) - stagedSize) {
        std::memcpy(staged + stagedSize, bytes + header, dataSize);
        stagedSize += dataSize;
        result[0] = 0;
      }
    } else if (bytes[0] == opCommit && !dataSize && tx == stageTransaction && offset == stagedSize) {
      BusDeviceConfig parsed;
      if (!decodeBusConfig(staged, stagedSize, parsed)) result[0] = 2;
      else {
        std::lock_guard<std::mutex> lock(settingsLock);
        uint8_t old[busDeviceMaxConfig];
        const auto oldSize = encodeBusConfig(current, old);
        if (oldSize == stagedSize && std::memcmp(old, staged, oldSize) == 0) result[0] = 0;
        else if (!storageAvailable || storage.putBytes("config", staged, stagedSize) != stagedSize) result[0] = 3;
        else {
          current = std::move(parsed);
          changed = true;
          result[0] = 0;
        }
      }
      stagedSize = 0;
      stageTransaction = 0;
    }
    response->setValue(result, header + returned);
  }
};

ServerEvents events;
Requests requests;
}  // namespace

void startBusDeviceRadio(BusDeviceConfig &config) {
  pinMode(bootPin, INPUT_PULLUP);
  storageAvailable = storage.begin("busdevice", false);
  if (storageAvailable) {
    uint8_t encoded[busDeviceMaxConfig];
    const auto size = storage.getBytesLength("config");
    if (size <= sizeof(encoded) && size && storage.getBytes("config", encoded, size) == size) {
      decodeBusConfig(encoded, size, config);
    }
  }
  current = config;
  char name[20];
  snprintf(name, sizeof(name), "BusDevice %04llX", static_cast<unsigned long long>((ESP.getEfuseMac() >> 32) & 0xffff));
  NimBLEDevice::init(name);
  NimBLEDevice::setSecurityIOCap(BLE_HS_IO_NO_INPUT_OUTPUT);
  NimBLEDevice::setSecurityAuth(true, false, true);
  NimBLEDevice::setSecurityInitKey(BLE_SM_PAIR_KEY_DIST_ENC | BLE_SM_PAIR_KEY_DIST_ID);
  NimBLEDevice::setSecurityRespKey(BLE_SM_PAIR_KEY_DIST_ENC | BLE_SM_PAIR_KEY_DIST_ID);

  auto *server = NimBLEDevice::createServer();
  server->setCallbacks(&events);
  server->advertiseOnDisconnect(true);
  auto *service = server->createService(serviceId);
  auto *request = service->createCharacteristic(requestId, NIMBLE_PROPERTY::WRITE | NIMBLE_PROPERTY::WRITE_ENC, header + chunk);
  response = service->createCharacteristic(responseId, NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::READ_ENC, header + chunk);
  request->setCallbacks(&requests);
  server->start();
  auto *advertising = NimBLEDevice::getAdvertising();
  advertising->addServiceUUID(serviceId);
  advertising->enableScanResponse(true);
  advertising->setName(name);
  if (!NimBLEDevice::getNumBonds()) openPairing();
  advertising->start();
  Serial.printf("BusDevice: Bluetooth LE — %s.\n", name);
}

bool pollBusDeviceRadio(BusDeviceConfig &config) {
  const bool pressed = digitalRead(bootPin) == LOW;
  if (pressed && !buttonPressed) openPairing();
  buttonPressed = pressed;
  std::lock_guard<std::mutex> lock(settingsLock);
  if (!changed) return false;
  config = current;
  changed = false;
  return true;
}
