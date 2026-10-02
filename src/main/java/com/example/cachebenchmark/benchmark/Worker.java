package com.example.cachebenchmark.benchmark;

import com.example.cachebenchmark.client.CacheClient;
import com.example.cachebenchmark.key.KeySpace;
import com.example.cachebenchmark.key.UniformKeyGenerator;
import com.example.cachebenchmark.metrics.LatencyRecorder;
import com.example.cachebenchmark.metrics.WorkerMetrics;
import com.example.cachebenchmark.workload.GetWorkload;
import com.example.cachebenchmark.workload.OperationMix;
import com.example.cachebenchmark.workload.SetWorkload;
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
    /** Non-null for a MIXED run, in which case {@link #workload} is unused. */
    private final OperationMix mix;
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
           Double setPercent,
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
        this.mix = setPercent == null ? null : new OperationMix(setPercent, randomSeed, workerId);
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
            if (mix == null) {
                loop();
            } else {
                mixedLoop();
            }
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

    /**
     * Same closed loop as {@link #loop()}, with one extra draw per iteration
     * to pick GET or SET. Kept as a separate method so the single-operation
     * path stays exactly as it was.
     *
     * <p>Each latency goes into the histogram of the operation that produced
     * it; the overall distribution is the merge of the two.
     */
    private void mixedLoop() {
        final long deadline = deadlineHolder.get();
        final CacheClient localClient = client;
        final Workload getWorkload = new GetWorkload();
        final Workload setWorkload = new SetWorkload();
        final OperationMix localMix = mix;
        final byte[] localPayload = payload;
        final UniformKeyGenerator keys = keyGenerator;
        final LatencyRecorder getRecorder = metrics.getMetrics().latency();
        final LatencyRecorder setRecorder = metrics.setMetrics().latency();
        final String[] precomputed = keySpace.precomputed();

        long getAttempted = 0;
        long getSuccessful = 0;
        long getFailed = 0;
        long setAttempted = 0;
        long setSuccessful = 0;
        long setFailed = 0;
        long hits = 0;
        long misses = 0;

        long now = System.nanoTime();
        while (now < deadline) {
            int index = keys.nextIndex();
            String key = precomputed != null ? precomputed[index] : KeySpace.keyFor(index);
            boolean isSet = localMix.nextIsSet();

            if (isSet) {
                setAttempted++;
            } else {
                getAttempted++;
            }
            long start = System.nanoTime();
            try {
                boolean hit = (isSet ? setWorkload : getWorkload).execute(localClient, key, localPayload);
                now = System.nanoTime();
                if (isSet) {
                    setRecorder.record(now - start);
                    setSuccessful++;
                } else {
                    getRecorder.record(now - start);
                    getSuccessful++;
                    if (hit) {
                        hits++;
                    } else {
                        misses++;
                    }
                }
            } catch (Throwable t) {
                now = System.nanoTime();
                if (isSet) {
                    setFailed++;
                } else {
                    getFailed++;
                }
                metrics.recordError(t);
            }
        }

        metrics.getMetrics().flush(getAttempted, getSuccessful, getFailed);
        metrics.setMetrics().flush(setAttempted, setSuccessful, setFailed);
        metrics.flush(getAttempted + setAttempted, getSuccessful + setSuccessful,
                getFailed + setFailed, hits, misses);
    }

    int workerId() {
        return workerId;
    }
}
