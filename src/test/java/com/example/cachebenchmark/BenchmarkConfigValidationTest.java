package com.example.cachebenchmark;

import com.example.cachebenchmark.config.BenchmarkConfig;
import com.example.cachebenchmark.config.ConfigurationException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PRD section 65: configuration validation. */
class BenchmarkConfigValidationTest {

    private static BenchmarkConfig valid() {
        BenchmarkConfig config = new BenchmarkConfig();
        config.getBenchmark().setTarget("redis");
        config.getBenchmark().setOperation("GET");
        config.getBenchmark().setThreads(16);
        config.getBenchmark().setKeyCount(1000);
        config.getBenchmark().setPayloadBytes(1024);
        config.getBenchmark().setWarmupSeconds(30);
        config.getBenchmark().setDurationSeconds(60);
        return config;
    }

    @Test
    void acceptsAValidConfiguration() {
        assertDoesNotThrow(() -> valid().validate());
    }

    @Test
    void rejectsNonPositiveThreads() {
        BenchmarkConfig config = valid();
        config.getBenchmark().setThreads(0);
        assertMessageContains(config, "threads must be greater than zero");
    }

    @Test
    void rejectsNonPositiveDuration() {
        BenchmarkConfig config = valid();
        config.getBenchmark().setDurationSeconds(0);
        assertMessageContains(config, "durationSeconds must be greater than zero");
    }

    @Test
    void rejectsNegativeWarmup() {
        BenchmarkConfig config = valid();
        config.getBenchmark().setWarmupSeconds(-1);
        assertMessageContains(config, "warmupSeconds must not be negative");
    }

    @Test
    void acceptsZeroWarmup() {
        BenchmarkConfig config = valid();
        config.getBenchmark().setWarmupSeconds(0);
        assertDoesNotThrow(config::validate);
    }

    @Test
    void rejectsNonPositiveKeyCount() {
        BenchmarkConfig config = valid();
        config.getBenchmark().setKeyCount(0);
        assertMessageContains(config, "keyCount must be greater than zero");
    }

    @Test
    void rejectsNonPositivePayload() {
        BenchmarkConfig config = valid();
        config.getBenchmark().setPayloadBytes(0);
        assertMessageContains(config, "payloadBytes must be greater than zero");
    }

    @Test
    void rejectsUnsupportedTarget() {
        BenchmarkConfig config = valid();
        config.getBenchmark().setTarget("memcached");
        assertMessageContains(config, "unsupported target");
    }

    @Test
    void rejectsUnsupportedOperation() {
        BenchmarkConfig config = valid();
        config.getBenchmark().setOperation("DELETE");
        assertMessageContains(config, "unsupported operation");
    }

    @Test
    void rejectsInvalidRedisPort() {
        BenchmarkConfig config = valid();
        config.getRedis().setPort(70000);
        assertMessageContains(config, "redis.port must be between 1 and 65535");
    }

    @Test
    void rejectsAUsernameWithoutAPassword() {
        // AUTH with a username needs both halves. Letting this through would
        // produce a WRONGPASS failure only after the client is already up.
        BenchmarkConfig config = valid();
        config.getRedis().setUsername("superadmin");
        config.getRedis().setPassword(null);
        assertMessageContains(config, "redis.username is set but redis.password is empty");
    }

    @Test
    void acceptsAUsernameWithAPassword() {
        BenchmarkConfig config = valid();
        config.getRedis().setUsername("superadmin");
        config.getRedis().setPassword("secret");
        assertDoesNotThrow(config::validate);
    }

    @Test
    void acceptsAPasswordWithoutAUsername() {
        BenchmarkConfig config = valid();
        config.getRedis().setPassword("secret");
        assertDoesNotThrow(config::validate);
    }

    @Test
    void rejectsEmptyHazelcastAddresses() {
        BenchmarkConfig config = valid();
        config.getBenchmark().setTarget("hazelcast");
        config.getHazelcast().setAddresses(List.of());
        assertMessageContains(config, "hazelcast.addresses must contain at least one");
    }

    @Test
    void doesNotValidateHazelcastWhenTargetIsRedis() {
        BenchmarkConfig config = valid();
        config.getHazelcast().setAddresses(List.of());
        assertDoesNotThrow(config::validate);
    }

    @Test
    void reportsEveryProblemAtOnce() {
        BenchmarkConfig config = valid();
        config.getBenchmark().setThreads(-1);
        config.getBenchmark().setDurationSeconds(-1);
        ConfigurationException e = assertThrows(ConfigurationException.class, config::validate);
        assertTrue(e.getMessage().contains("threads"), e.getMessage());
        assertTrue(e.getMessage().contains("durationSeconds"), e.getMessage());
    }

    private static void assertMessageContains(BenchmarkConfig config, String fragment) {
        ConfigurationException e = assertThrows(ConfigurationException.class, config::validate);
        assertTrue(e.getMessage().contains(fragment),
                "expected message to contain: " + fragment + System.lineSeparator() + e.getMessage());
    }
}
