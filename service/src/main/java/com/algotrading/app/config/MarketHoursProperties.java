package com.algotrading.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalTime;
import java.time.ZoneId;

@ConfigurationProperties(prefix = "trading.market-hours")
public record MarketHoursProperties(
        Boolean enabled,
        ZoneId zone,
        LocalTime closeTime
) {
    private static final boolean DEFAULT_ENABLED = true;
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Kolkata");
    private static final LocalTime DEFAULT_CLOSE_TIME = LocalTime.of(15, 30);

    public MarketHoursProperties {
        enabled = enabled == null ? DEFAULT_ENABLED : enabled;
        zone = zone == null ? DEFAULT_ZONE : zone;
        closeTime = closeTime == null ? DEFAULT_CLOSE_TIME : closeTime;
    }
}
