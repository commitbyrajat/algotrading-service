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
export AGENT_CRON_MINUTES="*/5"
export AGENT_TIMEZONE="Asia/Kolkata"
export AGENT_ALLOW_TRADING="false"
export AGENT_HTTP_HOST="0.0.0.0"
export AGENT_HTTP_PORT="8090"
export AGENT_LOG_LEVEL="INFO"
export AGENT_CANDLE_LOOKBACK_DAYS="120"
export AGENT_CANDLE_INTERVAL="day"
export AGENT_ORDER_QUANTITY="1"
export AGENT_ORDER_PRODUCT="CNC"
export AGENT_ORDER_TYPE="MARKET"
export AGENT_MAX_ORDERS_PER_CYCLE="2"
export AGENT_TODAY="2026-05-20"
export AGENT_INSTRUMENT_UNIVERSE="SHEKHAWATI
DRCSYSTEMS
EASEMYTRIP"
```

By default, the agent uses trading symbols derived from the first page of the
Screener "Best Penny Stocks" screen. The Screener display names are not passed
to the broker instrument API because `/api/v1/instruments/by-symbols` matches
exact Kite `tradingSymbol` values.

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

Order placement is disabled by default. To allow the agent to place orders from
its strategy recommendations:

```bash
export AGENT_ALLOW_TRADING=true
export AGENT_ORDER_QUANTITY=1
export AGENT_ORDER_PRODUCT=CNC
export AGENT_ORDER_TYPE=MARKET
export AGENT_MAX_ORDERS_PER_CYCLE=2
docker compose up -d agent
```

Execution rules:

- BUY signals use the place-order MCP tool with `transactionType=BUY`.
- SELL signals use the exit/sell MCP tool only after the agent confirms an
  existing completed BUY/position for that symbol.
- MARKET orders use `price=0` and `triggerPrice=0`.
- The agent checks order status when an order id is returned.
- If `AGENT_ALLOW_TRADING=false`, the agent reports intended actions without
  calling order placement tools.

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

The agent injects an explicit candle range into every run prompt. By default it
uses the last 120 calendar days ending on today's date in `AGENT_TIMEZONE`.

For example, on `2026-05-20` with the default settings, strategy calls must use:

```text
from=2026-01-20
to=2026-05-20
interval=day
```

This prevents the model from copying old OpenAPI example dates such as
`2024-06-06`.

For deterministic testing, set `AGENT_TODAY=YYYY-MM-DD`.
