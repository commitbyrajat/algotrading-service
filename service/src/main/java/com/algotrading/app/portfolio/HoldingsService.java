package com.algotrading.app.portfolio;

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
 * Application service for portfolio holdings.
 */
@Service
public class HoldingsService {

    private static final Logger log = LoggerFactory.getLogger(HoldingsService.class);
    private static final TypeReference<List<String>> SYMBOL_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<HoldingResponse>> HOLDING_LIST_TYPE = new TypeReference<>() {
    };
    private static final String HOLDING_SYMBOLS_CACHE_KEY = "kite:holdings:symbols";
    private static final String HOLDING_SYMBOL_CACHE_KEY_PREFIX = "kite:holdings:";
    private static final Duration HOLDING_CACHE_TTL = Duration.ofMinutes(60);

    private final HoldingsPort holdingsPort;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Object holdingsCacheLock = new Object();

    @Autowired
    public HoldingsService(HoldingsPort holdingsPort,
                           StringRedisTemplate redis,
                           ObjectMapper objectMapper) {
        this.holdingsPort = holdingsPort;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    HoldingsService(HoldingsPort holdingsPort) {
        this.holdingsPort = holdingsPort;
        this.redis = null;
        this.objectMapper = null;
    }

    public List<HoldingResponse> listHoldings() {
        log.debug("HoldingsService.listHoldings");
        if (!isHoldingCacheAvailable()) {
            return holdingsPort.listHoldings();
        }
        return loadHoldingsFromRedis()
                .orElseGet(() -> {
                    synchronized (holdingsCacheLock) {
                        return loadHoldingsFromRedis()
                                .orElseGet(this::fetchAndCacheHoldings);
                    }
                });
    }

    public List<HoldingResponse> listHoldingsByTradingSymbol(String tradingSymbol) {
        String normalizedSymbol = normalizeTradingSymbol(tradingSymbol);
        if (normalizedSymbol.isBlank()) {
            return List.of();
        }

        if (!isHoldingCacheAvailable()) {
            return holdingsPort.listHoldings().stream()
                    .filter(holding -> normalizedSymbol.equals(normalizeTradingSymbol(holding.tradingSymbol())))
                    .toList();
        }

        return loadHoldingsForSymbolFromRedis(normalizedSymbol)
                .orElseGet(() -> fetchAndCacheHoldings().stream()
                        .filter(holding -> normalizedSymbol.equals(normalizeTradingSymbol(holding.tradingSymbol())))
                        .toList());
    }

    public void evictHoldingsCacheForSymbol(String tradingSymbol) {
        if (!isHoldingCacheAvailable()) {
            return;
        }

        String normalizedSymbol = normalizeTradingSymbol(tradingSymbol);
        if (normalizedSymbol.isBlank()) {
            return;
        }

        try {
            String symbolCacheKey = holdingSymbolCacheKey(normalizedSymbol);
            redis.delete(symbolCacheKey);
            removeHoldingSymbolFromIndex(normalizedSymbol);
            log.info("Evicted holdings cache for tradingSymbol={} key={}",
                    normalizedSymbol,
                    symbolCacheKey);
        } catch (Exception ex) {
            log.warn("Could not evict holdings cache for tradingSymbol={} ({}): {}",
                    normalizedSymbol,
                    ex.getClass().getSimpleName(),
                    ex.getMessage());
        }
    }

    public void clearHoldingsCache() {
        log.info("Clearing holdings cache");
        evictHoldingsCache();
    }

    private List<HoldingResponse> fetchAndCacheHoldings() {
        List<HoldingResponse> holdings = holdingsPort.listHoldings();
        saveHoldingsToRedis(holdings);
        return holdings;
    }

    private Optional<List<HoldingResponse>> loadHoldingsFromRedis() {
        if (!isHoldingCacheAvailable()) {
            return Optional.empty();
        }
        try {
            String symbolsJson = redis.opsForValue().get(HOLDING_SYMBOLS_CACHE_KEY);
            if (symbolsJson == null || symbolsJson.isBlank()) {
                log.debug("Holdings symbol index cache miss key={}", HOLDING_SYMBOLS_CACHE_KEY);
                return Optional.empty();
            }

            List<String> symbols = objectMapper.readValue(symbolsJson, SYMBOL_LIST_TYPE);
            List<HoldingResponse> holdings = new ArrayList<>();
            for (String symbol : symbols) {
                String cacheKey = holdingSymbolCacheKey(symbol);
                String holdingsJson = redis.opsForValue().get(cacheKey);
                if (holdingsJson == null || holdingsJson.isBlank()) {
                    log.debug("Holdings cache miss key={}", cacheKey);
                    return Optional.empty();
                }
                holdings.addAll(objectMapper.readValue(holdingsJson, HOLDING_LIST_TYPE));
            }

            log.debug("Loaded {} holdings from Redis across {} symbols",
                    holdings.size(),
                    symbols.size());
            return Optional.of(holdings);
        } catch (JacksonException ex) {
            log.warn("Ignoring invalid holdings cache payload: {}", ex.getOriginalMessage());
            evictHoldingsCache();
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("Could not load holdings from Redis ({}): {}",
                    ex.getClass().getSimpleName(),
                    ex.getMessage());
            return Optional.empty();
        }
    }

    private Optional<List<HoldingResponse>> loadHoldingsForSymbolFromRedis(String normalizedSymbol) {
        try {
            String cacheKey = holdingSymbolCacheKey(normalizedSymbol);
            String holdingsJson = redis.opsForValue().get(cacheKey);
            if (holdingsJson == null || holdingsJson.isBlank()) {
                log.info("Holdings cache has no entry for tradingSymbol={}", normalizedSymbol);
                return Optional.empty();
            }

            List<HoldingResponse> holdings = objectMapper.readValue(holdingsJson, HOLDING_LIST_TYPE);
            if (holdings.isEmpty()) {
                log.info("Holdings cache entry is empty for tradingSymbol={}", normalizedSymbol);
                return Optional.empty();
            }
            return Optional.of(holdings);
        } catch (JacksonException ex) {
            log.warn("Ignoring invalid holdings cache payload for tradingSymbol={}: {}",
                    normalizedSymbol,
                    ex.getOriginalMessage());
            evictHoldingsCache();
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("Could not load holdings cache for tradingSymbol={} ({}): {}",
                    normalizedSymbol,
                    ex.getClass().getSimpleName(),
                    ex.getMessage());
            return Optional.empty();
        }
    }

    private void saveHoldingsToRedis(List<HoldingResponse> holdings) {
        if (!isHoldingCacheAvailable()) {
            return;
        }
        try {
            evictHoldingsCache();
            Map<String, List<HoldingResponse>> holdingsBySymbol = holdings.stream()
                    .collect(Collectors.groupingBy(
                            holding -> normalizeTradingSymbol(holding.tradingSymbol()),
                            LinkedHashMap::new,
                            Collectors.toList()
                    ));

            List<String> symbols = new ArrayList<>(holdingsBySymbol.keySet());
            for (Map.Entry<String, List<HoldingResponse>> entry : holdingsBySymbol.entrySet()) {
                redis.opsForValue().set(
                        holdingSymbolCacheKey(entry.getKey()),
                        objectMapper.writeValueAsString(entry.getValue()),
                        HOLDING_CACHE_TTL
                );
            }
            redis.opsForValue().set(
                    HOLDING_SYMBOLS_CACHE_KEY,
                    objectMapper.writeValueAsString(symbols),
                    HOLDING_CACHE_TTL
            );

            log.info("Cached {} holdings grouped by {} trading symbols TTL={}",
                    holdings.size(),
                    symbols.size(),
                    HOLDING_CACHE_TTL);
        } catch (Exception ex) {
            log.warn("Could not cache holdings in Redis ({}): {}",
                    ex.getClass().getSimpleName(),
                    ex.getMessage());
        }
    }

    private void evictHoldingsCache() {
        if (!isHoldingCacheAvailable()) {
            return;
        }
        try {
            String symbolsJson = redis.opsForValue().get(HOLDING_SYMBOLS_CACHE_KEY);
            if (symbolsJson != null && !symbolsJson.isBlank()) {
                List<String> symbols = objectMapper.readValue(symbolsJson, SYMBOL_LIST_TYPE);
                Set<String> symbolKeys = symbols.stream()
                        .map(this::holdingSymbolCacheKey)
                        .collect(Collectors.toSet());
                if (!symbolKeys.isEmpty()) {
                    redis.delete(symbolKeys);
                }
            }
            redis.delete(HOLDING_SYMBOLS_CACHE_KEY);
        } catch (Exception ex) {
            log.warn("Could not evict holdings cache ({}): {}",
                    ex.getClass().getSimpleName(),
                    ex.getMessage());
        }
    }

    private void removeHoldingSymbolFromIndex(String normalizedSymbol) throws JacksonException {
        String symbolsJson = redis.opsForValue().get(HOLDING_SYMBOLS_CACHE_KEY);
        if (symbolsJson == null || symbolsJson.isBlank()) {
            return;
        }

        List<String> symbols = objectMapper.readValue(symbolsJson, SYMBOL_LIST_TYPE);
        List<String> remainingSymbols = symbols.stream()
                .map(this::normalizeTradingSymbol)
                .filter(symbol -> !symbol.equals(normalizedSymbol))
                .distinct()
                .toList();

        if (remainingSymbols.isEmpty()) {
            redis.delete(HOLDING_SYMBOLS_CACHE_KEY);
            return;
        }

        redis.opsForValue().set(
                HOLDING_SYMBOLS_CACHE_KEY,
                objectMapper.writeValueAsString(remainingSymbols),
                HOLDING_CACHE_TTL
        );
    }

    private String holdingSymbolCacheKey(String tradingSymbol) {
        return HOLDING_SYMBOL_CACHE_KEY_PREFIX + normalizeTradingSymbol(tradingSymbol);
    }

    private String normalizeTradingSymbol(String tradingSymbol) {
        return tradingSymbol == null ? "" : tradingSymbol.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isHoldingCacheAvailable() {
        return redis != null && objectMapper != null;
    }
}
