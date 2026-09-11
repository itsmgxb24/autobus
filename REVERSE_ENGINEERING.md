# Reverse engineering — MyBus Online 2.8.11

## Artefakt i metoda

Przeanalizowano dostarczony plik
`/home/ruby/Pobrane/myBus+online_2.8.11_APKPure.apk` (`com.taran.mybus` 2.8.11,
SHA-256 `0e0069c56f921db66d9aaff3e3331595ebb14f9a8bc8dd257be0c57c1fb5ede8`).
Kod zdekompilowany JADX pozostaje lokalnie w `/tmp/mybus-jadx-codb97`. Kontrakt niżej
pochodzi z klas `com.taran.mybus.g`, `g4.c` i `g4.e`, a następnie został sprawdzony
minimalną serią żądań do usługi.

## Zweryfikowany kontrakt Wałbrzycha

Adres bazowy z wpisu `WAŁBRZYCH (Gmina Wałbrzych)` to
`http://rozklad.walbrzych.eu/myBusServices/SchedulesService.svc/`. Jest to HTTP, więc
aplikacja zezwala na cleartext wyłącznie dla `rozklad.walbrzych.eu` w
`network_security_config.xml`.

Każde żądanie jest `GET`, ma nagłówek `User-Agent: myBusOnline` i `Age` równy tokenowi
sesji powiększonemu o sumę kodów znaków `WALBR` (376). `PingService` jest wysyłany z
początkowym tokenem 60, zwraca XML `<int>…</int>` i ustala token na resztę sesji.

| Endpoint | Zweryfikowane parametry | Wynik używany przez aplikację |
| --- | --- | --- |
| `PingService` | — | token sesji `<int>` |
| `CompareScheduleFile` | `nIdWersja`, `nGeneracja` | `<int>1</int>` oznacza lokalną wersję aktualną |
| `GetScheduleFile` | — | bajty GZIP zawierające SQLite |
| `GetTimeTableReal` | `nBusStopId`, `nBusStopGroupId=0` | XML `Departures`/`D` |
| `GetDepartureInfo` | `cDate=yyyy-MM-dd`, `nBusStopId`, `nUqTripId` | XML `D`; ID wycieczki pochodzi z planera, więc ekran przystanku go nie zgaduje |
| `GetVehicles` | `cNbLst`, `cIdLst`, `cRouteLst`, `cTrackLst`, `cDirLst`, `cKrsLst` | XML `VL`/`V`, gdzie `x` to długość, a `y` szerokość |

`GetTimeTableReal` może zwracać `v="6 min"` (ETA) albo `v="19:02"` bez ETA.
Interfejs wyświetla odpowiednio `Przyjazd za 6 min` i dokładnie `Przyjazd: 19:02`.
Pozycje pojazdów nie są symulowane i są pobierane wyłącznie po kliknięciu użytkownika
dla wybranej linii oraz wariantu.

## Pobrana baza i mapowanie

`GetScheduleFile` zwrócił GZIP (192 901 B), który rozpakowuje się do SQLite (675 840 B).
`PRAGMA quick_check` oraz `PRAGMA integrity_check` zwróciły `ok`. Wersja `WERSJE`:
`377`, ważna od `2026-09-11`, generacja `1`.

| Tabela | Użycie runtime |
| --- | --- |
| `PRZYSTANKI` | identyfikator, nazwa, numer i współrzędne wszystkich przystanków |
| `DNI`, `KALENDARZ` | opis typów dni oraz przypisanie dat do kalendarza |
| `ODJAZDY` | czasy w sekundach od północy, linia, wariant i kolejność na przystanku |
| `KIERUNKI` | kierunek oraz `trasa`: uporządkowane ID przystanków wariantu |
| `WERSJE` | porównanie wersji i data widoczna w interfejsie |

`PUNKTY` w sprawdzonej bazie jest puste. Aplikacja może więc narysować tylko łamaną przez
rzeczywiste współrzędne przystanków zapisane w `KIERUNKI.trasa`; nie opisuje jej jako
geometrii ulic.

## Zasady implementacji

`ScheduleFileStore` najpierw rozpakowuje do pliku staging, sprawdza nagłówek SQLite,
integralność, wymagane tabele, osierocone odjazdy i minimalną kompletność, a dopiero potem
atomowo przełącza wskaźnik aktywnej bazy. Błąd, anulowanie lub nieprawidłowa odpowiedź
zostawia poprzednią działającą kopię. Przy kolejnym uruchomieniu jest ona od razu czytana
offline, a porównanie wersji odbywa się w tle.

`app/src/main/assets/data/walbrzych_stops.json` pozostaje wyłącznie fixturą testową;
nie stanowi źródła danych runtime.
