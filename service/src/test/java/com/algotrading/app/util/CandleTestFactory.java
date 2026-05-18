package com.algotrading.app.util;

import com.algotrading.app.model.Candle;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Test utility: deterministic candle list builders for unit tests.
 * Not a Spring bean – instantiated directly in tests.
 */
public final class CandleTestFactory {

    private static final Instant BASE = Instant.parse("2024-01-01T00:00:00Z");

    private CandleTestFactory() {}

    /**
     * All bars at the same price (neutral market – no crossover, RSI ≈ 50).
     */
    public static List<Candle> flat(int count, double price) {
        List<Candle> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double spread = price * 0.002;
            list.add(new Candle(
                    BASE.plus(i, ChronoUnit.DAYS),
                    price,
                    price + spread,
                    price - spread,
                    price,
                    100_000L
            ));
        }
        return list;
    }

    /**
     * Linearly rising prices; fast SMA will eventually cross above slow SMA → BUY.
     *
     * @param count      total bar count
     * @param startPrice price at bar 0
     * @param increment  per-bar price increase
     */
    public static List<Candle> uptrend(int count, double startPrice, double increment) {
        List<Candle> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double p = startPrice + (i * increment);
            list.add(new Candle(
                    BASE.plus(i, ChronoUnit.DAYS),
                    p,
                    p + 2.0,
                    p - 1.0,
                    p,
                    200_000L
            ));
        }
        return list;
    }

    /**
     * Linearly falling prices; fast SMA will eventually cross below slow SMA → SELL.
     * RSI during a sharp downtrend will go below 30 → BUY for mean reversion.
     *
     * @param count      total bar count
     * @param startPrice price at bar 0
     * @param decrement  per-bar absolute price decrease
     */
    public static List<Candle> downtrend(int count, double startPrice, double decrement) {
        List<Candle> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double p = Math.max(1.0, startPrice - (i * decrement));
            list.add(new Candle(
                    BASE.plus(i, ChronoUnit.DAYS),
                    p,
                    p + 1.0,
                    p - 0.5,
                    p,
                    150_000L
            ));
        }
        return list;
    }

    /**
     * Build candles from an explicit array of close prices.
     * OHLV are derived automatically using a ±0.5 % spread around each close.
     *
     * @param closes one close price per bar, left-to-right (oldest first)
     */
    public static List<Candle> fromCloses(double... closes) {
        List<Candle> list = new ArrayList<>(closes.length);
        for (int i = 0; i < closes.length; i++) {
            double c      = closes[i];
            double spread = Math.max(0.5, c * 0.005);   // at least 0.5 absolute
            list.add(new Candle(
                    BASE.plus(i, ChronoUnit.DAYS),
                    c,              // open ≈ close
                    c + spread,     // high
                    c - spread,     // low
                    c,              // close
                    100_000L
            ));
        }
        return list;
    }
}