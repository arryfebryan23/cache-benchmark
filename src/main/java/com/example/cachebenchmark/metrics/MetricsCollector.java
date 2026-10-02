package com.example.cachebenchmark.metrics;

import org.HdrHistogram.Histogram;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds one {@link WorkerMetrics} per worker and merges them once the phase
 * ends.
 *
 * <p>A fresh collector is created for each phase. That is how warmup results
 * are discarded: the warmup collector is simply thrown away and measurement
 * starts from an empty one, which satisfies the reset requirement in PRD
 * section 21 without any mutable reset logic to get wrong.
 */
public final class MetricsCollector {

    private final List<WorkerMetrics> workers;

    private final boolean perOperation;

    public MetricsCollector(int threadCount) {
        this(threadCount, false);
    }

    /**
     * @param perOperation also keep GET and SET apart, for a MIXED run
     */
    public MetricsCollector(int threadCount, boolean perOperation) {
        if (threadCount <= 0) {
            throw new IllegalArgumentException("threadCount must be greater than zero, was " + threadCount);
        }
        List<WorkerMetrics> list = new ArrayList<>(threadCount);
        for (int i = 0; i < threadCount; i++) {
            list.add(new WorkerMetrics(perOperation));
        }
        this.workers = Collections.unmodifiableList(list);
        this.perOperation = perOperation;
    }

    public boolean isPerOperation() {
        return perOperation;
    }

    public WorkerMetrics forWorker(int workerId) {
        return workers.get(workerId);
    }

    public long attempted() {
        long total = 0;
        for (WorkerMetrics w : workers) {
            total += w.attempted();
        }
        return total;
    }

    public long successful() {
        long total = 0;
        for (WorkerMetrics w : workers) {
            total += w.successful();
        }
        return total;
    }

    public long failed() {
        long total = 0;
        for (WorkerMetrics w : workers) {
            total += w.failed();
        }
        return total;
    }

    public long hits() {
        long total = 0;
        for (WorkerMetrics w : workers) {
            total += w.hits();
        }
        return total;
    }

    public long misses() {
        long total = 0;
        for (WorkerMetrics w : workers) {
            total += w.misses();
        }
        return total;
    }

    /**
     * All per-worker histograms combined into one distribution. For a MIXED
     * run this is GET and SET together, since the worker records each
     * operation only into its per-operation histogram.
     */
    public Histogram mergedHistogram() {
        Histogram merged = LatencyRecorder.newEmptyHistogram();
        for (WorkerMetrics w : workers) {
            merged.add(w.latency().histogram());
            if (perOperation) {
                merged.add(w.getMetrics().latency().histogram());
                merged.add(w.setMetrics().latency().histogram());
            }
        }
        return merged;
    }

    /**
     * One operation type of a MIXED run, summed across workers.
     *
     * @param set true for the SET side, false for GET
     */
    public OperationTotals operationTotals(boolean set) {
        if (!perOperation) {
            throw new IllegalStateException("per-operation metrics were not collected");
        }
        long attempted = 0;
        long successful = 0;
        long failed = 0;
        Histogram histogram = LatencyRecorder.newEmptyHistogram();
        for (WorkerMetrics w : workers) {
            OperationMetrics m = set ? w.setMetrics() : w.getMetrics();
            attempted += m.attempted();
            successful += m.successful();
            failed += m.failed();
            histogram.add(m.latency().histogram());
        }
        return new OperationTotals(attempted, successful, failed, histogram);
    }

    /** Totals for one operation type across all workers. */
    public static final class OperationTotals {
        public final long attempted;
        public final long successful;
        public final long failed;
        public final Histogram histogram;

        OperationTotals(long attempted, long successful, long failed, Histogram histogram) {
            this.attempted = attempted;
            this.successful = successful;
            this.failed = failed;
            this.histogram = histogram;
        }
    }

    /** Error types across all workers, counts summed, first sample message kept. */
    public Map<String, ErrorSummary> mergedErrors() {
        Map<String, ErrorSummary> merged = new LinkedHashMap<>();
        for (WorkerMetrics w : workers) {
            for (Map.Entry<String, ErrorSummary> entry : w.errors().entrySet()) {
                ErrorSummary existing = merged.get(entry.getKey());
                if (existing == null) {
                    existing = new ErrorSummary(entry.getKey(), entry.getValue().getSampleMessage());
                    merged.put(entry.getKey(), existing);
                }
                existing.add(entry.getValue().getCount());
            }
        }
        return merged;
    }
}
