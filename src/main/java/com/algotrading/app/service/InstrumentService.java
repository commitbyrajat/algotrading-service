package com.algotrading.app.service;

import com.algotrading.app.market.InstrumentPort;
import com.algotrading.app.model.InstrumentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Application service for Kite instrument master retrieval.
 */
@Service
public class InstrumentService {

    private static final Logger log = LoggerFactory.getLogger(InstrumentService.class);
    private static final TypeReference<List<InstrumentResponse>> INSTRUMENT_LIST_TYPE = new TypeReference<>() {
    };
    private static final String CACHE_KEY_PREFIX = "kite:instruments:";
    private static final String ALL_EXCHANGES_CACHE_KEY = "ALL";
    private static final Duration CACHE_TTL = Duration.ofHours(24);

    private final InstrumentPort instrumentPort;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Object cacheLoadLock = new Object();

    public InstrumentService(InstrumentPort instrumentPort,
                             StringRedisTemplate redis,
                             ObjectMapper objectMapper) {
        this.instrumentPort = instrumentPort;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public List<InstrumentResponse> listInstruments(Optional<String> exchange) {
        Optional<String> normalizedExchange = normalizeExchange(exchange);
        String cacheKey = cacheKey(normalizedExchange);

        return loadFromRedis(cacheKey).orElseGet(() -> {
            synchronized (cacheLoadLock) {
                return loadFromRedis(cacheKey).orElseGet(() -> fetchAndCache(normalizedExchange, cacheKey));
            }
        });
    }

    private List<InstrumentResponse> fetchAndCache(Optional<String> exchange, String cacheKey) {
        List<InstrumentResponse> instruments = instrumentPort.fetchInstruments(exchange);
        saveToRedis(cacheKey, instruments);
        return instruments;
    }

    private Optional<List<InstrumentResponse>> loadFromRedis(String cacheKey) {
        try {
            String cachedJson = redis.opsForValue().get(cacheKey);
            if (cachedJson == null || cachedJson.isBlank()) {
                log.debug("Instrument cache miss for key={}", cacheKey);
                return Optional.empty();
            }

            List<InstrumentResponse> instruments = objectMapper.readValue(cachedJson, INSTRUMENT_LIST_TYPE);
            log.debug("Loaded {} instruments from Redis key={}", instruments.size(), cacheKey);
            return Optional.of(instruments);
        } catch (JacksonException ex) {
            log.warn("Ignoring invalid instrument cache payload for key={}: {}", cacheKey, ex.getOriginalMessage());
            deleteInvalidCacheEntry(cacheKey);
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("Could not load instruments from Redis key={} ({}): {}",
                    cacheKey, ex.getClass().getSimpleName(), ex.getMessage());
            return Optional.empty();
        }
    }

    private void saveToRedis(String cacheKey, List<InstrumentResponse> instruments) {
        try {
            String json = objectMapper.writeValueAsString(instruments);
            redis.opsForValue().set(cacheKey, json, CACHE_TTL);
            log.info("Cached {} instruments in Redis key={} TTL={}", instruments.size(), cacheKey, CACHE_TTL);
        } catch (Exception ex) {
            log.warn("Could not cache instruments in Redis key={} ({}): {}",
                    cacheKey, ex.getClass().getSimpleName(), ex.getMessage());
        }
    }

    private Optional<String> normalizeExchange(Optional<String> exchange) {
        return exchange
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(value -> value.toUpperCase(Locale.ROOT));
    }

    private String cacheKey(Optional<String> exchange) {
        return CACHE_KEY_PREFIX + exchange.orElse(ALL_EXCHANGES_CACHE_KEY);
    }

    private void deleteInvalidCacheEntry(String cacheKey) {
        try {
            redis.delete(cacheKey);
        } catch (Exception ex) {
            log.warn("Could not delete invalid instrument cache key={} ({}): {}",
                    cacheKey, ex.getClass().getSimpleName(), ex.getMessage());
        }
    }
}
