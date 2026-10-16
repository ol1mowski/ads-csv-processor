#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
JFR="${JAVA_HOME:+$JAVA_HOME/bin/}jfr"
COMPOSE_FILE="$ROOT/docker-compose.yml"
BENCH_DB="adscsv_bench"

SIZE="5GiB"
MODE="local"
KEEP="false"
CANCEL="true"
HEAP="${BENCH_HEAP:-256m}"
PORT="${BENCH_PORT:-18080}"
OUT_DIR="${BENCH_DIR:-$ROOT/data/bench/$(date +%Y%m%d-%H%M%S)}"
BENCH_USER="${BENCH_USER:-alice}"
BENCH_PASSWORD="${BENCH_PASSWORD:-alice-demo-pass}"
POLL_INTERVAL="0.25"

API=""
APP_PID=""
RSS_SAMPLER_PID=""
CONTAINER_SAMPLER_PID=""
CONTAINER_ID=""
CREATED_DB="false"

usage() {
  cat >&2 <<USAGE
Benchmark przetwarzania dużego pliku CSV: upload (curl -T), przetwarzanie, pobranie wyniku, anulowanie w połowie.

Użycie: $(basename "$0") [--size 5GiB] [--mode local|compose] [--keep] [--no-cancel]

  --size       rozmiar generowanego pliku (B|KiB|MiB|GiB), domyślnie 5GiB
  --mode       local   (domyślnie) uruchamia jar z -Xmx\$BENCH_HEAP (256m) i JFR na porcie \$BENCH_PORT (18080);
                       baza: \$DB_URL, a gdy brak - Postgres z docker compose (osobna baza $BENCH_DB)
               compose mierzy działający stack z docker-compose.yml na \$BENCH_API (http://localhost:8080)
  --keep       nie kasuj wygenerowanego wejścia, pobranego wyniku ani katalogu storage
  --no-cancel  pomiń drugi przebieg z anulowaniem przy ~50 %

Wyniki i artefakty (JFR, log aplikacji, odpowiedzi JSON) trafiają do \$BENCH_DIR (domyślnie data/bench/<czas>).
Logowanie: \$BENCH_USER / \$BENCH_PASSWORD (domyślnie alice / alice-demo-pass z profilu demo);
tryb local uruchamia jar z profilem demo.
Wymaga: JDK 25+ (java, jfr), curl, jq; docker tylko dla bazy z compose albo trybu compose.
USAGE
}

log() {
  printf '[bench] %s\n' "$*" >&2
}

die() {
  log "BŁĄD: $*"
  exit 1
}

parse_args() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --size) SIZE="${2:?brak wartości dla --size}"; shift 2 ;;
      --mode) MODE="${2:?brak wartości dla --mode}"; shift 2 ;;
      --keep) KEEP="true"; shift ;;
      --no-cancel) CANCEL="false"; shift ;;
      -h | --help) usage; exit 0 ;;
      *) usage; exit 2 ;;
    esac
  done
  case "$MODE" in
    local | compose) ;;
    *) die "nieznany tryb: $MODE" ;;
  esac
}

require() {
  local tool
  for tool in "$@"; do
    command -v "$tool" >/dev/null 2>&1 || die "brak narzędzia: $tool"
  done
}

uses_compose_postgres() {
  [[ "$MODE" == "local" && -z "${DB_URL:-}" ]]
}

compose() {
  docker compose -f "$COMPOSE_FILE" "$@"
}

now() {
  jq -n 'now'
}

two_decimals() {
  LC_ALL=C awk -v value="$1" 'BEGIN { printf "%.2f", value }'
}

elapsed() {
  LC_ALL=C awk -v from="$1" -v to="$2" 'BEGIN { printf "%.2f", to - from }'
}

within_seconds() {
  LC_ALL=C awk -v from="$1" -v now="$(now)" -v limit="$2" 'BEGIN { exit !(now - from < limit) }'
}

mib() {
  LC_ALL=C awk -v bytes="$1" 'BEGIN { printf "%.0f", bytes / 1048576 }'
}

throughput() {
  LC_ALL=C awk -v bytes="$1" -v seconds="$2" 'BEGIN { if (seconds <= 0) { print "-" } else { printf "%.0f", bytes / 1048576 / seconds } }'
}

iso_millis() {
  jq -r "$1"' | capture("^(?<s>[^.Z]+)(\\.(?<f>[0-9]+))?Z$") | ((.s + "Z" | fromdateiso8601) * 1000 + ((.f // "0") + "000" | .[0:3] | tonumber))'
}

machine() {
  local cpu memory
  if [[ "$(uname -s)" == "Darwin" ]]; then
    cpu="$(sysctl -n machdep.cpu.brand_string), $(sysctl -n hw.ncpu) CPU"
    memory="$(( $(sysctl -n hw.memsize) / 1073741824 )) GiB RAM"
  else
    cpu="$(awk -F': ' '/model name/ { print $2; exit }' /proc/cpuinfo), $(nproc) CPU"
    memory="$(awk '/MemTotal/ { printf "%d GiB RAM", $2 / 1048576 }' /proc/meminfo)"
  fi
  printf '%s %s, %s, %s' "$(uname -s)" "$(uname -m)" "$cpu" "$memory"
}

java_version() {
  if [[ "$MODE" == "local" ]]; then
    "$JAVA" -XshowSettings:properties -version 2>&1
  else
    compose exec -T app java -XshowSettings:properties -version 2>&1
  fi | awk -F'= ' '/java.runtime.version/ { print $2; exit }'
}

runtime() {
  if [[ "$MODE" == "local" ]]; then
    printf 'java -Xmx%s na hoście' "$HEAP"
  else
    docker info --format '{{.OperatingSystem}}, VM {{.NCPU}} CPU, {{.MemTotal}} B RAM; JAVA_TOOL_OPTIONS z docker-compose.yml'
  fi
}

generate_input() {
  log "Generuję $SIZE do $OUT_DIR/input.csv"
  "$ROOT/scripts/generate-csv.sh" --size "$SIZE" --seed 42 --out "$OUT_DIR/input.csv" \
    | tee "$OUT_DIR/generator.txt" >&2
  REPORT="$(grep '^bytes=' "$OUT_DIR/generator.txt")"
  INPUT_BYTES="$(report_value bytes)"
  INPUT_ROWS="$(report_value validRows)"
  INPUT_IMPRESSIONS="$(report_value totalImpressions)"
  INPUT_CLICKS="$(report_value totalClicks)"
}

report_value() {
  printf '%s\n' "$REPORT" | tr ' ' '\n' | awk -F= -v key="$1" '$1 == key { print $2 }'
}

prepare_database() {
  log "Uruchamiam Postgresa z docker compose i zakładam bazę $BENCH_DB"
  compose up -d --wait postgres >&2
  compose exec -T postgres psql -U adscsv -d adscsv -v ON_ERROR_STOP=1 -q \
    -c "DROP DATABASE IF EXISTS $BENCH_DB WITH (FORCE)" \
    -c "CREATE DATABASE $BENCH_DB" >&2
  CREATED_DB="true"
  export DB_URL="jdbc:postgresql://localhost:5433/$BENCH_DB"
  export DB_USER="${DB_USER:-adscsv}"
  export DB_PASSWORD="${POSTGRES_PASSWORD:-adscsv}"
}

find_jar() {
  local jar
  for jar in "$ROOT"/server/target/ads-csv-processor-*.jar; do
    [[ -f "$jar" ]] && printf '%s' "$jar" && return 0
  done
  return 1
}

build_jar() {
  log "Buduję jar serwera z bieżących źródeł (bez testów)"
  (cd "$ROOT/server" && ./mvnw -B -ntp -q -Dmaven.test.skip=true package) >&2
  JAR="$(find_jar)" || die "nie znaleziono jara w server/target"
}

start_local_app() {
  API="http://localhost:$PORT"
  mkdir -p "$OUT_DIR/storage"
  log "Startuję aplikację: -Xmx$HEAP, JFR, port $PORT"
  APP_STORAGE_ROOT="$OUT_DIR/storage" SERVER_PORT="$PORT" SPRING_PROFILES_ACTIVE=demo \
    "$JAVA" "-Xmx$HEAP" -XX:+ExitOnOutOfMemoryError \
    "-XX:StartFlightRecording=filename=$OUT_DIR/app.jfr,dumponexit=true" \
    -Dspring.output.ansi.enabled=never \
    -jar "$JAR" >"$OUT_DIR/app.log" 2>&1 &
  APP_PID=$!
  sample_rss "$APP_PID" "$OUT_DIR/rss-max-kb" &
  RSS_SAMPLER_PID=$!
}

sample_rss() {
  local pid="$1" file="$2" max=0 rss
  while kill -0 "$pid" 2>/dev/null; do
    rss="$(ps -o rss= -p "$pid" 2>/dev/null | tr -d ' ' || true)"
    if [[ -n "$rss" && "$rss" -gt "$max" ]]; then
      max="$rss"
      printf '%s\n' "$max" >"$file"
    fi
    sleep 0.2
  done
}

attach_compose_app() {
  API="${BENCH_API:-http://localhost:8080}"
  CONTAINER_ID="$(compose ps -q app)"
  [[ -n "$CONTAINER_ID" ]] || die "stack nie działa: uruchom najpierw 'docker compose up -d --build'"
  touch "$OUT_DIR/.sampling"
  sample_container_memory "$CONTAINER_ID" "$OUT_DIR/container-max-mib" &
  CONTAINER_SAMPLER_PID=$!
}

sample_container_memory() {
  local container="$1" file="$2" max=0 usage
  while [[ -f "$OUT_DIR/.sampling" ]]; do
    usage="$(docker stats --no-stream --format '{{.MemUsage}}' "$container" 2>/dev/null \
      | LC_ALL=C awk '{
          value = $1; unit = value; sub(/[A-Za-z]+$/, "", value); sub(/^[0-9.]+/, "", unit);
          factor = unit == "GiB" ? 1024 : unit == "MiB" ? 1 : unit == "KiB" ? 1 / 1024 : 1 / 1048576;
          printf "%.0f", value * factor }' || true)"
    if [[ -n "$usage" && "$usage" -gt "$max" ]]; then
      max="$usage"
      printf '%s\n' "$max" >"$file"
    fi
  done
}

wait_for_health() {
  local deadline=$(( $(date +%s) + 120 ))
  until curl -fsS "$API/actuator/health" 2>/dev/null | grep -q '"UP"'; do
    if [[ -n "$APP_PID" ]] && ! kill -0 "$APP_PID" 2>/dev/null; then
      tail -40 "$OUT_DIR/app.log" >&2 || true
      die "aplikacja zakończyła się przed startem"
    fi
    [[ $(date +%s) -lt $deadline ]] || die "aplikacja nie odpowiada na $API/actuator/health"
    sleep 1
  done
}

cookie_jar() {
  printf '%s' "$OUT_DIR/cookies.txt"
}

csrf_token() {
  awk '$6 == "XSRF-TOKEN" { token = $7 } END { print token }' "$(cookie_jar)"
}

api() {
  curl -b "$(cookie_jar)" -c "$(cookie_jar)" -H "X-XSRF-TOKEN: $(csrf_token)" "$@"
}

login() {
  local credentials code
  : >"$(cookie_jar)"
  code="$(api -sS -o /dev/null -w '%{http_code}' "$API/api/v1/auth/me")"
  [[ "$code" == "200" || "$code" == "401" ]] || die "GET /api/v1/auth/me zwróciło $code"
  [[ -n "$(csrf_token)" ]] || die "serwer nie wydał ciasteczka XSRF-TOKEN (GET /api/v1/auth/me zwróciło $code)"
  credentials="$(jq -n --arg u "$BENCH_USER" --arg p "$BENCH_PASSWORD" '{username: $u, password: $p}')"
  code="$(api -sS -o "$OUT_DIR/login.json" -w '%{http_code}' \
    -H 'Content-Type: application/json' --data "$credentials" "$API/api/v1/auth/login")"
  [[ "$code" == "200" ]] || die "logowanie jako $BENCH_USER zwróciło $code: $(cat "$OUT_DIR/login.json")"
  log "Zalogowano jako $BENCH_USER"
}

upload() {
  local name="$1" label="$2"
  api -sS -X POST -T "$OUT_DIR/input.csv" -H 'Content-Type: text/csv' \
    -o "$OUT_DIR/$label-upload.json" \
    -w '%{http_code} %{time_total} %{size_upload}' \
    "$API/api/v1/jobs?fileName=$name"
}

job_status() {
  local code
  code="$(api -sS -o "$OUT_DIR/status.json" -w '%{http_code}' "$API/api/v1/jobs/$1")"
  [[ "$code" == "200" ]] || die "status zadania $1 zwrócił $code: $(cat "$OUT_DIR/status.json")"
  cat "$OUT_DIR/status.json"
}

wait_until_finished() {
  local id="$1" file="$2" json status since
  since="$(now)"
  while within_seconds "$since" 3600; do
    json="$(job_status "$id")"
    status="$(jq -r .status <<<"$json")"
    case "$status" in
      COMPLETED | FAILED | CANCELLED) printf '%s\n' "$json" >"$file"; return 0 ;;
    esac
    sleep 0.5
  done
  die "zadanie $id nie skończyło się w ciągu godziny"
}

job_directory_exists() {
  if [[ "$MODE" == "local" ]]; then
    [[ -d "$OUT_DIR/storage/jobs/$1" ]]
  else
    compose exec -T app test -d "/data/jobs/$1"
  fi
}

run_full_pass() {
  log "Przebieg 1: upload → przetwarzanie → pobranie wyniku"
  local started upload_result code id finished result_url download_result
  started="$(now)"
  upload_result="$(upload bench.csv full)"
  read -r code UPLOAD_SECONDS UPLOAD_BYTES <<<"$upload_result"
  [[ "$code" == "202" ]] || die "upload zwrócił $code: $(cat "$OUT_DIR/full-upload.json")"
  id="$(jq -r .id "$OUT_DIR/full-upload.json")"
  wait_until_finished "$id" "$OUT_DIR/full-status.json"
  finished="$(now)"
  WALL_SECONDS="$(elapsed "$started" "$finished")"
  verify_completed "$OUT_DIR/full-status.json"
  PROCESSING_SECONDS="$(LC_ALL=C awk -v from="$(iso_millis .startedAt <"$OUT_DIR/full-status.json")" \
    -v to="$(iso_millis .finishedAt <"$OUT_DIR/full-status.json")" 'BEGIN { printf "%.2f", (to - from) / 1000 }')"
  result_url="$(jq -r .resultUrl "$OUT_DIR/full-status.json")"
  download_result="$(api -sS -o "$OUT_DIR/result.csv" -w '%{http_code} %{time_total} %{size_download}' "$API$result_url")"
  read -r code DOWNLOAD_SECONDS OUTPUT_BYTES <<<"$download_result"
  [[ "$code" == "200" ]] || die "pobranie wyniku zwróciło $code"
  OUTPUT_LINES="$(wc -l <"$OUT_DIR/result.csv" | tr -d ' ')"
  [[ "$OUTPUT_LINES" -eq $(( INPUT_ROWS + 1 )) ]] || die "wynik ma $OUTPUT_LINES linii, oczekiwano $(( INPUT_ROWS + 1 ))"
  [[ "$(head -1 "$OUT_DIR/result.csv")" == *";ctr" ]] || die "nagłówek wyniku nie kończy się ;ctr"
}

verify_completed() {
  local file="$1"
  [[ "$(jq -r .status "$file")" == "COMPLETED" ]] || die "zadanie skończyło się statusem $(jq -c '{status, failureReason}' "$file")"
  [[ "$(jq -r .rowsProcessed "$file")" == "$INPUT_ROWS" ]] || die "rowsProcessed różni się od raportu generatora"
  [[ "$(jq -r .totals.impressions "$file")" == "$INPUT_IMPRESSIONS" ]] || die "suma impressions różni się od raportu generatora"
  [[ "$(jq -r .totals.clicks "$file")" == "$INPUT_CLICKS" ]] || die "suma clicks różni się od raportu generatora"
}

run_cancel_pass() {
  log "Przebieg 2: anulowanie przy ~50 %"
  local upload_result code id json status percent cancelled_at gone_at
  upload_result="$(upload bench-cancel.csv cancel)"
  read -r code _ _ <<<"$upload_result"
  [[ "$code" == "202" ]] || die "upload zwrócił $code"
  id="$(jq -r .id "$OUT_DIR/cancel-upload.json")"
  while true; do
    json="$(job_status "$id")"
    status="$(jq -r .status <<<"$json")"
    percent="$(jq -r .progressPercent <<<"$json")"
    if [[ "$status" != "QUEUED" && "$status" != "PROCESSING" ]]; then
      CANCEL_SUMMARY="pominięte: zadanie skończyło się ($status), zanim doszło do 50 %"
      return 0
    fi
    [[ "$status" == "PROCESSING" && "$percent" -ge 50 ]] && break
    sleep "$POLL_INTERVAL"
  done
  cancelled_at="$(now)"
  code="$(api -sS -X POST -o "$OUT_DIR/cancel-response.json" -w '%{http_code}' "$API/api/v1/jobs/$id/cancel")"
  [[ "$code" == "200" && "$(jq -r .status "$OUT_DIR/cancel-response.json")" == "CANCELLED" ]] \
    || die "anulowanie zwróciło $code: $(cat "$OUT_DIR/cancel-response.json")"
  while job_directory_exists "$id"; do
    within_seconds "$cancelled_at" 30 || die "katalog zadania nie zniknął w 30 s"
    sleep 0.1
  done
  gone_at="$(now)"
  CANCEL_SUMMARY="przy $percent %, status CANCELLED, katalog zadania usunięty po $(elapsed "$cancelled_at" "$gone_at") s"
}

stop_local_app() {
  [[ -n "$APP_PID" ]] || return 0
  log "Zatrzymuję aplikację (SIGTERM, zrzut JFR)"
  kill -TERM "$APP_PID" 2>/dev/null || true
  wait "$APP_PID" 2>/dev/null || true
  APP_PID=""
  [[ -z "$RSS_SAMPLER_PID" ]] || wait "$RSS_SAMPLER_PID" 2>/dev/null || true
  RSS_SAMPLER_PID=""
}

stop_container_sampler() {
  [[ -n "$CONTAINER_SAMPLER_PID" ]] || return 0
  rm -f "$OUT_DIR/.sampling"
  wait "$CONTAINER_SAMPLER_PID" 2>/dev/null || true
  CONTAINER_SAMPLER_PID=""
}

heap_summary() {
  "$JFR" print --json --events jdk.GCHeapSummary "$OUT_DIR/app.jfr" \
    | jq -r '[.recording.events[].values] as $events
      | def peak($when): [$events[] | select(.when == $when) | .heapUsed] | max // 0 | . / 1048576 | round;
      "\(peak("Before GC")) MiB przed GC, \(peak("After GC")) MiB po GC"'
}

print_summary() {
  local memory heap
  if [[ "$MODE" == "local" ]]; then
    heap="$(heap_summary) (JFR jdk.GCHeapSummary, -Xmx$HEAP)"
    memory="$(( $(cat "$OUT_DIR/rss-max-kb" 2>/dev/null || echo 0) / 1024 )) MiB (maks. RSS procesu, ps)"
  else
    heap="n/d (obraz JRE nie ma jcmd/jfr; limit z JAVA_TOOL_OPTIONS w compose)"
    memory="$(cat "$OUT_DIR/container-max-mib" 2>/dev/null || echo 0) MiB (maks. pamięć kontenera, docker stats)"
  fi
  cat <<SUMMARY

== Benchmark ($MODE) ==
maszyna:          $(machine)
środowisko:       $(runtime)
JDK:              $(java_version)
plik:             $INPUT_BYTES B ($(mib "$INPUT_BYTES") MiB), $INPUT_ROWS wierszy
upload:           $(two_decimals "$UPLOAD_SECONDS") s, $(throughput "$UPLOAD_BYTES" "$UPLOAD_SECONDS") MiB/s (curl -T, do odpowiedzi 202)
przetwarzanie:    $PROCESSING_SECONDS s, $(throughput "$INPUT_BYTES" "$PROCESSING_SECONDS") MiB/s (startedAt → finishedAt)
upload → wynik:   $WALL_SECONDS s (od startu uploadu do statusu COMPLETED)
pobranie wyniku:  $(two_decimals "$DOWNLOAD_SECONDS") s, $(throughput "$OUTPUT_BYTES" "$DOWNLOAD_SECONDS") MiB/s, $OUTPUT_BYTES B, $OUTPUT_LINES linii (wiersze + nagłówek)
weryfikacja:      COMPLETED, rowsProcessed i sumy zgodne z raportem generatora
szczyt sterty:    $heap
pamięć:           $memory
anulowanie:       $CANCEL_SUMMARY
artefakty:        $OUT_DIR
SUMMARY
}

cleanup() {
  local status=$?
  stop_container_sampler
  stop_local_app
  if [[ "$KEEP" != "true" ]]; then
    rm -rf "$OUT_DIR/input.csv" "$OUT_DIR/result.csv" "$OUT_DIR/storage"
  fi
  rm -f "$(cookie_jar)"
  if [[ "$CREATED_DB" == "true" ]]; then
    compose exec -T postgres psql -U adscsv -d adscsv -q \
      -c "DROP DATABASE IF EXISTS $BENCH_DB WITH (FORCE)" >/dev/null 2>&1 || true
  fi
  exit "$status"
}

main() {
  parse_args "$@"
  require curl jq awk
  if [[ "$MODE" == "local" ]]; then
    require "$JAVA" "$JFR"
  fi
  if [[ "$MODE" == "compose" ]] || uses_compose_postgres; then
    require docker
  fi
  mkdir -p "$OUT_DIR"
  CANCEL_SUMMARY="pominięte (--no-cancel)"
  trap cleanup EXIT
  trap 'exit 130' INT TERM
  generate_input
  if [[ "$MODE" == "local" ]]; then
    uses_compose_postgres && prepare_database
    build_jar
    start_local_app
  else
    attach_compose_app
  fi
  wait_for_health
  login
  run_full_pass
  rm -f "$OUT_DIR/result.csv"
  [[ "$CANCEL" == "true" ]] && run_cancel_pass
  stop_container_sampler
  stop_local_app
  print_summary | tee "$OUT_DIR/summary.txt"
}

main "$@"
