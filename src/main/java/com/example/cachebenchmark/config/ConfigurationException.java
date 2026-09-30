package com.example.cachebenchmark.config;

/**
 * Raised for any invalid configuration. The message is shown to the operator
 * verbatim and the process exits with code 1 (PRD sections 40 and 62).
 */
public class ConfigurationException extends RuntimeException {

    public ConfigurationException(String message) {
        super(message);
    }
}
