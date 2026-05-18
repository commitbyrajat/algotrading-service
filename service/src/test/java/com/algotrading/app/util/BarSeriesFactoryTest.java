package com.algotrading.app.util;

import com.algotrading.app.model.Candle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link BarSeriesFactory}.
 * Verifies correct ta4j 0.22.6 barBuilder() API usage.
 */
class BarSeriesFactoryTest {

    private BarSeriesFactory factory;

    @BeforeEach
    void setUp() {
        factory = new BarSeriesFactory();
    }

    @Test
    void create_setsCorrectBarCount() {
        List<Candle> candles = CandleTestFactory.flat(20, 500.0);
        BarSeries series = factory.create("test-series", candles);
        assertThat(series.getBarCount()).isEqualTo(20);
    }

    @Test
    void create_setsCorrectSeriesName() {
        List<Candle> candles = CandleTestFactory.flat(5, 100.0);
        BarSeries series = factory.create("my-series", candles);
        assertThat(series.getName()).isEqualTo("my-series");
    }

    @Test
    void create_setsCorrectClosePriceOnLastBar() {
        List<Candle> candles = CandleTestFactory.flat(10, 350.0);
        BarSeries series = factory.create("test", candles);
        double close = series.getBar(series.getEndIndex()).getClosePrice().doubleValue();
        assertThat(close).isEqualTo(350.0);
    }

    @Test
    void create_setsCorrectOpenPriceOnFirstBar() {
        List<Candle> candles = CandleTestFactory.flat(5, 200.0);
        BarSeries series = factory.create("test", candles);
        double open = series.getBar(0).getOpenPrice().doubleValue();
        assertThat(open).isEqualTo(200.0);
    }

    @Test
    void create_throwsIllegalArgument_whenCandlesAreEmpty() {
        assertThatThrownBy(() -> factory.create("empty", Collections.emptyList()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void create_throwsIllegalArgument_whenCandlesAreNull() {
        assertThatThrownBy(() -> factory.create("null", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_preservesCandleOrder_oldestFirst() {
        // fromCloses produces candles with strictly ascending timestamps
        List<Candle> candles = CandleTestFactory.fromCloses(100, 110, 120, 130, 140);
        BarSeries series = factory.create("ordered", candles);
        // Bar 0 should have close 100, Bar 4 should have close 140
        assertThat(series.getBar(0).getClosePrice().doubleValue()).isEqualTo(100.0);
        assertThat(series.getBar(4).getClosePrice().doubleValue()).isEqualTo(140.0);
    }
}