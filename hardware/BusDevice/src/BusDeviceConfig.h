#pragma once

#include <cstddef>
#include <cstdint>
#include <string>

enum class BusTheme : uint8_t { Mono = 0, RedWhite = 1, YellowAccent = 2, RedInverted = 3 };

struct BusDeviceConfig {
  uint16_t cityId = 10;
  uint32_t stopId = 395;
  uint16_t refreshMinutes = 5;
  BusTheme theme = BusTheme::RedWhite;
  std::string stopName = "Plac Grunwaldzki";
};

struct BusCity {
  uint16_t id;
  const char *name;
  const char *baseUrl;
  unsigned offset;
};

constexpr size_t busDeviceMaxConfig = 251;
const BusCity *getBusCity(uint16_t id);
size_t encodeBusConfig(const BusDeviceConfig &config, uint8_t out[busDeviceMaxConfig]);
bool decodeBusConfig(const uint8_t *bytes, size_t length, BusDeviceConfig &out);
