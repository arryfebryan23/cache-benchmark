package com.example.cachebenchmark.config;

import java.util.Arrays;
import java.util.stream.Collectors;

/** Supported benchmark backends (PRD section 38). */
public enum Target {

    REDIS,
    HAZELCAST;

    public static Target parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ConfigurationException("target must be provided. Valid values: " + valid());
        }
        for (Target t : values()) {
            if (t.name().equalsIgnoreCase(raw.trim())) {
                return t;
            }
        }
        throw new ConfigurationException("unsupported target '" + raw + "'. Valid values: " + valid());
    }

    public String lowerCase() {
        return name().toLowerCase();
    }

    private static String valid() {
        return Arrays.stream(values()).map(Target::lowerCase).collect(Collectors.joining(", "));
    }
}
