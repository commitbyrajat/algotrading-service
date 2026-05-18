package com.algotrading.app.strategy;

import com.algotrading.app.model.Candle;
import com.algotrading.app.model.StrategyDecision;
import com.algotrading.app.model.TradingSignal;
import com.algotrading.app.util.BarSeriesFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;

import java.util.List;

/**
 * SMA Golden/Death Cross Strategy.
 *
 * <h3>Signal logic (evaluated at the last two bars)</h3>
 * <ul>
 *   <li><b>BUY</b>  – fast SMA crosses above slow SMA on the final bar</li>
 *   <li><b>SELL</b> – fast SMA crosses below slow SMA on the final bar</li>
 *   <li><b>HOLD</b> – no crossover on the final two bars</li>
 * </ul>
 *
 * <h3>Two-constructor design</h3>
 * Spring Framework 7 requires an unambiguous injection point when multiple
 * constructors are present. {@code @Autowired} marks the Spring-managed
 * constructor; the configurable constructor is left unannotated for direct
 * use in unit tests without a Spring context.
 */
@Component
public class SmaCrossoverStrategy implements TechnicalStrategy {

    static final String STRATEGY_NAME = "SMA_CROSSOVER";

    private final BarSeriesFactory barSeriesFactory;
    private final int fastPeriod;
    private final int slowPeriod;

    /**
     * Spring-managed constructor – production defaults: fast=10, slow=30.
     *
     * <p>{@code @Autowired} is required in Spring Framework 7 when the class
     * declares more than one constructor, to tell the container which one to use
     * for dependency injection.</p>
     */
    @Autowired
    public SmaCrossoverStrategy(BarSeriesFactory barSeriesFactory) {
        this(barSeriesFactory, 10, 30);
    }

    /**
     * Configurable constructor for direct instantiation in unit tests
     * (no Spring context needed).
     *
     * @param barSeriesFactory factory for ta4j BarSeries construction
     * @param fastPeriod       number of bars for the fast SMA (must be &lt; slowPeriod)
     * @param slowPeriod       number of bars for the slow SMA
     */
    public SmaCrossoverStrategy(BarSeriesFactory barSeriesFactory,
                                int fastPeriod,
                                int slowPeriod) {
        if (fastPeriod <= 0) {
            throw new IllegalArgumentException("fastPeriod must be > 0, got: " + fastPeriod);
        }
        if (fastPeriod >= slowPeriod) {
            throw new IllegalArgumentException(
                    "fastPeriod (" + fastPeriod + ") must be < slowPeriod (" + slowPeriod + ")");
        }
        this.barSeriesFactory = barSeriesFactory;
        this.fastPeriod = fastPeriod;
        this.slowPeriod = slowPeriod;
    }

    @Override
    public String name() {
        return STRATEGY_NAME;
    }

    @Override
    public StrategyDecision evaluate(List<Candle> candles) {
        int minBars = slowPeriod + 1;
        if (candles == null || candles.size() < minBars) {
            throw new IllegalArgumentException(
                    STRATEGY_NAME + " requires at least " + minBars + " candles, got "
                            + (candles == null ? 0 : candles.size()));
        }

        BarSeries series = barSeriesFactory.create(STRATEGY_NAME, candles);
        ClosePriceIndicator close = new ClosePriceIndicator(series);

        // ta4j 0.22.6: SMAIndicator lives in org.ta4j.core.indicators.averages
        SMAIndicator fastSma = new SMAIndicator(close, fastPeriod);
        SMAIndicator slowSma = new SMAIndicator(close, slowPeriod);

        int last = series.getEndIndex();
        int prev = last - 1;

        Num fastLast = fastSma.getValue(last);
        Num slowLast = slowSma.getValue(last);
        Num fastPrev = fastSma.getValue(prev);
        Num slowPrev = slowSma.getValue(prev);

        // Golden cross: fast crossed above slow on exactly the last bar
        if (fastPrev.isLessThanOrEqual(slowPrev) && fastLast.isGreaterThan(slowLast)) {
            return StrategyDecision.of(STRATEGY_NAME, TradingSignal.BUY,
                    String.format("Golden cross: SMA(%d)=%.4f crossed above SMA(%d)=%.4f",
                            fastPeriod, fastLast.doubleValue(),
                            slowPeriod, slowLast.doubleValue()));
        }

        // Death cross: fast crossed below slow on exactly the last bar
        if (fastPrev.isGreaterThanOrEqual(slowPrev) && fastLast.isLessThan(slowLast)) {
            return StrategyDecision.of(STRATEGY_NAME, TradingSignal.SELL,
                    String.format("Death cross: SMA(%d)=%.4f crossed below SMA(%d)=%.4f",
                            fastPeriod, fastLast.doubleValue(),
                            slowPeriod, slowLast.doubleValue()));
        }

        return StrategyDecision.of(STRATEGY_NAME, TradingSignal.HOLD,
                String.format("No crossover – SMA(%d)=%.4f, SMA(%d)=%.4f",
                        fastPeriod, fastLast.doubleValue(),
                        slowPeriod, slowLast.doubleValue()));
    }
}