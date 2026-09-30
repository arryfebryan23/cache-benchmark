package com.example.cachebenchmark.metrics;

/** Verdict on whether a run may be used for comparison (PRD section 29). */
public enum ResultStatus {

    /** Error rate stayed within the configured threshold. */
    VALID,

    /** Error rate exceeded the threshold. Raw metrics are still written to disk. */
    INVALID
}
