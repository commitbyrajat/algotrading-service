package com.algotrading.app.portfolio;

import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.models.Holding;
import com.zerodhatech.models.MTFHolding;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KiteHoldingsAdapterTest {

    @Test
    void listHoldings_mapsKiteHoldingFields_sortedByExchangeAndSymbol() {
        Holding sbin = holding("SBIN", "NSE", 16, Instant.parse("2026-05-21T18:30:00Z"));
        Holding aaron = holding("AARON", "NSE", 1, Instant.parse("2026-05-20T18:30:00Z"));
        KiteHoldingsAdapter adapter = new KiteHoldingsAdapter(new FakeKiteConnect(List.of(sbin, aaron)));

        List<HoldingResponse> response = adapter.listHoldings();

        assertThat(response)
                .extracting(HoldingResponse::tradingSymbol)
                .containsExactly("AARON", "SBIN");

        HoldingResponse first = response.getFirst();
        assertThat(first.exchange()).isEqualTo("NSE");
        assertThat(first.instrumentToken()).isEqualTo("779521");
        assertThat(first.isin()).isEqualTo("INE062A01020");
        assertThat(first.product()).isEqualTo("CNC");
        assertThat(first.quantity()).isEqualTo(1);
        assertThat(first.usedQuantity()).isEqualTo(2);
        assertThat(first.t1Quantity()).isEqualTo(3);
        assertThat(first.realisedQuantity()).isEqualTo(4);
        assertThat(first.authorisedQuantity()).isEqualTo(5);
        assertThat(first.authorisedAt()).isEqualTo(Instant.parse("2026-05-20T18:30:00Z"));
        assertThat(first.collateralQuantity()).isEqualTo(6);
        assertThat(first.collateralType()).isEqualTo("");
        assertThat(first.discrepancy()).isFalse();
        assertThat(first.price()).isZero();
        assertThat(first.averagePrice()).isEqualTo(801.78);
        assertThat(first.lastPrice()).isEqualTo(762.45);
        assertThat(first.pnl()).isEqualTo(-629.30);
        assertThat(first.dayChange()).isEqualTo(-3.95);
        assertThat(first.dayChangePercentage()).isEqualTo(-0.52);
        assertThat(first.mtf()).isNotNull();
        assertThat(first.mtf().quantity()).isEqualTo(7);
        assertThat(first.mtf().usedQuantity()).isEqualTo(8);
        assertThat(first.mtf().averagePrice()).isEqualTo(100.25);
        assertThat(first.mtf().value()).isEqualTo(1000.0);
        assertThat(first.mtf().initialMargin()).isEqualTo(250.0);
    }

    @Test
    void listHoldings_handlesBlankOptionalNumericStringsAsZero() {
        Holding holding = holding("INFY", "NSE", 10, null);
        holding.price = "";
        holding.realisedQuantity = " ";
        holding.collateralQuantity = null;
        holding.mtf = null;
        KiteHoldingsAdapter adapter = new KiteHoldingsAdapter(new FakeKiteConnect(List.of(holding)));

        List<HoldingResponse> response = adapter.listHoldings();

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().price()).isZero();
        assertThat(response.getFirst().realisedQuantity()).isZero();
        assertThat(response.getFirst().collateralQuantity()).isZero();
        assertThat(response.getFirst().mtf()).isNull();
    }

    private static Holding holding(String tradingSymbol, String exchange, int quantity, Instant authorisedAt) {
        Holding holding = new Holding();
        holding.tradingSymbol = tradingSymbol;
        holding.exchange = exchange;
        holding.instrumentToken = "779521";
        holding.isin = "INE062A01020";
        holding.product = "CNC";
        holding.quantity = quantity;
        holding.usedQuantity = 2;
        holding.t1Quantity = 3;
        holding.realisedQuantity = "4";
        holding.authorisedQuantity = 5;
        holding.authorisedDate = authorisedAt != null ? Date.from(authorisedAt) : null;
        holding.collateralQuantity = "6";
        holding.collateraltype = "";
        holding.discrepancy = false;
        holding.price = "0";
        holding.averagePrice = 801.78;
        holding.lastPrice = 762.45;
        holding.pnl = -629.30;
        holding.dayChange = -3.95;
        holding.dayChangePercentage = -0.52;

        MTFHolding mtf = new MTFHolding();
        mtf.quantity = 7;
        mtf.usedQuantity = 8;
        mtf.averagePrice = 100.25;
        mtf.value = 1000.0;
        mtf.initialMargin = 250.0;
        holding.mtf = mtf;
        return holding;
    }

    private static class FakeKiteConnect extends KiteConnect {
        private final List<Holding> holdings;

        private FakeKiteConnect(List<Holding> holdings) {
            super("test-api-key");
            this.holdings = holdings;
        }

        @Override
        public List<Holding> getHoldings() {
            return holdings;
        }
    }
}
