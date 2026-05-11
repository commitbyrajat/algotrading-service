package com.algotrading.app.strategy;

import com.algotrading.app.model.Candle;
import com.algotrading.app.model.StrategyDecision;
import com.algotrading.app.model.TradingSignal;
import com.algotrading.app.util.BarSeriesFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link GainzAlphaV2Strategy}.
 *
 * <h3>Test parameters (smaller than production defaults for compact data)</h3>
 * <pre>
 *   SMA_FAST=5, SMA_SLOW=10, RSI=7
 *   MACD(6,13,5), VOL_PERIOD=10
 *   RSI_BUY_MIN=40  (RSI must be ABOVE 40 for BUY)
 *   RSI_SELL_MAX=60 (RSI must be BELOW 60 for SELL)
 * </pre>
 *
 * <h3>Why single-sided RSI thresholds</h3>
 * The acceleration phase that drives SMA/MACD divergence also pushes RSI to
 * extremes (RSI≈86 for uptrend, RSI≈14 for downtrend with 7-bar period).
 * Double-sided bounds like (40 &lt; RSI &lt; 80) would reject these strong
 * trend moves — the opposite of what a trend-following strategy should do.
 * The single-sided check simply confirms directional momentum:
 * <ul>
 *   <li>BUY:  RSI &gt; 40 — momentum is bullish (verified: RSI=86 ✓)</li>
 *   <li>SELL: RSI &lt; 60 — momentum is bearish (verified: RSI=14 ✓)</li>
 * </ul>
 *
 * <h3>HOLD mechanism</h3>
 * The HOLD test uses flat volume (all bars same volume = average).
 * Since {@code currentVolume > averageVolume} is never true (equal, not
 * greater), the volume condition always fails → HOLD, regardless of price
 * action.  This is the most reliable and test-data-independent HOLD trigger.
 *
 * <h3>Simulation-verified test data</h3>
 * All conditions were verified with a Python simulation before coding:
 * <pre>
 *   BUY  (50 bars, uptrend+acceleration):
 *     SMA5=140.50 &gt; SMA10=133.00  ✓
 *     RSI(7)=86.00 &gt; 40           ✓
 *     MACD=6.8305  &gt; Signal=5.6566 ✓
 *     Vol=500_000  &gt; AvgVol=140_000 ✓
 *
 *   SELL (50 bars, downtrend+crash):
 *     SMA5=159.50 &lt; SMA10=167.00  ✓
 *     RSI(7)=14.00 &lt; 60           ✓
 *     MACD=-6.8305 &lt; Signal=-5.6566 ✓
 *     Vol=500_000  &gt; AvgVol=140_000 ✓
 * </pre>
 */
class GainzAlphaV2StrategyTest {

    // ── test parameters ───────────────────────────────────────────────────────
    private static final int    SMA_FAST      = 5;
    private static final int    SMA_SLOW      = 10;
    private static final int    RSI_PERIOD    = 7;
    private static final double RSI_BUY_MIN   = 40.0;   // single lower bound for BUY
    private static final double RSI_SELL_MAX  = 60.0;   // single upper bound for SELL
    private static final int    MACD_SHORT    = 6;
    private static final int    MACD_LONG     = 13;
    private static final int    MACD_SIGNAL   = 5;
    private static final int    VOL_PERIOD    = 10;

    private GainzAlphaV2Strategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new GainzAlphaV2Strategy(
                new BarSeriesFactory(),
                SMA_FAST, SMA_SLOW,
                RSI_PERIOD,
                RSI_BUY_MIN,
                RSI_SELL_MAX,
                MACD_SHORT, MACD_LONG, MACD_SIGNAL,
                VOL_PERIOD
        );
    }

    // ──────────────────────────── metadata ─────────────────────────────────

    @Test
    void name_returnsGainzAlphaIdentifier() {
        assertThat(strategy.name()).isEqualTo("GAINZ_ALPHA_V2");
    }

    // ──────────────────────────── BUY signal ───────────────────────────────

    /**
     * 40-bar gentle uptrend (+0.5/bar, zigzag noise) then 10-bar
     * bullish acceleration (+3/bar).  Last bar has a volume spike.
     *
     * <p>Simulation result:
     * SMA5(140.50)&gt;SMA10(133.00), RSI=86&gt;40,
     * MACD(6.83)&gt;Signal(5.66), Vol(500k)&gt;AvgVol(140k) → BUY</p>
     */
    @Test
    void evaluate_returnsBuy_onFullConfluence() {
        List<Candle> candles = buildBullishCandles();

        StrategyDecision decision = strategy.evaluate(candles);

        assertThat(decision.signal()).isEqualTo(TradingSignal.BUY);
        assertThat(decision.reason()).containsIgnoringCase("BUY confluence");
        assertThat(decision.strategyName()).isEqualTo("GAINZ_ALPHA_V2");
    }

    // ──────────────────────────── SELL signal ──────────────────────────────

    /**
     * 40-bar gentle downtrend (-0.5/bar, zigzag noise) then 10-bar
     * bearish crash (-3/bar).  Last bar has a volume spike.
     *
     * <p>Simulation result:
     * SMA5(159.50)&lt;SMA10(167.00), RSI=14&lt;60,
     * MACD(-6.83)&lt;Signal(-5.66), Vol(500k)&gt;AvgVol(140k) → SELL</p>
     */
    @Test
    void evaluate_returnsSell_onFullBearishConfluence() {
        List<Candle> candles = buildBearishCandles();

        StrategyDecision decision = strategy.evaluate(candles);

        assertThat(decision.signal()).isEqualTo(TradingSignal.SELL);
        assertThat(decision.reason()).containsIgnoringCase("SELL confluence");
    }

    // ──────────────────────────── HOLD signal ──────────────────────────────

    /**
     * All bars have identical volume (100_000).
     * The volume condition requires {@code currentVolume > averageVolume};
     * when all bars are equal, current equals the average — not greater.
     * Therefore the volume condition always fails → HOLD.
     *
     * <p>This trigger is independent of price action, making it the most
     * reliable and deterministic HOLD test.</p>
     */
    @Test
    void evaluate_returnsHold_whenVolumeConditionFails() {
        List<Candle> candles = buildBullishCandlesWithFlatVolume();

        StrategyDecision decision = strategy.evaluate(candles);

        assertThat(decision.signal()).isEqualTo(TradingSignal.HOLD);
        assertThat(decision.reason()).containsIgnoringCase("HOLD");
    }

    // ──────────────────────────── guard rails ──────────────────────────────

    @Test
    void evaluate_throwsIllegalArgument_whenTooFewCandles() {
        int minBars = strategy.minimumBars();
        List<Candle> tooFew = buildFlatCandles(minBars - 1, 100.0, 100_000L);

        assertThatThrownBy(() -> strategy.evaluate(tooFew))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GAINZ_ALPHA_V2");
    }

    @Test
    void evaluate_throwsIllegalArgument_whenCandlesIsNull() {
        assertThatThrownBy(() -> strategy.evaluate(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decision_hasNonNullTimestamp() {
        // Flat volume → guaranteed HOLD; just need enough bars
        List<Candle> candles = buildFlatCandles(strategy.minimumBars() + 5, 100.0, 100_000L);

        StrategyDecision d = strategy.evaluate(candles);

        assertThat(d.evaluatedAt())
                .isNotNull()
                .isBeforeOrEqualTo(Instant.now());
    }

    // ──────────────────────────── registry ─────────────────────────────────

    @Test
    void strategyRegistry_resolvesGainzAlphaV2ByName() {
        GainzAlphaV2Strategy gainz = new GainzAlphaV2Strategy(new BarSeriesFactory());
        StrategyRegistry registry = new StrategyRegistry(List.of(gainz));

        TechnicalStrategy resolved = registry.get("GAINZ_ALPHA_V2");

        assertThat(resolved).isNotNull();
        assertThat(resolved.name()).isEqualTo("GAINZ_ALPHA_V2");
    }

    @Test
    void strategyRegistry_listNamesContainsAllThreeStrategies() {
        BarSeriesFactory factory = new BarSeriesFactory();
        StrategyRegistry registry = new StrategyRegistry(List.of(
                new SmaCrossoverStrategy(factory, 3, 5),
                new RsiMeanReversionStrategy(factory, 5, 30, 70),
                new GainzAlphaV2Strategy(factory)
        ));

        assertThat(registry.listNames())
                .containsExactlyInAnyOrder(
                        "SMA_CROSSOVER", "RSI_MEAN_REVERSION", "GAINZ_ALPHA_V2");
    }

    // ──────────────────────────── candle builders ───────────────────────────

    /**
     * 40-bar gentle uptrend (drift +0.5/bar, ±3 zigzag noise)
     * followed by 9 bars of +3/bar acceleration and 1 final volume-spike bar.
     *
     * <p>The acceleration phase drives SMA5 above SMA10, pushes RSI high,
     * and creates MACD/signal divergence.  The final spike bar satisfies the
     * volume condition.</p>
     */
    private List<Candle> buildBullishCandles() {
        List<Candle> list = new ArrayList<>();
        Instant base = Instant.parse("2024-01-01T00:00:00Z");

        // Phase 1 – gentle uptrend with noise (40 bars)
        for (int i = 0; i < 40; i++) {
            double p = 100 + i * 0.5 + (i % 3 == 0 ? -3 : 3);
            list.add(candle(base, i, Math.max(1, p), 100_000L));
        }
        // Phase 2 – acceleration (9 bars, regular volume)
        for (int i = 0; i < 9; i++) {
            double p = list.get(list.size() - 1).close() + 3;
            list.add(candle(base, 40 + i, p, 100_000L));
        }
        // Phase 3 – last bar: volume spike triggers volume condition
        double lastP = list.get(list.size() - 1).close() + 3;
        list.add(candle(base, 49, lastP, 500_000L));

        return list;   // 50 bars total
    }

    /**
     * 40-bar gentle downtrend (drift -0.5/bar, ±3 zigzag noise)
     * followed by 9 bars of -3/bar crash and 1 final volume-spike bar.
     */
    private List<Candle> buildBearishCandles() {
        List<Candle> list = new ArrayList<>();
        Instant base = Instant.parse("2024-01-01T00:00:00Z");

        // Phase 1 – gentle downtrend with noise
        for (int i = 0; i < 40; i++) {
            double p = 200 - i * 0.5 + (i % 3 == 0 ? 3 : -3);
            list.add(candle(base, i, Math.max(1, p), 100_000L));
        }
        // Phase 2 – crash (regular volume)
        for (int i = 0; i < 9; i++) {
            double p = Math.max(1, list.get(list.size() - 1).close() - 3);
            list.add(candle(base, 40 + i, p, 100_000L));
        }
        // Phase 3 – volume spike on last bar
        double lastP = Math.max(1, list.get(list.size() - 1).close() - 3);
        list.add(candle(base, 49, lastP, 500_000L));

        return list;   // 50 bars total
    }

    /**
     * Bullish price action (same as {@link #buildBullishCandles()}) but with
     * a constant volume of 100_000 on every bar, including the last.
     *
     * <p>Because all volumes are equal, {@code currentVolume == averageVolume}
     * (not strictly greater), so the volume condition fails → HOLD.</p>
     */
    private List<Candle> buildBullishCandlesWithFlatVolume() {
        List<Candle> list = new ArrayList<>();
        Instant base = Instant.parse("2024-01-01T00:00:00Z");

        for (int i = 0; i < 40; i++) {
            double p = 100 + i * 0.5 + (i % 3 == 0 ? -3 : 3);
            list.add(candle(base, i, Math.max(1, p), 100_000L));
        }
        for (int i = 0; i < 10; i++) {
            double p = list.get(list.size() - 1).close() + 3;
            list.add(candle(base, 40 + i, p, 100_000L));  // ← same vol, no spike
        }

        return list;   // 50 bars, all same volume
    }

    /** Flat price + constant volume – neutral baseline for guard-rail tests. */
    private List<Candle> buildFlatCandles(int count, double price, long volume) {
        List<Candle> list = new ArrayList<>(count);
        Instant base = Instant.parse("2024-01-01T00:00:00Z");
        for (int i = 0; i < count; i++) {
            list.add(candle(base, i, price, volume));
        }
        return list;
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private Candle candle(Instant base, int dayOffset, double price, long volume) {
        double spread = Math.max(0.5, price * 0.003);
        return new Candle(
                base.plus(dayOffset, ChronoUnit.DAYS),
                price,
                price + spread,
                price - spread,
                price,
                volume
        );
    }
}