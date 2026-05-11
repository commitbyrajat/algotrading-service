package com.algotrading.app.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Immutable query object for requesting historical candle data.
 */
public record HistoricalDataRequest(
        String instrumentToken,
        LocalDate from,
        LocalDate to,
        String interval
) {
    public HistoricalDataRequest {
        Objects.requireNonNull(instrumentToken, "instrumentToken must not be null");
        Objects.requireNonNull(from,            "from must not be null");
        Objects.requireNonNull(to,              "to must not be null");
        Objects.requireNonNull(interval,        "interval must not be null");
        if (instrumentToken.isBlank()) {
            throw new IllegalArgumentException("instrumentToken must not be blank");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException(
                    "'from' (" + from + ") must not be after 'to' (" + to + ")");
        }
    }
}