package com.example.cachebenchmark;

import com.example.cachebenchmark.benchmark.BenchmarkResult;
import com.example.cachebenchmark.metrics.ResultStatus;
import com.example.cachebenchmark.output.CsvReporter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PRD section 51: one row per invocation, columns lined up with the header. */
class CsvReporterTest {

    private static BenchmarkResult sampleResult() {
        BenchmarkResult r = new BenchmarkResult();
        r.timestamp = "2026-09-30T14:30:00+07:00";
        r.target = "redis";
        r.operation = "GET";
        r.threads = 16;
        r.keyCount = 1_000_000;
        r.payloadBytes = 1024;
        r.warmupSeconds = 30;
        r.configuredDurationSeconds = 60;
        r.actualDurationSeconds = 60.008;
        r.successfulOperations = 20_453_221L;
        r.failedOperations = 0L;
        r.tps = 340_841.2;
        r.latencyMs.p50 = 0.421;
        r.latencyMs.p95 = 1.120;
        r.latencyMs.p99 = 2.741;
        r.latencyMs.max = 18.892;
        r.errorRatePercent = 0.0;
        r.status = ResultStatus.VALID;
        return r;
    }

    @Test
    void rowHasTheSameColumnCountAsTheHeader(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("result.csv");
        new CsvReporter().write(sampleResult(), file);

        List<String> lines = Files.readAllLines(file);
        assertEquals(2, lines.size());

        int headerColumns = lines.get(0).split(",", -1).length;
        int rowColumns = lines.get(1).split(",", -1).length;
        assertEquals(headerColumns, rowColumns);
        assertEquals(23, headerColumns);
    }

    @Test
    void summaryWritesTheHeaderOnceAndAppendsEachRun(@TempDir Path dir) throws IOException {
        Path summary = dir.resolve("summary.csv");
        CsvReporter reporter = new CsvReporter();

        reporter.append(sampleResult(), summary);
        reporter.append(sampleResult(), summary);
        reporter.append(sampleResult(), summary);

        List<String> lines = Files.readAllLines(summary);
        assertEquals(4, lines.size(), "expected one header plus three rows");
        assertTrue(lines.get(0).startsWith("timestamp,"));
        assertTrue(lines.get(1).startsWith("2026-09-30"));
    }

    @Test
    void mixedRunFillsThePerOperationColumns(@TempDir Path dir) throws IOException {
        BenchmarkResult r = sampleResult();
        r.operation = "MIXED";
        r.setPercent = 20.0;
        r.operations = new java.util.LinkedHashMap<>();
        BenchmarkResult.OperationBreakdown get = new BenchmarkResult.OperationBreakdown();
        get.tps = 272_672.96;
        get.latencyMs.p99 = 2.5;
        BenchmarkResult.OperationBreakdown set = new BenchmarkResult.OperationBreakdown();
        set.tps = 68_168.24;
        set.latencyMs.p99 = 3.25;
        r.operations.put("GET", get);
        r.operations.put("SET", set);

        Path file = dir.resolve("result.csv");
        new CsvReporter().write(r, file);
        String row = Files.readAllLines(file).get(1);
        assertTrue(row.endsWith(",20.00,272672.96,68168.24,2.500,3.250"), row);
    }

    @Test
    void singleOperationRunLeavesThePerOperationColumnsEmpty(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("result.csv");
        new CsvReporter().write(sampleResult(), file);
        String row = Files.readAllLines(file).get(1);
        assertTrue(row.endsWith(",VALID,,,,,"), row);
    }

    @Test
    void usesADotAsTheDecimalSeparator(@TempDir Path dir) throws IOException {
        // On a locale that formats numbers with a comma, an unqualified
        // String.format would split a single value across two CSV columns.
        Path file = dir.resolve("result.csv");
        new CsvReporter().write(sampleResult(), file);
        String row = Files.readAllLines(file).get(1);
        assertTrue(row.contains("0.421"), row);
        assertTrue(row.contains("60.008"), row);
    }
}
