from __future__ import annotations

import logging

from pydantic_ai import Agent
from pydantic_ai.mcp import MCPServerStreamableHTTP

from config import AgentConfig
from prompts import run_prompt, system_prompt

logger = logging.getLogger(__name__)


class TradingMcpAgent:
    def __init__(self, config: AgentConfig):
        self.config = config
        self._server = MCPServerStreamableHTTP(config.mcp_url)
        self._agent = Agent(
            config.model,
            system_prompt=system_prompt(config),
            toolsets=[self._server],
        )

    async def run_once(self, prompt: str | None = None, cycle_id: str | None = None) -> str:
        prompt_text = run_prompt(self.config, prompt)
        from_date, to_date = self.config.candle_date_range()
        log_prefix = "cycle_id=%s " % cycle_id if cycle_id else ""
        logger.info(
            "%sstarting agent cycle model=%s mcp_url=%s from=%s to=%s interval=%s prompt_chars=%s",
            log_prefix,
            self.config.model,
            self.config.mcp_url,
            from_date.isoformat(),
            to_date.isoformat(),
            self.config.resolved_candle_interval(),
            len(prompt_text),
        )
        logger.info("%sprompt_preview=%s", log_prefix, self._preview(prompt_text))
        async with self._agent:
            result = await self._agent.run(prompt_text)

        output = str(result.output)
        logger.info("%sagent cycle completed output_chars=%s", log_prefix, len(output))
        return output

    @staticmethod
    def _preview(text: str, limit: int = 500) -> str:
        normalized = " ".join(text.split())
        if len(normalized) <= limit:
            return normalized
        return normalized[: limit - 3] + "..."
