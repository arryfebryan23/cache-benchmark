package com.example.cachebenchmark.benchmark;

import com.example.cachebenchmark.config.BenchmarkConfig;
import com.example.cachebenchmark.metrics.ResultStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the whole engine against a test double, so the phase logic is
 * covered without a live backend (PRD section 66: the normal build must not
 * need Redis or Hazelcast).
 */
class BenchmarkEngineTest {

    private static BenchmarkConfig config(String operation, int warmup, int duration) {
        BenchmarkConfig config = new BenchmarkConfig();
        config.getBenchmark().setTarget("redis");
        config.getBenchmark().setOperation(operation);
        config.getBenchmark().setThreads(2);
        config.getBenchmark().setKeyCount(200);
        config.getBenchmark().setPayloadBytes(64);
        config.getBenchmark().setWarmupSeconds(warmup);
        config.getBenchmark().setDurationSeconds(duration);
        config.getBenchmark().setPreload(true);
        return config;
    }

    @Test
    void warmupOperationsStayOutOfTheResult() throws InterruptedException {
        InMemoryCacheClient client = new InMemoryCacheClient();
        BenchmarkResult result = new BenchmarkRunner(config("GET", 1, 1)).run(client);

        // The client saw preload writes plus warmup reads plus measurement
        // reads. The result must only account for the last of those.
        long preloadWrites = 200;
        long accountedFor = result.attemptedOperations + preloadWrites;

        assertTrue(result.attemptedOperations > 0, "measurement recorded nothing");
        assertTrue(client.totalCalls() > accountedFor,
                "expected warmup traffic on top of the " + accountedFor
                        + " accounted operations, client saw " + client.totalCalls());
    }

    @Test
    void throughputIsSuccessfulOperationsOverActualElapsedTime() throws InterruptedException {
        InMemoryCacheClient client = new InMemoryCacheClient();
        BenchmarkResult result = new BenchmarkRunner(config("GET", 0, 1)).run(client);

        assertEquals(result.successfulOperations / result.actualDurationSeconds, result.tps, 0.0001);
        assertTrue(result.actualDurationSeconds >= 1.0,
                "measurement was shorter than configured: " + result.actualDurationSeconds);
    }

    @Test
    void getRunPreloadsTheKeyspaceAndReportsHits() throws InterruptedException {
        InMemoryCacheClient client = new InMemoryCacheClient();
        BenchmarkResult result = new BenchmarkRunner(config("GET", 0, 1)).run(client);

        assertEquals(200, client.size());
        assertTrue(result.preloaded);
        assertEquals(0, result.cacheMisses);
        assertEquals(result.successfulOperations, result.cacheHits);
        assertEquals(ResultStatus.VALID, result.status);
    }

    @Test
    void setRunSkipsPreload() throws InterruptedException {
        InMemoryCacheClient client = new InMemoryCacheClient();
        BenchmarkResult result = new BenchmarkRunner(config("SET", 0, 1)).run(client);

        assertFalse(result.preloaded);
        assertEquals("SET", result.operation);
        assertTrue(result.successfulOperations > 0);
    }

    @Test
    void getRunAgainstAnEmptyKeyspaceIsStopped() {
        // The failure this guards against: an unpopulated GET benchmark does
        // not error, it returns nulls very fast and looks like a great result.
        BenchmarkConfig config = config("GET", 0, 1);
        config.getBenchmark().setPreload(false);

        InMemoryCacheClient client = new InMemoryCacheClient();
        PreloadValidationException e = assertThrows(PreloadValidationException.class,
                () -> new BenchmarkRunner(config).run(client));
        assertTrue(e.getMessage().startsWith(PreloadValidationException.CODE), e.getMessage());
    }

    @Test
    void failedOperationsAreCountedAndMarkTheRunInvalid() throws InterruptedException {
        InMemoryCacheClient client = new InMemoryCacheClient();
        BenchmarkConfig config = config("SET", 0, 1);
        client.failEveryCall(true);

        BenchmarkResult result = new BenchmarkRunner(config).run(client);

        assertEquals(0, result.successfulOperations);
        assertTrue(result.failedOperations > 0);
        assertEquals(100.0, result.errorRatePercent, 0.0001);
        assertEquals(ResultStatus.INVALID, result.status);
        assertTrue(result.errors.containsKey("IllegalStateException"));
        assertEquals(result.failedOperations, result.errors.get("IllegalStateException").count);
        assertEquals("injected failure", result.errors.get("IllegalStateException").sampleMessage);
    }

    @Test
    void everyAttemptIsEitherSuccessfulOrFailed() throws InterruptedException {
        InMemoryCacheClient client = new InMemoryCacheClient();
        BenchmarkResult result = new BenchmarkRunner(config("SET", 0, 1)).run(client);

        assertEquals(result.attemptedOperations,
                result.successfulOperations + result.failedOperations);
    }
}
