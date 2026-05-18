package com.algotrading.app.order;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderServiceTest {

    private CapturingOrderPort orderPort;
    private OrderService service;

    @BeforeEach
    void setUp() {
        orderPort = new CapturingOrderPort();
        service = new OrderService(orderPort);
    }

    @Test
    void exitPosition_placesSellOrder() {
        ExitOrderRequest request = new ExitOrderRequest(
                "INFY",
                "NSE",
                5,
                "MARKET",
                "CNC",
                null,
                null
        );

        PlacedOrderResponse response = service.exitPosition(request);

        OrderRequest sellOrder = orderPort.lastRequest;

        assertThat(sellOrder.tradingSymbol()).isEqualTo("INFY");
        assertThat(sellOrder.exchange()).isEqualTo("NSE");
        assertThat(sellOrder.transactionType()).isEqualTo("SELL");
        assertThat(sellOrder.quantity()).isEqualTo(5);
        assertThat(sellOrder.orderType()).isEqualTo("MARKET");
        assertThat(sellOrder.product()).isEqualTo("CNC");
        assertThat(sellOrder.price()).isZero();
        assertThat(sellOrder.triggerPrice()).isZero();

        assertThat(response.orderId()).isEqualTo("250101000001234");
        assertThat(response.transactionType()).isEqualTo("SELL");
    }

    @Test
    void exitOrderRequest_defaultsOptionalPricesToZero() {
        ExitOrderRequest request = new ExitOrderRequest(
                "SBIN",
                "NSE",
                1,
                "LIMIT",
                "CNC",
                null,
                null
        );

        OrderRequest sellOrder = request.toSellOrderRequest();

        assertThat(sellOrder.transactionType()).isEqualTo("SELL");
        assertThat(sellOrder.price()).isZero();
        assertThat(sellOrder.triggerPrice()).isZero();
    }

    @Test
    void listPurchasedOrders_delegatesToOrderPort() {
        orderPort.purchasedOrders = List.of(new PurchasedOrderResponse(
                "250101000001234",
                "INFY",
                "NSE",
                "CNC",
                "MARKET",
                "BUY",
                1,
                1,
                0.0,
                1725.45,
                "COMPLETE",
                null
        ));

        List<PurchasedOrderResponse> response = service.listPurchasedOrders();

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().orderId()).isEqualTo("250101000001234");
        assertThat(response.getFirst().transactionType()).isEqualTo("BUY");
        assertThat(response.getFirst().status()).isEqualTo("COMPLETE");
    }

    private static class CapturingOrderPort implements OrderPort {
        private OrderRequest lastRequest;
        private List<PurchasedOrderResponse> purchasedOrders = List.of();

        @Override
        public PlacedOrderResponse placeOrder(OrderRequest request) {
            lastRequest = request;
            return PlacedOrderResponse.of("250101000001234", request);
        }

        @Override
        public List<PurchasedOrderResponse> listPurchasedOrders() {
            return purchasedOrders;
        }

        @Override
        public OrderStatusResponse getOrderStatus(String orderId) {
            throw new UnsupportedOperationException("status lookup is not used by these tests");
        }
    }
}
