package com.algotrading.app.service;

import com.algotrading.app.exception.InstrumentNotFoundException;
import com.algotrading.app.market.InstrumentPort;
import com.algotrading.app.model.InstrumentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Application service for Kite instrument master retrieval.
 */
@Service
public class InstrumentService {

    private static final Logger log = LoggerFactory.getLogger(InstrumentService.class);
    private static final TypeReference<List<InstrumentResponse>> INSTRUMENT_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, InstrumentResponse>> INSTRUMENT_MAP_TYPE = new TypeReference<>() {
    };
    private static final String CACHE_KEY_PREFIX = "kite:instruments:";
    private static final String SYMBOL_CACHE_KEY_PREFIX = "kite:instruments:by-symbol:";
    private static final String EXCHANGE_TOKEN_CACHE_KEY_PREFIX = "kite:instruments:by-exchange-token:";
    private static final String ALL_EXCHANGES_CACHE_KEY = "ALL";
    private static final Duration CACHE_TTL = Duration.ofHours(24);

    private final InstrumentPort instrumentPort;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final boolean requireExpiryForLookup;
    private final Object cacheLoadLock = new Object();

    public InstrumentService(InstrumentPort instrumentPort,
                             StringRedisTemplate redis,
                             ObjectMapper objectMapper,
                             @Value("${instrument.require-expiry-for-lookup:false}") boolean requireExpiryForLookup) {
        this.instrumentPort = instrumentPort;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.requireExpiryForLookup = requireExpiryForLookup;
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

    public List<InstrumentResponse> listInstrumentsByTradingSymbols(Optional<String> exchange,
                                                                    List<String> tradingSymbols) {
        Set<String> normalizedTradingSymbols = normalizeTradingSymbols(tradingSymbols);
        if (normalizedTradingSymbols.isEmpty()) {
            return List.of();
        }

        Optional<String> normalizedExchange = normalizeExchange(exchange);
        List<InstrumentResponse> result = listInstruments(normalizedExchange).stream()
                .filter(instrument -> normalizedTradingSymbols.contains(normalizeTradingSymbol(instrument.tradingSymbol())))
                .toList();
        if (result.isEmpty()) {
            throw new InstrumentNotFoundException(normalizedTradingSymbols, normalizedExchange);
        }
        return result;
    }

    public List<InstrumentResponse> listInstrumentsByIdentifiers(Optional<String> exchange,
                                                                 List<String> identifiers) {
        Set<String> normalizedIdentifiers = normalizeIdentifiers(identifiers);
        if (normalizedIdentifiers.isEmpty()) {
            return List.of();
        }

        Optional<String> normalizedExchange = normalizeExchange(exchange);
        List<InstrumentResponse> instruments = listInstruments(normalizedExchange);

        Map<String, InstrumentResponse> byTradingSymbol = loadMapFromRedis(symbolCacheKey(normalizedExchange))
                .orElseGet(() -> {
                    Map<String, InstrumentResponse> index = indexByTradingSymbol(instruments);
                    saveMapToRedis(symbolCacheKey(normalizedExchange), index);
                    return index;
                });
        Map<String, InstrumentResponse> byExchangeToken = loadMapFromRedis(exchangeTokenCacheKey(normalizedExchange))
                .orElseGet(() -> {
                    Map<String, InstrumentResponse> index = indexByExchangeToken(instruments);
                    saveMapToRedis(exchangeTokenCacheKey(normalizedExchange), index);
                    return index;
                });

        List<InstrumentResponse> result = normalizedIdentifiers.stream()
                .map(identifier -> resolveFromCaches(identifier, byTradingSymbol, byExchangeToken))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
        if (result.isEmpty()) {
            throw new InstrumentNotFoundException(normalizedIdentifiers, normalizedExchange);
        }
        return result;
    }

    private List<InstrumentResponse> fetchAndCache(Optional<String> exchange, String cacheKey) {
        List<InstrumentResponse> instruments = instrumentPort.fetchInstruments(exchange);
        saveInstrumentCaches(exchange, cacheKey, instruments);
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

    private Optional<Map<String, InstrumentResponse>> loadMapFromRedis(String cacheKey) {
        try {
            String cachedJson = redis.opsForValue().get(cacheKey);
            if (cachedJson == null || cachedJson.isBlank()) {
                log.debug("Instrument index cache miss for key={}", cacheKey);
                return Optional.empty();
            }

            Map<String, InstrumentResponse> instruments = objectMapper.readValue(cachedJson, INSTRUMENT_MAP_TYPE);
            log.debug("Loaded {} indexed instruments from Redis key={}", instruments.size(), cacheKey);
            return Optional.of(instruments);
        } catch (JacksonException ex) {
            log.warn("Ignoring invalid instrument index cache payload for key={}: {}", cacheKey, ex.getOriginalMessage());
            deleteInvalidCacheEntry(cacheKey);
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("Could not load instrument index cache key={} ({}): {}",
                    cacheKey, ex.getClass().getSimpleName(), ex.getMessage());
            return Optional.empty();
        }
    }

    private void saveInstrumentCaches(Optional<String> exchange,
                                      String listCacheKey,
                                      List<InstrumentResponse> instruments) {
        saveToRedis(listCacheKey, instruments);
        saveMapToRedis(symbolCacheKey(exchange), indexByTradingSymbol(instruments));
        saveMapToRedis(exchangeTokenCacheKey(exchange), indexByExchangeToken(instruments));
    }

    private boolean isLookupEligibleInstrument(InstrumentResponse instrument) {
        return instrument != null && (!requireExpiryForLookup || instrument.expiry() != null);
    }

    private Optional<InstrumentResponse> resolveFromCaches(String identifier,
                                                           Map<String, InstrumentResponse> byTradingSymbol,
                                                           Map<String, InstrumentResponse> byExchangeToken) {
        InstrumentResponse symbolMatch = byTradingSymbol.get(identifier);
        if (symbolMatch != null) {
            return cacheableCacheHit(identifier, "tradingSymbol", symbolMatch);
        }

        InstrumentResponse exchangeTokenMatch = byExchangeToken.get(identifier);
        if (exchangeTokenMatch != null) {
            return cacheableCacheHit(identifier, "exchangeToken", exchangeTokenMatch);
        }

        return Optional.empty();
    }

    private Optional<InstrumentResponse> cacheableCacheHit(String identifier,
                                                           String cacheType,
                                                           InstrumentResponse instrument) {
        if (isLookupEligibleInstrument(instrument)) {
            return Optional.of(instrument);
        }

        log.info("Treating cached instrument as not found because expiry is null and instrument.require-expiry-for-lookup=true identifier={} cacheType={} tradingSymbol={} exchangeToken={}",
                identifier,
                cacheType,
                instrument == null ? null : instrument.tradingSymbol(),
                instrument == null ? null : instrument.exchangeToken());
        return Optional.empty();
    }

    private void saveMapToRedis(String cacheKey, Map<String, InstrumentResponse> instrumentsByIdentifier) {
        try {
            String json = objectMapper.writeValueAsString(instrumentsByIdentifier);
            redis.opsForValue().set(cacheKey, json, CACHE_TTL);
            log.info("Cached {} instrument index entries in Redis key={} TTL={}",
                    instrumentsByIdentifier.size(), cacheKey, CACHE_TTL);
        } catch (Exception ex) {
            log.warn("Could not cache instrument index key={} ({}): {}",
                    cacheKey, ex.getClass().getSimpleName(), ex.getMessage());
        }
    }

    private Map<String, InstrumentResponse> indexByTradingSymbol(List<InstrumentResponse> instruments) {
        return instruments.stream()
                .filter(instrument -> !normalizeTradingSymbol(instrument.tradingSymbol()).isBlank())
                .collect(Collectors.toMap(
                        instrument -> normalizeTradingSymbol(instrument.tradingSymbol()),
                        Function.identity(),
                        (first, ignored) -> first
                ));
    }

    private Map<String, InstrumentResponse> indexByExchangeToken(List<InstrumentResponse> instruments) {
        return instruments.stream()
                .collect(Collectors.toMap(
                        instrument -> normalizeExchangeToken(instrument.exchangeToken()),
                        Function.identity(),
                        (first, ignored) -> first
                ));
    }

    private Optional<String> normalizeExchange(Optional<String> exchange) {
        return exchange
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(value -> value.toUpperCase(Locale.ROOT));
    }

    private Set<String> normalizeIdentifiers(List<String> identifiers) {
        Set<String> normalizedIdentifiers = new LinkedHashSet<>();
        if (identifiers == null) {
            return normalizedIdentifiers;
        }

        for (String identifier : identifiers) {
            String normalizedIdentifier = normalizeIdentifier(identifier);
            if (!normalizedIdentifier.isBlank()) {
                normalizedIdentifiers.add(normalizedIdentifier);
            }
        }
        return normalizedIdentifiers;
    }

    private String normalizeIdentifier(String identifier) {
        if (identifier == null) {
            return "";
        }
        return identifier.trim().toUpperCase(Locale.ROOT);
    }

    private Set<String> normalizeTradingSymbols(List<String> tradingSymbols) {
        Set<String> normalizedSymbols = new LinkedHashSet<>();
        if (tradingSymbols == null) {
            return normalizedSymbols;
        }

        for (String tradingSymbol : tradingSymbols) {
            String normalizedSymbol = normalizeTradingSymbol(tradingSymbol);
            if (!normalizedSymbol.isBlank()) {
                normalizedSymbols.add(normalizedSymbol);
            }
        }
        return normalizedSymbols;
    }

    private String normalizeTradingSymbol(String tradingSymbol) {
        if (tradingSymbol == null) {
            return "";
        }
        return tradingSymbol.trim().toUpperCase(Locale.ROOT);
    }

    private String cacheKey(Optional<String> exchange) {
        return CACHE_KEY_PREFIX + exchange.orElse(ALL_EXCHANGES_CACHE_KEY);
    }

    private String symbolCacheKey(Optional<String> exchange) {
        return SYMBOL_CACHE_KEY_PREFIX + exchange.orElse(ALL_EXCHANGES_CACHE_KEY);
    }

    private String exchangeTokenCacheKey(Optional<String> exchange) {
        return EXCHANGE_TOKEN_CACHE_KEY_PREFIX + exchange.orElse(ALL_EXCHANGES_CACHE_KEY);
    }

    private String normalizeExchangeToken(long exchangeToken) {
        return Long.toString(exchangeToken);
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
