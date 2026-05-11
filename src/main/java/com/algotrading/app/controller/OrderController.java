package com.algotrading.app.controller;

import com.algotrading.app.order.OrderRequest;
import com.algotrading.app.order.OrderService;
import com.algotrading.app.order.OrderStatusResponse;
import com.algotrading.app.order.PlacedOrderResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Order management endpoints.
 *
 * <h3>SAFETY NOTE</h3>
 * Strategy evaluation and order placement are completely decoupled.
 * A BUY/SELL signal from {@code /api/v1/strategies/.../evaluate} does NOT
 * automatically place an order.  You must explicitly call {@code POST /api/v1/orders}.
 *
 * <pre>
 * POST /api/v1/orders              → place a new order
 * GET  /api/v1/orders/{orderId}/status → check order status
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Place a new order.
     *
     * <pre>
     * POST /api/v1/orders
     * Content-Type: application/json
     *
     * {
     *   "tradingSymbol":   "INFY",
     *   "exchange":        "NSE",
     *   "transactionType": "BUY",
     *   "quantity":        10,
     *   "orderType":       "MARKET",
     *   "product":         "CNC",
     *   "price":           0
     * }
     * </pre>
     */
    @PostMapping
    public ResponseEntity<PlacedOrderResponse> placeOrder(
            @RequestBody @Valid OrderRequest request) {
        PlacedOrderResponse response = orderService.placeOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Fetch the latest status of an order.
     *
     * <pre>GET /api/v1/orders/{orderId}/status</pre>
     */
    @GetMapping("/{orderId}/status")
    public ResponseEntity<OrderStatusResponse> getOrderStatus(
            @PathVariable @NotBlank String orderId) {
        return ResponseEntity.ok(orderService.getOrderStatus(orderId));
    }
}