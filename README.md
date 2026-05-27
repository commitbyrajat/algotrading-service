# algotrading-service

Algorithmic trading workspace built around a Java Spring Boot trading service, an OpenAPI MCP bridge, and a scheduled Python Pydantic AI agent.

This top-level README is intentionally limited to repository orientation and full-stack operations. Module-specific details live in:

- [service/README.md](service/README.md) for the Java service API, Kite auth, caching, order safety, configuration, and tests.
- [agent/README.md](agent/README.md) for the Python agent, scheduling, MCP usage, prompts, and trading-execution policy.

## Repository Components

| Path | Purpose |
|---|---|
| `service/` | Spring Boot service for Kite auth, instruments, strategies, holdings, purchased orders, and order placement |
| `agent/` | Scheduled Pydantic AI agent that operates the service through MCP and a direct JSON order tool |
| `docs/` | Additional architecture, controller, product, and strategy notes |
| `docker-compose.yml` | Redis, service build/runtime, OpenAPI MCP bridge, and agent build/runtime |
| `pom.xml` | Root Maven aggregator for the Java service module |
| `Dockerfile` | Project Dockerfile retained for service packaging workflows |

## Stack

| Layer | Technology |
|---|---|
| Java service | Java 21, Spring Boot 4, Maven |
| Technical analysis | ta4j |
| Broker integration | Zerodha Kite Connect SDK |
| Cache | Redis |
| MCP bridge | `openapi-to-mcp` container reading service OpenAPI JSON |
| Agent | Python 3.12, uv, Pydantic AI, FastAPI, APScheduler |
| Orchestration | Docker Compose |

## High-Level Architecture

```text
                 ┌────────────────────┐
                 │  Python Agent       │
                 │  scheduler + API    │
                 └─────────┬──────────┘
                           │ MCP tools
                           ▼
                 ┌────────────────────┐
                 │  OpenAPI MCP        │
                 │  /mcp               │
                 └─────────┬──────────┘
                           │ OpenAPI/HTTP
                           ▼
┌─────────┐      ┌────────────────────┐      ┌──────────────────┐
│ Redis   │◄────►│  Java Service       │◄────►│ Zerodha Kite APIs │
└─────────┘      │  REST + OpenAPI     │      └──────────────────┘
                 └────────────────────┘
```

The agent uses MCP tools for service inspection and strategy workflows. For mutating order placement it uses its own `submit_order_json` tool, which sends JSON directly to the Java service and applies agent-side execution guards.

## Safety Model

The project has two independent safety layers:

- The Java service never places orders from strategy evaluation. Orders require explicit order endpoints.
- The Java service blocks order placement on weekends and at or after the configured weekday market close time, `TRADING_MARKET_CLOSE_TIME` defaulting to `15:30` in `Asia/Kolkata`.
- The agent never places orders unless `AGENT_ENABLE_ORDER_TOOLS=true`, `AGENT_ALLOW_TRADING=true`, and `AGENT_ORDER_PLACEMENT_MODE` permits the side.
- The agent can either use service-provided BUY `suggestedQuantity` values or the fixed `AGENT_ORDER_QUANTITY`, controlled by `AGENT_USE_STRATEGY_QUANTITY_RECOMMENDATION`.

SELL orders are additionally guarded by the Java service using purchased-order and holdings state. See [service/README.md](service/README.md) and [agent/README.md](agent/README.md) for the detailed rules.

## Prerequisites

- Docker and Docker Compose
- Java 21 and Maven 3.9+ for local service development
- Python 3.12 and `uv` for local agent development
- Zerodha Kite Connect app credentials
- OpenAI API key for the agent

Historical data and live order APIs require an appropriately enabled Zerodha Kite Connect app/account.

## Required Environment

At minimum, the full stack needs:

```bash
export KITE_API_KEY=...
export KITE_API_SECRET=...
export KITE_USER_ID=...
export KITE_REDIRECT_URL=http://127.0.0.1:8080/api/v1/kite/callback
export OPENAI_API_KEY=...
```

Most service and agent settings have defaults in `docker-compose.yml`, `service/src/main/resources/application.yml`, and the agent config. Keep secrets in your shell or a local `.env` file that is not committed.

Common operational toggles:

| Variable | Default | Applies to | Purpose |
|---|---:|---|---|
| `TRADING_MARKET_CLOSE_TIME` | `15:30` | Service | Blocks service order placement at or after this IST weekday time |
| `AGENT_ENABLE_ORDER_TOOLS` | `false` | Agent | Allows order, holdings, purchased-order, and order-status tools |
| `AGENT_ALLOW_TRADING` | `false` | Agent | Allows mutating order placement when other guards also pass |
| `AGENT_ORDER_PLACEMENT_MODE` | `NONE` | Agent | Permitted order side: `NONE`, `BUY`, `SELL`, or `ALL` |
| `AGENT_USE_STRATEGY_QUANTITY_RECOMMENDATION` | `true` | Agent | Uses non-null strategy BUY quantity recommendations instead of fixed quantity |
| `AGENT_ORDER_QUANTITY` | `1` | Agent | Fixed fallback quantity when strategy quantity recommendations are disabled or absent |

## Quick Start: Full Stack

From the repository root:

```bash
docker compose up
```

This starts:

- Redis
- Maven build container for the Java service
- Java service runtime
- OpenAPI MCP bridge
- uv build container for the Python agent
- Python agent runtime

Useful URLs:

| Component | URL |
|---|---|
| Java service | `http://localhost:8080` |
| Service OpenAPI UI | `http://localhost:8080/swagger-ui.html` |
| Service OpenAPI JSON | `http://localhost:8080/v3/api-docs` |
| MCP server | `http://localhost:3100/mcp` |
| Agent HTTP API | `http://localhost:8090` |
| Redis | `localhost:6379` |

Follow logs:

```bash
docker compose logs -f app
docker compose logs -f openapi-mcp
docker compose logs -f agent
```

Rebuild after code/config changes:

```bash
docker compose up --build --force-recreate
```

## Running Parts Separately

Start only the service dependencies:

```bash
docker compose up redis app openapi-mcp
```

Run one agent cycle locally:

```bash
cd agent
OPENAI_API_KEY=... uv run --locked python main.py --once
```

Run Java service tests:

```bash
mvn -pl service test
```

Run agent dependency sync:

```bash
cd agent
uv sync --locked
```

## Common Workflows

Authenticate Kite session:

```bash
curl http://localhost:8080/api/v1/kite/login-url
```

Open the returned login URL in a browser. The service receives the callback, sets the Kite access token, and caches it in Redis.

Check Java service health/auth:

```bash
curl http://localhost:8080/api/v1/kite/status
```

Check agent health/status:

```bash
curl http://localhost:8090/health
curl http://localhost:8090/status
```

Trigger one agent cycle:

```bash
curl -X POST http://localhost:8090/agent/run \
  -H "Content-Type: application/json" \
  -d '{}'
```

## Documentation Map

| Document | Use it for |
|---|---|
| [service/README.md](service/README.md) | Service API, configuration, order validation, caches, tests, troubleshooting |
| [agent/README.md](agent/README.md) | Agent configuration, prompt constraints, scheduler, local JSON order tool, liquidation |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Service architecture notes |
| [docs/CONTROLLER_ENDPOINTS_AND_STRATEGIES.md](docs/CONTROLLER_ENDPOINTS_AND_STRATEGIES.md) | Detailed endpoint and strategy behavior notes |
| [docs/TA4J_STRATEGY_IMPLEMENTATION.md](docs/TA4J_STRATEGY_IMPLEMENTATION.md) | ta4j strategy implementation details |
| [docs/PRD.md](docs/PRD.md) | Product requirements |
| [docs/PROJECT_CONTEXT.md](docs/PROJECT_CONTEXT.md) | Project context notes |

## Repository Status Notes

The service and agent READMEs are the source of truth for module-specific behavior. When changing endpoint behavior, service configuration, agent environment variables, or order execution policy, update the corresponding module README first and keep this file at the cross-stack level.
