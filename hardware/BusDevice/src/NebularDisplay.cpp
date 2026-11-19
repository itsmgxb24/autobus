#include "NebularDisplay.h"

#include <Arduino.h>
#include <algorithm>

#include "Swire.h"
#include "generated/NebularRamImage.h"

namespace {

static_assert(sizeof(NebularMailbox) == 24, "Unexpected mailbox layout");

constexpr size_t chunkBytes = 64;

void writeByte(uint32_t address, uint8_t value) { swsWrite(address, &value, 1); }

// Reads on longer wires may flip a single bit. Match two consecutive reads.
bool readStable(uint32_t address, void *out, size_t length, int attempts = 5) {
  uint8_t previous[chunkBytes], current[chunkBytes];
  bool havePrevious = false;
  for (int i = 0; i < attempts; ++i) {
    if (swsRead(address, current, length) != static_cast<int>(length)) {
      havePrevious = false;
      continue;
    }
    if (havePrevious && memcmp(previous, current, length) == 0) {
      memcpy(out, current, length);
      return true;
    }
    memcpy(previous, current, length);
    havePrevious = true;
  }
  return false;
}

bool writeVerified(uint32_t address, const uint8_t *bytes, size_t length) {
  uint8_t received[chunkBytes];
  for (int attempt = 0; attempt < 4; ++attempt) {
    swsWrite(address, bytes, length);
    for (int read = 0; read < 3; ++read) {
      if (swsRead(address, received, length) == static_cast<int>(length) &&
          memcmp(received, bytes, length) == 0) return true;
    }
    delay(1);
  }
  return false;
}

bool mailbox(NebularMailbox &out) {
  return readStable(NEBULAR_RAM_MAILBOX, &out, sizeof(out)) &&
         out.magic == NEBULAR_MAILBOX_MAGIC;
}

}  // namespace

bool NebularDisplay::startTag() {
  bool detected = false;
  // Leave timing margin for jumper wires and test the other rates if needed.
  for (uint8_t microseconds : {2, 4, 1}) {
    swsInit(sws_, microseconds);
    swsActivate(nrst_, 600);
    writeByte(0xb2, 64);
    uint8_t id[4];
    swsRead(0x7c, id, sizeof(id));  // discard the first read after changing the divider
    if (readStable(0x7c, id, sizeof(id)) && id[2] == 0x62 && id[3] == 0x55) {
      detected = true;
      break;
    }
  }
  if (!detected) {
    Serial.println("Nebular: brak odpowiedzi SWS od TLSR8359.");
    return false;
  }
  Serial.println("Nebular: TLSR8359 odpowiada przez SWS (0x5562).");

  for (size_t off = 0; off < sizeof(nebularRamImage); off += chunkBytes) {
    const auto count = std::min(chunkBytes, sizeof(nebularRamImage) - off);
    if (!writeVerified(NEBULAR_RAM_PROGRAM + off, nebularRamImage + off, count)) {
      Serial.printf("Nebular: błąd weryfikacji programu RAM przy 0x%04x.\n", unsigned(off));
      return false;
    }
    delay(1);
  }
  const NebularMailbox empty{};
  swsWrite(NEBULAR_RAM_MAILBOX, reinterpret_cast<const uint8_t *>(&empty), sizeof(empty));
  writeByte(0x602, 0x88);
  const auto start = millis();
  NebularMailbox reply;
  do {
    delay(80);
    if (mailbox(reply)) {
      Serial.println("Nebular: program w RAM gotowy do odbioru.");
      return true;
    }
  } while (millis() - start < 4000);
  Serial.println("Nebular: program w RAM nie potwierdził uruchomienia.");
  return false;
}

bool NebularDisplay::send(const uint8_t frame[NEBULAR_FRAME_BYTES]) {
  if (pending_) return false;
  NebularMailbox reply;
  if (!ready_ || !mailbox(reply)) ready_ = startTag();
  if (!ready_) return false;

  for (size_t off = 0; off < NEBULAR_FRAME_BYTES; off += chunkBytes) {
    const size_t count = std::min(chunkBytes, static_cast<size_t>(NEBULAR_FRAME_BYTES) - off);
    if (!writeVerified(NEBULAR_RAM_FRAME + off, frame + off, count)) {
      Serial.printf("Nebular: błąd odczytu kontrolnego obrazu przy 0x%04x.\n", unsigned(off));
      ready_ = false;
      return false;
    }
    if (off % (8 * chunkBytes) == 0) delay(1);
  }
  const uint32_t crc = nebularCrc(frame, NEBULAR_FRAME_BYTES);
  if (!writeVerified(NEBULAR_RAM_MAILBOX + offsetof(NebularMailbox, frameCrc),
                     reinterpret_cast<const uint8_t *>(&crc), sizeof(crc))) {
    Serial.println("Nebular: zapis CRC nie przeszedł odczytu kontrolnego.");
    ready_ = false;
    return false;
  }
  if (++sequence_ == 0) ++sequence_;
  writeByte(NEBULAR_RAM_MAILBOX + offsetof(NebularMailbox, request), sequence_);
  pending_ = true;
  sentAt_ = lastRead_ = millis();
  Serial.printf("Nebular: wysłano klatkę %lu, czekam na panel.\n", static_cast<unsigned long>(sequence_));
  return true;
}

NebularDisplay::Result NebularDisplay::poll() {
  if (!pending_) return Result::Idle;
  const auto now = millis();
  if (now - lastRead_ < 700) return Result::Busy;
  lastRead_ = now;
  NebularMailbox reply;
  if (mailbox(reply) && reply.finished == sequence_) {
    pending_ = false;
    if (reply.status == NEBULAR_DONE) {
      Serial.printf("Nebular: odświeżono klatkę %lu.\n", static_cast<unsigned long>(sequence_));
      return Result::Done;
    }
    Serial.printf("Nebular: klatka odrzucona (status %lu, szczegóły 0x%08lx).\n",
                  static_cast<unsigned long>(reply.status), static_cast<unsigned long>(reply.detail));
    return Result::Failed;
  }
  if (now - sentAt_ < 78000) return Result::Busy;
  Serial.println("Nebular: upłynął czas oczekiwania na potwierdzenie panelu.");
  pending_ = ready_ = false;
  return Result::Failed;
}
