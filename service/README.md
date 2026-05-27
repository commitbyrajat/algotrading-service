# Algo Trading Service

Spring Boot service for Zerodha Kite authentication, instrument lookup, historical strategy evaluation, portfolio holdings, and explicit order placement.

This README covers only the Java service module. The Python agent, MCP server, and full Docker stack are documented from the repository root.

## What This Module Provides

- Browser-based Zerodha Kite Connect authentication.
- Redis-backed Kite access-token restore after service restart.
- Instrument-master lookup and filtering.
- Historical candle retrieval through Kite.
- Strategy evaluation using ta4j.
- ATR-based BUY quantity suggestions.
- Holdings lookup with Redis cache.
- Purchased-order lookup with Redis cache.
- Explicit BUY and SELL order placement.
- Weekday market-close and weekend order-placement guard.
- SELL guard that checks purchased orders and holdings before submitting exit orders.

Strategy evaluation and order placement are intentionally separate. A `BUY` or `SELL` strategy signal never places an order by itself; a caller must explicitly call the order endpoint.

## Tech Stack

| Area | Technology |
|---|---|
| Runtime | Java 21 |
| Framework | Spring Boot 4.0.6 |
| Build | Maven |
| Broker SDK | Zerodha Kite Connect SDK |
| Technical indicators | ta4j |
| Cache | Redis via Spring Data Redis |
| API docs | springdoc OpenAPI |

## Module Layout

```text
service/
  pom.xml
  src/main/java/com/algotrading/app/
    auth/          Kite login flow and session restore
    config/        Spring beans and typed configuration properties
    controller/    REST controllers
    exception/     API exception mapping
    market/        Kite historical data and instrument adapters
    model/         API/domain records
    order/         Order requests, responses, service, and Kite adapter
    portfolio/     Holdings service, cache, and Kite adapter
    redis/         Redis token cache configuration
    service/       Instrument and strategy application services
    strategy/      Technical strategies and position sizing
    util/          Candle and time utilities
  src/main/resources/application.yml
  src/test/java/com/algotrading/app/
```

## Runtime Configuration

Configuration is read from `application.yml`, with environment-variable overrides.

| Environment variable | Default | Purpose |
|---|---:|---|
| `KITE_API_KEY` | `your-api-key-here` | Kite Connect API key |
| `KITE_API_SECRET` | `your-api-secret-here` | Kite Connect API secret |
| `KITE_USER_ID` | `your-user-id-here` | Zerodha user/client ID |
| `KITE_REDIRECT_URL` | `http://127.0.0.1:8080/kite/callback` | Redirect URL registered in Kite |
| `SPRING_DATA_REDIS_HOST` | `localhost` | Redis host |
| `SPRING_DATA_REDIS_PORT` | `6379` | Redis port |
| `INSTRUMENT_REQUIRE_EXPIRY_FOR_LOOKUP` | `false` | Require expiry when lookup can match derivative instruments |
| `TRADING_MARKET_CLOSE_TIME` | `15:30` | Market close time in `Asia/Kolkata`; order placement is blocked at or after this time on weekdays |
| `TRADING_POSITION_SIZING_CAPITAL` | `500000` | Capital base used for ATR position sizing |
| `TRADING_POSITION_SIZING_RISK_PERCENT` | `0.01` | Fraction of capital risked per BUY |
| `TRADING_POSITION_SIZING_ATR_PERIOD` | `14` | ATR lookback period |
| `TRADING_POSITION_SIZING_ATR_MULTIPLIER` | `1.5` | Stop-distance multiplier applied to ATR |
| `TRADING_POSITION_SIZING_MAX_PORTFOLIO_EXPOSURE_PERCENT` | `0.20` | Maximum capital allocation for one suggested BUY |

For Docker Compose, these service env vars are already wired in the root `docker-compose.yml`.

## Local Development

From the repository root:

```bash
docker compose up -d redis
mvn -pl service test
mvn -pl service spring-boot:run
```

Or package and run the jar:

```bash
mvn -pl service package
java -jar service/target/service-*.jar
```

The service listens on:

```text
http://localhost:8080
```

OpenAPI UI is available at:

```text
http://localhost:8080/swagger-ui.html
```

OpenAPI JSON is available at:

```text
http://localhost:8080/v3/api-docs
```

## Docker Compose

From the repository root:

```bash
docker compose up app
```

The app container depends on:

- `redis`
- `app-build`, which builds the jar inside a Maven container

After code or environment changes, recreate the app:

```bash
docker compose up --build --force-recreate app
```

## Authentication Flow

Zerodha requires an interactive browser login to create a daily access token.

1. Request a login URL:

```bash
curl http://localhost:8080/api/v1/kite/login-url
```

1. Open the returned `loginUrl` in a browser.
2. Complete Kite login.
3. Kite redirects to this service with a `request_token`.
4. The service exchanges the request token for an access token.
5. The access token is stored in Redis and applied to the shared `KiteConnect` bean.

Check auth status:

```bash
curl http://localhost:8080/api/v1/kite/status
```

If Redis still has the token when the service restarts, the service restores it automatically.

## REST API

### Kite Auth

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/v1/kite/login-url` | Return Zerodha login URL |
| `GET` | `/api/v1/kite/callback` | Receive Kite redirect and create session |
| `GET` | `/api/v1/kite/status` | Check whether session is authenticated |

### Instruments

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/v1/instruments` | List instruments, optionally filtered by `exchange` |
| `GET` | `/api/v1/instruments/by-symbols` | Find instruments by one or more trading symbols |
| `GET` | `/api/v1/instruments/lookup` | Resolve instruments by symbol/token criteria |

Examples:

```bash
curl "http://localhost:8080/api/v1/instruments?exchange=NSE"
curl "http://localhost:8080/api/v1/instruments/by-symbols?exchange=NSE&tradingSymbols=INFY,TCS"
curl "http://localhost:8080/api/v1/instruments/lookup?exchange=NSE&tradingSymbol=INFY"
```

### Strategies

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/v1/strategies` | List registered strategy names |
| `GET` | `/api/v1/strategies/{name}/evaluate` | Evaluate one strategy, or `ALL`, over historical candles |

Example:

```bash
curl "http://localhost:8080/api/v1/strategies/GAINZ_ALPHA_V2/evaluate?instrumentToken=408065&from=2026-01-01&to=2026-05-22&interval=day"
```

Common interval values are Kite intervals such as `minute`, `5minute`, `15minute`, `hour`, and `day`.

Registered strategies include:

- `SMA_CROSSOVER`
- `RSI_MEAN_REVERSION`
- `GAINZ_ALPHA_V2`

Use `ALL` as the strategy name to evaluate every registered strategy with one candle fetch. BUY strategy results include an ATR-based `quantitySuggestion`; HOLD and SELL results leave `quantitySuggestion` null.

### Holdings

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/v1/holdings` | List Kite holdings, optionally filtered by `tradingSymbol` |

Examples:

```bash
curl http://localhost:8080/api/v1/holdings
curl "http://localhost:8080/api/v1/holdings?tradingSymbol=JUSTDIAL"
```

Holdings are cached by trading symbol in Redis. A symbol lookup can return directly from cache without calling Kite.

### Orders

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/v1/orders` | Place an explicit BUY or SELL order |
| `POST` | `/api/v1/orders/exit` | Place a SELL order to exit a position |
| `POST` | `/api/v1/orders/sell` | Alias for `/api/v1/orders/exit` |
| `GET` | `/api/v1/orders/purchased` | List completed purchased orders still available after completed sells |
| `POST` | `/api/v1/orders/purchased/cache/clear` | Clear purchased-order cache |
| `GET` | `/api/v1/orders/{orderId}/status` | Fetch order status from Kite |

Place a market BUY:

```bash
curl -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{
    "tradingSymbol": "INFY",
    "exchange": "NSE",
    "transactionType": "BUY",
    "quantity": 1,
    "orderType": "MARKET",
    "product": "CNC",
    "price": 0,
    "triggerPrice": 0
  }'
```

Exit with a market SELL:

```bash
curl -X POST http://localhost:8080/api/v1/orders/exit \
  -H "Content-Type: application/json" \
  -d '{
    "tradingSymbol": "JUSTDIAL",
    "exchange": "NSE",
    "quantity": 1,
    "orderType": "MARKET",
    "product": "CNC",
    "price": 0,
    "triggerPrice": 0
  }'
```

## Order Safety Rules

Order placement is explicit and guarded:

- Strategy evaluation never auto-submits orders.
- `POST /api/v1/orders` is the only generic order mutation endpoint.
- `/api/v1/orders/exit` and `/api/v1/orders/sell` always submit `SELL`.
- Orders are rejected on Saturdays and Sundays.
- On weekdays, orders are rejected at or after `trading.market-hours.close-time`, default `15:30` in `Asia/Kolkata`.
- After a successful `BUY`, purchased-order cache is evicted.
- After a successful `SELL`, the symbol-level purchased-order and holdings caches are evicted.
- SELL validation checks sellable quantity before calling Kite.

The SELL guard accepts either source:

- purchased-order cache quantity from completed BUY orders, or
- holdings quantity from Kite holdings.

Holdings sellable quantity is calculated as:

```text
max(0, quantity + t1Quantity - usedQuantity)
```

This means a T+1 holding with `quantity=0`, `t1Quantity=1`, and `usedQuantity=0` can satisfy a SELL request for quantity `1`.

If neither source has sufficient quantity, the API returns `400 Bad Request` with the requested quantity and both cached quantities.

## Caching

Redis is used for:

- Kite access token.
- Instrument lists and lookup indices.
- Holdings grouped by trading symbol.
- Purchased orders grouped by trading symbol.

Cache behavior is intentionally conservative. If Redis is unavailable in paths where cache is optional, the service calls Kite directly. If a mutation succeeds, related caches are evicted to avoid stale sellability checks.

## Position Sizing

`AtrPositionSizingStrategy` computes suggested BUY quantity from:

```text
riskAmount = capital * riskPercent
riskPerShare = ATR(atrPeriod) * atrMultiplier
riskBasedQuantity = floor(riskAmount / riskPerShare)
maxExposureQuantity = floor((capital * maxPortfolioExposurePercent) / latestClose)
suggestedQuantity = min(riskBasedQuantity, maxExposureQuantity)
```

These settings are externalized under:

```yaml
trading:
  market-hours:
    close-time: "15:30"
  position-sizing:
    capital: 500000
    risk-percent: 0.01
    atr-period: 14
    atr-multiplier: 1.5
    max-portfolio-exposure-percent: 0.20
```

`trading.market-hours.close-time` is also exposed as `TRADING_MARKET_CLOSE_TIME` for Docker and shell configuration.

## Testing

Run all service tests:

```bash
mvn -pl service test
```

Run focused tests:

```bash
mvn -pl service test -Dtest=AtrPositionSizingStrategyTest
mvn -pl service test -Dtest=OrderServiceTest
```

Some local JDK setups can fail Mockito-based tests with a Byte Buddy self-attach error. That is an environment/JDK agent issue, not a service compilation failure. Focused non-Mockito tests should still run normally.

## Troubleshooting

### `No active Kite session`

Complete the browser login flow from `/api/v1/kite/login-url`. If Redis was cleared or the token expired, the service cannot recreate the access token without browser login.

### `Incorrect api_key or access_token`

The Kite token is absent, expired, or belongs to different Kite credentials. Re-authenticate and verify `KITE_API_KEY`, `KITE_API_SECRET`, and `KITE_USER_ID`.

### `Cannot place SELL order because neither purchased-order cache nor holdings cache has sufficient quantity`

Check both:

```bash
curl "http://localhost:8080/api/v1/orders/purchased"
curl "http://localhost:8080/api/v1/holdings?tradingSymbol=SYMBOL"
```

For holdings, the service counts `quantity + t1Quantity - usedQuantity`. If the running container still reports `holdingQuantity=0` for a positive `t1Quantity`, rebuild and recreate the `app` container so it uses the latest jar.

### `Cannot place order because market is closed`

The service blocks order placement on weekends and at or after `TRADING_MARKET_CLOSE_TIME` on weekdays. The default is `15:30` IST. To change it:

```bash
export TRADING_MARKET_CLOSE_TIME=15:45
docker compose up --build --force-recreate app
```

### Config changes are not visible in Docker

Recreate the container:

```bash
docker compose up --build --force-recreate app
```

### OpenAPI MCP cannot see endpoints

Verify the app is healthy and OpenAPI JSON is reachable:

```bash
curl http://localhost:8080/v3/api-docs
```
