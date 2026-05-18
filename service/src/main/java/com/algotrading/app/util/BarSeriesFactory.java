package com.algotrading.app.util;

import com.algotrading.app.model.Candle;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;

import java.time.Duration;
import java.util.List;

/**
 * Converts a list of domain {@link Candle} objects into a ta4j {@link BarSeries}.
 *
 * <p>Uses the fluent {@code series.barBuilder()} API that is the recommended
 * approach in ta4j 0.22.x (no deprecated constructors).</p>
 */
@Component
public class BarSeriesFactory {

    /**
     * Build a {@link BarSeries} from the supplied candles.
     * Each candle becomes a 1-day bar; the end-time is the candle's timestamp.
     *
     * @param seriesName a human-readable label for the series
     * @param candles    ordered list of candles, oldest-first; must not be null or empty
     * @return a fully populated, ready-to-use {@link BarSeries}
     * @throws IllegalArgumentException if {@code candles} is null or empty
     */
    public BarSeries create(String seriesName, List<Candle> candles) {
        if (candles == null || candles.isEmpty()) {
            throw new IllegalArgumentException(
                    "candles must not be null or empty to build a BarSeries");
        }

        BarSeries series = new BaseBarSeriesBuilder()
                .withName(seriesName)
                .build();

        for (Candle candle : candles) {
            series.addBar(
                    series.barBuilder()
                            .timePeriod(Duration.ofDays(1))
                            .endTime(candle.timestamp())
                            .openPrice(candle.open())
                            .highPrice(candle.high())
                            .lowPrice(candle.low())
                            .closePrice(candle.close())
                            .volume(candle.volume())
                            .build()
            );
        }

        return series;
    }
}