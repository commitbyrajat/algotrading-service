from __future__ import annotations

import asyncio
import logging
import signal
from time import monotonic
from uuid import uuid4

from apscheduler.schedulers.asyncio import AsyncIOScheduler
from apscheduler.triggers.cron import CronTrigger
import uvicorn

from api import create_app
from config import AgentConfig
from errors import AgentAlreadyRunningError
from trading_agent import TradingMcpAgent

logger = logging.getLogger(__name__)


class AgentScheduler:
    def __init__(self, config: AgentConfig):
        self.config = config
        self.agent = TradingMcpAgent(config)
        self.scheduler = AsyncIOScheduler(timezone=config.timezone)
        self._stop_event = asyncio.Event()
        self._run_lock = asyncio.Lock()

    async def run_forever(self) -> None:
        self.scheduler.add_job(
            self._run_job,
            CronTrigger(minute=self.config.cron_minutes, timezone=self.config.timezone),
            id="algotrading-agent-cycle",
            max_instances=1,
            coalesce=True,
            replace_existing=True,
        )

        loop = asyncio.get_running_loop()
        for sig in (signal.SIGINT, signal.SIGTERM):
            loop.add_signal_handler(sig, self._stop_event.set)

        http_server = self._create_http_server()
        http_task = asyncio.create_task(http_server.serve())

        logger.info(
            "starting scheduler cron_minutes=%s timezone=%s http=%s:%s",
            self.config.cron_minutes,
            self.config.timezone,
            self.config.http_host,
            self.config.http_port,
        )
        await self._run_job()
        self.scheduler.start()

        await self._stop_event.wait()
        logger.info("stopping scheduler")
        self.scheduler.shutdown(wait=False)
        http_server.should_exit = True
        await http_task

    async def _run_job(self) -> None:
        cycle_id = self._new_cycle_id()
        try:
            output = await self.run_agent(cycle_id=cycle_id, trigger="scheduled")
        except AgentAlreadyRunningError:
            logger.warning("cycle_id=%s trigger=scheduled skipped reason=agent_cycle_already_running", cycle_id)
            return
        except Exception:
            logger.exception("cycle_id=%s trigger=scheduled failed", cycle_id)
            return

        logger.info("cycle_id=%s trigger=scheduled output:\n%s", cycle_id, output)

    async def run_agent(self, prompt: str | None = None, trigger: str = "manual", cycle_id: str | None = None) -> str:
        cycle_id = cycle_id or self._new_cycle_id()
        if self._run_lock.locked():
            raise AgentAlreadyRunningError("agent cycle already running")

        async with self._run_lock:
            started = monotonic()
            logger.info("cycle_id=%s trigger=%s state=started", cycle_id, trigger)
            try:
                output = await self.agent.run_once(prompt=prompt, cycle_id=cycle_id)
            except Exception:
                logger.exception("cycle_id=%s trigger=%s state=failed", cycle_id, trigger)
                raise

            elapsed = monotonic() - started
            logger.info("cycle_id=%s trigger=%s state=completed elapsed_seconds=%.2f", cycle_id, trigger, elapsed)
            return output

    def _create_http_server(self) -> uvicorn.Server:
        app = create_app(self)
        config = uvicorn.Config(
            app,
            host=self.config.http_host,
            port=self.config.http_port,
            log_level="info",
        )
        return uvicorn.Server(config)

    @staticmethod
    def _new_cycle_id() -> str:
        return uuid4().hex[:12]
