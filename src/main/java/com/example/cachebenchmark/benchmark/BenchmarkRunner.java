package com.example.cachebenchmark.benchmark;

import com.example.cachebenchmark.AppInfo;
import com.example.cachebenchmark.client.CacheClient;
import com.example.cachebenchmark.client.CacheClientFactory;
import com.example.cachebenchmark.config.BenchmarkConfig;
import com.example.cachebenchmark.config.Operation;
import com.example.cachebenchmark.key.KeySpace;
import com.example.cachebenchmark.metrics.CpuSampler;
import com.example.cachebenchmark.metrics.EnvironmentInfo;
import com.example.cachebenchmark.metrics.ErrorSummary;
import com.example.cachebenchmark.metrics.GcSnapshot;
import com.example.cachebenchmark.metrics.MetricsCalculator;
import com.example.cachebenchmark.metrics.MetricsCollector;
import com.example.cachebenchmark.workload.PayloadFactory;
import com.example.cachebenchmark.workload.Workload;
import org.HdrHistogram.Histogram;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Drives one benchmark invocation through its phases (PRD section 20).
 *
 * <p>This class knows nothing about Redis or Hazelcast. It only holds a
 * {@link CacheClient}. That is the whole point of the design: both backends go
 * through this identical code path, so any difference in the result comes from
 * the backend and not from the harness (PRD section 4.1).
 */
public final class BenchmarkRunner {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkRunner.class);

    /** How many keys the post-preload sanity check reads back (PRD section 19). */
    private static final int PRELOAD_SAMPLE_SIZE = 100;

    private static final int PRELOAD_PROGRESS_STEP = 100_000;

    private final BenchmarkConfig config;

    public BenchmarkRunner(BenchmarkConfig config) {
        this.config = config;
    }

    /**
     * Runs the benchmark against the configured backend.
     *
     * <p>The client is opened once here and closed on the way out, never per
     * operation (Fairness Rule 12).
     */
    public BenchmarkResult run() throws InterruptedException {
        try (CacheClient client = CacheClientFactory.create(config)) {
            return run(client);
        } finally {
            log.info("Phase {}", BenchmarkPhase.SHUTDOWN);
        }
    }

    /**
     * Same run, against a client the caller supplies and owns.
     *
     * <p>Exists so the engine can be exercised without a live Redis or
     * Hazelcast. Everything a real run does after connecting happens here.
     */
    BenchmarkResult run(CacheClient client) throws InterruptedException {
        BenchmarkConfig.Benchmark bench = config.getBenchmark();
        Operation operation = config.operation();

        // ---------------- INITIALIZATION ----------------
        log.info("Phase {}", BenchmarkPhase.INITIALIZATION);
        log.info("target={} operation={} threads={} keys={} payloadBytes={} warmup={}s duration={}s seed={}",
                config.target().lowerCase(), operation, bench.getThreads(), bench.getKeyCount(),
                bench.getPayloadBytes(), bench.getWarmupSeconds(), bench.getDurationSeconds(),
                bench.getRandomSeed());

        long keyBuildStart = System.nanoTime();
        KeySpace keySpace = KeySpace.create(bench.getKeyCount(), bench.isPrecomputeKeys());
        byte[] payload = PayloadFactory.create(bench.getPayloadBytes());
        Workload workload = PayloadFactory.workloadFor(operation);
        log.info("Keyspace ready: {} keys, precomputed={} ({} ms)",
                keySpace.keyCount(), keySpace.isPrecomputed(),
                (System.nanoTime() - keyBuildStart) / 1_000_000);

        client.validateConnection();
        log.info("Connected: {}", client.connectionDescription());

        // ---------------- PRELOAD ----------------
        boolean preloaded = maybePreload(client, keySpace, payload, operation);

        // ---------------- WARMUP ----------------
        runWarmup(client, workload, keySpace, payload);

        // ---------------- MEASUREMENT ----------------
        log.info("Phase {} starting, {} s", BenchmarkPhase.MEASUREMENT, bench.getDurationSeconds());
        MetricsCollector collector = new MetricsCollector(bench.getThreads());
        GcSnapshot gcBefore = GcSnapshot.capture();

        try (CpuSampler cpuSampler = new CpuSampler()) {
            cpuSampler.start();
            double elapsedSeconds = runPhase(BenchmarkPhase.MEASUREMENT, bench.getDurationSeconds(),
                    client, workload, keySpace, payload, collector);
            GcSnapshot gcDelta = GcSnapshot.capture().since(gcBefore);
            log.info("Phase {} finished in {} s", BenchmarkPhase.MEASUREMENT,
                    String.format("%.3f", elapsedSeconds));

            // ---------------- RESULT ----------------
            return buildResult(collector, elapsedSeconds, gcDelta, cpuSampler, client, preloaded);
        }
    }

    // -----------------------------------------------------------------------
    // Preload (PRD sections 18 and 19)
    // -----------------------------------------------------------------------

    /**
     * Loads the keyspace when the workload needs it, then always verifies it.
     *
     * <p>Verification runs whether or not this invocation did the loading. A
     * GET run against an empty keyspace returns nulls very fast and looks like
     * a great result, so the check is the only thing standing between that and
     * a comparison table.
     *
     * @return true when this invocation performed the load
     */
    private boolean maybePreload(CacheClient client, KeySpace keySpace, byte[] payload, Operation operation)
            throws InterruptedException {
        BenchmarkConfig.Benchmark bench = config.getBenchmark();

        if (!operation.isRead()) {
            log.info("Phase {} skipped: {} writes its own data", BenchmarkPhase.PRELOAD, operation);
            return false;
        }

        boolean loaded = false;
        if (bench.isPreload()) {
            log.info("Phase {} starting, {} keys of {} bytes",
                    BenchmarkPhase.PRELOAD, keySpace.keyCount(), payload.length);
            preload(client, keySpace, payload, bench.getThreads());
            loaded = true;
        } else {
            log.warn("preload=false for a {} benchmark. The keyspace is assumed to be already "
                    + "populated with {} keys of {} bytes. If it is not, this run measures cache "
                    + "misses rather than reads.", operation, keySpace.keyCount(), payload.length);
        }

        verifyPreload(client, keySpace, payload);
        return loaded;
    }

    private void preload(CacheClient client, KeySpace keySpace, byte[] payload, int threads)
            throws InterruptedException {
        long start = System.nanoTime();
        int keyCount = keySpace.keyCount();

        ExecutorService pool = Executors.newFixedThreadPool(threads, namedThreadFactory("preload"));
        CountDownLatch done = new CountDownLatch(threads);
        AtomicLong completed = new AtomicLong();
        AtomicReference<RuntimeException> failure = new AtomicReference<>();

        int chunk = (keyCount + threads - 1) / threads;
        for (int t = 0; t < threads; t++) {
            final int from = t * chunk;
            final int to = Math.min(keyCount, from + chunk);
            pool.execute(() -> {
                try {
                    for (int i = from; i < to; i++) {
                        client.set(keySpace.key(i), payload);
                        long n = completed.incrementAndGet();
                        if (n % PRELOAD_PROGRESS_STEP == 0) {
                            log.info("Preload {} / {}", n, keyCount);
                        }
                    }
                } catch (RuntimeException e) {
                    failure.compareAndSet(null, e);
                } finally {
                    done.countDown();
                }
            });
        }

        done.await();
        shutdownQuietly(pool);

        RuntimeException error = failure.get();
        if (error != null) {
            throw new PreloadValidationException(PreloadValidationException.CODE
                    + ": preload failed after " + completed.get() + " of " + keyCount
                    + " keys: " + error);
        }

        double seconds = (System.nanoTime() - start) / 1_000_000_000.0;
        log.info("Preload complete: {} keys in {} s ({} keys/s)",
                keyCount, String.format("%.1f", seconds),
                String.format("%.0f", keyCount / Math.max(seconds, 0.001)));
    }

    /**
     * Reads back a sample of keys and requires every one of them to be present
     * with the expected size. The first, middle and last keys are always
     * included; the rest are drawn from a fixed seed so the check is repeatable.
     */
    private void verifyPreload(CacheClient client, KeySpace keySpace, byte[] payload) {
        int keyCount = keySpace.keyCount();
        int sampleSize = Math.min(PRELOAD_SAMPLE_SIZE, keyCount);

        int[] indexes = new int[sampleSize];
        indexes[0] = 0;
        if (sampleSize > 1) {
            indexes[1] = keyCount - 1;
        }
        if (sampleSize > 2) {
            indexes[2] = keyCount / 2;
        }
        SplittableRandom random = new SplittableRandom(0xC0FFEEL);
        for (int i = 3; i < sampleSize; i++) {
            indexes[i] = random.nextInt(keyCount);
        }

        for (int index : indexes) {
            String key = keySpace.key(index);
            byte[] value = client.get(key);
            if (value == null) {
                throw new PreloadValidationException(PreloadValidationException.CODE
                        + ": key " + key + " is missing. The keyspace is not fully populated, "
                        + "so a read benchmark would measure cache misses.");
            }
            if (value.length != payload.length) {
                throw new PreloadValidationException(PreloadValidationException.CODE
                        + ": key " + key + " holds " + value.length + " bytes but the configured "
                        + "payload is " + payload.length + " bytes. The keyspace was loaded with a "
                        + "different payload size.");
            }
        }

        log.info("Preload validation passed: {} sampled keys present with {} byte values",
                sampleSize, payload.length);
    }

    // -----------------------------------------------------------------------
    // Warmup and measurement (PRD sections 21, 22, 23, 24, 27)
    // -----------------------------------------------------------------------

    private void runWarmup(CacheClient client, Workload workload, KeySpace keySpace, byte[] payload)
            throws InterruptedException {
        int warmupSeconds = config.getBenchmark().getWarmupSeconds();
        if (warmupSeconds == 0) {
            log.warn("Phase {} skipped: warmupSeconds=0. JIT compilation and connection setup "
                    + "will land inside the measurement window.", BenchmarkPhase.WARMUP);
            return;
        }

        log.info("Phase {} starting, {} s", BenchmarkPhase.WARMUP, warmupSeconds);

        // A throwaway collector. Discarding it is the reset required by PRD
        // section 21: counters, error counts and the histogram all begin the
        // measurement phase empty because they are brand new objects.
        MetricsCollector warmupMetrics = new MetricsCollector(config.getBenchmark().getThreads());
        double elapsed = runPhase(BenchmarkPhase.WARMUP, warmupSeconds, client, workload,
                keySpace, payload, warmupMetrics);

        log.info("Phase {} finished in {} s, {} operations discarded",
                BenchmarkPhase.WARMUP, String.format("%.3f", elapsed), warmupMetrics.attempted());
    }

    /**
     * Runs the worker pool for a fixed wall-clock window.
     *
     * <p>All workers park on a start latch and the clock only starts once the
     * last one has arrived (PRD section 24). They share a single deadline, so
     * no worker stops earlier or later than the others.
     *
     * @return actual elapsed seconds, used as the TPS denominator so that
     *         overshoot from in-flight requests does not inflate throughput
     *         (PRD section 27)
     */
    private double runPhase(BenchmarkPhase phase,
                            int seconds,
                            CacheClient client,
                            Workload workload,
                            KeySpace keySpace,
                            byte[] payload,
                            MetricsCollector collector) throws InterruptedException {
        int threads = config.getBenchmark().getThreads();
        long randomSeed = config.getBenchmark().getRandomSeed();

        ExecutorService pool = Executors.newFixedThreadPool(threads,
                namedThreadFactory(phase.name().toLowerCase()));
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicLong deadline = new AtomicLong();

        for (int i = 0; i < threads; i++) {
            pool.execute(new Worker(i, client, workload, keySpace, payload, randomSeed,
                    collector.forWorker(i), ready, start, done, deadline));
        }

        ready.await();
        long startNanos = System.nanoTime();
        deadline.set(startNanos + TimeUnit.SECONDS.toNanos(seconds));
        start.countDown();

        done.await();
        long endNanos = System.nanoTime();

        shutdownQuietly(pool);
        return (endNanos - startNanos) / 1_000_000_000.0;
    }

    // -----------------------------------------------------------------------
    // Result assembly (PRD sections 26, 28, 29, 30, 33, 34)
    // -----------------------------------------------------------------------

    private BenchmarkResult buildResult(MetricsCollector collector,
                                        double elapsedSeconds,
                                        GcSnapshot gcDelta,
                                        CpuSampler cpuSampler,
                                        CacheClient client,
                                        boolean preloaded) {
        log.info("Phase {}", BenchmarkPhase.RESULT);
        BenchmarkConfig.Benchmark bench = config.getBenchmark();

        BenchmarkResult result = new BenchmarkResult();
        result.benchmarkVersion = AppInfo.VERSION;
        result.timestamp = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        result.target = config.target().lowerCase();
        result.operation = config.operation().name();

        result.threads = bench.getThreads();
        result.keyCount = bench.getKeyCount();
        result.payloadBytes = bench.getPayloadBytes();
        result.randomSeed = bench.getRandomSeed();

        result.warmupSeconds = bench.getWarmupSeconds();
        result.configuredDurationSeconds = bench.getDurationSeconds();
        result.actualDurationSeconds = elapsedSeconds;

        result.attemptedOperations = collector.attempted();
        result.successfulOperations = collector.successful();
        result.failedOperations = collector.failed();
        result.cacheHits = collector.hits();
        result.cacheMisses = collector.misses();

        result.tps = MetricsCalculator.tps(result.successfulOperations, elapsedSeconds);
        result.errorRatePercent = MetricsCalculator.errorRatePercent(
                result.failedOperations, result.attemptedOperations);
        result.status = MetricsCalculator.status(result.errorRatePercent, bench.getMaxErrorRatePercent());
        result.preloaded = preloaded;

        Histogram histogram = collector.mergedHistogram();
        result.latencyMs.p50 = MetricsCalculator.nanosToMillis(histogram.getValueAtPercentile(50.0));
        result.latencyMs.p95 = MetricsCalculator.nanosToMillis(histogram.getValueAtPercentile(95.0));
        result.latencyMs.p99 = MetricsCalculator.nanosToMillis(histogram.getValueAtPercentile(99.0));
        result.latencyMs.p999 = MetricsCalculator.nanosToMillis(histogram.getValueAtPercentile(99.9));
        result.latencyMs.mean = MetricsCalculator.nanosToMillis(histogram.getMean());
        result.latencyMs.max = MetricsCalculator.nanosToMillis(histogram.getMaxValue());

        for (Map.Entry<String, ErrorSummary> entry : collector.mergedErrors().entrySet()) {
            result.errors.put(entry.getKey(),
                    new BenchmarkResult.ErrorDetail(entry.getValue().getCount(),
                            entry.getValue().getSampleMessage()));
        }

        result.gc.collections = gcDelta.getCollections();
        result.gc.timeMs = gcDelta.getTimeMillis();

        result.cpu.availableProcessors = Runtime.getRuntime().availableProcessors();
        result.cpu.processCpuLoadAvg = orNull(cpuSampler.averageLoad());
        result.cpu.processCpuLoadMax = orNull(cpuSampler.maxLoad());

        result.connection.description = client.connectionDescription();
        result.fairness.precomputedKeys = bench.isPrecomputeKeys();
        result.fairness.operationTimeoutSeconds = bench.getOperationTimeoutSeconds();
        result.environment = EnvironmentInfo.capture();

        return result;
    }

    private static Double orNull(double value) {
        return Double.isNaN(value) ? null : value;
    }

    private static ThreadFactory namedThreadFactory(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + counter.getAndIncrement());
            thread.setDaemon(false);
            return thread;
        };
    }

    private static void shutdownQuietly(ExecutorService pool) {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(30, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
