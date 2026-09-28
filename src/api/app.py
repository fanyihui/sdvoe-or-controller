"""Minimal FastAPI surface for OR route control."""

from __future__ import annotations

from typing import Any, Optional

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

# Wire these in composition root / main.py
route_service = None  # set by create_app()


class RouteRequest(BaseModel):
    source_id: str
    sink_id: str
    operator: str = "system"
    scene_id: Optional[str] = None
    confirmed: bool = False
    fanout_count: int = 1
    cross_room: bool = False
    request_lock: str = "none"


class RouteResponse(BaseModel):
    route_id: str
    source_id: str
    sink_id: str
    fabrics: list[str]
    estimated_latency_ms: int
    policy_reason: str
    lock_mode: str


def create_app(svc: Any) -> FastAPI:
    global route_service
    route_service = svc
    app = FastAPI(title="OR SDVoE/Matrix Controller", version="0.1.0")

    @app.post("/routes", response_model=RouteResponse)
    def create_route(req: RouteRequest) -> RouteResponse:
        from domain.models import LockMode, RouteIntent

        assert route_service is not None
        intent = RouteIntent(
            source_id=req.source_id,
            sink_id=req.sink_id,
            operator=req.operator,
            scene_id=req.scene_id,
            request_lock=LockMode(req.request_lock),
        )
        try:
            state = route_service.route(
                intent,
                confirmed=req.confirmed,
                fanout_count=req.fanout_count,
                cross_room=req.cross_room,
            )
        except PermissionError as exc:
            raise HTTPException(status_code=409, detail=str(exc)) from exc
        except KeyError as exc:
            raise HTTPException(status_code=404, detail=str(exc)) from exc
        except RuntimeError as exc:
            raise HTTPException(status_code=422, detail=str(exc)) from exc

        return RouteResponse(
            route_id=state.route_id,
            source_id=state.source_id,
            sink_id=state.sink_id,
            fabrics=[f.value for f in state.plan.fabrics_used],
            estimated_latency_ms=state.plan.estimated_latency_ms,
            policy_reason=state.plan.decision.reason,
            lock_mode=state.lock_mode.value,
        )

    @app.get("/health")
    def health() -> dict[str, str]:
        return {"status": "ok"}

    return app
