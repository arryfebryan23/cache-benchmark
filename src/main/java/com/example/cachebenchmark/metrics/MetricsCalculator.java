package com.example.cachebenchmark.metrics;

/**
 * The arithmetic behind the headline numbers, kept in one place so it can be
 * unit tested without a backend (PRD sections 26, 29 and 65).
 */
public final class MetricsCalculator {

    private MetricsCalculator() {
    }

    /**
     * Throughput (PRD section 26). Only successful operations count, and the
     * denominator is the actual elapsed measurement time rather than the
     * configured duration, so a small overshoot at the end does not distort
     * the result (PRD section 27).
     */
    public static double tps(long successfulOperations, double actualElapsedSeconds) {
        if (actualElapsedSeconds <= 0) {
            throw new IllegalArgumentException(
                    "actualElapsedSeconds must be greater than zero, was " + actualElapsedSeconds);
        }
        if (successfulOperations < 0) {
            throw new IllegalArgumentException(
                    "successfulOperations must not be negative, was " + successfulOperations);
        }
        return successfulOperations / actualElapsedSeconds;
    }

    /** Error rate as a percentage (PRD section 29). Zero attempts means zero errors. */
    public static double errorRatePercent(long failedOperations, long attemptedOperations) {
        if (attemptedOperations <= 0) {
            return 0.0;
        }
        return (failedOperations * 100.0) / attemptedOperations;
    }

    /** A run at exactly the threshold is still VALID; only exceeding it is not. */
    public static ResultStatus status(double errorRatePercent, double maxErrorRatePercent) {
        return errorRatePercent > maxErrorRatePercent ? ResultStatus.INVALID : ResultStatus.VALID;
    }

    public static double nanosToMillis(double nanos) {
        return nanos / 1_000_000.0;
    }
}
