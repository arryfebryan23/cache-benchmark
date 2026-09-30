package com.example.cachebenchmark.workload;

/**
 * Builds the single reusable payload for a run (PRD section 15).
 *
 * <p>Content is a deterministic repeating byte pattern, so Redis and Hazelcast
 * receive byte-identical values for the same configuration (Fairness Rule 2).
 * A constant pattern also keeps the value incompressible in the same way on
 * both sides, since neither backend compresses it here.
 */
public final class PayloadFactory {

    private PayloadFactory() {
    }

    public static byte[] create(int payloadBytes) {
        if (payloadBytes <= 0) {
            throw new IllegalArgumentException("payloadBytes must be greater than zero, was " + payloadBytes);
        }
        byte[] payload = new byte[payloadBytes];
        for (int i = 0; i < payloadBytes; i++) {
            payload[i] = (byte) ((i % 251) + 1);
        }
        return payload;
    }

    public static Workload workloadFor(com.example.cachebenchmark.config.Operation operation) {
        switch (operation) {
            case GET:
                return new GetWorkload();
            case SET:
                return new SetWorkload();
            default:
                throw new IllegalStateException("Unhandled operation: " + operation);
        }
    }
}
