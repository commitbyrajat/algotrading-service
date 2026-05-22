# Algo Trading Agent

Scheduled Pydantic AI agent for operating the Java algo-trading service through the OpenAPI MCP server.

The agent is intentionally conservative:

- It only evaluates the configured instrument universe.
- It does not scan all instruments.
- It never places orders unless order tools, trading execution, and side-specific order placement mode are all enabled.
- It uses a local JSON tool, `submit_order_json`, for order placement instead of mutating OpenAPI MCP order tools.

## Runtime Shape

```text
agent/main.py
  AgentConfig.from_env()
  TradingMcpAgent
  AgentScheduler
    APScheduler cron job
    FastAPI control API
```

External services:

- OpenAI-compatible model via `pydantic-ai`.
- OpenAPI MCP endpoint, normally created by the root `openapi-mcp` Docker Compose service.
- Java service API for direct JSON order submission and market-close liquidation.

## Dependencies

The agent uses Python 3.12 and `uv`.

```bash
cd agent
uv sync --locked
```

Main dependencies are:

- `pydantic-ai-slim[mcp,openai]`
- `apscheduler`
- `fastapi`
- `uvicorn`

## Configuration

Required:

```bash
export OPENAI_API_KEY=...
```

Core runtime settings:

| Variable | Default in code | Docker Compose default | Purpose |
|---|---:|---:|---|
| `AGENT_MODEL` | `openai-chat:gpt-4o-mini` | same | Pydantic AI model identifier |
| `MCP_ENDPOINT_URL` | `http://localhost:3100/mcp` | `http://openapi-mcp:3100/mcp` | MCP endpoint URL |
| `AGENT_APP_BASE_URL` | `http://localhost:8080` | `http://app:8080` | Java service base URL for direct JSON calls |
| `AGENT_CRON_MINUTES` | `*/5` | same | APScheduler cron minute expression |
| `AGENT_TIMEZONE` | `Asia/Kolkata` | same | Scheduler timezone and market-close window timezone |
| `AGENT_HTTP_HOST` | `0.0.0.0` | same | FastAPI bind host |
| `AGENT_HTTP_PORT` | `8090` | same | FastAPI bind port |
| `AGENT_LOG_LEVEL` | `INFO` | same | Python logging level |

Instrument and strategy settings:

| Variable | Default in code | Docker Compose default | Purpose |
|---|---:|---:|---|
| `AGENT_INSTRUMENT_EXCHANGE` | `NSE` | same | Exchange used for lookup, strategy, and order payloads |
| `AGENT_INSTRUMENT_UNIVERSE` | built-in multiline list | unset | Complete instrument input set for each cycle |
| `AGENT_STRATEGY_NAMES` | `ALL`, `GAINZ_ALPHA_V2`, `SMA_CROSSOVER`, `RSI_MEAN_REVERSION` | `GAINZ_ALPHA_V2` | Strategies to evaluate |
| `AGENT_RUN_PROMPT` | built-in supervision prompt | unset | Replaces the base run prompt before constraints are appended |

Date and candle settings:

| Variable | Default in code | Docker Compose default | Purpose |
|---|---:|---:|---|
| `AGENT_CANDLE_LOOKBACK_DAYS` | `120` | same | Lookback for non-intraday intervals |
| `AGENT_INTRADAY_LOOKBACK_DAYS` | `1` | same | Requested intraday lookback, capped to 1 day |
| `AGENT_CANDLE_INTERVAL` | unset means derive from cron | empty string | Explicit Kite candle interval override |
| `AGENT_TODAY` | unset | empty string | Deterministic date override, format `YYYY-MM-DD` |

Order settings:

| Variable | Default | Purpose |
|---|---:|---|
| `AGENT_ENABLE_ORDER_TOOLS` | `false` | Allows order/holdings inspection tools when true |
| `AGENT_ALLOW_TRADING` | `false` | Allows mutating trading only when true |
| `AGENT_ORDER_PLACEMENT_MODE` | `NONE` | One of `NONE`, `BUY`, `SELL`, `ALL` |
| `AGENT_ORDER_QUANTITY` | `1` | Quantity used for normal strategy-driven orders |
| `AGENT_ORDER_PRODUCT` | `CNC` | Kite product for normal strategy-driven orders |
| `AGENT_ORDER_TYPE` | `MARKET` | Kite order type for normal strategy-driven orders |
| `AGENT_MAX_ORDERS_PER_CYCLE` | `2` | Maximum normal strategy-driven order submissions per cycle |
| `AGENT_MARKET_CLOSE_LIQUIDATION_ENABLED` | `false` | Enables the separate market-close liquidation path |

Boolean environment values are true when set to one of:

```text
1, true, yes, y, on
```

## Default Instrument Universe

If `AGENT_INSTRUMENT_UNIVERSE` is not set, the current code uses this built-in list:

```text
JUSTDIAL
QUESS
IRCON
GOLD360
RVNL
```

Use newline-delimited values for overrides:

```bash
export AGENT_INSTRUMENT_UNIVERSE="JUSTDIAL
QUESS
IRCON"
```

The values are treated as broker `tradingSymbol` values or exchange-token identifiers. The agent prompts the model to use the mixed instrument lookup tool, pass only this universe, and never fetch all instruments.

## Strategy Names

`AGENT_STRATEGY_NAMES` is newline-delimited.

The code default is:

```text
ALL
GAINZ_ALPHA_V2
SMA_CROSSOVER
RSI_MEAN_REVERSION
```

Docker Compose currently defaults to:

```text
GAINZ_ALPHA_V2
```

`ALL` is a service-supported aggregate evaluation mode. The agent treats it as special: it should call strategy evaluation once with `name=ALL` per resolved instrument instead of expanding to individual strategy calls.

## Candle Interval and Date Range

Every run prompt includes explicit `from`, `to`, and `interval` constraints.

If `AGENT_CANDLE_INTERVAL` is set and non-blank, that value is used directly.

If it is unset or blank, the interval is derived from `AGENT_CRON_MINUTES`:

| Cron minute expression | Resolved interval |
|---|---|
| `*/1` or `1` | `minute` |
| `*/3` | `3minute` |
| `*/5` | `5minute` |
| `*/10` | `10minute` |
| `*/15` | `15minute` |
| `*/30` | `30minute` |
| `*/60` or `60` | `60minute` |
| other minute values under 60 | `5minute` |
| non-minute cron expressions | `day` |

Intraday intervals use `AGENT_INTRADAY_LOOKBACK_DAYS`, capped to `1`, so Kite historical requests stay within the short intraday range. Daily intervals use `AGENT_CANDLE_LOOKBACK_DAYS`.

For deterministic testing:

```bash
export AGENT_TODAY=2026-05-22
```

## Running Locally

Start the Java service and OpenAPI MCP server first. From the repository root, Docker Compose can provide those dependencies:

```bash
docker compose up redis app openapi-mcp
```

Run one cycle:

```bash
cd agent
OPENAI_API_KEY=... uv run --locked python main.py --once
```

Run the scheduler and HTTP API:

```bash
cd agent
OPENAI_API_KEY=... uv run --locked python main.py
```

With no local override, the agent uses:

```text
MCP_ENDPOINT_URL=http://localhost:3100/mcp
AGENT_APP_BASE_URL=http://localhost:8080
```

## Docker Compose

From the repository root:

```bash
export OPENAI_API_KEY=...
docker compose up agent-build
docker compose up agent
```

For the full stack:

```bash
export OPENAI_API_KEY=...
docker compose up
```

The Docker agent uses internal service URLs:

```text
MCP_ENDPOINT_URL=http://openapi-mcp:3100/mcp
AGENT_APP_BASE_URL=http://app:8080
```

The HTTP API is exposed on:

```text
http://localhost:8090
```

## HTTP API

Health:

```bash
curl http://localhost:8090/health
```

Scheduler status:

```bash
curl http://localhost:8090/status
```

Status returns the model, MCP URL, app URL, cron expression, trading flag, order placement mode, market-close liquidation flag, and next scheduled run time.

Manual run with the configured prompt:

```bash
curl -X POST http://localhost:8090/agent/run \
  -H "Content-Type: application/json" \
  -d '{}'
```

Manual run with a custom base prompt:

```bash
curl -X POST http://localhost:8090/agent/run \
  -H "Content-Type: application/json" \
  -d '{"prompt":"Check the configured universe and report unresolved instruments only."}'
```

The custom prompt only replaces the base prompt. The agent still appends the date, instrument, strategy, and order-execution constraints.

If a scheduled cycle is already running, manual trigger returns HTTP `409`.

## Trading Execution Policy

Order tools are disabled by default.

With:

```bash
AGENT_ENABLE_ORDER_TOOLS=false
```

the agent prompt forbids all MCP tools related to:

- orders
- purchased orders
- order status
- holdings
- order placement
- exit/sell

Read-only inspection mode:

```bash
export AGENT_ENABLE_ORDER_TOOLS=true
export AGENT_ALLOW_TRADING=false
export AGENT_ORDER_PLACEMENT_MODE=NONE
```

Trading mode:

```bash
export AGENT_ENABLE_ORDER_TOOLS=true
export AGENT_ALLOW_TRADING=true
export AGENT_ORDER_PLACEMENT_MODE=ALL
export AGENT_ORDER_QUANTITY=1
export AGENT_ORDER_PRODUCT=CNC
export AGENT_ORDER_TYPE=MARKET
export AGENT_MAX_ORDERS_PER_CYCLE=2
```

Placement requires all of these to pass:

- `AGENT_ENABLE_ORDER_TOOLS=true`
- `AGENT_ALLOW_TRADING=true`
- `AGENT_ORDER_PLACEMENT_MODE` permits the side
- instrument resolved on `AGENT_INSTRUMENT_EXCHANGE`
- strategy result is explicit `BUY` or explicit `SELL`
- holdings lookup succeeds
- purchased/existing-order lookup supports the decision
- max orders per cycle has not been reached

`AGENT_ALLOW_TRADING=true` alone is not enough. `AGENT_ORDER_PLACEMENT_MODE=NONE` blocks placement.

## Local Order Placement Tool

The agent registers a local tool:

```text
submit_order_json
```

It bypasses mutating OpenAPI MCP order tools and sends `application/json` directly to the Java service.

For `BUY`, it calls:

```text
POST {AGENT_APP_BASE_URL}/api/v1/orders
```

with `transactionType=BUY`.

For `SELL`, it calls:

```text
POST {AGENT_APP_BASE_URL}/api/v1/orders/exit
```

without `transactionType`, because the Java service exit endpoint always submits `SELL`.

The local tool enforces:

- order tools enabled
- trading enabled
- placement mode permits the side
- exchange equals `AGENT_INSTRUMENT_EXCHANGE`
- quantity is positive
- price and trigger price are non-negative
- normal per-cycle max order count

The Java service still performs its own order validation, including SELL sellability checks against purchased orders and holdings.

## BUY and SELL Decision Rules

BUY:

- Instrument must resolve on the configured exchange.
- Strategy result must be explicit `BUY`.
- Holdings lookup must succeed.
- Existing-order lookup must show no duplicate completed or pending BUY exposure.
- Placement mode must be `BUY` or `ALL`.

SELL:

- Instrument must resolve on the configured exchange.
- Strategy result must be explicit `SELL`.
- Holdings lookup must succeed.
- Existing-order lookup plus holdings must confirm a completed BUY/position or sellable holding.
- Placement mode must be `SELL` or `ALL`.

If one strategy response contains both BUY and SELL for the same instrument, the prompt requires HOLD/conflict and no order.

For every skipped BUY or SELL, the final report must include the blocker, such as disabled order tools, trading disabled, side not allowed, duplicate exposure, holdings lookup failed, no sellable holding, max orders reached, conflicting signals, unresolved instrument, or tool/API failure.

## Market-Close Liquidation

Set:

```bash
export AGENT_MARKET_CLOSE_LIQUIDATION_ENABLED=true
```

When enabled, any cycle starting from `15:20:00` inclusive to before `15:30:00` in `AGENT_TIMEZONE` skips the normal strategy workflow and runs the liquidation path.

Liquidation flow:

1. Verify order tools are enabled.
2. Verify trading execution is enabled.
3. Verify placement mode permits SELL.
4. Fetch `GET {AGENT_APP_BASE_URL}/api/v1/orders/purchased`.
5. Group purchased positions by trading symbol, exchange, and product.
6. Submit one `POST /api/v1/orders/exit` request per grouped position.
7. Return a market-close liquidation report.

Required settings:

```bash
AGENT_ENABLE_ORDER_TOOLS=true
AGENT_ALLOW_TRADING=true
AGENT_ORDER_PLACEMENT_MODE=SELL
```

or:

```bash
AGENT_ORDER_PLACEMENT_MODE=ALL
```

Market-close liquidation is not capped by `AGENT_MAX_ORDERS_PER_CYCLE`; it is intended to close all purchased positions returned by the service.

## Scheduler Behavior

`python main.py` starts:

- one immediate scheduled cycle
- the APScheduler cron job
- the FastAPI control server

The scheduler uses:

```python
CronTrigger(minute=AGENT_CRON_MINUTES, timezone=AGENT_TIMEZONE)
```

Only one cycle can run at a time. Overlapping scheduled cycles are skipped, and overlapping manual runs return HTTP `409`.

## Logs

Follow Docker logs:

```bash
docker compose logs -f agent
```

Useful log fields:

- `cycle_id`
- trigger source, `scheduled` or `manual`
- model
- MCP URL
- date range
- interval
- order tool mode
- trading mode
- order placement mode
- max orders
- market-close liquidation flag
- elapsed seconds

If OpenAI returns `429 Too Many Requests`, the agent reached the model provider but the provider rejected the request. Reduce schedule frequency, switch `AGENT_MODEL`, or fix account quota.

## Common Commands

Run once with order inspection disabled:

```bash
cd agent
OPENAI_API_KEY=... uv run --locked python main.py --once
```

Run once with read-only order inspection:

```bash
cd agent
OPENAI_API_KEY=... \
AGENT_ENABLE_ORDER_TOOLS=true \
AGENT_ALLOW_TRADING=false \
uv run --locked python main.py --once
```

Run once with SELL-only execution:

```bash
cd agent
OPENAI_API_KEY=... \
AGENT_ENABLE_ORDER_TOOLS=true \
AGENT_ALLOW_TRADING=true \
AGENT_ORDER_PLACEMENT_MODE=SELL \
AGENT_ORDER_QUANTITY=1 \
uv run --locked python main.py --once
```

Use daily candles explicitly:

```bash
export AGENT_CANDLE_INTERVAL=day
```

Use scheduler-derived 5-minute candles:

```bash
unset AGENT_CANDLE_INTERVAL
export AGENT_CRON_MINUTES="*/5"
```

In Docker Compose, `AGENT_CANDLE_INTERVAL` defaults to an empty string, which also causes scheduler-derived interval selection.

## Troubleshooting

### Agent cannot connect to MCP

Check the MCP server:

```bash
curl http://localhost:3100
docker compose logs -f openapi-mcp
```

For local runs use:

```bash
export MCP_ENDPOINT_URL=http://localhost:3100/mcp
```

For Docker Compose, use the internal URL:

```bash
MCP_ENDPOINT_URL=http://openapi-mcp:3100/mcp
```

### Direct JSON order submission fails

Check:

- `AGENT_APP_BASE_URL`
- Java service health
- Kite authentication status
- order placement mode
- holdings and purchased-order state

Useful service checks:

```bash
curl http://localhost:8080/api/v1/kite/status
curl http://localhost:8080/api/v1/orders/purchased
curl "http://localhost:8080/api/v1/holdings?tradingSymbol=JUSTDIAL"
```

### Agent uses old example dates

The prompt explicitly injects the date range. Verify the runtime config in logs:

```text
from=...
to=...
interval=...
```

Set `AGENT_TODAY=YYYY-MM-DD` for deterministic testing.

### Orders are not submitted even though strategy says BUY or SELL

Check the final report blocker. Common causes:

- `AGENT_ENABLE_ORDER_TOOLS=false`
- `AGENT_ALLOW_TRADING=false`
- `AGENT_ORDER_PLACEMENT_MODE=NONE`
- side not allowed by placement mode
- holdings lookup failed
- no sellable holding
- duplicate BUY exposure
- max orders reached
- unresolved instrument
- conflicting strategy signals

### Market-close liquidation does not run

Check:

- `AGENT_MARKET_CLOSE_LIQUIDATION_ENABLED=true`
- local time in `AGENT_TIMEZONE` is from `15:20:00` to before `15:30:00`
- order tools enabled
- trading enabled
- placement mode `SELL` or `ALL`

If it runs, the normal strategy workflow is skipped for that cycle.
