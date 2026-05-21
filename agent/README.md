## Algo Trading Agent

Scheduled Pydantic AI agent that connects to the OpenAPI MCP endpoint exposed by
`openapi-mcp` in the root `docker-compose.yml`.

When run locally, the default MCP endpoint is:

```text
http://localhost:3100/mcp
```

When run through Docker Compose, the agent uses the internal endpoint:

```text
http://openapi-mcp:3100/mcp
```

### Setup

```bash
uv sync
```

Required environment:

```bash
export OPENAI_API_KEY=...
```

Optional environment:

```bash
export AGENT_MODEL="openai-chat:gpt-4o-mini"
export MCP_ENDPOINT_URL="http://localhost:3100/mcp"
export AGENT_APP_BASE_URL="http://localhost:8080"
export AGENT_CRON_MINUTES="*/5"
export AGENT_TIMEZONE="Asia/Kolkata"
export AGENT_ENABLE_ORDER_TOOLS="false"
export AGENT_ALLOW_TRADING="false"
export AGENT_ORDER_PLACEMENT_MODE="NONE"
export AGENT_INSTRUMENT_EXCHANGE="NSE"
export AGENT_STRATEGY_NAMES="GAINZ_ALPHA_V2"
export AGENT_HTTP_HOST="0.0.0.0"
export AGENT_HTTP_PORT="8090"
export AGENT_LOG_LEVEL="INFO"
export AGENT_CANDLE_LOOKBACK_DAYS="120"
export AGENT_INTRADAY_LOOKBACK_DAYS="1"
export AGENT_CANDLE_INTERVAL=""
export AGENT_ORDER_QUANTITY="1"
export AGENT_ORDER_PRODUCT="CNC"
export AGENT_ORDER_TYPE="MARKET"
export AGENT_MAX_ORDERS_PER_CYCLE="2"
export AGENT_MARKET_CLOSE_LIQUIDATION_ENABLED="false"
export AGENT_TODAY="2026-05-20"
export AGENT_INSTRUMENT_UNIVERSE="SHEKHAWATI
DRCSYSTEMS
EASEMYTRIP"
```

By default, the agent uses trading symbols derived from the first page of the
Screener "Best Penny Stocks" screen. The Screener display names are not passed
to the broker instrument API. The agent first uses the mixed instrument lookup
API, which accepts either Kite `tradingSymbol` values or `exchangeToken` values
in the same identifier list. By default, the agent passes `exchange=NSE` for
instrument lookup and does not retry unresolved symbols on BSE.

### Docker Compose

From the repository root:

```bash
export OPENAI_API_KEY=...
docker compose up agent-build
docker compose up agent
```

To build and start the full stack including Redis, the Java service, MCP, and the
agent:

```bash
export OPENAI_API_KEY=...
docker compose up
```

The `agent-build` service runs:

```bash
uv sync --locked
```

The `agent` service starts the scheduler and HTTP API:

```bash
uv run --locked python main.py
```

The HTTP API is exposed on port `8090`.

### Run Once

```bash
uv run python main.py --once
```

### Run Every 5 Minutes

```bash
uv run python main.py
```

By default the scheduler runs one cycle immediately, then runs on the cron
schedule `*/5 * * * *`.

### HTTP Endpoints

Health check:

```bash
curl http://localhost:8090/health
```

Scheduler status:

```bash
curl http://localhost:8090/status
```

Manually trigger one agent cycle with the default prompt:

```bash
curl -X POST http://localhost:8090/agent/run \
  -H "Content-Type: application/json" \
  -d '{}'
```

Manually trigger one cycle with a custom prompt:

```bash
curl -X POST http://localhost:8090/agent/run \
  -H "Content-Type: application/json" \
  -d '{"prompt":"Check the configured Screener penny-stock universe and report unresolved instruments only."}'
```

If a scheduled cycle is already running, manual trigger returns HTTP `409`.

### Trading Execution

Order tools are disabled by default. With `AGENT_ENABLE_ORDER_TOOLS=false`, the
agent must not call any MCP tools related to orders, including purchased-order
lookup, order status, order placement, exit, or sell tools.

To allow read-only order inspection without placement:

```bash
export AGENT_ENABLE_ORDER_TOOLS=true
export AGENT_ALLOW_TRADING=false
docker compose up -d agent
```

To allow the agent to place orders from its strategy recommendations:

```bash
export AGENT_ENABLE_ORDER_TOOLS=true
export AGENT_ALLOW_TRADING=true
export AGENT_ORDER_PLACEMENT_MODE=ALL
export AGENT_ORDER_QUANTITY=1
export AGENT_ORDER_PRODUCT=CNC
export AGENT_ORDER_TYPE=MARKET
export AGENT_MAX_ORDERS_PER_CYCLE=2
docker compose up -d agent
```

Execution rules:

- The agent must first lookup instruments by trading symbol or exchange token.
- The agent executes strategies only for resolved instruments.
- The agent may inspect existing purchased/completed orders before deciding
  whether to BUY, SELL, or HOLD only when `AGENT_ENABLE_ORDER_TOOLS=true`.
- `AGENT_ALLOW_TRADING=true` does not permit placement by itself. Set
  `AGENT_ORDER_PLACEMENT_MODE=BUY`, `SELL`, or `ALL` to allow the matching
  mutating order side. The default is `NONE`.
- BUY decisions use the place-order MCP tool with `transactionType=BUY`, only
  when `AGENT_ENABLE_ORDER_TOOLS=true`, `AGENT_ALLOW_TRADING=true`,
  `AGENT_ORDER_PLACEMENT_MODE` is `BUY` or `ALL`, and no duplicate exposure exists.
- SELL decisions use the exit/sell MCP tool only after the agent confirms an
  existing completed BUY/position for that symbol, and only when
  `AGENT_ENABLE_ORDER_TOOLS=true`, `AGENT_ALLOW_TRADING=true`, and
  `AGENT_ORDER_PLACEMENT_MODE` is `SELL` or `ALL`.
- HOLD decisions never place orders.
- MARKET orders use `price=0` and `triggerPrice=0`.
- The agent checks order status when an order id is returned.
- If `AGENT_ALLOW_TRADING=false`, the agent reports intended actions without
  calling order placement tools.

### Market Close Liquidation

Set `AGENT_MARKET_CLOSE_LIQUIDATION_ENABLED=true` to make the agent close open
purchased positions near market close. When enabled, any cycle that starts from
15:20:00 inclusive to before 15:30:00 in `AGENT_TIMEZONE` fetches
`/api/v1/orders/purchased`, groups remaining purchased quantity by symbol,
exchange, and product, submits SELL exit orders for all grouped positions, and
skips the normal strategy workflow for that cycle.
The liquidation path is not capped by `AGENT_MAX_ORDERS_PER_CYCLE`; the purpose
of the flag is to close all purchased positions found in that window.

This feature still requires:

- `AGENT_ENABLE_ORDER_TOOLS=true`
- `AGENT_ALLOW_TRADING=true`
- `AGENT_ORDER_PLACEMENT_MODE=SELL` or `AGENT_ORDER_PLACEMENT_MODE=ALL`

### Logs

Follow agent logs:

```bash
docker compose logs -f agent
```

Each agent run logs a `cycle_id`, trigger source, prompt preview, MCP URL, model,
completion state, elapsed time, and final output.

Useful examples:

```text
cycle_id=abc123 trigger=scheduled state=started
cycle_id=abc123 starting agent cycle model=openai-chat:gpt-4o-mini mcp_url=http://openapi-mcp:3100/mcp
cycle_id=abc123 trigger=scheduled state=completed elapsed_seconds=12.34
```

If OpenAI returns `429 Too Many Requests`, the agent has reached the model call
but the provider rejected the request due to rate limits or quota. In that case,
reduce schedule frequency, switch `AGENT_MODEL`, or fix the OpenAI account quota.

### Strategy Date Range

The agent injects an explicit candle range and interval into every run prompt.
It derives the Kite candle interval from `AGENT_CRON_MINUTES` unless
`AGENT_CANDLE_INTERVAL` is set explicitly.

For example, with `AGENT_CRON_MINUTES=*/5`, strategy calls must use:

```text
interval=5minute
```

Minute-based intervals use `AGENT_INTRADAY_LOOKBACK_DAYS`, capped to `1`, so
the date range gap stays under 2 days for Kite historical-data limits. On
`2026-05-20` with `AGENT_CRON_MINUTES=*/5`, strategy calls must use:

```text
from=2026-05-19
to=2026-05-20
interval=5minute
```

Day intervals use `AGENT_CANDLE_LOOKBACK_DAYS`, which defaults to `120`.

This prevents the model from copying old OpenAPI example dates such as
`2024-06-06`.

Set `AGENT_CANDLE_INTERVAL` only when you need to override the scheduler-derived
interval explicitly. For deterministic testing, set `AGENT_TODAY=YYYY-MM-DD`.
