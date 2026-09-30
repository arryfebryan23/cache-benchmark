package com.example.cachebenchmark.benchmark;

/** The phases one execution moves through (PRD section 20). */
public enum BenchmarkPhase {

    INITIALIZATION,
    PRELOAD,
    WARMUP,
    MEASUREMENT,
    RESULT,
    SHUTDOWN
}
