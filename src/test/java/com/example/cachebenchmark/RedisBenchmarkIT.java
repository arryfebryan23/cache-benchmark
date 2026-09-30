package com.example.cachebenchmark;

import com.example.cachebenchmark.benchmark.BenchmarkResult;
import com.example.cachebenchmark.benchmark.BenchmarkRunner;
import com.example.cachebenchmark.config.BenchmarkConfig;
import com.example.cachebenchmark.metrics.ResultStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Short end-to-end run against a live Redis (PRD section 66).
 *
 * <p>Only runs under {@code mvn verify -Pintegration}. It exists to prove the
 * adapter and the engine work together, not to produce a number worth quoting:
 * five seconds with a tiny keyspace measures nothing meaningful.
 *
 * <pre>
 * mvn verify -Pintegration -Dredis.host=10.10.10.11 -Dredis.port=6379
 * </pre>
 */
class RedisBenchmarkIT {

    private static final String HOST = System.getProperty("redis.host", "127.0.0.1");
    private static final int PORT = Integer.getInteger("redis.port", 6379);

    private static BenchmarkConfig config(String operation) {
        BenchmarkConfig config = new BenchmarkConfig();
        config.getBenchmark().setTarget("redis");
        config.getBenchmark().setOperation(operation);
        config.getBenchmark().setThreads(4);
        config.getBenchmark().setKeyCount(1_000);
        config.getBenchmark().setPayloadBytes(256);
        config.getBenchmark().setWarmupSeconds(1);
        config.getBenchmark().setDurationSeconds(5);
        config.getBenchmark().setOutputDirectory("./target/it-results");
        config.getRedis().setHost(HOST);
        config.getRedis().setPort(PORT);
        config.validate();
        return config;
    }

    @Test
    void getBenchmarkCompletes() throws InterruptedException {
        BenchmarkResult result = new BenchmarkRunner(config("GET")).run();

        assertEquals("redis", result.target);
        assertEquals(ResultStatus.VALID, result.status);
        assertTrue(result.tps > 0, "no throughput recorded");
        assertEquals(0, result.cacheMisses, "preloaded keyspace should not miss");
        assertTrue(result.latencyMs.p50 > 0, "no latency recorded");
    }

    @Test
    void setBenchmarkCompletes() throws InterruptedException {
        BenchmarkResult result = new BenchmarkRunner(config("SET")).run();

        assertEquals(ResultStatus.VALID, result.status);
        assertTrue(result.tps > 0, "no throughput recorded");
    }
}
