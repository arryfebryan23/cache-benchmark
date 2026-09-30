package com.example.cachebenchmark.client;

import com.example.cachebenchmark.config.BenchmarkConfig;
import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.ConnectionRetryConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

import java.time.Duration;

/**
 * Hazelcast backend built on the official Java client (PRD section 11).
 *
 * <p>Data lives in an {@code IMap<String, byte[]>} so the stored value is the
 * same opaque byte array Redis receives (Fairness Rule 11).
 *
 * <p>No Near Cache is configured. A Near Cache would answer reads inside the
 * benchmark process and Redis has no equivalent enabled here, so results would
 * stop being comparable. The constructor asserts none is present.
 *
 * <p>Smart routing is left at its default, which is the architecture Hazelcast
 * recommends: the client keeps a connection to every member and routes each
 * key to its owner. PRD section 43 allows each client to use its own native
 * connection model.
 */
public final class HazelcastCacheClient implements CacheClient {

    private final HazelcastInstance instance;
    private final IMap<String, byte[]> map;
    private final String description;

    public HazelcastCacheClient(BenchmarkConfig.Hazelcast config, Duration operationTimeout) {
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.setClusterName(config.getClusterName());
        clientConfig.getNetworkConfig().setAddresses(config.getAddresses());

        // The client retries forever by default. PRD section 41 wants an
        // unreachable backend to exit non-zero rather than hang, so give up
        // after the same window Redis gets.
        ConnectionRetryConfig retry = clientConfig.getConnectionStrategyConfig().getConnectionRetryConfig();
        retry.setClusterConnectTimeoutMillis(operationTimeout.toMillis());

        // Matches the Redis command timeout so a stalled request is counted as
        // a failure at the same moment on both backends.
        clientConfig.setProperty("hazelcast.client.invocation.timeout.seconds",
                String.valueOf(operationTimeout.toSeconds()));
        clientConfig.setProperty("hazelcast.logging.type", "slf4j");

        if (!clientConfig.getNearCacheConfigMap().isEmpty()) {
            throw new IllegalStateException(
                    "A Near Cache is configured. That would serve reads inside the benchmark "
                            + "process and make the comparison with Redis invalid.");
        }

        this.instance = HazelcastClient.newHazelcastClient(clientConfig);
        this.map = instance.getMap(config.getMapName());
        this.description = "hazelcast java client, smart routing, cluster="
                + config.getClusterName()
                + ", members=" + String.join(",", config.getAddresses())
                + ", map=" + config.getMapName()
                + ", nearCache=disabled"
                + ", invocationTimeout=" + operationTimeout.toSeconds() + "s";
    }

    @Override
    public String backendName() {
        return "hazelcast";
    }

    @Override
    public byte[] get(String key) {
        return map.get(key);
    }

    @Override
    public void set(String key, byte[] value) {
        // set() rather than put(): put() would ship the previous value back to
        // the client, which Redis SET does not do.
        map.set(key, value);
    }

    @Override
    public void validateConnection() {
        if (!instance.getLifecycleService().isRunning()) {
            throw new IllegalStateException("Hazelcast client is not running.");
        }
        if (instance.getCluster().getMembers().isEmpty()) {
            throw new IllegalStateException("Hazelcast client is connected to a cluster with no members.");
        }
        // A real round trip to the cluster, so a half-open connection fails here
        // rather than during measurement.
        map.size();
    }

    @Override
    public String connectionDescription() {
        return description;
    }

    @Override
    public void close() {
        instance.shutdown();
    }
}
