from __future__ import annotations

import os
from dataclasses import dataclass
from datetime import date, datetime, timedelta
from zoneinfo import ZoneInfo


def _bool_env(name: str, default: bool = False) -> bool:
    value = os.getenv(name)
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "y", "on"}


@dataclass(frozen=True)
class AgentConfig:
    model: str
    mcp_url: str
    cron_minutes: str
    http_host: str
    http_port: int
    allow_trading: bool
    prompt: str
    instrument_universe: str
    candle_lookback_days: int
    candle_interval: str
    order_quantity: int
    order_product: str
    order_type: str
    max_orders_per_cycle: int
    today_override: date | None
    timezone: str

    @classmethod
    def from_env(cls) -> "AgentConfig":
        return cls(
            model=os.getenv("AGENT_MODEL", "openai-chat:gpt-4o-mini"),
            mcp_url=os.getenv("MCP_ENDPOINT_URL", "http://localhost:3100/mcp"),
            cron_minutes=os.getenv("AGENT_CRON_MINUTES", "*/5"),
            http_host=os.getenv("AGENT_HTTP_HOST", "0.0.0.0"),
            http_port=int(os.getenv("AGENT_HTTP_PORT", "8090")),
            allow_trading=_bool_env("AGENT_ALLOW_TRADING"),
            prompt=os.getenv("AGENT_RUN_PROMPT", default_run_prompt()),
            instrument_universe=os.getenv("AGENT_INSTRUMENT_UNIVERSE", default_instrument_universe()),
            candle_lookback_days=int(os.getenv("AGENT_CANDLE_LOOKBACK_DAYS", "120")),
            candle_interval=os.getenv("AGENT_CANDLE_INTERVAL", "day"),
            order_quantity=int(os.getenv("AGENT_ORDER_QUANTITY", "1")),
            order_product=os.getenv("AGENT_ORDER_PRODUCT", "CNC"),
            order_type=os.getenv("AGENT_ORDER_TYPE", "MARKET"),
            max_orders_per_cycle=int(os.getenv("AGENT_MAX_ORDERS_PER_CYCLE", "2")),
            today_override=_date_env("AGENT_TODAY"),
            timezone=os.getenv("AGENT_TIMEZONE", "Asia/Kolkata"),
        )

    def candle_date_range(self) -> tuple[date, date]:
        today = self.today_override or current_date(self.timezone)
        return today - timedelta(days=self.candle_lookback_days), today


def default_run_prompt() -> str:
    return (
        "Run one scheduled algo-trading supervision cycle. Use the available MCP tools "
        "to inspect current service state, auth/session status, and the configured penny-stock "
        "trading-symbol universe. Evaluate the service strategies only against those symbols "
        "that resolve to Kite instruments. If trading is enabled, place orders for actionable "
        "BUY/SELL recommendations using the configured execution policy. Summarize market or "
        "service conditions, identify actionable risks, and report any submitted orders. Be "
        "concise and include the specific API/tool evidence used."
    )


def current_date(timezone: str) -> date:
    return date.today() if not timezone else datetime.now(ZoneInfo(timezone)).date()


def _date_env(name: str) -> date | None:
    value = os.getenv(name)
    if not value:
        return None
    return date.fromisoformat(value)


def default_instrument_universe() -> str:
    return "\n".join(
        [
            "532015",
            "517393",
            "SHEKHAWATI",
            "539288",
            "RMDRIP",
            "539594",
            "530805",
            "MEDISTEP",
            "539607",
            "GATECHDVR",
            "NILASPACES",
            "538834",
            "ENSER",
            "534535",
            "521228",
            "DRCSYSTEMS",
            "AHCL",
            "531395",
            "513337",
            "IDENTICAL",
            "NILAINFRA",
            "BTML",
            "EASEMYTRIP",
            "531671",
            "543285",
        ]
    )
