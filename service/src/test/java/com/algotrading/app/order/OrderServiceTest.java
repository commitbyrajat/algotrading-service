package com.algotrading.app.order;

import com.algotrading.app.portfolio.HoldingsService;
import com.algotrading.app.portfolio.HoldingsPort;
import com.algotrading.app.portfolio.HoldingResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderServiceTest {

    private CapturingOrderPort orderPort;
    private OrderService service;

    @BeforeEach
    void setUp() {
        orderPort = new CapturingOrderPort();
        service = new OrderService(orderPort);
    }

    @Test
    void exitPosition_placesSellOrder() {
        ExitOrderRequest request = new ExitOrderRequest(
                "INFY",
                "NSE",
                5,
                "MARKET",
                "CNC",
                null,
                null
        );

        PlacedOrderResponse response = service.exitPosition(request);

        OrderRequest sellOrder = orderPort.lastRequest;

        assertThat(sellOrder.tradingSymbol()).isEqualTo("INFY");
        assertThat(sellOrder.exchange()).isEqualTo("NSE");
        assertThat(sellOrder.transactionType()).isEqualTo("SELL");
        assertThat(sellOrder.quantity()).isEqualTo(5);
        assertThat(sellOrder.orderType()).isEqualTo("MARKET");
        assertThat(sellOrder.product()).isEqualTo("CNC");
        assertThat(sellOrder.price()).isZero();
        assertThat(sellOrder.triggerPrice()).isZero();

        assertThat(response.orderId()).isEqualTo("250101000001234");
        assertThat(response.transactionType()).isEqualTo("SELL");
    }

    @Test
    void exitOrderRequest_defaultsOptionalPricesToZero() {
        ExitOrderRequest request = new ExitOrderRequest(
                "SBIN",
                "NSE",
                1,
                "LIMIT",
                "CNC",
                null,
                null
        );

        OrderRequest sellOrder = request.toSellOrderRequest();

        assertThat(sellOrder.transactionType()).isEqualTo("SELL");
        assertThat(sellOrder.price()).isZero();
        assertThat(sellOrder.triggerPrice()).isZero();
    }

    @Test
    void listPurchasedOrders_delegatesToOrderPort() {
        orderPort.purchasedOrders = List.of(new PurchasedOrderResponse(
                "250101000001234",
                "INFY",
                "NSE",
                "CNC",
                "MARKET",
                "BUY",
                1,
                1,
                0.0,
                1725.45,
                "COMPLETE",
                null
        ));

        List<PurchasedOrderResponse> response = service.listPurchasedOrders();

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().orderId()).isEqualTo("250101000001234");
        assertThat(response.getFirst().transactionType()).isEqualTo("BUY");
        assertThat(response.getFirst().status()).isEqualTo("COMPLETE");
    }

    @Test
    void placeOrder_evictsHoldingsCacheAfterSuccessfulSellOrder() {
        CapturingHoldingsService holdingsService = new CapturingHoldingsService();
        service = new OrderService(orderPort, holdingsService, null, null);
        OrderRequest request = new OrderRequest(
                "INFY",
                "NSE",
                "SELL",
                5,
                "MARKET",
                "CNC",
                null,
                null
        );

        service.placeOrder(request);

        assertThat(holdingsService.evictedTradingSymbol).isEqualTo("INFY");
    }

    @Test
    void placeOrder_allowsSellWhenPurchasedOrderCacheMissesButHoldingsHaveQuantity() {
        CapturingHoldingsService holdingsService = new CapturingHoldingsService();
        holdingsService.holdings = List.of(sampleHolding("INFY", 8, 0, 2));
        service = new OrderService(orderPort, holdingsService, redisWithoutPurchasedOrders(), new ObjectMapper());

        OrderRequest request = new OrderRequest(
                "INFY",
                "NSE",
                "SELL",
                5,
                "MARKET",
                "CNC",
                null,
                null
        );

        service.placeOrder(request);

        assertThat(orderPort.lastRequest).isEqualTo(request);
        assertThat(holdingsService.lookupTradingSymbol).isEqualTo("INFY");
    }

    @Test
    void placeOrder_rejectsSellWhenNeitherPurchasedOrdersNorHoldingsHaveQuantity() {
        CapturingHoldingsService holdingsService = new CapturingHoldingsService();
        holdingsService.holdings = List.of(sampleHolding("INFY", 3, 0, 1));
        service = new OrderService(orderPort, holdingsService, redisWithoutPurchasedOrders(), new ObjectMapper());

        OrderRequest request = new OrderRequest(
                "INFY",
                "NSE",
                "SELL",
                5,
                "MARKET",
                "CNC",
                null,
                null
        );

        assertThatThrownBy(() -> service.placeOrder(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("neither purchased-order cache nor holdings cache has sufficient quantity")
                .hasMessageContaining("requested=5")
                .hasMessageContaining("purchasedOrderQuantity=0")
                .hasMessageContaining("holdingQuantity=2");
    }

    @Test
    void placeOrder_allowsSellWhenHoldingQuantityIsT1() {
        CapturingHoldingsService holdingsService = new CapturingHoldingsService();
        holdingsService.holdings = List.of(sampleHolding("JUSTDIAL", 0, 1, 0));
        service = new OrderService(orderPort, holdingsService, redisWithoutPurchasedOrders(), new ObjectMapper());

        OrderRequest request = new OrderRequest(
                "JUSTDIAL",
                "NSE",
                "SELL",
                1,
                "MARKET",
                "CNC",
                null,
                null
        );

        service.placeOrder(request);

        assertThat(orderPort.lastRequest).isEqualTo(request);
        assertThat(holdingsService.lookupTradingSymbol).isEqualTo("JUSTDIAL");
    }

    @Test
    void placeOrder_allowsWeekdayOrderBeforeMarketClose() {
        service = new OrderService(orderPort, marketHoursGuardAt("2026-05-25T09:59:00Z"));
        OrderRequest request = buyOrder();

        service.placeOrder(request);

        assertThat(orderPort.lastRequest).isEqualTo(request);
    }

    @Test
    void placeOrder_rejectsWeekdayOrderAtMarketClose() {
        service = new OrderService(orderPort, marketHoursGuardAt("2026-05-25T10:00:00Z"));
        OrderRequest request = buyOrder();

        assertThatThrownBy(() -> service.placeOrder(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("market is closed at or after 15:30");
        assertThat(orderPort.lastRequest).isNull();
    }

    @Test
    void placeOrder_rejectsWeekdayOrderAfterMarketClose() {
        service = new OrderService(orderPort, marketHoursGuardAt("2026-05-25T10:01:00Z"));
        OrderRequest request = buyOrder();

        assertThatThrownBy(() -> service.placeOrder(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("market is closed at or after 15:30");
        assertThat(orderPort.lastRequest).isNull();
    }

    @Test
    void placeOrder_usesConfiguredMarketCloseTime() {
        service = new OrderService(
                orderPort,
                marketHoursGuardAt("2026-05-25T10:59:00Z", LocalTime.of(16, 30))
        );
        OrderRequest request = buyOrder();

        service.placeOrder(request);

        assertThat(orderPort.lastRequest).isEqualTo(request);
    }

    @Test
    void placeOrder_rejectsWeekendOrderForWholeDay() {
        service = new OrderService(orderPort, marketHoursGuardAt("2026-05-23T05:30:00Z"));
        OrderRequest request = buyOrder();

        assertThatThrownBy(() -> service.placeOrder(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("market is closed on weekends");
        assertThat(orderPort.lastRequest).isNull();
    }

    @Test
    void exitPosition_rejectsWeekendOrderBeforeCallingPort() {
        service = new OrderService(orderPort, marketHoursGuardAt("2026-05-24T05:30:00Z"));
        ExitOrderRequest request = new ExitOrderRequest(
                "INFY",
                "NSE",
                5,
                "MARKET",
                "CNC",
                null,
                null
        );

        assertThatThrownBy(() -> service.exitPosition(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("market is closed on weekends");
        assertThat(orderPort.lastRequest).isNull();
    }

    private StringRedisTemplate redisWithoutPurchasedOrders() {
        return new FakeStringRedisTemplate();
    }

    private OrderRequest buyOrder() {
        return new OrderRequest(
                "INFY",
                "NSE",
                "BUY",
                5,
                "MARKET",
                "CNC",
                null,
                null
        );
    }

    private MarketHoursGuard marketHoursGuardAt(String instant) {
        return marketHoursGuardAt(instant, MarketHoursGuard.DEFAULT_MARKET_CLOSE_TIME);
    }

    private MarketHoursGuard marketHoursGuardAt(String instant, LocalTime marketCloseTime) {
        return new MarketHoursGuard(Clock.fixed(
                Instant.parse(instant),
                ZoneId.of("Asia/Kolkata")
        ), marketCloseTime);
    }

    private HoldingResponse sampleHolding(String tradingSymbol, int quantity, int t1Quantity, int usedQuantity) {
        return new HoldingResponse(
                tradingSymbol,
                "NSE",
                "779521",
                "INE062A01020",
                "CNC",
                quantity,
                usedQuantity,
                t1Quantity,
                quantity,
                0,
                null,
                0,
                "",
                false,
                0.0,
                801.78,
                762.45,
                766.4,
                -39.33,
                -3.95,
                -0.52,
                null
        );
    }

    private static class CapturingHoldingsService extends HoldingsService {
        private String evictedTradingSymbol;
        private String lookupTradingSymbol;
        private List<HoldingResponse> holdings = List.of();

        private CapturingHoldingsService() {
            super((HoldingsPort) () -> List.of(), null, null);
        }

        @Override
        public void evictHoldingsCacheForSymbol(String tradingSymbol) {
            evictedTradingSymbol = tradingSymbol;
        }

        @Override
        public List<HoldingResponse> listHoldingsByTradingSymbol(String tradingSymbol) {
            lookupTradingSymbol = tradingSymbol;
            return holdings;
        }
    }

    private static class CapturingOrderPort implements OrderPort {
        private OrderRequest lastRequest;
        private List<PurchasedOrderResponse> purchasedOrders = List.of();

        @Override
        public PlacedOrderResponse placeOrder(OrderRequest request) {
            lastRequest = request;
            return PlacedOrderResponse.of("250101000001234", request);
        }

        @Override
        public List<PurchasedOrderResponse> listPurchasedOrders() {
            return purchasedOrders;
        }

        @Override
        public OrderStatusResponse getOrderStatus(String orderId) {
            throw new UnsupportedOperationException("status lookup is not used by these tests");
        }
    }

    private static class FakeStringRedisTemplate extends StringRedisTemplate {
        private final Map<String, String> values = new HashMap<>();
        private final Map<String, Duration> expirations = new HashMap<>();
        private final FakeValueOperations valueOperations = new FakeValueOperations(this);

        @Override
        public ValueOperations<String, String> opsForValue() {
            return valueOperations;
        }

        @Override
        public Boolean delete(String key) {
            return values.remove(key) != null;
        }

        @Override
        public Long delete(Collection<String> keys) {
            long deleted = 0;
            for (String key : keys) {
                if (Boolean.TRUE.equals(delete(key))) {
                    deleted++;
                }
            }
            return deleted;
        }
    }

    private static class FakeValueOperations implements ValueOperations<String, String> {
        private final FakeStringRedisTemplate redis;

        private FakeValueOperations(FakeStringRedisTemplate redis) {
            this.redis = redis;
        }

        @Override
        public void set(String key, String value) {
            redis.values.put(key, value);
        }

        @Override
        public String setGet(String key, String value, long timeout, TimeUnit unit) {
            String previous = redis.values.put(key, value);
            redis.expirations.put(key, Duration.ofMillis(unit.toMillis(timeout)));
            return previous;
        }

        @Override
        public String setGet(String key, String value, Duration timeout) {
            String previous = redis.values.put(key, value);
            redis.expirations.put(key, timeout);
            return previous;
        }

        @Override
        public void set(String key, String value, long timeout, TimeUnit unit) {
            redis.values.put(key, value);
            redis.expirations.put(key, Duration.ofMillis(unit.toMillis(timeout)));
        }

        @Override
        public void set(String key, String value, Duration timeout) {
            redis.values.put(key, value);
            redis.expirations.put(key, timeout);
        }

        @Override
        public String get(Object key) {
            return redis.values.get(key);
        }

        @Override
        public Boolean setIfAbsent(String key, String value) {
            return redis.values.putIfAbsent(key, value) == null;
        }

        @Override
        public Boolean setIfAbsent(String key, String value, long timeout, TimeUnit unit) {
            Boolean result = setIfAbsent(key, value);
            if (Boolean.TRUE.equals(result)) {
                redis.expirations.put(key, Duration.ofMillis(unit.toMillis(timeout)));
            }
            return result;
        }

        @Override
        public Boolean setIfAbsent(String key, String value, Duration timeout) {
            Boolean result = setIfAbsent(key, value);
            if (Boolean.TRUE.equals(result)) {
                redis.expirations.put(key, timeout);
            }
            return result;
        }

        @Override
        public Boolean setIfPresent(String key, String value) {
            if (!redis.values.containsKey(key)) {
                return false;
            }
            redis.values.put(key, value);
            return true;
        }

        @Override
        public Boolean setIfPresent(String key, String value, long timeout, TimeUnit unit) {
            Boolean result = setIfPresent(key, value);
            if (Boolean.TRUE.equals(result)) {
                redis.expirations.put(key, Duration.ofMillis(unit.toMillis(timeout)));
            }
            return result;
        }

        @Override
        public Boolean setIfPresent(String key, String value, Duration timeout) {
            Boolean result = setIfPresent(key, value);
            if (Boolean.TRUE.equals(result)) {
                redis.expirations.put(key, timeout);
            }
            return result;
        }

        @Override
        public void multiSet(Map<? extends String, ? extends String> map) {
            redis.values.putAll(map);
        }

        @Override
        public Boolean multiSetIfAbsent(Map<? extends String, ? extends String> map) {
            if (map.keySet().stream().anyMatch(redis.values::containsKey)) {
                return false;
            }
            redis.values.putAll(map);
            return true;
        }

        @Override
        public String getAndDelete(String key) {
            return redis.values.remove(key);
        }

        @Override
        public String getAndExpire(String key, long timeout, TimeUnit unit) {
            redis.expirations.put(key, Duration.ofMillis(unit.toMillis(timeout)));
            return get(key);
        }

        @Override
        public String getAndExpire(String key, Duration timeout) {
            redis.expirations.put(key, timeout);
            return get(key);
        }

        @Override
        public String getAndPersist(String key) {
            redis.expirations.remove(key);
            return get(key);
        }

        @Override
        public String getAndSet(String key, String value) {
            return redis.values.put(key, value);
        }

        @Override
        public List<String> multiGet(Collection<String> keys) {
            return keys.stream().map(redis.values::get).toList();
        }

        @Override
        public Long increment(String key) {
            return increment(key, 1);
        }

        @Override
        public Long increment(String key, long delta) {
            long value = Long.parseLong(redis.values.getOrDefault(key, "0")) + delta;
            redis.values.put(key, Long.toString(value));
            return value;
        }

        @Override
        public Double increment(String key, double delta) {
            double value = Double.parseDouble(redis.values.getOrDefault(key, "0")) + delta;
            redis.values.put(key, Double.toString(value));
            return value;
        }

        @Override
        public Long decrement(String key) {
            return increment(key, -1);
        }

        @Override
        public Long decrement(String key, long delta) {
            return increment(key, -delta);
        }

        @Override
        public Integer append(String key, String value) {
            String appended = redis.values.getOrDefault(key, "") + value;
            redis.values.put(key, appended);
            return appended.length();
        }

        @Override
        public String get(String key, long start, long end) {
            String value = redis.values.get(key);
            if (value == null) {
                return null;
            }
            return value.substring((int) start, Math.min((int) end + 1, value.length()));
        }

        @Override
        public void set(String key, String value, long offset) {
            set(key, value);
        }

        @Override
        public Long size(String key) {
            String value = redis.values.get(key);
            return value == null ? 0L : (long) value.length();
        }

        @Override
        public Boolean setBit(String key, long offset, boolean value) {
            throw new UnsupportedOperationException("setBit is not used by these tests");
        }

        @Override
        public Boolean getBit(String key, long offset) {
            throw new UnsupportedOperationException("getBit is not used by these tests");
        }

        @Override
        public List<Long> bitField(String key, BitFieldSubCommands subCommands) {
            throw new UnsupportedOperationException("bitField is not used by these tests");
        }

        @Override
        public RedisOperations<String, String> getOperations() {
            return redis;
        }
    }
}
