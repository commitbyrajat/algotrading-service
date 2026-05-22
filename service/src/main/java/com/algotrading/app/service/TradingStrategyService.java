package com.algotrading.app.service;

import com.algotrading.app.market.MarketDataPort;
import com.algotrading.app.model.Candle;
import com.algotrading.app.model.HistoricalDataRequest;
import com.algotrading.app.model.StrategyDecision;
import com.algotrading.app.model.TradingSignal;
import com.algotrading.app.strategy.PositionSizingStrategy;
import com.algotrading.app.strategy.StrategyRegistry;
import com.algotrading.app.strategy.TechnicalStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Application service that coordinates market data retrieval and strategy evaluation.
 */
@Service
public class TradingStrategyService {

    private static final Logger log = LoggerFactory.getLogger(TradingStrategyService.class);

    private final StrategyRegistry strategyRegistry;
    private final MarketDataPort marketDataPort;
    private final PositionSizingStrategy positionSizingStrategy;

    public TradingStrategyService(StrategyRegistry strategyRegistry,
                                  MarketDataPort marketDataPort,
                                  PositionSizingStrategy positionSizingStrategy) {
        this.strategyRegistry = strategyRegistry;
        this.marketDataPort   = marketDataPort;
        this.positionSizingStrategy = positionSizingStrategy;
    }

    /**
     * Fetch historical candles and evaluate the named strategy.
     *
     * @param strategyName case-sensitive name as registered in {@link StrategyRegistry}
     * @param request      parameters for the historical data query
     * @return the strategy's decision for the fetched candle window
     */
    public StrategyDecision evaluate(String strategyName, HistoricalDataRequest request) {
        log.info("Evaluating strategy='{}' token='{}' from={} to={} interval={}",
                strategyName,
                request.instrumentToken(),
                request.from(),
                request.to(),
                request.interval());

        TechnicalStrategy strategy = strategyRegistry.get(strategyName);
        List<Candle> candles = marketDataPort.fetchCandles(request);

        log.debug("Fetched candles count={} strategy='{}'",
                candles.size(), strategyName);

        StrategyDecision decision = withBuyQuantitySuggestion(strategy.evaluate(candles), candles);
        log.info("Strategy evaluated strategy='{}' signal={} candles={} suggestedQuantity={}",
                strategyName,
                decision.signal(),
                candles.size(),
                decision.quantitySuggestion() != null
                        ? decision.quantitySuggestion().suggestedQuantity()
                        : null);
        return decision;
    }

    /**
     * Fetch historical candles once and evaluate every registered strategy.
     *
     * @param request parameters for the historical data query
     * @return one decision per registered strategy
     */
    public List<StrategyDecision> evaluateAll(HistoricalDataRequest request) {
        List<String> strategyNames = strategyRegistry.listNames();
        log.info("Evaluating all strategies count={} token='{}' from={} to={} interval={}",
                strategyNames.size(),
                request.instrumentToken(),
                request.from(),
                request.to(),
                request.interval());

        List<Candle> candles = marketDataPort.fetchCandles(request);
        log.debug("Fetched candles count={} strategies={}", candles.size(), strategyNames);

        List<StrategyDecision> decisions = strategyNames.stream()
                .map(strategyName -> evaluateFetchedCandles(strategyName, candles))
                .toList();

        log.info("All strategies evaluated count={} candles={}",
                decisions.size(),
                candles.size());
        return decisions;
    }

    /**
     * Returns all currently registered strategy names.
     *
     * @return unmodifiable list of strategy names
     */
    public List<String> listStrategies() {
        return strategyRegistry.listNames();
    }

    private StrategyDecision evaluateFetchedCandles(String strategyName, List<Candle> candles) {
        log.debug("Handing fetched candles to strategy='{}'", strategyName);
        try {
            StrategyDecision decision = withBuyQuantitySuggestion(
                    strategyRegistry.get(strategyName).evaluate(candles),
                    candles
            );
            log.info("Strategy evaluated strategy='{}' signal={} suggestedQuantity={}",
                    strategyName,
                    decision.signal(),
                    decision.quantitySuggestion() != null
                            ? decision.quantitySuggestion().suggestedQuantity()
                            : null);
            return decision;
        } catch (RuntimeException ex) {
            log.error("Strategy evaluation failed strategy='{}' candles={} error='{}'",
                    strategyName,
                    candles.size(),
                    ex.getMessage(),
                    ex);
            throw ex;
        }
    }

    private StrategyDecision withBuyQuantitySuggestion(StrategyDecision decision, List<Candle> candles) {
        if (decision.signal() != TradingSignal.BUY) {
            return decision;
        }
        return decision.withQuantitySuggestion(positionSizingStrategy.suggestBuyQuantity(candles));
    }
}
