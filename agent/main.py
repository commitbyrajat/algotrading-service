from __future__ import annotations

import argparse
import asyncio
import logging
import os

from config import AgentConfig
from scheduler import AgentScheduler
from trading_agent import TradingMcpAgent


def configure_logging() -> None:
    logging.basicConfig(
        level=os.getenv("AGENT_LOG_LEVEL", "INFO").upper(),
        format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
    )


async def run_once() -> None:
    config = AgentConfig.from_env()
    output = await TradingMcpAgent(config).run_once()
    print(output)


async def run_scheduler() -> None:
    config = AgentConfig.from_env()
    await AgentScheduler(config).run_forever()


def main() -> None:
    parser = argparse.ArgumentParser(description="Run the algo trading MCP agent.")
    parser.add_argument(
        "--once",
        action="store_true",
        help="Run one agent cycle and exit instead of starting the cron scheduler.",
    )
    args = parser.parse_args()

    configure_logging()
    asyncio.run(run_once() if args.once else run_scheduler())


if __name__ == "__main__":
    main()
