package com.example.cachebenchmark.key;

import java.util.SplittableRandom;

/**
 * Uniform random key index generator, one instance per worker (PRD section 13).
 *
 * <p>Every key in the keyspace has roughly the same chance of being selected.
 * {@link SplittableRandom} is used because it is cheap and needs no
 * synchronisation, which matters inside the measured loop (PRD section 67).
 *
 * <p>Not thread safe on purpose: each worker owns one, seeded deterministically
 * from the global seed so a run can be reproduced.
 */
public final class UniformKeyGenerator {

    private final SplittableRandom random;
    private final int keyCount;

    /**
     * @param globalSeed the seed from configuration
     * @param workerId   0-based worker index; worker N is seeded with globalSeed + N
     * @param keyCount   size of the keyspace, must be positive
     */
    public UniformKeyGenerator(long globalSeed, int workerId, int keyCount) {
        if (keyCount <= 0) {
            throw new IllegalArgumentException("keyCount must be greater than zero, was " + keyCount);
        }
        this.random = new SplittableRandom(globalSeed + workerId);
        this.keyCount = keyCount;
    }

    /** @return an index in [0, keyCount) */
    public int nextIndex() {
        return random.nextInt(keyCount);
    }

    public int keyCount() {
        return keyCount;
    }
}
