package com.algotrading.app.order;

import com.algotrading.app.exception.MarketDataException;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.Order;
import com.zerodhatech.models.OrderParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Adapter that wraps Zerodha Kite SDK order APIs.
 *
 * <p>This is the <em>only</em> class that imports Kite order-related types.</p>
 *
 * <h3>Market protection — why this keeps failing</h3>
 * {@code OrderParams.marketProtection} is typed as {@code Double} (boxed),
 * not {@code double} (primitive). Its default value is therefore {@code null}.
 * {@code KiteConnect.placeOrder()} calls
 * {@code params.put("market_protection", orderParams.marketProtection)}
 * <strong>unconditionally</strong> — there is no null-guard in the SDK source.
 * When {@code marketProtection} is {@code null}, the string {@code "null"} (or
 * an absent value) is sent to the Zerodha API, which rejects it with:
 * <pre>
 *   InputException: Market orders without market protection are not allowed
 *   via API. Please set market protection or use a Limit order.
 * </pre>
 *
 * <h3>Fix</h3>
 * This adapter always sets {@code params.marketProtection = -1} for every
 * MARKET order. The value {@code -1} instructs Kite to apply its own
 * auto-protection limits (equivalent to the shield icon in the Kite web UI).
 * Callers never need to think about this field.
 *
 * <h3>Kite SDK 4.0.0 order API</h3>
 * <pre>
 *   kiteConnect.placeOrder(OrderParams, variety) → OrderResponse
 *   orderResponse.orderId                        → String
 *   kiteConnect.getOrderHistory(orderId)         → ArrayList&lt;Order&gt;
 * </pre>
 */
@Component
public class KiteOrderAdapter implements OrderPort {

    private static final Logger log = LoggerFactory.getLogger(KiteOrderAdapter.class);

    private static final String VARIETY_REGULAR = "regular";

    /**
     * Magic value accepted by Zerodha: instructs Kite to apply its default
     * auto-protection limits for MARKET orders.
     * Source: javakiteconnect/sample/src/Examples.java
     *   {@code orderParams.marketProtection = -1;}
     */
    private static final double AUTO_MARKET_PROTECTION = -1.0;

    private final KiteConnect kiteConnect;

    public KiteOrderAdapter(KiteConnect kiteConnect) {
        this.kiteConnect = kiteConnect;
    }

    @Override
    public PlacedOrderResponse placeOrder(OrderRequest request) {
        log.info("Placing {} {} {} qty={} price={} product={} at {}",
                request.orderType(), request.transactionType(),
                request.tradingSymbol(), request.quantity(),
                request.price(), request.product(), request.exchange());
        try {
            OrderParams params = buildOrderParams(request);

            // SDK 4.0.0: placeOrder returns OrderResponse (not Order)
            com.zerodhatech.models.OrderResponse response =
                    kiteConnect.placeOrder(params, VARIETY_REGULAR);

            log.info("Order placed: orderId={}", response.orderId);
            return PlacedOrderResponse.of(response.orderId, request);

        } catch (Exception | KiteException ex) {
            throw new MarketDataException(
                    "Failed to place order for " + request.tradingSymbol()
                            + ": " + ex.getMessage(), ex);
        }
    }

    @Override
    public List<PurchasedOrderResponse> listPurchasedOrders() {
        log.debug("Fetching Kite order book for open completed BUY quantities");
        try {
            List<Order> completedFilledOrders = kiteConnect.getOrders().stream()
                    .filter(KiteOrderAdapter::isCompletedFilledBuyOrSellOrder)
                    .sorted(Comparator.comparing(
                            KiteOrderAdapter::orderTimestamp,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();

            return remainingBuyLotsAfterCompletedSells(completedFilledOrders).stream()
                    .map(BuyLot::toPurchasedOrderResponse)
                    .sorted(Comparator.comparing(
                            PurchasedOrderResponse::orderTimestamp,
                            Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();
        } catch (Exception | KiteException ex) {
            throw new MarketDataException(
                    "Failed to list purchased orders: " + ex.getMessage(), ex);
        }
    }

    @Override
    public OrderStatusResponse getOrderStatus(String orderId) {
        log.debug("Fetching order history: orderId={}", orderId);
        try {
            List<Order> history = kiteConnect.getOrderHistory(orderId);

            if (history == null || history.isEmpty()) {
                throw new MarketDataException(
                        "No order history found for orderId: " + orderId);
            }

            Order latest = history.getLast();
            return new OrderStatusResponse(
                    latest.orderId,
                    latest.tradingSymbol,
                    latest.transactionType,
                    Integer.parseInt(latest.quantity),
                    Double.parseDouble(latest.price),
                    latest.status,
                    latest.statusMessage != null ? latest.statusMessage : "",
                    Instant.now()
            );

        } catch (MarketDataException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new MarketDataException(
                    "Failed to fetch order status for orderId: " + orderId, ex);
        } catch (KiteException e) {
            throw new RuntimeException(e);
        }
    }

    // ── private ───────────────────────────────────────────────────────────────

    /**
     * Maps the domain {@link OrderRequest} to a Kite SDK {@link OrderParams}.
     *
     * <h3>Market protection — critical detail</h3>
     * {@code OrderParams.marketProtection} is a {@code Double} (boxed). The SDK
     * sends it to the API unconditionally via
     * {@code params.put("market_protection", orderParams.marketProtection)}.
     * Leaving it as {@code null} causes Zerodha to reject the order.
     * We always set it to {@code -1} for MARKET orders (Kite auto-protection).
     * For non-MARKET orders we explicitly set it to {@code 0} (disabled) to
     * prevent sending a null value.
     */
    private OrderParams buildOrderParams(OrderRequest request) {
        OrderParams params = new OrderParams();
        params.tradingsymbol   = request.tradingSymbol();
        params.exchange        = request.exchange();
        params.transactionType = request.transactionType();
        params.orderType       = request.orderType();
        params.quantity        = request.quantity();
        params.product         = request.product();
        params.validity        = "DAY";

        // Price: required for LIMIT/SL; Kite ignores it for MARKET
        if (request.price() > 0) {
            params.price = request.price();
        }

        // Trigger price: required for SL and SL-M orders
        if (request.triggerPrice() > 0) {
            params.triggerPrice = request.triggerPrice();
        }

        // ── MARKET PROTECTION (the root cause of the recurring error) ─────────
        // OrderParams.marketProtection is a boxed Double, default null.
        // KiteConnect.java sends it unconditionally: params.put("market_protection", ...)
        // null → Zerodha rejects with InputException.
        //
        //   -1  → Kite applies auto-protection (recommended)
        //    0  → no protection (disabled) — valid for non-MARKET orders
        // 0..1  → explicit tolerance percentage (e.g. 0.01 = 1%)
        if ("MARKET".equalsIgnoreCase(request.orderType())) {
            params.marketProtection = AUTO_MARKET_PROTECTION;  // always -1 for MARKET
            log.debug("MARKET order: marketProtection set to {} (Kite auto-protection)",
                    AUTO_MARKET_PROTECTION);
        } else {
            params.marketProtection = 0.0;  // explicitly disabled for non-MARKET
        }

        return params;
    }

    private static boolean isCompletedBuyOrder(Order order) {
        return "BUY".equalsIgnoreCase(order.transactionType)
                && "COMPLETE".equalsIgnoreCase(order.status)
                && parseInt(order.filledQuantity) > 0;
    }

    private static boolean isCompletedSellOrder(Order order) {
        return "SELL".equalsIgnoreCase(order.transactionType)
                && "COMPLETE".equalsIgnoreCase(order.status)
                && parseInt(order.filledQuantity) > 0;
    }

    private static boolean isCompletedFilledBuyOrSellOrder(Order order) {
        return isCompletedBuyOrder(order) || isCompletedSellOrder(order);
    }

    private static List<BuyLot> remainingBuyLotsAfterCompletedSells(List<Order> completedFilledOrders) {
        Map<String, List<BuyLot>> lotsBySymbol = new LinkedHashMap<>();

        for (Order order : completedFilledOrders) {
            String symbol = normalizeTradingSymbol(order.tradingSymbol);
            if (symbol.isBlank()) {
                continue;
            }

            if (isCompletedBuyOrder(order)) {
                lotsBySymbol.computeIfAbsent(symbol, ignored -> new ArrayList<>())
                        .add(new BuyLot(order, parseInt(order.filledQuantity)));
                continue;
            }

            int remainingSellQuantity = parseInt(order.filledQuantity);
            List<BuyLot> buyLots = lotsBySymbol.getOrDefault(symbol, List.of());
            for (BuyLot buyLot : buyLots) {
                if (remainingSellQuantity <= 0) {
                    break;
                }
                int consumedQuantity = Math.min(buyLot.remainingQuantity, remainingSellQuantity);
                buyLot.remainingQuantity -= consumedQuantity;
                remainingSellQuantity -= consumedQuantity;
            }
        }

        return lotsBySymbol.values().stream()
                .flatMap(List::stream)
                .filter(BuyLot::hasRemainingQuantity)
                .toList();
    }

    private static PurchasedOrderResponse toPurchasedOrderResponse(Order order) {
        return new PurchasedOrderResponse(
                order.orderId,
                order.tradingSymbol,
                order.exchange,
                order.product,
                order.orderType,
                order.transactionType,
                parseInt(order.quantity),
                parseInt(order.filledQuantity),
                parseDouble(order.price),
                parseDouble(order.averagePrice),
                order.status,
                order.orderTimestamp != null ? order.orderTimestamp.toInstant() : null
        );
    }

    private static Instant orderTimestamp(Order order) {
        return order.orderTimestamp != null ? order.orderTimestamp.toInstant() : null;
    }

    private static String normalizeTradingSymbol(String tradingSymbol) {
        return tradingSymbol == null ? "" : tradingSymbol.trim().toUpperCase(Locale.ROOT);
    }

    private static int parseInt(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        return Integer.parseInt(value);
    }

    private static double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return 0.0;
        }
        return Double.parseDouble(value);
    }

    private static final class BuyLot {
        private final Order order;
        private int remainingQuantity;

        private BuyLot(Order order, int remainingQuantity) {
            this.order = order;
            this.remainingQuantity = remainingQuantity;
        }

        private boolean hasRemainingQuantity() {
            return remainingQuantity > 0;
        }

        private PurchasedOrderResponse toPurchasedOrderResponse() {
            return new PurchasedOrderResponse(
                    order.orderId,
                    order.tradingSymbol,
                    order.exchange,
                    order.product,
                    order.orderType,
                    order.transactionType,
                    parseInt(order.quantity),
                    remainingQuantity,
                    parseDouble(order.price),
                    parseDouble(order.averagePrice),
                    order.status,
                    orderTimestamp(order)
            );
        }
    }
}
