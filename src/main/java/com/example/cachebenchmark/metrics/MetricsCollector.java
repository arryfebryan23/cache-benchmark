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

    public MetricsCollector(int threadCount) {
        if (threadCount <= 0) {
            throw new IllegalArgumentException("threadCount must be greater than zero, was " + threadCount);
        }
        List<WorkerMetrics> list = new ArrayList<>(threadCount);
        for (int i = 0; i < threadCount; i++) {
            list.add(new WorkerMetrics());
        }
        this.workers = Collections.unmodifiableList(list);
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

    /** All per-worker histograms combined into one distribution. */
    public Histogram mergedHistogram() {
        Histogram merged = LatencyRecorder.newEmptyHistogram();
        for (WorkerMetrics w : workers) {
            merged.add(w.latency().histogram());
        }
        return merged;
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
