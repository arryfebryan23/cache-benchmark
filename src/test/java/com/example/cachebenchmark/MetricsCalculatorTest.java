package com.example.cachebenchmark;

import com.example.cachebenchmark.metrics.MetricsCalculator;
import com.example.cachebenchmark.metrics.ResultStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** PRD section 65: throughput formula and result status. */
class MetricsCalculatorTest {

    @Test
    void oneMillionOperationsInTenSecondsIsOneHundredThousandTps() {
        assertEquals(100_000.0, MetricsCalculator.tps(1_000_000L, 10.0), 0.0001);
    }

    @Test
    void usesActualElapsedTimeNotTheConfiguredDuration() {
        // 60.013 s of real measurement, not the configured 60 s (PRD section 27)
        double tps = MetricsCalculator.tps(20_453_221L, 60.013);
        assertEquals(20_453_221L / 60.013, tps, 0.0001);
    }

    @Test
    void rejectsAZeroLengthMeasurement() {
        assertThrows(IllegalArgumentException.class, () -> MetricsCalculator.tps(100L, 0.0));
    }

    @Test
    void errorRateIsFailedOverAttempted() {
        assertEquals(2.0, MetricsCalculator.errorRatePercent(2L, 100L), 0.0001);
        assertEquals(0.002, MetricsCalculator.errorRatePercent(2L, 100_000L), 0.000001);
    }

    @Test
    void noAttemptsMeansNoErrors() {
        assertEquals(0.0, MetricsCalculator.errorRatePercent(0L, 0L), 0.0);
    }

    @Test
    void errorRateWithinThresholdIsValid() {
        assertEquals(ResultStatus.VALID, MetricsCalculator.status(0.5, 1.0));
    }

    @Test
    void errorRateExactlyAtThresholdIsStillValid() {
        assertEquals(ResultStatus.VALID, MetricsCalculator.status(1.0, 1.0));
    }

    @Test
    void errorRateAboveThresholdIsInvalid() {
        assertEquals(ResultStatus.INVALID, MetricsCalculator.status(1.01, 1.0));
    }

    @Test
    void convertsNanosecondsToMilliseconds() {
        assertEquals(0.421, MetricsCalculator.nanosToMillis(421_000), 0.0001);
    }
}
