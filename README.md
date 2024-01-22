# ING
## Uruchamianie

Wymagania: zainstalowany Docker, w IDE Lombok z włączonym `Annotation processing`.
Konfiguracja: ścieżka do miejsca na pliki ocen jest ustawiana jako `voteFile.location` w `application.properties`.
Wykonaj `docker compose up` w katalogu głównym projektu. Gdy wstanie PostgreSQL, uruchom `IngApplication`.

## Uwagi
Na projekt poświęciłem około 8-10 godzin. Działa - spełnia wymagania, natomiast sporo w nim brakuje albo jest do poprawy/przepisania.

### Do dopisania
Testy - obecnie brak jakichkolwiek, nie wystarczyło mi czasu.

Przystosowanie do dużej ilości danych. Dla 2,5 mln rekordów w bazie wywołanie `/api/{songId}/avg?since={dateSince}&until={dateUntil)` trwa u mnie 150-200 ms. Już dla bazy większej o jeden czy dwa rzędy wielkości pojawi się problem.

Obsługa wyjątków.

### Do poprawy/przepisania/analizy
Znany błąd: przy próbie odczytu kolejnego pliku z ocenami (dla pierwszego działa OK), dostaję wyjątek `Proces nie może uzyskać dostępu do pliku, ponieważ jest on używany przez inny proces`. Obejściem jest zmiana nazwy pliku.

Import danych obecnie używa `Scanner`, do przepisania na `commons-csv` (tak jest zrobiony eksport).

Kwestia użycia Lomboka/rekordów dla klas DTO/encji.

Indeksy na tabeli `votes`.

Dokończenie i analiza metody `exportTrends` na streamach - wydajność będzie znacznie niższa, lecz kod czytelniejszy.

Sprawdzenie, przy importowaniu jakiej ilości rekordów nie opłaca się zrównoleglanie zapisu do bazy.

Dla środowiska produkcyjnego należy odkomentować `volumes` w `docker-compose`.

