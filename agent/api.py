from __future__ import annotations

from datetime import datetime, timezone
from typing import TYPE_CHECKING, Any

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

from errors import AgentAlreadyRunningError

if TYPE_CHECKING:
    from scheduler import AgentScheduler


class TriggerRequest(BaseModel):
    prompt: str | None = None


class TriggerResponse(BaseModel):
    status: str
    started_at: str
    finished_at: str
    output: str


def create_app(agent_scheduler: AgentScheduler) -> FastAPI:
    app = FastAPI(title="Algo Trading Agent", version="0.1.0")

    @app.get("/health")
    async def health() -> dict[str, str]:
        return {"status": "ok"}

    @app.get("/status")
    async def status() -> dict[str, Any]:
        job = agent_scheduler.scheduler.get_job("algotrading-agent-cycle")
        return {
            "status": "running",
            "model": agent_scheduler.config.model,
            "mcp_url": agent_scheduler.config.mcp_url,
            "app_base_url": agent_scheduler.config.app_base_url,
            "cron_minutes": agent_scheduler.config.cron_minutes,
            "trading_enabled": agent_scheduler.config.allow_trading,
            "order_placement_mode": agent_scheduler.config.order_placement_mode,
            "next_run_at": job.next_run_time.isoformat() if job and job.next_run_time else None,
        }

    @app.post("/agent/run", response_model=TriggerResponse)
    async def run_agent(request: TriggerRequest) -> TriggerResponse:
        started_at = datetime.now(timezone.utc)
        try:
            output = await agent_scheduler.run_agent(prompt=request.prompt, trigger="manual")
        except AgentAlreadyRunningError as exc:
            raise HTTPException(status_code=409, detail=str(exc)) from exc

        finished_at = datetime.now(timezone.utc)
        return TriggerResponse(
            status="completed",
            started_at=started_at.isoformat(),
            finished_at=finished_at.isoformat(),
            output=output,
        )

    return app
