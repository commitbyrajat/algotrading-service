# Architecture

## Overview

`algotrading-service` is a layered Spring Boot application with a light hexagonal architecture. Controllers expose REST APIs, application services coordinate workflows, domain strategies evaluate candles, and Kite-specific integrations live behind ports/adapters.

```text
Client
  |
  v
REST Controllers
  |-- KiteAuthController
  |-- StrategyController
  |-- OrderController
  |
  v
Application Services
  |-- KiteAuthService
  |-- TradingStrategyService
  |-- OrderService
  |
  v
Ports and Domain
  |-- MarketDataPort
  |-- OrderPort
  |-- TechnicalStrategy
  |-- Candle / StrategyDecision / OrderRequest
  |
  v
Adapters and Infrastructure
  |-- KiteMarketDataAdapter
  |-- KiteOrderAdapter
  |-- RedisTokenCacheService
  |-- KiteConnect bean
```

## Package Responsibilities

| Package | Responsibility |
|---|---|
| `auth` | Kite login flow, callback handling, in-memory session store |
| `config` | Spring configuration and Kite property binding |
| `controller` | HTTP API layer |
| `exception` | Error translation to `ProblemDetail` |
| `market` | Historical market data port and Kite adapter |
| `model` | Domain records for candles, requests, and strategy decisions |
| `order` | Order port, service, request/response records, Kite order adapter |
| `redis` | Redis configuration and Kite token cache |
| `service` | Strategy application service |
| `strategy` | Strategy interface, registry, and concrete technical strategies |
| `util` | Candle timestamp parsing and ta4j `BarSeries` construction |

## Authentication Flow

1. Client calls `GET /api/v1/kite/login-url`.
2. Client opens the Zerodha login URL in a browser.
3. Zerodha redirects to `GET /api/v1/kite/callback?request_token=...&status=success`.
4. `KiteAuthService` exchanges the request token through `KiteConnect.generateSession`.
5. The returned access token is applied to the shared `KiteConnect` bean.
6. `RedisTokenCacheService` stores the access token as `kite:access-token`.
7. `KiteSessionStore` stores the current in-memory session.

On startup, `KiteAuthService.tryRestoreTokenFromRedis` attempts to load the cached token. If Redis has no token, the application still starts but Kite-backed endpoints require login.

## Strategy Evaluation Flow

1. Client calls `GET /api/v1/strategies/{name}/evaluate`.
2. `StrategyController` builds `HistoricalDataRequest`.
3. `TradingStrategyService` resolves the strategy from `StrategyRegistry`.
4. `KiteMarketDataAdapter` fetches historical candles via `KiteConnect`.
5. Kite SDK candles are mapped to domain `Candle` records.
6. The selected `TechnicalStrategy` converts candles to ta4j `BarSeries` and evaluates the latest bar.
7. The API returns a `StrategyDecision`.

Current strategies:

- `SMA_CROSSOVER`: detects fast SMA crossing slow SMA on the final bar.
- `RSI_MEAN_REVERSION`: buys below oversold threshold and sells above overbought threshold.
- `GAINZ_ALPHA_V2`: requires SMA trend, RSI momentum, MACD confirmation, and above-average volume.

## Order Flow

1. Client explicitly calls `POST /api/v1/orders`.
2. `OrderController` validates and passes `OrderRequest` to `OrderService`.
3. `KiteOrderAdapter` maps the request to Kite `OrderParams`.
4. Market orders set `marketProtection = -1.0` for Kite auto-protection.
5. `KiteConnect.placeOrder` submits the order with variety `regular`.
6. API returns `PlacedOrderResponse`.

Order status is fetched through `KiteConnect.getOrderHistory(orderId)`, and the latest history item is returned.

## Key Design Decisions

- Strategy signals and order placement are decoupled to avoid accidental live trades.
- Kite SDK imports are kept out of strategy and domain model code.
- Strategy implementations are Spring components, auto-registered through `StrategyRegistry`.
- Candle-to-ta4j conversion is centralized in `BarSeriesFactory`.
- Access-token cache is best-effort: Redis failure does not block application startup.
- The application currently assumes one Kite user/session per service instance.

## Data Model

| Type | Purpose |
|---|---|
| `Candle` | Immutable OHLCV bar with `Instant` timestamp |
| `HistoricalDataRequest` | Historical data query parameters |
| `StrategyDecision` | Strategy name, signal, reason, and evaluation time |
| `TradingSignal` | `BUY`, `SELL`, `HOLD` |
| `OrderRequest` | Explicit order placement request |
| `PlacedOrderResponse` | Order placement result |
| `OrderStatusResponse` | Latest broker order status |

## Runtime Dependencies

- Java 21
- Spring Boot 4.0.6
- ta4j 0.22.6
- Zerodha Kite Connect SDK 4.0.0
- Redis
- Maven

## Testing Architecture

- Strategy tests instantiate strategies directly and do not need Spring, Kite, or Redis.
- `TradingStrategyServiceTest` uses Mockito for service-level delegation behavior.
- Utility/model tests cover candle mapping and `BarSeriesFactory`.

Current local test status: `mvn test` compiles main code but fails three Mockito-backed service tests because the inline mock maker cannot self-attach to the current JVM. The remaining tests pass.

## Extension Points

### Add a Strategy

1. Implement `TechnicalStrategy`.
2. Annotate the class with `@Component`.
3. Return a unique `name()`.
4. Use `BarSeriesFactory` for ta4j conversion.
5. Add focused unit tests.

`StrategyRegistry` will discover the strategy automatically.

### Add Another Broker

1. Create broker-specific implementations of `MarketDataPort` and `OrderPort`.
2. Move broker selection into Spring configuration.
3. Keep domain records unchanged unless broker-neutral data fields are insufficient.

### Add Auto-Trading

Auto-trading is intentionally out of scope today. A future design must add explicit risk limits, dry-run mode, position awareness, idempotency, audit logs, and a kill switch before any scheduler links signals to orders.
