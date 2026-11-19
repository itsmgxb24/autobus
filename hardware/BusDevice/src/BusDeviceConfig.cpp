#include "BusDeviceConfig.h"

#include <cstring>
#include <utility>

#include "generated/BusCities.h"

namespace {
constexpr size_t header = 11;
constexpr uint8_t version = 1;

uint16_t le16(const uint8_t *b) { return b[0] | (uint16_t(b[1]) << 8); }

bool validName(const uint8_t *data, size_t length) {
  for (size_t i = 0; i < length;) {
    const uint8_t first = data[i++];
    if (first >= 0x20 && first <= 0x7e) continue;
    const unsigned tail = first >= 0xc2 && first <= 0xdf ? 1 :
                          first >= 0xe0 && first <= 0xef ? 2 :
                          first >= 0xf0 && first <= 0xf4 ? 3 : 0;
    if (!tail || i + tail > length) return false;
    if ((first == 0xe0 && data[i] < 0xa0) || (first == 0xed && data[i] >= 0xa0) ||
        (first == 0xf0 && data[i] < 0x90) || (first == 0xf4 && data[i] >= 0x90)) return false;
    for (unsigned n = 0; n < tail; ++n) if ((data[i++] & 0xc0) != 0x80) return false;
  }
  return length != 0;
}
}  // namespace

const BusCity *getBusCity(uint16_t id) {
  for (const auto &city : busCities) if (city.id == id) return &city;
  return nullptr;
}

size_t encodeBusConfig(const BusDeviceConfig &config, uint8_t out[busDeviceMaxConfig]) {
  if (config.stopName.size() > busDeviceMaxConfig - header) return 0;
  out[0] = version;
  out[1] = config.cityId;
  out[2] = config.cityId >> 8;
  for (unsigned i = 0; i < 4; ++i) out[3+i] = config.stopId >> (8*i);
  out[7] = config.refreshMinutes;
  out[8] = config.refreshMinutes >> 8;
  out[9] = static_cast<uint8_t>(config.theme);
  out[10] = config.stopName.size();
  std::memcpy(out + header, config.stopName.data(), config.stopName.size());
  return header + config.stopName.size();
}

bool decodeBusConfig(const uint8_t *bytes, size_t length, BusDeviceConfig &out) {
  if (length <= header || length > busDeviceMaxConfig || bytes[0] != version || bytes[10] != length - header ||
      !validName(bytes + header, length - header)) return false;
  BusDeviceConfig parsed;
  parsed.cityId = le16(bytes + 1);
  parsed.stopId = le16(bytes + 3) | (uint32_t(le16(bytes + 5)) << 16);
  parsed.refreshMinutes = le16(bytes + 7);
  if (!getBusCity(parsed.cityId) || !parsed.stopId || parsed.stopId > 0x7fffffffu ||
      parsed.refreshMinutes < 1 || parsed.refreshMinutes > 60 || bytes[9] > uint8_t(BusTheme::RedInverted)) return false;
  parsed.theme = static_cast<BusTheme>(bytes[9]);
  parsed.stopName.assign(reinterpret_cast<const char *>(bytes + header), length - header);
  out = std::move(parsed);
  return true;
}
