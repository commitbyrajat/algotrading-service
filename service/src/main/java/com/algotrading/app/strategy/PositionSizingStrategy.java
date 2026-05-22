package com.algotrading.app.strategy;

import com.algotrading.app.model.Candle;
import com.algotrading.app.model.QuantitySuggestion;

import java.util.List;

/**
 * Calculates order quantity suggestions from market risk inputs.
 */
public interface PositionSizingStrategy {

    QuantitySuggestion suggestBuyQuantity(List<Candle> candles);
}
