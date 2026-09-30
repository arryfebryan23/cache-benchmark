package com.example.cachebenchmark.metrics;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;

/**
 * Samples the benchmark JVM's own CPU load once a second during measurement
 * (PRD section 45).
 *
 * <p>On a 4 vCPU box the load generator can saturate before the backend does.
 * When that happens the result describes the generator, not Redis or
 * Hazelcast. This records the evidence; it deliberately draws no conclusion.
 *
 * <p>One daemon thread waking once a second is not enough load to disturb the
 * measurement. If the platform does not expose process CPU load the values
 * come back as NaN rather than failing the run.
 */
public final class CpuSampler implements AutoCloseable {

    private final Thread thread;
    private volatile boolean running = true;

    private double sum;
    private int samples;
    private double max = Double.NaN;

    public CpuSampler() {
        this.thread = new Thread(this::loop, "cpu-sampler");
        this.thread.setDaemon(true);
    }

    public void start() {
        // getProcessCpuLoad() needs a previous reading to compute against, so
        // the very first call always comes back unusable. Burning it here means
        // the first sample the loop takes is already a real number.
        readProcessCpuLoad();
        thread.start();
    }

    private void loop() {
        while (running) {
            addSample(readProcessCpuLoad());
            try {
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void addSample(double load) {
        if (Double.isNaN(load) || load < 0) {
            return;
        }
        synchronized (this) {
            sum += load;
            samples++;
            if (Double.isNaN(max) || load > max) {
                max = load;
            }
        }
    }

    private static double readProcessCpuLoad() {
        OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
        if (bean instanceof com.sun.management.OperatingSystemMXBean sunBean) {
            return sunBean.getProcessCpuLoad();
        }
        return Double.NaN;
    }

    public synchronized double averageLoad() {
        return samples == 0 ? Double.NaN : sum / samples;
    }

    public synchronized double maxLoad() {
        return max;
    }

    @Override
    public void close() {
        // One last reading, so a run shorter than the sampling interval still
        // reports something instead of n/a.
        addSample(readProcessCpuLoad());
        running = false;
        thread.interrupt();
    }
}
