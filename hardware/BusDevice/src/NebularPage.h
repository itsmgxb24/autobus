#pragma once

#include "BusDeviceConfig.h"
#include "MyBusData.h"
#include "NebularProtocol.h"

// Returns a fingerprint of the visible departures. The data timestamp is excluded.
uint32_t renderNebularPage(const DepartureBoard &board, const BusCity &city, const BusDeviceConfig &config,
                          uint8_t frame[NEBULAR_FRAME_BYTES]);
