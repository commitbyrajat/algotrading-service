package com.algotrading.app.service;

import com.algotrading.app.market.MarketDataPort;
import com.algotrading.app.model.Candle;
import com.algotrading.app.model.HistoricalDataRequest;
import com.algotrading.app.model.QuantitySuggestion;
import com.algotrading.app.model.StrategyDecision;
import com.algotrading.app.model.TradingSignal;
import com.algotrading.app.strategy.PositionSizingStrategy;
import com.algotrading.app.strategy.StrategyRegistry;
import com.algotrading.app.strategy.TechnicalStrategy;
import com.algotrading.app.util.CandleTestFactory;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TradingStrategyService}.
 * Uses local fakes to isolate the service from registry and market-data concerns.
 */
class TradingStrategyServiceTest {

    private static final HistoricalDataRequest SAMPLE_REQUEST = new HistoricalDataRequest(
            "256265",
            LocalDate.of(2024, 1, 1),
            LocalDate.of(2024, 6, 30),
            "day"
    );

    @Test
    void evaluate_delegatesCorrectly_andReturnsDecision() {
        List<Candle> candles = CandleTestFactory.flat(10, 100.0);
        StrategyDecision expected =
                StrategyDecision.of("MOCK_STRATEGY", TradingSignal.BUY, "test reason");
        FakeStrategy strategy = new FakeStrategy("MOCK_STRATEGY", expected);
        FakeMarketDataPort marketDataPort = new FakeMarketDataPort(candles);
        TradingStrategyService service = service(marketDataPort, strategy);

        StrategyDecision result = service.evaluate("MOCK_STRATEGY", SAMPLE_REQUEST);

        assertThat(result).isNotNull();
        assertThat(result.signal()).isEqualTo(TradingSignal.BUY);
        assertThat(result.strategyName()).isEqualTo("MOCK_STRATEGY");
        assertThat(result.quantitySuggestion()).isNotNull();
        assertThat(result.quantitySuggestion().suggestedQuantity()).isEqualTo(42);
        assertThat(strategy.evaluatedCandles()).isSameAs(candles);
        assertThat(marketDataPort.fetchCount()).isEqualTo(1);
    }

    @Test
    void evaluate_callsRegistryWithStrategyName() {
        List<Candle> candles = CandleTestFactory.flat(10, 100.0);
        StrategyDecision stub =
                StrategyDecision.of("SMA_CROSSOVER", TradingSignal.HOLD, "stub");
        FakeStrategy strategy = new FakeStrategy("SMA_CROSSOVER", stub);
        FakeMarketDataPort marketDataPort = new FakeMarketDataPort(candles);
        TradingStrategyService service = service(marketDataPort, strategy);

        service.evaluate("SMA_CROSSOVER", SAMPLE_REQUEST);

        assertThat(marketDataPort.lastRequest()).isEqualTo(SAMPLE_REQUEST);
        assertThat(strategy.evaluateCount()).isEqualTo(1);
        assertThat(strategy.evaluatedCandles()).isSameAs(candles);
    }

    @Test
    void evaluateAll_fetchesCandlesOnce_andEvaluatesEveryStrategy() {
        List<Candle> candles = CandleTestFactory.flat(10, 100.0);
        StrategyDecision firstDecision =
                StrategyDecision.of("SMA_CROSSOVER", TradingSignal.HOLD, "first");
        StrategyDecision secondDecision =
                StrategyDecision.of("RSI_MEAN_REVERSION", TradingSignal.BUY, "second");
        FakeStrategy firstStrategy = new FakeStrategy("SMA_CROSSOVER", firstDecision);
        FakeStrategy secondStrategy = new FakeStrategy("RSI_MEAN_REVERSION", secondDecision);
        FakeMarketDataPort marketDataPort = new FakeMarketDataPort(candles);
        TradingStrategyService service = service(marketDataPort, firstStrategy, secondStrategy);

        List<StrategyDecision> decisions = service.evaluateAll(SAMPLE_REQUEST);

        assertThat(decisions)
                .extracting(StrategyDecision::strategyName)
                .containsExactlyInAnyOrder("SMA_CROSSOVER", "RSI_MEAN_REVERSION");
        assertThat(decisions)
                .filteredOn(decision -> decision.signal() == TradingSignal.BUY)
                .singleElement()
                .extracting(StrategyDecision::quantitySuggestion)
                .isNotNull();
        assertThat(marketDataPort.fetchCount()).isEqualTo(1);
        assertThat(firstStrategy.evaluatedCandles()).isSameAs(candles);
        assertThat(secondStrategy.evaluatedCandles()).isSameAs(candles);
    }

    @Test
    void listStrategies_delegatesToRegistry() {
        TradingStrategyService service = service(
                new FakeMarketDataPort(List.of()),
                new FakeStrategy("SMA_CROSSOVER",
                        StrategyDecision.of("SMA_CROSSOVER", TradingSignal.HOLD, "stub")),
                new FakeStrategy("RSI_MEAN_REVERSION",
                        StrategyDecision.of("RSI_MEAN_REVERSION", TradingSignal.HOLD, "stub"))
        );

        List<String> names = service.listStrategies();

        assertThat(names)
                .hasSize(2)
                .containsExactlyInAnyOrder("SMA_CROSSOVER", "RSI_MEAN_REVERSION");
    }

    private static TradingStrategyService service(MarketDataPort marketDataPort,
                                                  TechnicalStrategy... strategies) {
        return new TradingStrategyService(
                new StrategyRegistry(List.of(strategies)),
                marketDataPort,
                new FakePositionSizingStrategy()
        );
    }

    private static final class FakePositionSizingStrategy implements PositionSizingStrategy {
        @Override
        public QuantitySuggestion suggestBuyQuantity(List<Candle> candles) {
            return new QuantitySuggestion(
                    42,
                    "TEST",
                    100.0,
                    2.0,
                    1.5,
                    3.0,
                    100000.0,
                    0.01,
                    1000.0,
                    333,
                    200,
                    "test quantity"
            );
        }
    }

    private static final class FakeMarketDataPort implements MarketDataPort {
        private final List<Candle> candles;
        private HistoricalDataRequest lastRequest;
        private int fetchCount;

        private FakeMarketDataPort(List<Candle> candles) {
            this.candles = candles;
        }

        @Override
        public List<Candle> fetchCandles(HistoricalDataRequest request) {
            this.lastRequest = request;
            this.fetchCount++;
            return candles;
        }

        private HistoricalDataRequest lastRequest() {
            return lastRequest;
        }

        private int fetchCount() {
            return fetchCount;
        }
    }

    private static final class FakeStrategy implements TechnicalStrategy {
        private final String name;
        private final StrategyDecision decision;
        private List<Candle> evaluatedCandles;
        private int evaluateCount;

        private FakeStrategy(String name, StrategyDecision decision) {
            this.name = name;
            this.decision = decision;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public StrategyDecision evaluate(List<Candle> candles) {
            this.evaluatedCandles = candles;
            this.evaluateCount++;
            return decision;
        }

        private List<Candle> evaluatedCandles() {
            return evaluatedCandles;
        }

        private int evaluateCount() {
            return evaluateCount;
        }
    }
}
