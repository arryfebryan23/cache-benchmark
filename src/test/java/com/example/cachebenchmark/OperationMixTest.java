package com.example.cachebenchmark;

import com.example.cachebenchmark.workload.OperationMix;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The GET/SET draw behind a MIXED run. */
class OperationMixTest {

    private static int countSets(OperationMix mix, int draws) {
        int sets = 0;
        for (int i = 0; i < draws; i++) {
            if (mix.nextIsSet()) {
                sets++;
            }
        }
        return sets;
    }

    @Test
    void hitsTheConfiguredRatio() {
        int sets = countSets(new OperationMix(30, 123456L, 0), 1_000_000);
        assertEquals(300_000, sets, 3_000);
    }

    @Test
    void honoursFractionalPercentages() {
        int sets = countSets(new OperationMix(2.5, 123456L, 0), 1_000_000);
        assertEquals(25_000, sets, 1_000);
    }

    @Test
    void zeroAndHundredAreExact() {
        assertEquals(0, countSets(new OperationMix(0, 1L, 0), 100_000));
        assertEquals(100_000, countSets(new OperationMix(100, 1L, 0), 100_000));
    }

    @Test
    void sameSeedAndWorkerGiveTheSameSequence() {
        // Fairness: the Redis run and the Hazelcast run must issue the same
        // GET/SET sequence for the same configuration.
        OperationMix a = new OperationMix(50, 42L, 3);
        OperationMix b = new OperationMix(50, 42L, 3);
        for (int i = 0; i < 10_000; i++) {
            assertEquals(a.nextIsSet(), b.nextIsSet(), "diverged at draw " + i);
        }
    }

    @Test
    void rejectsOutOfRangePercent() {
        assertThrows(IllegalArgumentException.class, () -> new OperationMix(-0.1, 1L, 0));
        assertThrows(IllegalArgumentException.class, () -> new OperationMix(100.1, 1L, 0));
    }
}
