#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GENERATOR="$ROOT/server/src/test/java/pl/oliwier/adscsv/support/AdsCsvGenerator.java"
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
REQUIRED_JAVA=25

usage() {
  cat >&2 <<USAGE
Generuje plik CSV w formacie próbki (deterministycznie, tylko JDK).

Użycie: $(basename "$0") --size <n>[B|KiB|MiB|GiB] --out <plik> [--seed <long>] [--invalid-every <n>]

  --size           docelowy rozmiar; plik kończy się na tej granicy lub tuż przed nią
  --out            ścieżka pliku wynikowego
  --seed           ziarno generatora (domyślnie 42)
  --invalid-every  co który wiersz ma być niepoprawny (domyślnie 0 = brak)

Na końcu wypisuje raport: bytes, rows, validRows, invalidRows, totalImpressions, totalClicks, totalCtr.
Wymaga JDK $REQUIRED_JAVA+ (uruchamia generator przez source launcher, bez kompilacji projektu).
USAGE
}

java_feature_version() {
  "$JAVA" -XshowSettings:properties -version 2>&1 \
    | awk -F'= ' '/java.specification.version/ { split($2, v, "."); print v[1]; exit }'
}

main() {
  if [[ $# -eq 0 || "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
    usage
    exit 2
  fi
  if ! command -v "$JAVA" >/dev/null 2>&1; then
    echo "Nie znaleziono polecenia java (ustaw JAVA_HOME na JDK $REQUIRED_JAVA+)." >&2
    exit 1
  fi
  local version
  version="$(java_feature_version)"
  if [[ -z "$version" || "$version" -lt "$REQUIRED_JAVA" ]]; then
    echo "Wymagany JDK $REQUIRED_JAVA+, znaleziono: ${version:-nieznany} ($JAVA)." >&2
    exit 1
  fi
  exec "$JAVA" "$GENERATOR" "$@"
}

main "$@"
