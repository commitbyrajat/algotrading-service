package com.algotrading.app.order;

import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.models.Order;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KiteOrderAdapterTest {

    @Test
    void listPurchasedOrders_returnsOnlyUnsoldCompletedFilledBuyOrders_sortedNewestFirst() {
        Order olderCompletedBuy = order(
                "order-1",
                "INFY",
                "BUY",
                "COMPLETE",
                "10",
                "10",
                "0",
                "1725.45",
                Instant.parse("2026-05-16T08:15:30Z")
        );
        Order newerCompletedBuy = order(
                "order-2",
                "SBIN",
                "BUY",
                "COMPLETE",
                "2",
                "2",
                "0",
                "825.10",
                Instant.parse("2026-05-16T09:15:30Z")
        );
        Order completedSell = order(
                "order-3",
                "INFY",
                "SELL",
                "COMPLETE",
                "10",
                "10",
                "0",
                "1730.00",
                Instant.parse("2026-05-16T10:15:30Z")
        );
        Order openBuy = order(
                "order-4",
                "TCS",
                "BUY",
                "OPEN",
                "1",
                "0",
                "0",
                "0",
                Instant.parse("2026-05-16T11:15:30Z")
        );
        Order unfilledCompletedBuy = order(
                "order-5",
                "HDFCBANK",
                "BUY",
                "COMPLETE",
                "1",
                "0",
                "0",
                "0",
                Instant.parse("2026-05-16T12:15:30Z")
        );
        KiteOrderAdapter adapter = new KiteOrderAdapter(new FakeKiteConnect(List.of(
                olderCompletedBuy,
                newerCompletedBuy,
                completedSell,
                openBuy,
                unfilledCompletedBuy
        )));

        List<PurchasedOrderResponse> response = adapter.listPurchasedOrders();

        assertThat(response)
                .extracting(PurchasedOrderResponse::orderId)
                .containsExactly("order-2");

        PurchasedOrderResponse first = response.getFirst();
        assertThat(first.tradingSymbol()).isEqualTo("SBIN");
        assertThat(first.exchange()).isEqualTo("NSE");
        assertThat(first.product()).isEqualTo("CNC");
        assertThat(first.orderType()).isEqualTo("MARKET");
        assertThat(first.transactionType()).isEqualTo("BUY");
        assertThat(first.quantity()).isEqualTo(2);
        assertThat(first.filledQuantity()).isEqualTo(2);
        assertThat(first.price()).isZero();
        assertThat(first.averagePrice()).isEqualTo(825.10);
        assertThat(first.status()).isEqualTo("COMPLETE");
        assertThat(first.orderTimestamp()).isEqualTo(Instant.parse("2026-05-16T09:15:30Z"));
    }

    @Test
    void listPurchasedOrders_reducesBuyFilledQuantityWhenLaterSellPartiallyExitsPosition() {
        Order completedBuy = order(
                "order-1",
                "INFY",
                "BUY",
                "COMPLETE",
                "10",
                "10",
                "0",
                "1725.45",
                Instant.parse("2026-05-16T08:15:30Z")
        );
        Order completedSell = order(
                "order-2",
                "INFY",
                "SELL",
                "COMPLETE",
                "4",
                "4",
                "0",
                "1730.00",
                Instant.parse("2026-05-16T10:15:30Z")
        );
        KiteOrderAdapter adapter = new KiteOrderAdapter(new FakeKiteConnect(List.of(
                completedBuy,
                completedSell
        )));

        List<PurchasedOrderResponse> response = adapter.listPurchasedOrders();

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().orderId()).isEqualTo("order-1");
        assertThat(response.getFirst().quantity()).isEqualTo(10);
        assertThat(response.getFirst().filledQuantity()).isEqualTo(6);
    }

    @Test
    void listPurchasedOrders_handlesBlankOptionalNumericFieldsAsZero() {
        Order completedBuy = order(
                "order-1",
                "INFY",
                "BUY",
                "COMPLETE",
                " ",
                "1",
                "",
                null,
                null
        );
        KiteOrderAdapter adapter = new KiteOrderAdapter(new FakeKiteConnect(List.of(completedBuy)));

        List<PurchasedOrderResponse> response = adapter.listPurchasedOrders();

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().quantity()).isZero();
        assertThat(response.getFirst().price()).isZero();
        assertThat(response.getFirst().averagePrice()).isZero();
        assertThat(response.getFirst().orderTimestamp()).isNull();
    }

    private static Order order(
            String orderId,
            String tradingSymbol,
            String transactionType,
            String status,
            String quantity,
            String filledQuantity,
            String price,
            String averagePrice,
            Instant orderTimestamp
    ) {
        Order order = new Order();
        order.orderId = orderId;
        order.tradingSymbol = tradingSymbol;
        order.exchange = "NSE";
        order.product = "CNC";
        order.orderType = "MARKET";
        order.transactionType = transactionType;
        order.status = status;
        order.quantity = quantity;
        order.filledQuantity = filledQuantity;
        order.price = price;
        order.averagePrice = averagePrice;
        order.orderTimestamp = orderTimestamp != null ? Date.from(orderTimestamp) : null;
        return order;
    }

    private static class FakeKiteConnect extends KiteConnect {
        private final List<Order> orders;

        private FakeKiteConnect(List<Order> orders) {
            super("test-api-key");
            this.orders = orders;
        }

        @Override
        public List<Order> getOrders() {
            return orders;
        }
    }
}
