package com.algotrading.app.auth;

import com.algotrading.app.config.KiteProperties;
import com.algotrading.app.redis.RedisTokenCacheService;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.User;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Manages the Kite Connect OAuth2-style authentication flow.
 *
 * <h3>Flow</h3>
 * <ol>
 *   <li>Client opens {@code GET /kite/login} → redirected to Zerodha login page.</li>
 *   <li>After login Zerodha redirects to
 *       {@code GET /kite/callback?request_token=xxx&status=success}.</li>
 *   <li>{@link #handleCallback(String)} calls
 *       {@link KiteConnect#generateSession(String, String)} which internally
 *       computes {@code SHA256(apiKey + requestToken + apiSecret)} and POSTs
 *       to {@code /session/token}.  The SDK returns a {@link User} with
 *       {@code accessToken} and {@code publicToken}.</li>
 *   <li>Tokens are stored in {@link KiteSessionStore} and applied to the
 *       shared {@link KiteConnect} bean so all downstream calls succeed.</li>
 * </ol>
 *
 * <p>The {@code request_token} is one-time-use and expires within a few
 * minutes.  The resulting {@code access_token} is valid until 06:00 IST
 * the following day.</p>
 */
@Service
public class KiteAuthService {

    private static final Logger log = LoggerFactory.getLogger(KiteAuthService.class);

    private final KiteConnect      kiteConnect;
    private final KiteProperties   props;
    private final KiteSessionStore sessionStore;
    private final RedisTokenCacheService tokenCache;

    public KiteAuthService(KiteConnect kiteConnect,
                           KiteProperties props,
                           KiteSessionStore sessionStore,
                           RedisTokenCacheService tokenCache) {
        this.kiteConnect  = kiteConnect;
        this.props        = props;
        this.sessionStore = sessionStore;
        this.tokenCache   = tokenCache;
    }

    // ─────────────────────────── Bootstrap ─────────────────────────────────

    /**
     * Attempt to restore a cached {@code access_token} from Redis on startup.
     * Failure is non-fatal – the application continues without a token.
     */
    @PostConstruct
    public void tryRestoreTokenFromRedis() {
        tokenCache.loadAccessToken().ifPresentOrElse(
                token -> {
                    kiteConnect.setAccessToken(token);
                    sessionStore.save(new KiteSessionStore.Session(
                            token, null, props.userId(), Instant.now()));
                    log.info("Kite access_token restored from Redis – app is ready");
                },
                () -> log.warn(
                        "No Kite access_token in Redis. "
                                + "Visit GET /api/v1/kite/login to authenticate. "
                                + "Strategy evaluation will fail until authentication is complete.")
        );
    }


    /**
     * Returns the Zerodha login URL to redirect the user to.
     *
     * <p>The URL embeds the {@code api_key} and the {@code v=3} version flag.
     * After successful login Zerodha will redirect back to the redirect URL
     * configured in the Developer Console with a {@code request_token} param.</p>
     *
     * @return full login URL, e.g.
     *         {@code https://kite.zerodha.com/connect/login?v=3&api_key=xxx}
     */
    public String getLoginUrl() {
        String url = kiteConnect.getLoginURL();
        log.debug("Generated Kite login URL: {}", url);
        return url;
    }

    /**
     * Exchanges a one-time {@code request_token} for a long-lived
     * {@code access_token} and stores the result.
     *
     * <p>The SDK's {@code generateSession()} method internally computes
     * {@code SHA256(apiKey + requestToken + apiSecret)} — no manual
     * checksum computation is required in our code.</p>
     *
     * @param requestToken the {@code request_token} query param from Zerodha's redirect
     * @return the persisted {@link KiteSessionStore.Session}
     * @throws KiteAuthException if session generation fails for any reason
     */
    public KiteSessionStore.Session handleCallback(String requestToken) {
        log.info("Exchanging request_token for access_token (token truncated: {}...)",
                requestToken.length() > 6 ? requestToken.substring(0, 6) : requestToken);
        try {
            // SDK computes SHA256(apiKey + requestToken + apiSecret) internally
            User user = kiteConnect.generateSession(requestToken, props.apiSecret());

            // Apply tokens to the shared KiteConnect bean immediately
            kiteConnect.setAccessToken(user.accessToken);
            kiteConnect.setPublicToken(user.publicToken);

            // Persist in Redis for restart resilience
            tokenCache.saveAccessToken(user.accessToken);

            KiteSessionStore.Session session = new KiteSessionStore.Session(
                    user.accessToken,
                    user.publicToken,
                    user.userId,
                    Instant.now()
            );
            sessionStore.save(session);

            log.info("Kite session established for userId={} at {}",
                    user.userId, session.createdAt());
            return session;

        } catch (Exception ex) {
            throw new KiteAuthException(
                    "Failed to exchange request_token for access_token: " + ex.getMessage(), ex);
        } catch (KiteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns the current session status.
     *
     * @return {@code true} if a valid session is stored
     */
    public boolean isAuthenticated() {
        return sessionStore.isAuthenticated();
    }
}