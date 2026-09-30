package com.example.cachebenchmark;

import com.example.cachebenchmark.key.UniformKeyGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PRD section 65: generated indexes must stay inside the keyspace. */
class UniformKeyGeneratorTest {

    @Test
    void neverLeavesTheKeyspace() {
        int keyCount = 1_000;
        UniformKeyGenerator generator = new UniformKeyGenerator(123456L, 0, keyCount);
        for (int i = 0; i < 1_000_000; i++) {
            int index = generator.nextIndex();
            assertTrue(index >= 0 && index < keyCount, "index out of range: " + index);
        }
    }

    @Test
    void handlesAKeyspaceOfOne() {
        UniformKeyGenerator generator = new UniformKeyGenerator(1L, 0, 1);
        for (int i = 0; i < 100; i++) {
            assertEquals(0, generator.nextIndex());
        }
    }

    @Test
    void rejectsAnEmptyKeyspace() {
        assertThrows(IllegalArgumentException.class, () -> new UniformKeyGenerator(1L, 0, 0));
    }

    @Test
    void sameSeedAndWorkerProduceTheSameSequence() {
        UniformKeyGenerator a = new UniformKeyGenerator(42L, 3, 10_000);
        UniformKeyGenerator b = new UniformKeyGenerator(42L, 3, 10_000);
        for (int i = 0; i < 10_000; i++) {
            assertEquals(a.nextIndex(), b.nextIndex(), "sequences diverged at " + i);
        }
    }

    @Test
    void differentWorkersDoNotWalkTheSameSequence() {
        UniformKeyGenerator worker0 = new UniformKeyGenerator(42L, 0, 1_000_000);
        UniformKeyGenerator worker1 = new UniformKeyGenerator(42L, 1, 1_000_000);

        int identical = 0;
        for (int i = 0; i < 1_000; i++) {
            if (worker0.nextIndex() == worker1.nextIndex()) {
                identical++;
            }
        }
        assertNotEquals(1_000, identical, "workers 0 and 1 produced identical sequences");
    }

    @Test
    void coversTheKeyspaceRoughlyEvenly() {
        int keyCount = 100;
        int draws = 1_000_000;
        int[] counts = new int[keyCount];
        UniformKeyGenerator generator = new UniformKeyGenerator(7L, 0, keyCount);
        for (int i = 0; i < draws; i++) {
            counts[generator.nextIndex()]++;
        }

        // Uniform means every bucket should sit near draws/keyCount. A 20%
        // band is loose enough to never flake and tight enough to catch a
        // generator that is not uniform at all.
        int expected = draws / keyCount;
        for (int i = 0; i < keyCount; i++) {
            assertTrue(counts[i] > expected * 0.8 && counts[i] < expected * 1.2,
                    "bucket " + i + " had " + counts[i] + ", expected near " + expected);
        }
    }
}
