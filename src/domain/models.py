"""Domain models for OR video routing (SDVoE + Matrix)."""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum
from typing import Any, Optional


class FabricKind(str, Enum):
    MATRIX = "MATRIX"
    SDVOE = "SDVOE"
    BRIDGE = "BRIDGE"  # Matrix <-> SDVoE converter hop


class SourceType(str, Enum):
    ENDOSCOPE = "ENDOSCOPE"
    SURGICAL_CAM = "SURGICAL_CAM"
    ULTRASOUND = "ULTRASOUND"
    CARM_FLUORO = "CARM_FLUORO"
    PATIENT_MONITOR = "PATIENT_MONITOR"
    PACS_WORKSTATION = "PACS_WORKSTATION"
    NAVIGATION = "NAVIGATION"
    ROOM_PC = "ROOM_PC"
    RECORDER_PLAYBACK = "RECORDER_PLAYBACK"
    EXTERNAL_IN = "EXTERNAL_IN"


class SinkRole(str, Enum):
    PRIMARY_SURGEON_DISPLAY = "PRIMARY_SURGEON_DISPLAY"
    SECONDARY_DISPLAY = "SECONDARY_DISPLAY"
    WALL_DISPLAY = "WALL_DISPLAY"
    NURSE_STATION = "NURSE_STATION"
    RECORDER = "RECORDER"
    TEACHING_DISPLAY = "TEACHING_DISPLAY"
    STREAMING_ENCODER = "STREAMING_ENCODER"


class LockMode(str, Enum):
    NONE = "none"
    SOFT = "soft"
    HARD = "hard"


class DistanceClass(str, Enum):
    NEAR = "NEAR"
    FAR = "FAR"


@dataclass
class SignalCapability:
    max_width: int = 3840
    max_height: int = 2160
    max_fps: int = 60
    color_spaces: list[str] = field(default_factory=lambda: ["YUV422", "RGB"])
    interfaces: list[str] = field(default_factory=lambda: ["HDMI", "SDI"])
    lossless: bool = True
    hdcp: bool = False


@dataclass
class PhysicalEndpoint:
    id: str
    fabric: FabricKind
    device_id: str
    port: str
    direction: str  # "in" | "out" | "bidirectional"
    online: bool = True
    capabilities: SignalCapability = field(default_factory=SignalCapability)
    labels: dict[str, str] = field(default_factory=dict)


@dataclass
class VideoSource:
    id: str
    name: str
    source_type: SourceType
    endpoints: list[PhysicalEndpoint]
    distance_class: DistanceClass = DistanceClass.NEAR
    critical: bool = False
    metadata: dict[str, Any] = field(default_factory=dict)


@dataclass
class VideoSink:
    id: str
    name: str
    role: SinkRole
    endpoints: list[PhysicalEndpoint]
    ultra_low_latency: bool = False
    metadata: dict[str, Any] = field(default_factory=dict)


@dataclass
class RouteIntent:
    source_id: str
    sink_id: str
    operator: str
    scene_id: Optional[str] = None
    request_lock: LockMode = LockMode.NONE
    context: dict[str, Any] = field(default_factory=dict)


@dataclass
class PolicyConstraints:
    max_latency_ms: int = 100
    max_bridge_hops: int = 1
    require_lossless: bool = True
    require_genlock_with: Optional[SourceType] = None
    allow_multicast_fanout: bool = False
    prefer_multicast_when_sinks_gte: int = 2
    lock_mode: LockMode = LockMode.NONE
    capability_driven: bool = False


@dataclass
class PolicyDecision:
    preferred_fabrics: list[FabricKind]
    fallback_fabrics: list[FabricKind]
    constraints: PolicyConstraints
    reason: str
    priority: int = 100


@dataclass
class FabricStep:
    fabric: FabricKind
    action: str
    params: dict[str, Any]
    compensation_action: Optional[str] = None
    compensation_params: Optional[dict[str, Any]] = None


@dataclass
class RoutePlan:
    intent: RouteIntent
    steps: list[FabricStep]
    estimated_latency_ms: int
    fabrics_used: list[FabricKind]
    decision: PolicyDecision
    path_node_ids: list[str] = field(default_factory=list)


@dataclass
class RouteState:
    route_id: str
    source_id: str
    sink_id: str
    plan: RoutePlan
    active: bool = True
    lock_mode: LockMode = LockMode.NONE
    version: int = 1
