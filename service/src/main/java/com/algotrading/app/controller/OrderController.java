package com.algotrading.app.controller;

import com.algotrading.app.order.ExitOrderRequest;
import com.algotrading.app.order.OrderRequest;
import com.algotrading.app.order.OrderService;
import com.algotrading.app.order.OrderStatusResponse;
import com.algotrading.app.order.PlacedOrderResponse;
import com.algotrading.app.order.PurchasedOrderResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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

import java.util.List;

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
 * POST /api/v1/orders/exit         → place a SELL order to exit a position
 * GET  /api/v1/orders/purchased    → list completed BUY orders
 * GET  /api/v1/orders/{orderId}/status → check order status
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/orders")
@Tag(
        name = "Orders",
        description = "Explicit Zerodha Kite order placement and order-status endpoints. Strategy signals never place orders automatically."
)
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
    @Operation(
            summary = "Place a Kite order",
            description = """
                    Places a new order through Kite. This endpoint is the only API that sends an order
                    to the broker; evaluating a strategy only returns a signal and does not call this API.
                    """
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = """
                    Order details accepted by Kite. Use price 0 for MARKET orders.
                    triggerPrice is only required for stop-loss order types such as SL or SL-M.
                    """,
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = OrderRequest.class),
                    examples = @ExampleObject(name = "Market buy", value = """
                            {
                              "tradingSymbol": "INFY",
                              "exchange": "NSE",
                              "transactionType": "BUY",
                              "quantity": 10,
                              "orderType": "MARKET",
                              "product": "CNC",
                              "price": 0,
                              "triggerPrice": 0
                            }
                            """))
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Order placed successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = PlacedOrderResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid order request",
                    content = @Content),
            @ApiResponse(responseCode = "401", description = "No active Kite session or Kite rejected authentication",
                    content = @Content),
            @ApiResponse(responseCode = "502", description = "Kite order service failed",
                    content = @Content),
            @ApiResponse(responseCode = "500", description = "Unexpected error",
                    content = @Content)
    })
    public ResponseEntity<PlacedOrderResponse> placeOrder(
            @RequestBody @Valid OrderRequest request) {
        PlacedOrderResponse response = orderService.placeOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * List successfully completed BUY orders from Kite.
     *
     * <pre>GET /api/v1/orders/purchased</pre>
     */
    @GetMapping("/purchased")
    @Operation(
            summary = "List purchased orders",
            description = "Returns orders from the Kite order book where transactionType is BUY, status is COMPLETE, and filledQuantity is greater than zero."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Purchased orders returned successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = PurchasedOrderResponse.class))),
            @ApiResponse(responseCode = "401", description = "No active Kite session",
                    content = @Content),
            @ApiResponse(responseCode = "502", description = "Kite order-book lookup failed",
                    content = @Content),
            @ApiResponse(responseCode = "500", description = "Unexpected error",
                    content = @Content)
    })
    public ResponseEntity<List<PurchasedOrderResponse>> listPurchasedOrders() {
        return ResponseEntity.ok(orderService.listPurchasedOrders());
    }

    /**
     * Exit an existing position with a SELL order.
     *
     * <pre>
     * POST /api/v1/orders/exit
     * Content-Type: application/json
     *
     * {
     *   "tradingSymbol": "INFY",
     *   "exchange":      "NSE",
     *   "quantity":      10,
     *   "orderType":     "MARKET",
     *   "product":       "CNC",
     *   "price":         0
     * }
     * </pre>
     */
    @PostMapping({"/exit", "/sell"})
    @Operation(
            summary = "Exit a position with a SELL order",
            description = """
                    Places a SELL order through Kite using the supplied symbol, quantity, order type,
                    and product. The request body intentionally omits transactionType because this
                    endpoint always submits SELL.
                    """
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "Exit details accepted by Kite. The service always submits transactionType SELL.",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ExitOrderRequest.class),
                    examples = @ExampleObject(name = "Market exit", value = """
                            {
                              "tradingSymbol": "INFY",
                              "exchange": "NSE",
                              "quantity": 10,
                              "orderType": "MARKET",
                              "product": "CNC",
                              "price": 0,
                              "triggerPrice": 0
                            }
                            """))
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Exit order placed successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = PlacedOrderResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid exit order request",
                    content = @Content),
            @ApiResponse(responseCode = "401", description = "No active Kite session or Kite rejected authentication",
                    content = @Content),
            @ApiResponse(responseCode = "502", description = "Kite order service failed",
                    content = @Content),
            @ApiResponse(responseCode = "500", description = "Unexpected error",
                    content = @Content)
    })
    public ResponseEntity<PlacedOrderResponse> exitPosition(
            @RequestBody @Valid ExitOrderRequest request) {
        PlacedOrderResponse response = orderService.exitPosition(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Fetch the latest status of an order.
     *
     * <pre>GET /api/v1/orders/{orderId}/status</pre>
     */
    @GetMapping("/{orderId}/status")
    @Operation(
            summary = "Get Kite order status",
            description = "Fetches the latest broker status for an existing Kite order id."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order status returned successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = OrderStatusResponse.class))),
            @ApiResponse(responseCode = "400", description = "Order id is blank or invalid",
                    content = @Content),
            @ApiResponse(responseCode = "401", description = "No active Kite session",
                    content = @Content),
            @ApiResponse(responseCode = "502", description = "Kite order-status lookup failed",
                    content = @Content),
            @ApiResponse(responseCode = "500", description = "Unexpected error",
                    content = @Content)
    })
    public ResponseEntity<OrderStatusResponse> getOrderStatus(
            @Parameter(
                    description = "Kite order id returned by the order-placement API.",
                    required = true,
                    example = "240516000001234"
            )
            @PathVariable @NotBlank String orderId) {
        return ResponseEntity.ok(orderService.getOrderStatus(orderId));
    }
}
