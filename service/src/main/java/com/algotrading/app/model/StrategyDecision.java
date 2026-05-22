package com.algotrading.app.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable result of evaluating a {@link com.algotrading.app.strategy.TechnicalStrategy}.
 */
@Schema(description = "Decision produced by evaluating a technical strategy over historical candles.")
public record StrategyDecision(
        @Schema(description = "Strategy name that was evaluated.", example = "SMA_CROSSOVER")
        String strategyName,
        @Schema(description = "Actionable signal produced by the strategy.", example = "BUY")
        TradingSignal signal,
        @Schema(description = "Human-readable explanation for the signal.", example = "Fast SMA crossed above slow SMA on the latest candle.")
        String reason,
        @Schema(description = "Suggested BUY quantity. Present only when signal is BUY.")
        QuantitySuggestion quantitySuggestion,
        @Schema(description = "Timestamp when the evaluation completed.", example = "2026-05-16T10:15:30Z")
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
        return new StrategyDecision(strategyName, signal, reason, null, Instant.now());
    }

    public StrategyDecision withQuantitySuggestion(QuantitySuggestion quantitySuggestion) {
        return new StrategyDecision(strategyName, signal, reason, quantitySuggestion, evaluatedAt);
    }
}
