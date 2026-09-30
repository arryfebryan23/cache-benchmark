package com.example.cachebenchmark.benchmark;

import com.example.cachebenchmark.client.CacheClient;
import com.example.cachebenchmark.key.KeySpace;
import com.example.cachebenchmark.key.UniformKeyGenerator;
import com.example.cachebenchmark.metrics.LatencyRecorder;
import com.example.cachebenchmark.metrics.WorkerMetrics;
import com.example.cachebenchmark.workload.Workload;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

/**
 * One closed-loop benchmark worker (PRD sections 5 and 23).
 *
 * <p>Send a request, wait for the response, record it, send the next. No
 * batching, no pipelining, no sleeping.
 *
 * <p>The loop is written to allocate nothing and to call System.nanoTime()
 * twice per iteration: the timestamp taken after an operation completes is
 * reused as the deadline check for the next one (PRD section 67).
 */
final class Worker implements Runnable {

    private final int workerId;
    private final CacheClient client;
    private final Workload workload;
    private final KeySpace keySpace;
    private final byte[] payload;
    private final UniformKeyGenerator keyGenerator;
    private final WorkerMetrics metrics;

    private final CountDownLatch readyLatch;
    private final CountDownLatch startLatch;
    private final CountDownLatch doneLatch;
    private final AtomicLong deadlineHolder;

    Worker(int workerId,
           CacheClient client,
           Workload workload,
           KeySpace keySpace,
           byte[] payload,
           long randomSeed,
           WorkerMetrics metrics,
           CountDownLatch readyLatch,
           CountDownLatch startLatch,
           CountDownLatch doneLatch,
           AtomicLong deadlineHolder) {
        this.workerId = workerId;
        this.client = client;
        this.workload = workload;
        this.keySpace = keySpace;
        this.payload = payload;
        this.keyGenerator = new UniformKeyGenerator(randomSeed, workerId, keySpace.keyCount());
        this.metrics = metrics;
        this.readyLatch = readyLatch;
        this.startLatch = startLatch;
        this.doneLatch = doneLatch;
        this.deadlineHolder = deadlineHolder;
    }

    @Override
    public void run() {
        try {
            // Announce readiness, then block until every worker has done the
            // same. The measurement clock only starts once all of them are
            // parked here (PRD section 24).
            readyLatch.countDown();
            startLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            doneLatch.countDown();
            return;
        }

        try {
            loop();
        } finally {
            doneLatch.countDown();
        }
    }

    private void loop() {
        final long deadline = deadlineHolder.get();
        final CacheClient localClient = client;
        final Workload localWorkload = workload;
        final byte[] localPayload = payload;
        final UniformKeyGenerator keys = keyGenerator;
        final LatencyRecorder recorder = metrics.latency();

        // Hoisted so the hot loop reads a local array rather than chasing
        // fields. Null when precomputeKeys is off.
        final String[] precomputed = keySpace.precomputed();

        long attempted = 0;
        long successful = 0;
        long failed = 0;
        long hits = 0;
        long misses = 0;

        long now = System.nanoTime();
        while (now < deadline) {
            int index = keys.nextIndex();
            String key = precomputed != null ? precomputed[index] : KeySpace.keyFor(index);

            attempted++;
            long start = System.nanoTime();
            try {
                boolean hit = localWorkload.execute(localClient, key, localPayload);
                now = System.nanoTime();
                recorder.record(now - start);
                successful++;
                if (hit) {
                    hits++;
                } else {
                    misses++;
                }
            } catch (Throwable t) {
                now = System.nanoTime();
                failed++;
                metrics.recordError(t);
            }
        }

        metrics.flush(attempted, successful, failed, hits, misses);
    }

    int workerId() {
        return workerId;
    }
}
