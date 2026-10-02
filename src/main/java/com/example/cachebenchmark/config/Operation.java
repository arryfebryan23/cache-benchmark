package com.example.cachebenchmark.config;

import java.util.Arrays;
import java.util.stream.Collectors;

/** Supported benchmark operations (PRD section 39). Input is case insensitive. */
public enum Operation {

    GET,
    SET,

    /**
     * GET and SET interleaved in one closed loop. Each iteration draws which
     * of the two to run, with SET chosen {@code setPercent} of the time.
     */
    MIXED;

    public static Operation parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ConfigurationException("operation must be provided. Valid values: " + valid());
        }
        for (Operation o : values()) {
            if (o.name().equalsIgnoreCase(raw.trim())) {
                return o;
            }
        }
        throw new ConfigurationException("unsupported operation '" + raw + "'. Valid values: " + valid());
    }

    /** True when the operation reads, and therefore needs a populated keyspace. */
    public boolean isRead() {
        return this == GET || this == MIXED;
    }

    private static String valid() {
        return Arrays.stream(values()).map(Enum::name).collect(Collectors.joining(", "));
    }
}
