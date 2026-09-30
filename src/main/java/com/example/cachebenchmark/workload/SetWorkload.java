package com.example.cachebenchmark.workload;

import com.example.cachebenchmark.client.CacheClient;
import com.example.cachebenchmark.config.Operation;

/**
 * Write workload (PRD section 17).
 *
 * <p>The operation counts as complete when the synchronous client call returns
 * to the caller. The same reusable payload instance is written every time; a
 * fresh byte[] per request would measure allocation and GC, not the backend
 * (PRD sections 7, 44 and 67).
 */
public final class SetWorkload implements Workload {

    @Override
    public Operation operation() {
        return Operation.SET;
    }

    @Override
    public boolean execute(CacheClient client, String key, byte[] payload) {
        client.set(key, payload);
        return true;
    }
}
