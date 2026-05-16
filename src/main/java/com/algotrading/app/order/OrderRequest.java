package com.algotrading.app.order;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;

/**
 * Immutable domain request to place a single order.
 *
 * <h3>SAFETY NOTE</h3>
 * Strategy evaluation NEVER automatically places orders.
 * Orders are only placed when this object is explicitly submitted to
 * {@code POST /api/v1/orders}.
 *
 * <h3>Market protection</h3>
 * Handled automatically by {@link KiteOrderAdapter}; callers do not supply it.
 *
 * <h3>Why boxed Double/Integer instead of primitives</h3>
 * Jackson 3 (shipped with Spring Boot 4) throws
 * {@code MismatchedInputException: Cannot map null into type double}
 * when a JSON field is {@code null} or absent and the target type is a
 * primitive ({@code double}, {@code int}).  Using boxed types
 * ({@code Double}, {@code Integer}) allows {@code null} JSON values to
 * deserialise to Java {@code null}, which we then replace with safe defaults
 * in the compact constructor.
 *
 * @param tradingSymbol    NSE/BSE symbol, e.g. {@code INFY}
 * @param exchange         {@code NSE}, {@code BSE}, {@code NFO}, {@code MCX}
 * @param transactionType  {@code BUY} or {@code SELL}
 * @param quantity         number of shares/lots (must be &gt; 0)
 * @param orderType        {@code MARKET}, {@code LIMIT}, {@code SL}, {@code SL-M}
 * @param product          {@code CNC} (delivery), {@code MIS} (intraday),
 *                         {@code NRML} (F&amp;O)
 * @param price            limit price; {@code null}/omitted/0 for MARKET orders
 * @param triggerPrice     trigger price for SL / SL-M orders;
 *                         {@code null}/omitted/0 otherwise
 */
@Schema(description = "Order details required to place one explicit Zerodha Kite order.")
public record OrderRequest(
        @Schema(description = "Tradable symbol exactly as listed by Kite.", example = "INFY", requiredMode = Schema.RequiredMode.REQUIRED)
        String  tradingSymbol,
        @Schema(description = "Kite exchange segment for the symbol.", example = "NSE", allowableValues = {"NSE", "BSE", "NFO", "BFO", "CDS", "MCX"}, requiredMode = Schema.RequiredMode.REQUIRED)
        String  exchange,
        @Schema(description = "Order side.", example = "BUY", allowableValues = {"BUY", "SELL"}, requiredMode = Schema.RequiredMode.REQUIRED)
        String  transactionType,
        @Schema(description = "Number of shares or lots to place. Must be greater than zero.", example = "10", minimum = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        Integer quantity,
        @Schema(description = "Kite order type. MARKET orders should use price 0.", example = "MARKET", allowableValues = {"MARKET", "LIMIT", "SL", "SL-M"}, requiredMode = Schema.RequiredMode.REQUIRED)
        String  orderType,
        @Schema(description = "Kite product type: CNC for delivery, MIS for intraday, NRML for F&O carry-forward.", example = "CNC", allowableValues = {"CNC", "MIS", "NRML"}, requiredMode = Schema.RequiredMode.REQUIRED)
        String  product,
        @Schema(description = "Limit price. Use 0 or omit for MARKET orders.", example = "0", minimum = "0")
        Double  price,
        @Schema(description = "Trigger price for SL and SL-M orders. Use 0 or omit for non-stop-loss orders.", example = "0", minimum = "0")
        Double  triggerPrice
) {
    /**
     * Compact constructor – replaces nulls with safe defaults, then validates.
     */
    public OrderRequest {
        Objects.requireNonNull(tradingSymbol,   "tradingSymbol must not be null");
        Objects.requireNonNull(exchange,        "exchange must not be null");
        Objects.requireNonNull(transactionType, "transactionType must not be null");
        Objects.requireNonNull(orderType,       "orderType must not be null");
        Objects.requireNonNull(product,         "product must not be null");

        // Replace null with safe defaults so callers can omit optional fields
        if (quantity     == null) quantity     = 0;
        if (price        == null) price        = 0.0;
        if (triggerPrice == null) triggerPrice = 0.0;

        if (quantity <= 0)    throw new IllegalArgumentException("quantity must be > 0");
        if (price    < 0)     throw new IllegalArgumentException("price must be >= 0");
        if (triggerPrice < 0) throw new IllegalArgumentException("triggerPrice must be >= 0");
    }
}
