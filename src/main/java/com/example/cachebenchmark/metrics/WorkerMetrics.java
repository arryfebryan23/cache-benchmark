package com.example.cachebenchmark.metrics;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Counters owned by a single worker (PRD section 28).
 *
 * <p>Nothing here is shared between threads, so no atomics and no locks are
 * needed on the measured path. The worker keeps its tallies in local variables
 * and calls {@link #flush} once, after the measurement window closes.
 */
public final class WorkerMetrics {

    private final LatencyRecorder latency = new LatencyRecorder();
    private final Map<String, ErrorSummary> errors = new LinkedHashMap<>();

    /**
     * Per-operation tallies for a MIXED run, null otherwise. A histogram is a
     * few hundred kilobytes, so single-operation runs do not pay for two that
     * would stay empty.
     */
    private final OperationMetrics getMetrics;
    private final OperationMetrics setMetrics;

    private long attempted;
    private long successful;
    private long failed;
    private long hits;
    private long misses;

    public WorkerMetrics() {
        this(false);
    }

    public WorkerMetrics(boolean perOperation) {
        this.getMetrics = perOperation ? new OperationMetrics() : null;
        this.setMetrics = perOperation ? new OperationMetrics() : null;
    }

    public LatencyRecorder latency() {
        return latency;
    }

    /** GET side of a MIXED run; null for a single-operation run. */
    public OperationMetrics getMetrics() {
        return getMetrics;
    }

    /** SET side of a MIXED run; null for a single-operation run. */
    public OperationMetrics setMetrics() {
        return setMetrics;
    }

    /** Called from the worker thread only, on the failure path. */
    public void recordError(Throwable t) {
        String type = t.getClass().getSimpleName();
        ErrorSummary summary = errors.get(type);
        if (summary == null) {
            String message = t.getMessage();
            summary = new ErrorSummary(type, message == null ? t.getClass().getName() : message);
            errors.put(type, summary);
        }
        summary.increment();
    }

    /** Publishes the worker's local tallies once the loop has finished. */
    public void flush(long attempted, long successful, long failed, long hits, long misses) {
        this.attempted = attempted;
        this.successful = successful;
        this.failed = failed;
        this.hits = hits;
        this.misses = misses;
    }

    public long attempted() { return attempted; }
    public long successful() { return successful; }
    public long failed() { return failed; }
    public long hits() { return hits; }
    public long misses() { return misses; }

    public Map<String, ErrorSummary> errors() {
        return errors;
    }
}
