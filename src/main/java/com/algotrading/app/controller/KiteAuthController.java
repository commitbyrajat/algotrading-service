package com.algotrading.app.controller;

import com.algotrading.app.auth.KiteAuthService;
import com.algotrading.app.auth.KiteSessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Map;

/**
 * Kite Connect authentication endpoints.
 *
 * <pre>
 * GET /api/v1/kite/login-url   → returns login URL (open in browser)
 * GET /api/v1/kite/callback    → Zerodha OAuth redirect target
 * GET /api/v1/kite/status      → check authentication state
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/kite")
public class KiteAuthController {

    private static final Logger log = LoggerFactory.getLogger(KiteAuthController.class);

    private final KiteAuthService kiteAuthService;

    public KiteAuthController(KiteAuthService kiteAuthService) {
        this.kiteAuthService = kiteAuthService;
    }

    /**
     * Returns the Zerodha login URL.
     *
     * <p>Open the returned URL in a browser – Zerodha requires a real browser
     * session.  After login, Zerodha redirects to {@code /api/v1/kite/callback}.</p>
     *
     * <pre>GET /api/v1/kite/login-url</pre>
     */
    @GetMapping("/login-url")
    public ResponseEntity<Map<String, String>> loginUrl() {
        String url = kiteAuthService.getLoginUrl();
        log.info("Login URL requested: {}", url);
        return ResponseEntity.ok(Map.of(
                "loginUrl", url,
                "instruction", "Open this URL in a browser to authenticate with Zerodha"
        ));
    }

    /**
     * OAuth callback – Zerodha redirects here after login.
     *
     * <pre>
     * GET /api/v1/kite/callback
     *     ?request_token=xxxxxxxxxxxxxxxx
     *     &amp;action=login
     *     &amp;status=success
     * </pre>
     *
     * <p>Exchanges the one-time {@code request_token} for an {@code access_token},
     * stores it in Redis (TTL 24 h) and applies it to the live
     * {@code KiteConnect} bean.</p>
     */
    @GetMapping("/callback")
    public ResponseEntity<Map<String, Object>> callback(
            @RequestParam("request_token") String requestToken,
            @RequestParam(value = "status", defaultValue = "unknown") String status,
            @RequestParam(value = "action", defaultValue = "unknown") String action) {

        log.info("Kite OAuth callback: action={} status={}", action, status);

        if (!"success".equalsIgnoreCase(status)) {
            log.warn("Kite login not successful: status={}", status);
            return ResponseEntity.badRequest().body(Map.of(
                    "error",   "Kite login was not successful",
                    "status",  status,
                    "hint",    "Visit GET /api/v1/kite/login-url to retry"
            ));
        }

        if (requestToken == null || requestToken.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "request_token is missing or blank"
            ));
        }

        KiteSessionStore.Session session = kiteAuthService.handleCallback(requestToken);

        return ResponseEntity.ok(Map.of(
                "message",          "Kite session authenticated successfully",
                "userId",           session.userId(),
                "sessionCreatedAt", session.createdAt().toString(),
                "tokenCachedInRedis", true,
                "note", "Token cached in Redis for 24 h. "
                        + "Re-authenticate via GET /api/v1/kite/login-url after expiry."
        ));
    }

    /**
     * Returns the current authentication status.
     *
     * <pre>GET /api/v1/kite/status</pre>
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        boolean auth = kiteAuthService.isAuthenticated();
        if (auth) {
            return ResponseEntity.ok(Map.of(
                    "authenticated", true,
                    "message", "Kite session is active. All endpoints are available."
            ));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "authenticated", false,
                "message",  "No active Kite session.",
                "loginUrl", "/api/v1/kite/login-url"
        ));
    }
}