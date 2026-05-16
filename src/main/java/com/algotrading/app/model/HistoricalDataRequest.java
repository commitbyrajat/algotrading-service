package com.algotrading.app.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Immutable query object for requesting historical candle data.
 */
@Schema(description = "Historical candle query used internally for strategy evaluation.")
public record HistoricalDataRequest(
        @Schema(description = "Kite instrument token.", example = "256265")
        String instrumentToken,
        @Schema(description = "Start date in ISO-8601 yyyy-MM-dd format.", example = "2024-01-01")
        LocalDate from,
        @Schema(description = "End date in ISO-8601 yyyy-MM-dd format.", example = "2024-06-30")
        LocalDate to,
        @Schema(description = "Kite candle interval.", example = "day")
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
