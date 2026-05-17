package com.algotrading.app.order;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Objects;

/**
 * Completed BUY order returned from the Kite order book.
 */
@Schema(description = "Successfully completed BUY order from Kite.")
public record PurchasedOrderResponse(
        @Schema(description = "Broker order id.", example = "240516000001234")
        String orderId,
        @Schema(description = "Trading symbol associated with the order.", example = "INFY")
        String tradingSymbol,
        @Schema(description = "Exchange segment.", example = "NSE")
        String exchange,
        @Schema(description = "Kite product type.", example = "CNC")
        String product,
        @Schema(description = "Kite order type.", example = "MARKET")
        String orderType,
        @Schema(description = "Order side. Purchased orders are always BUY.", example = "BUY")
        String transactionType,
        @Schema(description = "Original ordered quantity.", example = "10")
        int quantity,
        @Schema(description = "Filled quantity reported by Kite.", example = "10")
        int filledQuantity,
        @Schema(description = "Limit/order price reported by Kite.", example = "0")
        double price,
        @Schema(description = "Average executed buy price reported by Kite.", example = "1725.45")
        double averagePrice,
        @Schema(description = "Latest broker status.", example = "COMPLETE")
        String status,
        @Schema(description = "Broker order timestamp.", example = "2026-05-16T10:15:30Z")
        Instant orderTimestamp
) {
    public PurchasedOrderResponse {
        Objects.requireNonNull(orderId, "orderId");
    }
}
