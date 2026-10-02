#!/usr/bin/env bash
#
# Runs the benchmark matrix (PRD sections 46, 47, 48, 49).
#
# Every combination gets a fresh JVM, which is deliberate: five runs inside one
# JVM would share a warmed-up JIT and a warmed-up heap, and the later runs
# would not be comparable with the first.
#
# Defaults reproduce the recommended matrix:
#   2 targets x 2 operations x 3 payloads x 7 thread counts x 5 repeats
#
# Override anything with environment variables:
#
#   TARGETS="redis hazelcast" \
#   OPERATIONS="GET SET" \
#   SET_PERCENTS="10 20 50" \
#   PAYLOADS="100 1024 10240" \
#   THREADS="1 2 4 8 16 32 64" \
#   REPEATS=5 \
#   DURATION=60 WARMUP=30 KEYS=1000000 \
#   COOLDOWN_SECONDS=10 \
#   ./scripts/run-matrix.sh
#
# SET_PERCENTS only applies when OPERATIONS contains MIXED: every MIXED cell
# is run once per listed SET share. GET and SET cells ignore it.
#
#   OPERATIONS="MIXED" SET_PERCENTS="10 20 50" ./scripts/run-matrix.sh
#
set -uo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"

JAR="${JAR:-$PROJECT_ROOT/target/cache-benchmark.jar}"
CONFIG="${CONFIG:-$PROJECT_ROOT/config/benchmark.yaml}"
JVM_OPTS="${JVM_OPTS:--Xms2g -Xmx4g}"

TARGETS="${TARGETS:-redis hazelcast}"
OPERATIONS="${OPERATIONS:-GET SET}"
SET_PERCENTS="${SET_PERCENTS:-50}"
PAYLOADS="${PAYLOADS:-100 1024 10240}"
THREADS="${THREADS:-1 2 4 8 16 32 64}"
REPEATS="${REPEATS:-5}"

KEYS="${KEYS:-1000000}"
WARMUP="${WARMUP:-30}"
DURATION="${DURATION:-60}"
SEED="${SEED:-123456}"
PRELOAD="${PRELOAD:-true}"
COOLDOWN_SECONDS="${COOLDOWN_SECONDS:-10}"
OUTPUT_DIR="${OUTPUT_DIR:-$PROJECT_ROOT/results}"

if [[ ! -f "$JAR" ]]; then
  echo "ERROR: $JAR not found. Run ./scripts/build.sh first." >&2
  exit 1
fi

mkdir -p "$OUTPUT_DIR"
LOG_DIR="$OUTPUT_DIR/logs"
mkdir -p "$LOG_DIR"

# The SET shares to run for one operation. "-" stands for "not a MIXED run".
mixes_for() {
  if [[ "${1^^}" == "MIXED" ]]; then
    echo "$SET_PERCENTS"
  else
    echo "-"
  fi
}

total=0
for _t in $TARGETS; do for _o in $OPERATIONS; do for _m in $(mixes_for "$_o"); do
  for _p in $PAYLOADS; do for _c in $THREADS; do
    total=$((total + REPEATS))
  done; done
done; done; done

echo "================================================================"
echo "Benchmark matrix"
echo "================================================================"
echo "targets     : $TARGETS"
echo "operations  : $OPERATIONS"
if [[ " ${OPERATIONS^^} " == *" MIXED "* ]]; then
  echo "set percents: $SET_PERCENTS"
fi
echo "payloads    : $PAYLOADS"
echo "threads     : $THREADS"
echo "repeats     : $REPEATS"
echo "keys        : $KEYS"
echo "warmup      : ${WARMUP}s"
echo "duration    : ${DURATION}s"
echo "cooldown    : ${COOLDOWN_SECONDS}s"
echo "output      : $OUTPUT_DIR"
echo "total runs  : $total"
echo
estimated=$(( total * (WARMUP + DURATION + COOLDOWN_SECONDS) ))
printf 'estimated   : ~%d h %d min, excluding preload time\n' $((estimated / 3600)) $(((estimated % 3600) / 60))
echo "================================================================"
echo

run_index=0
failed_runs=0
invalid_runs=0
started_at=$(date +%s)

for target in $TARGETS; do
  for operation in $OPERATIONS; do
   for mix in $(mixes_for "$operation"); do
    for payload in $PAYLOADS; do
      for threads in $THREADS; do
        for repeat in $(seq 1 "$REPEATS"); do
          run_index=$((run_index + 1))
          mix_args=()
          mix_label=""
          if [[ "$mix" != "-" ]]; then
            mix_args=(--set-percent "$mix")
            mix_label="_s${mix}"
          fi
          label="${target}_${operation}${mix_label}_p${payload}_t${threads}_r${repeat}"
          log_file="$LOG_DIR/${label}.log"

          printf '[%d/%d] %s ... ' "$run_index" "$total" "$label"

          java $JVM_OPTS -jar "$JAR" \
            --config "$CONFIG" \
            --target "$target" \
            --operation "$operation" \
            "${mix_args[@]}" \
            --threads "$threads" \
            --keys "$KEYS" \
            --payload "$payload" \
            --warmup "$WARMUP" \
            --duration "$DURATION" \
            --seed "$SEED" \
            --preload "$PRELOAD" \
            --output "$OUTPUT_DIR" \
            >"$log_file" 2>&1

          exit_code=$?
          case $exit_code in
            0)
              tps=$(grep -m1 'Throughput' "$log_file" | awk -F: '{ gsub(/^ +| +$/, "", $2); print $2 }')
              echo "OK    ${tps:-}"
              ;;
            2)
              echo "INVALID (error rate above threshold, see $log_file)"
              invalid_runs=$((invalid_runs + 1))
              ;;
            *)
              echo "FAILED exit=$exit_code (see $log_file)"
              failed_runs=$((failed_runs + 1))
              ;;
          esac

          # Cooldown so the previous run does not bleed into the next one
          # (PRD section 49). Skipped after the final run.
          if (( run_index < total )); then
            sleep "$COOLDOWN_SECONDS"
          fi
        done
      done
    done
   done
  done
done

elapsed=$(( $(date +%s) - started_at ))

echo
echo "================================================================"
echo "Matrix complete"
echo "================================================================"
printf 'elapsed       : %d h %d min\n' $((elapsed / 3600)) $(((elapsed % 3600) / 60))
echo "runs          : $run_index"
echo "failed        : $failed_runs"
echo "invalid       : $invalid_runs"
echo "summary csv   : $OUTPUT_DIR/summary.csv"
echo "per-run logs  : $LOG_DIR"
echo "================================================================"

if (( failed_runs > 0 )); then
  exit 1
fi
