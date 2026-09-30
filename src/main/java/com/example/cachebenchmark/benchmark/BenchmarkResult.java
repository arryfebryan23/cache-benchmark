package com.example.cachebenchmark.benchmark;

import com.example.cachebenchmark.metrics.EnvironmentInfo;
import com.example.cachebenchmark.metrics.ResultStatus;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The complete outcome of one benchmark invocation (PRD sections 33 and 34).
 *
 * <p>Plain public fields on purpose: this is a data transfer object that
 * Jackson writes straight to JSON, and the field order here is the order that
 * appears in the file.
 */
@JsonPropertyOrder({
        "benchmarkVersion", "timestamp",
        "target", "operation",
        "threads", "keyCount", "payloadBytes", "randomSeed",
        "warmupSeconds", "configuredDurationSeconds", "actualDurationSeconds",
        "attemptedOperations", "successfulOperations", "failedOperations",
        "cacheHits", "cacheMisses",
        "tps", "latencyMs", "errorRatePercent", "status",
        "preloaded", "errors", "gc", "cpu", "connection", "fairness", "environment"
})
public final class BenchmarkResult {

    public String benchmarkVersion;
    public String timestamp;

    public String target;
    public String operation;

    public int threads;
    public int keyCount;
    public int payloadBytes;
    public long randomSeed;

    public int warmupSeconds;
    public int configuredDurationSeconds;
    public double actualDurationSeconds;

    public long attemptedOperations;
    public long successfulOperations;
    public long failedOperations;

    public long cacheHits;
    public long cacheMisses;

    public double tps;
    public Latency latencyMs = new Latency();
    public double errorRatePercent;
    public ResultStatus status;

    public boolean preloaded;

    /** Error type to count plus one sample message (PRD section 61). */
    public Map<String, ErrorDetail> errors = new LinkedHashMap<>();

    public Gc gc = new Gc();
    public Cpu cpu = new Cpu();
    public Connection connection = new Connection();
    public Fairness fairness = new Fairness();
    public EnvironmentInfo environment;

    @JsonPropertyOrder({"p50", "p95", "p99", "p999", "mean", "max"})
    public static final class Latency {
        public double p50;
        public double p95;
        public double p99;
        public double p999;
        public double mean;
        public double max;
    }

    @JsonPropertyOrder({"count", "sampleMessage"})
    public static final class ErrorDetail {
        public long count;
        public String sampleMessage;

        public ErrorDetail() {
        }

        public ErrorDetail(long count, String sampleMessage) {
            this.count = count;
            this.sampleMessage = sampleMessage;
        }
    }

    @JsonPropertyOrder({"collections", "timeMs"})
    public static final class Gc {
        public long collections;
        public long timeMs;
    }

    @JsonPropertyOrder({"availableProcessors", "processCpuLoadAvg", "processCpuLoadMax"})
    public static final class Cpu {
        public int availableProcessors;
        public Double processCpuLoadAvg;
        public Double processCpuLoadMax;
    }

    @JsonPropertyOrder({"description", "nativeClientConnectionManagement"})
    public static final class Connection {
        public String description;
        /** PRD section 43 requires the report to state this explicitly. */
        public boolean nativeClientConnectionManagement = true;
    }

    /**
     * The constraints that make this number comparable with the other backend
     * (PRD section 42). Written into every result so a stray JSON file can
     * still be judged on its own.
     */
    @JsonPropertyOrder({
            "benchmarkModel", "sharedBenchmarkEngine", "keyDistribution", "keyPattern",
            "valueType", "explicitPipelining", "explicitBatching", "asynchronousWorkload",
            "precomputedKeys", "reusedPayload", "operationTimeoutSeconds"
    })
    public static final class Fairness {
        public String benchmarkModel = "closed-loop-synchronous";
        public boolean sharedBenchmarkEngine = true;
        public String keyDistribution = "uniform";
        public String keyPattern = "benchmark:<number>";
        public String valueType = "byte[]";
        public boolean explicitPipelining = false;
        public boolean explicitBatching = false;
        public boolean asynchronousWorkload = false;
        public boolean precomputedKeys;
        public boolean reusedPayload = true;
        public int operationTimeoutSeconds;
    }
}
