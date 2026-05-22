package com.algotrading.app.strategy;

import com.algotrading.app.config.PositionSizingProperties;
import com.algotrading.app.model.Candle;
import com.algotrading.app.model.QuantitySuggestion;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ATR-based position sizing for BUY decisions.
 *
 * <p>Quantity is derived from risk amount divided by ATR-based stop distance:
 * {@code quantity = riskAmount / (ATR * multiplier)}. A max portfolio exposure
 * cap is then applied using the latest close as the price proxy.</p>
 */
@Component
public class AtrPositionSizingStrategy implements PositionSizingStrategy {

    private static final String METHOD = "ATR_RISK";

    private final double capital;
    private final double riskPercent;
    private final int atrPeriod;
    private final double atrMultiplier;
    private final double maxPortfolioExposurePercent;

    public AtrPositionSizingStrategy(PositionSizingProperties properties) {
        this.capital = properties.capital();
        this.riskPercent = properties.riskPercent();
        this.atrPeriod = properties.atrPeriod();
        this.atrMultiplier = properties.atrMultiplier();
        this.maxPortfolioExposurePercent = properties.maxPortfolioExposurePercent();
    }

    @Override
    public QuantitySuggestion suggestBuyQuantity(List<Candle> candles) {
        if (candles == null || candles.isEmpty()) {
            return zeroSuggestion(0, 0, 0, "No candles available; quantity cannot be sized.");
        }

        Candle latest = candles.getLast();
        double currentPrice = latest.close();
        double riskAmount = capital * riskPercent;
        if (candles.size() < atrPeriod + 1) {
            return zeroSuggestion(
                    currentPrice,
                    0,
                    riskAmount,
                    String.format("Need at least %d candles for ATR(%d); got %d.",
                            atrPeriod + 1,
                            atrPeriod,
                            candles.size())
            );
        }

        double atr = averageTrueRange(candles, atrPeriod);
        double riskPerShare = atr * atrMultiplier;
        if (atr <= 0 || riskPerShare <= 0 || currentPrice <= 0) {
            return zeroSuggestion(
                    currentPrice,
                    atr,
                    riskAmount,
                    String.format("ATR %.2f or current price %.2f is not usable for position sizing.",
                            atr,
                            currentPrice)
            );
        }

        int riskBasedQuantity = (int) Math.floor(riskAmount / riskPerShare);
        int maxExposureQuantity = (int) Math.floor((capital * maxPortfolioExposurePercent) / currentPrice);
        int suggestedQuantity = Math.max(0, Math.min(riskBasedQuantity, maxExposureQuantity));

        String explanation = String.format(
                "ATR-based sizing: capital %.2f, risk %.2f%% = %.2f risk amount; "
                        + "ATR(%d)=%.2f with %.2fx stop gives %.2f risk/share; "
                        + "risk qty=%d, max %.2f%% exposure qty=%d, suggested qty=%d.",
                capital,
                riskPercent * 100,
                riskAmount,
                atrPeriod,
                atr,
                atrMultiplier,
                riskPerShare,
                riskBasedQuantity,
                maxPortfolioExposurePercent * 100,
                maxExposureQuantity,
                suggestedQuantity
        );

        return new QuantitySuggestion(
                suggestedQuantity,
                METHOD,
                round(currentPrice),
                round(atr),
                atrMultiplier,
                round(riskPerShare),
                round(capital),
                riskPercent,
                round(riskAmount),
                riskBasedQuantity,
                maxExposureQuantity,
                explanation
        );
    }

    private QuantitySuggestion zeroSuggestion(double currentPrice,
                                              double atr,
                                              double riskAmount,
                                              String explanation) {
        return new QuantitySuggestion(
                0,
                METHOD,
                round(currentPrice),
                round(atr),
                atrMultiplier,
                round(atr * atrMultiplier),
                round(capital),
                riskPercent,
                round(riskAmount),
                0,
                currentPrice > 0
                        ? (int) Math.floor((capital * maxPortfolioExposurePercent) / currentPrice)
                        : 0,
                explanation
        );
    }

    private static double averageTrueRange(List<Candle> candles, int period) {
        int fromIndex = candles.size() - period;
        double sum = 0;
        for (int i = fromIndex; i < candles.size(); i++) {
            Candle candle = candles.get(i);
            Candle previous = candles.get(i - 1);
            double highLow = candle.high() - candle.low();
            double highPreviousClose = Math.abs(candle.high() - previous.close());
            double lowPreviousClose = Math.abs(candle.low() - previous.close());
            sum += Math.max(highLow, Math.max(highPreviousClose, lowPreviousClose));
        }
        return sum / period;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
