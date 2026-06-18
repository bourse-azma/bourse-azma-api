package com.ernoxin.boorsazmaapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.tsetmc")
public record TsetmcApiProperties(
        String baseUrl,
        long connectTimeoutMs,
        long readTimeoutMs
) {
    public TsetmcApiProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:9000/tsetmc";
        }
        baseUrl = baseUrl.replaceAll("/+$", "");
        if (connectTimeoutMs <= 0) {
            connectTimeoutMs = 3_000;
        }
        if (readTimeoutMs <= 0) {
            readTimeoutMs = 8_000;
        }
    }
}
