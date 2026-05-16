package com.algotrading.app.controller;

import com.algotrading.app.auth.KiteAuthService;
import com.algotrading.app.auth.KiteSessionStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
@Tag(
        name = "Kite Authentication",
        description = "Zerodha Kite OAuth endpoints used to create and verify the session required by market-data and order APIs."
)
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
    @Operation(
            summary = "Get Zerodha Kite login URL",
            description = """
                    Returns the Zerodha Kite login URL. Open this URL in a browser, complete login,
                    and Zerodha will redirect back to the configured callback endpoint with a one-time request token.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login URL generated successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Map.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "loginUrl": "https://kite.zerodha.com/connect/login?...",
                                      "instruction": "Open this URL in a browser to authenticate with Zerodha"
                                    }
                                    """))),
            @ApiResponse(responseCode = "500", description = "Unexpected error while building login URL",
                    content = @Content)
    })
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
    @Operation(
            summary = "Handle Zerodha Kite OAuth callback",
            description = """
                    Callback endpoint invoked by Zerodha after browser login. When status is success,
                    the endpoint exchanges request_token for an access token, caches the session in Redis,
                    and applies it to the live Kite client used by the service.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Kite session authenticated and cached",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Map.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "message": "Kite session authenticated successfully",
                                      "userId": "AB1234",
                                      "sessionCreatedAt": "2026-05-16T10:15:30Z",
                                      "tokenCachedInRedis": true,
                                      "note": "Token cached in Redis for 24 h. Re-authenticate via GET /api/v1/kite/login-url after expiry."
                                    }
                                    """))),
            @ApiResponse(responseCode = "400", description = "Login failed or request_token is missing",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "401", description = "Kite rejected the request token",
                    content = @Content),
            @ApiResponse(responseCode = "500", description = "Unexpected authentication error",
                    content = @Content)
    })
    public ResponseEntity<Map<String, Object>> callback(
            @Parameter(
                    description = "One-time request token returned by Zerodha after successful login.",
                    required = true,
                    example = "u7L6H7x6aYf3eZ9pQw2R"
            )
            @RequestParam("request_token") String requestToken,
            @Parameter(
                    description = "Login status returned by Zerodha. Expected value is success.",
                    example = "success"
            )
            @RequestParam(value = "status", defaultValue = "unknown") String status,
            @Parameter(
                    description = "Callback action returned by Zerodha. Usually login.",
                    example = "login"
            )
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
    @Operation(
            summary = "Check Kite session status",
            description = "Returns whether the service currently has an active Kite session available for protected Kite operations."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Kite session is active",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Map.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "authenticated": true,
                                      "message": "Kite session is active. All endpoints are available."
                                    }
                                    """))),
            @ApiResponse(responseCode = "401", description = "No active Kite session",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Map.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "authenticated": false,
                                      "message": "No active Kite session.",
                                      "loginUrl": "/api/v1/kite/login-url"
                                    }
                                    """)))
    })
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
