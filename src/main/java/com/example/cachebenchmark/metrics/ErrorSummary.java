package com.example.cachebenchmark.metrics;

/**
 * One error type with its count and a single example message (PRD section 61).
 *
 * <p>100,000 timeouts must not produce 100,000 stack traces, so only the first
 * message of each type is kept.
 */
public final class ErrorSummary {

    private final String type;
    private long count;
    private final String sampleMessage;

    public ErrorSummary(String type, String sampleMessage) {
        this.type = type;
        this.sampleMessage = sampleMessage;
        this.count = 0;
    }

    public void increment() {
        count++;
    }

    public void add(long delta) {
        count += delta;
    }

    public String getType() {
        return type;
    }

    public long getCount() {
        return count;
    }

    public String getSampleMessage() {
        return sampleMessage;
    }
}
