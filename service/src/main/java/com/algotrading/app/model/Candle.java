package com.algotrading.app.model;

import java.time.Instant;

/**
 * Immutable domain value object representing a single OHLCV candle.
 * No Zerodha SDK types appear in this record – pure domain model.
 */
public record Candle(
        Instant timestamp,
        double open,
        double high,
        double low,
        double close,
        long volume
) {
    /**
     * Compact constructor for validation.
     */
    public Candle {
        if (timestamp == null) {
            throw new IllegalArgumentException("timestamp must not be null");
        }
        if (open <= 0 || high <= 0 || low <= 0 || close <= 0) {
            throw new IllegalArgumentException(
                    "OHLC values must all be positive, got: open=" + open
                            + " high=" + high + " low=" + low + " close=" + close);
        }
        if (high < low) {
            throw new IllegalArgumentException(
                    "high (" + high + ") must be >= low (" + low + ")");
        }
        if (volume < 0) {
            throw new IllegalArgumentException(
                    "volume must be non-negative, got: " + volume);
        }
    }
}