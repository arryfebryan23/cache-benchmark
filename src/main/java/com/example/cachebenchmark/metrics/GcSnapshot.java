package com.example.cachebenchmark.metrics;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;

/**
 * Garbage collection counters around the measurement window (PRD section 44).
 *
 * <p>These do not feed into TPS. They exist so an unexpectedly low result can
 * be traced back to the benchmark client stalling in GC rather than to the
 * backend being slow.
 */
public final class GcSnapshot {

    private final long collections;
    private final long timeMillis;

    private GcSnapshot(long collections, long timeMillis) {
        this.collections = collections;
        this.timeMillis = timeMillis;
    }

    public static GcSnapshot capture() {
        long collections = 0;
        long time = 0;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            long count = bean.getCollectionCount();
            long millis = bean.getCollectionTime();
            if (count > 0) {
                collections += count;
            }
            if (millis > 0) {
                time += millis;
            }
        }
        return new GcSnapshot(collections, time);
    }

    /** @return what happened between this snapshot and a later one */
    public GcSnapshot since(GcSnapshot earlier) {
        return new GcSnapshot(collections - earlier.collections, timeMillis - earlier.timeMillis);
    }

    public long getCollections() {
        return collections;
    }

    public long getTimeMillis() {
        return timeMillis;
    }
}
