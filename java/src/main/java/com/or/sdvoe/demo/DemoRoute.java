package com.or.sdvoe.demo;

import com.or.sdvoe.adapter.BridgeAdapter;
import com.or.sdvoe.adapter.MatrixAdapter;
import com.or.sdvoe.adapter.SDVoEAdapter;
import com.or.sdvoe.domain.DistanceClass;
import com.or.sdvoe.domain.FabricKind;
import com.or.sdvoe.domain.PhysicalEndpoint;
import com.or.sdvoe.domain.RouteIntent;
import com.or.sdvoe.domain.RouteState;
import com.or.sdvoe.domain.SinkRole;
import com.or.sdvoe.domain.SourceType;
import com.or.sdvoe.domain.VideoSink;
import com.or.sdvoe.domain.VideoSource;
import com.or.sdvoe.planner.GraphEdge;
import com.or.sdvoe.planner.GraphNode;
import com.or.sdvoe.planner.PathPlanner;
import com.or.sdvoe.planner.TopologyGraph;
import com.or.sdvoe.policy.PolicyEngine;
import com.or.sdvoe.service.FabricOrchestrator;
import com.or.sdvoe.service.Inventory;
import com.or.sdvoe.service.RouteService;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/** Demo: endoscope prefers Matrix; surgical cam fanout prefers SDVoE. */
public final class DemoRoute {

    private DemoRoute() {
    }

    public static void main(String[] args) {
        InputStream cfg = DemoRoute.class.getClassLoader().getResourceAsStream("routing-policies.yaml");
        if (cfg == null) {
            throw new IllegalStateException("routing-policies.yaml not found on classpath");
        }

        DemoTopology demo = buildDemoTopology();
        PolicyEngine policy = new PolicyEngine(cfg);
        PathPlanner planner = new PathPlanner();
        FabricOrchestrator orch = new FabricOrchestrator(Map.of(
                FabricKind.MATRIX, new MatrixAdapter(null),
                FabricKind.SDVOE, new SDVoEAdapter(null),
                FabricKind.BRIDGE, new BridgeAdapter()));
        RouteService svc = new RouteService(
                demo.inventory(), demo.graph(), policy, planner, orch, "steal_with_confirm");

        System.out.println("=== Case 1: 腔镜 -> 主吊臂（应走 MATRIX） ===");
        RouteState s1 = svc.route(new RouteIntent("endoscope_main", "boom_main", "nurse.li"));
        System.out.printf(
                "route=%s fabrics=%s latency=%dms reason=%s%n",
                s1.getRouteId(),
                s1.getPlan().getFabricsUsed(),
                s1.getPlan().getEstimatedLatencyMs(),
                s1.getPlan().getDecision().getReason());

        System.out.println("=== Case 2: 术野相机 -> 墙显，fanout=3（应走 SDVoE） ===");
        RouteState s2 = svc.route(
                new RouteIntent("surgical_cam", "wall_left", "nurse.li"), true, 3, false);
        System.out.printf(
                "route=%s fabrics=%s latency=%dms reason=%s%n",
                s2.getRouteId(),
                s2.getPlan().getFabricsUsed(),
                s2.getPlan().getEstimatedLatencyMs(),
                s2.getPlan().getDecision().getReason());
    }

    private static DemoTopology buildDemoTopology() {
        PhysicalEndpoint endoMatIn = new PhysicalEndpoint(
                "ep.endo.matrix.in3", FabricKind.MATRIX, "matrix-or1", "IN3", "in");
        PhysicalEndpoint camEnc = new PhysicalEndpoint(
                "ep.cam.sdvoe.enc7", FabricKind.SDVOE, "enc-7", "HDMI", "in");
        PhysicalEndpoint boomMatOut = new PhysicalEndpoint(
                "ep.boom.matrix.out1", FabricKind.MATRIX, "matrix-or1", "OUT1", "out");
        PhysicalEndpoint wallDec = new PhysicalEndpoint(
                "ep.wall.sdvoe.dec2", FabricKind.SDVOE, "dec-2", "HDMI", "out");

        Inventory inventory = new Inventory()
                .putSource(new VideoSource(
                                "endoscope_main",
                                "腔镜主机主输出",
                                SourceType.ENDOSCOPE,
                                List.of(endoMatIn))
                        .distanceClass(DistanceClass.NEAR)
                        .critical(true))
                .putSource(new VideoSource(
                                "surgical_cam",
                                "术野摄像机",
                                SourceType.SURGICAL_CAM,
                                List.of(camEnc))
                        .distanceClass(DistanceClass.NEAR))
                .putSink(new VideoSink(
                                "boom_main",
                                "主吊臂显示",
                                SinkRole.PRIMARY_SURGEON_DISPLAY,
                                List.of(boomMatOut))
                        .ultraLowLatency(true))
                .putSink(new VideoSink(
                        "wall_left",
                        "左侧墙显",
                        SinkRole.WALL_DISPLAY,
                        List.of(wallDec)));

        TopologyGraph g = new TopologyGraph();
        g.addNode(new GraphNode("ep.endo.matrix.in3", "matrix_in", FabricKind.MATRIX));
        g.addNode(new GraphNode("matrix.core", "matrix_core", FabricKind.MATRIX));
        g.addNode(new GraphNode("ep.boom.matrix.out1", "matrix_out", FabricKind.MATRIX));
        g.addNode(new GraphNode("ep.cam.sdvoe.enc7", "sdvoe_enc", FabricKind.SDVOE));
        g.addNode(new GraphNode("sdvoe.fabric", "sdvoe_core", FabricKind.SDVOE));
        g.addNode(new GraphNode("ep.wall.sdvoe.dec2", "sdvoe_dec", FabricKind.SDVOE));

        g.addEdge(GraphEdge.of(
                "ep.endo.matrix.in3",
                "matrix.core",
                FabricKind.MATRIX,
                5,
                0.99,
                Map.of("action", "ingress")));
        g.addEdge(GraphEdge.of(
                "matrix.core",
                "ep.boom.matrix.out1",
                FabricKind.MATRIX,
                5,
                0.99,
                Map.of(
                        "action", "switch",
                        "input", "IN3",
                        "output", "OUT1",
                        "compensation", Map.of("input", "IN0", "output", "OUT1"))));

        g.addEdge(GraphEdge.of(
                "ep.cam.sdvoe.enc7",
                "sdvoe.fabric",
                FabricKind.SDVOE,
                15,
                0.97,
                Map.of(
                        "action", "set_stream",
                        "params", Map.of(
                                "encoder_id", "enc-7",
                                "stream_id", "stream-cam",
                                "mode", "multicast"))));
        g.addEdge(GraphEdge.of(
                "sdvoe.fabric",
                "ep.wall.sdvoe.dec2",
                FabricKind.SDVOE,
                15,
                0.97,
                Map.of(
                        "action", "subscribe",
                        "params", Map.of(
                                "decoder_id", "dec-2",
                                "stream_id", "stream-cam"))));

        return new DemoTopology(inventory, g);
    }

    private record DemoTopology(Inventory inventory, TopologyGraph graph) {
    }
}
