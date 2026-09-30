package com.example.cachebenchmark.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Loads config/benchmark.yaml into a {@link BenchmarkConfig} (PRD section 35).
 *
 * <p>Resolution order, highest priority last applied by the caller:
 * application default, then YAML, then CLI (PRD section 36).
 */
public final class ConfigLoader {

    public static final String DEFAULT_CONFIG_PATH = "config/benchmark.yaml";

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory())
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private ConfigLoader() {
    }

    /**
     * Reads the config file if one is available.
     *
     * @param explicitPath path given with --config, or null to probe the default location
     * @return the parsed configuration, or the application defaults when no file exists
     *         and no explicit path was requested
     */
    public static BenchmarkConfig load(String explicitPath) {
        if (explicitPath != null && !explicitPath.isBlank()) {
            Path path = Paths.get(explicitPath);
            if (!Files.isRegularFile(path)) {
                throw new ConfigurationException("Configuration error:" + System.lineSeparator()
                        + "config file not found: " + path.toAbsolutePath());
            }
            return read(path);
        }

        Path fallback = Paths.get(DEFAULT_CONFIG_PATH);
        if (Files.isRegularFile(fallback)) {
            return read(fallback);
        }
        return new BenchmarkConfig();
    }

    private static BenchmarkConfig read(Path path) {
        try {
            BenchmarkConfig config = YAML.readValue(path.toFile(), BenchmarkConfig.class);
            return config == null ? new BenchmarkConfig() : config;
        } catch (IOException e) {
            throw new ConfigurationException("Configuration error:" + System.lineSeparator()
                    + "failed to parse " + path.toAbsolutePath() + ": " + rootMessage(e));
        }
    }

    private static String rootMessage(Throwable t) {
        Throwable current = t;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null ? current.getClass().getSimpleName() : message;
    }
}
