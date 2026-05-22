package com.algotrading.app.portfolio;

import com.algotrading.app.exception.MarketDataException;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.Holding;
import com.zerodhatech.models.MTFHolding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * Kite adapter for long-term equity holdings.
 */
@Component
public class KiteHoldingsAdapter implements HoldingsPort {

    private static final Logger log = LoggerFactory.getLogger(KiteHoldingsAdapter.class);

    private final KiteConnect kiteConnect;

    public KiteHoldingsAdapter(KiteConnect kiteConnect) {
        this.kiteConnect = kiteConnect;
    }

    @Override
    public List<HoldingResponse> listHoldings() {
        log.debug("Fetching Kite portfolio holdings");
        try {
            return kiteConnect.getHoldings().stream()
                    .map(KiteHoldingsAdapter::toHoldingResponse)
                    .sorted(Comparator
                            .comparing(HoldingResponse::exchange, Comparator.nullsLast(String::compareTo))
                            .thenComparing(HoldingResponse::tradingSymbol, Comparator.nullsLast(String::compareTo)))
                    .toList();
        } catch (Exception | KiteException ex) {
            throw new MarketDataException(
                    "Failed to list holdings: " + ex.getMessage(), ex);
        }
    }

    private static HoldingResponse toHoldingResponse(Holding holding) {
        return new HoldingResponse(
                holding.tradingSymbol,
                holding.exchange,
                holding.instrumentToken,
                holding.isin,
                holding.product,
                holding.quantity,
                holding.usedQuantity,
                holding.t1Quantity,
                parseInt(holding.realisedQuantity),
                holding.authorisedQuantity,
                toInstant(holding.authorisedDate),
                parseInt(holding.collateralQuantity),
                holding.collateraltype,
                holding.discrepancy,
                parseDouble(holding.price),
                valueOrZero(holding.averagePrice),
                valueOrZero(holding.lastPrice),
                optionalDoubleField(holding, "closePrice"),
                valueOrZero(holding.pnl),
                holding.dayChange,
                holding.dayChangePercentage,
                toMtfHoldingResponse(holding.mtf)
        );
    }

    private static MtfHoldingResponse toMtfHoldingResponse(MTFHolding mtf) {
        if (mtf == null) {
            return null;
        }
        return new MtfHoldingResponse(
                mtf.quantity,
                mtf.usedQuantity,
                valueOrZero(mtf.averagePrice),
                valueOrZero(mtf.value),
                valueOrZero(mtf.initialMargin)
        );
    }

    private static Instant toInstant(Date date) {
        return date != null ? date.toInstant() : null;
    }

    private static int parseInt(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        return Integer.parseInt(value);
    }

    private static double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return 0.0;
        }
        return Double.parseDouble(value);
    }

    private static double valueOrZero(Double value) {
        return value != null ? value : 0.0;
    }

    private static double optionalDoubleField(Holding holding, String fieldName) {
        try {
            Object value = Holding.class.getField(fieldName).get(holding);
            if (value instanceof Number number) {
                return number.doubleValue();
            }
            if (value instanceof String text) {
                return parseDouble(text);
            }
            return 0.0;
        } catch (NoSuchFieldException ex) {
            return 0.0;
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("Could not read Holding." + fieldName, ex);
        }
    }
}
