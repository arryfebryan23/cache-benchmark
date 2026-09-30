#!/usr/bin/env bash
#
# Runs one benchmark with the recommended JVM settings for a 4 vCPU / 16 GB
# VM (PRD section 7). Every argument is passed through to the application.
#
#   ./scripts/run.sh --target redis     --operation GET --threads 16
#   ./scripts/run.sh --target hazelcast --operation GET --threads 16
#
# Override the heap with JVM_OPTS if you need to.
#
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"

JAR="${JAR:-$PROJECT_ROOT/target/cache-benchmark.jar}"
JVM_OPTS="${JVM_OPTS:--Xms2g -Xmx4g}"
CONFIG="${CONFIG:-$PROJECT_ROOT/config/benchmark.yaml}"

if [[ ! -f "$JAR" ]]; then
  echo "ERROR: $JAR not found. Run ./scripts/build.sh first." >&2
  exit 1
fi

CONFIG_ARGS=()
if [[ -f "$CONFIG" ]]; then
  CONFIG_ARGS=(--config "$CONFIG")
fi

# shellcheck disable=SC2086
exec java $JVM_OPTS -jar "$JAR" "${CONFIG_ARGS[@]}" "$@"
