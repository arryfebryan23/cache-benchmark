package com.example.cachebenchmark.output;

import com.example.cachebenchmark.benchmark.BenchmarkResult;
import com.example.cachebenchmark.benchmark.BenchmarkRunner;
import com.example.cachebenchmark.metrics.ResultStatus;

import java.io.PrintStream;
import java.util.Map;

/**
 * Human readable summary printed at the end of a run (PRD section 31).
 *
 * <p>Written to stdout, separate from the slf4j log on stderr, so a wrapper
 * script can capture one without the other.
 */
public final class ConsoleReporter {

    private static final String LINE = "==================================================";
    private static final String THIN = "--------------------------------------------------";

    private final PrintStream out;

    public ConsoleReporter(PrintStream out) {
        this.out = out;
    }

    public void report(BenchmarkResult r) {
        out.println();
        out.println(LINE);
        out.println("CACHE BENCHMARK RESULT");
        out.println(LINE);
        out.println();
        out.printf("Target              : %s%n", r.target);
        out.printf("Operation           : %s%n", r.operation);
        if (r.setPercent != null) {
            out.printf("Mix                 : %s%% SET / %s%% GET%n",
                    BenchmarkRunner.formatPercent(r.setPercent),
                    BenchmarkRunner.formatPercent(100 - r.setPercent));
        }
        out.println();
        out.printf("Threads             : %d%n", r.threads);
        out.printf("Key Count           : %d%n", r.keyCount);
        out.printf("Payload             : %d bytes%n", r.payloadBytes);
        out.println();
        out.printf("Warmup              : %.3f sec%n", (double) r.warmupSeconds);
        out.printf("Configured Duration : %.3f sec%n", (double) r.configuredDurationSeconds);
        out.printf("Actual Duration     : %.3f sec%n", r.actualDurationSeconds);
        out.println();
        out.printf("Successful Ops      : %,d%n", r.successfulOperations);
        out.printf("Failed Ops          : %,d%n", r.failedOperations);
        if ("GET".equals(r.operation) || "MIXED".equals(r.operation)) {
            out.printf("Cache Hits          : %,d%n", r.cacheHits);
            out.printf("Cache Misses        : %,d%n", r.cacheMisses);
        }
        out.println();
        out.printf("Throughput          : %,.0f ops/sec%n", r.tps);
        out.printf("Error Rate          : %.3f %%%n", r.errorRatePercent);
        out.println();
        out.println("Latency");
        out.println(THIN);
        out.printf("p50                 : %.3f ms%n", r.latencyMs.p50);
        out.printf("p95                 : %.3f ms%n", r.latencyMs.p95);
        out.printf("p99                 : %.3f ms%n", r.latencyMs.p99);
        out.printf("p99.9               : %.3f ms%n", r.latencyMs.p999);
        out.printf("mean                : %.3f ms%n", r.latencyMs.mean);
        out.printf("max                 : %.3f ms%n", r.latencyMs.max);

        printBreakdown(r);
        printErrors(r);
        printDiagnostics(r);

        out.println();
        out.printf("Result Status       : %s%n", r.status);
        if (r.status == ResultStatus.INVALID) {
            out.println("                      Error rate exceeded the configured threshold.");
            out.println("                      Raw metrics were still written to disk.");
        }
        out.println();
        out.println(LINE);
        out.flush();
    }

    private void printBreakdown(BenchmarkResult r) {
        if (r.operations == null) {
            return;
        }
        out.println();
        out.println("Per Operation          GET             SET");
        out.println(THIN);
        BenchmarkResult.OperationBreakdown get = r.operations.get("GET");
        BenchmarkResult.OperationBreakdown set = r.operations.get("SET");
        out.printf("Actual Share        : %13.2f %%  %13.2f %%%n", get.actualSharePercent, set.actualSharePercent);
        out.printf("Successful Ops      : %,15d  %,15d%n", get.successfulOperations, set.successfulOperations);
        out.printf("Failed Ops          : %,15d  %,15d%n", get.failedOperations, set.failedOperations);
        out.printf("Throughput          : %,15.0f  %,15.0f  ops/sec%n", get.tps, set.tps);
        out.printf("p50                 : %15.3f  %15.3f  ms%n", get.latencyMs.p50, set.latencyMs.p50);
        out.printf("p95                 : %15.3f  %15.3f  ms%n", get.latencyMs.p95, set.latencyMs.p95);
        out.printf("p99                 : %15.3f  %15.3f  ms%n", get.latencyMs.p99, set.latencyMs.p99);
        out.printf("p99.9               : %15.3f  %15.3f  ms%n", get.latencyMs.p999, set.latencyMs.p999);
        out.printf("max                 : %15.3f  %15.3f  ms%n", get.latencyMs.max, set.latencyMs.max);
    }

    private void printErrors(BenchmarkResult r) {
        if (r.errors.isEmpty()) {
            return;
        }
        out.println();
        out.println("Errors");
        out.println(THIN);
        for (Map.Entry<String, BenchmarkResult.ErrorDetail> entry : r.errors.entrySet()) {
            out.printf("%-28s : %,d%n", entry.getKey(), entry.getValue().count);
            out.printf("%-28s   %s%n", "", entry.getValue().sampleMessage);
        }
    }

    private void printDiagnostics(BenchmarkResult r) {
        out.println();
        out.println("Benchmark Client Diagnostics");
        out.println(THIN);
        out.printf("GC Collections      : %d%n", r.gc.collections);
        out.printf("GC Time             : %d ms%n", r.gc.timeMs);
        out.printf("Available CPUs      : %d%n", r.cpu.availableProcessors);
        out.printf("Process CPU Load    : %s avg, %s max%n",
                formatLoad(r.cpu.processCpuLoadAvg), formatLoad(r.cpu.processCpuLoadMax));
        out.println("                      A load near 1.00 means this VM, not the backend,");
        out.println("                      may be the limiting factor.");
    }

    private static String formatLoad(Double load) {
        return load == null ? "n/a" : String.format("%.2f", load);
    }
}
