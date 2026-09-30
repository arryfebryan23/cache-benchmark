package com.example.cachebenchmark.workload;

import com.example.cachebenchmark.client.CacheClient;
import com.example.cachebenchmark.config.Operation;

/**
 * Read workload (PRD section 16.1).
 *
 * <p>A null reply is a cache miss, not an error. Misses are counted and
 * reported separately (PRD section 28) because a run where everything missed
 * measures nothing useful, even though it produces no exceptions.
 */
public final class GetWorkload implements Workload {

    @Override
    public Operation operation() {
        return Operation.GET;
    }

    @Override
    public boolean execute(CacheClient client, String key, byte[] payload) {
        return client.get(key) != null;
    }
}
