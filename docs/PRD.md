# Product Requirements Document
## Redis vs Hazelcast Java Benchmark Tool

**Document Version:** 1.0  
**Target Platform:** Red Hat Enterprise Linux 9  
**Runtime:** Java 17  
**Build Tool:** Maven  
**Primary Objective:** Membandingkan throughput/TPS operasi GET dan SET antara Redis dan Hazelcast dengan workload yang identik dan reproducible.

---

# 1. Background

Diperlukan sebuah aplikasi benchmark sederhana berbasis Java untuk membandingkan performa Redis dan Hazelcast dari sisi throughput operasi cache.

Aplikasi benchmark harus menggunakan:

- Redis Java Client untuk Redis.
- Hazelcast Java Client untuk Hazelcast.
- Benchmark engine yang sama.
- Workload generation yang sama.
- Formula perhitungan throughput yang sama.
- Concurrency yang sama.
- Payload yang sama.
- Keyspace yang sama.
- Durasi pengujian yang sama.

Benchmark **tidak ditujukan untuk membandingkan arsitektur Redis dan Hazelcast**, tetapi secara spesifik membandingkan kemampuan kedua sistem dalam melayani workload GET dan SET yang dihasilkan oleh benchmark client yang sama.

Aplikasi benchmark akan dijalankan pada VM dengan spesifikasi:

```text
CPU    : 4 vCPU
Memory : 16 GB RAM
OS     : RHEL 9
```

Redis dan Hazelcast dianggap sudah tersedia dan dapat diakses melalui network dari VM benchmark.

---

# 2. Goals

Aplikasi harus mampu menghasilkan benchmark yang konsisten dan repeatable untuk membandingkan:

```text
Redis GET TPS
vs
Hazelcast GET TPS
```

dan:

```text
Redis SET TPS
vs
Hazelcast SET TPS
```

Aplikasi harus memungkinkan perubahan parameter benchmark tanpa melakukan perubahan source code.

Parameter utama yang harus dapat dikonfigurasi:

- target backend
- operation
- thread/concurrency
- duration
- warmup duration
- jumlah key
- payload size
- random seed
- Redis endpoint
- Hazelcast endpoint
- output directory

Hasil benchmark minimal harus menghasilkan:

- successful operations
- failed operations
- TPS
- p50 latency
- p95 latency
- p99 latency
- max latency
- elapsed measurement time
- error rate

---

# 3. Non-Goals

Versi pertama aplikasi **tidak perlu** melakukan hal berikut:

- Membandingkan high availability.
- Membandingkan replication.
- Membandingkan failover.
- Membandingkan cluster recovery.
- Membandingkan persistence.
- Membandingkan eviction policy.
- Membandingkan memory efficiency server.
- Melakukan distributed load generation.
- Menjalankan benchmark dari beberapa VM sekaligus.
- Membandingkan Redis pipelining.
- Membandingkan Redis Lua.
- Membandingkan Hazelcast EntryProcessor.
- Menggunakan batching.
- Menggunakan asynchronous benchmark workload.
- Menentukan produk mana yang secara umum "lebih baik".

Scope versi pertama difokuskan pada:

```text
Single benchmark VM
        |
        |
        +-------- Redis
        |
        +-------- Hazelcast
```

dengan operasi synchronous GET dan SET.

---

# 4. Benchmark Philosophy

Benchmark harus memenuhi prinsip berikut.

## 4.1 Same Workload Generator

Redis dan Hazelcast harus menggunakan benchmark runner yang sama.

Arsitektur yang diharapkan:

```text
                   BenchmarkRunner
                         |
                  WorkloadEngine
                         |
                   CacheClient
                    /       \
                   /         \
                  /           \
        RedisCacheClient   HazelcastCacheClient
                |                 |
           Redis Client      Hazelcast Client
                |                 |
             Redis            Hazelcast
```

`BenchmarkRunner` tidak boleh memiliki logic khusus Redis maupun Hazelcast.

Perbedaan implementasi backend harus diisolasi pada adapter `CacheClient`.

---

# 5. Benchmark Model

Benchmark menggunakan model:

```text
Closed-loop synchronous benchmark
```

Setiap worker menjalankan pola:

```text
send request
    |
    v
wait response
    |
    v
record result
    |
    v
send next request
```

Tidak boleh ada request batching yang dilakukan benchmark engine.

Tidak boleh ada explicit pipelining.

Native multiplexing yang dilakukan internal oleh library client diperbolehkan selama benchmark code tidak secara eksplisit melakukan batching atau pipeline.

---

# 6. Target Runtime Environment

Benchmark application harus dapat berjalan pada:

```text
Red Hat Enterprise Linux 9
```

Minimum runtime:

```text
OpenJDK 17
```

Contoh dependency sistem:

```bash
sudo dnf install java-17-openjdk java-17-openjdk-devel
```

Build environment menggunakan Maven.

Contoh:

```bash
sudo dnf install maven
```

Aplikasi harus dapat dijalankan sebagai executable JAR:

```bash
java -jar cache-benchmark.jar
```

Tidak boleh memerlukan:

- Docker
- Kubernetes
- application server
- database lokal
- Redis lokal
- Hazelcast lokal

untuk menjalankan benchmark client.

---

# 7. JVM Resource Configuration

VM benchmark memiliki resource:

```text
4 vCPU
16 GB RAM
```

Recommended JVM configuration:

```bash
-Xms2g
-Xmx4g
```

Contoh:

```bash
java \
  -Xms2g \
  -Xmx4g \
  -jar cache-benchmark.jar
```

Benchmark application tidak boleh membutuhkan heap lebih dari 4 GB untuk konfigurasi default.

Aplikasi tidak boleh menyimpan seluruh value dataset di memory benchmark client.

Untuk SET workload, payload dapat berupa reusable deterministic `byte[]`.

Contoh:

```java
byte[] payload = new byte[payloadSize];
```

Payload tersebut dapat digunakan kembali oleh worker.

---

# 8. Technology Requirements

Gunakan teknologi berikut:

```text
Language      : Java
Java Version  : 17
Build         : Maven
Configuration : YAML
```

Recommended libraries:

```text
Redis Client:
Lettuce

Hazelcast:
Hazelcast Java Client

Latency:
HdrHistogram

Configuration:
Jackson YAML atau SnakeYAML

CLI:
Picocli
```

Dependency harus di-pin ke exact version pada `pom.xml`.

Jangan menggunakan version range.

---

# 9. Core Interface

Harus terdapat abstraction untuk backend cache.

Contoh:

```java
public interface CacheClient {

    void set(String key, byte[] value);

    byte[] get(String key);

    void close();
}
```

Implementasi:

```text
CacheClient
   |
   +--- RedisCacheClient
   |
   +--- HazelcastCacheClient
```

Benchmark engine hanya boleh berinteraksi melalui interface tersebut.

---

# 10. Redis Client Requirement

Redis implementation menggunakan Lettuce.

Redis adapter harus mendukung:

```text
GET
SET
```

Gunakan synchronous API.

Pseudo implementation:

```java
public class RedisCacheClient implements CacheClient {

    @Override
    public byte[] get(String key) {
        return redisCommands.get(key);
    }

    @Override
    public void set(String key, byte[] value) {
        redisCommands.set(key, value);
    }
}
```

Client dan connection harus dibuat sebelum warmup dimulai.

Connection tidak boleh dibuat per operation.

Pattern berikut dilarang:

```text
connect
GET
disconnect

connect
GET
disconnect
```

Harus menggunakan long-lived client connection.

---

# 11. Hazelcast Client Requirement

Hazelcast implementation menggunakan official Hazelcast Java Client.

Data disimpan menggunakan:

```text
IMap<String, byte[]>
```

Nama map configurable.

Default:

```text
benchmark-map
```

Pseudo implementation:

```java
public class HazelcastCacheClient implements CacheClient {

    private final IMap<String, byte[]> map;

    @Override
    public byte[] get(String key) {
        return map.get(key);
    }

    @Override
    public void set(String key, byte[] value) {
        map.set(key, value);
    }
}
```

Hazelcast client harus dibuat sebelum warmup.

Hazelcast client tidak boleh dibuat ulang untuk setiap request.

---

# 12. Key Format

Gunakan key dengan format:

```text
benchmark:<number>
```

Contoh:

```text
benchmark:0
benchmark:1
benchmark:2
benchmark:3
...
benchmark:999999
```

Default key count:

```text
1,000,000
```

Key count harus configurable.

---

# 13. Key Generation

Versi pertama menggunakan:

```text
Uniform Random Distribution
```

Setiap key memiliki probability yang kurang lebih sama untuk dipilih.

Gunakan random generator yang ringan seperti:

```java
SplittableRandom
```

atau:

```java
ThreadLocalRandom
```

Untuk reproducibility, benchmark harus mendukung:

```text
randomSeed
```

Contoh:

```yaml
randomSeed: 123456
```

Jika menggunakan per-worker random generator, seed harus diturunkan secara deterministic dari global seed.

Contoh:

```text
worker 0 = seed + 0
worker 1 = seed + 1
worker 2 = seed + 2
```

---

# 14. Key Precomputation

Untuk menghindari overhead pembuatan String pada setiap request, key sebaiknya diprecompute sebelum benchmark.

Contoh:

```java
String[] keys;
```

Berisi:

```text
benchmark:0
benchmark:1
...
benchmark:999999
```

Key generation tidak boleh masuk ke pengukuran latency backend apabila key dapat diprecompute.

Default:

```yaml
precomputeKeys: true
```

---

# 15. Payload

Value menggunakan:

```java
byte[]
```

Jangan menggunakan:

- JSON serialization
- Java object serialization
- protobuf
- custom POJO

pada benchmark baseline.

Tujuannya adalah meminimalkan serialization overhead yang tidak berhubungan langsung dengan cache operation.

Payload harus configurable berdasarkan bytes.

Contoh:

```yaml
payloadBytes: 1024
```

Recommended benchmark payload matrix:

```text
100 bytes
1 KB
10 KB
```

Default:

```text
1024 bytes
```

Payload harus deterministic.

Contoh isi:

```text
0x01 0x02 0x03 ...
```

atau fixed repeated byte pattern.

Tidak perlu melakukan random payload generation setiap request.

---

# 16. Benchmark Operations

Aplikasi wajib mendukung dua workload.

## 16.1 GET

```yaml
operation: GET
```

Worker melakukan:

```text
select random key
GET key
record latency
increment success/error counter
repeat
```

Sebelum menjalankan GET benchmark, semua key harus sudah tersedia pada target cache.

---

# 17. SET

```yaml
operation: SET
```

Worker melakukan:

```text
select random key
SET key payload
record latency
increment success/error counter
repeat
```

SET dianggap selesai setelah synchronous client operation berhasil kembali ke caller.

---

# 18. Preload Phase

GET benchmark harus memiliki preload phase.

Flow:

```text
Application start
      |
      v
Initialize Client
      |
      v
Generate Keys
      |
      v
Preload Dataset
      |
      v
Warmup
      |
      v
Measurement
      |
      v
Result
```

Preload tidak masuk dalam TPS measurement.

Preload harus memasukkan:

```text
keyCount
```

keys ke backend.

Contoh:

```text
benchmark:0
benchmark:1
...
benchmark:999999
```

dengan payload yang sama seperti konfigurasi benchmark.

Untuk operation SET, preload boleh dilewati secara default.

Config:

```yaml
preload: true
```

Untuk GET, jika `preload=false`, aplikasi harus mengeluarkan warning yang jelas.

---

# 19. Preload Verification

Setelah preload selesai, aplikasi harus melakukan sanity check.

Minimal ambil beberapa sample key.

Contoh:

```text
benchmark:0
benchmark:500000
benchmark:999999
```

Pastikan GET menghasilkan value non-null.

Jika sanity check gagal, benchmark harus dihentikan.

Output:

```text
PRELOAD_VALIDATION_FAILED
```

---

# 20. Benchmark Phases

Satu execution memiliki phase:

```text
INITIALIZATION
      |
      v
PRELOAD
      |
      v
WARMUP
      |
      v
MEASUREMENT
      |
      v
RESULT
      |
      v
SHUTDOWN
```

---

# 21. Warmup

Warmup wajib tersedia.

Default:

```text
30 seconds
```

Warmup bertujuan untuk:

- JVM JIT compilation.
- Menghangatkan client library.
- Mengaktifkan connection.
- Mengurangi startup effect.
- Menghangatkan code path.

Operation selama warmup **tidak boleh masuk hasil benchmark**.

Sebelum measurement dimulai:

```text
reset operation counter
reset error counter
reset latency histogram
```

---

# 22. Measurement Duration

Default measurement:

```text
60 seconds
```

Config:

```yaml
durationSeconds: 60
```

Gunakan:

```java
System.nanoTime()
```

untuk pengukuran waktu.

Jangan gunakan:

```java
System.currentTimeMillis()
```

sebagai primary measurement clock.

---

# 23. Worker Threads

Benchmark menggunakan fixed number of worker threads.

Contoh:

```java
Executors.newFixedThreadPool(threadCount);
```

Thread count configurable.

Contoh:

```yaml
threads: 16
```

Setiap worker menjalankan request terus-menerus sampai measurement selesai.

Pseudo logic:

```java
while (System.nanoTime() < deadline) {

    String key = selectKey();

    long start = System.nanoTime();

    try {

        cache.get(key);

        long latency = System.nanoTime() - start;

        latencyRecorder.record(latency);

        successfulOperations.increment();

    } catch (Exception e) {

        failedOperations.increment();
    }
}
```

---

# 24. Worker Synchronization

Semua worker harus mulai measurement hampir bersamaan.

Gunakan synchronization mechanism seperti:

```text
CountDownLatch
```

atau:

```text
CyclicBarrier
```

Measurement timer tidak boleh dimulai sebelum seluruh worker siap.

Flow:

```text
Create workers

Worker 1 ready
Worker 2 ready
Worker 3 ready
...
Worker N ready

        |
        v

Start signal

        |
        v

All workers execute
```

---

# 25. Thread Matrix

VM benchmark memiliki:

```text
4 vCPU
```

Recommended concurrency matrix:

```text
1
2
4
8
16
32
64
```

Optional:

```text
128
```

128 thread digunakan untuk melihat apakah throughput sudah plateau atau turun.

Default benchmark:

```text
threads = 16
```

Jangan menganggap jumlah thread optimal sama dengan jumlah vCPU.

Karena benchmark melakukan network I/O synchronous, jumlah worker dapat lebih tinggi dari jumlah CPU.

---

# 26. Throughput Calculation

TPS dihitung hanya dari successful completed operation.

Formula:

```text
TPS =
successful completed operations
/
actual measurement elapsed seconds
```

Contoh:

```text
Successful operations:
30,000,000

Measurement:
60 seconds
```

Maka:

```text
TPS = 500,000 ops/sec
```

Failed operations tidak boleh dihitung sebagai successful TPS.

---

# 27. Measurement Boundary

Worker tidak boleh memulai request baru setelah deadline measurement tercapai.

Request yang sudah dimulai sebelum deadline diperbolehkan selesai.

Gunakan actual elapsed measurement time untuk denominator TPS sehingga sedikit overshoot pada akhir benchmark tidak menyebabkan hasil throughput salah.

Contoh:

```text
Configured duration:
60.000 sec

Actual measurement completion:
60.013 sec
```

TPS menggunakan:

```text
60.013 sec
```

---

# 28. Counters

Minimal counter:

```text
attemptedOperations
successfulOperations
failedOperations
```

Untuk GET tambahkan:

```text
cacheHits
cacheMisses
```

GET dianggap:

```text
hit  = returned value != null
miss = returned value == null
```

Cache miss bukan exception tetapi harus dilaporkan.

---

# 29. Error Rate

Formula:

```text
errorRate =
failedOperations
/
attemptedOperations
```

Output dalam percentage.

Contoh:

```text
Error Rate : 0.002%
```

Jika error rate lebih dari:

```text
1%
```

result harus diberi status:

```text
INVALID
```

Tetapi raw metrics tetap harus disimpan.

Threshold harus configurable.

Default:

```yaml
maxErrorRatePercent: 1.0
```

---

# 30. Latency Measurement

Setiap successful operation harus mencatat latency.

Gunakan:

```text
HdrHistogram
```

Internal unit direkomendasikan:

```text
nanoseconds
```

Output ditampilkan dalam:

```text
milliseconds
```

Minimal percentile:

```text
p50
p95
p99
max
```

Optional:

```text
p99.9
mean
```

Latency histogram warmup harus dibuang sebelum measurement.

---

# 31. Result Output

Console output harus human-readable.

Contoh:

```text
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
Cache Misses        : 0

Throughput          : 340,841 ops/sec
Error Rate          : 0.000 %

Latency
--------------------------------------------------
p50                 : 0.421 ms
p95                 : 1.120 ms
p99                 : 2.741 ms
max                 : 18.892 ms

Result Status       : VALID

==================================================
```

---

# 32. Machine-Readable Result

Selain console output, aplikasi wajib menghasilkan:

```text
JSON
CSV
```

Contoh directory:

```text
results/
```

Contoh filename:

```text
redis_GET_t16_p1024_20260930_143000.json
redis_GET_t16_p1024_20260930_143000.csv
```

---

# 33. JSON Output Schema

Minimal format:

```json
{
  "benchmarkVersion": "1.0.0",
  "timestamp": "2026-09-30T14:30:00+07:00",

  "target": "redis",
  "operation": "GET",

  "threads": 16,
  "keyCount": 1000000,
  "payloadBytes": 1024,

  "warmupSeconds": 30,
  "configuredDurationSeconds": 60,
  "actualDurationSeconds": 60.008,

  "attemptedOperations": 20453221,
  "successfulOperations": 20453221,
  "failedOperations": 0,

  "cacheHits": 20453221,
  "cacheMisses": 0,

  "tps": 340841.2,

  "latencyMs": {
    "p50": 0.421,
    "p95": 1.120,
    "p99": 2.741,
    "max": 18.892
  },

  "errorRatePercent": 0.0,

  "status": "VALID"
}
```

---

# 34. Environment Metadata

JSON result juga harus menyimpan informasi environment.

Contoh:

```json
{
  "environment": {
    "javaVersion": "17",
    "osName": "Linux",
    "osVersion": "...",
    "availableProcessors": 4,
    "maxJvmHeapBytes": 4294967296
  }
}
```

Jika memungkinkan tambahkan:

```text
hostname
JVM vendor
JVM version
benchmark application version
```

Tujuannya agar hasil benchmark dapat ditelusuri kembali ke environment yang digunakan.

---

# 35. Configuration

Default configuration menggunakan YAML.

File:

```text
config/benchmark.yaml
```

Example:

```yaml
benchmark:

  target: redis

  operation: GET

  threads: 16

  keyCount: 1000000

  payloadBytes: 1024

  randomSeed: 123456

  warmupSeconds: 30

  durationSeconds: 60

  preload: true

  precomputeKeys: true

  maxErrorRatePercent: 1.0

  outputDirectory: ./results


redis:

  host: 10.10.10.11

  port: 6379

  password: null

  database: 0


hazelcast:

  clusterName: benchmark

  addresses:

    - 10.10.10.21:5701

  mapName: benchmark-map
```

---

# 36. CLI Override

Semua parameter benchmark penting harus dapat dioverride melalui CLI.

Contoh:

```bash
java -jar cache-benchmark.jar \
  --config config/benchmark.yaml \
  --target redis \
  --operation GET \
  --threads 16 \
  --payload 1024 \
  --keys 1000000 \
  --warmup 30 \
  --duration 60
```

CLI harus override YAML.

Priority:

```text
CLI argument
    >
YAML configuration
    >
application default
```

---

# 37. CLI Help

Harus tersedia:

```bash
java -jar cache-benchmark.jar --help
```

Minimal menampilkan:

```text
--config
--target
--operation
--threads
--keys
--payload
--warmup
--duration
--seed
--preload
--output
--help
--version
```

---

# 38. Supported Target

Argument:

```text
--target
```

Valid values:

```text
redis
hazelcast
```

Target lain harus menghasilkan validation error.

---

# 39. Supported Operation

Argument:

```text
--operation
```

Valid values v1:

```text
GET
SET
```

Case insensitive input diperbolehkan.

Contoh:

```text
get
GET
Get
```

semuanya diperlakukan sebagai:

```text
GET
```

---

# 40. Validation

Aplikasi harus melakukan configuration validation sebelum membuka benchmark.

Contoh invalid configuration:

```text
threads <= 0
duration <= 0
warmup < 0
keyCount <= 0
payloadBytes <= 0
unsupported target
unsupported operation
empty Hazelcast address
invalid Redis port
```

Aplikasi harus berhenti dengan descriptive error message.

Contoh:

```text
Configuration error:
threads must be greater than zero.
```

---

# 41. Connection Validation

Sebelum preload/warmup, aplikasi harus memvalidasi backend dapat diakses.

Redis:

```text
connect
PING
```

Hazelcast:

```text
connect to cluster
obtain IMap reference
```

Jika gagal:

```text
exit code != 0
```

dan jangan menjalankan benchmark.

---

# 42. Fairness Rules

Fairness adalah requirement utama project.

Berikut aturan yang **harus dipatuhi**.

## Rule 1

Jumlah benchmark worker harus sama.

Contoh:

```text
Redis     = 16 workers
Hazelcast = 16 workers
```

## Rule 2

Payload harus sama.

```text
Redis     = 1024 bytes
Hazelcast = 1024 bytes
```

## Rule 3

Key count harus sama.

```text
Redis     = 1,000,000
Hazelcast = 1,000,000
```

## Rule 4

Key pattern harus sama.

```text
benchmark:<number>
```

## Rule 5

Random distribution harus sama.

Baseline:

```text
uniform
```

## Rule 6

Warmup harus sama.

## Rule 7

Measurement duration harus sama.

## Rule 8

Benchmark runner harus sama.

## Rule 9

Jangan menggunakan explicit Redis pipeline.

## Rule 10

Jangan menggunakan explicit Hazelcast batching.

## Rule 11

Jangan melakukan serialization format yang berbeda secara sengaja.

Value:

```text
byte[]
```

pada kedua backend.

## Rule 12

Jangan membuat connection baru setiap request.

---

# 43. Client Connection Philosophy

Redis dan Hazelcast memiliki internal client networking model yang berbeda.

Benchmark tidak perlu memaksakan jumlah physical TCP connection agar identik.

Yang harus identik adalah:

```text
application worker concurrency
workload
operation
payload
duration
key distribution
```

Gunakan normal thread-safe client architecture yang direkomendasikan masing-masing Java client.

Benchmark report harus mencatat bahwa:

```text
native client connection management is used
```

Benchmark engine dilarang memberikan optimization khusus ke salah satu backend.

---

# 44. GC Consideration

Benchmark application harus menghindari allocation yang tidak perlu.

Hindari:

```java
String key = "benchmark:" + randomNumber;
```

di setiap request jika key dapat diprecompute.

Hindari membuat payload baru pada setiap SET.

Gunakan reusable payload.

Aplikasi minimal harus mencatat GC count sebelum dan sesudah measurement jika memungkinkan melalui standard JVM MXBean.

Output optional:

```text
GC Collections : 3
GC Time        : 41 ms
```

GC metrics tidak menentukan TPS tetapi berguna untuk mendeteksi masalah benchmark client.

---

# 45. Benchmark Client Saturation

Karena benchmark VM hanya memiliki:

```text
4 vCPU
```

terdapat kemungkinan benchmark client menjadi bottleneck sebelum Redis/Hazelcast.

Untuk membantu identifikasi, result harus mencatat:

```text
availableProcessors
```

dan jika memungkinkan:

```text
processCpuLoad
```

Tidak perlu membuat automated conclusion.

Data hanya dicatat.

---

# 46. Benchmark Matrix

Recommended official comparison matrix:

## Payload

```text
100 bytes
1024 bytes
10240 bytes
```

## Threads

```text
1
2
4
8
16
32
64
```

Optional:

```text
128
```

## Operations

```text
GET
SET
```

Sehingga secara konseptual:

```text
2 targets
×
2 operations
×
3 payload sizes
×
7 concurrency levels
```

Total:

```text
84 benchmark runs
```

sebelum repetition.

---

# 47. Repetition

Setiap kombinasi benchmark direkomendasikan dijalankan:

```text
5 kali
```

Minimum:

```text
3 kali
```

Setiap invocation aplikasi hanya perlu menjalankan satu measurement.

Repetition dilakukan melalui shell script agar setiap test mendapatkan fresh JVM process.

Contoh:

```text
run 1 -> new JVM
run 2 -> new JVM
run 3 -> new JVM
run 4 -> new JVM
run 5 -> new JVM
```

Ini sengaja dipilih daripada melakukan lima benchmark dalam satu JVM.

---

# 48. Matrix Runner Script

Repository harus menyediakan:

```text
scripts/run-matrix.sh
```

Script tersebut melakukan loop atas:

```text
target
operation
payload
threads
repeat
```

Contoh pseudo-script:

```bash
TARGETS=("redis" "hazelcast")
OPERATIONS=("GET" "SET")
PAYLOADS=(100 1024 10240)
THREADS=(1 2 4 8 16 32 64)
REPEATS=5
```

Script harus menjalankan executable JAR untuk setiap kombinasi.

---

# 49. Cooldown

Matrix runner harus mendukung cooldown antar benchmark.

Default:

```text
10 seconds
```

Contoh:

```text
benchmark
sleep 10
benchmark
sleep 10
```

Config shell:

```bash
COOLDOWN_SECONDS=10
```

Cooldown tidak perlu dilakukan oleh Java application.

---

# 50. Results Directory

Contoh directory:

```text
results/
├── redis/
│   ├── GET/
│   └── SET/
│
└── hazelcast/
    ├── GET/
    └── SET/
```

Atau flat directory diperbolehkan selama filename unik dan mengandung configuration metadata.

---

# 51. CSV Schema

CSV minimal:

```text
timestamp,
target,
operation,
threads,
key_count,
payload_bytes,
warmup_seconds,
duration_seconds,
actual_duration_seconds,
successful_operations,
failed_operations,
tps,
p50_ms,
p95_ms,
p99_ms,
max_ms,
error_rate_percent,
status
```

Satu benchmark invocation menghasilkan satu row.

---

# 52. Suggested Repository Structure

```text
cache-benchmark/
│
├── README.md
├── pom.xml
│
├── config/
│   └── benchmark.yaml
│
├── scripts/
│   ├── build.sh
│   ├── run.sh
│   ├── run-matrix.sh
│   └── preflight.sh
│
├── results/
│
└── src/
    ├── main/
    │   └── java/
    │       └── com/example/cachebenchmark/
    │
    │           ├── Main.java
    │
    │           ├── benchmark/
    │           │   ├── BenchmarkRunner.java
    │           │   ├── BenchmarkPhase.java
    │           │   ├── BenchmarkResult.java
    │           │   └── Worker.java
    │           │
    │           ├── client/
    │           │   ├── CacheClient.java
    │           │   ├── RedisCacheClient.java
    │           │   └── HazelcastCacheClient.java
    │           │
    │           ├── config/
    │           │   ├── BenchmarkConfig.java
    │           │   └── ConfigLoader.java
    │           │
    │           ├── workload/
    │           │   ├── Workload.java
    │           │   ├── GetWorkload.java
    │           │   └── SetWorkload.java
    │           │
    │           ├── key/
    │           │   └── UniformKeyGenerator.java
    │           │
    │           ├── metrics/
    │           │   ├── MetricsCollector.java
    │           │   └── LatencyRecorder.java
    │           │
    │           └── output/
    │               ├── ConsoleReporter.java
    │               ├── JsonReporter.java
    │               └── CsvReporter.java
    │
    └── test/
        └── java/
```

---

# 53. Build

Build command:

```bash
mvn clean package
```

Expected artifact:

```text
target/cache-benchmark.jar
```

JAR harus merupakan:

```text
self-contained executable fat JAR
```

Semua runtime dependency harus terdapat di executable artifact.

---

# 54. Run Example — Redis GET

```bash
java \
  -Xms2g \
  -Xmx4g \
  -jar target/cache-benchmark.jar \
  --target redis \
  --operation GET \
  --threads 16 \
  --keys 1000000 \
  --payload 1024 \
  --warmup 30 \
  --duration 60
```

---

# 55. Run Example — Hazelcast GET

```bash
java \
  -Xms2g \
  -Xmx4g \
  -jar target/cache-benchmark.jar \
  --target hazelcast \
  --operation GET \
  --threads 16 \
  --keys 1000000 \
  --payload 1024 \
  --warmup 30 \
  --duration 60
```

Parameter workload antara kedua command harus identik.

Hanya target backend yang berubah.

---

# 56. Run Example — Redis SET

```bash
java \
  -Xms2g \
  -Xmx4g \
  -jar target/cache-benchmark.jar \
  --target redis \
  --operation SET \
  --threads 32 \
  --keys 1000000 \
  --payload 1024 \
  --warmup 30 \
  --duration 60
```

---

# 57. Run Example — Hazelcast SET

```bash
java \
  -Xms2g \
  -Xmx4g \
  -jar target/cache-benchmark.jar \
  --target hazelcast \
  --operation SET \
  --threads 32 \
  --keys 1000000 \
  --payload 1024 \
  --warmup 30 \
  --duration 60
```

---

# 58. Logging

Gunakan logging sederhana.

Level:

```text
INFO
WARN
ERROR
```

Jangan log setiap request.

Dilarang:

```text
INFO GET benchmark:1234
INFO GET benchmark:98321
INFO GET benchmark:1021
```

karena akan menghancurkan validity benchmark.

Logging hanya untuk:

```text
startup
configuration
connection
preload progress
warmup start/end
measurement start/end
errors summary
shutdown
```

---

# 59. Preload Progress

Untuk dataset besar, aplikasi boleh menampilkan progress.

Contoh:

```text
Preload 100000 / 1000000
Preload 200000 / 1000000
...
Preload complete.
```

Progress logging preload tidak menjadi masalah karena preload berada di luar measurement.

---

# 60. Request-Level Logging

Request-level logging harus:

```text
DISABLED
```

selama warmup dan measurement.

Tidak boleh melakukan:

```text
String.format(...)
```

per operation hanya untuk logging.

---

# 61. Error Handling

Exception pada operation harus:

1. Ditangkap worker.
2. Menambah failed operation counter.
3. Tidak menyebabkan seluruh JVM langsung crash untuk isolated operation error.
4. Tidak melakukan stack trace logging setiap occurrence.

Contoh:

```text
100,000 Redis timeout
```

jangan menghasilkan:

```text
100,000 stack traces
```

Cukup simpan:

```text
error type
error count
sample error
```

Contoh summary:

```text
Errors:

RedisCommandTimeoutException : 231
ConnectionException          : 4
```

---

# 62. Shutdown

Aplikasi harus menutup:

```text
ExecutorService
Redis connection
Redis client
Hazelcast client
```

Gunakan graceful shutdown.

Application exit code:

```text
0 = benchmark completed
1 = configuration / runtime failure
2 = invalid benchmark result
```

---

# 63. Preflight Script

Buat:

```text
scripts/preflight.sh
```

Minimal mengecek:

```text
Java installed
Java >= 17
jar exists
results directory writable
```

Optional:

```text
Redis port reachable
Hazelcast port reachable
```

Contoh tool:

```bash
nc -z host port
```

jika netcat tersedia.

Jangan membuat netcat sebagai mandatory dependency aplikasi.

---

# 64. README

README wajib menjelaskan:

1. Purpose project.
2. Prerequisites.
3. RHEL 9 installation.
4. Build procedure.
5. Configuration.
6. Redis benchmark.
7. Hazelcast benchmark.
8. GET benchmark.
9. SET benchmark.
10. Benchmark matrix.
11. Result interpretation.
12. Fairness constraints.

README juga harus memberikan warning:

```text
The benchmark measures performance as observed from the benchmark
client VM. If the benchmark VM reaches CPU, network, memory, or GC
saturation, the result may represent the benchmark generator limit
rather than the backend limit.
```

---

# 65. Unit Tests

Minimum unit tests:

## Configuration

Test:

```text
valid configuration
invalid threads
invalid duration
invalid operation
invalid target
```

## Key Generator

Pastikan generated index selalu:

```text
>= 0
< keyCount
```

## TPS Calculation

Contoh:

```text
1,000,000 ops
10 sec
```

Expected:

```text
100,000 TPS
```

## Result status

Test:

```text
error rate <= threshold -> VALID
error rate > threshold  -> INVALID
```

---

# 66. Integration Tests

Jika backend tersedia, integration test dapat diaktifkan menggunakan Maven profile.

Contoh:

```bash
mvn verify -Pintegration
```

Integration test bukan requirement agar normal unit test dapat berjalan.

Jangan membuat build gagal hanya karena Redis/Hazelcast server tidak tersedia pada development machine.

---

# 67. Performance Anti-Patterns

Implementation **tidak boleh** melakukan hal berikut pada hot loop:

```java
new Random()
```

setiap request.

Tidak boleh:

```java
new byte[payloadSize]
```

setiap SET.

Tidak boleh:

```java
"benchmark:" + randomKey
```

setiap request jika precomputed key digunakan.

Tidak boleh:

```text
write result file per request
```

Tidak boleh:

```text
console logging per request
```

Tidak boleh membuat connection per request.

Tidak boleh menggunakan:

```text
Thread.sleep()
```

pada worker selama measurement.

---

# 68. Benchmark Default Configuration

Default recommended configuration untuk VM:

```text
4 vCPU
16 GB
```

adalah:

```yaml
benchmark:

  operation: GET

  threads: 16

  keyCount: 1000000

  payloadBytes: 1024

  randomSeed: 123456

  warmupSeconds: 30

  durationSeconds: 60

  preload: true

  precomputeKeys: true

  maxErrorRatePercent: 1.0

  outputDirectory: ./results
```

JVM:

```text
-Xms2g
-Xmx4g
```

---

# 69. Recommended Test Procedure

Untuk setiap test combination:

```text
1. Start fresh benchmark JVM.
2. Connect to target.
3. Preload if required.
4. Validate preload.
5. Warmup 30 seconds.
6. Reset metrics.
7. Measure 60 seconds.
8. Save JSON.
9. Save CSV.
10. Close client.
11. Exit JVM.
12. Cooldown 10 seconds.
13. Start next run.
```

---

# 70. Recommended Baseline Sequence

Pertama jalankan GET dengan payload:

```text
1 KB
```

Concurrency:

```text
1
2
4
8
16
32
64
```

Redis:

```text
GET / 1 KB / 1 thread
GET / 1 KB / 2 threads
GET / 1 KB / 4 threads
GET / 1 KB / 8 threads
GET / 1 KB / 16 threads
GET / 1 KB / 32 threads
GET / 1 KB / 64 threads
```

Kemudian Hazelcast dengan matrix identik.

Setelah GET selesai, ulangi untuk SET.

---

# 71. Result Comparison

Aplikasi tidak perlu menentukan winner.

Aplikasi hanya menghasilkan raw benchmark metrics.

Comparison dapat dilakukan berdasarkan:

```text
Target
Concurrency
Payload
Operation
TPS
Latency
Error Rate
```

Contoh expected comparison table dari data benchmark:

```text
Operation : GET
Payload   : 1024 bytes

Threads | Redis TPS | Hazelcast TPS
--------|-----------|--------------
1       | ...       | ...
2       | ...       | ...
4       | ...       | ...
8       | ...       | ...
16      | ...       | ...
32      | ...       | ...
64      | ...       | ...
```

---

# 72. Acceptance Criteria

Project dianggap selesai jika seluruh requirement berikut terpenuhi.

## Build

```bash
mvn clean package
```

berhasil pada Java 17.

## RHEL

Executable JAR dapat dijalankan pada RHEL 9.

## Redis

Command berikut berhasil:

```bash
java -jar cache-benchmark.jar \
  --target redis \
  --operation GET
```

## Hazelcast

Command berikut berhasil:

```bash
java -jar cache-benchmark.jar \
  --target hazelcast \
  --operation GET
```

## Operations

Mendukung:

```text
GET
SET
```

## Workload

Mendukung configurable:

```text
threads
duration
warmup
keys
payload
random seed
```

## Warmup

Warmup tidak masuk TPS measurement.

## TPS

TPS dihitung berdasarkan:

```text
successful completed operations
/
actual measurement elapsed time
```

## Latency

Menghasilkan:

```text
p50
p95
p99
max
```

## Output

Menghasilkan:

```text
console
JSON
CSV
```

## Preload

GET workload dapat melakukan preload dataset.

## Error Handling

Failed requests tidak dihitung sebagai successful TPS.

## Connection

Client dibuat sekali sebelum benchmark.

Tidak membuat connection per request.

## Fairness

Benchmark engine yang sama digunakan untuk Redis dan Hazelcast.

## Automation

Tersedia:

```text
scripts/run-matrix.sh
```

untuk menjalankan benchmark matrix.

---

# 73. Definition of Done

Definition of Done:

```text
[ ] Maven project tersedia
[ ] Java 17 compatible
[ ] RHEL 9 compatible
[ ] Fat JAR dapat dijalankan
[ ] YAML configuration tersedia
[ ] CLI override tersedia
[ ] Redis adapter tersedia
[ ] Hazelcast adapter tersedia
[ ] GET benchmark tersedia
[ ] SET benchmark tersedia
[ ] Uniform key generator tersedia
[ ] Key precomputation tersedia
[ ] Payload configurable
[ ] Preload tersedia
[ ] Warmup tersedia
[ ] Fixed worker pool tersedia
[ ] Synchronized benchmark start tersedia
[ ] TPS measurement tersedia
[ ] Error counter tersedia
[ ] Cache miss counter tersedia
[ ] HdrHistogram latency tersedia
[ ] p50 tersedia
[ ] p95 tersedia
[ ] p99 tersedia
[ ] max latency tersedia
[ ] JSON report tersedia
[ ] CSV report tersedia
[ ] Environment metadata tersedia
[ ] Matrix runner tersedia
[ ] Unit test tersedia
[ ] README tersedia
```

---

# 74. Future Enhancements

Fitur berikut **jangan diimplementasikan dalam MVP kecuali diminta secara eksplisit**:

```text
95% GET / 5% SET workload
50% GET / 50% SET workload
Zipfian key distribution
multiple benchmark generator VMs
open-loop load generator
target request rate
async clients
Redis pipelining
batch operations
multiple Redis connections configuration
TLS comparison
authentication overhead comparison
cluster topology comparison
network latency injection
failure testing
CPU profiling
JFR integration
Prometheus metrics
Grafana dashboard
HTML benchmark report
```

Desain internal boleh memungkinkan fitur tersebut ditambahkan kemudian, tetapi implementasi MVP harus tetap sederhana.

---

# 75. Primary Design Principle

Jika terdapat pilihan implementation yang membuat benchmark lebih cepat tetapi membuat perilaku Redis dan Hazelcast menjadi berbeda, pilih implementation yang lebih sederhana dan comparable.

Prioritas:

```text
1. Fairness
2. Correctness
3. Reproducibility
4. Observability
5. Maximum benchmark-generator performance
```

Benchmark ini bertujuan menghasilkan perbandingan yang dapat dijelaskan dan diulang, bukan menghasilkan angka TPS setinggi mungkin melalui optimization khusus salah satu backend.

---

# 76. Expected Final Deliverable

Coding agent harus menghasilkan repository yang dapat digunakan seperti berikut:

```bash
git clone <repository>

cd cache-benchmark

mvn clean package
```

Kemudian:

```bash
java \
  -Xms2g \
  -Xmx4g \
  -jar target/cache-benchmark.jar \
  --config config/benchmark.yaml \
  --target redis \
  --operation GET \
  --threads 16 \
  --payload 1024 \
  --keys 1000000 \
  --warmup 30 \
  --duration 60
```

dan:

```bash
java \
  -Xms2g \
  -Xmx4g \
  -jar target/cache-benchmark.jar \
  --config config/benchmark.yaml \
  --target hazelcast \
  --operation GET \
  --threads 16 \
  --payload 1024 \
  --keys 1000000 \
  --warmup 30 \
  --duration 60
```

Kedua execution tersebut harus melewati benchmark engine, workload generation, measurement, dan reporting logic yang sama; hanya backend adapter yang berbeda.

---

# 77. Final Objective

Output akhir project harus memungkinkan operator menjalankan:

```text
Redis GET benchmark
Redis SET benchmark
Hazelcast GET benchmark
Hazelcast SET benchmark
```

dengan parameter identik dan memperoleh dataset yang dapat digunakan untuk membuat grafik:

```text
TPS
 ^
 |
 |
 |
 |
 +--------------------------------> concurrency
```

untuk melihat:

```text
throughput scaling
saturation point
TPS maksimum yang terobservasi dari benchmark VM
latency pada masing-masing concurrency level
```

tanpa memasukkan perbedaan benchmark tool sebagai variable tambahan dalam comparison.