package com.algotrading.app.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The three possible trading signals produced by any strategy.
 */
@Schema(description = "Trading signal produced by a strategy evaluation.")
public enum TradingSignal {
    /** A favourable entry or add-to-position opportunity is detected. */
    BUY,
    /** A favourable exit or short-entry opportunity is detected. */
    SELL,
    /** No actionable signal – remain in current position. */
    HOLD
}
