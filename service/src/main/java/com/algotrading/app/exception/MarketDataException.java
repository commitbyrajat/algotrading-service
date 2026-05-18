package com.algotrading.app.exception;

/**
 * Thrown when the market data source (Kite) returns an error
 * or the response cannot be parsed into domain objects.
 */
public class MarketDataException extends RuntimeException {

    public MarketDataException(String message) {
        super(message);
    }

    public MarketDataException(String message, Throwable cause) {
        super(message, cause);
    }
}