package com.example.cachebenchmark.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Full benchmark configuration, mapped 1:1 onto config/benchmark.yaml
 * (PRD section 35).
 *
 * <p>Values here are the application defaults. The YAML file overrides these,
 * and CLI arguments override the YAML (PRD section 36).
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public class BenchmarkConfig {

    private Benchmark benchmark = new Benchmark();
    private Redis redis = new Redis();
    private Hazelcast hazelcast = new Hazelcast();

    public Benchmark getBenchmark() {
        return benchmark;
    }

    public void setBenchmark(Benchmark benchmark) {
        this.benchmark = benchmark;
    }

    public Redis getRedis() {
        return redis;
    }

    public void setRedis(Redis redis) {
        this.redis = redis;
    }

    public Hazelcast getHazelcast() {
        return hazelcast;
    }

    public void setHazelcast(Hazelcast hazelcast) {
        this.hazelcast = hazelcast;
    }

    // -----------------------------------------------------------------------
    // Validation (PRD section 40)
    // -----------------------------------------------------------------------

    /**
     * Validates everything that can be checked without touching the network.
     * Only the section relevant to the selected target is validated, so a
     * Redis run does not fail because the Hazelcast address list is empty.
     *
     * @throws ConfigurationException listing every problem found, one per line
     */
    public void validate() {
        List<String> errors = new ArrayList<>();

        Target target = null;
        try {
            target = Target.parse(benchmark.getTarget());
        } catch (ConfigurationException e) {
            errors.add(e.getMessage());
        }

        try {
            Operation.parse(benchmark.getOperation());
        } catch (ConfigurationException e) {
            errors.add(e.getMessage());
        }
        if (Double.isNaN(benchmark.getSetPercent())
                || benchmark.getSetPercent() < 0 || benchmark.getSetPercent() > 100) {
            errors.add("setPercent must be between 0 and 100.");
        }

        if (benchmark.getThreads() <= 0) {
            errors.add("threads must be greater than zero.");
        }
        if (benchmark.getDurationSeconds() <= 0) {
            errors.add("durationSeconds must be greater than zero.");
        }
        if (benchmark.getWarmupSeconds() < 0) {
            errors.add("warmupSeconds must not be negative.");
        }
        if (benchmark.getKeyCount() <= 0) {
            errors.add("keyCount must be greater than zero.");
        }
        if (benchmark.getPayloadBytes() <= 0) {
            errors.add("payloadBytes must be greater than zero.");
        }
        if (benchmark.getMaxErrorRatePercent() < 0 || benchmark.getMaxErrorRatePercent() > 100) {
            errors.add("maxErrorRatePercent must be between 0 and 100.");
        }
        if (benchmark.getOperationTimeoutSeconds() <= 0) {
            errors.add("operationTimeoutSeconds must be greater than zero.");
        }
        if (benchmark.getOutputDirectory() == null || benchmark.getOutputDirectory().isBlank()) {
            errors.add("outputDirectory must not be empty.");
        }

        if (target == Target.REDIS) {
            validateRedis(errors);
        }
        if (target == Target.HAZELCAST) {
            validateHazelcast(errors);
        }

        if (!errors.isEmpty()) {
            throw new ConfigurationException("Configuration error:" + System.lineSeparator()
                    + String.join(System.lineSeparator(), errors));
        }
    }

    private void validateRedis(List<String> errors) {
        if (redis.getHost() == null || redis.getHost().isBlank()) {
            errors.add("redis.host must not be empty.");
        }
        if (redis.getPort() < 1 || redis.getPort() > 65535) {
            errors.add("redis.port must be between 1 and 65535, was " + redis.getPort() + ".");
        }
        if (redis.getDatabase() < 0) {
            errors.add("redis.database must not be negative.");
        }
        boolean hasUser = redis.getUsername() != null && !redis.getUsername().isBlank();
        boolean hasPassword = redis.getPassword() != null && !redis.getPassword().isEmpty();
        if (hasUser && !hasPassword) {
            errors.add("redis.username is set but redis.password is empty. "
                    + "ACL authentication needs both.");
        }
    }

    private void validateHazelcast(List<String> errors) {
        if (hazelcast.getClusterName() == null || hazelcast.getClusterName().isBlank()) {
            errors.add("hazelcast.clusterName must not be empty.");
        }
        if (hazelcast.getMapName() == null || hazelcast.getMapName().isBlank()) {
            errors.add("hazelcast.mapName must not be empty.");
        }
        if (hazelcast.getAddresses() == null || hazelcast.getAddresses().isEmpty()) {
            errors.add("hazelcast.addresses must contain at least one host:port entry.");
            return;
        }
        for (String address : hazelcast.getAddresses()) {
            if (address == null || address.isBlank()) {
                errors.add("hazelcast.addresses contains an empty entry.");
                continue;
            }
            int colon = address.lastIndexOf(58);
            if (colon < 0) {
                // A bare host is legal; Hazelcast falls back to the default port range.
                continue;
            }
            String portPart = address.substring(colon + 1);
            int port;
            try {
                port = Integer.parseInt(portPart);
            } catch (NumberFormatException e) {
                errors.add("hazelcast.addresses entry " + address + " has a non-numeric port.");
                continue;
            }
            if (port < 1 || port > 65535) {
                errors.add("hazelcast.addresses entry " + address + " has a port outside 1-65535.");
            }
        }
    }

    @JsonIgnore
    public Target target() {
        return Target.parse(benchmark.getTarget());
    }

    @JsonIgnore
    public Operation operation() {
        return Operation.parse(benchmark.getOperation());
    }

    // -----------------------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = false)
    public static class Benchmark {

        private String target = "redis";
        private String operation = "GET";
        private double setPercent = 50.0;
        private int threads = 16;
        private int keyCount = 1_000_000;
        private int payloadBytes = 1024;
        private long randomSeed = 123456L;
        private int warmupSeconds = 30;
        private int durationSeconds = 60;
        private boolean preload = true;
        private boolean precomputeKeys = true;
        private double maxErrorRatePercent = 1.0;
        private int operationTimeoutSeconds = 10;
        private String outputDirectory = "./results";

        public String getTarget() { return target; }
        public void setTarget(String target) { this.target = target; }

        public String getOperation() { return operation; }
        public void setOperation(String operation) { this.operation = operation; }

        /** Share of MIXED operations that are SET; the rest are GET. Ignored otherwise. */
        public double getSetPercent() { return setPercent; }
        public void setSetPercent(double setPercent) { this.setPercent = setPercent; }

        public int getThreads() { return threads; }
        public void setThreads(int threads) { this.threads = threads; }

        public int getKeyCount() { return keyCount; }
        public void setKeyCount(int keyCount) { this.keyCount = keyCount; }

        public int getPayloadBytes() { return payloadBytes; }
        public void setPayloadBytes(int payloadBytes) { this.payloadBytes = payloadBytes; }

        public long getRandomSeed() { return randomSeed; }
        public void setRandomSeed(long randomSeed) { this.randomSeed = randomSeed; }

        public int getWarmupSeconds() { return warmupSeconds; }
        public void setWarmupSeconds(int warmupSeconds) { this.warmupSeconds = warmupSeconds; }

        public int getDurationSeconds() { return durationSeconds; }
        public void setDurationSeconds(int durationSeconds) { this.durationSeconds = durationSeconds; }

        public boolean isPreload() { return preload; }
        public void setPreload(boolean preload) { this.preload = preload; }

        public boolean isPrecomputeKeys() { return precomputeKeys; }
        public void setPrecomputeKeys(boolean precomputeKeys) { this.precomputeKeys = precomputeKeys; }

        public double getMaxErrorRatePercent() { return maxErrorRatePercent; }
        public void setMaxErrorRatePercent(double v) { this.maxErrorRatePercent = v; }

        public int getOperationTimeoutSeconds() { return operationTimeoutSeconds; }
        public void setOperationTimeoutSeconds(int v) { this.operationTimeoutSeconds = v; }

        public String getOutputDirectory() { return outputDirectory; }
        public void setOutputDirectory(String outputDirectory) { this.outputDirectory = outputDirectory; }
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public static class Redis {

        private String host = "127.0.0.1";
        private int port = 6379;
        private String username;
        private String password;
        private int database = 0;

        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }

        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }

        public int getDatabase() { return database; }
        public void setDatabase(int database) { this.database = database; }
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public static class Hazelcast {

        private String clusterName = "dev";
        private List<String> addresses = new ArrayList<>(List.of("127.0.0.1:5701"));
        private String mapName = "benchmark-map";

        public String getClusterName() { return clusterName; }
        public void setClusterName(String clusterName) { this.clusterName = clusterName; }

        public List<String> getAddresses() { return addresses; }
        public void setAddresses(List<String> addresses) { this.addresses = addresses; }

        public String getMapName() { return mapName; }
        public void setMapName(String mapName) { this.mapName = mapName; }
    }
}
