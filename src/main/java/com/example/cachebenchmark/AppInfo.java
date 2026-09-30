package com.example.cachebenchmark;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Application version, filtered in from the POM at build time so the number in
 * a result file always matches the JAR that produced it (PRD section 34).
 */
public final class AppInfo {

    public static final String NAME = "cache-benchmark";
    public static final String VERSION = readVersion();

    private AppInfo() {
    }

    private static String readVersion() {
        try (InputStream in = AppInfo.class.getResourceAsStream("/cache-benchmark.properties")) {
            if (in == null) {
                return "unknown";
            }
            Properties properties = new Properties();
            properties.load(in);
            return properties.getProperty("version", "unknown");
        } catch (IOException e) {
            return "unknown";
        }
    }
}
