package com.example.cachebenchmark;

import com.example.cachebenchmark.benchmark.BenchmarkResult;
import com.example.cachebenchmark.benchmark.BenchmarkRunner;
import com.example.cachebenchmark.config.BenchmarkConfig;
import com.example.cachebenchmark.metrics.ResultStatus;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Short end-to-end run against a live Hazelcast cluster (PRD section 66).
 *
 * <pre>
 * mvn verify -Pintegration -Dhz.addresses=10.10.10.21:5701 -Dhz.cluster=benchmark
 * </pre>
 */
class HazelcastBenchmarkIT {

    private static final String ADDRESSES = System.getProperty("hz.addresses", "127.0.0.1:5701");
    private static final String CLUSTER = System.getProperty("hz.cluster", "dev");
    private static final String MAP = System.getProperty("hz.map", "benchmark-map");

    private static BenchmarkConfig config(String operation) {
        BenchmarkConfig config = new BenchmarkConfig();
        config.getBenchmark().setTarget("hazelcast");
        config.getBenchmark().setOperation(operation);
        config.getBenchmark().setThreads(4);
        config.getBenchmark().setKeyCount(1_000);
        config.getBenchmark().setPayloadBytes(256);
        config.getBenchmark().setWarmupSeconds(1);
        config.getBenchmark().setDurationSeconds(5);
        config.getBenchmark().setOutputDirectory("./target/it-results");
        config.getHazelcast().setAddresses(Arrays.asList(ADDRESSES.split(",")));
        config.getHazelcast().setClusterName(CLUSTER);
        config.getHazelcast().setMapName(MAP);
        config.validate();
        return config;
    }

    @Test
    void getBenchmarkCompletes() throws InterruptedException {
        BenchmarkResult result = new BenchmarkRunner(config("GET")).run();

        assertEquals("hazelcast", result.target);
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
