package com.algotrading.app.strategy;

import com.algotrading.app.config.PositionSizingProperties;
import com.algotrading.app.model.Candle;
import com.algotrading.app.model.QuantitySuggestion;
import com.algotrading.app.util.CandleTestFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AtrPositionSizingStrategyTest {

    @Test
    void suggestBuyQuantity_usesAtrRiskAndExposureCap() {
        AtrPositionSizingStrategy strategy = new AtrPositionSizingStrategy(
                defaultProperties()
        );
        List<Candle> candles = CandleTestFactory.uptrend(20, 100.0, 1.0);

        QuantitySuggestion suggestion = strategy.suggestBuyQuantity(candles);

        assertThat(suggestion.method()).isEqualTo("ATR_RISK");
        assertThat(suggestion.atr()).isEqualTo(3.0);
        assertThat(suggestion.riskAmount()).isEqualTo(5_000.0);
        assertThat(suggestion.riskPerShare()).isEqualTo(4.5);
        assertThat(suggestion.riskBasedQuantity()).isEqualTo(1111);
        assertThat(suggestion.maxExposureQuantity()).isEqualTo(840);
        assertThat(suggestion.suggestedQuantity()).isEqualTo(840);
        assertThat(suggestion.explanation()).contains("ATR-based sizing");
    }

    @Test
    void suggestBuyQuantity_returnsZero_whenCandlesAreInsufficientForAtr() {
        AtrPositionSizingStrategy strategy = new AtrPositionSizingStrategy(
                defaultProperties()
        );

        QuantitySuggestion suggestion = strategy.suggestBuyQuantity(CandleTestFactory.flat(5, 100.0));

        assertThat(suggestion.suggestedQuantity()).isZero();
        assertThat(suggestion.explanation()).contains("Need at least 15 candles");
    }

    private PositionSizingProperties defaultProperties() {
        return new PositionSizingProperties(
                500_000,
                0.01,
                14,
                1.5,
                0.20
        );
    }
}
