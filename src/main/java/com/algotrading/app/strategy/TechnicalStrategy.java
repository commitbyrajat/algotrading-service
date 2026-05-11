package com.algotrading.app.strategy;

import com.algotrading.app.model.Candle;
import com.algotrading.app.model.StrategyDecision;

import java.util.List;

/**
 * Contract for all technical analysis strategies.
 *
 * <p>Implementations must be:</p>
 * <ul>
 *   <li>Stateless (safe for concurrent evaluation)</li>
 *   <li>Configurable via constructor parameters</li>
 *   <li>Testable without Spring context</li>
 * </ul>
 */
public interface TechnicalStrategy {

    /**
     * Unique, human-readable strategy identifier used as the registry key.
     *
     * @return strategy name, never {@code null} or blank
     */
    String name();

    /**
     * Evaluate the strategy against the supplied candle history.
     *
     * @param candles time-ordered list of candles, oldest first; must contain
     *                enough bars for the strategy's warmup period
     * @return a {@link StrategyDecision} – never {@code null}
     * @throws IllegalArgumentException if {@code candles} contains fewer bars
     *                                  than the strategy requires
     */
    StrategyDecision evaluate(List<Candle> candles);
}