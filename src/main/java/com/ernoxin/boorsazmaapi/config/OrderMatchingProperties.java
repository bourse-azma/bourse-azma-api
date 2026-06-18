package com.ernoxin.boorsazmaapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.order-matching")
public record OrderMatchingProperties(
        long pollIntervalMs,
        boolean enabled
) {
    public OrderMatchingProperties {
        if (pollIntervalMs <= 0) {
            pollIntervalMs = 5_000;
        }
    }
}
