#pragma once

#include <stdint.h>

#define NEBULAR_RAM_PROGRAM 0x040000u
#define NEBULAR_RAM_MAILBOX 0x046000u
#define NEBULAR_RAM_FRAME   0x048000u
#define NEBULAR_RAM_CPU_BASE 0x800000u
#define NEBULAR_MAILBOX_MAGIC 0x41554231u

#define NEBULAR_WIDTH 176
#define NEBULAR_HEIGHT 480
#define NEBULAR_FRAME_BYTES (NEBULAR_WIDTH * NEBULAR_HEIGHT / 4)

enum {
  NEBULAR_IDLE = 0,
  NEBULAR_DRAWING = 1,
  NEBULAR_DONE = 2,
  NEBULAR_BAD_FRAME = 3,
  NEBULAR_BAD_CALIBRATION = 4,
  NEBULAR_BAD_PANEL = 5,
  NEBULAR_TIMEOUT = 6
};

typedef struct {
  uint32_t magic;
  uint32_t request;
  uint32_t frameCrc;
  uint32_t finished;
  uint32_t status;
  uint32_t detail;
} NebularMailbox;

static inline uint16_t nebularCrc(const volatile uint8_t *data, unsigned size) {
  uint16_t crc = 0x1d0f;
  while (size--) {
    crc ^= (uint16_t)*data++ << 8;
    for (unsigned bit = 0; bit < 8; ++bit) {
      crc = (crc & 0x8000) ? (crc << 1) ^ 0x1021 : crc << 1;
    }
  }
  return crc;
}
