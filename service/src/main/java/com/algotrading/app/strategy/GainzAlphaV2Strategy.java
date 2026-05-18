package com.algotrading.app.strategy;

import com.algotrading.app.model.Candle;
import com.algotrading.app.model.StrategyDecision;
import com.algotrading.app.model.TradingSignal;
import com.algotrading.app.util.BarSeriesFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;

import java.util.List;

/**
 * GAINZ_ALPHA_V2 – Multi-indicator confluence strategy.
 *
 * <h3>Entry conditions – all four must fire simultaneously</h3>
 *
 * <table border="1">
 *   <tr><th>Condition</th><th>BUY</th><th>SELL</th></tr>
 *   <tr><td>SMA trend</td>
 *       <td>SMA(fast) &gt; SMA(slow)</td>
 *       <td>SMA(fast) &lt; SMA(slow)</td></tr>
 *   <tr><td>RSI momentum</td>
 *       <td>RSI &gt; rsiBuyMin (default 40) – bullish bias confirmed</td>
 *       <td>RSI &lt; rsiSellMax (default 60) – bearish bias confirmed</td></tr>
 *   <tr><td>MACD</td>
 *       <td>MACD line &gt; signal line</td>
 *       <td>MACD line &lt; signal line</td></tr>
 *   <tr><td>Volume</td>
 *       <td>current bar volume &gt; SMA(volume, volPeriod)</td>
 *       <td>current bar volume &gt; SMA(volume, volPeriod)</td></tr>
 * </table>
 *
 * <h3>Why single-sided RSI bounds</h3>
 * <p>Earlier versions used a double-sided RSI band (e.g. 40 &lt; RSI &lt; 80).
 * In a genuine trending market RSI regularly exceeds 80 (uptrend) or drops
 * below 20 (downtrend).  Capping RSI at 80 would reject exactly the strongest
 * trend moves, which is the opposite of what a trend-following strategy wants.
 * The single-sided check simply confirms that momentum is aligned with the
 * trend direction:</p>
 * <ul>
 *   <li>BUY:  RSI &gt; 40 – momentum is bullish (not bearish)</li>
 *   <li>SELL: RSI &lt; 60 – momentum is bearish (not bullish)</li>
 * </ul>
 *
 * <h3>Minimum bars</h3>
 * {@code max(slowSma+1, rsiPeriod+1, macdLong+macdSignal, volPeriod)}
 * = 35 with default parameters.
 *
 * <h3>ta4j 0.22.6 packages</h3>
 * <ul>
 *   <li>{@code org.ta4j.core.indicators.MACDIndicator}</li>
 *   <li>{@code org.ta4j.core.indicators.averages.EMAIndicator}</li>
 *   <li>{@code org.ta4j.core.indicators.averages.SMAIndicator}</li>
 *   <li>{@code org.ta4j.core.indicators.RSIIndicator}</li>
 *   <li>{@code org.ta4j.core.indicators.helpers.ClosePriceIndicator}</li>
 * </ul>
 */
@Component
public class GainzAlphaV2Strategy implements TechnicalStrategy {

    public static final String STRATEGY_NAME = "GAINZ_ALPHA_V2";

    // ── parameters ────────────────────────────────────────────────────────────
    private final BarSeriesFactory barSeriesFactory;
    private final int    smaFastPeriod;
    private final int    smaSlowPeriod;
    private final int    rsiPeriod;
    /** RSI must be ABOVE this value for a BUY signal (momentum is bullish). */
    private final double rsiBuyMin;
    /** RSI must be BELOW this value for a SELL signal (momentum is bearish). */
    private final double rsiSellMax;
    private final int    macdShort;
    private final int    macdLong;
    private final int    macdSignal;
    private final int    volPeriod;

    // ── Spring-managed constructor ────────────────────────────────────────────

    /**
     * Production defaults: SMA(10/30), RSI(14, buy&gt;40, sell&lt;60),
     * MACD(12,26,9), volume SMA(20).
     *
     * <p>{@code @Autowired} is required: Spring Framework 7 requires an
     * explicit injection point when multiple constructors exist.</p>
     */
    @Autowired
    public GainzAlphaV2Strategy(BarSeriesFactory barSeriesFactory) {
        this(barSeriesFactory,
                10, 30,         // SMA fast / slow
                14,             // RSI period
                40.0,           // RSI buy minimum  (RSI > 40 = bullish)
                60.0,           // RSI sell maximum (RSI < 60 = bearish)
                12, 26, 9,      // MACD short / long / signal
                20);            // volume lookback
    }

    // ── Configurable constructor (tests + custom wiring) ─────────────────────

    /**
     * All-parameter constructor for direct instantiation in tests.
     *
     * @param barSeriesFactory ta4j bar series factory
     * @param smaFastPeriod    fast SMA period (must be &lt; smaSlowPeriod)
     * @param smaSlowPeriod    slow SMA period
     * @param rsiPeriod        RSI lookback
     * @param rsiBuyMin        RSI threshold above which a BUY is considered
     * @param rsiSellMax       RSI threshold below which a SELL is considered
     * @param macdShort        MACD fast EMA period
     * @param macdLong         MACD slow EMA period
     * @param macdSignal       MACD signal EMA period
     * @param volPeriod        volume average lookback
     */
    public GainzAlphaV2Strategy(BarSeriesFactory barSeriesFactory,
                                int    smaFastPeriod,
                                int    smaSlowPeriod,
                                int    rsiPeriod,
                                double rsiBuyMin,
                                double rsiSellMax,
                                int    macdShort,
                                int    macdLong,
                                int    macdSignal,
                                int    volPeriod) {
        if (smaFastPeriod >= smaSlowPeriod)
            throw new IllegalArgumentException("smaFastPeriod must be < smaSlowPeriod");
        if (rsiPeriod < 2)
            throw new IllegalArgumentException("rsiPeriod must be >= 2");
        if (rsiBuyMin < 0 || rsiBuyMin >= 100)
            throw new IllegalArgumentException("rsiBuyMin must be in [0,100)");
        if (rsiSellMax <= 0 || rsiSellMax > 100)
            throw new IllegalArgumentException("rsiSellMax must be in (0,100]");
        if (macdShort >= macdLong)
            throw new IllegalArgumentException("macdShort must be < macdLong");

        this.barSeriesFactory = barSeriesFactory;
        this.smaFastPeriod   = smaFastPeriod;
        this.smaSlowPeriod   = smaSlowPeriod;
        this.rsiPeriod       = rsiPeriod;
        this.rsiBuyMin       = rsiBuyMin;
        this.rsiSellMax      = rsiSellMax;
        this.macdShort       = macdShort;
        this.macdLong        = macdLong;
        this.macdSignal      = macdSignal;
        this.volPeriod       = volPeriod;
    }

    @Override
    public String name() {
        return STRATEGY_NAME;
    }

    /** Exposed for test assertions. */
    int minimumBars() {
        return Math.max(
                Math.max(smaSlowPeriod + 1, rsiPeriod + 1),
                Math.max(macdLong + macdSignal, volPeriod)
        );
    }

    // ── evaluation ────────────────────────────────────────────────────────────

    @Override
    public StrategyDecision evaluate(List<Candle> candles) {
        int minBars = minimumBars();
        if (candles == null || candles.size() < minBars) {
            throw new IllegalArgumentException(
                    STRATEGY_NAME + " requires at least " + minBars + " candles, got "
                            + (candles == null ? 0 : candles.size()));
        }

        BarSeries series = barSeriesFactory.create(STRATEGY_NAME, candles);
        int last = series.getEndIndex();

        // ── 1. SMA trend ──────────────────────────────────────────────────────
        ClosePriceIndicator close   = new ClosePriceIndicator(series);
        SMAIndicator        fastSma = new SMAIndicator(close, smaFastPeriod);
        SMAIndicator        slowSma = new SMAIndicator(close, smaSlowPeriod);
        Num fastVal = fastSma.getValue(last);
        Num slowVal = slowSma.getValue(last);
        boolean uptrend   = fastVal.isGreaterThan(slowVal);
        boolean downtrend = fastVal.isLessThan(slowVal);

        // ── 2. RSI momentum ───────────────────────────────────────────────────
        RSIIndicator rsi    = new RSIIndicator(close, rsiPeriod);
        double       rsiVal = rsi.getValue(last).doubleValue();
        // Single-sided: BUY needs RSI above the bullish floor;
        //               SELL needs RSI below the bearish ceiling.
        boolean rsiBullish = !Double.isNaN(rsiVal) && rsiVal > rsiBuyMin;
        boolean rsiBearish = !Double.isNaN(rsiVal) && rsiVal < rsiSellMax;

        // ── 3. MACD confirmation ─────────────────────────────────────────────
        MACDIndicator macd    = new MACDIndicator(close, macdShort, macdLong);
        EMAIndicator  signal  = new EMAIndicator(macd, macdSignal);
        Num macdVal   = macd.getValue(last);
        Num signalVal = signal.getValue(last);
        boolean macdBullish = macdVal.isGreaterThan(signalVal);
        boolean macdBearish = macdVal.isLessThan(signalVal);

        // ── 4. Volume confirmation ────────────────────────────────────────────
        Num    currentVol = series.getBar(last).getVolume();
        double avgVol     = averageVolume(series, last, volPeriod);
        boolean aboveAvgVol = currentVol.doubleValue() > avgVol;

        // ── Signal decision ───────────────────────────────────────────────────
        if (uptrend && rsiBullish && macdBullish && aboveAvgVol) {
            return StrategyDecision.of(STRATEGY_NAME, TradingSignal.BUY,
                    String.format(
                            "BUY confluence: SMA(%d/%.2f)>SMA(%d/%.2f), "
                                    + "RSI(%.2f)>%.0f, "
                                    + "MACD(%.4f)>Signal(%.4f), "
                                    + "Vol(%.0f)>AvgVol(%.0f)",
                            smaFastPeriod, fastVal.doubleValue(),
                            smaSlowPeriod, slowVal.doubleValue(),
                            rsiVal, rsiBuyMin,
                            macdVal.doubleValue(), signalVal.doubleValue(),
                            currentVol.doubleValue(), avgVol));
        }

        if (downtrend && rsiBearish && macdBearish && aboveAvgVol) {
            return StrategyDecision.of(STRATEGY_NAME, TradingSignal.SELL,
                    String.format(
                            "SELL confluence: SMA(%d/%.2f)<SMA(%d/%.2f), "
                                    + "RSI(%.2f)<%.0f, "
                                    + "MACD(%.4f)<Signal(%.4f), "
                                    + "Vol(%.0f)>AvgVol(%.0f)",
                            smaFastPeriod, fastVal.doubleValue(),
                            smaSlowPeriod, slowVal.doubleValue(),
                            rsiVal, rsiSellMax,
                            macdVal.doubleValue(), signalVal.doubleValue(),
                            currentVol.doubleValue(), avgVol));
        }

        return StrategyDecision.of(STRATEGY_NAME, TradingSignal.HOLD,
                buildHoldReason(uptrend, downtrend,
                        rsiBullish, rsiBearish,
                        macdBullish, macdBearish,
                        aboveAvgVol, rsiVal));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private double averageVolume(BarSeries series, int endIndex, int period) {
        int start = Math.max(0, endIndex - period + 1);
        double sum = 0;
        int count  = 0;
        for (int i = start; i <= endIndex; i++) {
            sum += series.getBar(i).getVolume().doubleValue();
            count++;
        }
        return count == 0 ? 0 : sum / count;
    }

    private String buildHoldReason(boolean uptrend, boolean downtrend,
                                   boolean rsiBullish, boolean rsiBearish,
                                   boolean macdBullish, boolean macdBearish,
                                   boolean aboveAvgVol, double rsiVal) {
        StringBuilder sb = new StringBuilder("HOLD – no confluence: ");
        if (!uptrend && !downtrend)        sb.append("SMA flat/equal; ");
        if (uptrend  && !rsiBullish)       sb.append(String.format("uptrend but RSI(%.2f) not > %.0f; ", rsiVal, rsiBuyMin));
        if (downtrend && !rsiBearish)      sb.append(String.format("downtrend but RSI(%.2f) not < %.0f; ", rsiVal, rsiSellMax));
        if (!macdBullish && !macdBearish)  sb.append("MACD neutral/equal; ");
        if (!aboveAvgVol)                  sb.append("volume not above average; ");
        sb.append(String.format("RSI=%.2f", rsiVal));
        return sb.toString();
    }
}