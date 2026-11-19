// Bit-banged Telink Swire (SWS) master.
//
// Wire format (from pvvx/TlsrComProg825x and hacst.net "Telink Swire on the wire"):
//   bit 0 = 1 unit low + 4 units high, bit 1 = 4 units low + 1 unit high
//   byte  = cmd bit, data bits MSB first, then a 1-unit-low end slot
//   write: [cmd 0x5a] a2 a1 a0 0x00 data... [cmd 0xff]
//   read:  [cmd 0x5a] a2 a1 a0 0x80, then per byte the master pulls low 1 unit
//          and releases; the slave answers 8 bits + end slot; then [cmd 0xff]
// The slave decodes master bits by low/high ratio, so slow units are fine
// (pvvx uses ~4.3 us). Its own reply speed comes from register 0xb2.
#pragma once
#include <Arduino.h>
#include <soc/gpio_reg.h>

static uint32_t swsMask;
static uint32_t swsUnit;
static uint8_t swsAddrBytes = 3;    // TLSR825x: 3, TLSR826x: 2
static portMUX_TYPE swsMux = portMUX_INITIALIZER_UNLOCKED;

static inline void swsLow() {
  REG_WRITE(GPIO_OUT_W1TC_REG, swsMask);
  REG_WRITE(GPIO_ENABLE_W1TS_REG, swsMask);
}
static inline void swsHigh() {
  REG_WRITE(GPIO_OUT_W1TS_REG, swsMask);
  REG_WRITE(GPIO_ENABLE_W1TS_REG, swsMask);
}
static inline void swsRelease() { REG_WRITE(GPIO_ENABLE_W1TC_REG, swsMask); }
static inline bool swsLevel() { return REG_READ(GPIO_IN_REG) & swsMask; }
static inline void swsWaitUntil(uint32_t t) {
  while ((int32_t)(ESP.getCycleCount() - t) < 0) {}
}

static void swsInit(uint8_t pin, uint8_t microseconds) {
  swsMask = 1u << pin;
  swsUnit = getCpuFrequencyMhz() * microseconds;
  pinMode(pin, INPUT_PULLUP);  // idle high; also puts the pad in GPIO mode
}

static void swsBit(bool b, uint32_t &t) {
  swsLow();
  t += (b ? 4 : 1) * swsUnit;
  swsWaitUntil(t);
  swsHigh();
  t += (b ? 1 : 4) * swsUnit;
  swsWaitUntil(t);
}

static void swsByte(bool cmd, uint8_t d, uint32_t &t) {
  swsBit(cmd, t);
  for (int i = 7; i >= 0; i--) swsBit((d >> i) & 1, t);
  swsBit(0, t);  // end slot
}

static void swsHeader(uint32_t addr, bool rd, uint32_t &t) {
  swsByte(1, 0x5a, t);
  for (int i = swsAddrBytes - 1; i >= 0; i--) swsByte(0, addr >> (8 * i), t);
  swsByte(0, rd ? 0x80 : 0x00, t);
}

static void swsWrite(uint32_t addr, const uint8_t *data, size_t n) {
  portENTER_CRITICAL(&swsMux);
  uint32_t t = ESP.getCycleCount();
  swsHeader(addr, false, t);
  for (size_t i = 0; i < n; i++) swsByte(0, data[i], t);
  swsByte(1, 0xff, t);
  swsRelease();
  portEXIT_CRITICAL(&swsMux);
}

// Raw capture of one slave reply: low/high durations in CPU cycles.
struct SwsCap {
  uint8_t n;          // number of low pulses seen
  uint32_t lo[16], hi[16];
  bool timeout;
};

// Trigger one reply byte and capture it. Returns decoded byte or -1.
static int swsReadByteRaw(SwsCap &c, uint32_t maxCyc) {
  c.n = 0;
  c.timeout = false;
  uint32_t t = ESP.getCycleCount();
  swsLow();
  t += swsUnit;
  swsWaitUntil(t);
  swsRelease();  // pull-up takes it high; slave answers
  // Count from the release itself: at fast slave speeds the first reply bit can
  // start before the pull-up ever brings the line high. A pull-up rise glitch
  // may add one extra short low at the start, so decode the last 9 lows
  // (8 data bits + end slot).
  uint32_t start = ESP.getCycleCount();
  uint32_t lastEdge = start;
  bool lvl = swsLevel();
  uint32_t maxBit = 0;
  for (;;) {
    bool now = swsLevel();
    uint32_t tc = ESP.getCycleCount();
    if (now != lvl) {
      uint32_t w = tc - lastEdge;
      if (lvl) {  // a high period ended
        if (c.n && c.n <= 16) {
          c.hi[c.n - 1] = w;
          maxBit = max(maxBit, c.lo[c.n - 1] + w);
        }
      } else {  // a low period ended
        if (c.n < 16) c.lo[c.n] = w, c.hi[c.n] = 0;
        c.n++;
      }
      lvl = now;
      lastEdge = tc;
    }
    if (c.n >= 9 && lvl && maxBit && tc - lastEdge > 3 * maxBit) break;
    if (tc - start > maxCyc) { c.timeout = true; break; }
  }
  if (c.timeout || c.n < 9 || c.n > 16) return -1;
  int first = c.n - 9;
  int v = 0;
  for (int i = first; i < first + 8; i++) {
    // a '1' is long-low/short-high; compare against the bit's own high time
    v = (v << 1) | (c.lo[i] > c.hi[i] ? 1 : 0);
  }
  return v;
}

// Read n bytes; returns count read. cap (optional) receives the first byte's capture.
static int swsRead(uint32_t addr, uint8_t *out, size_t n, SwsCap *cap0 = nullptr) {
  SwsCap c;
  int got = 0;
  portENTER_CRITICAL(&swsMux);
  uint32_t t = ESP.getCycleCount();
  swsHeader(addr, true, t);
  for (size_t i = 0; i < n; i++) {
    t = ESP.getCycleCount();
    int v = swsReadByteRaw(c, 240 * 2000);  // 2 ms max per byte
    if (i == 0 && cap0) *cap0 = c;
    if (v < 0) break;
    out[got++] = v;
  }
  t = ESP.getCycleCount() + swsUnit;  // small gap
  swsWaitUntil(t);
  swsByte(1, 0xff, t);
  swsRelease();
  portEXIT_CRITICAL(&swsMux);
  return got;
}

// Halt the CPU right out of reset: hold NRST low, then spam [0x602]=0x05
// across the release so the write lands before firmware disables SWS / sleeps.
static void swsActivate(int pinRst, uint32_t ms) {
  const uint8_t stop = 0x05;
  pinMode(pinRst, OUTPUT_OPEN_DRAIN);
  digitalWrite(pinRst, LOW);
  delay(20);
  for (int i = 0; i < 5; i++) swsWrite(0x0602, &stop, 1);
  pinMode(pinRst, INPUT);  // release reset
  uint32_t t0 = millis();
  while (millis() - t0 < ms) swsWrite(0x0602, &stop, 1);
}
