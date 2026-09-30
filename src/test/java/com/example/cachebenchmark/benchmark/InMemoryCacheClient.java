package com.example.cachebenchmark.benchmark;

import com.example.cachebenchmark.client.CacheClient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A backend-free {@link CacheClient} used to exercise the benchmark engine in
 * unit tests. Not a benchmark target: it measures nothing useful, it only lets
 * the engine run without Redis or Hazelcast being up.
 */
final class InMemoryCacheClient implements CacheClient {

    private final Map<String, byte[]> store = new ConcurrentHashMap<>();
    private final AtomicLong getCalls = new AtomicLong();
    private final AtomicLong setCalls = new AtomicLong();

    private volatile boolean failEveryCall;

    @Override
    public String backendName() {
        return "in-memory";
    }

    @Override
    public byte[] get(String key) {
        getCalls.incrementAndGet();
        if (failEveryCall) {
            throw new IllegalStateException("injected failure");
        }
        return store.get(key);
    }

    @Override
    public void set(String key, byte[] value) {
        setCalls.incrementAndGet();
        if (failEveryCall) {
            throw new IllegalStateException("injected failure");
        }
        store.put(key, value);
    }

    @Override
    public void validateConnection() {
        // Always reachable.
    }

    @Override
    public String connectionDescription() {
        return "in-memory test double";
    }

    @Override
    public void close() {
        // Nothing to release.
    }

    void failEveryCall(boolean fail) {
        this.failEveryCall = fail;
    }

    long totalCalls() {
        return getCalls.get() + setCalls.get();
    }

    int size() {
        return store.size();
    }
}
