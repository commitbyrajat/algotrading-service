package com.algotrading.app.order;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable response after a successful order placement.
 */
public record PlacedOrderResponse(
        String  orderId,
        String  tradingSymbol,
        String  transactionType,
        int     quantity,
        double  price,
        String  status,
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