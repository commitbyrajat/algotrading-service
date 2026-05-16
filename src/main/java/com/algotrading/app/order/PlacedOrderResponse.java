package com.algotrading.app.order;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable response after a successful order placement.
 */
@Schema(description = "Response returned after Kite accepts an order-placement request.")
public record PlacedOrderResponse(
        @Schema(description = "Broker order id returned by Kite.", example = "240516000001234")
        String  orderId,
        @Schema(description = "Symbol submitted in the order request.", example = "INFY")
        String  tradingSymbol,
        @Schema(description = "Order side submitted to Kite.", example = "BUY", allowableValues = {"BUY", "SELL"})
        String  transactionType,
        @Schema(description = "Submitted order quantity.", example = "10")
        int     quantity,
        @Schema(description = "Submitted order price. Market orders usually return 0 here.", example = "0")
        double  price,
        @Schema(description = "Initial local order status after successful placement.", example = "OPEN")
        String  status,
        @Schema(description = "Timestamp when this service placed the order.", example = "2026-05-16T10:15:30Z")
        Instant placedAt
) {
    public PlacedOrderResponse {
        Objects.requireNonNull(orderId, "orderId");
    }

    public static PlacedOrderResponse of(String orderId, OrderRequest request) {
        return new PlacedOrderResponse(
                orderId,
                request.tradingSymbol(),
                request.transactionType(),
                request.quantity(),
                request.price(),
                "OPEN",
                Instant.now()
        );
    }
}
