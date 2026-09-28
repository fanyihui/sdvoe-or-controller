"""Application service: intent -> policy -> plan -> orchestrate."""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Optional

from domain.models import (
    FabricKind,
    RouteIntent,
    RouteState,
    VideoSink,
    VideoSource,
)
from policy.engine import PolicyEngine
from services.orchestrator import FabricOrchestrator
from services.path_planner import PathPlanner, TopologyGraph


@dataclass
class Inventory:
    sources: dict[str, VideoSource] = field(default_factory=dict)
    sinks: dict[str, VideoSink] = field(default_factory=dict)

    def get_source(self, source_id: str) -> VideoSource:
        return self.sources[source_id]

    def get_sink(self, sink_id: str) -> VideoSink:
        return self.sinks[sink_id]


class RouteService:
    def __init__(
        self,
        inventory: Inventory,
        graph: TopologyGraph,
        policy_engine: PolicyEngine,
        planner: PathPlanner,
        orchestrator: FabricOrchestrator,
        conflict_policy: str = "steal_with_confirm",
    ):
        self.inventory = inventory
        self.graph = graph
        self.policy = policy_engine
        self.planner = planner
        self.orchestrator = orchestrator
        self.conflict_policy = conflict_policy

    def route(
        self,
        intent: RouteIntent,
        *,
        confirmed: bool = False,
        fanout_count: int = 1,
        cross_room: bool = False,
    ) -> RouteState:
        source = self.inventory.get_source(intent.source_id)
        sink = self.inventory.get_sink(intent.sink_id)

        active_endo_fabric = self._active_fabric_for_type_prefix("endoscope")
        decision = self.policy.decide(
            source,
            sink,
            fanout_count=fanout_count,
            cross_room=cross_room,
            active_endoscope_fabric=active_endo_fabric,
        )
        plan = self.planner.plan(self.graph, source, sink, intent, decision)
        if plan is None and decision.fallback_fabrics:
            # Retry with fallback-only preference
            decision.preferred_fabrics = list(decision.fallback_fabrics)
            plan = self.planner.plan(self.graph, source, sink, intent, decision)
        if plan is None:
            raise RuntimeError(
                f"no feasible path for {intent.source_id} -> {intent.sink_id}; "
                f"policy={decision.reason}"
            )
        return self.orchestrator.execute(
            plan, conflict_policy=self.conflict_policy, confirmed=confirmed
        )

    def _active_fabric_for_type_prefix(self, source_id_prefix: str) -> Optional[FabricKind]:
        for state in self.orchestrator._routes.values():  # noqa: SLF001
            if state.active and state.source_id.startswith(source_id_prefix):
                if state.plan.fabrics_used:
                    return state.plan.fabrics_used[0]
        return None
