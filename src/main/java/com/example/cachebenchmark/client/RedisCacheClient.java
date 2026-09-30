package com.example.cachebenchmark.client;

import com.example.cachebenchmark.config.BenchmarkConfig;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

import java.time.Duration;

/**
 * Redis backend built on Lettuce, using the synchronous API (PRD section 10).
 *
 * <p>One long lived connection is shared by every worker thread. That is the
 * architecture Lettuce documents for thread safe synchronous use: the
 * connection multiplexes concurrent commands over a single socket internally.
 * PRD section 5 allows that, because it is the client library doing it, not
 * the benchmark explicitly batching or pipelining. PRD section 43 is explicit
 * that the physical TCP connection count does not have to match Hazelcast.
 */
public final class RedisCacheClient implements CacheClient {

    /** Read during connection validation. Never written, so a miss is expected. */
    private static final String CONNECTIVITY_PROBE_KEY = "cache-benchmark:connectivity-probe";

    private final RedisClient client;
    private final StatefulRedisConnection<String, byte[]> connection;
    private final RedisCommands<String, byte[]> commands;
    private final String description;

    public RedisCacheClient(BenchmarkConfig.Redis config, Duration operationTimeout) {
        RedisURI.Builder uri = RedisURI.Builder
                .redis(config.getHost(), config.getPort())
                .withDatabase(config.getDatabase())
                .withTimeout(operationTimeout);

        String username = config.getUsername();
        String password = config.getPassword();
        boolean hasUsername = username != null && !username.isBlank();
        if (password != null && !password.isEmpty()) {
            if (hasUsername) {
                // Redis 6 ACL user. AUTH with only a password authenticates as
                // the default user, which a server with named users rejects.
                uri.withAuthentication(username, password.toCharArray());
            } else {
                uri.withPassword(password.toCharArray());
            }
        }

        this.client = RedisClient.create(uri.build());
        this.client.setDefaultTimeout(operationTimeout);
        // Same bound as Hazelcast gets, so an unreachable backend fails at the
        // same point on both sides instead of hanging (PRD section 41).
        this.client.setOptions(ClientOptions.builder()
                .socketOptions(SocketOptions.builder().connectTimeout(operationTimeout).build())
                .build());
        this.connection = client.connect(new StringByteArrayCodec());
        this.commands = connection.sync();
        this.description = "lettuce sync, single shared multiplexed connection to "
                + config.getHost() + ":" + config.getPort()
                + ", db=" + config.getDatabase()
                + ", auth=" + (password == null || password.isEmpty()
                        ? "none" : (hasUsername ? "acl user " + username : "password"))
                + ", commandTimeout=" + operationTimeout.toSeconds() + "s";
    }

    @Override
    public String backendName() {
        return "redis";
    }

    @Override
    public byte[] get(String key) {
        return commands.get(key);
    }

    @Override
    public void set(String key, byte[] value) {
        commands.set(key, value);
    }

    @Override
    public void validateConnection() {
        String reply = commands.ping();
        if (!"PONG".equalsIgnoreCase(reply)) {
            throw new IllegalStateException("Redis PING returned an unexpected reply: " + reply);
        }
        // PING alone is not enough. Some servers answer it on an unauthenticated
        // connection, so bad credentials would survive validation and only
        // surface part-way through preload. A real read exercises the same
        // command path the benchmark uses, including ACL permissions.
        commands.get(CONNECTIVITY_PROBE_KEY);
    }

    @Override
    public String connectionDescription() {
        return description;
    }

    @Override
    public void close() {
        try {
            connection.close();
        } finally {
            client.shutdown();
        }
    }
}
