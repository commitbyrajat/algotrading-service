package com.algotrading.app.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers auth-layer beans that must not live in {@code KiteConfig}
 * to avoid a circular dependency between {@link KiteSessionStore}
 * and {@link com.algotrading.app.config.KiteConfig}.
 */
@Configuration
public class KiteAuthConfig {

    /**
     * Singleton session store shared between {@link KiteAuthService}
     * and any component that needs to read the current token status.
     */
    @Bean
    public KiteSessionStore kiteSessionStore() {
        return new KiteSessionStore();
    }
}