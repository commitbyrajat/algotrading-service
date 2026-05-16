# Controller Endpoints and Strategy Behavior

This document explains every controller endpoint, what each method accepts, what it returns, and why it exists. It also explains what the strategies are trying to achieve and how they work.

## Authentication Controller

Class: `KiteAuthController`  
Base path: `/api/v1/kite`

The authentication controller exists because Zerodha Kite requires a browser-based login before the service can call historical-data or order APIs.

### Get Login URL

Method:

```http
GET /api/v1/kite/login-url
```

Controller method:

```java
loginUrl()
```

Purpose:

Returns the Zerodha login URL that the user must open in a browser. This starts the Kite authentication flow.

Input:

No request body and no query parameters.

Output:

HTTP `200 OK`

```json
{
  "loginUrl": "https://kite.zerodha.com/connect/login?v=3&api_key=...",
  "instruction": "Open this URL in a browser to authenticate with Zerodha"
}
```

What happens internally:

- Calls `KiteAuthService.getLoginUrl()`.
- The login URL comes from the shared `KiteConnect` bean.
- The user opens that URL and completes Zerodha login in a browser.

### Kite Callback

Method:

```http
GET /api/v1/kite/callback?request_token=...&status=success&action=login
```

Controller method:

```java
callback(requestToken, status, action)
```

Purpose:

Receives Zerodha's redirect after browser login. It exchanges the one-time `request_token` for an access token and stores that token for future Kite API calls.

Input:

Query parameters:

| Name | Required | Example | Purpose |
|---|---|---|---|
| `request_token` | Yes | `abc123...` | One-time token returned by Zerodha after login |
| `status` | No | `success` | Login status from Zerodha |
| `action` | No | `login` | Action returned by Zerodha |

Success output:

HTTP `200 OK`

```json
{
  "message": "Kite session authenticated successfully",
  "userId": "AB1234",
  "sessionCreatedAt": "2026-05-11T16:55:00Z",
  "tokenCachedInRedis": true,
  "note": "Token cached in Redis for 24 h. Re-authenticate via GET /api/v1/kite/login-url after expiry."
}
```

Failure output:

If `status` is not `success`, it returns HTTP `400 Bad Request` with an error payload.

What happens internally:

- Validates that Zerodha login succeeded.
- Calls `KiteAuthService.handleCallback(requestToken)`.
- `KiteAuthService` calls `KiteConnect.generateSession`.
- The returned access token is set on the shared `KiteConnect` bean.
- The access token is cached in Redis.
- The session is saved in `KiteSessionStore`.

### Check Auth Status

Method:

```http
GET /api/v1/kite/status
```

Controller method:

```java
status()
```

Purpose:

Checks whether the service currently has an active Kite session.

Input:

No request body and no query parameters.

Authenticated output:

HTTP `200 OK`

```json
{
  "authenticated": true,
  "message": "Kite session is active. All endpoints are available."
}
```

Unauthenticated output:

HTTP `401 Unauthorized`

```json
{
  "authenticated": false,
  "message": "No active Kite session.",
  "loginUrl": "/api/v1/kite/login-url"
}
```

What happens internally:

- Calls `KiteAuthService.isAuthenticated()`.
- That checks whether `KiteSessionStore` currently holds a session.

## Instrument Controller

Class: `InstrumentController`  
Base path: `/api/v1/instruments`

The instrument controller retrieves Zerodha Kite's instrument master. The official API returns a large gzipped CSV dump from `GET /instruments`; this service exposes the parsed rows as JSON.

Official reference: `https://kite.trade/docs/connect/v3/market-data-and-instruments/`

### List Instruments

Method:

```http
GET /api/v1/instruments
```

Controller method:

```java
listInstruments(exchange)
```

Purpose:

Returns the full list of tradable instruments across exchanges.

Input:

Optional query parameters:

| Name | Required | Example | Purpose |
|---|---|---|---|
| `exchange` | No | `NSE` | Restricts the list to one exchange |

Output:

HTTP `200 OK`

```json
[
  {
    "instrumentToken": 408065,
    "exchangeToken": 1594,
    "tradingSymbol": "INFY",
    "name": "INFOSYS",
    "lastPrice": 0.0,
    "expiry": null,
    "strike": null,
    "tickSize": 0.05,
    "lotSize": 1,
    "instrumentType": "EQ",
    "segment": "NSE",
    "exchange": "NSE"
  }
]
```

What happens internally:

- Calls `InstrumentService.listInstruments(exchange)`.
- The service delegates to `InstrumentPort`.
- `KiteInstrumentAdapter` calls `KiteConnect#getInstruments()` or `KiteConnect#getInstruments(exchange)`.
- Zerodha SDK types are mapped to `InstrumentResponse` before returning from the controller.

## Strategy Controller

Class: `StrategyController`  
Base path: `/api/v1/strategies`

The strategy controller exists to list and evaluate trading strategies. It does not place orders.

### List Strategies

Method:

```http
GET /api/v1/strategies
```

Controller method:

```java
listStrategies()
```

Purpose:

Returns all registered strategy names.

Input:

No request body and no query parameters.

Output:

HTTP `200 OK`

```json
[
  "SMA_CROSSOVER",
  "RSI_MEAN_REVERSION",
  "GAINZ_ALPHA_V2"
]
```

What happens internally:

- Calls `TradingStrategyService.listStrategies()`.
- The service delegates to `StrategyRegistry.listNames()`.
- `StrategyRegistry` knows all Spring beans that implement `TechnicalStrategy`.

### Evaluate Strategy

Method:

```http
GET /api/v1/strategies/{name}/evaluate?token=256265&from=2024-01-01&to=2024-06-30&interval=day
```

Controller method:

```java
evaluate(name, token, from, to, interval)
```

Purpose:

Fetches historical candles for an instrument and evaluates one named strategy against those candles.

Input:

Path variable:

| Name | Example | Purpose |
|---|---|---|
| `name` | `SMA_CROSSOVER` | Strategy name registered in `StrategyRegistry` |

Query parameters:

| Name | Required | Example | Purpose |
|---|---|---|---|
| `token` | Yes | `256265` | Zerodha instrument token, not the Kite access token |
| `from` | Yes | `2024-01-01` | Start date for historical candles |
| `to` | Yes | `2024-06-30` | End date for historical candles |
| `interval` | No | `day` | Candle interval; defaults to `day` |

Output:

HTTP `200 OK`

```json
{
  "strategyName": "SMA_CROSSOVER",
  "signal": "BUY",
  "reason": "Golden cross: SMA(10)=155.4000 crossed above SMA(30)=142.1000",
  "evaluatedAt": "2026-05-11T16:55:00Z"
}
```

Possible signals:

| Signal | Meaning |
|---|---|
| `BUY` | Strategy found a bullish or long-entry signal |
| `SELL` | Strategy found a bearish, exit, or short-entry signal |
| `HOLD` | Strategy did not find a complete actionable setup |

What happens internally:

- Controller creates a `HistoricalDataRequest`.
- `TradingStrategyService` resolves the strategy by name.
- `MarketDataPort` fetches historical candles from Kite through `KiteMarketDataAdapter`.
- The selected strategy converts candles into a ta4j `BarSeries`.
- ta4j indicators calculate SMA, RSI, MACD, or EMA values.
- Strategy-specific rules convert indicator values into `BUY`, `SELL`, or `HOLD`.

Important safety point:

This endpoint only returns a signal. It does not place an order.

## Order Controller

Class: `OrderController`  
Base path: `/api/v1/orders`

The order controller exists to place and inspect real Kite orders. These endpoints are intentionally separate from strategy evaluation.

### Place Order

Method:

```http
POST /api/v1/orders
```

Controller method:

```java
placeOrder(request)
```

Purpose:

Places a real order through Zerodha Kite.

Input:

JSON request body:

```json
{
  "tradingSymbol": "INFY",
  "exchange": "NSE",
  "transactionType": "BUY",
  "quantity": 1,
  "orderType": "MARKET",
  "product": "CNC",
  "price": 0,
  "triggerPrice": 0
}
```

Fields:

| Field | Required | Example | Purpose |
|---|---|---|---|
| `tradingSymbol` | Yes | `INFY` | Exchange trading symbol |
| `exchange` | Yes | `NSE` | Exchange segment |
| `transactionType` | Yes | `BUY` | Buy or sell side |
| `quantity` | Yes | `1` | Number of shares or lots |
| `orderType` | Yes | `MARKET` | Kite order type |
| `product` | Yes | `CNC` | Kite product type |
| `price` | No | `0` | Limit price; usually `0` for market orders |
| `triggerPrice` | No | `0` | Trigger price for stop-loss orders |

Output:

HTTP `201 Created`

```json
{
  "orderId": "250101000001234",
  "tradingSymbol": "INFY",
  "transactionType": "BUY",
  "quantity": 1,
  "price": 0.0,
  "status": "OPEN",
  "placedAt": "2026-05-11T16:55:00Z"
}
```

What happens internally:

- Spring deserializes the JSON body into `OrderRequest`.
- `OrderRequest` validates required fields, positive quantity, and non-negative prices.
- `OrderService` delegates to `OrderPort`.
- `KiteOrderAdapter` maps `OrderRequest` to Kite `OrderParams`.
- For `MARKET` orders, it sets Kite `marketProtection = -1.0`.
- Calls `KiteConnect.placeOrder(params, "regular")`.
- Returns the Kite order ID.

Safety point:

This endpoint places real orders. It should only be called deliberately.

### Get Order Status

Method:

```http
GET /api/v1/orders/{orderId}/status
```

Controller method:

```java
getOrderStatus(orderId)
```

Purpose:

Fetches the latest known status for a Kite order.

Input:

Path variable:

| Name | Example | Purpose |
|---|---|---|
| `orderId` | `250101000001234` | Kite order ID returned by the place-order endpoint |

Output:

HTTP `200 OK`

```json
{
  "orderId": "250101000001234",
  "tradingSymbol": "INFY",
  "transactionType": "BUY",
  "quantity": 1,
  "price": 0.0,
  "status": "COMPLETE",
  "statusMessage": "",
  "fetchedAt": "2026-05-11T16:55:00Z"
}
```

What happens internally:

- `OrderService` delegates to `OrderPort`.
- `KiteOrderAdapter` calls `KiteConnect.getOrderHistory(orderId)`.
- It takes the latest entry from the Kite order history.
- It maps that entry into `OrderStatusResponse`.

## What We Are Trying to Achieve With Strategies

The strategies provide repeatable, rule-based interpretation of historical candle data. Instead of manually looking at charts, the service calculates technical indicators and returns a clear signal.

The goal is not automatic trading. The goal is to answer:

- Is there a bullish setup?
- Is there a bearish setup?
- Are conditions unclear enough to stay out?
- Why did the strategy make this decision?

Every strategy returns:

- `strategyName`: which strategy was evaluated.
- `signal`: `BUY`, `SELL`, or `HOLD`.
- `reason`: explanation with indicator values or condition summary.
- `evaluatedAt`: timestamp when the signal was produced.

## Strategy Full Forms

| Strategy Name | Full Form | What It Tries To Achieve |
|---|---|---|
| `SMA_CROSSOVER` | Simple Moving Average Crossover | Detect a possible trend change by comparing short-term and longer-term average prices |
| `RSI_MEAN_REVERSION` | Relative Strength Index Mean Reversion | Detect when price may be overbought or oversold and could revert |
| `GAINZ_ALPHA_V2` | Gainz Alpha Version 2 | Project-specific multi-indicator strategy that requires trend, momentum, MACD, and volume confirmation |

Indicator full forms:

| Indicator | Full Form |
|---|---|
| `SMA` | Simple Moving Average |
| `RSI` | Relative Strength Index |
| `MACD` | Moving Average Convergence Divergence |
| `EMA` | Exponential Moving Average |

`GAINZ_ALPHA_V2` is a project strategy name, not a standard industry indicator abbreviation. In this project it means the second version of the Gainz Alpha confluence strategy.

## How the Strategies Work

### Shared Strategy Flow

All strategies follow the same basic flow:

1. Receive `List<Candle>`.
2. Check there are enough candles for the configured indicators.
3. Convert candles into a ta4j `BarSeries`.
4. Create ta4j indicators.
5. Read indicator values at the latest bar.
6. Apply strategy rules.
7. Return `StrategyDecision`.

### How ta4j Is Used Internally

ta4j is used as the technical-analysis calculation engine. The project does not manually calculate SMA, RSI, MACD, or EMA.

The implementation flow is:

1. Kite returns historical OHLCV candles.
2. `KiteMarketDataAdapter` maps Kite SDK data to project `Candle` records.
3. `BarSeriesFactory` converts `List<Candle>` to ta4j `BarSeries`.
4. Each strategy creates ta4j indicators from the `BarSeries`.
5. The strategy reads the latest indicator values.
6. Project code applies business rules and returns `StrategyDecision`.

The central conversion looks like this conceptually:

```java
BarSeries series = barSeriesFactory.create(strategyName, candles);
ClosePriceIndicator close = new ClosePriceIndicator(series);
```

After that, each strategy creates its own indicators:

```java
SMAIndicator fastSma = new SMAIndicator(close, fastPeriod);
RSIIndicator rsi = new RSIIndicator(close, rsiPeriod);
MACDIndicator macd = new MACDIndicator(close, macdShort, macdLong);
EMAIndicator signal = new EMAIndicator(macd, macdSignal);
```

ta4j calculates indicator values. The strategy class decides what those values mean.

### SMA_CROSSOVER: Simple Moving Average Crossover

What it tries to achieve:

Detect a possible trend change.

How it works:

- Converts candles to ta4j `BarSeries`.
- Creates `ClosePriceIndicator`.
- Creates fast `SMAIndicator` and slow `SMAIndicator`.
- Looks at the previous bar and latest bar.
- If fast SMA moves from below/equal to above slow SMA, it returns `BUY`.
- If fast SMA moves from above/equal to below slow SMA, it returns `SELL`.
- Otherwise it returns `HOLD`.

Example from tests:

- Prices `{100, 100, 100, 100, 100, 100, 1000}` create a final sharp spike.
- Fast SMA crosses above slow SMA.
- Output: `BUY`.

### RSI_MEAN_REVERSION: Relative Strength Index Mean Reversion

What it tries to achieve:

Detect when price movement may be stretched too far and could revert.

How it works:

- Converts candles to ta4j `BarSeries`.
- Creates `ClosePriceIndicator`.
- Creates ta4j `RSIIndicator`.
- Calculates RSI from close prices.
- If RSI is below the oversold threshold, it returns `BUY`.
- If RSI is above the overbought threshold, it returns `SELL`.
- If RSI is between thresholds, it returns `HOLD`.

Example from tests:

- A 30-bar downtrend from 600, falling 20 per bar, drives RSI below 30.
- The strategy treats this as oversold.
- Output: `BUY`.

### GAINZ_ALPHA_V2: Gainz Alpha Version 2

What it tries to achieve:

Produce a signal only when multiple independent conditions agree.

How it works:

- Converts candles to ta4j `BarSeries`.
- Creates `ClosePriceIndicator`.
- Checks trend using fast `SMAIndicator` vs slow `SMAIndicator`.
- Checks momentum using `RSIIndicator`.
- Checks confirmation using `MACDIndicator` vs `EMAIndicator` signal line.
- Checks participation using current `BarSeries` volume above recent average volume.
- Returns `BUY` only when all bullish conditions are true.
- Returns `SELL` only when all bearish conditions are true.
- Returns `HOLD` when any required condition is missing.

Example from tests:

- Bullish candles create SMA uptrend.
- RSI is above 40.
- MACD is above signal line.
- Final candle has a volume spike.
- Output: `BUY`.

Another test:

- Bullish price action exists, but volume is flat.
- Volume condition fails.
- Output: `HOLD`.

## Important Separation

Strategy evaluation and order placement are separate by design.

```text
Strategy endpoint -> returns signal only
Order endpoint    -> places real order only when explicitly called
```

This prevents a strategy signal from automatically becoming a live trade.
