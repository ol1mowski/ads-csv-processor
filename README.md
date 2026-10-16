# Procesor CSV kampanii reklamowych (pliki do 5 GiB)

Aplikacja przyjmuje plik CSV w formacie `sample_ads_data.csv`, zapisuje go strumieniowo na dysk, przetwarza asynchronicznie i dopisuje do każdego wiersza kolumnę **`ctr = clicks / impressions`**. Przy okazji liczy sumy całego pliku. REST API pod `/api/v1` pozwala wysłać plik, przerwać przetwarzanie, odczytać status i przejrzeć historię. Interfejs w React 19 obsługuje logowanie, upload z paskiem postępu, anulowanie, status i pobranie wyniku.

Upload i przetwarzanie działają w stałej pamięci: plik 5 GiB przechodzi przy stercie ograniczonej do 256 MiB.

**Stack:** Java 25 · Spring Boot 4.1.1 (Spring Security 7.1) · PostgreSQL 18 + Flyway · React 19 + TypeScript + Vite 8 + Tailwind CSS 4 · Docker Compose.

## 1. Szybki start

```bash
docker compose up --build
```

Otwórz http://localhost:8080 i zaloguj się jako **`alice` / `alice-demo-pass`** (albo `bob` / `bob-demo-pass`). Spring serwuje SPA i API z jednego originu, więc nie ma CORS ani proxy. Porty są wystawione tylko na `127.0.0.1`. Wszystko usuniesz poleceniem `docker compose down -v`.

Ten sam przepływ z linii poleceń (JWT w ciasteczku `HttpOnly` + token CSRF w nagłówku):

```bash
JAR=$(mktemp); API=http://localhost:8080/api/v1
xsrf() { awk '$6 == "XSRF-TOKEN" { print $7 }' "$JAR"; }
curl -s -c "$JAR" "$API/auth/me" >/dev/null
curl -s -b "$JAR" -c "$JAR" -H "X-XSRF-TOKEN: $(xsrf)" -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"alice-demo-pass"}' "$API/auth/login"
ID=$(curl -s -b "$JAR" -H "X-XSRF-TOKEN: $(xsrf)" -X POST -T sample_ads_data.csv -H 'Content-Type: text/csv' \
  "$API/jobs?fileName=sample_ads_data.csv" | jq -r .id)
until curl -s -b "$JAR" "$API/jobs/$ID" | jq -e '.status != "QUEUED" and .status != "PROCESSING"' >/dev/null; do sleep 1; done
curl -s -b "$JAR" "$API/jobs/$ID" | jq '{status, rowsProcessed, totals}'
curl -s -b "$JAR" -OJ "$API/jobs/$ID/result"
```

Używaj `curl -T` (strumień z dysku). `--data-binary @plik` wczytuje cały plik do pamięci curla.

**Konta** pochodzą wyłącznie z konfiguracji (`app.security.users`: nazwa + hash `{bcrypt}`). Profil `demo` ma dwa konta z jawnymi hasłami, więc nadaje się tylko na lokalną maszynę; compose, tryb deweloperski, `scripts/bench.sh` i konfiguracja IntelliJ włączają go jawnie. Bez profilu aplikacja nie ma żadnych kont ani sekretu i nie wystartuje. Własne konto podasz zmiennymi `APP_SECURITY_USERS_0_USERNAME` i `APP_SECURITY_USERS_0_PASSWORD_HASH` (hash: `htpasswd -nbBC 10 '' 'haslo' | tr -d ':\n'`), a sekret zmienną `JWT_SECRET` (min. 32 znaki).

**Tryb deweloperski** (JDK 25, Node 24, Docker):

```bash
docker compose up -d postgres                                        # PostgreSQL na localhost:5433
cd server && ./mvnw spring-boot:run -Dspring-boot.run.profiles=demo  # API na :8080
cd client && npm ci && npm run dev                                   # Vite na :5173, proxy /api -> :8080
```

**Testy:**

| Polecenie | Co uruchamia | Docker |
|---|---|---|
| `cd server && ./mvnw clean test` | 464 testy jednostkowe i slice (`@WebMvcTest` z prawdziwym łańcuchem Spring Security), ArchUnit, inwentarz endpointów | nie |
| `cd server && ./mvnw clean verify` | powyższe + 241 testów integracyjnych na PostgreSQL 18 (Testcontainers) i prawdziwym Tomcacie, bramka JaCoCo, google-java-format | tak |
| `cd client && npm run verify` | `tsc`, ESLint, Prettier, 142 testy Vitest + MSW, build produkcyjny | nie |

CI (`.github/workflows/ci.yml`) uruchamia oba `verify` i buduje obraz.

## 2. Wymagania z zadania

| Wymaganie | Realizacja |
|---|---|
| Przyjęcie pliku | `POST /api/v1/jobs?fileName=…` z surowym body `text/csv`, zapis strumieniowy, **202 + `Location`** |
| Przerwanie przetwarzania | `POST /api/v1/jobs/{id}/cancel`: strzeżony `UPDATE` w bazie, worker zatrzymuje się przy najbliższym checkpoincie i sprząta pliki |
| Odczyt statusu | `GET /api/v1/jobs/{id}`: status, postęp w %, liczniki wierszy, sumy, próbki odrzuceń, powód błędu |
| Historia | `GET /api/v1/jobs?page&size&status`, od najnowszych, z filtrem po statusach |
| Asynchroniczność | 202 zaraz po trwałym zapisie; kolejka w tabeli PostgreSQL (`FOR UPDATE SKIP LOCKED`), pula 2 workerów |
| Pliki do 5 GB | strumień przy uploadzie i przetwarzaniu, limit 5 GiB włącznie, stała pamięć (§5) |
| Transformacja dwóch kolumn | nowa kolumna `ctr` + sumy `impressions`, `clicks` i CTR całego pliku (§3) |
| Testy wszystkich endpointów | macierz endpoint × kod statusu w testach slice i integracyjnych; `EndpointInventoryTest` psuje build, gdy pojawi się nieprzetestowany endpoint; JaCoCo wymaga 100 % linii warstwy web |
| Java 25 + Spring Boot + REST, React | Boot 4.1.1, rekordy, `sealed interface` + `switch` ze wzorcami, wątki wirtualne, RFC 9457 (`ProblemDetail`); React 19 |

Poza zakresem zadania, ale potrzebne, bo aplikacja przyjmuje 5 GB od klientów: logowanie (JWT w ciasteczku `HttpOnly`), CSRF i własność zadań. Co celowo pominąłem i dlaczego, opisuje §6.

## 3. API i transformacja

Wszystkie ścieżki są pod `/api/v1`. Błędy mają typ `application/problem+json` (RFC 9457) ze stałym polem `code`. Każdy endpoint `/jobs` może dodatkowo zwrócić 401 (brak tokenu), a każdy `POST` 403 `CSRF_INVALID`.

| Metoda i ścieżka | Sukces | Błędy (`code`) |
|---|---|---|
| `GET /auth/me` | 200 `{"username":"alice"}` | 401; zawsze wydaje ciasteczko `XSRF-TOKEN` |
| `POST /auth/login` | 200, ciasteczko `access_token` (JWT, `HttpOnly`, `SameSite=Lax`) | 400, 401 `BAD_CREDENTIALS` (identyczne dla nieznanego konta i złego hasła) |
| `POST /auth/logout` | 204, ciasteczka `access_token` i `XSRF-TOKEN` skasowane | 403 |
| `POST /jobs?fileName={nazwa}.csv` z body `text/csv` | **202** + `Location`, zadanie `QUEUED` | 400 `EMPTY_FILE` / `UPLOAD_INTERRUPTED`, 413 `FILE_TOO_LARGE`, 415, 422 `INVALID_CSV_HEADER`, 507 `INSUFFICIENT_STORAGE` |
| `GET /jobs/{id}` | 200 `JobResponse` | 404 `JOB_NOT_FOUND` |
| `GET /jobs?page=0&size=20&status=COMPLETED,FAILED` | 200 `{items, page, size, totalElements, totalPages}` | 400 |
| `POST /jobs/{id}/cancel` | 200 `CANCELLED` (idempotentne) | 404, 409 `JOB_NOT_CANCELLABLE` |
| `GET /jobs/{id}/result` | 200 `text/csv` strumieniowo, `Content-Disposition` z `filename*` | 404, 409 `RESULT_NOT_AVAILABLE`, 410 `RESULT_GONE` |

Cudze zadanie daje 404 jak nieistniejące, żeby nie zdradzać, że istnieje. Sumy w `totals` są stringami, bo dla 5 GiB przekraczają 2⁵³.

**Transformacja: `ctr = clicks / impressions`**, jako ułamek. Sumy całego pliku liczymy w tym samym przebiegu, a CTR całości to iloraz sum (nie średnia CTR-ów wierszy).

| Reguła | Decyzja |
|---|---|
| Arytmetyka | `BigDecimal`, 6 miejsc, `HALF_UP`, `toPlainString()`: bez błędów `double`, bez notacji naukowej, niezależnie od `Locale` |
| `impressions = 0` | **pusta komórka** (w próbce 11 takich wierszy); `0` zaniżałoby średnie, a `NaN` psułby parsowanie |
| Wyjście | wiersz wejścia bajt w bajt + `;` + `ctr`; nagłówek + `;ctr` |
| `clicks > impressions` | wiersz odrzucany (`CLICKS_EXCEED_IMPRESSIONS`), bo CTR powyżej 100 % jest niemożliwy; w próbce nie ma takich wierszy. Gdyby źródło miało znane rozjazdy w zliczaniu kliknięć, tę regułę łatwo zamienić na samo liczenie bez odrzucania |
| Złe wiersze | pomijane i liczone, 10 próbek `{line, reason}`; interfejs pokazuje przy nich kolumnę i oczekiwany format (np. „liczba z kropką, nie 2,9798”) |
| Kiedy zadanie pada | po przejściu całego pliku: **> 1 % odrzuconych wierszy** daje `FAILED(TOO_MANY_INVALID_ROWS)`, a sam brak poprawnych wierszy `FAILED(NO_VALID_ROWS)`; wynik jest wtedy kasowany. Duży plik przerywa się wcześniej, gdy odrzuceń jest > 1000 **i** > 1 % (`min-rejected-to-abort`): na początku pliku sam procent jest niestabilny |

Próbka daje sumy `6733389` / `176755` / `0.026251`. Inną transformację dodaje się nową implementacją `ColumnTransformation` (test `RowTransformerTest.shouldUseInjectedTransformationWhenItIsReplaced` liczy tak `revenue × advertiserMargin`).

## 4. Architektura

```
Przeglądarka / curl -T
  ▼
Spring Security: JWT z ciasteczka (podpis, iss, exp, konto z konfiguracji) ─► CSRF ─► autoryzacja
  ▼
JobController ─► JobUploadService: czy jest miejsce na wejście i wynik (inaczej 507)
  │                 └─ LimitedInputStream ─► incoming/<id>.part ─fsync─► ATOMIC_MOVE ─► jobs/<id>/input.csv ─► INSERT QUEUED ─► 202
  └─► JobService (status, historia, anulowanie, wynik)

JobDispatcher (co 1 s, Semaphore = 2) ─► JobStateService.claimNext(): FOR UPDATE SKIP LOCKED
  └─► csv-worker-N (wątki platformowe) ─► CsvJobProcessor
        input.csv ─► BoundedLineReader ─► RowTransformer ─► output.csv
        co ≤ 1 s: UPDATE postęp … WHERE status = 'PROCESSING'   (0 wierszy ⇒ anulowano, stop)

StartupRecovery (przy starcie, przed pierwszym żądaniem): PROCESSING ⇒ QUEUED albo FAILED(INTERRUPTED);
                usuwa pliki zadań CANCELLED, FAILED i bez wiersza w bazie
  ▼                                                  ▼
PostgreSQL: processing_job (kolejka, status,       Wolumen /data: incoming/<uuid>.part,
historia)                                          jobs/<uuid>/{input,output}.csv
```

Pakiety: `controller`, `security`, `service` (`upload`, `job`, `processing`, `state`, `csv`, `transformation`, `storage`, `auth`), `repository`, `model`, `dto`, `exception`, `config`, `shared`. Reguły warstw egzekwuje `ArchitectureTest` (ArchUnit).

**Maszyna stanów:**

```
            INSERT (bajty trwałe na dysku)
                     ▼
   ┌───────────── QUEUED ◄──────────────────────────────┐
   │ claim             │ cancel                         │ release przy wyłączaniu
   ▼                   ▼                                │ albo odzysk przy starcie (attempts < 3)
PROCESSING ─ cancel ─► CANCELLED                        │
   ├─ complete ──────► COMPLETED                        │
   ├─ fail ──────────► FAILED                           │
   ├─ odzysk przy starcie (attempts ≥ 3) ─► FAILED(INTERRUPTED)
   └────────────────────────────────────────────────────┘
```

Kluczowe decyzje:

- **Wszystkie przejścia stanu są w `JobStateService`.** Jedynym `save()` jest INSERT zadania `QUEUED`, a każde dalsze przejście to natywny `UPDATE … WHERE status = …`; liczba zmienionych wierszy mówi, kto wygrał. Dostęp do repozytorium zadań spoza tej klasy blokuje reguła ArchUnit, a spójność wierszy pilnują ograniczenia `CHECK` w migracji.
- **Anulowanie bez przerywania wątków.** Worker co 1 MiB przeczytanych bajtów sprawdza stan, a najwyżej raz na sekundę zapisuje postęp strzeżonym `UPDATE`. Checkpoint liczy bajty, a nie wiersze, więc nawet pomijana linia długości kilku GiB nie wstrzymuje anulowania. Gdy ten `UPDATE` nie zmieni wiersza, zadanie zostało anulowane: worker kończy i kasuje pliki. `Thread.interrupt` odpada, bo strumienie `Files.*` go ignorują.
- **Kolejka w Postgresie zamiast brokera.** `FOR UPDATE SKIP LOCKED` daje rozłączne claimy, kolejka przeżywa restart, a ograniczona pula workerów daje backpressure.
- **Odzysk przy starcie zamiast heartbeatu.** Aplikacja działa jako jedna instancja, więc zadanie `PROCESSING` po starcie zawsze jest osierocone. `StartupRecovery` wraca je do kolejki (z wyzerowanym postępem), a po 3 próbach kończy jako `FAILED(INTERRUPTED)`, żeby plik wywracający JVM nie zapętlił restartów. SIGTERM w trakcie pliku oddaje zadanie do kolejki od razu. Ten sam odzysk usuwa katalogi zadań `CANCELLED`, `FAILED` i takich bez wiersza w bazie: zostają po twardym crashu między zmianą stanu a skasowaniem plików (np. anulowanie, po którym kontener dostał SIGKILL).
- **Wątki:** HTTP i harmonogram na wątkach wirtualnych (I/O), workery CSV na 2 wątkach platformowych, bo parsowanie jest CPU-bound, a planista wątków wirtualnych nie ma time-sharingu.
- **Własny parser na bajtach** (`BoundedLineReader` + walidatory z allow-listą): `readLine()` na pliku bez `\n` daje OOM, a łagodne parsery JDK przepuszczają `+5` czy `1E+3`. Dialekt nie zna cudzysłowów, więc pełny parser CSV nie jest potrzebny.

Wyścigi sprawdzają testy na prawdziwym Postgresie, każdy powtarzany 50 razy (`JobStateRaceIntegrationTest`, `JobQueueIntegrationTest`):

| Wyścig | Rozstrzygnięcie |
|---|---|
| cancel kontra complete | wygrywa pierwszy zatwierdzony; przegrany worker kasuje pliki, przegrany cancel dostaje 409 |
| claim kontra cancel | cancel blokuje wiersz (`FOR UPDATE`), claim go pomija (`SKIP LOCKED`) albo przegrywa `UPDATE` |
| cancel kontra oddanie do kolejki | ta sama blokada wiersza: nigdy fałszywe 409 |
| claim kontra claim | rozłączne zbiory zadań |

## 5. Pliki 5 GB

1. **Bez multipartu.** Body to surowe `text/csv` czytane jako strumień i zapisywane na dysk dokładnie raz (`spring.servlet.multipart.enabled=false`).
2. **Odmowa przed odczytem.** `Content-Length` > 5 GiB daje 413, a brak miejsca na wejście i wynik (2,2× rozmiaru) daje 507. Dzięki `continueResponseTiming = ON_REQUEST_BODY_READ` klient z `Expect: 100-continue` nie wysyła wtedy ani bajtu.
3. **Twardy limit w strumieniu.** `LimitedInputStream` przerywa przy bajcie 5 368 709 121, także przy uploadzie chunked.
4. **Nagłówek na pierwszej linii.** Zły nagłówek daje 422, zanim reszta pliku trafi na dysk.
5. **Przetwarzanie w O(1) pamięci.** `BoundedLineReader` czyta `byte[]` z buforem 1 MiB i limitem linii 1024 B; zbyt długa linia jest odrzucana jako `LINE_TOO_LONG`. Walidatory pól to pętle po bajtach, bez regexów i bez `String` na pole.

Pomiar `scripts/bench.sh --size 5GiB` (Apple M5, 32 GiB RAM, SSD; lokalnie JDK 25.0.3, w obrazie Temurin 25.0.4.1, Docker Desktop 29.6.1; plik 5 368 709 094 B, 69 088 120 wierszy, ziarno 42):

| Przebieg | Upload | Przetwarzanie | Pobranie wyniku | Pamięć | Anulowanie |
|---|---|---|---|---|---|
| `local`: `java -Xmx256m`, JFR | 3,48 s | **16,64 s (308 MiB/s)** | 3,17 s | sterta: szczyt **116 MiB**, 61 MiB po GC; RSS 378 MiB | przy 54 %, katalog usunięty po 1,19 s |
| `compose`: obraz, `-Xmx512m` | 17,89 s | **19,61 s (261 MiB/s)** | 26,85 s | kontener 626 MiB wg `docker stats` (z page cache) | przy 51 %, katalog usunięty po 1,29 s |

Oba przebiegi sprawdziły status COMPLETED, liczbę wierszy, sumy i liczbę linii wyniku (69 088 121, wynik 5 984 973 842 B). Kontener nie został zabity (`OOMKilled=false`). Przetwarzanie ogranicza CPU jednego workera, a upload i pobranie w `compose` ogranicza przekierowanie portów i dysk VM Docker Desktop. Odtworzenie: `scripts/bench.sh --size 5GiB` (tryb `local`) albo `docker compose up -d --build && scripts/bench.sh --size 5GiB --mode compose`. Generator danych: `scripts/generate-csv.sh`.

## 6. Bezpieczeństwo

| Zagrożenie | Obrona |
|---|---|
| Dostęp bez logowania | każdy endpoint `/api/v1/**` poza `GET /auth/me` i `POST /auth/login` wymaga ważnego JWT; wszystko spoza listy publicznej jest odrzucane |
| Kradzież tokenu | JWT (HS256, klucz spoza repozytorium) w ciasteczku `HttpOnly`, `SameSite=Lax`, nigdy w `localStorage`; ważny 8 h, bez odnawiania; filtr sprawdza podpis, `iss`, `exp` i to, czy konto nadal jest w konfiguracji; `alg=none` odrzucany |
| CSRF | ciasteczko `XSRF-TOKEN` + nagłówek `X-XSRF-TOKEN` na każdym żądaniu zmieniającym stan |
| Cudze zadania (IDOR) | właściciel jest warunkiem samego zapytania (`WHERE id = :id AND owner = :owner`); cudze zadanie daje 404 |
| Zgadywanie haseł | identyczne 401 dla nieznanego konta i złego hasła, bcrypt liczony także dla nieznanej nazwy; limit prób na proxy (niżej) |
| Zalewanie dużymi plikami | limit 5 GiB przed odczytem i w strumieniu, sprawdzenie wolnego miejsca (507) |
| Path traversal, CSV injection | ścieżki wyłącznie z UUID, `fileName` to tylko metadana; każde pole przechodzi allow-listę liczbową albo UUID |
| Wyciek informacji | stałe komunikaty błędów, logi bez treści wierszy i haseł, Actuator wystawia tylko `health` |
| Przeglądarka | CSP `default-src 'self'` bez `unsafe-inline`, `frame-ancestors 'none'`, `nosniff`, `Referrer-Policy: same-origin`, `Cache-Control: no-store` na API |
| Kontener | użytkownik bez uprawnień root, obraz JRE, pliki zadań `rw-------` |

**Świadome uproszczenia:**

- **Wylogowanie nie unieważnia tokenu na serwerze.** Kasuje ciasteczko, ale skopiowany wcześniej token działa do końca swoich 8 h. Lista unieważnionych tokenów oznaczałaby zapytanie do bazy przy każdym żądaniu, czyli sesję serwerową z dodatkowymi krokami. Gdy natychmiastowe unieważnienie stanie się wymaganiem, prościej przejść na sesję (Spring Session JDBC).
- **Bez limitów żądań w aplikacji.** Limit prób logowania i uploadów należy do proxy albo ingressu (`limit_req` w nginx, adnotacje ingressu w Kubernetes), gdzie działa wspólnie dla wszystkich instancji. Dysk aplikacja chroni sama: limit 5 GiB i 507 przy braku miejsca.
- **Bez rotacji tokenu CSRF przy logowaniu.** Token wydany przez `GET /auth/me` obowiązuje do wylogowania, które go kasuje; klient od razu pobiera nowy tym samym `GET /auth/me`.

## 7. Znane ograniczenia i kolejne kroki

- **Jedna instancja.** Odzysk przy starcie zakłada, że żaden inny proces nie przetwarza zadań ani nie zapisuje plików na tym samym wolumenie. Przy wielu instancjach: heartbeat z numerem próby jako tokenem fencingu, sprzątanie sierot tylko dla katalogów starszych niż najdłuższy upload i wspólny `JWT_SECRET`.
- **Wolni klienci.** Nie ma limitu równoległych uploadów ani minimalnej przepustowości: bezczynne połączenie zamyka Tomcat po 60 s, a limit liczby uploadów należy do proxy.
- **Brak TTL wyników.** Wynik COMPLETED żyje do ręcznego usunięcia; pliki osierocone przez awarię sprząta odzysk przy starcie.
- **Za reverse proxy** trzeba ustawić zaufane proxy (`server.forward-headers-strategy=native`), inaczej logi logowania pokażą adres proxy zamiast klienta; dla nginx także `client_max_body_size 5121m`, `proxy_request_buffering off` i `limit_req` dla `/api/v1/auth/login` oraz `POST /api/v1/jobs`.
- **Konta tylko z konfiguracji**, bez rejestracji i zmiany hasła.
- **Upload 5 GiB z przeglądarki** nie był testowany ręcznie; referencyjną ścieżką dla dużych plików jest `curl -T`.

## 8. Struktura repozytorium

```
client/                 React 19 + TypeScript + Vite 8 + Tailwind CSS 4
server/                 Java 25 + Spring Boot 4.1.1 + Maven Wrapper
  src/main/resources/db/migration/   V1 (zadania)
  src/main/resources/application.yml (bez kont), application-demo.yml (alice, bob jako {bcrypt})
scripts/                generator CSV dowolnego rozmiaru i benchmark
Dockerfile              obraz wieloetapowy (klient → serwer z SPA → JRE)
docker-compose.yml      postgres:18-alpine + aplikacja
.github/workflows/      CI: server, client, image
sample_ads_data.csv     próbka z zadania
```
