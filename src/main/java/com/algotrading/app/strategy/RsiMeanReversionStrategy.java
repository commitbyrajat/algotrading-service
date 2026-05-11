package com.algotrading.app.strategy;

import com.algotrading.app.model.Candle;
import com.algotrading.app.model.StrategyDecision;
import com.algotrading.app.model.TradingSignal;
import com.algotrading.app.util.BarSeriesFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;

import java.util.List;

/**
 * RSI Mean-Reversion Strategy.
 *
 * <h3>Signal logic (evaluated on the last bar)</h3>
 * <ul>
 *   <li><b>BUY</b>  – RSI &lt; oversold threshold (default 30)</li>
 *   <li><b>SELL</b> – RSI &gt; overbought threshold (default 70)</li>
 *   <li><b>HOLD</b> – RSI in neutral band</li>
 * </ul>
 *
 * <h3>Two-constructor design</h3>
 * Spring Framework 7 requires an unambiguous injection point when multiple
 * constructors are present. {@code @Autowired} marks the Spring-managed
 * constructor; the configurable constructor is left unannotated for direct
 * use in unit tests without a Spring context.
 */
@Component
public class RsiMeanReversionStrategy implements TechnicalStrategy {

    static final String STRATEGY_NAME = "RSI_MEAN_REVERSION";

    private final BarSeriesFactory barSeriesFactory;
    private final int rsiPeriod;
    private final double oversoldThreshold;
    private final double overboughtThreshold;

    /**
     * Spring-managed constructor – production defaults: RSI(14), 30/70 thresholds.
     *
     * <p>{@code @Autowired} is required in Spring Framework 7 when the class
     * declares more than one constructor, to tell the container which one to use
     * for dependency injection.</p>
     */
    @Autowired
    public RsiMeanReversionStrategy(BarSeriesFactory barSeriesFactory) {
        this(barSeriesFactory, 14, 30.0, 70.0);
    }

    /**
     * Configurable constructor for direct instantiation in unit tests
     * (no Spring context needed).
     *
     * @param barSeriesFactory    factory for ta4j BarSeries
     * @param rsiPeriod           lookback period (must be ≥ 2)
     * @param oversoldThreshold   RSI level below which BUY is signalled
     * @param overboughtThreshold RSI level above which SELL is signalled
     */
    public RsiMeanReversionStrategy(BarSeriesFactory barSeriesFactory,
                                    int rsiPeriod,
                                    double oversoldThreshold,
                                    double overboughtThreshold) {
        if (rsiPeriod < 2) {
            throw new IllegalArgumentException("rsiPeriod must be >= 2, got: " + rsiPeriod);
        }
        if (oversoldThreshold <= 0 || oversoldThreshold >= 100) {
            throw new IllegalArgumentException(
                    "oversoldThreshold must be in (0,100), got: " + oversoldThreshold);
        }
        if (overboughtThreshold <= 0 || overboughtThreshold >= 100) {
            throw new IllegalArgumentException(
                    "overboughtThreshold must be in (0,100), got: " + overboughtThreshold);
        }
        if (oversoldThreshold >= overboughtThreshold) {
            throw new IllegalArgumentException(
                    "oversoldThreshold (" + oversoldThreshold
                            + ") must be < overboughtThreshold (" + overboughtThreshold + ")");
        }
        this.barSeriesFactory    = barSeriesFactory;
        this.rsiPeriod           = rsiPeriod;
        this.oversoldThreshold   = oversoldThreshold;
        this.overboughtThreshold = overboughtThreshold;
    }

    @Override
    public String name() {
        return STRATEGY_NAME;
    }

    @Override
    public StrategyDecision evaluate(List<Candle> candles) {
        int minBars = rsiPeriod + 1;
        if (candles == null || candles.size() < minBars) {
            throw new IllegalArgumentException(
                    STRATEGY_NAME + " requires at least " + minBars + " candles, got "
                            + (candles == null ? 0 : candles.size()));
        }

        BarSeries series = barSeriesFactory.create(STRATEGY_NAME, candles);
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        RSIIndicator rsi = new RSIIndicator(close, rsiPeriod);

        int lastIndex   = series.getEndIndex();
        Num rsiValue    = rsi.getValue(lastIndex);
        double rsiDouble = rsiValue.doubleValue();

        // ta4j 0.22.6: RSIIndicator returns NaN during the unstable warm-up period
        if (Double.isNaN(rsiDouble)) {
            return StrategyDecision.of(STRATEGY_NAME, TradingSignal.HOLD,
                    "RSI value is NaN (still in warm-up period) – insufficient stable data");
        }

        if (rsiDouble < oversoldThreshold) {
            return StrategyDecision.of(STRATEGY_NAME, TradingSignal.BUY,
                    String.format("RSI(%.2f) < oversold(%.2f) – mean reversion long signal",
                            rsiDouble, oversoldThreshold));
        }

        if (rsiDouble > overboughtThreshold) {
            return StrategyDecision.of(STRATEGY_NAME, TradingSignal.SELL,
                    String.format("RSI(%.2f) > overbought(%.2f) – mean reversion short signal",
                            rsiDouble, overboughtThreshold));
        }

        return StrategyDecision.of(STRATEGY_NAME, TradingSignal.HOLD,
                String.format("RSI(%.2f) is neutral [%.2f, %.2f]",
                        rsiDouble, oversoldThreshold, overboughtThreshold));
    }
}