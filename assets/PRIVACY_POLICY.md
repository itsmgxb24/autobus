# Polityka prywatności autoBus

**Data wejścia w życie:** 14 września 2026 r.<br>
**Dotyczy aplikacji:** autoBus (`pl.ruby.lubiechowlabs.autobus`)

## 1. Administrator i kontakt

Administratorem aplikacji autoBus jest jej autor. W sprawach dotyczących prywatności, danych lub tej polityki skontaktuj się pod adresem: [itsmgxb25@gmail.com](mailto:itsmgxb25@gmail.com).

## 2. Zakres polityki

autoBus jest aplikacją informacyjną dla komunikacji miejskiej. Umożliwia pobieranie rozkładów, wyświetlanie przystanków i map, sprawdzanie dostępnych danych czasu rzeczywistego oraz korzystanie z widgetów. Aplikacja nie wymaga założenia konta i nie umożliwia zakupu biletów ani przetwarzania płatności.

## 3. Dane przetwarzane przez aplikację

### Lokalizacja urządzenia

Za Twoją zgodą aplikacja może jednorazowo odczytać przybliżoną lub dokładną lokalizację urządzenia na pierwszym planie. Służy ona wyłącznie do:

- wyśrodkowania mapy na użytkowniku;
- sortowania przystanków według odległości;
- pokazania odległości od przystanków.

autoBus nie prowadzi śledzenia lokalizacji w tle, nie zapisuje historii lokalizacji i nie wysyła współrzędnych do własnego serwera. Odmowa zgody nie blokuje korzystania z rozkładów — lista przystanków jest wtedy sortowana względem środka pobranych danych miasta.

### Dane techniczne przekazywane usługom zewnętrznym

Gdy korzystasz z funkcji sieciowych, Twoje urządzenie łączy się bezpośrednio z usługami właściwego operatora komunikacji, aby pobrać rozkład, dane czasu rzeczywistego i — jeśli operator je publikuje — pozycje pojazdów. Dostawca takiej usługi może otrzymać standardowe dane techniczne połączenia, w szczególności adres IP, datę i godzinę żądania, nagłówki HTTP oraz identyfikator żądanego zasobu.

Mapy korzystają z kafelków OpenStreetMap. Dostawca kafelków może otrzymać adres IP oraz obszar mapy wynikający z pobieranych kafelków. Jeżeli mapa jest wyśrodkowana na Twojej lokalizacji, obszar pobieranych kafelków może pośrednio wskazywać jej przybliżenie. Więcej informacji znajduje się w [polityce prywatności OpenStreetMap Foundation](https://wiki.osmfoundation.org/wiki/Privacy_Policy).

autoBus nie prowadzi własnego serwera analitycznego, reklamowego ani profilującego. Nie używa SDK reklamowych, analitycznych ani narzędzi do raportowania awarii, które celowo przekazywałyby dane osobowe do autora aplikacji.

### Dane przechowywane lokalnie na urządzeniu

Aplikacja zapisuje wyłącznie w prywatnej pamięci aplikacji dane niezbędne do jej działania, w tym:

- pobrane rozkłady, kalendarze kursowania i dane przystanków;
- cache kafelków mapy;
- wybrane miasto, ustawienia interfejsu, ulubione przystanki i konfigurację widgetów;
- informacje o wybranym śledzonym odjeździe, jeśli użytkownik włączy powiadomienie Live Update;
- demonstracyjne bilety utworzone lokalnie w aplikacji.

Te dane nie są wysyłane przez autoBus do autora aplikacji. Można je usunąć, czyszcząc dane aplikacji w ustawieniach systemu Android albo odinstalowując aplikację. Aplikacja ma wyłączony systemowy backup danych.

## 4. Uprawnienia

| Uprawnienie | Cel |
| --- | --- |
| Lokalizacja przybliżona i dokładna | Jednorazowe wyświetlenie lokalizacji użytkownika na mapie oraz sortowanie przystanków według odległości. |
| Powiadomienia | Wyłącznie po wybraniu przez użytkownika śledzenia konkretnego odjazdu. |
| Internet | Pobieranie rozkładów i danych czasu rzeczywistego od operatorów oraz kafelków mapy. |
| Usługa na pierwszym planie / uruchomienie po restarcie | Utrzymanie wybranego przez użytkownika powiadomienia Live Update; nie służy do śledzenia lokalizacji. |

Uprawnienia lokalizacji i powiadomień można odmówić albo cofnąć w ustawieniach Androida. Nie wpływa to na możliwość korzystania z lokalnie zapisanych rozkładów.

## 5. Odbiorcy danych

W zależności od wybranego miasta dane techniczne połączenia mogą być przetwarzane przez:

- operatora komunikacji miejskiej udostępniającego rozkład i dane czasu rzeczywistego;
- dostawcę kafelków OpenStreetMap;
- dostawcę połączenia internetowego użytkownika.

Autor autoBus nie sprzedaje danych, nie udostępnia ich brokerom danych i nie używa ich do reklamy behawioralnej.

## 6. Bezpieczeństwo połączeń

W miarę dostępności aplikacja korzysta z HTTPS. Część starszych serwerów rozkładowych operatorów działa wyłącznie przez HTTP; użytkownik może wybrać ten tryb w ustawieniach aplikacji. Przy HTTP treść połączenia nie ma ochrony zapewnianej przez TLS, dlatego zalecamy używanie HTTPS, gdy dany operator je obsługuje.

## 7. Dzieci

Aplikacja nie jest kierowana szczególnie do dzieci i nie zbiera świadomie danych osobowych dzieci. Nie ma kont użytkownika, czatu ani funkcji społecznościowych. KanarAlert pozostaje wyłączony i nie przyjmuje zgłoszeń.

## 8. Zmiany polityki

Polityka może zostać zaktualizowana, gdy zmieni się działanie aplikacji lub wymagania prawne. Aktualna wersja będzie publikowana pod tym samym adresem w repozytorium projektu. Data wejścia w życie na początku dokumentu wskazuje ostatnią aktualizację.

## 9. Kontakt

Pytania, prośby lub uwagi dotyczące prywatności: [itsmgxb25@gmail.com](mailto:itsmgxb25@gmail.com).
