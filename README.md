# cache-benchmark

Redis vs Hazelcast GET/SET throughput benchmark.

One benchmark engine, one workload generator, one set of counters. The only
thing that changes between a Redis run and a Hazelcast run is which
`CacheClient` implementation is behind the interface.

Built from [docs/PRD.md](docs/PRD.md).

---

## 1. Purpose

Answer two questions with numbers that can be defended:

```
Redis GET TPS  vs  Hazelcast GET TPS
Redis SET TPS  vs  Hazelcast SET TPS
```

This is **not** a comparison of Redis and Hazelcast architectures. It measures
how each one serves a GET/SET workload produced by the same client, under the
same concurrency, with the same payload and the same keyspace.

The application deliberately does not declare a winner. It emits raw metrics;
the comparison is yours to make.

### Architecture

```
                   BenchmarkRunner
                         |
                  WorkloadEngine
                         |
                   CacheClient
                    /       \
                   /         \
        RedisCacheClient   HazelcastCacheClient
                |                 |
           Lettuce           Hazelcast Java Client
                |                 |
             Redis            Hazelcast
```

`BenchmarkRunner` contains no Redis-specific or Hazelcast-specific logic. Every
backend difference is isolated in the adapter.

---

## 2. Prerequisites

| Need | Version |
|---|---|
| JDK | 17 or newer |
| Maven | 3.8 or newer |
| Redis | reachable over the network |
| Hazelcast | reachable over the network |

The benchmark client needs **no** Docker, Kubernetes, application server, or a
local copy of either backend.

---

## 3. RHEL 9 setup

```bash
sudo dnf install -y java-17-openjdk java-17-openjdk-devel maven
java -version
mvn -v
```

Target VM profile the defaults are tuned for:

```
CPU    : 4 vCPU
Memory : 16 GB RAM
OS     : RHEL 9
```

Open the outbound ports the benchmark needs: 6379 for Redis, 5701 for
Hazelcast.

### Local development on Windows

Development works the same way, but `java -version` on a stock Windows box is
often Java 8, which cannot compile this project.

```powershell
winget install Microsoft.OpenJDK.17
```

Maven is not in the winget catalogue. Download the binary zip and unpack it:

```powershell
$tools = "$env:USERPROFILE\tools"
New-Item -ItemType Directory -Force -Path $tools | Out-Null
$zip = "$env:TEMP\apache-maven-3.9.9-bin.zip"
Invoke-WebRequest -UseBasicParsing -OutFile $zip `
  -Uri "https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.zip"
Expand-Archive -Path $zip -DestinationPath $tools -Force
```

Then put both on `PATH` for the session, or permanently through the System
Properties dialog:

```powershell
$env:JAVA_HOME = (Get-ChildItem "C:\Program Files\Microsoft\jdk-17*" -Directory)[0].FullName
$env:PATH = "$env:JAVA_HOME\bin;$env:USERPROFILE\tools\apache-maven-3.9.9\bin;$env:PATH"
```

A stock Windows box often has Java 8 earlier on `PATH`, so confirm
`java -version` reports 17 before building. Then use the PowerShell helpers:

```powershell
.\scripts\build.ps1
.\scripts\run.ps1 --target redis --operation GET --threads 16
```

The `.sh` scripts are the ones that matter on RHEL; the `.ps1` pair exists only
so you can iterate locally.


---

## 4. Build

```bash
mvn clean package
```

Produces a self-contained executable JAR:

```
target/cache-benchmark.jar
```

Or:

```bash
./scripts/build.sh
./scripts/build.sh --skip-tests
```

Unit tests run on every build and never need a live backend.

Before the first run:

```bash
REDIS_HOST=10.10.10.11 HZ_HOST=10.10.10.21 ./scripts/preflight.sh
```

---

## 5. Configuration

`config/benchmark.yaml` holds the defaults. Priority is:

```
CLI argument  >  YAML file  >  application default
```

```yaml
benchmark:
  target: redis                # redis | hazelcast
  operation: GET               # GET | SET
  threads: 16
  keyCount: 1000000
  payloadBytes: 1024
  randomSeed: 123456
  warmupSeconds: 30
  durationSeconds: 60
  preload: true
  precomputeKeys: true
  maxErrorRatePercent: 1.0
  operationTimeoutSeconds: 10
  outputDirectory: ./results

redis:
  host: 127.0.0.1              # point at your Redis VM
  port: 6379
  password: null
  database: 0

hazelcast:
  clusterName: dev             # must match the cluster name on the members
  addresses:
    - 127.0.0.1:5701           # point at your Hazelcast members
  mapName: benchmark-map
```

The shipped file targets localhost. Change the two endpoints for your VMs, or
override them per run with `--redis-host` and `--hz-addresses`.

Full option list:

```bash
java -jar target/cache-benchmark.jar --help
```

---

## 6. Redis benchmark

```bash
java \
  -Xms2g \
  -Xmx4g \
  -jar target/cache-benchmark.jar \
  --config config/benchmark.yaml \
  --target redis \
  --operation GET \
  --threads 16 \
  --keys 1000000 \
  --payload 1024 \
  --warmup 30 \
  --duration 60
```

Or with the endpoint on the command line:

```bash
./scripts/run.sh --target redis --redis-host 10.10.10.11 --redis-port 6379 --operation GET
```

---

## 7. Hazelcast benchmark

```bash
java \
  -Xms2g \
  -Xmx4g \
  -jar target/cache-benchmark.jar \
  --config config/benchmark.yaml \
  --target hazelcast \
  --operation GET \
  --threads 16 \
  --keys 1000000 \
  --payload 1024 \
  --warmup 30 \
  --duration 60
```

Or:

```bash
./scripts/run.sh --target hazelcast --hz-addresses 10.10.10.21:5701 --hz-cluster benchmark --operation GET
```

Every workload parameter between sections 6 and 7 is identical. Only the
backend changes.

---

## 8. GET benchmark

A GET run needs a populated keyspace, so it preloads `keyCount` keys first,
then verifies them.

```
Initialize client
      |
Generate keys
      |
Preload dataset          <- outside measurement
      |
Preload validation       <- 100 sampled keys must all be present
      |
Warmup                   <- discarded
      |
Measurement
      |
Result
```

Preload and warmup are excluded from TPS.

The validation step is not ceremony. A GET benchmark against an empty keyspace
does not fail: it returns nulls very quickly and produces an excellent-looking
number. The check runs on every GET benchmark, including when
`--preload false` reuses a keyspace from an earlier run, and aborts with:

```
PRELOAD_VALIDATION_FAILED
```

### Reusing a preloaded keyspace

Preloading a million keys before every run costs real time in a large matrix.
Once the keyspace is loaded with the payload size you are about to test:

```bash
./scripts/run.sh --target redis --operation GET --payload 1024 --preload false
```

Validation still runs, and still fails if the data is not there or was loaded
at a different payload size.

---

## 9. SET benchmark

```bash
./scripts/run.sh --target redis --operation SET --threads 32
```

SET writes its own data, so preload is skipped. An operation counts as complete
when the synchronous client call returns to the caller.

---

## 10. Benchmark matrix

The recommended comparison grid (PRD section 46):

```
2 targets  x  2 operations  x  3 payloads  x  7 concurrency levels  =  84 runs
```

with 5 repeats each, every run in a fresh JVM.

```bash
./scripts/run-matrix.sh
```

Narrow it down with environment variables:

```bash
TARGETS="redis hazelcast" \
OPERATIONS="GET" \
PAYLOADS="1024" \
THREADS="1 2 4 8 16 32 64" \
REPEATS=3 \
./scripts/run-matrix.sh
```

A fresh JVM per run is deliberate. Five runs inside one JVM would share a
warmed-up JIT and a warmed-up heap, so the later runs would not be comparable
with the first.

Default cooldown between runs is 10 seconds (`COOLDOWN_SECONDS`).

### Recommended sequence

Start with GET at 1 KB across the whole concurrency ladder on Redis, repeat the
identical ladder on Hazelcast, then do the same for SET. That is what the
default matrix does.

---

## 11. Reading the results

```
results/
├── summary.csv                 <- every run, one row each
├── logs/                       <- per-run console output from the matrix script
├── redis/
│   ├── GET/
│   │   ├── redis_GET_t16_p1024_20260930_143000.json
│   │   └── redis_GET_t16_p1024_20260930_143000.csv
│   └── SET/
└── hazelcast/
    ├── GET/
    └── SET/
```

Console output:

```
==================================================
CACHE BENCHMARK RESULT
==================================================

Target              : redis
Operation           : GET

Threads             : 16
Key Count           : 1000000
Payload             : 1024 bytes

Warmup              : 30.000 sec
Configured Duration : 60.000 sec
Actual Duration     : 60.008 sec

Successful Ops      : 20,453,221
Failed Ops          : 0
Cache Hits          : 20,453,221
Cache Misses        : 0

Throughput          : 340,841 ops/sec
Error Rate          : 0.000 %

Latency
--------------------------------------------------
p50                 : 0.421 ms
...

Result Status       : VALID
```

### What the numbers mean

| Field | Meaning |
|---|---|
| `tps` | successful operations / **actual** elapsed measurement seconds |
| `actualDurationSeconds` | real measurement window, used as the TPS denominator |
| `cacheMisses` | GET returned null. Not an error, but a miss-heavy GET run measures almost nothing |
| `errorRatePercent` | failed / attempted |
| `status` | `INVALID` once the error rate passes `maxErrorRatePercent`. Raw metrics are still written |
| `gc` | benchmark client GC during measurement. High values point at the generator, not the backend |
| `cpu.processCpuLoadAvg` | benchmark client CPU. Near 1.00 means this VM is the limit |

Latency percentiles come from HdrHistogram and cover successful operations
only. Failed operations have no meaningful service time, and folding timeouts
into the distribution would make the percentiles describe the timeout setting
rather than the backend.

Exit codes:

```
0 = benchmark completed
1 = configuration or runtime failure
2 = benchmark completed but the result is INVALID
```

Build the comparison table from `summary.csv`:

```
Operation : GET
Payload   : 1024 bytes

Threads | Redis TPS | Hazelcast TPS
--------|-----------|--------------
1       | ...       | ...
16      | ...       | ...
64      | ...       | ...
```

---

## 12. Fairness constraints

These are enforced by the design, not by convention. Breaking one of them
invalidates the comparison.

| # | Rule | How it is enforced |
|---|---|---|
| 1 | Same worker count | One `threads` setting, applied to both targets |
| 2 | Same payload | One `byte[]` built from `payloadBytes`, reused for every request |
| 3 | Same key count | One `keyCount` setting |
| 4 | Same key pattern | `benchmark:<number>` from a shared `KeySpace` |
| 5 | Same distribution | Uniform, from `UniformKeyGenerator`, seeded from `randomSeed` |
| 6 | Same warmup | One `warmupSeconds` setting |
| 7 | Same duration | One `durationSeconds` setting |
| 8 | Same benchmark runner | `BenchmarkRunner` holds only a `CacheClient` |
| 9 | No explicit Redis pipelining | The adapter calls `commands.get` / `commands.set` and waits |
| 10 | No explicit Hazelcast batching | The adapter calls `map.get` / `map.set` and waits |
| 11 | Same serialization | `byte[]` on both sides. No JSON, no Java serialization, no POJO |
| 12 | No per-request connections | Clients are built once, before warmup |

Every result file carries a `fairness` block recording these, so a stray JSON
can still be judged on its own.

### Deliberate choices worth knowing about

**Value type.** Both backends store an opaque `byte[]`. Lettuce gets a custom
codec for this; Hazelcast uses `IMap<String, byte[]>`. This keeps serialization
overhead out of the comparison, at the cost of not exercising either product's
richer serialization.

**Near Cache is off.** A Hazelcast Near Cache would serve reads inside the
benchmark process without touching the cluster, and nothing equivalent is
enabled on the Redis side. `HazelcastCacheClient` asserts none is configured.

**TCP connections are not forced to match.** Lettuce multiplexes concurrent
commands over one connection; the Hazelcast client keeps one per member. Both
are the architecture their vendor recommends. What is held identical is
application worker concurrency, workload, operation, payload, duration and key
distribution (PRD section 43). The connection model each client uses is
recorded in the result under `connection.description`.

**`map.set` rather than `map.put`.** `put` would ship the previous value back
to the client; Redis `SET` does not. Using `set` keeps the wire traffic
comparable.

**Operation timeout is shared.** One `operationTimeoutSeconds` drives the
Lettuce command timeout and the Hazelcast invocation timeout, so a stalled
request is counted as a failure at the same moment on both sides.

---

## Warning

```
The benchmark measures performance as observed from the benchmark
client VM. If the benchmark VM reaches CPU, network, memory, or GC
saturation, the result may represent the benchmark generator limit
rather than the backend limit.
```

On a 4 vCPU VM this is a real possibility, not a formality. Every result
records `cpu.processCpuLoadAvg`, `cpu.processCpuLoadMax`,
`environment.availableProcessors` and the GC counters so you can tell the two
situations apart. The application draws no conclusion from them; it only
records the evidence.

If `processCpuLoadMax` sits near 1.00, you are measuring this VM.

---

## Development

```bash
mvn test                                     # unit tests, no backend needed
mvn verify -Pintegration \
  -Dredis.host=10.10.10.11 -Dredis.port=6379 \
  -Dhz.addresses=10.10.10.21:5701 -Dhz.cluster=benchmark
```

Integration tests only run under the `integration` profile. A missing Redis or
Hazelcast never fails the normal build.

### Layout

```
src/main/java/com/example/cachebenchmark/
├── Main.java                  CLI, exit codes
├── benchmark/                 phases, workers, result assembly
├── client/                    CacheClient and the two adapters
├── config/                    YAML model, validation
├── workload/                  GET and SET workloads, payload
├── key/                       keyspace and uniform key generator
├── metrics/                   counters, HdrHistogram, GC, CPU
└── output/                    console, JSON, CSV reporters
```

### Where to start reading

`CacheClient` is the contract. `BenchmarkRunner.run(CacheClient)` is the whole
benchmark in one method. `Worker.loop()` is the measured code path, and is the
only place where allocation and timing matter.
