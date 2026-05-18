package com.algotrading.app.market;

import com.algotrading.app.model.Candle;
import com.algotrading.app.model.HistoricalDataRequest;

import java.util.List;

/**
 * Hexagonal port – primary inbound abstraction for fetching market data.
 * <p>
 * No Zerodha SDK types cross this boundary.
 * Implementations (adapters) live in the same package.
 * </p>
 */
public interface MarketDataPort {

    /**
     * Fetch OHLCV candles for the given request parameters.
     *
     * @param request the query: token, date range, interval
     * @return ordered list of candles, oldest first; never {@code null}
     * @throws com.algotrading.app.exception.MarketDataException on any data-source failure
     */
    List<Candle> fetchCandles(HistoricalDataRequest request);
}