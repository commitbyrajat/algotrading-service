package com.algotrading.app.market;

import com.algotrading.app.exception.MarketDataException;
import com.algotrading.app.model.Candle;
import com.algotrading.app.model.HistoricalDataRequest;
import com.algotrading.app.util.DateTimeUtils;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.HistoricalData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Adapter that wraps the Zerodha Kite SDK.
 *
 * <p>This is the <em>only</em> class in the codebase that imports
 * {@code com.zerodhatech.*} types, enforcing the anti-corruption layer.</p>
 *
 * <p>Kite SDK version 4.0.0 – API used:
 * {@code KiteConnect#getHistoricalData(Date, Date, String, String, boolean, boolean)}
 * returns a {@code HistoricalData} wrapper whose {@code dataArrayList}
 * holds individual candles with public fields:
 * {@code timeStamp (Date)}, {@code open}, {@code high}, {@code low},
 * {@code close}, {@code volume} (all double).</p>
 */
@Component
public class KiteMarketDataAdapter implements MarketDataPort {

    private static final Logger log = LoggerFactory.getLogger(KiteMarketDataAdapter.class);

    private final KiteConnect kiteConnect;

    public KiteMarketDataAdapter(KiteConnect kiteConnect) {
        this.kiteConnect = kiteConnect;
    }

    @Override
    public List<Candle> fetchCandles(HistoricalDataRequest request) {
        log.debug("Fetching candles – token={} from={} to={} interval={}",
                request.instrumentToken(), request.from(), request.to(), request.interval());
        try {
            // Convert LocalDate → java.util.Date at UTC midnight
            Date from = Date.from(
                    request.from().atStartOfDay(ZoneOffset.UTC).toInstant());
            Date to = Date.from(
                    request.to().atStartOfDay(ZoneOffset.UTC).toInstant());

            HistoricalData result = kiteConnect.getHistoricalData(
                    from,
                    to,
                    request.instrumentToken(),
                    request.interval(),
                    false,   // continuous – false for equities
                    false    // oi – open interest not needed here
            );

            if (result == null || result.dataArrayList == null) {
                throw new MarketDataException(
                        "Kite returned null response for token: " + request.instrumentToken());
            }

            log.debug("Received {} candles from Kite", result.dataArrayList.size());

            return result.dataArrayList.stream()
                    .map(this::mapToCandle)
                    .collect(Collectors.toList());

        } catch (MarketDataException ex) {
            throw ex;
        } catch (KiteException ex) {
            log.error("Kite historical data lookup failed token={} from={} to={} interval={} error='{}'",
                    request.instrumentToken(),
                    request.from(),
                    request.to(),
                    request.interval(),
                    ex.getMessage(),
                    ex);
            throw new MarketDataException(
                    "Failed to fetch historical data from Kite for token: "
                            + request.instrumentToken(), ex);
        } catch (Exception ex) {
            log.error("Historical data lookup failed token={} from={} to={} interval={} error='{}'",
                    request.instrumentToken(),
                    request.from(),
                    request.to(),
                    request.interval(),
                    ex.getMessage(),
                    ex);
            throw new MarketDataException(
                    "Failed to fetch historical data from Kite for token: "
                            + request.instrumentToken(), ex);
        }
    }

    /**
     * Maps a single Kite SDK inner {@link HistoricalData} item to a domain {@link Candle}.
     * Fields used: timeStamp, open, high, low, close, volume.
     */
    private Candle mapToCandle(HistoricalData item) {
        return new Candle(
                DateTimeUtils.parse(item.timeStamp),
//                Instant.parse(item.timeStamp),   // java.util.Date → java.time.Instant
                item.open,
                item.high,
                item.low,
                item.close,
                (long) item.volume
        );
    }
}
