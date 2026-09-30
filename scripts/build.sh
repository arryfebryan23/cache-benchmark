#!/usr/bin/env bash
#
# Builds the executable fat JAR (PRD section 53).
#
#   ./scripts/build.sh          build and run unit tests
#   ./scripts/build.sh --skip-tests
#
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"

MAVEN_ARGS=(clean package)
if [[ "${1:-}" == "--skip-tests" ]]; then
  MAVEN_ARGS+=(-DskipTests)
fi

if ! command -v mvn >/dev/null 2>&1; then
  echo "ERROR: maven not found. On RHEL 9: sudo dnf install -y maven" >&2
  exit 1
fi

mvn "${MAVEN_ARGS[@]}"

JAR="$PROJECT_ROOT/target/cache-benchmark.jar"
if [[ ! -f "$JAR" ]]; then
  echo "ERROR: build finished but $JAR is missing" >&2
  exit 1
fi

echo
echo "Built: $JAR"
java -jar "$JAR" --version
