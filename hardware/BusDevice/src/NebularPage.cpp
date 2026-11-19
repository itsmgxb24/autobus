#include "NebularPage.h"

#include <algorithm>
#include <charconv>
#include <cstring>
#include <string_view>

namespace {

struct Glyph {
  uint16_t offset;
  uint8_t width, height, advance;
  int8_t x, y;
};

struct Font {
  const Glyph *glyphs;
  const uint8_t *pixels;
};

#include "generated/InterBitmaps.h"

constexpr uint8_t red = 3;
constexpr uint8_t white = 1;
constexpr uint8_t black = 0;
constexpr uint8_t yellow = 2;
constexpr uint8_t defaultInk = 0xff;

struct Palette { uint8_t ink, paper, badgeFill, badgeInk; };

Palette paletteFor(BusTheme theme) {
  switch (theme) {
    case BusTheme::Mono: return {black, white, black, white};
    case BusTheme::YellowAccent: return {red, white, yellow, red};
    case BusTheme::RedInverted: return {white, red, white, red};
    default: return {red, white, red, white};
  }
}

uint16_t nextGlyph(std::string_view &text) {
  const auto first = static_cast<uint8_t>(text.front());
  text.remove_prefix(1);
  if (first >= 32 && first <= 126) return first - 32;
  int bytes = first >= 0xc2 && first <= 0xdf ? 1 : first >= 0xe0 && first <= 0xef ? 2 : 0;
  unsigned code = first & (bytes == 1 ? 0x1f : 0x0f);
  if (bytes && text.size() >= static_cast<unsigned>(bytes)) {
    for (int i = 0; i < bytes; ++i) {
      const auto next = static_cast<uint8_t>(text.front());
      if ((next & 0xc0) != 0x80) return '?' - 32;
      text.remove_prefix(1);
      code = (code << 6) | (next & 0x3f);
    }
    for (unsigned i = 0; i < sizeof(extraGlyphs) / sizeof(extraGlyphs[0]); ++i) {
      if (extraGlyphs[i] == code) return 95 + i;
    }
  }
  return '?' - 32;
}

int textWidth(std::string_view text, const Font &font) {
  int width = 0;
  while (!text.empty()) width += font.glyphs[nextGlyph(text)].advance;
  return width;
}

class Canvas {
 public:
  Canvas(uint8_t *frame, Palette palette) : palette(palette), frame_(frame) {
    std::memset(frame_, palette.paper * 0x55, NEBULAR_FRAME_BYTES);
  }

  const Palette palette;

  void pixel(int x, int y, uint8_t color) {
    if (x < 0 || x >= NEBULAR_HEIGHT || y < 0 || y >= NEBULAR_WIDTH) return;
    // Rotate landscape into the panel's portrait order and mirror its scanlines.
    const unsigned column = NEBULAR_WIDTH - 1 - y;
    const unsigned offset = (NEBULAR_HEIGHT - 1 - x) * (NEBULAR_WIDTH / 4) + column / 4;
    const unsigned shift = (3 - column % 4) * 2;
    frame_[offset] = (frame_[offset] & ~(3u << shift)) | (color << shift);
  }

  void rect(int x, int y, int width, int height, uint8_t color = defaultInk) {
    if (color == defaultInk) color = palette.ink;
    for (int dy = 0; dy < height; ++dy) {
      for (int dx = 0; dx < width; ++dx) pixel(x + dx, y + dy, color);
    }
  }

  void dot(int x, int y) {
    for (int dy = -2; dy <= 2; ++dy) {
      for (int dx = -2; dx <= 2; ++dx) {
        if (dx * dx + dy * dy <= 5) pixel(x + dx, y + dy, palette.ink);
      }
    }
  }

  void text(int x, int baseline, std::string_view value, const Font &font,
            int available = 480, uint8_t color = defaultInk) {
    if (color == defaultInk) color = palette.ink;
    const int ellipsis = textWidth("…", font);
    const bool truncate = textWidth(value, font) > available;
    int limit = x + available - (truncate ? ellipsis : 0);
    while (!value.empty()) {
      const auto index = nextGlyph(value);
      const Glyph &glyph = font.glyphs[index];
      if (x + glyph.advance > limit) break;
      drawGlyph(x, baseline, glyph, font, color);
      x += glyph.advance;
    }
    if (truncate && ellipsis <= available) text(x, baseline, "…", font, ellipsis, color);
  }

 private:
  void drawGlyph(int x, int y, const Glyph &glyph, const Font &font, uint8_t color) {
    const auto *bits = font.pixels + glyph.offset;
    unsigned i = 0;
    for (int dy = 0; dy < glyph.height; ++dy) {
      for (int dx = 0; dx < glyph.width; ++dx, ++i) {
        if (bits[i / 8] & (0x80u >> (i % 8))) pixel(x + glyph.x + dx, y + glyph.y + dy, color);
      }
    }
  }

  uint8_t *frame_;
};

int clockMinutes(std::string_view time) {
  if (time.size() < 5 || time[2] != ':') return -1;
  const auto a = time[0] - '0', b = time[1] - '0', c = time[3] - '0', d = time[4] - '0';
  if (a < 0 || a > 2 || b < 0 || b > 9 || c < 0 || c > 5 || d < 0 || d > 9 || a*10+b > 23) return -1;
  return (a*10+b)*60+c*10+d;
}

int etaMinutes(std::string_view label) {
  if (label == "<1 min") return 0;
  if (label.size() < 5 || label.substr(label.size() - 4) != " min") return -1;
  label.remove_suffix(4);
  int result = -1;
  const auto number = std::from_chars(label.data(), label.data() + label.size(), result);
  return number.ec == std::errc{} && number.ptr == label.data() + label.size() && result >= 0 && result <= 120
      ? result : -1;
}

void drawDeparture(Canvas &canvas, const Departure &row, int index, int serverMinutes) {
  const int y = 70 + 22 * index;
  canvas.rect(14, y, 32, 17, index == 0 ? canvas.palette.badgeFill : canvas.palette.ink);
  if (index != 0) canvas.rect(15, y+1, 30, 15, canvas.palette.paper);
  const auto routeWidth = std::min(28, textWidth(row.line, bodyFont));
  canvas.text(30 - routeWidth/2, y+13, row.line, bodyFont, routeWidth,
              index == 0 ? canvas.palette.badgeInk : canvas.palette.ink);
  canvas.text(59, y+13, row.direction, bodyFont, 344);

  const int eta = etaMinutes(row.displayValue);
  const bool predicted = eta >= 0 && serverMinutes >= 0;
  int minutes = predicted ? (serverMinutes + eta) % 1440 : clockMinutes(row.displayValue);
  if (minutes < 0 && row.scheduledSeconds >= 0 && row.scheduledSeconds < 86400) {
    minutes = row.scheduledSeconds / 60;
  }
  char time[] = "--:--";
  if (minutes >= 0 && minutes < 1440) {
    const int hour = minutes / 60, minute = minutes % 60;
    time[0] = '0' + hour / 10;
    time[1] = '0' + hour % 10;
    time[3] = '0' + minute / 10;
    time[4] = '0' + minute % 10;
  }
  if (predicted) canvas.dot(410, y+8);
  canvas.text(466 - textWidth(time, timeFont), y+14, time, timeFont);
}

}  // namespace

uint32_t renderNebularPage(const DepartureBoard &board, const BusCity &city, const BusDeviceConfig &config,
                          uint8_t frame[NEBULAR_FRAME_BYTES]) {
  Canvas canvas(frame, paletteFor(config.theme));
  canvas.rect(14, 9, 31, 31);
  canvas.rect(15, 10, 29, 29, canvas.palette.paper);
  canvas.rect(21, 14, 17, 17);
  canvas.rect(23, 17, 13, 7, canvas.palette.paper);
  canvas.rect(23, 26, 2, 2, canvas.palette.paper);
  canvas.rect(34, 26, 2, 2, canvas.palette.paper);
  canvas.rect(22, 31, 4, 3);
  canvas.rect(33, 31, 4, 3);
  canvas.text(55, 16, std::string(city.name) + " / ODJAZDY", captionFont, 411);
  canvas.text(54, 39, config.stopName, titleFont, 412);
  canvas.rect(14, 47, 452, 2);
  canvas.text(14, 62, "LINIA", captionFont);
  canvas.text(59, 62, "KIERUNEK", captionFont);
  canvas.text(466 - textWidth("ODJAZD", captionFont), 62, "ODJAZD", captionFont);

  const int serverMinutes = clockMinutes(board.serverTime);
  const unsigned shown = std::min<size_t>(4, board.rows.size());
  for (unsigned i = 0; i < shown; ++i) {
    drawDeparture(canvas, board.rows[i], i, serverMinutes);
    if (i + 1 < shown) canvas.rect(14, 89 + 22*i, 452, 1);
  }
  if (!shown) canvas.text(14, 96, "Brak bieżących odjazdów", bodyFont);
  canvas.rect(14, 156, 452, 2);
  canvas.dot(413, 166);
  canvas.text(466 - textWidth("Prognoza", captionFont), 170, "Prognoza", captionFont);

  uint32_t fingerprint = 2166136261u;
  for (unsigned i = 0; i < NEBULAR_FRAME_BYTES; ++i) fingerprint = (fingerprint ^ frame[i]) * 16777619u;
  if (serverMinutes >= 0) canvas.text(14, 170, "Dane z " + board.serverTime.substr(0, 5), captionFont);
  return fingerprint;
}
