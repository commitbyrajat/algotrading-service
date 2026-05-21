package com.algotrading.app.market;

import com.algotrading.app.exception.MarketDataException;
import com.algotrading.app.model.Candle;
import com.algotrading.app.model.HistoricalDataRequest;
import com.algotrading.app.util.DateTimeUtils;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.HistoricalData;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.function.Supplier;
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
    private static final int MAX_ATTEMPTS = 4;
    private static final Duration INITIAL_RETRY_WAIT = Duration.ofMillis(200);
    private static final double RETRY_MULTIPLIER = 2.0;

    private final KiteConnect kiteConnect;
    private final Retry historicalDataRetry;

    public KiteMarketDataAdapter(KiteConnect kiteConnect) {
        this.kiteConnect = kiteConnect;
        this.historicalDataRetry = Retry.of(
                "kiteHistoricalData",
                RetryConfig.custom()
                        .maxAttempts(MAX_ATTEMPTS)
                        .intervalFunction(IntervalFunction.ofExponentialBackoff(
                                INITIAL_RETRY_WAIT,
                                RETRY_MULTIPLIER
                        ))
                        .retryExceptions(Exception.class)
                        .build()
        );
        this.historicalDataRetry.getEventPublisher()
                .onRetry(event -> log.warn(
                        "Retrying Kite historical data request attempt={} wait={}ms error='{}'",
                        event.getNumberOfRetryAttempts(),
                        event.getWaitInterval().toMillis(),
                        event.getLastThrowable() == null ? null : event.getLastThrowable().getMessage()
                ))
                .onError(event -> log.error(
                        "Kite historical data retries exhausted attempts={} error='{}'",
                        event.getNumberOfRetryAttempts(),
                        event.getLastThrowable() == null ? null : event.getLastThrowable().getMessage()
                ))
                .onSuccess(event -> {
                    if (event.getNumberOfRetryAttempts() > 0) {
                        log.info("Kite historical data request succeeded after retries attempts={}",
                                event.getNumberOfRetryAttempts());
                    }
                });
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

            HistoricalData result = fetchHistoricalDataWithRetry(request, from, to);

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
        } catch (Exception ex) {
            Throwable cause = unwrapKiteRequestException(ex);
            if (cause instanceof KiteException) {
                log.error("Kite historical data lookup failed after retries token={} from={} to={} interval={} error='{}'",
                        request.instrumentToken(),
                        request.from(),
                        request.to(),
                        request.interval(),
                        cause.getMessage(),
                        cause);
            } else {
                log.error("Historical data lookup failed after retries token={} from={} to={} interval={} error='{}'",
                    request.instrumentToken(),
                    request.from(),
                    request.to(),
                    request.interval(),
                    cause.getMessage(),
                    cause);
            }
            throw new MarketDataException(
                    "Failed to fetch historical data from Kite for token: "
                            + request.instrumentToken(), cause);
        }
    }

    private HistoricalData fetchHistoricalDataWithRetry(HistoricalDataRequest request,
                                                        Date from,
                                                        Date to) {
        Supplier<HistoricalData> supplier = Retry.decorateSupplier(
                historicalDataRetry,
                () -> fetchHistoricalData(request, from, to)
        );
        return supplier.get();
    }

    private HistoricalData fetchHistoricalData(HistoricalDataRequest request,
                                               Date from,
                                               Date to) {
        try {
            return kiteConnect.getHistoricalData(
                    from,
                    to,
                    request.instrumentToken(),
                    request.interval(),
                    false,   // continuous – false for equities
                    false    // oi – open interest not needed here
            );
        } catch (KiteException ex) {
            throw new KiteHistoricalDataRequestException(ex);
        } catch (Exception ex) {
            throw new KiteHistoricalDataRequestException(ex);
        }
    }

    private static final class KiteHistoricalDataRequestException extends RuntimeException {
        private KiteHistoricalDataRequestException(Throwable cause) {
            super(cause.getMessage(), cause);
        }
    }

    private Throwable unwrapKiteRequestException(Exception ex) {
        if (ex instanceof KiteHistoricalDataRequestException && ex.getCause() != null) {
            return ex.getCause();
        }
        return ex;
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
