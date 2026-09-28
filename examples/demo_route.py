"""Demo: endoscope prefers Matrix; surgical cam fanout prefers SDVoE."""

from __future__ import annotations

import os
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
SRC = os.path.join(ROOT, "src")
sys.path.insert(0, SRC)

from adapters.fabrics import BridgeAdapter, MatrixAdapter, SDVoEAdapter  # noqa: E402
from domain.models import (  # noqa: E402
    DistanceClass,
    FabricKind,
    PhysicalEndpoint,
    RouteIntent,
    SignalCapability,
    SinkRole,
    SourceType,
    VideoSink,
    VideoSource,
)
from policy.engine import PolicyEngine  # noqa: E402
from services.orchestrator import FabricOrchestrator  # noqa: E402
from services.path_planner import GraphEdge, GraphNode, PathPlanner, TopologyGraph  # noqa: E402
from services.route_service import Inventory, RouteService  # noqa: E402


def build_demo_topology() -> tuple[Inventory, TopologyGraph]:
    # Physical endpoints
    endo_mat_in = PhysicalEndpoint(
        id="ep.endo.matrix.in3",
        fabric=FabricKind.MATRIX,
        device_id="matrix-or1",
        port="IN3",
        direction="in",
    )
    cam_enc = PhysicalEndpoint(
        id="ep.cam.sdvoe.enc7",
        fabric=FabricKind.SDVOE,
        device_id="enc-7",
        port="HDMI",
        direction="in",
    )
    boom_mat_out = PhysicalEndpoint(
        id="ep.boom.matrix.out1",
        fabric=FabricKind.MATRIX,
        device_id="matrix-or1",
        port="OUT1",
        direction="out",
    )
    wall_dec = PhysicalEndpoint(
        id="ep.wall.sdvoe.dec2",
        fabric=FabricKind.SDVOE,
        device_id="dec-2",
        port="HDMI",
        direction="out",
    )

    inventory = Inventory(
        sources={
            "endoscope_main": VideoSource(
                id="endoscope_main",
                name="腔镜主机主输出",
                source_type=SourceType.ENDOSCOPE,
                endpoints=[endo_mat_in],
                distance_class=DistanceClass.NEAR,
                critical=True,
            ),
            "surgical_cam": VideoSource(
                id="surgical_cam",
                name="术野摄像机",
                source_type=SourceType.SURGICAL_CAM,
                endpoints=[cam_enc],
                distance_class=DistanceClass.NEAR,
            ),
        },
        sinks={
            "boom_main": VideoSink(
                id="boom_main",
                name="主吊臂显示",
                role=SinkRole.PRIMARY_SURGEON_DISPLAY,
                endpoints=[boom_mat_out],
                ultra_low_latency=True,
            ),
            "wall_left": VideoSink(
                id="wall_left",
                name="左侧墙显",
                role=SinkRole.WALL_DISPLAY,
                endpoints=[wall_dec],
            ),
        },
    )

    g = TopologyGraph()
    for node_id, kind, fabric in [
        ("ep.endo.matrix.in3", "matrix_in", FabricKind.MATRIX),
        ("matrix.core", "matrix_core", FabricKind.MATRIX),
        ("ep.boom.matrix.out1", "matrix_out", FabricKind.MATRIX),
        ("ep.cam.sdvoe.enc7", "sdvoe_enc", FabricKind.SDVOE),
        ("sdvoe.fabric", "sdvoe_core", FabricKind.SDVOE),
        ("ep.wall.sdvoe.dec2", "sdvoe_dec", FabricKind.SDVOE),
    ]:
        g.add_node(GraphNode(id=node_id, kind=kind, fabric=fabric))

    # Matrix path: IN3 -> core -> OUT1
    g.add_edge(
        GraphEdge(
            src="ep.endo.matrix.in3",
            dst="matrix.core",
            fabric=FabricKind.MATRIX,
            latency_ms=5,
            reliability=0.99,
            meta={"action": "ingress"},
        )
    )
    g.add_edge(
        GraphEdge(
            src="matrix.core",
            dst="ep.boom.matrix.out1",
            fabric=FabricKind.MATRIX,
            latency_ms=5,
            reliability=0.99,
            meta={
                "action": "switch",
                "input": "IN3",
                "output": "OUT1",
                "compensation": {"input": "IN0", "output": "OUT1"},
            },
        )
    )

    # SDVoE path: enc7 -> fabric -> dec2
    g.add_edge(
        GraphEdge(
            src="ep.cam.sdvoe.enc7",
            dst="sdvoe.fabric",
            fabric=FabricKind.SDVOE,
            latency_ms=15,
            reliability=0.97,
            meta={
                "action": "set_stream",
                "params": {
                    "encoder_id": "enc-7",
                    "stream_id": "stream-cam",
                    "mode": "multicast",
                },
            },
        )
    )
    g.add_edge(
        GraphEdge(
            src="sdvoe.fabric",
            dst="ep.wall.sdvoe.dec2",
            fabric=FabricKind.SDVOE,
            latency_ms=15,
            reliability=0.97,
            meta={
                "action": "subscribe",
                "params": {"decoder_id": "dec-2", "stream_id": "stream-cam"},
            },
        )
    )
    return inventory, g


def main() -> None:
    cfg = os.path.join(ROOT, "config", "routing-policies.yaml")
    inventory, graph = build_demo_topology()
    policy = PolicyEngine(cfg)
    planner = PathPlanner()
    orch = FabricOrchestrator(
        {
            FabricKind.MATRIX: MatrixAdapter(transport=None),
            FabricKind.SDVOE: SDVoEAdapter(manager_client=None),
            FabricKind.BRIDGE: BridgeAdapter(),
        }
    )
    svc = RouteService(inventory, graph, policy, planner, orch)

    print("=== Case 1: 腔镜 -> 主吊臂（应走 MATRIX） ===")
    s1 = svc.route(
        RouteIntent(
            source_id="endoscope_main",
            sink_id="boom_main",
            operator="nurse.li",
        )
    )
    print(
        f"route={s1.route_id} fabrics={[f.value for f in s1.plan.fabrics_used]} "
        f"latency={s1.plan.estimated_latency_ms}ms reason={s1.plan.decision.reason}"
    )

    print("=== Case 2: 术野相机 -> 墙显，fanout=3（应走 SDVoE） ===")
    s2 = svc.route(
        RouteIntent(
            source_id="surgical_cam",
            sink_id="wall_left",
            operator="nurse.li",
        ),
        fanout_count=3,
    )
    print(
        f"route={s2.route_id} fabrics={[f.value for f in s2.plan.fabrics_used]} "
        f"latency={s2.plan.estimated_latency_ms}ms reason={s2.plan.decision.reason}"
    )


if __name__ == "__main__":
    main()
