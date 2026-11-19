#pragma once

#include <cstddef>
#include <string>
#include <vector>

struct Departure {
  std::string line;
  std::string direction;
  std::string displayValue;  // Wartość MyBus, np. „18:36”, „6 min” lub „<1 min”.
  int scheduledSeconds;    // Sekundy od północy według rozkładu.
};

struct DepartureBoard {
  std::string serverTime;
  std::string notice;
  std::vector<Departure> rows;
};

bool parseSessionToken(const char *xml, std::size_t length, int &token);
bool parseDepartures(const char *xml, std::size_t length, unsigned stopId,
                     DepartureBoard &board);
