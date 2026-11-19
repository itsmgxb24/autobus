# autoBus BusDevice

Urządzenie działa na Waveshare ESP32-S3-Zero z Nebular Pro. ESP32 pobiera odjazdy z MyBus przez Wi-Fi i wysyła obraz do panelu po SWS. Aplikacja autoBus konfiguruje urządzenie przez Bluetooth LE w **Ustawienia → Opcje deweloperskie → BusDevice**. Po zapisaniu konfiguracji telefon nie jest potrzebny do działania tablicy.

| ESP32-S3-Zero | Nebular Pro |
| --- | --- |
| GPIO5 | SWS |
| GPIO6 | NRST |
| GND | GND |
| 3V3 | VCC przy odłączonych bateriach Nebulara |

1. Na Linuksie zbuduj program RAM dla Telinka wykonując `sh tag-firmware/get-deps.sh` i `make -C tag-firmware` z katalogu szkicu. Program jest osadzany w firmware ESP32 i nie nadpisuje pamięci flash Nebulara. Szczegóły sterownika panelu i protokołu SWS są w [repozytorium Nebular Pro](https://github.com/itsmgxb24/nebular-pro-reverse-engineered).
2. W Arduino IDE wybierz **Waveshare ESP32-S3-Zero**, pakiet **esp32 3.3.12**, **Hardware CDC and JTAG**, **USB CDC On Boot: Enabled** i **Minimal SPIFFS (1.9MB APP with OTA/128KB SPIFFS)**. Zainstaluj **NimBLE-Arduino 2.5.1** z menedżera bibliotek.
3. Wpisz SSID i hasło sieci 2,4 GHz na początku `BusDevice.ino` lub zdefiniuj obie stałe w lokalnym `WifiLocal.h`. Wgraj szkic na ESP32. Dane Wi-Fi pozostają lokalne i nie są przesyłane przez BLE.
4. Krótko naciśnij BOOT na uruchomionym ESP32, otwórz BusDevice w autoBus i znajdź urządzenie. Okno na dodanie nowego sparowania trwa dwie minuty. Pierwsze uruchomienie fabrycznie niesparowanego ESP32 również otwiera to okno.

Ustawienia są zapisywane w NVS po zatwierdzeniu całej konfiguracji przez sparowane urządzenie. Aplikacja odczytuje wynik zapisu przez GATT. ESP32 obsługuje miasta z katalogu MyBus. W pilotażowym Nebularze kod koloru czarnego może wyświetlić się pomarańczowo. Dodatkowe motywy korzystają z czerwieni, bieli i żółci.

`tools/make-cities.py` odświeża katalog firmware na podstawie `CityCatalog` aplikacji. `tools/make-fonts.py <Inter-SemiBold.ttf> <Inter-Bold.ttf>` generuje bitmapy glifów. Licencje Inter i TinyXML2 znajdują się obok ich źródeł lub narzędzi.
