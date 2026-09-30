package com.example.cachebenchmark.workload;

import com.example.cachebenchmark.client.CacheClient;
import com.example.cachebenchmark.config.Operation;

/**
 * One measured unit of work. Implementations must do exactly one backend call
 * and allocate nothing (PRD section 67).
 */
public interface Workload {

    Operation operation();

    /**
     * Runs a single operation against the backend.
     *
     * @param payload the shared reusable payload; never copied or rebuilt
     * @return true when the backend returned a value. GET reports a cache hit
     *         this way; SET always returns true.
     * @throws Exception any backend failure, counted by the worker as a failed operation
     */
    boolean execute(CacheClient client, String key, byte[] payload) throws Exception;
}
