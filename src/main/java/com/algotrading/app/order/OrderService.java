package com.algotrading.app.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Application service for order lifecycle management.
 *
 * <h3>Safety guarantee</h3>
 * Strategy evaluation and order placement are intentionally kept in
 * separate services and separate HTTP endpoints.  A strategy returning
 * {@code BUY} or {@code SELL} does NOT automatically trigger an order.
 * The caller must make an explicit {@code POST /api/v1/orders} request.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderPort orderPort;

    public OrderService(OrderPort orderPort) {
        this.orderPort = orderPort;
    }

    /**
     * Place a new order.
     *
     * @param request fully validated order parameters
     * @return confirmation with the Kite-assigned {@code orderId}
     */
    public PlacedOrderResponse placeOrder(OrderRequest request) {
        log.info("OrderService.placeOrder: {} {} qty={}",
                request.transactionType(), request.tradingSymbol(), request.quantity());
        return orderPort.placeOrder(request);
    }

    /**
     * Retrieve the current status of an order.
     *
     * @param orderId Kite order ID
     * @return latest status snapshot
     */
    public OrderStatusResponse getOrderStatus(String orderId) {
        log.debug("OrderService.getOrderStatus: orderId={}", orderId);
        return orderPort.getOrderStatus(orderId);
    }
}