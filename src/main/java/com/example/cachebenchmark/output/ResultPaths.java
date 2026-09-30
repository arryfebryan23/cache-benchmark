package com.example.cachebenchmark.output;

import com.example.cachebenchmark.benchmark.BenchmarkResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Where result files go (PRD sections 32 and 50).
 *
 * <p>Layout is results/&lt;target&gt;/&lt;operation&gt;/, and the file name carries
 * enough configuration to identify a run without opening it:
 * {@code redis_GET_t16_p1024_20260930_143000.json}
 */
public final class ResultPaths {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final Path directory;
    private final String baseName;

    private ResultPaths(Path directory, String baseName) {
        this.directory = directory;
        this.baseName = baseName;
    }

    public static ResultPaths create(String outputDirectory, BenchmarkResult result) throws IOException {
        Path directory = Paths.get(outputDirectory).resolve(result.target).resolve(result.operation);
        Files.createDirectories(directory);

        String baseName = String.format("%s_%s_t%d_p%d_%s",
                result.target,
                result.operation,
                result.threads,
                result.payloadBytes,
                LocalDateTime.now().format(STAMP));

        return new ResultPaths(directory, baseName);
    }

    public Path json() {
        return directory.resolve(baseName + ".json");
    }

    public Path csv() {
        return directory.resolve(baseName + ".csv");
    }

    /**
     * A single appendable file collecting every run under the output root.
     * Not required by the PRD, but it turns the 84-run matrix into one
     * spreadsheet instead of 84 files to merge by hand.
     */
    public static Path summaryCsv(String outputDirectory) throws IOException {
        Path root = Paths.get(outputDirectory);
        Files.createDirectories(root);
        return root.resolve("summary.csv");
    }
}
