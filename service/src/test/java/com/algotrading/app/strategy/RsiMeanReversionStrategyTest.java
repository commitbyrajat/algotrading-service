package com.algotrading.app.strategy;

import com.algotrading.app.model.Candle;
import com.algotrading.app.model.StrategyDecision;
import com.algotrading.app.model.TradingSignal;
import com.algotrading.app.util.BarSeriesFactory;
import com.algotrading.app.util.CandleTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link RsiMeanReversionStrategy}.
 * rsiPeriod=5, oversold=30, overbought=70 → minimum bars = 6.
 *
 * <h3>Why flat data is NOT used for the HOLD test</h3>
 * ta4j 0.22.6 RSIIndicator: when every close-to-close change is zero
 * (identical prices), avgGain=0 and avgLoss=0.  The implementation returns
 * {@code numOf(0)} in that case, producing RSI=0 which falls below the
 * oversold threshold of 30 and triggers a BUY signal — not HOLD.
 *
 * <h3>HOLD data: alternating zigzag [105, 95, 105, 95, ...]</h3>
 * Each up-bar gains +10 and each down-bar loses -10, so avgGain ≈ avgLoss
 * and RSI converges to ≈ 44 — safely inside the neutral [30, 70] band.
 *
 * <h3>BUY data: sustained downtrend (start=600, step=20)</h3>
 * Every bar is a loss; avgGain → 0, avgLoss dominates → RSI → 0 &lt; 30.
 *
 * <h3>SELL data: sustained uptrend (start=100, step=20)</h3>
 * Every bar is a gain; avgLoss → 0, avgGain dominates → RSI → 100 &gt; 70.
 */
class RsiMeanReversionStrategyTest {

    private static final int    RSI_PERIOD   = 5;
    private static final double OVERSOLD     = 30.0;
    private static final double OVERBOUGHT   = 70.0;

    private RsiMeanReversionStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new RsiMeanReversionStrategy(
                new BarSeriesFactory(), RSI_PERIOD, OVERSOLD, OVERBOUGHT);
    }

    // ──────────────────────────── metadata ─────────────────────────────────

    @Test
    void name_returnsRsiIdentifier() {
        assertThat(strategy.name()).isEqualTo("RSI_MEAN_REVERSION");
    }

    // ──────────────────────────── BUY signal ───────────────────────────────

    /**
     * BUY scenario (oversold):
     * A steep, sustained downtrend drives all gains negative,
     * so RSI accumulates only losses → RSI → 0, well below 30.
     */
    @Test
    void evaluate_returnsBuy_whenOversold() {
        // 30-bar downtrend: 600 → drops 20 per bar → ends near 0
        List<Candle> candles = CandleTestFactory.downtrend(30, 600.0, 20.0);

        StrategyDecision decision = strategy.evaluate(candles);

        assertThat(decision.signal()).isEqualTo(TradingSignal.BUY);
        assertThat(decision.reason()).containsIgnoringCase("oversold");
    }

    // ──────────────────────────── SELL signal ──────────────────────────────

    /**
     * SELL scenario (overbought):
     * A steep, sustained uptrend drives all losses to zero,
     * so RSI accumulates only gains → RSI → 100, well above 70.
     */
    @Test
    void evaluate_returnsSell_whenOverbought() {
        // 30-bar uptrend: 100 → climbs 20 per bar
        List<Candle> candles = CandleTestFactory.uptrend(30, 100.0, 20.0);

        StrategyDecision decision = strategy.evaluate(candles);

        assertThat(decision.signal()).isEqualTo(TradingSignal.SELL);
        assertThat(decision.reason()).containsIgnoringCase("overbought");
    }

    // ──────────────────────────── HOLD signal ──────────────────────────────

    /**
     * Alternating zigzag prices: [105, 95, 105, 95, ...] over 30 bars.
     *
     * <p>Each up-move = +10, each down-move = -10.
     * Wilder's smoothing keeps avgGain ≈ avgLoss → RSI ≈ 44.
     * That is inside the neutral band [30, 70] → HOLD.</p>
     *
     * <p>Do NOT use flat(n, price) here: ta4j 0.22.6 RSIIndicator returns 0
     * when both avgGain and avgLoss are zero (all-identical closes),
     * which would falsely trigger a BUY signal.</p>
     */
    @Test
    void evaluate_returnsHold_whenNeutral() {
        List<Candle> candles = zigzag(30, 100.0, 5.0);

        StrategyDecision decision = strategy.evaluate(candles);

        assertThat(decision.signal()).isEqualTo(TradingSignal.HOLD);
        assertThat(decision.reason()).containsIgnoringCase("neutral");
    }

    // ──────────────────────────── guard rails ──────────────────────────────

    @Test
    void evaluate_throwsIllegalArgument_whenTooFewCandles() {
        // minimum is RSI_PERIOD + 1 = 6; give only 5
        List<Candle> tooFew = CandleTestFactory.flat(RSI_PERIOD, 100.0);
        assertThatThrownBy(() -> strategy.evaluate(tooFew))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RSI_MEAN_REVERSION");
    }

    @Test
    void evaluate_throwsIllegalArgument_whenCandlesIsNull() {
        assertThatThrownBy(() -> strategy.evaluate(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decision_reasonContainsRsiValue() {
        List<Candle> candles = CandleTestFactory.flat(20, 100.0);
        StrategyDecision d = strategy.evaluate(candles);
        // The reason string must always embed the numeric RSI value
        assertThat(d.reason()).containsPattern("\\d+\\.\\d+");
    }

    @Test
    void constructor_rejects_oversoldGreaterThanOverbought() {
        assertThatThrownBy(() ->
                new RsiMeanReversionStrategy(new BarSeriesFactory(), 14, 70.0, 30.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("oversoldThreshold");
    }

    // ──────────────────────────── private helpers ───────────────────────────

    /**
     * Builds a list of candles with alternating high/low close prices.
     * Even-indexed bars close at {@code base + delta},
     * odd-indexed bars close at  {@code base - delta}.
     *
     * <p>This produces equal average gains and losses so that
     * Wilder's RSI converges to ≈ 44 – safely inside the neutral band.</p>
     *
     * @param count number of bars to generate
     * @param base  mid-point price
     * @param delta half-swing; must satisfy {@code base - delta > 0}
     */
    private static List<Candle> zigzag(int count, double base, double delta) {
        List<Candle> list = new ArrayList<>(count);
        Instant start = Instant.parse("2024-01-01T00:00:00Z");
        for (int i = 0; i < count; i++) {
            double close  = (i % 2 == 0) ? (base + delta) : (base - delta);
            double spread = delta * 0.1;  // tiny intra-bar spread
            list.add(new Candle(
                    start.plus(i, ChronoUnit.DAYS),
                    close,
                    close + spread,
                    close - spread,
                    close,
                    100_000L
            ));
        }
        return list;
    }
}