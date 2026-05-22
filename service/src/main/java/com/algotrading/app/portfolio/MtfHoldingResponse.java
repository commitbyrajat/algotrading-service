package com.algotrading.app.portfolio;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Margin Trading Facility details embedded in a Kite holding.
 */
@Schema(description = "Margin Trading Facility details for a holding.")
public record MtfHoldingResponse(
        @Schema(description = "MTF quantity.", example = "0")
        int quantity,
        @Schema(description = "MTF used quantity.", example = "0")
        int usedQuantity,
        @Schema(description = "MTF average price.", example = "0.0")
        double averagePrice,
        @Schema(description = "MTF holding value.", example = "0.0")
        double value,
        @Schema(description = "MTF initial margin.", example = "0.0")
        double initialMargin
) {
}
