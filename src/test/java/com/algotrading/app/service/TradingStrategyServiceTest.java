package com.algotrading.app.service;

import com.algotrading.app.market.MarketDataPort;
import com.algotrading.app.model.Candle;
import com.algotrading.app.model.HistoricalDataRequest;
import com.algotrading.app.model.StrategyDecision;
import com.algotrading.app.model.TradingSignal;
import com.algotrading.app.strategy.StrategyRegistry;
import com.algotrading.app.strategy.TechnicalStrategy;
import com.algotrading.app.util.CandleTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link TradingStrategyService}.
 * Uses Mockito to isolate the service from registry and market-data concerns.
 */
@ExtendWith(MockitoExtension.class)
class TradingStrategyServiceTest {

    @Mock private StrategyRegistry  strategyRegistry;
    @Mock private MarketDataPort    marketDataPort;
    @Mock private TechnicalStrategy technicalStrategy;

    private TradingStrategyService service;

    @BeforeEach
    void setUp() {
        service = new TradingStrategyService(strategyRegistry, marketDataPort);
    }

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

        given(strategyRegistry.get("MOCK_STRATEGY")).willReturn(technicalStrategy);
        given(marketDataPort.fetchCandles(any(HistoricalDataRequest.class))).willReturn(candles);
        given(technicalStrategy.evaluate(candles)).willReturn(expected);

        StrategyDecision result = service.evaluate("MOCK_STRATEGY", SAMPLE_REQUEST);

        assertThat(result).isNotNull();
        assertThat(result.signal()).isEqualTo(TradingSignal.BUY);
        assertThat(result.strategyName()).isEqualTo("MOCK_STRATEGY");
    }

    @Test
    void evaluate_callsRegistryWithStrategyName() {
        List<Candle> candles = CandleTestFactory.flat(10, 100.0);
        StrategyDecision stub =
                StrategyDecision.of("SMA_CROSSOVER", TradingSignal.HOLD, "stub");

        given(strategyRegistry.get("SMA_CROSSOVER")).willReturn(technicalStrategy);
        given(marketDataPort.fetchCandles(any())).willReturn(candles);
        given(technicalStrategy.evaluate(candles)).willReturn(stub);

        service.evaluate("SMA_CROSSOVER", SAMPLE_REQUEST);

        verify(strategyRegistry).get("SMA_CROSSOVER");
        verify(marketDataPort).fetchCandles(SAMPLE_REQUEST);
        verify(technicalStrategy).evaluate(candles);
    }

    @Test
    void listStrategies_delegatesToRegistry() {
        given(strategyRegistry.listNames())
                .willReturn(List.of("SMA_CROSSOVER", "RSI_MEAN_REVERSION"));

        List<String> names = service.listStrategies();

        assertThat(names)
                .hasSize(2)
                .containsExactly("SMA_CROSSOVER", "RSI_MEAN_REVERSION");
        verify(strategyRegistry).listNames();
    }
}