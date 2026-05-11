package com.algotrading.app.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable result of evaluating a {@link com.algotrading.app.strategy.TechnicalStrategy}.
 */
public record StrategyDecision(
        String strategyName,
        TradingSignal signal,
        String reason,
        Instant evaluatedAt
) {
    public StrategyDecision {
        Objects.requireNonNull(strategyName, "strategyName must not be null");
        Objects.requireNonNull(signal,       "signal must not be null");
        Objects.requireNonNull(reason,       "reason must not be null");
        Objects.requireNonNull(evaluatedAt,  "evaluatedAt must not be null");
    }

    /**
     * Factory that stamps the current instant automatically.
     */
    public static StrategyDecision of(String strategyName, TradingSignal signal, String reason) {
        return new StrategyDecision(strategyName, signal, reason, Instant.now());
    }
}