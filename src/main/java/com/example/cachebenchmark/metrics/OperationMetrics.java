package com.example.cachebenchmark.metrics;

/**
 * Counters and latency for one operation type inside a MIXED run.
 *
 * <p>Owned by a single worker, like {@link WorkerMetrics}: the worker keeps
 * its tallies in locals and calls {@link #flush} once at the end.
 */
public final class OperationMetrics {

    private final LatencyRecorder latency = new LatencyRecorder();

    private long attempted;
    private long successful;
    private long failed;

    public LatencyRecorder latency() {
        return latency;
    }

    public void flush(long attempted, long successful, long failed) {
        this.attempted = attempted;
        this.successful = successful;
        this.failed = failed;
    }

    public long attempted() { return attempted; }
    public long successful() { return successful; }
    public long failed() { return failed; }
}
