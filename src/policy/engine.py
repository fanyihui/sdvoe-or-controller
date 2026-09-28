"""Routing policy engine: choose Matrix vs SDVoE by source type and context."""

from __future__ import annotations

from typing import Any, Optional

import yaml

from domain.models import (
    DistanceClass,
    FabricKind,
    LockMode,
    PolicyConstraints,
    PolicyDecision,
    SinkRole,
    SourceType,
    VideoSink,
    VideoSource,
)


class PolicyEngine:
    """Loads YAML strategies and resolves a PolicyDecision for a route intent."""

    def __init__(self, config_path: str):
        with open(config_path, "r", encoding="utf-8") as f:
            self._cfg = yaml.safe_load(f)
        self._policies: dict[str, Any] = self._cfg.get("source_type_policies", {})

    def decide(
        self,
        source: VideoSource,
        sink: VideoSink,
        *,
        fanout_count: int = 1,
        cross_room: bool = False,
        active_endoscope_fabric: Optional[FabricKind] = None,
    ) -> PolicyDecision:
        raw = self._policies.get(source.source_type.value)
        if not raw:
            return PolicyDecision(
                preferred_fabrics=[FabricKind.SDVOE, FabricKind.MATRIX],
                fallback_fabrics=[FabricKind.MATRIX],
                constraints=PolicyConstraints(),
                reason="default: unknown source type",
                priority=0,
            )

        preferred = [FabricKind(x) for x in raw.get("preferred_fabrics", [])]
        fallback = [FabricKind(x) for x in raw.get("fallback_fabrics", [])]
        constraints = self._parse_constraints(raw.get("constraints", {}))
        reason_parts = [raw.get("description", source.source_type.value)]

        # Sink-role overrides (e.g. endoscope -> primary display forces matrix)
        for override in raw.get("sink_overrides", []):
            if override.get("when_sink_role") == sink.role.value:
                if "preferred_fabrics" in override:
                    preferred = [FabricKind(x) for x in override["preferred_fabrics"]]
                    reason_parts.append(f"sink_override={sink.role.value}")
                if "constraints" in override:
                    constraints = self._merge_constraints(
                        constraints, override["constraints"]
                    )

        # Dynamic rules (ultrasound near/far, etc.)
        for rule in raw.get("rules", []):
            if self._eval_rule(rule.get("if", ""), source, cross_room):
                if "preferred_fabrics" in rule:
                    preferred = [FabricKind(x) for x in rule["preferred_fabrics"]]
                    reason_parts.append(f"rule={rule.get('if')}")

        # Fanout: prefer SDVoE multicast when many sinks
        if (
            constraints.allow_multicast_fanout
            and fanout_count >= constraints.prefer_multicast_when_sinks_gte
        ):
            preferred = self._boost_fabric(preferred, FabricKind.SDVOE)
            reason_parts.append(f"fanout={fanout_count} -> prefer SDVoE")

        # Primary surgeon ultra-low latency boost for critical sources
        if sink.ultra_low_latency or sink.role == SinkRole.PRIMARY_SURGEON_DISPLAY:
            if source.source_type in {SourceType.ENDOSCOPE, SourceType.CARM_FLUORO}:
                preferred = self._boost_fabric(preferred, FabricKind.MATRIX)
                constraints.max_bridge_hops = min(constraints.max_bridge_hops, 0)
                constraints.max_latency_ms = min(constraints.max_latency_ms, 40)
                if constraints.lock_mode == LockMode.NONE:
                    constraints.lock_mode = LockMode.HARD
                reason_parts.append("critical primary display -> MATRIX first")

        # Navigation: align fabric with active endoscope path when possible
        if (
            source.source_type == SourceType.NAVIGATION
            and constraints.require_genlock_with == SourceType.ENDOSCOPE
            and active_endoscope_fabric is not None
        ):
            preferred = self._boost_fabric(preferred, active_endoscope_fabric)
            reason_parts.append(
                f"genlock with endoscope on {active_endoscope_fabric.value}"
            )

        # Capability-driven external sources: prefer fabric matching endpoint
        if constraints.capability_driven and source.endpoints:
            fabrics = {ep.fabric for ep in source.endpoints if ep.online}
            if fabrics == {FabricKind.MATRIX}:
                preferred = [FabricKind.MATRIX]
                reason_parts.append("capability: source only on MATRIX")
            elif fabrics == {FabricKind.SDVOE}:
                preferred = [FabricKind.SDVOE]
                reason_parts.append("capability: source only on SDVOE")

        return PolicyDecision(
            preferred_fabrics=preferred,
            fallback_fabrics=fallback,
            constraints=constraints,
            reason=" | ".join(reason_parts),
            priority=200 if source.critical else 100,
        )

    @staticmethod
    def _parse_constraints(data: dict[str, Any]) -> PolicyConstraints:
        genlock = data.get("require_genlock_with")
        return PolicyConstraints(
            max_latency_ms=int(data.get("max_latency_ms", 100)),
            max_bridge_hops=int(data.get("max_bridge_hops", 1)),
            require_lossless=bool(data.get("require_lossless", True)),
            require_genlock_with=SourceType(genlock) if genlock else None,
            allow_multicast_fanout=bool(data.get("allow_multicast_fanout", False)),
            prefer_multicast_when_sinks_gte=int(
                data.get("prefer_multicast_when_sinks_gte", 2)
            ),
            lock_mode=LockMode(data.get("lock_mode", "none")),
            capability_driven=bool(data.get("capability_driven", False)),
        )

    @staticmethod
    def _merge_constraints(
        base: PolicyConstraints, override: dict[str, Any]
    ) -> PolicyConstraints:
        data = {
            "max_latency_ms": override.get("max_latency_ms", base.max_latency_ms),
            "max_bridge_hops": override.get("max_bridge_hops", base.max_bridge_hops),
            "require_lossless": override.get("require_lossless", base.require_lossless),
            "require_genlock_with": (
                override.get("require_genlock_with").value
                if hasattr(override.get("require_genlock_with"), "value")
                else override.get(
                    "require_genlock_with",
                    base.require_genlock_with.value if base.require_genlock_with else None,
                )
            ),
            "allow_multicast_fanout": override.get(
                "allow_multicast_fanout", base.allow_multicast_fanout
            ),
            "prefer_multicast_when_sinks_gte": override.get(
                "prefer_multicast_when_sinks_gte", base.prefer_multicast_when_sinks_gte
            ),
            "lock_mode": override.get("lock_mode", base.lock_mode.value),
            "capability_driven": override.get(
                "capability_driven", base.capability_driven
            ),
        }
        return PolicyEngine._parse_constraints(data)

    @staticmethod
    def _eval_rule(expr: str, source: VideoSource, cross_room: bool) -> bool:
        if not expr:
            return False
        # Intentionally small expression surface for safety.
        if "distance_class == 'NEAR'" in expr and source.distance_class == DistanceClass.NEAR:
            return True
        if "distance_class == 'FAR'" in expr and source.distance_class == DistanceClass.FAR:
            return True
        if "cross_room" in expr and cross_room:
            return True
        return False

    @staticmethod
    def _boost_fabric(
        preferred: list[FabricKind], fabric: FabricKind
    ) -> list[FabricKind]:
        rest = [f for f in preferred if f != fabric]
        return [fabric] + rest
