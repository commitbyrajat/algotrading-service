from __future__ import annotations

import os
from dataclasses import dataclass
from datetime import date, datetime, timedelta
from pathlib import Path
from urllib.parse import urlsplit, urlunsplit
from zoneinfo import ZoneInfo


INTRADAY_MAX_LOOKBACK_DAYS = 1
ORDER_PLACEMENT_MODES = {"NONE", "BUY", "SELL", "ALL"}


def _bool_env(name: str, default: bool = False) -> bool:
    value = os.getenv(name)
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "y", "on"}


@dataclass(frozen=True)
class AgentConfig:
    model: str
    openai_api_key: str
    openai_base_url: str
    mcp_url: str
    app_base_url: str
    cron_minutes: str
    http_host: str
    http_port: int
    enable_order_tools: bool
    allow_trading: bool
    order_placement_mode: str
    prompt: str
    instrument_universe: str
    instrument_exchange: str
    strategy_names: str
    candle_lookback_days: int
    intraday_lookback_days: int
    candle_interval: str
    order_quantity: int
    use_strategy_quantity_recommendation: bool
    order_product: str
    order_type: str
    max_orders_per_cycle: int
    market_close_liquidation_enabled: bool
    today_override: date | None
    timezone: str

    @classmethod
    def from_env(cls) -> "AgentConfig":
        return cls(
            model=os.getenv("AGENT_MODEL", "openai-chat:gpt-4o-mini"),
            openai_api_key=_required_env("OPENAI_API_KEY"),
            openai_base_url=_required_url_env("OPENAI_BASE_URL"),
            mcp_url=os.getenv("MCP_ENDPOINT_URL", "http://localhost:3100/mcp"),
            app_base_url=os.getenv("AGENT_APP_BASE_URL", "http://localhost:8080").rstrip("/"),
            cron_minutes=os.getenv("AGENT_CRON_MINUTES", "*/5"),
            http_host=os.getenv("AGENT_HTTP_HOST", "0.0.0.0"),
            http_port=int(os.getenv("AGENT_HTTP_PORT", "8090")),
            enable_order_tools=_bool_env("AGENT_ENABLE_ORDER_TOOLS"),
            allow_trading=_bool_env("AGENT_ALLOW_TRADING"),
            order_placement_mode=_order_placement_mode_env("AGENT_ORDER_PLACEMENT_MODE"),
            prompt=os.getenv("AGENT_RUN_PROMPT", default_run_prompt()),
            instrument_universe=os.getenv("AGENT_INSTRUMENT_UNIVERSE", default_instrument_universe()),
            instrument_exchange=os.getenv("AGENT_INSTRUMENT_EXCHANGE", "NSE").strip().upper(),
            strategy_names=os.getenv("AGENT_STRATEGY_NAMES", default_strategy_names()),
            candle_lookback_days=int(os.getenv("AGENT_CANDLE_LOOKBACK_DAYS", "120")),
            intraday_lookback_days=int(os.getenv("AGENT_INTRADAY_LOOKBACK_DAYS", "1")),
            candle_interval=os.getenv("AGENT_CANDLE_INTERVAL", "day"),
            order_quantity=int(os.getenv("AGENT_ORDER_QUANTITY", "1")),
            use_strategy_quantity_recommendation=_bool_env(
                "AGENT_USE_STRATEGY_QUANTITY_RECOMMENDATION",
                True,
            ),
            order_product=os.getenv("AGENT_ORDER_PRODUCT", "CNC"),
            order_type=os.getenv("AGENT_ORDER_TYPE", "MARKET"),
            max_orders_per_cycle=int(os.getenv("AGENT_MAX_ORDERS_PER_CYCLE", "2")),
            market_close_liquidation_enabled=_bool_env("AGENT_MARKET_CLOSE_LIQUIDATION_ENABLED"),
            today_override=_date_env("AGENT_TODAY"),
            timezone=os.getenv("AGENT_TIMEZONE", "Asia/Kolkata"),
        )

    def candle_date_range(self) -> tuple[date, date]:
        today = self.today_override or current_date(self.timezone)
        if is_intraday_interval(self.resolved_candle_interval()):
            requested_days = max(self.intraday_lookback_days, 1)
            lookback_days = min(requested_days, INTRADAY_MAX_LOOKBACK_DAYS)
        else:
            lookback_days = max(self.candle_lookback_days, 1)
        return today - timedelta(days=lookback_days), today

    def resolved_candle_interval(self) -> str:
        explicit_interval = os.getenv("AGENT_CANDLE_INTERVAL")
        if explicit_interval and explicit_interval.strip():
            return explicit_interval.strip()
        return candle_interval_from_cron_minutes(self.cron_minutes)


def default_run_prompt() -> str:
    return (
        "Run one scheduled algo-trading supervision cycle. Follow the required workflow exactly: "
        "check service/auth status, lookup only the configured instrument universe on the configured exchange, "
        "do not fetch or scan all instruments, "
        "list registered strategies, execute only configured registered strategies for resolved instruments, inspect existing purchased/completed orders and holdings only "
        "when order tools are enabled, then decide BUY, SELL, or HOLD for each actionable result. If trading is enabled, place orders "
        "only after that decision step and according to the configured execution policy. Summarize "
        "market or service conditions, decisions, and submitted or skipped orders with specific API/tool evidence."
    )


def current_date(timezone: str) -> date:
    return date.today() if not timezone else datetime.now(ZoneInfo(timezone)).date()


def default_strategy_names() -> str:
    return "\n".join(
        [
            "ALL",
            "GAINZ_ALPHA_V2",
            "SMA_CROSSOVER",
            "RSI_MEAN_REVERSION"
        ]
    )


def candle_interval_from_cron_minutes(cron_minutes: str) -> str:
    cron_minutes = cron_minutes.strip()
    minute_value = None

    if cron_minutes.startswith("*/"):
        minute_value = cron_minutes.removeprefix("*/")
    elif cron_minutes.isdigit():
        minute_value = cron_minutes

    if minute_value is None:
        return "day"

    try:
        minutes = int(minute_value)
    except ValueError:
        return "day"

    if minutes <= 1:
        return "minute"
    if minutes in {3, 5, 10, 15, 30, 60}:
        return f"{minutes}minute"
    if minutes < 60:
        return "5minute"
    return "60minute"


def is_intraday_interval(interval: str) -> bool:
    normalized = interval.strip().lower()
    return normalized == "minute" or normalized.endswith("minute")


def _date_env(name: str) -> date | None:
    value = os.getenv(name)
    if not value:
        return None
    return date.fromisoformat(value)


def _required_env(name: str) -> str:
    value = os.getenv(name)
    if value is None or not value.strip():
        raise ValueError(f"{name} must be set")
    return value.strip()


def _required_url_env(name: str) -> str:
    return _docker_host_url(_required_env(name).rstrip("/"))


def _docker_host_url(url: str) -> str:
    parsed = urlsplit(url)
    if not _running_in_docker() or parsed.hostname not in {"localhost", "127.0.0.1"}:
        return url

    netloc = "host.docker.internal"
    if parsed.port is not None:
        netloc = f"{netloc}:{parsed.port}"
    return urlunsplit((parsed.scheme, netloc, parsed.path, parsed.query, parsed.fragment))


def _running_in_docker() -> bool:
    return Path("/.dockerenv").exists() or os.getenv("AGENT_RUNNING_IN_DOCKER") == "true"


def _order_placement_mode_env(name: str) -> str:
    value = os.getenv(name, "NONE").strip().upper()
    if value not in ORDER_PLACEMENT_MODES:
        allowed = ", ".join(sorted(ORDER_PLACEMENT_MODES))
        raise ValueError(f"{name} must be one of: {allowed}")
    return value


def default_instrument_universe() -> str:
    return "\n".join(
        [
          "JUSTDIAL",
          "QUESS",
          "IRCON",
          "GOLD360",
#           "FINEORG",
          "YESBANK",
          "IDEA",
          "SUZLON",
          "RVNL",
#           "IREDA",
#           "IRFC",
#           "HUDCO",
#           "NBCC",
#           "HFCL",
          "RBLBANK",
          "IEX",
#           "IRCTC",
#           "BHEL",
#           "NATIONALUM",
#           "INOXWIND",
          "TRIDENT",
          "JPPOWER",
          "HINDCOPPER",
          "VEDL",
#           "ACE"
        ]
#         ,[
#           "WAAREEINDO",
#           "TIPSMUSIC",
#           "NESTLEIND",
#           "MCX",
#           "HINDZINC",
#           "WEBELSOLAR",
#           "SWARAJENG",
#           "BSE",
#           "ANANDRATHI",
#           "IEX",
#           "IRCTC",
#           "GRSE",
#           "DIXON",
#           "FRONTSP",
#           "VMARCIND",
#           "KPENERGY",
#           "DSSL",
#           "SOLARINDS",
#           "GOKULAGRO",
#           "THYROCARE",
#           "JYOTIRES",
#           "BEL",
#           "LLOYDSME",
#           "MAZDOCK"
#         ]
    )
