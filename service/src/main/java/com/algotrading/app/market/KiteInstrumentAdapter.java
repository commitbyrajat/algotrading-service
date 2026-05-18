package com.algotrading.app.market;

import com.algotrading.app.exception.MarketDataException;
import com.algotrading.app.model.InstrumentResponse;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.Instrument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Adapter for Kite's gzipped CSV instrument dump.
 */
@Component
public class KiteInstrumentAdapter implements InstrumentPort {

    private static final Logger log = LoggerFactory.getLogger(KiteInstrumentAdapter.class);

    private final KiteConnect kiteConnect;

    public KiteInstrumentAdapter(KiteConnect kiteConnect) {
        this.kiteConnect = kiteConnect;
    }

    @Override
    public List<InstrumentResponse> fetchInstruments(Optional<String> exchange) {
        String normalizedExchange = exchange
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(String::toUpperCase)
                .orElse(null);

        try {
            List<Instrument> instruments = normalizedExchange == null
                    ? kiteConnect.getInstruments()
                    : kiteConnect.getInstruments(normalizedExchange);

            if (instruments == null) {
                throw new MarketDataException("Kite returned null instrument list");
            }

            log.debug("Received {} instruments from Kite for exchange={}",
                    instruments.size(), normalizedExchange == null ? "ALL" : normalizedExchange);

            return instruments.stream()
                    .map(this::mapToResponse)
                    .toList();
        } catch (MarketDataException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new MarketDataException("Failed to fetch instrument list from Kite", ex);
        } catch (KiteException ex) {
            throw new MarketDataException("Kite rejected instrument list request: " + ex.message, ex);
        }
    }

    private InstrumentResponse mapToResponse(Instrument instrument) {
        return new InstrumentResponse(
                instrument.instrument_token,
                instrument.exchange_token,
                instrument.tradingsymbol,
                instrument.name,
                instrument.last_price,
                toLocalDate(instrument.expiry),
                instrument.strike,
                instrument.tick_size,
                instrument.lot_size,
                instrument.instrument_type,
                instrument.segment,
                instrument.exchange
        );
    }

    private LocalDate toLocalDate(Date date) {
        if (date == null) {
            return null;
        }
        return date.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
    }
}
