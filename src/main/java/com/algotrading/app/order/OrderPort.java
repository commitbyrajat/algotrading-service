package com.algotrading.app.order;

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
     * Fetch the latest status of an existing order.
     *
     * @param orderId the Kite order ID returned when the order was placed
     * @return current order status snapshot
     */
    OrderStatusResponse getOrderStatus(String orderId);
}