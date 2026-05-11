package com.algotrading.app.strategy;

import com.algotrading.app.model.Candle;
import com.algotrading.app.model.StrategyDecision;
import com.algotrading.app.model.TradingSignal;
import com.algotrading.app.util.BarSeriesFactory;
import com.algotrading.app.util.CandleTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link SmaCrossoverStrategy}.
 *
 * Uses fast=3, slow=5 so we need only ~7 candles rather than 31,
 * making test data easy to reason about.
 */
class SmaCrossoverStrategyTest {

    // Small periods so test data stays compact
    private static final int FAST = 3;
    private static final int SLOW = 5;

    private SmaCrossoverStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new SmaCrossoverStrategy(new BarSeriesFactory(), FAST, SLOW);
    }

    // ──────────────────────────── metadata ─────────────────────────────────

    @Test
    void name_returnsSmaCrossoverIdentifier() {
        assertThat(strategy.name()).isEqualTo("SMA_CROSSOVER");
    }

    // ──────────────────────────── BUY signal ───────────────────────────────

    /**
     * 6 flat bars keep both SMAs equal (no crossover yet).
     * Single spike on bar 7 drives SMA3 above SMA5 on exactly the last bar.
     *
     * <p>Verified arithmetic:
     * prev → SMA3=100.00, SMA5=100.00 (fast ≤ slow ✓)
     * last → SMA3=400.00, SMA5=280.00 (fast > slow ✓) → BUY</p>
     */
    @Test
    void evaluate_returnsBuy_onGoldenCross() {
        // 6 flat bars then one sharp spike on the last bar
        double[] closes = { 100, 100, 100, 100, 100, 100, 1000 };
        List<Candle> candles = CandleTestFactory.fromCloses(closes);

        StrategyDecision decision = strategy.evaluate(candles);

        assertThat(decision.signal()).isEqualTo(TradingSignal.BUY);
        assertThat(decision.reason()).containsIgnoringCase("crossed above");
        assertThat(decision.strategyName()).isEqualTo("SMA_CROSSOVER");
    }

    // ──────────────────────────── SELL signal ──────────────────────────────

    /**
     * 6 flat bars keep both SMAs equal (no crossover yet).
     * Single crash on bar 7 drives SMA3 below SMA5 on exactly the last bar.
     *
     * <p>Verified arithmetic:
     * prev → SMA3=200.00, SMA5=200.00 (fast ≥ slow ✓)
     * last → SMA3=133.67, SMA5=160.20 (fast < slow ✓) → SELL</p>
     */
    @Test
    void evaluate_returnsSell_onDeathCross() {
        // 6 flat bars then one sharp crash on the last bar
        double[] closes = { 200, 200, 200, 200, 200, 200, 1 };
        List<Candle> candles = CandleTestFactory.fromCloses(closes);

        StrategyDecision decision = strategy.evaluate(candles);

        assertThat(decision.signal()).isEqualTo(TradingSignal.SELL);
        assertThat(decision.reason()).containsIgnoringCase("crossed below");
    }

    // ──────────────────────────── HOLD signal ──────────────────────────────

    /**
     * HOLD scenario:
     * Completely flat prices → both SMAs equal at all times → no crossover.
     */
    @Test
    void evaluate_returnsHold_whenPricesAreFlat() {
        List<Candle> candles = CandleTestFactory.flat(12, 150.0);

        StrategyDecision decision = strategy.evaluate(candles);

        assertThat(decision.signal()).isEqualTo(TradingSignal.HOLD);
        assertThat(decision.reason()).containsIgnoringCase("No crossover");
    }

    // ──────────────────────────── guard rails ──────────────────────────────

    @Test
    void evaluate_throwsIllegalArgument_whenTooFewCandles() {
        // SLOW+1 = 6 minimum; give only 5
        List<Candle> tooFew = CandleTestFactory.flat(SLOW, 100.0);
        assertThatThrownBy(() -> strategy.evaluate(tooFew))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SMA_CROSSOVER");
    }

    @Test
    void evaluate_throwsIllegalArgument_whenCandlesIsNull() {
        assertThatThrownBy(() -> strategy.evaluate(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decision_hasNonNullTimestamp() {
        List<Candle> candles = CandleTestFactory.flat(10, 100.0);
        StrategyDecision d = strategy.evaluate(candles);
        assertThat(d.evaluatedAt()).isNotNull()
                .isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void constructor_rejects_fastPeriodGreaterThanSlow() {
        assertThatThrownBy(() -> new SmaCrossoverStrategy(new BarSeriesFactory(), 10, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fastPeriod");
    }
}