package com.example.cachebenchmark.key;

/**
 * The benchmark keyspace: benchmark:0 .. benchmark:&lt;keyCount-1&gt;
 * (PRD section 12, Fairness Rule 4).
 *
 * <p>When precomputation is on, every key string is built once up front so the
 * measured loop only does an array read. Building "benchmark:" + n inside the
 * loop would put String concatenation and the resulting garbage inside the
 * latency measurement (PRD sections 14, 44 and 67).
 */
public final class KeySpace {

    public static final String PREFIX = "benchmark:";

    private final int keyCount;
    private final String[] keys;

    private KeySpace(int keyCount, String[] keys) {
        this.keyCount = keyCount;
        this.keys = keys;
    }

    public static KeySpace create(int keyCount, boolean precompute) {
        if (keyCount <= 0) {
            throw new IllegalArgumentException("keyCount must be greater than zero, was " + keyCount);
        }
        if (!precompute) {
            return new KeySpace(keyCount, null);
        }
        String[] keys = new String[keyCount];
        for (int i = 0; i < keyCount; i++) {
            keys[i] = PREFIX + i;
        }
        return new KeySpace(keyCount, keys);
    }

    /** Format a key without consulting the cache. Used by tests and preload. */
    public static String keyFor(int index) {
        return PREFIX + index;
    }

    public String key(int index) {
        return keys != null ? keys[index] : PREFIX + index;
    }

    /** Null when precomputation is disabled. Workers read it directly in the hot loop. */
    public String[] precomputed() {
        return keys;
    }

    public boolean isPrecomputed() {
        return keys != null;
    }

    public int keyCount() {
        return keyCount;
    }
}
