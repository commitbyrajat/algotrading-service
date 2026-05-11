package com.algotrading.app.model;

/**
 * The three possible trading signals produced by any strategy.
 */
public enum TradingSignal {
    /** A favourable entry or add-to-position opportunity is detected. */
    BUY,
    /** A favourable exit or short-entry opportunity is detected. */
    SELL,
    /** No actionable signal – remain in current position. */
    HOLD
}