package com.algotrading.app.order;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;

/**
 * Request body for exiting an existing long position.
 *
 * <p>The transaction side is intentionally omitted. This request always maps to
 * a Kite {@code SELL} order.</p>
 *
 * @param tradingSymbol NSE/BSE symbol, e.g. {@code INFY}
 * @param exchange      {@code NSE}, {@code BSE}, {@code NFO}, {@code MCX}
 * @param quantity      number of shares/lots to sell (must be &gt; 0)
 * @param orderType     {@code MARKET}, {@code LIMIT}, {@code SL}, {@code SL-M}
 * @param product       {@code CNC} (delivery), {@code MIS} (intraday),
 *                      {@code NRML} (F&amp;O)
 * @param price         limit price; {@code null}/omitted/0 for MARKET orders
 * @param triggerPrice  trigger price for SL / SL-M orders;
 *                      {@code null}/omitted/0 otherwise
 */
@Schema(description = "Exit order details. The service always submits this as a SELL order.")
public record ExitOrderRequest(
        @Schema(description = "Tradable symbol exactly as listed by Kite.", example = "INFY", requiredMode = Schema.RequiredMode.REQUIRED)
        String tradingSymbol,
        @Schema(description = "Kite exchange segment for the symbol.", example = "NSE", allowableValues = {"NSE", "BSE", "NFO", "BFO", "CDS", "MCX"}, requiredMode = Schema.RequiredMode.REQUIRED)
        String exchange,
        @Schema(description = "Number of shares or lots to sell. Must be greater than zero.", example = "10", minimum = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        Integer quantity,
        @Schema(description = "Kite order type. MARKET orders should use price 0.", example = "MARKET", allowableValues = {"MARKET", "LIMIT", "SL", "SL-M"}, requiredMode = Schema.RequiredMode.REQUIRED)
        String orderType,
        @Schema(description = "Kite product type: CNC for delivery, MIS for intraday, NRML for F&O carry-forward.", example = "CNC", allowableValues = {"CNC", "MIS", "NRML"}, requiredMode = Schema.RequiredMode.REQUIRED)
        String product,
        @Schema(description = "Limit price. Use 0 or omit for MARKET orders.", example = "0", minimum = "0")
        Double price,
        @Schema(description = "Trigger price for SL and SL-M orders. Use 0 or omit for non-stop-loss orders.", example = "0", minimum = "0")
        Double triggerPrice
) {
    public ExitOrderRequest {
        Objects.requireNonNull(tradingSymbol, "tradingSymbol must not be null");
        Objects.requireNonNull(exchange, "exchange must not be null");
        Objects.requireNonNull(orderType, "orderType must not be null");
        Objects.requireNonNull(product, "product must not be null");

        if (quantity == null) quantity = 0;
        if (price == null) price = 0.0;
        if (triggerPrice == null) triggerPrice = 0.0;

        if (quantity <= 0) throw new IllegalArgumentException("quantity must be > 0");
        if (price < 0) throw new IllegalArgumentException("price must be >= 0");
        if (triggerPrice < 0) throw new IllegalArgumentException("triggerPrice must be >= 0");
    }

    public OrderRequest toSellOrderRequest() {
        return new OrderRequest(
                tradingSymbol,
                exchange,
                "SELL",
                quantity,
                orderType,
                product,
                price,
                triggerPrice
        );
    }
}
