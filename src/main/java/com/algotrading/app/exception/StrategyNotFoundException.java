package com.algotrading.app.exception;

/**
 * Thrown when a caller requests a strategy by name that is not registered.
 */
public class StrategyNotFoundException extends RuntimeException {

    public StrategyNotFoundException(String strategyName) {
        super("No strategy registered with name: '" + strategyName + "'");
    }
}