# Product Requirements Document

## Product

`algotrading-service` is a Spring Boot REST service for evaluating technical trading strategies on Zerodha Kite historical market data and manually placing Kite orders. Strategy evaluation and order execution are intentionally separate flows.

## Problem

Retail and semi-systematic traders need a local service that can authenticate with Zerodha, fetch historical OHLCV candles, evaluate repeatable technical strategies, and place orders only after an explicit client action. The service should make strategy experimentation easier without creating an unsafe auto-trading loop.

## Goals

- Provide REST APIs for Zerodha Kite authentication, strategy evaluation, order placement, and order status checks.
- Cache Kite access tokens in Redis so service restarts do not always require re-authentication.
- Keep domain strategy code independent of the Kite SDK.
- Support multiple pluggable technical strategies through a registry.
- Return transparent strategy decisions with signal, reason, strategy name, and evaluation timestamp.
- Prevent automatic order placement from strategy signals.

## Non-Goals

- Fully automated trading or scheduler-driven order placement.
- Portfolio management, position sizing, risk engine, or P&L reporting.
- Multi-user token isolation.
- Broker abstraction beyond the current Zerodha Kite implementation.
- Live market data streaming.
- Strategy backtesting UI.

## Users

- Developer/operator running the service locally or in a controlled environment.
- Trading strategy developer adding new `TechnicalStrategy` implementations.
- API client that evaluates strategies and explicitly places orders.

## Implemented Capabilities

### Authentication

- `GET /api/v1/kite/login-url` returns the Zerodha login URL.
- `GET /api/v1/kite/callback` exchanges Zerodha `request_token` for an `access_token`.
- `GET /api/v1/kite/status` reports whether an in-memory Kite session exists.
- Redis stores `kite:access-token` with a 24-hour TTL.
- Startup attempts to restore a cached token from Redis.

### Strategy Evaluation

- `GET /api/v1/strategies` lists registered strategy names.
- `GET /api/v1/strategies/{name}/evaluate` fetches Kite historical candles and evaluates the selected strategy.
- Implemented strategies:
  - `SMA_CROSSOVER`
  - `RSI_MEAN_REVERSION`
  - `GAINZ_ALPHA_V2`

### Order Management

- `POST /api/v1/orders` places a Kite regular order.
- `GET /api/v1/orders/{orderId}/status` fetches the latest order status from Kite order history.
- Market orders set Kite market protection automatically.

## Functional Requirements

| ID | Requirement | Status |
|---|---|---|
| FR-1 | Service must expose a login URL endpoint for browser-based Kite login. | Implemented |
| FR-2 | Service must handle Kite callback and persist the access token in Redis. | Implemented |
| FR-3 | Service must restore an existing Redis token on startup. | Implemented |
| FR-4 | Service must list available strategies. | Implemented |
| FR-5 | Service must evaluate a named strategy against Kite historical candles. | Implemented |
| FR-6 | Service must return `BUY`, `SELL`, or `HOLD` with a human-readable reason. | Implemented |
| FR-7 | Service must require an explicit API call to place orders. | Implemented |
| FR-8 | Service must place Kite regular orders and return the Kite order ID. | Implemented |
| FR-9 | Service must fetch order status by order ID. | Implemented |
| FR-10 | Service must provide consistent problem responses for expected errors. | Partially implemented |

## Safety Requirements

- A strategy signal must never automatically place an order.
- Order APIs must remain explicit and separate from strategy APIs.
- Market orders must include Kite market protection.
- Production deployments should keep credentials and tokens outside source control.
- Future auto-trading features must require a separate risk-control design before implementation.

## Validation Requirements

- Strategy inputs must reject empty candle sets and insufficient candle windows.
- Historical data requests must reject blank instrument tokens and `from > to`.
- Order requests must reject non-positive quantity and negative price fields.
- Future improvement: restrict order fields to known Kite-supported values.

## Quality Requirements

- Java 21.
- Spring Boot 4.0.6.
- Maven test suite should run consistently on supported JDKs.
- Domain model and strategy code should remain free of Kite SDK imports.
- Kite SDK usage should stay inside adapter/config/auth layers.
- New strategies should be unit-tested without requiring Kite or Redis.

## Success Metrics

- All tests pass in CI and local development.
- A user can authenticate, evaluate a strategy, and place/check an order through documented APIs.
- Strategy additions require only a new `TechnicalStrategy` component and tests.
- No accidental order is placed as a side effect of strategy evaluation.

## Known Gaps

- Tests currently fail in this local run because Mockito inline mock maker cannot self-attach on the current JDK. Main code compiles; non-Mockito tests pass.
- Runtime auth path hints are inconsistent in a few messages and docs.
- Order request validation does not yet enforce allowed enum-like values.
- The app is single-user and stores only one active Kite session.
- Redis token TTL is a fixed 24 hours rather than expiring exactly at Kite's daily cutoff.
- No integration tests cover live Kite or Redis flows.
