#!/usr/bin/env bash
set -euo pipefail

HOST="${REDIS_HOST:-127.0.0.1}"
PORT="${REDIS_PORT:-6379}"
PASSWORD="${REDIS_PASSWORD:-}"
COUNT="${COUNT:-20000}"
BATCH="${BATCH:-500}"
PREFIX="${PREFIX:-fota:device:bench}"

if ! command -v redis-cli >/dev/null 2>&1; then
  printf "redis-cli not found\n" >&2
  exit 1
fi

redis_cmd() {
  if [[ -n "$PASSWORD" ]]; then
    redis-cli -h "$HOST" -p "$PORT" -a "$PASSWORD" "$@"
  else
    redis-cli -h "$HOST" -p "$PORT" "$@"
  fi
}

redis_pipe() {
  if [[ -n "$PASSWORD" ]]; then
    redis-cli -h "$HOST" -p "$PORT" -a "$PASSWORD" --pipe
  else
    redis-cli -h "$HOST" -p "$PORT" --pipe
  fi
}

populate() {
  seq 1 "$COUNT" | awk -v p="$PREFIX" '{printf "SET %s:%d 1 EX 86400\n", p, $1}' | redis_pipe >/dev/null
}

clean_all() {
  seq 1 "$COUNT" | awk -v p="$PREFIX" '{printf "DEL %s:%d\n", p, $1}' | redis_pipe >/dev/null
}

bench_collection() {
  local start end i j
  start=$(date +%s%N)
  for ((i=1; i<=COUNT; i+=BATCH)); do
    local keys=()
    for ((j=i; j<=COUNT && j<i+BATCH; j++)); do
      keys+=("$PREFIX:$j")
    done
    redis_cmd DEL "${keys[@]}" >/dev/null
  done
  end=$(date +%s%N)
  printf "%d" $(((end - start) / 1000000))
}

bench_pipeline() {
  local start end i j
  start=$(date +%s%N)
  for ((i=1; i<=COUNT; i+=BATCH)); do
    for ((j=i; j<=COUNT && j<i+BATCH; j++)); do
      printf "DEL %s:%d\n" "$PREFIX" "$j"
    done | redis_pipe >/dev/null
  done
  end=$(date +%s%N)
  printf "%d" $(((end - start) / 1000000))
}

clean_all
populate
collection_ms=$(bench_collection)
populate
pipeline_ms=$(bench_pipeline)
clean_all

printf "count=%s batch=%s collection_ms=%s pipeline_ms=%s\n" "$COUNT" "$BATCH" "$collection_ms" "$pipeline_ms"
