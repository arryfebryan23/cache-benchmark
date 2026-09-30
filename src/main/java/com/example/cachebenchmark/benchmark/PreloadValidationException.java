package com.example.cachebenchmark.benchmark;

/**
 * Thrown when the sanity check after preload cannot find the data it just
 * wrote (PRD section 19).
 *
 * <p>This matters more than it looks. A GET benchmark against an empty
 * keyspace does not fail, it just returns null very quickly, and the run looks
 * like an excellent result. Stopping here is the only thing preventing that
 * number from reaching a comparison table.
 */
public class PreloadValidationException extends RuntimeException {

    public static final String CODE = "PRELOAD_VALIDATION_FAILED";

    public PreloadValidationException(String message) {
        super(message);
    }
}
