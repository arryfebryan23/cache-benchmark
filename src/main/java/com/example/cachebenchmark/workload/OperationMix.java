package com.example.cachebenchmark.workload;

import java.util.SplittableRandom;

/**
 * Chooses GET or SET for each iteration of a MIXED run, one instance per
 * worker.
 *
 * <p>The choice is a seeded draw rather than a fixed pattern such as "every
 * fifth operation is a SET", so writes are not phase-locked to the key
 * sequence. The seed is derived from the global seed and the worker id, so a
 * Redis run and a Hazelcast run with the same configuration issue the same
 * sequence of operations against the same keys.
 *
 * <p>Resolution is 0.01 %: the percentage is converted to basis points once,
 * and each draw is a single {@code nextInt}, which allocates nothing.
 */
public final class OperationMix {

    private static final int SCALE = 10_000;

    /**
     * Keeps the operation stream independent of the key stream. Both are
     * seeded from the same global seed and worker id; without the offset they
     * would be the same generator and the GET/SET choice would correlate with
     * the key index.
     */
    private static final long SEED_OFFSET = 0x5DEECE66DL;

    private final SplittableRandom random;
    private final int setThreshold;

    /**
     * @param setPercent share of operations that are SET, 0 to 100
     * @param globalSeed the seed from configuration
     * @param workerId   0-based worker index
     */
    public OperationMix(double setPercent, long globalSeed, int workerId) {
        if (Double.isNaN(setPercent) || setPercent < 0 || setPercent > 100) {
            throw new IllegalArgumentException("setPercent must be between 0 and 100, was " + setPercent);
        }
        this.setThreshold = (int) Math.round(setPercent * (SCALE / 100));
        this.random = new SplittableRandom(globalSeed + workerId + SEED_OFFSET);
    }

    /** @return true when the next operation should be a SET */
    public boolean nextIsSet() {
        return random.nextInt(SCALE) < setThreshold;
    }
}
