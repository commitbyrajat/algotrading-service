package com.algotrading.app.portfolio;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class HoldingsServiceTest {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private CapturingHoldingsPort holdingsPort;
    private FakeStringRedisTemplate redis;
    private ObjectMapper objectMapper;
    private HoldingsService service;

    @BeforeEach
    void setUp() {
        holdingsPort = new CapturingHoldingsPort();
        redis = new FakeStringRedisTemplate();
        objectMapper = new ObjectMapper();
        service = new HoldingsService(holdingsPort, redis, objectMapper);
    }

    @Test
    void listHoldingsByTradingSymbol_returnsCachedSymbolHoldingsWithoutCallingKite() throws Exception {
        HoldingResponse cached = sampleHolding("INFY");
        redis.values.put("kite:holdings:INFY", objectMapper.writeValueAsString(List.of(cached)));

        List<HoldingResponse> result = service.listHoldingsByTradingSymbol(" infy ");

        assertThat(result).containsExactly(cached);
        assertThat(holdingsPort.listHoldingsCalls).isZero();
    }

    @Test
    void listHoldingsByTradingSymbol_lazyLoadsAndCachesGroupedHoldingsOnMiss() {
        HoldingResponse infy = sampleHolding("INFY");
        HoldingResponse tcs = sampleHolding("TCS");
        holdingsPort.holdings = List.of(infy, tcs);

        List<HoldingResponse> result = service.listHoldingsByTradingSymbol("INFY");

        assertThat(result).containsExactly(infy);
        assertThat(holdingsPort.listHoldingsCalls).isOne();
        assertThat(redis.values).containsKeys("kite:holdings:INFY", "kite:holdings:TCS", "kite:holdings:symbols");
        assertThat(redis.expirations).containsEntry("kite:holdings:INFY", Duration.ofMinutes(60));
        assertThat(redis.expirations).containsEntry("kite:holdings:TCS", Duration.ofMinutes(60));
        assertThat(redis.expirations).containsEntry("kite:holdings:symbols", Duration.ofMinutes(60));
    }

    @Test
    void evictHoldingsCacheForSymbol_removesSymbolEntryAndIndexMember() throws Exception {
        redis.values.put("kite:holdings:symbols", objectMapper.writeValueAsString(List.of("INFY", "TCS")));
        redis.values.put("kite:holdings:INFY", objectMapper.writeValueAsString(List.of(sampleHolding("INFY"))));
        redis.values.put("kite:holdings:TCS", objectMapper.writeValueAsString(List.of(sampleHolding("TCS"))));

        service.evictHoldingsCacheForSymbol(" infy ");

        assertThat(redis.values).doesNotContainKey("kite:holdings:INFY");
        assertThat(redis.values).containsKey("kite:holdings:TCS");
        assertThat(objectMapper.readValue(redis.values.get("kite:holdings:symbols"), STRING_LIST_TYPE))
                .containsExactly("TCS");
    }

    private HoldingResponse sampleHolding(String tradingSymbol) {
        return new HoldingResponse(
                tradingSymbol,
                "NSE",
                "779521",
                "INE062A01020",
                "CNC",
                1,
                0,
                0,
                1,
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

    private static class CapturingHoldingsPort implements HoldingsPort {
        private List<HoldingResponse> holdings = List.of();
        private int listHoldingsCalls;

        @Override
        public List<HoldingResponse> listHoldings() {
            listHoldingsCalls++;
            return holdings;
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
