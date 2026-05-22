package com.algotrading.app.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Position-sizing guidance for a BUY strategy decision.
 */
@Schema(description = "Suggested BUY quantity calculated from volatility and risk settings.")
public record QuantitySuggestion(
        @Schema(description = "Suggested quantity to buy after risk and exposure caps.", example = "133")
        int suggestedQuantity,
        @Schema(description = "Sizing method used.", example = "ATR_RISK")
        String method,
        @Schema(description = "Latest candle close used as current market price proxy.", example = "1000.0")
        double currentPrice,
        @Schema(description = "Average True Range used for volatility sizing.", example = "25.0")
        double atr,
        @Schema(description = "ATR multiplier used to estimate stop distance.", example = "1.5")
        double atrMultiplier,
        @Schema(description = "Risk per share: ATR multiplied by the ATR multiplier.", example = "37.5")
        double riskPerShare,
        @Schema(description = "Configured capital used for sizing.", example = "500000.0")
        double capital,
        @Schema(description = "Risk percentage per trade.", example = "0.01")
        double riskPercent,
        @Schema(description = "Risk amount for the trade.", example = "5000.0")
        double riskAmount,
        @Schema(description = "Quantity before max-exposure cap.", example = "133")
        int riskBasedQuantity,
        @Schema(description = "Quantity allowed by max portfolio exposure cap.", example = "100")
        int maxExposureQuantity,
        @Schema(description = "Plain-English explanation of the quantity calculation.")
        String explanation
) {
}
