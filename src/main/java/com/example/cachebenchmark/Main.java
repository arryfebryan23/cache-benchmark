package com.example.cachebenchmark;

import com.example.cachebenchmark.benchmark.BenchmarkResult;
import com.example.cachebenchmark.benchmark.BenchmarkRunner;
import com.example.cachebenchmark.benchmark.PreloadValidationException;
import com.example.cachebenchmark.config.BenchmarkConfig;
import com.example.cachebenchmark.config.ConfigLoader;
import com.example.cachebenchmark.config.ConfigurationException;
import com.example.cachebenchmark.metrics.ResultStatus;
import com.example.cachebenchmark.output.ConsoleReporter;
import com.example.cachebenchmark.output.CsvReporter;
import com.example.cachebenchmark.output.JsonReporter;
import com.example.cachebenchmark.output.ResultPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * CLI entry point (PRD sections 36, 37 and 62).
 *
 * <p>Every option here overrides the YAML file. Options left unset fall
 * through to the YAML, and then to the application defaults.
 *
 * <p>Exit codes: 0 benchmark completed, 1 configuration or runtime failure,
 * 2 benchmark completed but the result is INVALID.
 */
@Command(
        name = "cache-benchmark",
        mixinStandardHelpOptions = true,
        versionProvider = Main.VersionProvider.class,
        sortOptions = false,
        usageHelpWidth = 100,
        description = "Compare Redis and Hazelcast GET/SET throughput using one shared benchmark engine.")
public final class Main implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    static final int EXIT_OK = 0;
    static final int EXIT_FAILURE = 1;
    static final int EXIT_INVALID_RESULT = 2;

    @Option(names = {"-c", "--config"},
            paramLabel = "PATH",
            description = "YAML configuration file. Defaults to config/benchmark.yaml when present.")
    String configPath;

    @Option(names = "--target", paramLabel = "NAME",
            description = "Backend to benchmark: redis or hazelcast.")
    String target;

    @Option(names = "--operation", paramLabel = "OP",
            description = "Operation to benchmark: GET or SET (case insensitive).")
    String operation;

    @Option(names = "--threads", paramLabel = "N",
            description = "Number of benchmark worker threads.")
    Integer threads;

    @Option(names = "--keys", paramLabel = "N",
            description = "Size of the keyspace.")
    Integer keyCount;

    @Option(names = "--payload", paramLabel = "BYTES",
            description = "Value size in bytes.")
    Integer payloadBytes;

    @Option(names = "--warmup", paramLabel = "SECONDS",
            description = "Warmup duration. Excluded from the result.")
    Integer warmupSeconds;

    @Option(names = "--duration", paramLabel = "SECONDS",
            description = "Measurement duration.")
    Integer durationSeconds;

    @Option(names = "--seed", paramLabel = "N",
            description = "Global random seed. Worker N is seeded with seed + N.")
    Long randomSeed;

    @Option(names = "--preload", paramLabel = "BOOL", arity = "1",
            description = "Populate the keyspace before a GET benchmark.")
    Boolean preload;

    @Option(names = "--precompute-keys", paramLabel = "BOOL", arity = "1",
            description = "Build key strings up front so they are not inside the measured latency.")
    Boolean precomputeKeys;

    @Option(names = "--max-error-rate", paramLabel = "PERCENT",
            description = "Error rate above which the result is reported as INVALID.")
    Double maxErrorRatePercent;

    @Option(names = "--timeout", paramLabel = "SECONDS",
            description = "Client operation timeout, applied identically to both backends.")
    Integer operationTimeoutSeconds;

    @Option(names = {"-o", "--output"}, paramLabel = "DIR",
            description = "Directory for JSON and CSV results.")
    String outputDirectory;

    @Option(names = "--redis-host", paramLabel = "HOST", description = "Redis host.")
    String redisHost;

    @Option(names = "--redis-port", paramLabel = "PORT", description = "Redis port.")
    Integer redisPort;

    @Option(names = "--redis-password", paramLabel = "SECRET", description = "Redis password.")
    String redisPassword;

    @Option(names = "--redis-database", paramLabel = "N", description = "Redis database index.")
    Integer redisDatabase;

    @Option(names = "--hz-addresses", paramLabel = "HOST:PORT", split = ",",
            description = "Hazelcast member addresses, comma separated.")
    List<String> hazelcastAddresses;

    @Option(names = "--hz-cluster", paramLabel = "NAME", description = "Hazelcast cluster name.")
    String hazelcastClusterName;

    @Option(names = "--hz-map", paramLabel = "NAME", description = "Hazelcast IMap name.")
    String hazelcastMapName;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        BenchmarkConfig config;
        try {
            config = ConfigLoader.load(configPath);
            applyOverrides(config);
            config.validate();
        } catch (ConfigurationException e) {
            System.err.println(e.getMessage());
            return EXIT_FAILURE;
        }

        BenchmarkResult result;
        try {
            result = new BenchmarkRunner(config).run();
        } catch (PreloadValidationException e) {
            System.err.println(e.getMessage());
            return EXIT_FAILURE;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Benchmark interrupted.");
            return EXIT_FAILURE;
        } catch (RuntimeException e) {
            // Connection failures land here (PRD section 41): do not run the
            // benchmark, exit non-zero.
            System.err.println("Benchmark failed: " + e);
            log.debug("Benchmark failure detail", e);
            return EXIT_FAILURE;
        }

        try {
            writeReports(config, result);
        } catch (Exception e) {
            System.err.println("Benchmark completed but writing the result files failed: " + e);
            return EXIT_FAILURE;
        }

        new ConsoleReporter(System.out).report(result);

        return result.status == ResultStatus.INVALID ? EXIT_INVALID_RESULT : EXIT_OK;
    }

    private void writeReports(BenchmarkConfig config, BenchmarkResult result) throws Exception {
        String outputDir = config.getBenchmark().getOutputDirectory();
        ResultPaths paths = ResultPaths.create(outputDir, result);

        Path json = new JsonReporter().write(result, paths.json());
        CsvReporter csv = new CsvReporter();
        Path csvFile = csv.write(result, paths.csv());
        Path summary = csv.append(result, ResultPaths.summaryCsv(outputDir));

        log.info("Result written: {}", json.toAbsolutePath());
        log.info("Result written: {}", csvFile.toAbsolutePath());
        log.info("Appended to:    {}", summary.toAbsolutePath());
    }

    /** CLI beats YAML beats application default (PRD section 36). */
    private void applyOverrides(BenchmarkConfig config) {
        BenchmarkConfig.Benchmark bench = config.getBenchmark();
        if (target != null) bench.setTarget(target);
        if (operation != null) bench.setOperation(operation);
        if (threads != null) bench.setThreads(threads);
        if (keyCount != null) bench.setKeyCount(keyCount);
        if (payloadBytes != null) bench.setPayloadBytes(payloadBytes);
        if (warmupSeconds != null) bench.setWarmupSeconds(warmupSeconds);
        if (durationSeconds != null) bench.setDurationSeconds(durationSeconds);
        if (randomSeed != null) bench.setRandomSeed(randomSeed);
        if (preload != null) bench.setPreload(preload);
        if (precomputeKeys != null) bench.setPrecomputeKeys(precomputeKeys);
        if (maxErrorRatePercent != null) bench.setMaxErrorRatePercent(maxErrorRatePercent);
        if (operationTimeoutSeconds != null) bench.setOperationTimeoutSeconds(operationTimeoutSeconds);
        if (outputDirectory != null) bench.setOutputDirectory(outputDirectory);

        BenchmarkConfig.Redis redis = config.getRedis();
        if (redisHost != null) redis.setHost(redisHost);
        if (redisPort != null) redis.setPort(redisPort);
        if (redisPassword != null) redis.setPassword(redisPassword);
        if (redisDatabase != null) redis.setDatabase(redisDatabase);

        BenchmarkConfig.Hazelcast hazelcast = config.getHazelcast();
        if (hazelcastAddresses != null && !hazelcastAddresses.isEmpty()) {
            hazelcast.setAddresses(hazelcastAddresses);
        }
        if (hazelcastClusterName != null) hazelcast.setClusterName(hazelcastClusterName);
        if (hazelcastMapName != null) hazelcast.setMapName(hazelcastMapName);
    }

    static final class VersionProvider implements CommandLine.IVersionProvider {
        @Override
        public String[] getVersion() {
            return new String[]{AppInfo.NAME + " " + AppInfo.VERSION};
        }
    }
}
