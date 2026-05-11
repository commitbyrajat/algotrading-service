package com.algotrading.app.auth;

/**
 * Thrown when Kite authentication fails — either during session generation
 * or when an API call is made before authentication is complete.
 */
public class KiteAuthException extends RuntimeException {

    public KiteAuthException(String message) {
        super(message);
    }

    public KiteAuthException(String message, Throwable cause) {
        super(message, cause);
    }
}