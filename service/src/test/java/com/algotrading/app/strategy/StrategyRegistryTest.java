package com.algotrading.app.strategy;

import com.algotrading.app.exception.StrategyNotFoundException;
import com.algotrading.app.util.BarSeriesFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link StrategyRegistry}.
 */
class StrategyRegistryTest {

    private StrategyRegistry registry;

    @BeforeEach
    void setUp() {
        BarSeriesFactory factory = new BarSeriesFactory();
        // Use small periods so tests don't need large candle sets
        SmaCrossoverStrategy sma     = new SmaCrossoverStrategy(factory, 3, 5);
        RsiMeanReversionStrategy rsi = new RsiMeanReversionStrategy(factory, 5, 30, 70);

        registry = new StrategyRegistry(List.of(sma, rsi));
    }

    @Test
    void get_returnsSmaStrategy_byExactName() {
        TechnicalStrategy s = registry.get("SMA_CROSSOVER");
        assertThat(s).isNotNull();
        assertThat(s.name()).isEqualTo("SMA_CROSSOVER");
    }

    @Test
    void get_returnsRsiStrategy_byExactName() {
        TechnicalStrategy s = registry.get("RSI_MEAN_REVERSION");
        assertThat(s).isNotNull();
        assertThat(s.name()).isEqualTo("RSI_MEAN_REVERSION");
    }

    @Test
    void get_throwsStrategyNotFoundException_forUnknownName() {
        assertThatThrownBy(() -> registry.get("BOLLINGER_BANDS"))
                .isInstanceOf(StrategyNotFoundException.class)
                .hasMessageContaining("BOLLINGER_BANDS");
    }

    @Test
    void listNames_containsBothRegisteredStrategies() {
        List<String> names = registry.listNames();
        assertThat(names)
                .hasSize(2)
                .containsExactlyInAnyOrder("SMA_CROSSOVER", "RSI_MEAN_REVERSION");
    }

    @Test
    void listNames_returnsUnmodifiableList() {
        List<String> names = registry.listNames();
        assertThatThrownBy(() -> names.add("ILLEGAL"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}