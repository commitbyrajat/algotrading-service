package com.algotrading.app.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Stores and retrieves the Kite {@code access_token} in Redis.
 *
 * <h3>Key design</h3>
 * <pre>
 *   Key : kite:access-token
 *   Type: String
 *   TTL : 24 hours (Kite tokens expire at 06:00 IST the next day)
 * </pre>
 *
 * <h3>Important limitation</h3>
 * <p>This service can only <em>reuse</em> a previously stored token.
 * It cannot automatically generate a new token on its own because Zerodha
 * requires a browser-based login to obtain a {@code request_token}, which
 * is then exchanged for an {@code access_token}.  See
 * {@code GET /api/v1/kite/login} to start the login flow.</p>
 */
@Service
public class RedisTokenCacheService {

    private static final Logger log = LoggerFactory.getLogger(RedisTokenCacheService.class);

    /** Redis key for the Kite access token. */
    public static final String ACCESS_TOKEN_KEY = "kite:access-token";

    /** Token TTL matches Kite's daily expiry cycle. */
    private static final Duration TOKEN_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redis;

    public RedisTokenCacheService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Persist the {@code access_token} in Redis with a 24-hour TTL.
     *
     * @param accessToken the token returned by Kite's session endpoint
     */
    public void saveAccessToken(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("accessToken must not be blank");
        }
        redis.opsForValue().set(ACCESS_TOKEN_KEY, accessToken, TOKEN_TTL);
        log.info("Kite access_token stored in Redis (TTL={})", TOKEN_TTL);
    }

    /**
     * Retrieve the stored {@code access_token} from Redis.
     *
     * @return an {@link Optional} containing the token, or empty if absent/expired
     */
    public Optional<String> loadAccessToken() {
        try {
            String token = redis.opsForValue().get(ACCESS_TOKEN_KEY);
            if (token == null || token.isBlank()) {
                log.debug("No access_token found in Redis");
                return Optional.empty();
            }
            log.debug("access_token loaded from Redis");
            return Optional.of(token);
        } catch (Exception ex) {
            // Redis unavailable – degrade gracefully; do not block startup
            log.warn("Could not load access_token from Redis ({}): {}",
                    ex.getClass().getSimpleName(), ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Remove the cached token (e.g. on logout or detected expiry).
     */
    public void clearAccessToken() {
        redis.delete(ACCESS_TOKEN_KEY);
        log.info("Kite access_token cleared from Redis");
    }
}