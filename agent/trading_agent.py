from __future__ import annotations

import asyncio
import json
import logging
from collections import defaultdict
from dataclasses import dataclass
from datetime import datetime, time
from typing import Any, Literal
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen
from zoneinfo import ZoneInfo

from pydantic_ai import Agent
from pydantic_ai.mcp import MCPServerStreamableHTTP
from pydantic_ai.models.openai import OpenAIChatModel
from pydantic_ai.providers.openai import OpenAIProvider

from config import AgentConfig
from prompts import run_prompt, system_prompt

logger = logging.getLogger(__name__)

MARKET_CLOSE_LIQUIDATION_START = time(15, 20)
MARKET_CLOSE_TIME = time(15, 30)


@dataclass
class LiquidationPosition:
    trading_symbol: str
    exchange: str
    quantity: int
    product: str


class TradingMcpAgent:
    def __init__(self, config: AgentConfig):
        self.config = config
        self._submitted_order_count = 0
        self._server = MCPServerStreamableHTTP(config.mcp_url)
        self._agent = Agent(
            self._model_from_config(config),
            system_prompt=system_prompt(config),
            toolsets=[self._server],
        )
        self._register_tools()

    @staticmethod
    def _model_from_config(config: AgentConfig) -> OpenAIChatModel:
        model_name = config.model
        for prefix in ("openai-chat:", "openai:"):
            if model_name.startswith(prefix):
                model_name = model_name.removeprefix(prefix)
                break

        provider = OpenAIProvider(
            api_key=config.openai_api_key,
            base_url=config.openai_base_url,
        )
        return OpenAIChatModel(model_name, provider=provider)

    async def run_once(self, prompt: str | None = None, cycle_id: str | None = None) -> str:
        self._submitted_order_count = 0
        close_liquidation_output = await self._run_market_close_liquidation_if_due(cycle_id=cycle_id)
        if close_liquidation_output is not None:
            return close_liquidation_output

        prompt_text = run_prompt(self.config, prompt)
        from_date, to_date = self.config.candle_date_range()
        log_prefix = "cycle_id=%s " % cycle_id if cycle_id else ""
        logger.info(
            "%sstarting agent cycle model=%s mcp_url=%s from=%s to=%s interval=%s order_tools=%s trading=%s order_placement_mode=%s use_strategy_quantity_recommendation=%s max_orders=%s market_close_liquidation=%s prompt_chars=%s",
            log_prefix,
            self.config.model,
            self.config.mcp_url,
            from_date.isoformat(),
            to_date.isoformat(),
            self.config.resolved_candle_interval(),
            self.config.enable_order_tools,
            self.config.allow_trading,
            self.config.order_placement_mode,
            self.config.use_strategy_quantity_recommendation,
            self.config.max_orders_per_cycle,
            self.config.market_close_liquidation_enabled,
            len(prompt_text),
        )
        logger.info("%sprompt_preview=%s", log_prefix, self._preview(prompt_text))
        async with self._agent:
            result = await self._agent.run(prompt_text)

        output = str(result.output)
        logger.info("%sagent cycle completed output_chars=%s", log_prefix, len(output))
        return output

    def _register_tools(self) -> None:
        @self._agent.tool_plain(
            name="submit_order_json",
            description=(
                "Submit a BUY or SELL order directly to the algo-trading service using an "
                "application/json request body. Use this local tool for mutating order placement "
                "instead of the OpenAPI MCP order-placement tools."
            ),
        )
        async def submit_order_json(
            trading_symbol: str,
            transaction_type: Literal["BUY", "SELL"],
            quantity: int,
            order_type: str,
            product: str,
            exchange: str,
            price: float = 0,
            trigger_price: float = 0,
            suggested_quantity: int | None = None,
        ) -> dict[str, Any]:
            """
            Submit one JSON order to the application.

            Args:
                trading_symbol: Broker trading symbol returned by instrument lookup.
                transaction_type: BUY places a buy order; SELL exits a position.
                quantity: Number of shares or lots. For BUY, this is overridden by
                    suggested_quantity when strategy quantity recommendation mode is enabled.
                order_type: Kite order type such as MARKET, LIMIT, SL, or SL-M.
                product: Kite product such as CNC, MIS, or NRML.
                exchange: Exchange segment, normally NSE.
                price: Limit price, or 0 for MARKET.
                trigger_price: Trigger price for stop-loss order types, otherwise 0.
                suggested_quantity: Strategy response suggestedQuantity for BUY decisions,
                    when present.
            """
            payload, path = self._build_order_payload(
                trading_symbol=trading_symbol,
                transaction_type=transaction_type,
                quantity=quantity,
                order_type=order_type,
                product=product,
                exchange=exchange,
                price=price,
                trigger_price=trigger_price,
                suggested_quantity=suggested_quantity,
            )
            url = f"{self.config.app_base_url}{path}"
            return await asyncio.to_thread(self._post_json, url, payload)

    async def _run_market_close_liquidation_if_due(self, cycle_id: str | None = None) -> str | None:
        if not self.config.market_close_liquidation_enabled:
            return None

        now = datetime.now(ZoneInfo(self.config.timezone))
        if not (MARKET_CLOSE_LIQUIDATION_START <= now.time() < MARKET_CLOSE_TIME):
            return None

        log_prefix = "cycle_id=%s " % cycle_id if cycle_id else ""
        logger.info(
            "%smarket_close_liquidation started now=%s timezone=%s window=%s-%s",
            log_prefix,
            now.isoformat(),
            self.config.timezone,
            MARKET_CLOSE_LIQUIDATION_START.isoformat(timespec="minutes"),
            MARKET_CLOSE_TIME.isoformat(timespec="minutes"),
        )

        blockers = self._market_close_liquidation_blockers()
        if blockers:
            logger.warning(
                "%smarket_close_liquidation blocked blockers=%s",
                log_prefix,
                blockers,
            )
            return self._market_close_liquidation_report(
                now=now,
                positions=[],
                submitted=[],
                blockers=blockers,
            )

        purchased_orders_url = f"{self.config.app_base_url}/api/v1/orders/purchased"
        try:
            purchased_orders = await asyncio.to_thread(self._get_json, purchased_orders_url)
        except Exception as exc:
            logger.exception("%smarket_close_liquidation failed to fetch purchased orders", log_prefix)
            return self._market_close_liquidation_report(
                now=now,
                positions=[],
                submitted=[],
                blockers=[f"failed to fetch purchased orders: {exc}"],
            )
        positions = self._positions_from_purchased_orders(purchased_orders)
        submitted: list[dict[str, Any]] = []

        for position in positions:
            payload = {
                "tradingSymbol": position.trading_symbol,
                "exchange": position.exchange,
                "quantity": position.quantity,
                "orderType": self.config.order_type.strip().upper(),
                "product": position.product,
                "price": 0,
                "triggerPrice": 0,
            }
            url = f"{self.config.app_base_url}/api/v1/orders/exit"
            response = await asyncio.to_thread(self._post_json, url, payload)
            submitted.append(
                {
                    "tradingSymbol": position.trading_symbol,
                    "exchange": position.exchange,
                    "quantity": position.quantity,
                    "product": position.product,
                    "response": response,
                }
            )

        logger.info(
            "%smarket_close_liquidation completed positions=%s submitted=%s",
            log_prefix,
            len(positions),
            len(submitted),
        )
        return self._market_close_liquidation_report(
            now=now,
            positions=positions,
            submitted=submitted,
            blockers=[],
        )

    def _build_order_payload(
        self,
        *,
        trading_symbol: str,
        transaction_type: str,
        quantity: int,
        order_type: str,
        product: str,
        exchange: str,
        price: float,
        trigger_price: float,
        suggested_quantity: int | None = None,
    ) -> tuple[dict[str, Any], str]:
        side = transaction_type.strip().upper()
        placement_mode = self.config.order_placement_mode

        if not self.config.enable_order_tools:
            raise ValueError("Order tools are disabled by AGENT_ENABLE_ORDER_TOOLS=false")
        if not self.config.allow_trading:
            raise ValueError("Trading execution is disabled by AGENT_ALLOW_TRADING=false")
        if placement_mode == "NONE":
            raise ValueError("Order placement is disabled by AGENT_ORDER_PLACEMENT_MODE=NONE")
        if side not in {"BUY", "SELL"}:
            raise ValueError("transaction_type must be BUY or SELL")
        if placement_mode != "ALL" and placement_mode != side:
            raise ValueError(
                f"AGENT_ORDER_PLACEMENT_MODE={placement_mode} does not permit {side} orders"
            )
        if self._submitted_order_count >= self.config.max_orders_per_cycle:
            raise ValueError(
                f"max_orders_per_cycle={self.config.max_orders_per_cycle} has already been reached"
            )
        if exchange.strip().upper() != self.config.instrument_exchange:
            raise ValueError(
                f"exchange must be {self.config.instrument_exchange}; got {exchange}"
            )
        effective_quantity = quantity
        if (
            side == "BUY"
            and self.config.use_strategy_quantity_recommendation
            and suggested_quantity is not None
        ):
            if suggested_quantity <= 0:
                raise ValueError(
                    f"strategy suggestedQuantity must be > 0 for BUY orders; got {suggested_quantity}"
                )
            effective_quantity = suggested_quantity
            if quantity != suggested_quantity:
                logger.info(
                    "using strategy suggestedQuantity for BUY order trading_symbol=%s configured_quantity=%s suggested_quantity=%s",
                    trading_symbol,
                    quantity,
                    suggested_quantity,
                )
        elif side == "BUY" and suggested_quantity is not None:
            logger.info(
                "ignoring strategy suggestedQuantity for BUY order because AGENT_USE_STRATEGY_QUANTITY_RECOMMENDATION=false trading_symbol=%s configured_quantity=%s suggested_quantity=%s",
                trading_symbol,
                quantity,
                suggested_quantity,
            )

        if effective_quantity <= 0:
            raise ValueError("quantity must be > 0")
        if price < 0 or trigger_price < 0:
            raise ValueError("price and trigger_price must be >= 0")

        base_payload: dict[str, Any] = {
            "tradingSymbol": trading_symbol.strip().upper(),
            "exchange": exchange.strip().upper(),
            "quantity": effective_quantity,
            "orderType": order_type.strip().upper(),
            "product": product.strip().upper(),
            "price": price,
            "triggerPrice": trigger_price,
        }

        if side == "BUY":
            return {**base_payload, "transactionType": "BUY"}, "/api/v1/orders"
        return base_payload, "/api/v1/orders/exit"

    def _market_close_liquidation_blockers(self) -> list[str]:
        blockers: list[str] = []
        if not self.config.enable_order_tools:
            blockers.append("AGENT_ENABLE_ORDER_TOOLS=false")
        if not self.config.allow_trading:
            blockers.append("AGENT_ALLOW_TRADING=false")
        if self.config.order_placement_mode not in {"SELL", "ALL"}:
            blockers.append(
                f"AGENT_ORDER_PLACEMENT_MODE={self.config.order_placement_mode} does not permit SELL"
            )
        return blockers

    def _positions_from_purchased_orders(self, purchased_orders: Any) -> list[LiquidationPosition]:
        if not isinstance(purchased_orders, list):
            raise ValueError("Expected /api/v1/orders/purchased to return a JSON array")

        grouped: dict[tuple[str, str, str], int] = defaultdict(int)
        for order in purchased_orders:
            if not isinstance(order, dict):
                continue

            trading_symbol = str(order.get("tradingSymbol") or "").strip().upper()
            if not trading_symbol:
                continue

            exchange = str(order.get("exchange") or self.config.instrument_exchange).strip().upper()
            product = str(order.get("product") or self.config.order_product).strip().upper()
            quantity = self._positive_int(order.get("filledQuantity"))
            if quantity <= 0:
                quantity = self._positive_int(order.get("quantity"))
            if quantity <= 0:
                logger.warning("skipping purchased order with no positive quantity order=%s", order)
                continue

            grouped[(trading_symbol, exchange, product)] += quantity

        return [
            LiquidationPosition(
                trading_symbol=trading_symbol,
                exchange=exchange,
                quantity=quantity,
                product=product,
            )
            for (trading_symbol, exchange, product), quantity in sorted(grouped.items())
        ]

    def _market_close_liquidation_report(
        self,
        *,
        now: datetime,
        positions: list[LiquidationPosition],
        submitted: list[dict[str, Any]],
        blockers: list[str],
    ) -> str:
        lines = [
            "### Market Close Liquidation Report",
            "",
            f"- Time: {now.isoformat()} ({self.config.timezone})",
            "- Feature flag: AGENT_MARKET_CLOSE_LIQUIDATION_ENABLED=true",
            "- Window: 15:20 to before 15:30 local market time",
        ]
        if blockers:
            lines.append(f"- Status: blocked ({'; '.join(blockers)})")
            return "\n".join(lines)

        if not positions:
            lines.append("- Status: no purchased orders to sell")
            return "\n".join(lines)

        lines.extend(
            [
                "- Status: submitted SELL exit orders for all purchased positions found",
                "",
                "| Symbol | Exchange | Quantity | Product | Submitted | Response |",
                "|---|---|---:|---|---|---|",
            ]
        )
        for order in submitted:
            response = order["response"]
            lines.append(
                "| {symbol} | {exchange} | {quantity} | {product} | {submitted} | {response} |".format(
                    symbol=order["tradingSymbol"],
                    exchange=order["exchange"],
                    quantity=order["quantity"],
                    product=order["product"],
                    submitted="yes" if response.get("ok") else "failed",
                    response=json.dumps(response.get("response"), default=str),
                )
            )
        return "\n".join(lines)

    def _post_json(self, url: str, payload: dict[str, Any]) -> dict[str, Any]:
        request = Request(
            url,
            data=json.dumps(payload).encode("utf-8"),
            headers={
                "Accept": "application/json",
                "Content-Type": "application/json",
            },
            method="POST",
        )
        logger.info("submitting json order url=%s payload=%s", url, payload)
        try:
            with urlopen(request, timeout=30) as response:
                body = response.read().decode("utf-8")
                self._submitted_order_count += 1
                return {
                    "ok": 200 <= response.status < 300,
                    "status_code": response.status,
                    "response": self._decode_response_body(body),
                }
        except HTTPError as exc:
            body = exc.read().decode("utf-8", errors="replace")
            logger.error("json order submission failed status=%s body=%s", exc.code, body)
            return {
                "ok": False,
                "status_code": exc.code,
                "response": self._decode_response_body(body),
            }
        except URLError as exc:
            logger.error("json order submission failed url=%s error=%s", url, exc)
            return {
                "ok": False,
                "status_code": None,
                "response": str(exc),
            }

    def _get_json(self, url: str) -> Any:
        request = Request(
            url,
            headers={"Accept": "application/json"},
            method="GET",
        )
        logger.info("fetching json url=%s", url)
        try:
            with urlopen(request, timeout=30) as response:
                body = response.read().decode("utf-8")
                return self._decode_response_body(body)
        except HTTPError as exc:
            body = exc.read().decode("utf-8", errors="replace")
            logger.error("json fetch failed status=%s body=%s", exc.code, body)
            raise ValueError(
                f"GET {url} failed status={exc.code} response={self._decode_response_body(body)}"
            ) from exc
        except URLError as exc:
            logger.error("json fetch failed url=%s error=%s", url, exc)
            raise ValueError(f"GET {url} failed error={exc}") from exc

    @staticmethod
    def _decode_response_body(body: str) -> Any:
        if not body:
            return None
        try:
            return json.loads(body)
        except json.JSONDecodeError:
            return body

    @staticmethod
    def _preview(text: str, limit: int = 500) -> str:
        normalized = " ".join(text.split())
        if len(normalized) <= limit:
            return normalized
        return normalized[: limit - 3] + "..."

    @staticmethod
    def _positive_int(value: Any) -> int:
        try:
            parsed = int(value)
        except (TypeError, ValueError):
            return 0
        return max(parsed, 0)
