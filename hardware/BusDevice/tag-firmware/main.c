/* RAM-only driver for the TLSR8359 / JD79661 in the Nebular Pro.
 * GPIO, clocks and panel sequence follow nebular-pro-reverse-engineered.
 * The host writes a frame and CRC before committing its request number. */
#include "common.h"
#include "analog.h"
#include "flash.h"
#include "../src/NebularProtocol.h"

#define MAILBOX ((volatile NebularMailbox *)(NEBULAR_RAM_CPU_BASE + NEBULAR_RAM_MAILBOX))
#define FRAME ((const volatile u8 *)(NEBULAR_RAM_CPU_BASE + NEBULAR_RAM_FRAME))
#define CAL_ADDRESS 0x78000u
#define CAL_LENGTH 0x3000u
#define WAVE_LENGTH 0x217u
#define CAL_ENTRY_LENGTH 0x22cu

#define EPD_CS 0x304
#define EPD_CLK 0x380
#define EPD_DATA 0x140
#define EPD_DC 0x180
#define EPD_RESET 0x310
#define EPD_BUSY 0x002
#define PORT(pin) (0x580 + (((pin) >> 8) << 3))
#define MASK(pin) ((u8)(pin))

static u8 calFile[CAL_LENGTH];
static const u8 *wave;

static inline u32 now(void) { return REG_ADDR32(0x740); }

_attribute_ram_code_ void sleep_us(u32 us) {
  u32 start = now();
  while ((u32)(now() - start) < us * 16) {}
}

static void initializeClock(void) {
  reg_irq_en = 0;
  REG_ADDR32(0x60) = 0xff000000;
  REG_ADDR16(0x64) = 0xffff;
  analog_write(0x82, 0x64);
  analog_write(0x52, 0x80);
  analog_write(0x0b, 0x38);
  analog_write(0x8c, 0x02);
  analog_write(0x02, 0xa2);
  REG_ADDR8(0xc20) = 0;
  analog_write(0xc8, 0x80);
  analog_write(0x30, analog_read(0x30) | BIT(7));
  analog_write(0xc7, 0x0e);
  analog_write(0xc7, 0x0f);
  while (!(analog_read(0xcf) & 0x80)) {}
  analog_write(0x33, analog_read(0xcb));
  analog_write(0x30, analog_read(0x30) & ~BIT(7));
  analog_write(0xc7, 0x0e);
  reg_rst_clk = FLD_CLK_SPI_EN | FLD_CLK_UART_EN | FLD_CLK_SWIRE_EN;
  reg_clk_en = FLD_CLK_ZB_EN | FLD_CLK_SYS_TIMER_EN | FLD_CLK_DMA_EN | FLD_CLK_MCIC_EN | BIT(13);
  reg_clk_sel = MASK_VAL(FLD_SCLK_DIV, 6) | MASK_VAL(FLD_SCLK_SEL, 0) | FLD_SCLK_HS_SEL;
  reg_fhs_sel = 0;
  reg_system_tick_ctrl = FLD_SYSTEM_TICK_START;
}

static inline void pinSet(u16 pin, int high) {
  if (high) REG_ADDR8(PORT(pin) + 3) |= MASK(pin);
  else REG_ADDR8(PORT(pin) + 3) &= ~MASK(pin);
}

static inline int pinGet(u16 pin) { return REG_ADDR8(PORT(pin)) & MASK(pin); }

static void pinOutput(u16 pin, int high) {
  pinSet(pin, high);
  REG_ADDR8(PORT(pin) + 6) |= MASK(pin);
  REG_ADDR8(PORT(pin) + 2) &= ~MASK(pin);
}

static void pinInput(u16 pin) {
  REG_ADDR8(PORT(pin) + 6) |= MASK(pin);
  REG_ADDR8(PORT(pin) + 2) |= MASK(pin);
  if ((pin >> 8) == 1) analog_write(0xbd, analog_read(0xbd) | MASK(pin));
  else REG_ADDR8(PORT(pin) + 1) |= MASK(pin);
}

static int waitPanel(u32 timeoutMs, int requireBusy) {
  u32 t = now();
  int sawBusy = 0;
  while ((u32)(now() - t) < timeoutMs * 16000u) {
    if (!pinGet(EPD_BUSY)) sawBusy = 1;
    else if (sawBusy || (!requireBusy && (u32)(now() - t) > 32000)) return 1;
  }
  return 0;
}

static void spiByte(u8 byte) {
  for (int bit = 0; bit < 8; ++bit) {
    pinSet(EPD_CLK, 0);
    pinSet(EPD_DATA, byte & 0x80);
    byte <<= 1;
    pinSet(EPD_CLK, 1);
  }
  pinSet(EPD_CLK, 0);
}

static void sendByte(int isData, u8 value) {
  pinSet(EPD_CS, 0);
  pinSet(EPD_DC, isData);
  spiByte(value);
  pinSet(EPD_CS, 1);
}

static u8 readByte(void) {
  u8 value = 0;
  pinInput(EPD_DATA);
  pinSet(EPD_CS, 0);
  pinSet(EPD_CLK, 0);
  pinSet(EPD_DC, 1);
  for (int bit = 0; bit < 8; ++bit) {
    value = (value << 1) | (pinGet(EPD_DATA) != 0);
    pinSet(EPD_CLK, 1);
    pinSet(EPD_CLK, 0);
  }
  pinSet(EPD_CS, 1);
  pinOutput(EPD_DATA, 0);
  return value;
}

#define CMD(value) sendByte(0, (value))
#define DATA(value) sendByte(1, (value))

static void panelReset(void) {
  pinOutput(EPD_CS, 1);
  pinOutput(EPD_CLK, 0);
  pinOutput(EPD_DATA, 0);
  pinOutput(EPD_DC, 1);
  pinOutput(EPD_RESET, 1);
  pinInput(EPD_BUSY);
  for (int i = 0; i < 2; ++i) {
    sleep_us(2000);
    pinSet(EPD_RESET, 0);
    sleep_us(20000);
    pinSet(EPD_RESET, 1);
  }
  sleep_us(2000);
}

static unsigned le16(const u8 *p) { return p[0] | ((unsigned)p[1] << 8); }

static int selectWave(int temperature) {
  for (unsigned i = 0; i < 20; ++i) {
    const u8 *entry = calFile + 0x5e + i * 20;
    unsigned length = le16(entry + 5);
    if (length == 0) break;
    int min = (int16_t)le16(entry);
    int max = (int16_t)le16(entry + 2);
    u32 addr = le16(entry + 7) | ((u32)le16(entry + 9) << 16);
    if (temperature > min && temperature <= max && length == CAL_ENTRY_LENGTH &&
        addr >= CAL_ADDRESS && addr <= CAL_ADDRESS + CAL_LENGTH - CAL_ENTRY_LENGTH) {
      wave = calFile + addr - CAL_ADDRESS;
      return 1;
    }
  }
  return 0;
}

static void calibrated(u8 cmd, const u8 *fallback, unsigned count) {
  const u8 *table = wave + WAVE_LENGTH;
  unsigned limit = le16(table) + 2;
  if (limit > CAL_ENTRY_LENGTH - WAVE_LENGTH) limit = CAL_ENTRY_LENGTH - WAVE_LENGTH;
  for (unsigned p = 2; p + 1 < limit; p += 2 + table[p + 1]) {
    unsigned n = table[p + 1];
    if (p + 2 + n > limit) break;
    if (table[p] == cmd) {
      fallback = table + p + 2;
      count = n;
      break;
    }
  }
  CMD(cmd);
  for (unsigned i = 0; i < count; ++i) DATA(fallback[i]);
}

static void configurePanel(void) {
  static const u8 psr[] = {0x07,0xa8}, d03[] = {0x10,0x54,0x44};
  static const u8 d06[] = {0x0f,0x0a,0x2f,0x25,0x22,0x2e,0x21};
  static const u8 d60[] = {0x02,0x02}, d65[] = {0,0,0,0};
  static const u8 de3[] = {0x22}, de7[] = {0x1c}, de9[] = {1};
  calibrated(0x00, psr, 2);
  CMD(0x01); DATA(0x07);
  for (unsigned i = 0; i < 5; ++i) DATA(wave[i]);
  calibrated(0x03, d03, 3);
  calibrated(0x06, d06, 7);
  CMD(0x30); DATA(wave[6]);
  CMD(0x50); DATA(0x37);
  CMD(0xf0); DATA(0x5f);
  calibrated(0x60, d60, 2);
  CMD(0x61); DATA(0x00); DATA(0xb0); DATA(0x01); DATA(0xe0);
  calibrated(0x65, d65, 4);
  CMD(0x82); DATA(wave[5] ^ 0x80);
  calibrated(0xe3, de3, 1);
  calibrated(0xe7, de7, 1);
  calibrated(0xe9, de9, 1);
  CMD(0x20);
  for (unsigned i = 0; i < WAVE_LENGTH; ++i) DATA(wave[i]);
}

static unsigned draw(void) {
  if (nebularCrc(calFile + 2, CAL_LENGTH - 2) != le16(calFile)) return NEBULAR_BAD_CALIBRATION;
  panelReset();
  MAILBOX->detail = 0x02;
  if (!waitPanel(2000, 0)) return NEBULAR_TIMEOUT;

  CMD(0x41); DATA(0);
  CMD(0x40);
  if (!waitPanel(1000, 0)) return NEBULAR_TIMEOUT;
  int temperature = (int8_t)readByte() * 100;
  temperature += (readByte() >> 6) * 25;

  CMD(0x70);
  u32 revision = 0;
  for (unsigned i = 0; i < 4; ++i) revision = (revision << 8) | readByte();
  MAILBOX->detail = revision;
  // The first bit on this half-duplex read can be low during turnaround.
  if ((revision & 0x7fffffffu) != 0x0c020100u) return NEBULAR_BAD_PANEL;
  MAILBOX->detail = temperature;
  if (!selectWave(temperature)) return NEBULAR_BAD_CALIBRATION;

  configurePanel();
  CMD(0x04);
  MAILBOX->detail = 0x04;
  int ok = waitPanel(5000, 1);
  if (ok) {
    CMD(0x10);
    for (unsigned i = 0; i < NEBULAR_FRAME_BYTES; ++i) DATA(FRAME[i]);
    CMD(0x12); DATA(0);
    MAILBOX->detail = 0x12;
    ok = waitPanel(60000, 1);
    CMD(0x02); DATA(0);
    if (!waitPanel(5000, 0) && ok) {
      MAILBOX->detail = 0x20;
      ok = 0;
    }
  }
  CMD(0x07); DATA(0xa5);
  return ok ? NEBULAR_DONE : NEBULAR_TIMEOUT;
}

_attribute_ram_code_ int main(void) {
  initializeClock();
  flash_read_page(CAL_ADDRESS, CAL_LENGTH, calFile);
  MAILBOX->magic = NEBULAR_MAILBOX_MAGIC;
  MAILBOX->status = NEBULAR_IDLE;
  while (1) {
    u32 request = MAILBOX->request;
    if (!request || request == MAILBOX->finished) continue;
    MAILBOX->status = NEBULAR_DRAWING;
    MAILBOX->detail = 0;
    const uint16_t actual = nebularCrc(FRAME, NEBULAR_FRAME_BYTES);
    const uint32_t expected = MAILBOX->frameCrc;
    unsigned status = NEBULAR_BAD_FRAME;
    if (actual == expected) status = draw();
    else MAILBOX->detail = (expected << 16) | actual;
    MAILBOX->status = status;
    MAILBOX->finished = request;
  }
}
