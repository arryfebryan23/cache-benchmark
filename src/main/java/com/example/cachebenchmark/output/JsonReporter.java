package com.example.cachebenchmark.output;

import com.example.cachebenchmark.benchmark.BenchmarkResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Writes the machine-readable result (PRD sections 32, 33 and 34).
 *
 * <p>Field order follows the {@code @JsonPropertyOrder} on
 * {@link BenchmarkResult}, so the file reads top-down the same way the console
 * summary does.
 */
public final class JsonReporter {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public Path write(BenchmarkResult result, Path target) throws IOException {
        MAPPER.writeValue(target.toFile(), result);
        return target;
    }
}
