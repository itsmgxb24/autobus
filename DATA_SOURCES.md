# Źródła danych

Źródłem runtime jest oficjalna usługa MyBus dla Wałbrzycha:

- `http://rozklad.walbrzych.eu/myBusServices/SchedulesService.svc/`

Szczegółowy, zweryfikowany kontrakt i sposób mapowania znajdują się w
[`REVERSE_ENGINEERING.md`](REVERSE_ENGINEERING.md). Pełna baza jest pobierana tylko,
gdy `CompareScheduleFile` wskazuje nową wersję, a następnie działa lokalnie offline.

`app/src/main/assets/data/walbrzych_stops.json` jest zachowany wyłącznie jako mały
fixture parsera/testów. Nie jest odczytywany przez aplikację podczas działania i nie
ogranicza liczby prezentowanych przystanków.
