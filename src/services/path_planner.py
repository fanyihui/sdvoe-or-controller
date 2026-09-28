"""Topology graph and path planner across Matrix / SDVoE / Bridge hops."""

from __future__ import annotations

import heapq
from dataclasses import dataclass, field
from typing import Optional

from domain.models import (
    FabricKind,
    FabricStep,
    PolicyDecision,
    RouteIntent,
    RoutePlan,
    VideoSink,
    VideoSource,
)


@dataclass
class GraphNode:
    id: str
    kind: str  # source_ep | matrix_in | matrix_out | sdvoe_enc | sdvoe_dec | sink_ep | bridge
    fabric: FabricKind
    ref: str = ""


@dataclass
class GraphEdge:
    src: str
    dst: str
    fabric: FabricKind
    latency_ms: int
    reliability: float = 1.0  # 0..1
    is_bridge: bool = False
    exclusive: bool = False
    meta: dict = field(default_factory=dict)


@dataclass
class TopologyGraph:
    nodes: dict[str, GraphNode] = field(default_factory=dict)
    edges: list[GraphEdge] = field(default_factory=list)
    adjacency: dict[str, list[GraphEdge]] = field(default_factory=dict)

    def add_node(self, node: GraphNode) -> None:
        self.nodes[node.id] = node
        self.adjacency.setdefault(node.id, [])

    def add_edge(self, edge: GraphEdge) -> None:
        self.edges.append(edge)
        self.adjacency.setdefault(edge.src, []).append(edge)
        self.adjacency.setdefault(edge.dst, [])

    def endpoints_for_source(self, source: VideoSource) -> list[str]:
        return [ep.id for ep in source.endpoints if ep.online]

    def endpoints_for_sink(self, sink: VideoSink) -> list[str]:
        return [ep.id for ep in sink.endpoints if ep.online]


class PathPlanner:
    """Dijkstra-style planner with policy constraints and fabric preference."""

    def __init__(self, cost_weights: Optional[dict[str, float]] = None):
        self.w = cost_weights or {
            "latency": 0.45,
            "bridge_hop": 0.25,
            "reliability": 0.15,
            "hop_count": 0.10,
            "fabric_preference": 0.05,
        }

    def plan(
        self,
        graph: TopologyGraph,
        source: VideoSource,
        sink: VideoSink,
        intent: RouteIntent,
        decision: PolicyDecision,
    ) -> Optional[RoutePlan]:
        starts = graph.endpoints_for_source(source)
        goals = set(graph.endpoints_for_sink(sink))
        if not starts or not goals:
            return None

        best: Optional[tuple[float, list[str], list[GraphEdge]]] = None
        fabric_order = decision.preferred_fabrics + [
            f for f in decision.fallback_fabrics if f not in decision.preferred_fabrics
        ]

        for start in starts:
            result = self._dijkstra(graph, start, goals, decision, fabric_order)
            if result is None:
                continue
            cost, path, edges = result
            if best is None or cost < best[0]:
                best = (cost, path, edges)

        if best is None:
            return None

        _, path, edges = best
        steps = self._edges_to_steps(edges)
        fabrics = []
        for e in edges:
            if e.fabric not in fabrics and e.fabric != FabricKind.BRIDGE:
                fabrics.append(e.fabric)
        latency = sum(e.latency_ms for e in edges)
        return RoutePlan(
            intent=intent,
            steps=steps,
            estimated_latency_ms=latency,
            fabrics_used=fabrics,
            decision=decision,
            path_node_ids=path,
        )

    def _dijkstra(
        self,
        graph: TopologyGraph,
        start: str,
        goals: set[str],
        decision: PolicyDecision,
        fabric_order: list[FabricKind],
    ) -> Optional[tuple[float, list[str], list[GraphEdge]]]:
        # state: (cost, latency, bridges, hops, node, path_nodes, path_edges)
        pq: list[tuple[float, int, int, int, str, tuple[str, ...], tuple[GraphEdge, ...]]] = []
        heapq.heappush(pq, (0.0, 0, 0, 0, start, (start,), tuple()))
        visited: dict[str, float] = {}

        while pq:
            cost, latency, bridges, hops, node, path_nodes, path_edges = heapq.heappop(pq)
            if node in visited and visited[node] <= cost:
                continue
            visited[node] = cost

            if node in goals:
                if latency <= decision.constraints.max_latency_ms and bridges <= decision.constraints.max_bridge_hops:
                    return cost, list(path_nodes), list(path_edges)
                continue

            for edge in graph.adjacency.get(node, []):
                new_bridges = bridges + (1 if edge.is_bridge else 0)
                if new_bridges > decision.constraints.max_bridge_hops:
                    continue
                new_latency = latency + edge.latency_ms
                if new_latency > decision.constraints.max_latency_ms:
                    continue
                if decision.constraints.require_lossless and edge.meta.get("lossy"):
                    continue

                pref_penalty = self._fabric_preference_penalty(edge.fabric, fabric_order)
                step_cost = (
                    self.w["latency"] * edge.latency_ms
                    + self.w["bridge_hop"] * (20.0 if edge.is_bridge else 0.0)
                    + self.w["reliability"] * (1.0 - edge.reliability) * 50.0
                    + self.w["hop_count"] * 5.0
                    + self.w["fabric_preference"] * pref_penalty
                )
                new_cost = cost + step_cost
                heapq.heappush(
                    pq,
                    (
                        new_cost,
                        new_latency,
                        new_bridges,
                        hops + 1,
                        edge.dst,
                        path_nodes + (edge.dst,),
                        path_edges + (edge,),
                    ),
                )
        return None

    @staticmethod
    def _fabric_preference_penalty(
        fabric: FabricKind, fabric_order: list[FabricKind]
    ) -> float:
        if fabric == FabricKind.BRIDGE:
            return 15.0
        try:
            return float(fabric_order.index(fabric) * 10)
        except ValueError:
            return 30.0

    @staticmethod
    def _edges_to_steps(edges: list[GraphEdge]) -> list[FabricStep]:
        steps: list[FabricStep] = []
        for edge in edges:
            if edge.fabric == FabricKind.MATRIX and edge.meta.get("action") == "switch":
                steps.append(
                    FabricStep(
                        fabric=FabricKind.MATRIX,
                        action="switch_crosspoint",
                        params={
                            "input": edge.meta["input"],
                            "output": edge.meta["output"],
                        },
                        compensation_action="switch_crosspoint",
                        compensation_params=edge.meta.get("compensation"),
                    )
                )
            elif edge.fabric == FabricKind.SDVOE:
                action = edge.meta.get("action", "subscribe")
                steps.append(
                    FabricStep(
                        fabric=FabricKind.SDVOE,
                        action=action,
                        params=dict(edge.meta.get("params", {})),
                    )
                )
            elif edge.is_bridge or edge.fabric == FabricKind.BRIDGE:
                steps.append(
                    FabricStep(
                        fabric=FabricKind.BRIDGE,
                        action="enable_bridge",
                        params=dict(edge.meta),
                    )
                )
        return steps
