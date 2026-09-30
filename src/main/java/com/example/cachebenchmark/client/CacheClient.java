package com.example.cachebenchmark.client;

/**
 * The only door between the benchmark engine and a backend (PRD section 9).
 *
 * <p>The engine must never know which implementation it is holding. Every
 * backend-specific decision lives behind this interface, which is what makes
 * the Redis and Hazelcast runs comparable (PRD section 4.1).
 *
 * <p>Implementations must open their connection once, at construction time,
 * and keep it for the whole run. Connecting per operation is forbidden
 * (Fairness Rule 12, PRD sections 10 and 11).
 *
 * <p>{@link #get} and {@link #set} are called from many worker threads at
 * once, so implementations must be thread safe.
 */
public interface CacheClient extends AutoCloseable {

    /** Backend name as it appears in reports: redis or hazelcast. */
    String backendName();

    /**
     * Reads one value.
     *
     * @return the stored value, or null when the key is absent (a cache miss)
     */
    byte[] get(String key);

    /** Writes one value. Completes when the synchronous client call returns. */
    void set(String key, byte[] value);

    /**
     * Proves the backend is reachable before preload and warmup begin
     * (PRD section 41). Throws when it is not.
     */
    void validateConnection();

    /** Human readable connection summary, recorded in the result metadata. */
    String connectionDescription();

    @Override
    void close();
}
