package com.example.cachebenchmark.client;

import com.example.cachebenchmark.config.BenchmarkConfig;
import com.example.cachebenchmark.config.Target;

import java.time.Duration;

/** Builds the one client the run needs, keeping the choice out of the engine. */
public final class CacheClientFactory {

    private CacheClientFactory() {
    }

    public static CacheClient create(BenchmarkConfig config) {
        Duration timeout = Duration.ofSeconds(config.getBenchmark().getOperationTimeoutSeconds());
        Target target = config.target();
        switch (target) {
            case REDIS:
                return new RedisCacheClient(config.getRedis(), timeout);
            case HAZELCAST:
                return new HazelcastCacheClient(config.getHazelcast(), timeout);
            default:
                throw new IllegalStateException("Unhandled target: " + target);
        }
    }
}
