package com.algotrading.app.order;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable status snapshot of an existing order.
 */
@Schema(description = "Latest broker status snapshot for a Kite order.")
public record OrderStatusResponse(
        @Schema(description = "Broker order id.", example = "240516000001234")
        String  orderId,
        @Schema(description = "Trading symbol associated with the order.", example = "INFY")
        String  tradingSymbol,
        @Schema(description = "Order side.", example = "BUY", allowableValues = {"BUY", "SELL"})
        String  transactionType,
        @Schema(description = "Order quantity.", example = "10")
        int     quantity,
        @Schema(description = "Order price reported by Kite.", example = "0")
        double  price,
        @Schema(description = "Latest broker status.", example = "COMPLETE")
        String  status,
        @Schema(description = "Broker status message or rejection reason, when available.", example = "Order executed successfully")
        String  statusMessage,
        @Schema(description = "Timestamp when this service fetched the status.", example = "2026-05-16T10:15:30Z")
        Instant fetchedAt
) {
    public OrderStatusResponse {
        Objects.requireNonNull(orderId, "orderId");
    }
}
