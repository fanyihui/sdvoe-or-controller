"""Fabric adapters: Matrix and SDVoE implementations behind a common interface."""

from __future__ import annotations

from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from typing import Any, Callable, Optional

from domain.models import FabricKind, FabricStep, PhysicalEndpoint


@dataclass
class FabricHealth:
    fabric: FabricKind
    online: bool
    detail: dict[str, Any] = field(default_factory=dict)


@dataclass
class ApplyResult:
    ok: bool
    message: str = ""
    applied: list[FabricStep] = field(default_factory=list)


class FabricAdapter(ABC):
    kind: FabricKind

    @abstractmethod
    def discover(self) -> list[PhysicalEndpoint]:
        raise NotImplementedError

    @abstractmethod
    def get_health(self) -> FabricHealth:
        raise NotImplementedError

    @abstractmethod
    def apply_steps(self, steps: list[FabricStep]) -> ApplyResult:
        raise NotImplementedError

    @abstractmethod
    def release(self, route_id: str) -> None:
        raise NotImplementedError

    def subscribe_events(self, handler: Callable[[dict[str, Any]], None]) -> None:
        return None


class MatrixAdapter(FabricAdapter):
    """Vendor-agnostic matrix adapter skeleton (Crestron/Extron/Blackmagic/...)."""

    kind = FabricKind.MATRIX

    def __init__(self, transport: Any):
        self._transport = transport  # RS232/TCP/HTTP client injected
        self._routes: dict[str, dict[str, Any]] = {}

    def discover(self) -> list[PhysicalEndpoint]:
        # Real impl: query matrix for port map / EDID / signal presence
        return []

    def get_health(self) -> FabricHealth:
        return FabricHealth(fabric=self.kind, online=True, detail={"transport": "ok"})

    def apply_steps(self, steps: list[FabricStep]) -> ApplyResult:
        applied: list[FabricStep] = []
        for step in steps:
            if step.fabric != FabricKind.MATRIX:
                continue
            if step.action == "switch_crosspoint":
                inp = step.params["input"]
                out = step.params["output"]
                # Example vendor call:
                # self._transport.switch(inp, out)
                self._routes[str(out)] = {"input": inp}
                applied.append(step)
            else:
                return ApplyResult(ok=False, message=f"unsupported matrix action: {step.action}")
        return ApplyResult(ok=True, message="matrix ok", applied=applied)

    def release(self, route_id: str) -> None:
        self._routes.pop(route_id, None)


class SDVoEAdapter(FabricAdapter):
    """SDVoE encoder/decoder control skeleton (multicast subscribe model)."""

    kind = FabricKind.SDVOE

    def __init__(self, manager_client: Any):
        self._client = manager_client
        self._subscriptions: dict[str, dict[str, Any]] = {}

    def discover(self) -> list[PhysicalEndpoint]:
        # Real impl: query SDVoE manager for encoders/decoders and stream IDs
        return []

    def get_health(self) -> FabricHealth:
        return FabricHealth(fabric=self.kind, online=True, detail={"manager": "ok"})

    def apply_steps(self, steps: list[FabricStep]) -> ApplyResult:
        applied: list[FabricStep] = []
        for step in steps:
            if step.fabric != FabricKind.SDVOE:
                continue
            if step.action == "set_stream":
                enc = step.params["encoder_id"]
                mode = step.params.get("mode", "multicast")
                stream_id = step.params["stream_id"]
                # self._client.set_encoder_stream(enc, stream_id, mode)
                self._subscriptions[enc] = {"stream_id": stream_id, "mode": mode}
                applied.append(step)
            elif step.action == "subscribe":
                dec = step.params["decoder_id"]
                stream_id = step.params["stream_id"]
                # self._client.subscribe(dec, stream_id)
                self._subscriptions[dec] = {"stream_id": stream_id}
                applied.append(step)
            elif step.action == "set_genlock":
                # self._client.set_genlock(**step.params)
                applied.append(step)
            else:
                return ApplyResult(ok=False, message=f"unsupported sdvoe action: {step.action}")
        return ApplyResult(ok=True, message="sdvoe ok", applied=applied)

    def release(self, route_id: str) -> None:
        self._subscriptions.pop(route_id, None)


class BridgeAdapter(FabricAdapter):
    """Controls Matrix <-> SDVoE media converters / glue ports."""

    kind = FabricKind.BRIDGE

    def __init__(self):
        self._enabled: set[str] = set()

    def discover(self) -> list[PhysicalEndpoint]:
        return []

    def get_health(self) -> FabricHealth:
        return FabricHealth(fabric=self.kind, online=True)

    def apply_steps(self, steps: list[FabricStep]) -> ApplyResult:
        applied: list[FabricStep] = []
        for step in steps:
            if step.fabric != FabricKind.BRIDGE:
                continue
            bridge_id = step.params.get("bridge_id", "default")
            if step.action == "enable_bridge":
                self._enabled.add(bridge_id)
                applied.append(step)
            elif step.action == "disable_bridge":
                self._enabled.discard(bridge_id)
                applied.append(step)
        return ApplyResult(ok=True, message="bridge ok", applied=applied)

    def release(self, route_id: str) -> None:
        return None
