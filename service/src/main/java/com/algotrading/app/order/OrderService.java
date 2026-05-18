package com.algotrading.app.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

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
     * Exit an existing long position by submitting an explicit SELL order.
     *
     * @param request exit order parameters without a caller-supplied side
     * @return confirmation with the Kite-assigned {@code orderId}
     */
    public PlacedOrderResponse exitPosition(ExitOrderRequest request) {
        OrderRequest sellOrder = request.toSellOrderRequest();
        log.info("OrderService.exitPosition: SELL {} qty={}",
                sellOrder.tradingSymbol(), sellOrder.quantity());
        return orderPort.placeOrder(sellOrder);
    }

    /**
     * List successfully completed BUY orders.
     *
     * @return completed purchase orders
     */
    public List<PurchasedOrderResponse> listPurchasedOrders() {
        log.debug("OrderService.listPurchasedOrders");
        return orderPort.listPurchasedOrders();
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
