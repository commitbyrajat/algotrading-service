package com.algotrading.app.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/**
 * API representation of one Kite instrument master row.
 */
@Schema(description = "One row from Kite's instrument master.")
public record InstrumentResponse(
        @Schema(description = "Unique Kite instrument token used for historical data and quotes.", example = "408065")
        long instrumentToken,
        @Schema(description = "Exchange-specific token for the instrument.", example = "1594")
        long exchangeToken,
        @Schema(description = "Tradable symbol exactly as accepted by Kite order APIs.", example = "INFY")
        String tradingSymbol,
        @Schema(description = "Human-readable instrument name.", example = "INFOSYS")
        String name,
        @Schema(description = "Last reference price supplied by the instrument master, when available.", example = "1475.25")
        double lastPrice,
        @Schema(description = "Expiry date for derivative instruments. Null for cash-market equities.", example = "2026-05-28")
        LocalDate expiry,
        @Schema(description = "Strike price for options. Empty or zero for non-option instruments.", example = "1500")
        String strike,
        @Schema(description = "Minimum tick size accepted by the exchange.", example = "0.05")
        double tickSize,
        @Schema(description = "Exchange lot size.", example = "1")
        int lotSize,
        @Schema(description = "Instrument type such as EQ, FUT, CE, or PE.", example = "EQ")
        String instrumentType,
        @Schema(description = "Kite market segment.", example = "NSE")
        String segment,
        @Schema(description = "Kite exchange code.", example = "NSE")
        String exchange
) {
}
