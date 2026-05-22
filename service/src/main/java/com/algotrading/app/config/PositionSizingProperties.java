package com.algotrading.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Strongly-typed binding for ATR position sizing configuration.
 */
@ConfigurationProperties(prefix = "trading.position-sizing")
public record PositionSizingProperties(
        double capital,
        double riskPercent,
        int atrPeriod,
        double atrMultiplier,
        double maxPortfolioExposurePercent
) {
    public PositionSizingProperties {
        if (capital <= 0) {
            throw new IllegalStateException("trading.position-sizing.capital must be greater than 0");
        }
        if (riskPercent <= 0) {
            throw new IllegalStateException("trading.position-sizing.risk-percent must be greater than 0");
        }
        if (atrPeriod <= 0) {
            throw new IllegalStateException("trading.position-sizing.atr-period must be greater than 0");
        }
        if (atrMultiplier <= 0) {
            throw new IllegalStateException("trading.position-sizing.atr-multiplier must be greater than 0");
        }
        if (maxPortfolioExposurePercent <= 0) {
            throw new IllegalStateException(
                    "trading.position-sizing.max-portfolio-exposure-percent must be greater than 0");
        }
    }
}
