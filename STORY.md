# Story: AI-Assisted Trading Operations Platform

## Positioning

This product is an **AI-assisted trading operations platform**.

It sits between a trading signal tool, a broker API layer, and an execution automation system.

Short category line:

> AI trading operations layer for safer broker-connected strategy workflows.

The platform helps B2C and B2B businesses offer AI-assisted trading workflows. It can watch a focused market list, evaluate strategies, check account context, explain signals, and apply execution rules.

It does not blindly place trades. AI supervises and explains the workflow, while order placement stays controlled by permissions, safety rules, and service validation.

## Why This Is Needed Now

Trading customers now expect more than charts and alerts. They want software that can help them understand market context and take the next step.

At the same time, broker APIs make trading workflows easier to automate. This creates both opportunity and risk. A product can now connect market data, positions, strategy signals, and order placement. But it must not turn every signal into a live order.

Modern trading products need:

- AI-assisted review,
- clear signal explanations,
- broker-connected data,
- account and position context,
- controlled execution,
- simple reports,
- safety boundaries.

This platform gives businesses a way to adopt AI in trading without making AI an uncontrolled order engine.

## Target Audience

### B2C Businesses

B2C businesses can use this platform to build:

- retail trading assistants,
- watchlist monitoring tools,
- portfolio signal summaries,
- trading education products,
- paid trader community tools,
- private or local AI trading helpers.

The B2C value is simple: give customers a better daily trading routine with less noise and clearer decisions.

### B2B Businesses

B2B businesses can use this platform to build:

- broker-connected strategy assistants,
- advisory review workflows,
- research desk tools,
- trader workstations,
- white-labeled trading intelligence,
- safer algo-style workflows.

The B2B value is control: faster product delivery, repeatable workflows, and safer execution rules.

## Customer Problem

Most trading workflows are still manual.

A trader often has to:

1. Check a watchlist.
2. Open charts.
3. Read indicators.
4. Check holdings.
5. Review recent orders.
6. Decide if a signal still matters.
7. Place or skip an order.
8. Remember to exit positions before market close.

This is tiring and easy to get wrong.

Businesses see the same problem at scale. Their customers want AI help, but the business cannot allow uncontrolled trade execution.

The missing piece is not another indicator. The missing piece is a safe AI workflow around trading decisions.

## Solution Workflow

The platform turns trading into a guided workflow:

```text
Login -> Check watchlist -> Evaluate strategies -> Check positions -> Use AI to reason -> Apply execution rules -> Report
```

AI helps with:

- reading the current trading context,
- using available tools,
- comparing strategy results,
- explaining `BUY`, `SELL`, or `HOLD`,
- summarizing risks,
- explaining skipped or blocked actions.

AI is not given full control.

Orders are still controlled by:

- user permissions,
- allowed instruments,
- broker session state,
- market hours,
- allowed order side,
- quantity limits,
- service validation.

This makes AI a supervisor, not an uncontrolled trader.

## Current Feature Catalog

The application already includes the main building blocks for an AI-assisted trading operations product.

### Broker Connection and Session

- Broker login URL flow.
- Broker callback handling.
- Access token restore from cache after restart.
- Authentication status endpoint.
- Broker credentials and redirect URL through environment configuration.

Use:

- A B2C app can let a user connect their trading account.
- A B2B platform can build broker-connected workflows without exposing broker details to every feature team.

### Instrument and Market Data

- Instrument master lookup.
- Lookup by trading symbol.
- Mixed lookup by symbol or exchange token.
- Historical candle fetch through the broker data API.
- Optional exchange filtering.
- Instrument lookup caching.

Use:

- A product can resolve a customer watchlist before strategy checks.
- A research workflow can evaluate only approved instruments.

### Strategy Engine

- Strategy registry.
- Single-strategy evaluation.
- `ALL` mode to evaluate all registered strategies for one instrument.
- Structured decisions with `BUY`, `SELL`, or `HOLD`.
- Human-readable decision reasons.
- ATR (Average True Range) based BUY quantity suggestion.

Implemented strategies:

| Strategy | Full form | Usage |
|---|---|---|
| `SMA_CROSSOVER` | Simple Moving Average Crossover | Finds trend-change signals when fast and slow averages cross |
| `RSI_MEAN_REVERSION` | Relative Strength Index Mean Reversion | Finds possible reversal setups when RSI is oversold or overbought |
| `GAINZ_ALPHA_V2` | Multi-indicator trend, momentum, and volume strategy | Looks for stronger signals where trend, momentum, MACD, and volume agree |
| `ALL` | All registered strategies | Compares multiple strategy outputs in one review |

Use:

- A B2C product can show simple strategy explanations to users.
- A B2B platform can standardize approved strategy checks across teams.

### Holdings, Orders, and Position Context

- Holdings endpoint.
- Holdings by trading symbol.
- Completed BUY order lookup.
- Purchased-order cache.
- Purchased-order cache clear endpoint.
- Order status lookup.
- Explicit BUY or SELL order placement.
- Explicit exit-position endpoint.
- SELL validation against purchased orders and holdings.

Use:

- The assistant can check position context before action.
- A business can reduce the risk of SELL orders without position evidence.

### Execution Safety

- Strategy evaluation never places orders.
- Order placement uses separate explicit endpoints.
- Market-hours guard blocks orders on weekends and at or after configured close time.
- Market orders use broker market protection.
- Agent-side execution gates:
  - order tools enabled or disabled,
  - trading enabled or disabled,
  - order side mode: `NONE`, `BUY`, `SELL`, or `ALL`,
  - maximum orders per cycle,
  - configured exchange check.

Modes:

- Dry-run advisory mode: evaluates, explains, and reports, but does not submit real or simulated orders.
- BUY assistance only.
- SELL assistance only.
- Full execution with strict rules.

Current note:

- `AGENT_ALLOW_TRADING=false` enables dry-run advisory mode. The agent can inspect data, evaluate strategies, and create reports, but it does not place real orders and it does not create simulated paper orders.

### AI Agent and Scheduling

- Scheduled agent cycle.
- Manual agent trigger endpoint.
- Agent health endpoint.
- Agent status endpoint.
- Configured watchlist universe.
- Configured strategy list.
- Configured candle interval and lookback.
- OpenAI-compatible model support.
- Local model support through a custom base URL.
- Docker support for local model endpoints.
- Direct JSON order tool with extra execution guards.
- Market-close liquidation workflow between 15:20 and 15:30 in configured timezone.

Use:

- B2C products can offer a premium AI trading assistant.
- B2B platforms can run scheduled supervision workflows for approved watchlists.

### Runtime and Integration

- Java service API.
- OpenAPI documentation.
- OpenAPI-to-tool bridge for AI workflows.
- Redis-backed cache.
- Docker Compose stack for service, cache, tool bridge, and agent.
- Environment-driven configuration.

Use:

- A business can run the full stack locally or in a controlled environment.
- Product teams can integrate through HTTP APIs or AI tool workflows.

## Example Use Cases

### B2C: AI Assistant for Retail Traders

A retail trading app wants to launch a premium assistant for active traders.

The customer sets a watchlist and chooses strategies. The assistant runs every few minutes. It checks the watchlist, evaluates strategies, checks position context, and returns a simple report.

Example report:

```text
Market check complete.
5 instruments checked.
3 strategies evaluated.
1 BUY candidate, 0 SELL candidates, 4 HOLD.
Execution mode: disabled.
Action needed: review RVNL before placing any order.
Reason: trend and momentum are positive, but volume should be checked.
```

The customer gets useful help without trusting a black box.

The B2C business gets a stronger product: not just alerts, but an AI-guided trading routine.

### B2B: AI Layer for Trading Products

A fintech company wants to offer AI-assisted strategy workflows to retail customers.

The product must be smart, but it must also be safe. It needs strategy evaluation, portfolio context, order workflows, and clear safety controls.

This platform gives the business a base to build on:

- strategy results are structured,
- execution needs permission,
- AI actions are bounded,
- order placement is explicit,
- market-hour rules are enforced,
- workflows can run on a schedule,
- reports explain what happened.

This helps the business offer assisted trading intelligence instead of blind automation.

### B2B: Advisory and Research Teams

An advisory or research team wants all analysts to follow the same review process.

With this platform, the team can:

1. Define approved instruments.
2. Define approved strategies.
3. Run scheduled checks.
4. Use AI to summarize signal context.
5. Keep execution disabled or send it for approval.
6. Keep reports for review.

The value is consistency. The team can move faster, but still keep a clear boundary between research and execution.

## Sales Pitch

For B2C businesses, this platform creates an AI trading companion that can watch a customer’s list, explain signals, and support action without forcing full automation.

For B2B businesses, it creates a trading intelligence layer that can fit inside advisory platforms, trader workstations, fintech products, or white-labeled tools.

The pitch:

- Give customers AI-assisted trading supervision, not just alerts.
- Turn strategy signals into clear decisions.
- Help businesses adopt AI without losing execution control.
- Support cloud or local AI based on business needs.
- Keep analysis, recommendation, and order placement as separate steps.
- Build safer trading products faster.

This is not AI that promises to beat the market. It is AI that helps customers trade with more discipline.

## Business Value

| Business need | Value |
|---|---|
| Build a smarter trading product | Adds AI supervision and explanation |
| Move beyond alerts | Gives context-aware reports |
| Reduce manual work | Automates watchlist and strategy checks |
| Avoid unsafe automation | Keeps execution gated |
| Support privacy needs | Allows local or private AI models |
| Support advisory teams | Creates repeatable review workflows |
| Build broker-connected features faster | Provides APIs for data, holdings, orders, and status |
| Build customer trust | Explains actions, skipped actions, and blocked actions |

## Why This Wins

Many products stop at alerts. Alerts only say that something happened.

This platform says:

- what happened,
- why it matters,
- what context was checked,
- what action is allowed,
- what was skipped,
- what was blocked.

That is the difference between an alerting tool and an AI-driven trading workflow.

## Acceptance Story

A B2C product manager should be able to say:

> We can offer customers an AI trading assistant that checks their watchlist, explains signals, and keeps execution disabled unless the customer enables it.

A B2B platform owner should be able to say:

> We can add AI trading intelligence to our product while keeping execution controls, reviewable decisions, and safety rules.

An end customer should be able to say:

> I get useful trading help without trusting a black box to trade freely for me.

## Roadmap

The current product is a strong base. The next roadmap can grow in two directions: business features and technical maturity.

### Functional Roadmap

1. Paper trading mode.
   Add simulated orders, simulated fills, virtual positions, and paper P&L. This is different from the current dry-run advisory mode, which only evaluates and reports.

2. Backtesting and performance reports.
   Show how each strategy performed on past data before customers trust it in live workflows.

3. User-level watchlists.
   Allow each customer or business team to maintain their own approved instruments.

4. Strategy library.
   Let businesses package approved strategies with descriptions, risk notes, and usage rules.

5. Approval workflow for orders.
   Add a human approval step between AI recommendation and live execution.

6. Risk dashboard.
   Show exposure, open positions, pending orders, daily order count, and blocked actions.

7. Alert delivery.
   Send reports through email, chat, mobile push, or business messaging tools.

8. Multi-broker support.
   Add more broker adapters while keeping the same strategy and AI workflow.

9. Multi-user support.
   Separate broker sessions, watchlists, permissions, and audit trails by user or tenant.

10. Compliance-ready audit logs.
    Store every signal, model summary, tool call, approval, order request, and blocked action.

### Technical Roadmap

1. Stronger configuration model.
   Externalize all remaining strategy parameters, cache settings, and market-session rules.

2. Integration tests.
   Add tests for Redis, broker adapter boundaries, order safety, and agent-service workflows.

3. Model provider abstraction.
   Support multiple AI providers and local models through a clean provider layer.

4. Safer agent tool policy.
   Add stricter tool allowlists by mode, customer, and environment.

5. Idempotency for orders.
   Prevent duplicate order submissions during retries or repeated agent cycles.

6. Event-driven architecture.
   Publish strategy decisions, order actions, and blocked actions as events.

7. Observability.
   Add metrics, traces, dashboards, and alerting for service health and trading workflow health.

8. Secret management.
   Move credentials and tokens to a managed secret store for production use.

9. Deployment hardening.
   Add production profiles, health checks, rollout strategy, and resource limits.

10. Security and access control.
    Add authentication, authorization, tenant isolation, and role-based permissions.

## Boundaries

This platform is not investment advice. It does not guarantee profit. It does not replace compliance, risk review, or human responsibility.

Strategy quality, capital use, taxes, slippage, broker issues, and compliance duties remain the responsibility of the business using the product.

The strongest claim is not prediction. The strongest claim is controlled intelligence.

## External References

- Zerodha Kite Connect APIs: https://zerodha.com/products/api/
- NSE market timings: https://www.nseindia.com/resources/exchange-communication-holidays
- SEBI circular on safer retail algo participation: https://www.sebi.gov.in/legal/circulars/feb-2025/safer-participation-of-retail-investors-in-algorithmic-trading_91614.html
- Zerodha market protection explainer: https://support.zerodha.com/category/trading-and-markets/charts-and-orders/order/articles/market-price-protection-on-the-order-window
- Simple Moving Average reference: https://www.fidelity.com/learning-center/trading-investing/technical-analysis/technical-indicator-guide/sma
- Relative Strength Index reference: https://www.techopedia.com/definition/relative-strength-index-rsi
- Moving Average Convergence Divergence reference: https://www.britannica.com/money/macd-moving-average-convergence-divergence
- Average True Range reference: https://www.capital.com/en-int/learn/technical-analysis/average-true-range-trading-strategy
