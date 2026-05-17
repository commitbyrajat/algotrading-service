package com.algotrading.app.order;

import java.util.List;

/**
 * Hexagonal port for order management.
 * No Kite SDK types cross this boundary.
 */
public interface OrderPort {

    /**
     * Place a new order.
     *
     * @param request the order parameters
     * @return confirmation with the assigned {@code orderId}
     */
    PlacedOrderResponse placeOrder(OrderRequest request);

    /**
     * List successfully completed BUY orders from the broker order book.
     *
     * @return completed purchase orders
     */
    List<PurchasedOrderResponse> listPurchasedOrders();

    /**
     * Fetch the latest status of an existing order.
     *
     * @param orderId the Kite order ID returned when the order was placed
     * @return current order status snapshot
     */
    OrderStatusResponse getOrderStatus(String orderId);
}
