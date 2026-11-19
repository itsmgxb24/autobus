#include "MyBusData.h"

#include <charconv>
#include <string_view>
#include <utility>

#include "tinyxml2/tinyxml2.h"

namespace {

std::string_view trim(const char *text) {
  std::string_view value(text ? text : "");
  constexpr char whitespace[] = " \t\r\n";
  const auto start = value.find_first_not_of(whitespace);
  if (start == std::string_view::npos) return {};
  return value.substr(start, value.find_last_not_of(whitespace) - start + 1);
}

template <typename Integer>
bool parseNumber(std::string_view value, Integer &number) {
  if (value.empty()) return false;
  const char *end = value.data() + value.size();
  const auto result = std::from_chars(value.data(), end, number);
  return result.ec == std::errc{} && result.ptr == end;
}

const tinyxml2::XMLElement *parseRoot(tinyxml2::XMLDocument &doc, const char *xml,
                                     std::size_t length, const char *name) {
  if (length == 0 || doc.Parse(xml, length) != tinyxml2::XML_SUCCESS) return nullptr;
  const auto *root = doc.FirstChildElement(name);
  return root && root == doc.RootElement() && !root->NextSiblingElement() ? root : nullptr;
}

}  // namespace

bool parseSessionToken(const char *xml, std::size_t length, int &token) {
  tinyxml2::XMLDocument doc;
  const auto *root = parseRoot(doc, xml, length, "int");
  int parsed = 0;
  if (!root || !parseNumber(trim(root->GetText()), parsed) || parsed <= 0) return false;
  token = parsed;
  return true;
}

bool parseDepartures(const char *xml, std::size_t length, unsigned stopId,
                     DepartureBoard &board) {
  tinyxml2::XMLDocument doc;
  const auto *root = parseRoot(doc, xml, length, "Departures");
  if (!root) return false;

  if (const auto *id = root->Attribute("i")) {
    unsigned receivedStop = 0;
    if (!parseNumber(trim(id), receivedStop) || receivedStop != stopId) return false;
  }
  const auto serverTime = trim(root->Attribute("time"));
  if (serverTime.empty()) return false;
  DepartureBoard parsed;
  parsed.serverTime = serverTime;
  if (const auto *notice = root->FirstChildElement("N")) {
    parsed.notice = trim(notice->GetText());
  }

  for (const auto *row = root->FirstChildElement("D"); row;
       row = row->NextSiblingElement("D")) {
    const auto line = trim(row->Attribute("r"));
    const auto direction = trim(row->Attribute("d"));
    const auto displayValue = trim(row->Attribute("v"));
    int scheduledSeconds = 0;
    if (line.empty() || !row->Attribute("d") || !row->Attribute("v") ||
        !parseNumber(trim(row->Attribute("t")), scheduledSeconds)) return false;
    parsed.rows.push_back({std::string(line), std::string(direction),
                           std::string(displayValue), scheduledSeconds});
  }

  board = std::move(parsed);
  return true;
}
