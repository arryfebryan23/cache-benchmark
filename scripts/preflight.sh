#!/usr/bin/env bash
#
# Checks the benchmark VM is ready before a run (PRD section 63).
#
#   ./scripts/preflight.sh
#   REDIS_HOST=10.10.10.11 HZ_HOST=10.10.10.21 ./scripts/preflight.sh
#
set -uo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR="${JAR:-$PROJECT_ROOT/target/cache-benchmark.jar}"
RESULTS_DIR="${RESULTS_DIR:-$PROJECT_ROOT/results}"

REDIS_HOST="${REDIS_HOST:-}"
REDIS_PORT="${REDIS_PORT:-6379}"
HZ_HOST="${HZ_HOST:-}"
HZ_PORT="${HZ_PORT:-5701}"

failures=0

pass() { printf '  OK    %s\n' "$1"; }
fail() { printf '  FAIL  %s\n' "$1"; failures=$((failures + 1)); }
warn() { printf '  WARN  %s\n' "$1"; }

echo "Preflight checks"
echo "----------------------------------------------------------------"

# Java present
if ! command -v java >/dev/null 2>&1; then
  fail "java not found on PATH. RHEL 9: sudo dnf install -y java-17-openjdk java-17-openjdk-devel"
else
  raw_version="$(java -version 2>&1 | head -1)"
  # Handles both "17.0.9" and legacy "1.8.0_411"
  version="$(printf '%s' "$raw_version" | sed -n 's/.*version "\([^"]*\)".*/\1/p')"
  major="$(printf '%s' "$version" | awk -F'[._-]' '{ if ($1 == 1) print $2; else print $1 }')"
  if [[ -z "$major" ]]; then
    warn "could not parse the java version from: $raw_version"
  elif (( major < 17 )); then
    fail "java $version found, 17 or newer is required"
  else
    pass "java $version"
  fi
fi

# JAR present
if [[ -f "$JAR" ]]; then
  pass "jar present: $JAR"
else
  fail "jar missing: $JAR  (run ./scripts/build.sh)"
fi

# Results directory writable
mkdir -p "$RESULTS_DIR" 2>/dev/null
if [[ -d "$RESULTS_DIR" && -w "$RESULTS_DIR" ]]; then
  pass "results directory writable: $RESULTS_DIR"
else
  fail "results directory not writable: $RESULTS_DIR"
fi

# Optional reachability checks. netcat is not a hard dependency (PRD section 63).
check_port() {
  local label="$1" host="$2" port="$3"
  if [[ -z "$host" ]]; then
    return
  fi
  if command -v nc >/dev/null 2>&1; then
    if nc -z -w 3 "$host" "$port" >/dev/null 2>&1; then
      pass "$label reachable at $host:$port"
    else
      fail "$label not reachable at $host:$port"
    fi
  elif command -v timeout >/dev/null 2>&1; then
    if timeout 3 bash -c "exec 3<>/dev/tcp/$host/$port" 2>/dev/null; then
      pass "$label reachable at $host:$port"
    else
      fail "$label not reachable at $host:$port"
    fi
  else
    warn "$label reachability not checked: neither nc nor timeout available"
  fi
}

check_port "redis" "$REDIS_HOST" "$REDIS_PORT"
check_port "hazelcast" "$HZ_HOST" "$HZ_PORT"

if [[ -z "$REDIS_HOST" && -z "$HZ_HOST" ]]; then
  warn "backend reachability not checked. Set REDIS_HOST and/or HZ_HOST to include it."
fi

echo "----------------------------------------------------------------"
if (( failures > 0 )); then
  echo "$failures check(s) failed."
  exit 1
fi
echo "All checks passed."
