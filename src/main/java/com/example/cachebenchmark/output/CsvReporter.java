package com.example.cachebenchmark.output;

import com.example.cachebenchmark.benchmark.BenchmarkResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

/**
 * One row per benchmark invocation (PRD section 51).
 *
 * <p>Written twice: a standalone file next to the JSON, and appended to a
 * single summary.csv under the output root so a whole matrix lands in one
 * table.
 */
public final class CsvReporter {

    static final String HEADER = String.join(",",
            "timestamp",
            "target",
            "operation",
            "threads",
            "key_count",
            "payload_bytes",
            "warmup_seconds",
            "duration_seconds",
            "actual_duration_seconds",
            "successful_operations",
            "failed_operations",
            "tps",
            "p50_ms",
            "p95_ms",
            "p99_ms",
            "max_ms",
            "error_rate_percent",
            "status");

    public Path write(BenchmarkResult result, Path target) throws IOException {
        String content = HEADER + System.lineSeparator() + row(result) + System.lineSeparator();
        Files.writeString(target, content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return target;
    }

    /** Appends to the shared summary, writing the header only when creating it. */
    public Path append(BenchmarkResult result, Path summary) throws IOException {
        boolean fresh = !Files.exists(summary) || Files.size(summary) == 0;
        StringBuilder content = new StringBuilder();
        if (fresh) {
            content.append(HEADER).append(System.lineSeparator());
        }
        content.append(row(result)).append(System.lineSeparator());

        Files.writeString(summary, content.toString(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        return summary;
    }

    static String row(BenchmarkResult r) {
        return String.join(",",
                r.timestamp,
                r.target,
                r.operation,
                Integer.toString(r.threads),
                Integer.toString(r.keyCount),
                Integer.toString(r.payloadBytes),
                Integer.toString(r.warmupSeconds),
                Integer.toString(r.configuredDurationSeconds),
                fixed(r.actualDurationSeconds, 3),
                Long.toString(r.successfulOperations),
                Long.toString(r.failedOperations),
                fixed(r.tps, 2),
                fixed(r.latencyMs.p50, 3),
                fixed(r.latencyMs.p95, 3),
                fixed(r.latencyMs.p99, 3),
                fixed(r.latencyMs.max, 3),
                fixed(r.errorRatePercent, 4),
                r.status.name());
    }

    private static String fixed(double value, int decimals) {
        // Locale.ROOT keeps the decimal separator a dot on a machine configured
        // for a comma, which would otherwise break the CSV column count.
        return String.format(Locale.ROOT, "%." + decimals + "f", value);
    }
}
