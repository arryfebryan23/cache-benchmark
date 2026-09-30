package com.example.cachebenchmark.metrics;

import org.HdrHistogram.Histogram;

/**
 * Per-worker latency histogram (PRD section 30).
 *
 * <p>Values are recorded in nanoseconds and reported in milliseconds. Each
 * worker owns one recorder so the measured loop never touches a shared,
 * contended structure; the histograms are merged once at the end.
 *
 * <p>Only successful operations are recorded. A failed operation has no
 * meaningful service time, and mixing timeouts into the distribution would
 * make the percentiles describe the timeout setting rather than the backend.
 */
public final class LatencyRecorder {

    /** 1 microsecond. Network round trips never land below this. */
    private static final long LOWEST_DISCERNIBLE_NANOS = 1_000L;

    /** 60 seconds. Well above any sane operation timeout. */
    private static final long HIGHEST_TRACKABLE_NANOS = 60_000_000_000L;

    private static final int SIGNIFICANT_DIGITS = 3;

    private final Histogram histogram;

    public LatencyRecorder() {
        this.histogram = new Histogram(LOWEST_DISCERNIBLE_NANOS, HIGHEST_TRACKABLE_NANOS, SIGNIFICANT_DIGITS);
    }

    public void record(long latencyNanos) {
        long value = latencyNanos;
        if (value < 0) {
            value = 0;
        } else if (value > HIGHEST_TRACKABLE_NANOS) {
            value = HIGHEST_TRACKABLE_NANOS;
        }
        histogram.recordValue(value);
    }

    public Histogram histogram() {
        return histogram;
    }

    public static Histogram newEmptyHistogram() {
        return new Histogram(LOWEST_DISCERNIBLE_NANOS, HIGHEST_TRACKABLE_NANOS, SIGNIFICANT_DIGITS);
    }
}
