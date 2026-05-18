package com.algotrading.app.market;

import com.algotrading.app.model.InstrumentResponse;

import java.util.List;
import java.util.Optional;

/**
 * Port for retrieving Kite's instrument master.
 */
public interface InstrumentPort {

    /**
     * Fetch tradable instruments from Kite.
     *
     * @param exchange optional exchange filter, e.g. {@code NSE}, {@code BSE}, {@code NFO}
     * @return list of tradable instruments; never {@code null}
     */
    List<InstrumentResponse> fetchInstruments(Optional<String> exchange);
}
