"""Fabric orchestrator: transactional multi-fabric route apply with compensation."""

from __future__ import annotations

import itertools
import logging
from typing import Optional

from adapters.fabrics import ApplyResult, FabricAdapter
from domain.models import FabricKind, FabricStep, LockMode, RoutePlan, RouteState

logger = logging.getLogger(__name__)


class FabricOrchestrator:
    def __init__(self, adapters: dict[FabricKind, FabricAdapter]):
        self._adapters = adapters
        self._routes: dict[str, RouteState] = {}
        self._sink_index: dict[str, str] = {}  # sink_id -> route_id
        self._id_seq = itertools.count(1)

    def execute(
        self,
        plan: RoutePlan,
        *,
        conflict_policy: str = "steal_with_confirm",
        confirmed: bool = False,
    ) -> RouteState:
        sink_id = plan.intent.sink_id
        existing = self._sink_index.get(sink_id)
        if existing and conflict_policy == "reject":
            raise RuntimeError(f"sink {sink_id} busy by route {existing}")
        if existing and conflict_policy == "steal_with_confirm" and not confirmed:
            raise PermissionError(
                f"sink {sink_id} occupied by {existing}; confirm to steal"
            )

        route_id = f"rt-{next(self._id_seq)}"
        applied: list[tuple[FabricAdapter, FabricStep]] = []

        try:
            for step in plan.steps:
                adapter = self._adapters.get(step.fabric)
                if adapter is None and step.fabric == FabricKind.BRIDGE:
                    # Bridge optional if no dedicated adapter
                    continue
                if adapter is None:
                    raise RuntimeError(f"no adapter for fabric {step.fabric}")
                result = adapter.apply_steps([step])
                if not result.ok:
                    raise RuntimeError(result.message)
                applied.append((adapter, step))

            if existing:
                self.release(existing)

            state = RouteState(
                route_id=route_id,
                source_id=plan.intent.source_id,
                sink_id=sink_id,
                plan=plan,
                active=True,
                lock_mode=plan.decision.constraints.lock_mode
                if plan.intent.request_lock == LockMode.NONE
                else plan.intent.request_lock,
            )
            self._routes[route_id] = state
            self._sink_index[sink_id] = route_id
            logger.info(
                "route applied %s %s -> %s via %s (%s)",
                route_id,
                state.source_id,
                state.sink_id,
                [f.value for f in plan.fabrics_used],
                plan.decision.reason,
            )
            return state
        except Exception:
            self._compensate(list(reversed(applied)))
            raise

    def release(self, route_id: str) -> None:
        state = self._routes.pop(route_id, None)
        if not state:
            return
        self._sink_index.pop(state.sink_id, None)
        for fabric in state.plan.fabrics_used:
            adapter = self._adapters.get(fabric)
            if adapter:
                adapter.release(route_id)

    def get_route_for_sink(self, sink_id: str) -> Optional[RouteState]:
        rid = self._sink_index.get(sink_id)
        return self._routes.get(rid) if rid else None

    @staticmethod
    def _compensate(applied: list[tuple[FabricAdapter, FabricStep]]) -> None:
        for adapter, step in applied:
            if not step.compensation_action:
                continue
            comp = FabricStep(
                fabric=step.fabric,
                action=step.compensation_action,
                params=step.compensation_params or {},
            )
            try:
                adapter.apply_steps([comp])
            except Exception as exc:  # noqa: BLE001
                logger.error("compensation failed: %s", exc)
