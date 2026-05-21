package com.algotrading.app.service;

import com.algotrading.app.market.InstrumentPort;
import com.algotrading.app.model.InstrumentResponse;
import com.algotrading.app.exception.InstrumentNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InstrumentServiceTest {

    @Mock private InstrumentPort instrumentPort;
    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valueOperations;

    private ObjectMapper objectMapper;
    private InstrumentService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new InstrumentService(instrumentPort, redis, objectMapper, false);

        lenient().when(redis.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void listInstruments_returnsCachedExchangeListWithoutCallingKite() throws Exception {
        List<InstrumentResponse> cached = List.of(sampleInstrument());
        given(valueOperations.get("kite:instruments:NSE"))
                .willReturn(objectMapper.writeValueAsString(cached));

        List<InstrumentResponse> result = service.listInstruments(Optional.of(" nse "));

        assertThat(result).containsExactlyElementsOf(cached);
        verify(instrumentPort, never()).fetchInstruments(Optional.of("NSE"));
    }

    @Test
    void listInstruments_lazyLoadsAndCachesOnMiss() {
        List<InstrumentResponse> fetched = List.of(sampleInstrument());
        given(valueOperations.get("kite:instruments:NSE")).willReturn(null);
        given(instrumentPort.fetchInstruments(Optional.of("NSE"))).willReturn(fetched);

        List<InstrumentResponse> result = service.listInstruments(Optional.of("NSE"));

        assertThat(result).containsExactlyElementsOf(fetched);
        verify(instrumentPort).fetchInstruments(Optional.of("NSE"));
        verify(valueOperations).set(eq("kite:instruments:NSE"), anyString(), eq(Duration.ofHours(24)));
        verify(valueOperations).set(eq("kite:instruments:by-symbol:NSE"), anyString(), eq(Duration.ofHours(24)));
        verify(valueOperations).set(eq("kite:instruments:by-exchange-token:NSE"), anyString(), eq(Duration.ofHours(24)));
    }

    @Test
    void listInstruments_fetchesFromKiteWhenRedisReadFails() {
        List<InstrumentResponse> fetched = List.of(sampleInstrument());
        given(valueOperations.get("kite:instruments:ALL")).willThrow(new RuntimeException("redis down"));
        given(instrumentPort.fetchInstruments(Optional.empty())).willReturn(fetched);

        List<InstrumentResponse> result = service.listInstruments(Optional.empty());

        assertThat(result).containsExactlyElementsOf(fetched);
        verify(instrumentPort).fetchInstruments(Optional.empty());
    }

    @Test
    void listInstrumentsByTradingSymbols_filtersExchangeListIgnoringCaseAndBlanks() {
        InstrumentResponse infy = sampleInstrument("INFY", "INFOSYS", "NSE");
        InstrumentResponse tcs = sampleInstrument("TCS", "TATA CONSULTANCY SERVICES", "NSE");
        InstrumentResponse reliance = sampleInstrument("RELIANCE", "RELIANCE INDUSTRIES", "NSE");
        List<InstrumentResponse> fetched = List.of(infy, tcs, reliance);
        given(valueOperations.get("kite:instruments:NSE")).willReturn(null);
        given(instrumentPort.fetchInstruments(Optional.of("NSE"))).willReturn(fetched);

        List<InstrumentResponse> result = service.listInstrumentsByTradingSymbols(
                Optional.of("nse"),
                List.of(" infy ", "TCS", "", "infy")
        );

        assertThat(result).containsExactly(infy, tcs);
        verify(instrumentPort).fetchInstruments(Optional.of("NSE"));
    }

    @Test
    void listInstrumentsByTradingSymbols_returnsEmptyListForBlankSymbolsWithoutCallingKite() {
        List<InstrumentResponse> result = service.listInstrumentsByTradingSymbols(
                Optional.empty(),
                List.of(" ", "")
        );

        assertThat(result).isEmpty();
        verify(instrumentPort, never()).fetchInstruments(Optional.empty());
    }

    @Test
    void listInstrumentsByIdentifiers_returnsSymbolMatchBeforeExchangeTokenMatch() throws Exception {
        InstrumentResponse symbolMatch = sampleInstrument("1594", "SYMBOL NAMED LIKE TOKEN", "NSE", 9999);
        InstrumentResponse tokenMatch = sampleInstrument("INFY", "INFOSYS", "NSE", 1594);

        given(valueOperations.get("kite:instruments:NSE"))
                .willReturn(objectMapper.writeValueAsString(List.of(symbolMatch, tokenMatch)));
        given(valueOperations.get("kite:instruments:by-symbol:NSE"))
                .willReturn(objectMapper.writeValueAsString(java.util.Map.of(
                        "1594", symbolMatch,
                        "INFY", tokenMatch
                )));
        given(valueOperations.get("kite:instruments:by-exchange-token:NSE"))
                .willReturn(objectMapper.writeValueAsString(java.util.Map.of(
                        "1594", tokenMatch,
                        "9999", symbolMatch
                )));

        List<InstrumentResponse> result = service.listInstrumentsByIdentifiers(
                Optional.of("nse"),
                List.of("1594")
        );

        assertThat(result).containsExactly(symbolMatch);
        verify(instrumentPort, never()).fetchInstruments(Optional.of("NSE"));
    }

    @Test
    void listInstrumentsByIdentifiers_fallsBackToExchangeTokenCache() throws Exception {
        InstrumentResponse infy = sampleInstrument("INFY", "INFOSYS", "NSE", 1594);

        given(valueOperations.get("kite:instruments:NSE"))
                .willReturn(objectMapper.writeValueAsString(List.of(infy)));
        given(valueOperations.get("kite:instruments:by-symbol:NSE"))
                .willReturn(objectMapper.writeValueAsString(java.util.Map.of("INFY", infy)));
        given(valueOperations.get("kite:instruments:by-exchange-token:NSE"))
                .willReturn(objectMapper.writeValueAsString(java.util.Map.of("1594", infy)));

        List<InstrumentResponse> result = service.listInstrumentsByIdentifiers(
                Optional.of("NSE"),
                List.of("1594")
        );

        assertThat(result).containsExactly(infy);
    }

    @Test
    void listInstruments_cachesAndReturnsNullExpiryInstruments() {
        InstrumentResponse cashInstrument = sampleInstrument("INFY", "INFOSYS", "NSE", 1594, null);
        InstrumentResponse derivativeInstrument = sampleInstrument("NIFTY26JUNFUT", "NIFTY", "NFO", 35001);

        given(valueOperations.get("kite:instruments:NFO")).willReturn(null);
        given(instrumentPort.fetchInstruments(Optional.of("NFO")))
                .willReturn(List.of(cashInstrument, derivativeInstrument));

        List<InstrumentResponse> result = service.listInstruments(Optional.of("NFO"));

        assertThat(result).containsExactly(cashInstrument, derivativeInstrument);
        verify(valueOperations).set(eq("kite:instruments:NFO"), anyString(), eq(Duration.ofHours(24)));
        verify(valueOperations).set(eq("kite:instruments:by-symbol:NFO"), anyString(), eq(Duration.ofHours(24)));
        verify(valueOperations).set(eq("kite:instruments:by-exchange-token:NFO"), anyString(), eq(Duration.ofHours(24)));
    }

    @Test
    void listInstrumentsByIdentifiers_returnsNullExpiryMatchWhenExpiryRequirementDisabled() {
        InstrumentResponse cashInstrument = sampleInstrument("INFY", "INFOSYS", "NSE", 1594, null);

        given(valueOperations.get("kite:instruments:NSE")).willReturn(null);
        given(instrumentPort.fetchInstruments(Optional.of("NSE")))
                .willReturn(List.of(cashInstrument));

        List<InstrumentResponse> result = service.listInstrumentsByIdentifiers(
                Optional.of("NSE"),
                List.of("INFY", "1594")
        );

        assertThat(result).containsExactly(cashInstrument, cashInstrument);
    }

    @Test
    void listInstrumentsByIdentifiers_throwsNotFoundWhenCachedIndexHitHasNullExpiryAndFeatureEnabled() throws Exception {
        service = new InstrumentService(instrumentPort, redis, objectMapper, true);
        InstrumentResponse cashInstrument = sampleInstrument("INFY", "INFOSYS", "NSE", 1594, null);

        given(valueOperations.get("kite:instruments:NSE"))
                .willReturn(objectMapper.writeValueAsString(List.of(cashInstrument)));
        given(valueOperations.get("kite:instruments:by-symbol:NSE"))
                .willReturn(objectMapper.writeValueAsString(java.util.Map.of("INFY", cashInstrument)));
        given(valueOperations.get("kite:instruments:by-exchange-token:NSE"))
                .willReturn(objectMapper.writeValueAsString(java.util.Map.of("1594", cashInstrument)));

        assertThatThrownBy(() -> service.listInstrumentsByIdentifiers(
                Optional.of("NSE"),
                List.of("INFY")
        )).isInstanceOf(InstrumentNotFoundException.class)
                .hasMessageContaining("INFY");
    }

    private InstrumentResponse sampleInstrument() {
        return sampleInstrument("INFY", "INFOSYS", "NSE");
    }

    private InstrumentResponse sampleInstrument(String tradingSymbol, String name, String exchange) {
        return sampleInstrument(tradingSymbol, name, exchange, 1594);
    }

    private InstrumentResponse sampleInstrument(String tradingSymbol, String name, String exchange, long exchangeToken) {
        return sampleInstrument(tradingSymbol, name, exchange, exchangeToken, LocalDate.of(2026, 6, 25));
    }

    private InstrumentResponse sampleInstrument(String tradingSymbol,
                                                String name,
                                                String exchange,
                                                long exchangeToken,
                                                LocalDate expiry) {
        return new InstrumentResponse(
                408065,
                exchangeToken,
                tradingSymbol,
                name,
                0.0,
                expiry,
                null,
                0.05,
                1,
                "EQ",
                exchange,
                exchange
        );
    }
}
