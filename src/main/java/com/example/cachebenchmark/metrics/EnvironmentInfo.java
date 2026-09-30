package com.example.cachebenchmark.metrics;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Where and on what this run happened (PRD section 34), so a number in a
 * spreadsheet months from now can still be traced back to a machine.
 */
public final class EnvironmentInfo {

    public final String hostname;
    public final String javaVersion;
    public final String javaVendor;
    public final String jvmName;
    public final String jvmVersion;
    public final String osName;
    public final String osVersion;
    public final String osArch;
    public final int availableProcessors;
    public final long maxJvmHeapBytes;
    public final String jvmArguments;

    private EnvironmentInfo(String hostname, String javaVersion, String javaVendor, String jvmName,
                           String jvmVersion, String osName, String osVersion, String osArch,
                           int availableProcessors, long maxJvmHeapBytes, String jvmArguments) {
        this.hostname = hostname;
        this.javaVersion = javaVersion;
        this.javaVendor = javaVendor;
        this.jvmName = jvmName;
        this.jvmVersion = jvmVersion;
        this.osName = osName;
        this.osVersion = osVersion;
        this.osArch = osArch;
        this.availableProcessors = availableProcessors;
        this.maxJvmHeapBytes = maxJvmHeapBytes;
        this.jvmArguments = jvmArguments;
    }

    public static EnvironmentInfo capture() {
        return new EnvironmentInfo(
                hostname(),
                System.getProperty("java.version"),
                System.getProperty("java.vendor"),
                System.getProperty("java.vm.name"),
                System.getProperty("java.vm.version"),
                System.getProperty("os.name"),
                System.getProperty("os.version"),
                System.getProperty("os.arch"),
                Runtime.getRuntime().availableProcessors(),
                Runtime.getRuntime().maxMemory(),
                String.join(" ", ManagementFactory.getRuntimeMXBean().getInputArguments()));
    }

    private static String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            String fromEnv = System.getenv("HOSTNAME");
            return fromEnv != null ? fromEnv : "unknown";
        }
    }
}
