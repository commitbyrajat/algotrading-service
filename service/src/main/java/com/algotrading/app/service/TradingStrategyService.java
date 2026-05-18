package com.algotrading.app.service;

import com.algotrading.app.market.MarketDataPort;
import com.algotrading.app.model.Candle;
import com.algotrading.app.model.HistoricalDataRequest;
import com.algotrading.app.model.StrategyDecision;
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

    public TradingStrategyService(StrategyRegistry strategyRegistry,
                                  MarketDataPort marketDataPort) {
        this.strategyRegistry = strategyRegistry;
        this.marketDataPort   = marketDataPort;
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

        log.debug("Fetched {} candles; handing off to strategy '{}'",
                candles.size(), strategyName);

        return strategy.evaluate(candles);
    }

    /**
     * Returns all currently registered strategy names.
     *
     * @return unmodifiable list of strategy names
     */
    public List<String> listStrategies() {
        return strategyRegistry.listNames();
    }
}