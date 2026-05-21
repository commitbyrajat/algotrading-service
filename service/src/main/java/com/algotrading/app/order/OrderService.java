package com.algotrading.app.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
    private static final TypeReference<List<String>> SYMBOL_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<PurchasedOrderResponse>> PURCHASED_ORDER_LIST_TYPE = new TypeReference<>() {
    };
    private static final String PURCHASED_ORDER_SYMBOLS_CACHE_KEY = "kite:purchased-orders:symbols";
    private static final String PURCHASED_ORDER_SYMBOL_CACHE_KEY_PREFIX = "kite:purchased-orders:";
    private static final Duration PURCHASED_ORDER_CACHE_TTL = Duration.ofMinutes(60);

    private final OrderPort orderPort;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Object purchasedOrdersCacheLock = new Object();

    @Autowired
    public OrderService(OrderPort orderPort,
                        StringRedisTemplate redis,
                        ObjectMapper objectMapper) {
        this.orderPort = orderPort;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    OrderService(OrderPort orderPort) {
        this.orderPort = orderPort;
        this.redis = null;
        this.objectMapper = null;
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
        validateSellOrderAgainstPurchasedOrderCache(request);
        PlacedOrderResponse response = orderPort.placeOrder(request);
        evictPurchasedOrdersCache();
        return response;
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
        validateSellOrderAgainstPurchasedOrderCache(sellOrder);
        PlacedOrderResponse response = orderPort.placeOrder(sellOrder);
        evictPurchasedOrdersCache();
        return response;
    }

    /**
     * List successfully completed BUY orders.
     *
     * @return completed purchase orders
     */
    public List<PurchasedOrderResponse> listPurchasedOrders() {
        log.debug("OrderService.listPurchasedOrders");
        if (!isPurchasedOrderCacheAvailable()) {
            return orderPort.listPurchasedOrders();
        }
        return loadPurchasedOrdersFromRedis()
                .orElseGet(() -> {
                    synchronized (purchasedOrdersCacheLock) {
                        return loadPurchasedOrdersFromRedis()
                                .orElseGet(this::fetchAndCachePurchasedOrders);
                    }
                });
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

    /**
     * Clear cached purchased orders grouped by trading symbol.
     */
    public void clearPurchasedOrdersCache() {
        log.info("Clearing purchased-order cache");
        evictPurchasedOrdersCache();
    }

    private List<PurchasedOrderResponse> fetchAndCachePurchasedOrders() {
        List<PurchasedOrderResponse> purchasedOrders = orderPort.listPurchasedOrders();
        savePurchasedOrdersToRedis(purchasedOrders);
        return purchasedOrders;
    }

    private Optional<List<PurchasedOrderResponse>> loadPurchasedOrdersFromRedis() {
        if (!isPurchasedOrderCacheAvailable()) {
            return Optional.empty();
        }
        try {
            String symbolsJson = redis.opsForValue().get(PURCHASED_ORDER_SYMBOLS_CACHE_KEY);
            if (symbolsJson == null || symbolsJson.isBlank()) {
                log.debug("Purchased order symbol index cache miss key={}", PURCHASED_ORDER_SYMBOLS_CACHE_KEY);
                return Optional.empty();
            }

            List<String> symbols = objectMapper.readValue(symbolsJson, SYMBOL_LIST_TYPE);
            List<PurchasedOrderResponse> purchasedOrders = new ArrayList<>();
            for (String symbol : symbols) {
                String cacheKey = purchasedOrderSymbolCacheKey(symbol);
                String ordersJson = redis.opsForValue().get(cacheKey);
                if (ordersJson == null || ordersJson.isBlank()) {
                    log.debug("Purchased order cache miss key={}", cacheKey);
                    return Optional.empty();
                }
                purchasedOrders.addAll(objectMapper.readValue(ordersJson, PURCHASED_ORDER_LIST_TYPE));
            }

            log.debug("Loaded {} purchased orders from Redis across {} symbols",
                    purchasedOrders.size(),
                    symbols.size());
            return Optional.of(purchasedOrders);
        } catch (JacksonException ex) {
            log.warn("Ignoring invalid purchased-order cache payload: {}", ex.getOriginalMessage());
            evictPurchasedOrdersCache();
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("Could not load purchased orders from Redis ({}): {}",
                    ex.getClass().getSimpleName(),
                    ex.getMessage());
            return Optional.empty();
        }
    }

    private void validateSellOrderAgainstPurchasedOrderCache(OrderRequest request) {
        if (!"SELL".equalsIgnoreCase(request.transactionType())) {
            return;
        }

        if (!isPurchasedOrderCacheAvailable()) {
            log.warn("Purchased-order cache is unavailable; skipping SELL cache guard for symbol={}",
                    request.tradingSymbol());
            return;
        }

        String normalizedSymbol = normalizeTradingSymbol(request.tradingSymbol());
        List<PurchasedOrderResponse> purchasedOrders = loadPurchasedOrdersForSymbolFromRedis(normalizedSymbol)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Cannot place SELL order because tradingSymbol '" + normalizedSymbol
                                + "' is not present in purchased-order cache"));

        int sellableQuantity = purchasedOrders.stream()
                .mapToInt(PurchasedOrderResponse::filledQuantity)
                .sum();
        if (sellableQuantity < request.quantity()) {
            throw new IllegalArgumentException(
                    "Cannot place SELL order because purchased-order cache has insufficient quantity for tradingSymbol '"
                            + normalizedSymbol + "': requested=" + request.quantity()
                            + ", cachedFilledQuantity=" + sellableQuantity);
        }

        log.debug("SELL cache guard passed symbol={} requestedQty={} cachedFilledQty={}",
                normalizedSymbol,
                request.quantity(),
                sellableQuantity);
    }

    private Optional<List<PurchasedOrderResponse>> loadPurchasedOrdersForSymbolFromRedis(String normalizedSymbol) {
        try {
            String cacheKey = purchasedOrderSymbolCacheKey(normalizedSymbol);
            String ordersJson = redis.opsForValue().get(cacheKey);
            if (ordersJson == null || ordersJson.isBlank()) {
                log.info("Purchased-order cache has no entry for tradingSymbol={}", normalizedSymbol);
                return Optional.empty();
            }

            List<PurchasedOrderResponse> purchasedOrders = objectMapper.readValue(
                    ordersJson,
                    PURCHASED_ORDER_LIST_TYPE
            );
            if (purchasedOrders.isEmpty()) {
                log.info("Purchased-order cache entry is empty for tradingSymbol={}", normalizedSymbol);
                return Optional.empty();
            }
            return Optional.of(purchasedOrders);
        } catch (JacksonException ex) {
            log.warn("Ignoring invalid purchased-order cache payload for tradingSymbol={}: {}",
                    normalizedSymbol,
                    ex.getOriginalMessage());
            evictPurchasedOrdersCache();
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("Could not load purchased-order cache for tradingSymbol={} ({}): {}",
                    normalizedSymbol,
                    ex.getClass().getSimpleName(),
                    ex.getMessage());
            return Optional.empty();
        }
    }

    private void savePurchasedOrdersToRedis(List<PurchasedOrderResponse> purchasedOrders) {
        if (!isPurchasedOrderCacheAvailable()) {
            return;
        }
        try {
            evictPurchasedOrdersCache();
            Map<String, List<PurchasedOrderResponse>> ordersBySymbol = purchasedOrders.stream()
                    .collect(Collectors.groupingBy(
                            order -> normalizeTradingSymbol(order.tradingSymbol()),
                            LinkedHashMap::new,
                            Collectors.toList()
                    ));

            List<String> symbols = new ArrayList<>(ordersBySymbol.keySet());
            for (Map.Entry<String, List<PurchasedOrderResponse>> entry : ordersBySymbol.entrySet()) {
                redis.opsForValue().set(
                        purchasedOrderSymbolCacheKey(entry.getKey()),
                        objectMapper.writeValueAsString(entry.getValue()),
                        PURCHASED_ORDER_CACHE_TTL
                );
            }
            redis.opsForValue().set(
                    PURCHASED_ORDER_SYMBOLS_CACHE_KEY,
                    objectMapper.writeValueAsString(symbols),
                    PURCHASED_ORDER_CACHE_TTL
            );

            log.info("Cached {} purchased orders grouped by {} trading symbols TTL={}",
                    purchasedOrders.size(),
                    symbols.size(),
                    PURCHASED_ORDER_CACHE_TTL);
        } catch (Exception ex) {
            log.warn("Could not cache purchased orders in Redis ({}): {}",
                    ex.getClass().getSimpleName(),
                    ex.getMessage());
        }
    }

    private void evictPurchasedOrdersCache() {
        if (!isPurchasedOrderCacheAvailable()) {
            return;
        }
        try {
            String symbolsJson = redis.opsForValue().get(PURCHASED_ORDER_SYMBOLS_CACHE_KEY);
            if (symbolsJson != null && !symbolsJson.isBlank()) {
                List<String> symbols = objectMapper.readValue(symbolsJson, SYMBOL_LIST_TYPE);
                Set<String> symbolKeys = symbols.stream()
                        .map(this::purchasedOrderSymbolCacheKey)
                        .collect(Collectors.toSet());
                if (!symbolKeys.isEmpty()) {
                    redis.delete(symbolKeys);
                }
            }
            redis.delete(PURCHASED_ORDER_SYMBOLS_CACHE_KEY);
        } catch (Exception ex) {
            log.warn("Could not evict purchased-order cache ({}): {}",
                    ex.getClass().getSimpleName(),
                    ex.getMessage());
        }
    }

    private String purchasedOrderSymbolCacheKey(String tradingSymbol) {
        return PURCHASED_ORDER_SYMBOL_CACHE_KEY_PREFIX + normalizeTradingSymbol(tradingSymbol);
    }

    private String normalizeTradingSymbol(String tradingSymbol) {
        return tradingSymbol == null ? "" : tradingSymbol.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isPurchasedOrderCacheAvailable() {
        return redis != null && objectMapper != null;
    }
}
