from __future__ import annotations

import asyncio
import json
import logging
from typing import Any, Literal
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from pydantic_ai import Agent
from pydantic_ai.mcp import MCPServerStreamableHTTP

from config import AgentConfig
from prompts import run_prompt, system_prompt

logger = logging.getLogger(__name__)


class TradingMcpAgent:
    def __init__(self, config: AgentConfig):
        self.config = config
        self._submitted_order_count = 0
        self._server = MCPServerStreamableHTTP(config.mcp_url)
        self._agent = Agent(
            config.model,
            system_prompt=system_prompt(config),
            toolsets=[self._server],
        )
        self._register_tools()

    async def run_once(self, prompt: str | None = None, cycle_id: str | None = None) -> str:
        self._submitted_order_count = 0
        prompt_text = run_prompt(self.config, prompt)
        from_date, to_date = self.config.candle_date_range()
        log_prefix = "cycle_id=%s " % cycle_id if cycle_id else ""
        logger.info(
            "%sstarting agent cycle model=%s mcp_url=%s from=%s to=%s interval=%s order_tools=%s trading=%s order_placement_mode=%s max_orders=%s prompt_chars=%s",
            log_prefix,
            self.config.model,
            self.config.mcp_url,
            from_date.isoformat(),
            to_date.isoformat(),
            self.config.resolved_candle_interval(),
            self.config.enable_order_tools,
            self.config.allow_trading,
            self.config.order_placement_mode,
            self.config.max_orders_per_cycle,
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
        ) -> dict[str, Any]:
            """
            Submit one JSON order to the application.

            Args:
                trading_symbol: Broker trading symbol returned by instrument lookup.
                transaction_type: BUY places a buy order; SELL exits a position.
                quantity: Number of shares or lots.
                order_type: Kite order type such as MARKET, LIMIT, SL, or SL-M.
                product: Kite product such as CNC, MIS, or NRML.
                exchange: Exchange segment, normally NSE.
                price: Limit price, or 0 for MARKET.
                trigger_price: Trigger price for stop-loss order types, otherwise 0.
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
            )
            url = f"{self.config.app_base_url}{path}"
            return await asyncio.to_thread(self._post_json, url, payload)

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
        if quantity <= 0:
            raise ValueError("quantity must be > 0")
        if price < 0 or trigger_price < 0:
            raise ValueError("price and trigger_price must be >= 0")

        base_payload: dict[str, Any] = {
            "tradingSymbol": trading_symbol.strip().upper(),
            "exchange": exchange.strip().upper(),
            "quantity": quantity,
            "orderType": order_type.strip().upper(),
            "product": product.strip().upper(),
            "price": price,
            "triggerPrice": trigger_price,
        }

        if side == "BUY":
            return {**base_payload, "transactionType": "BUY"}, "/api/v1/orders"
        return base_payload, "/api/v1/orders/exit"

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
