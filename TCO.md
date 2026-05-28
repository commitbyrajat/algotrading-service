# Total Cost of Ownership

## Purpose

This document provides a placeholder-based Total Cost of Ownership model for building and running the AI-assisted trading operations platform.

Actual values should be filled after choosing:

- delivery location,
- salary or vendor rates,
- cloud provider,
- LLM provider or local model setup,
- broker API plan,
- production scale.

## Cost Categories

The TCO is composed of four primary cost categories:

1. Development team cost
2. Cloud infrastructure cost
3. LLM cost
4. Broker platform cost

## 1. Development Team Cost

Development cost includes onboarding and running the delivery team needed to build, test, deploy, and maintain the platform.

### Team Composition

| Role | Count | Monthly cost per person | Monthly total | Annual total |
|---|---:|---:|---:|---:|
| Backend Developer | 4 | `<BACKEND_DEV_MONTHLY_COST>` | `<4 * BACKEND_DEV_MONTHLY_COST>` | `<MONTHLY_TOTAL * 12>` |
| Frontend Developer | 2 | `<FRONTEND_DEV_MONTHLY_COST>` | `<2 * FRONTEND_DEV_MONTHLY_COST>` | `<MONTHLY_TOTAL * 12>` |
| QA Engineer | 2 | `<QA_MONTHLY_COST>` | `<2 * QA_MONTHLY_COST>` | `<MONTHLY_TOTAL * 12>` |
| DevOps Engineer | 2 | `<DEVOPS_MONTHLY_COST>` | `<2 * DEVOPS_MONTHLY_COST>` | `<MONTHLY_TOTAL * 12>` |
| UX Designer | 1 | `<UX_MONTHLY_COST>` | `<1 * UX_MONTHLY_COST>` | `<MONTHLY_TOTAL * 12>` |
| Lead Engineer / Technical Architect | 1 | `<LEAD_ARCHITECT_MONTHLY_COST>` | `<1 * LEAD_ARCHITECT_MONTHLY_COST>` | `<MONTHLY_TOTAL * 12>` |

### Development Cost Formula

```text
monthly_development_cost =
  (4 * BACKEND_DEV_MONTHLY_COST)
+ (2 * FRONTEND_DEV_MONTHLY_COST)
+ (2 * QA_MONTHLY_COST)
+ (2 * DEVOPS_MONTHLY_COST)
+ (1 * UX_MONTHLY_COST)
+ (1 * LEAD_ARCHITECT_MONTHLY_COST)
```

```text
annual_development_cost =
  monthly_development_cost * 12
```

### Development Cost Placeholder

| Item | Value |
|---|---:|
| Monthly development cost | `<MONTHLY_DEVELOPMENT_COST>` |
| Annual development cost | `<ANNUAL_DEVELOPMENT_COST>` |

## 2. Cloud Infrastructure Cost

Choose one cloud provider:

- AWS
- GCP
- Azure

The platform can run on containerized infrastructure and needs compute, cache, storage, network, monitoring, and security services.

### Cloud Cost Components

| Cloud component | Purpose | Monthly cost placeholder | Annual cost placeholder |
|---|---|---:|---:|
| Application compute | Java service, Python agent, OpenAPI tool bridge | `<APP_COMPUTE_MONTHLY>` | `<APP_COMPUTE_MONTHLY * 12>` |
| Container runtime | ECS/EKS, GKE, AKS, App Service, or equivalent | `<CONTAINER_RUNTIME_MONTHLY>` | `<CONTAINER_RUNTIME_MONTHLY * 12>` |
| Redis/cache | Token cache, instrument cache, holdings/orders cache | `<REDIS_MONTHLY>` | `<REDIS_MONTHLY * 12>` |
| Persistent storage | Reports, audit logs, paper trading history when added | `<STORAGE_MONTHLY>` | `<STORAGE_MONTHLY * 12>` |
| Database | Users, tenants, configuration, audit, product data | `<DATABASE_MONTHLY>` | `<DATABASE_MONTHLY * 12>` |
| Load balancer / gateway | Public or private traffic routing | `<LOAD_BALANCER_MONTHLY>` | `<LOAD_BALANCER_MONTHLY * 12>` |
| Network transfer | API traffic, model calls, broker calls, user traffic | `<NETWORK_MONTHLY>` | `<NETWORK_MONTHLY * 12>` |
| Monitoring and logs | Logs, metrics, traces, alerts, dashboards | `<OBSERVABILITY_MONTHLY>` | `<OBSERVABILITY_MONTHLY * 12>` |
| Secrets management | API keys, broker secrets, model provider credentials | `<SECRETS_MONTHLY>` | `<SECRETS_MONTHLY * 12>` |
| Backup and recovery | Redis/database backups, restore testing | `<BACKUP_MONTHLY>` | `<BACKUP_MONTHLY * 12>` |
| Security tooling | WAF, vulnerability scanning, image scanning, IAM controls | `<SECURITY_MONTHLY>` | `<SECURITY_MONTHLY * 12>` |

### Cloud Cost Formula

```text
monthly_cloud_cost =
  APP_COMPUTE_MONTHLY
+ CONTAINER_RUNTIME_MONTHLY
+ REDIS_MONTHLY
+ STORAGE_MONTHLY
+ DATABASE_MONTHLY
+ LOAD_BALANCER_MONTHLY
+ NETWORK_MONTHLY
+ OBSERVABILITY_MONTHLY
+ SECRETS_MONTHLY
+ BACKUP_MONTHLY
+ SECURITY_MONTHLY
```

```text
annual_cloud_cost =
  monthly_cloud_cost * 12
```

### Cloud Cost Placeholder

| Item | Value |
|---|---:|
| Selected cloud provider | `<AWS_OR_GCP_OR_AZURE>` |
| Monthly cloud cost | `<MONTHLY_CLOUD_COST>` |
| Annual cloud cost | `<ANNUAL_CLOUD_COST>` |

## 3. LLM Cost

The AI agent uses an OpenAI-compatible model endpoint through:

```text
OPENAI_API_KEY
OPENAI_BASE_URL
AGENT_MODEL
```

The LLM cost depends on:

- selected model,
- number of agent cycles,
- watchlist size,
- number of strategies,
- prompt size,
- report size,
- whether the model is hosted or local.

### LLM Usage Inputs

| Input | Placeholder |
|---|---:|
| Agent runs per day | `<AGENT_RUNS_PER_DAY>` |
| Trading days per month | `<TRADING_DAYS_PER_MONTH>` |
| Model calls per run | `<MODEL_CALLS_PER_RUN>` |
| Average input tokens per call | `<AVG_INPUT_TOKENS_PER_CALL>` |
| Average output tokens per call | `<AVG_OUTPUT_TOKENS_PER_CALL>` |
| Input token price | `<INPUT_TOKEN_PRICE>` |
| Output token price | `<OUTPUT_TOKEN_PRICE>` |

### Hosted LLM Cost Formula

```text
monthly_model_calls =
  AGENT_RUNS_PER_DAY
* TRADING_DAYS_PER_MONTH
* MODEL_CALLS_PER_RUN
```

```text
monthly_input_tokens =
  monthly_model_calls
* AVG_INPUT_TOKENS_PER_CALL
```

```text
monthly_output_tokens =
  monthly_model_calls
* AVG_OUTPUT_TOKENS_PER_CALL
```

```text
monthly_llm_cost =
  (monthly_input_tokens * INPUT_TOKEN_PRICE)
+ (monthly_output_tokens * OUTPUT_TOKEN_PRICE)
```

```text
annual_llm_cost =
  monthly_llm_cost * 12
```

### Local LLM Cost Placeholders

Use this section if the model is hosted locally through Ollama or another OpenAI-compatible local endpoint.

| Cost item | Monthly cost placeholder | Annual cost placeholder |
|---|---:|---:|
| GPU/CPU machine amortization | `<LOCAL_MODEL_HARDWARE_MONTHLY>` | `<LOCAL_MODEL_HARDWARE_MONTHLY * 12>` |
| Hosting or data center cost | `<LOCAL_MODEL_HOSTING_MONTHLY>` | `<LOCAL_MODEL_HOSTING_MONTHLY * 12>` |
| Power and cooling | `<LOCAL_MODEL_POWER_MONTHLY>` | `<LOCAL_MODEL_POWER_MONTHLY * 12>` |
| Maintenance and tuning | `<LOCAL_MODEL_MAINTENANCE_MONTHLY>` | `<LOCAL_MODEL_MAINTENANCE_MONTHLY * 12>` |

```text
monthly_local_llm_cost =
  LOCAL_MODEL_HARDWARE_MONTHLY
+ LOCAL_MODEL_HOSTING_MONTHLY
+ LOCAL_MODEL_POWER_MONTHLY
+ LOCAL_MODEL_MAINTENANCE_MONTHLY
```

### LLM Cost Placeholder

| Item | Value |
|---|---:|
| LLM approach | `<HOSTED_OR_LOCAL>` |
| Selected model | `<SELECTED_MODEL>` |
| Monthly LLM cost | `<MONTHLY_LLM_COST>` |
| Annual LLM cost | `<ANNUAL_LLM_COST>` |

## 4. Broker Platform Cost

The platform currently integrates with Zerodha Kite Connect APIs.

Broker platform cost may include:

- Kite Connect API subscription,
- market data access,
- trading account charges,
- exchange or data feed charges,
- broker API limits or add-ons,
- support or commercial broker partnership costs.

### Broker Cost Components

| Broker cost item | Monthly cost placeholder | Annual cost placeholder |
|---|---:|---:|
| Kite Connect API subscription | `<KITE_API_MONTHLY>` | `<KITE_API_MONTHLY * 12>` |
| Market data access | `<MARKET_DATA_MONTHLY>` | `<MARKET_DATA_MONTHLY * 12>` |
| Broker account charges | `<BROKER_ACCOUNT_MONTHLY>` | `<BROKER_ACCOUNT_MONTHLY * 12>` |
| Exchange/data feed charges | `<EXCHANGE_DATA_MONTHLY>` | `<EXCHANGE_DATA_MONTHLY * 12>` |
| Broker support or partnership | `<BROKER_SUPPORT_MONTHLY>` | `<BROKER_SUPPORT_MONTHLY * 12>` |

### Broker Cost Formula

```text
monthly_broker_platform_cost =
  KITE_API_MONTHLY
+ MARKET_DATA_MONTHLY
+ BROKER_ACCOUNT_MONTHLY
+ EXCHANGE_DATA_MONTHLY
+ BROKER_SUPPORT_MONTHLY
```

```text
annual_broker_platform_cost =
  monthly_broker_platform_cost * 12
```

### Broker Cost Placeholder

| Item | Value |
|---|---:|
| Broker platform | `Zerodha Kite Connect` |
| Monthly broker platform cost | `<MONTHLY_BROKER_PLATFORM_COST>` |
| Annual broker platform cost | `<ANNUAL_BROKER_PLATFORM_COST>` |

## Overall TCO

### Monthly TCO Formula

```text
monthly_tco =
  MONTHLY_DEVELOPMENT_COST
+ MONTHLY_CLOUD_COST
+ MONTHLY_LLM_COST
+ MONTHLY_BROKER_PLATFORM_COST
```

### Annual TCO Formula

```text
annual_tco =
  ANNUAL_DEVELOPMENT_COST
+ ANNUAL_CLOUD_COST
+ ANNUAL_LLM_COST
+ ANNUAL_BROKER_PLATFORM_COST
```

### Overall TCO Placeholder

| Cost category | Monthly cost | Annual cost |
|---|---:|---:|
| Development team | `<MONTHLY_DEVELOPMENT_COST>` | `<ANNUAL_DEVELOPMENT_COST>` |
| Cloud infrastructure | `<MONTHLY_CLOUD_COST>` | `<ANNUAL_CLOUD_COST>` |
| LLM | `<MONTHLY_LLM_COST>` | `<ANNUAL_LLM_COST>` |
| Broker platform | `<MONTHLY_BROKER_PLATFORM_COST>` | `<ANNUAL_BROKER_PLATFORM_COST>` |
| **Total** | `<MONTHLY_TCO>` | `<ANNUAL_TCO>` |

## Optional Contingency

Add contingency to cover estimation error, vendor price changes, additional environments, compliance work, and production incidents.

Recommended placeholder:

```text
contingency = MONTHLY_TCO * <CONTINGENCY_PERCENT>
```

Suggested range:

```text
CONTINGENCY_PERCENT = 15% to 25%
```

### TCO With Contingency

| Item | Monthly value | Annual value |
|---|---:|---:|
| Base TCO | `<MONTHLY_TCO>` | `<ANNUAL_TCO>` |
| Contingency | `<MONTHLY_CONTINGENCY>` | `<ANNUAL_CONTINGENCY>` |
| **TCO with contingency** | `<MONTHLY_TCO_WITH_CONTINGENCY>` | `<ANNUAL_TCO_WITH_CONTINGENCY>` |

## Notes

- Development team cost is expected to be the largest early-stage cost.
- Cloud cost increases with production environments, traffic, monitoring, storage, and high availability.
- LLM cost increases with agent run frequency, watchlist size, strategy count, and prompt size.
- Broker platform cost depends on the selected broker plan and market data access.
- Paper trading, audit logs, approval workflows, and multi-user support will increase both cloud and development cost.
