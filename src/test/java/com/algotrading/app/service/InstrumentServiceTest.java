package com.algotrading.app.service;

import com.algotrading.app.market.InstrumentPort;
import com.algotrading.app.model.InstrumentResponse;
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
        service = new InstrumentService(instrumentPort, redis, objectMapper);

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

    private InstrumentResponse sampleInstrument() {
        return sampleInstrument("INFY", "INFOSYS", "NSE");
    }

    private InstrumentResponse sampleInstrument(String tradingSymbol, String name, String exchange) {
        return new InstrumentResponse(
                408065,
                1594,
                tradingSymbol,
                name,
                0.0,
                LocalDate.of(2026, 6, 25),
                null,
                0.05,
                1,
                "EQ",
                exchange,
                exchange
        );
    }
}
