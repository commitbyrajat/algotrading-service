package com.algotrading.app.order;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable status snapshot of an existing order.
 */
public record OrderStatusResponse(
        String  orderId,
        String  tradingSymbol,
        String  transactionType,
        int     quantity,
        double  price,
        String  status,
        String  statusMessage,
        Instant fetchedAt
) {
    public OrderStatusResponse {
        Objects.requireNonNull(orderId, "orderId");
    }
}