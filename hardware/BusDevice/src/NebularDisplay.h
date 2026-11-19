#pragma once

#include "NebularProtocol.h"

class NebularDisplay {
 public:
  enum class Result { Idle, Busy, Done, Failed };

  NebularDisplay(uint8_t nrst, uint8_t sws) : nrst_(nrst), sws_(sws) {}
  bool send(const uint8_t frame[NEBULAR_FRAME_BYTES]);
  Result poll();

 private:
  bool startTag();

  uint8_t nrst_;
  uint8_t sws_;
  bool ready_ = false;
  bool pending_ = false;
  uint8_t sequence_ = 0;
  unsigned long sentAt_ = 0;
  unsigned long lastRead_ = 0;
};
