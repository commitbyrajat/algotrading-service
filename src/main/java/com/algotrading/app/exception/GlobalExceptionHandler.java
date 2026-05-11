package com.algotrading.app.exception;

import com.algotrading.app.auth.KiteAuthException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;

/**
 * Translates domain exceptions into RFC 9457 ProblemDetail responses.
 * Spring Boot 4 / Spring Framework 7 includes ProblemDetail natively.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(KiteAuthException.class)
    public ProblemDetail handleKiteAuth(KiteAuthException ex) {
        log.error("Kite auth error: {}", ex.getMessage(), ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, ex.getMessage());
        pd.setType(URI.create("urn:algotrading:error:kite-auth"));
        pd.setProperty("timestamp", Instant.now().toString());
        pd.setProperty("hint", "Visit GET /kite/login to re-authenticate");
        return pd;
    }

    @ExceptionHandler(StrategyNotFoundException.class)
    public ProblemDetail handleStrategyNotFound(StrategyNotFoundException ex) {
        log.warn("Strategy lookup failed: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(URI.create("urn:algotrading:error:strategy-not-found"));
        pd.setProperty("timestamp", Instant.now().toString());
        return pd;
    }

    @ExceptionHandler(MarketDataException.class)
    public ProblemDetail handleMarketData(MarketDataException ex) {
        log.error("Market data error: {}", ex.getMessage(), ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_GATEWAY, ex.getMessage());
        pd.setType(URI.create("urn:algotrading:error:market-data"));
        pd.setProperty("timestamp", Instant.now().toString());
        return pd;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleBadRequest(IllegalArgumentException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setType(URI.create("urn:algotrading:error:bad-request"));
        pd.setProperty("timestamp", Instant.now().toString());
        return pd;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex) {
        log.error("Unexpected error", ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected internal error occurred");
        pd.setType(URI.create("urn:algotrading:error:internal"));
        pd.setProperty("timestamp", Instant.now().toString());
        return pd;
    }
}