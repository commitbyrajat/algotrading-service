package com.algotrading.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Strongly-typed binding for all {@code kite.*} properties in application.yml.
 * Eliminates scattered {@code @Value} annotations and makes the config
 * surface explicit and testable.
 */
@ConfigurationProperties(prefix = "kite")
public record KiteProperties(
        String apiKey,
        String apiSecret,
        String userId,
        String redirectUrl
) {
    public KiteProperties {
        if (apiKey    == null || apiKey.isBlank())    throw new IllegalStateException("kite.api-key must be set");
        if (apiSecret == null || apiSecret.isBlank()) throw new IllegalStateException("kite.api-secret must be set");
        if (userId    == null || userId.isBlank())    throw new IllegalStateException("kite.user-id must be set");
        if (redirectUrl == null || redirectUrl.isBlank()) throw new IllegalStateException("kite.redirect-url must be set");
    }
}