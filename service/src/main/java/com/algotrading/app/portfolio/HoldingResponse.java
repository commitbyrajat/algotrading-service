package com.algotrading.app.portfolio;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Long-term equity holding returned from Kite portfolio holdings.
 */
@Schema(description = "Long-term equity holding from Kite portfolio holdings.")
public record HoldingResponse(
        @Schema(description = "Exchange trading symbol.", example = "SBIN")
        String tradingSymbol,
        @Schema(description = "Exchange segment.", example = "NSE")
        String exchange,
        @Schema(description = "Kite instrument token.", example = "779521")
        String instrumentToken,
        @Schema(description = "ISIN identifier.", example = "INE062A01020")
        String isin,
        @Schema(description = "Kite product type.", example = "CNC")
        String product,
        @Schema(description = "Quantity currently held.", example = "16")
        int quantity,
        @Schema(description = "Quantity already sold from net holdings.", example = "0")
        int usedQuantity,
        @Schema(description = "T+1 quantity after order execution.", example = "0")
        int t1Quantity,
        @Schema(description = "Quantity delivered to Demat.", example = "16")
        int realisedQuantity,
        @Schema(description = "Quantity authorised at depository for sale.", example = "0")
        int authorisedQuantity,
        @Schema(description = "Date on which holding authorisation applies.")
        Instant authorisedAt,
        @Schema(description = "Quantity used as collateral.", example = "0")
        int collateralQuantity,
        @Schema(description = "Collateral type.", example = "")
        String collateralType,
        @Schema(description = "Whether holding has any price discrepancy.", example = "false")
        boolean discrepancy,
        @Schema(description = "Order price field returned by Kite.", example = "0.0")
        double price,
        @Schema(description = "Average acquisition price.", example = "801.78")
        double averagePrice,
        @Schema(description = "Last traded market price.", example = "762.45")
        double lastPrice,
        @Schema(description = "Previous close price.", example = "766.4")
        double closePrice,
        @Schema(description = "Profit and loss.", example = "-629.30")
        double pnl,
        @Schema(description = "Day change in absolute value.", example = "-3.95")
        double dayChange,
        @Schema(description = "Day change percentage.", example = "-0.52")
        double dayChangePercentage,
        @Schema(description = "Margin Trading Facility holding details.")
        MtfHoldingResponse mtf
) {
}
