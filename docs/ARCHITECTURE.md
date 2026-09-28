# 手术室 SDVoE Controller 整体框架

> 目标：统一管理手术室视频源与显示/录制输出的路由，同时兼容 **SDVoE（IP 视频）** 与 **传统视频矩阵**，并按视频源类型做传输通路策略选择。

---

## 1. 业务背景

手术室内典型端点：

| 角色 | 示例 |
|------|------|
| 视频源 | 腔镜主机、术野摄像机、超声、C 臂、麻醉监护、PACS 工作站、导航系统 |
| 视频输出 | 吊臂显示、墙显、护士站监视器、录播主机、示教/远程推流编码器 |
| 传输设施 | SDVoE Encoder/Decoder、SDI/HDMI 矩阵、光端机/光纤矩阵、混插转换器 |

痛点：

1. 老系统多为矩阵路由，新系统多为 SDVoE，需长期共存。
2. 不同源对延迟、带宽、色彩采样、EDID、HDCP、锁定同步要求不同。
3. 医护操作需要“选源 → 选屏”的统一体验，不应感知底层是矩阵还是 IP。
4. 需要支持场景预设（开台、缝合、示教、录制）、故障切换、审计。

---

## 2. 设计原则

1. **统一领域模型，异构适配**：上层只认识 Source / Sink / Route / Scene；Matrix 与 SDVoE 通过 Adapter 落地。
2. **策略与执行分离**：Policy Engine 决定走哪条通路；Fabric Driver 只负责下发。
3. **通路可组合**：允许 `Source → Matrix → SDVoE Encoder → Decoder → Sink` 等混合路径。
4. **能力驱动路由**：用 Capability（分辨率、色深、延迟等级、协议）匹配，而不是硬编码端口号。
5. **手术安全优先**：低延迟、可回退、可锁定关键路由、变更可审计。

---

## 3. 总体架构

```text
┌─────────────────────────────────────────────────────────────────┐
│                        Presentation / API                        │
│   REST / WebSocket / MQTT / HL7-FHIR 钩子 / 触摸屏 UI / 语音助手   │
└───────────────────────────────┬─────────────────────────────────┘
                                │
┌───────────────────────────────▼─────────────────────────────────┐
│                      Application Services                        │
│  RouteService · SceneService · DeviceInventory · SessionService  │
│  HealthMonitor · AuditService · PresetService                    │
└───────────────────────────────┬─────────────────────────────────┘
                                │
┌───────────────────────────────▼─────────────────────────────────┐
│                         Domain Core                              │
│  Source · Sink · Endpoint · RouteIntent · RoutePlan · Scene      │
│  CapabilityGraph · Topology · LockPolicy                         │
└───────────────┬─────────────────────────────┬───────────────────┘
                │                             │
┌───────────────▼──────────────┐  ┌───────────▼───────────────────┐
│        Policy Engine         │  │       Path Planner            │
│  SourceTypeStrategy          │  │  最短/最优代价路径              │
│  QoS / Latency / Fallback    │  │  混合 Fabric 构图              │
│  PreferMatrix / PreferSDVoE  │  │  冲突检测与抢占                 │
└───────────────┬──────────────┘  └───────────┬───────────────────┘
                │                             │
                └──────────────┬──────────────┘
                               │ RoutePlan (可执行步骤序列)
┌──────────────────────────────▼──────────────────────────────────┐
│                     Fabric Orchestrator                          │
│  事务式下发 · 补偿回滚 · 幂等 · 超时重试 · 状态收敛                │
└───────┬───────────────────────────────┬─────────────────────────┘
        │                               │
┌───────▼────────────┐        ┌─────────▼────────────────────────┐
│  Matrix Adapter    │        │  SDVoE Adapter                    │
│  Crestron/Extron   │        │  AptoVision/Semtech 生态          │
│  Blackmagic/自定义 │        │  Encoder/Decoder/Switch API       │
│  RS232/TCP/API     │        │  Multicast / Unicast / Genlock    │
└───────┬────────────┘        └─────────┬────────────────────────┘
        │                               │
┌───────▼───────────────────────────────▼────────────────────────┐
│                     Physical / Network Layer                     │
│  SDI/HDMI Matrix · SDVoE Fabric · Media Converter · Displays     │
└──────────────────────────────────────────────────────────────────┘
```

---

## 4. 分层职责

### 4.1 Presentation / API

- `POST /routes`：创建意图路由（sourceId → sinkId）
- `POST /scenes/{id}/apply`：应用开台预设
- `GET /topology`：当前拓扑与在线状态
- WebSocket 推送：链路状态、告警、EDID 变更

### 4.2 Application Services

- **RouteService**：接收意图，调用策略与规划，驱动编排
- **SceneService**：多路路由原子切换（开台/示教/清场）
- **DeviceInventory**：端点注册、能力发现、心跳
- **HealthMonitor**：丢包、黑场、链路断开、矩阵端口故障
- **AuditService**：谁在何时把哪路源切到哪块屏

### 4.3 Domain Core

核心对象：

- `VideoSource`：逻辑源（腔镜主输出、辅输出等）
- `VideoSink`：逻辑目的（主吊臂、辅吊臂、录播 In1）
- `PhysicalEndpoint`：真实端口（矩阵 In3、SDVoE Enc-12）
- `RouteIntent`：业务意图
- `RoutePlan`：可执行步骤列表（跨 Fabric）
- `Fabric`：`MATRIX` | `SDVOE` | `HYBRID_BRIDGE`

### 4.4 Policy + Planner

- Policy 输出：`preferredFabrics`、`forbiddenFabrics`、`maxLatencyMs`、`fallbackChain`
- Planner 在拓扑图上求满足约束的路径

### 4.5 Fabric Orchestrator + Adapters

- Orchestrator 把 `RoutePlan` 拆成 Adapter 调用
- Adapter 屏蔽厂商差异（矩阵切换 API vs SDVoE subscribe）

---

## 5. 拓扑抽象（关键）

把矩阵端口与 SDVoE 设备统一为图：

```text
[Source.Logical]
      │
      ▼
[Ingress Endpoint] ──capability──► 可进入 MATRIX 或 SDVOE 或两者
      │
      ├── MATRIX_IN ──► MATRIX ──► MATRIX_OUT ──► Bridge/Converter ──┐
      │                                                              │
      └── SDVOE_ENC ──► IP Fabric ──► SDVOE_DEC ─────────────────────┤
                                                                     ▼
                                                              [Egress Endpoint]
                                                                     │
                                                                     ▼
                                                               [Sink.Logical]
```

边带代价（Cost）：

- `latencyScore`
- `bandwidthCost`
- `reliabilityScore`
- `isBridgeHop`（矩阵↔IP 桥接惩罚，默认加分/加代价）
- `exclusiveLock`（独占端口冲突）

---

## 6. 视频源类型与路由策略

### 6.1 源类型枚举（建议）

| SourceType | 典型信号 | 关键诉求 |
|------------|----------|----------|
| `ENDOSCOPE` | 4K/HD SDI/HDMI | 极低延迟、稳定、主屏优先 |
| `SURGICAL_CAM` | HDMI/SDI | 低延迟、可多屏分发 |
| `ULTRASOUND` | HDMI/DVI | 中低延迟、分辨率保真 |
| `CARM_FLUORO` | DVI/SDI | 低延迟、灰阶保真 |
| `PATIENT_MONITOR` | HDMI/VGA→HDMI | 可容忍稍高延迟 |
| `PACS_WORKSTATION` | DP/HDMI | 高分辨率、静态画面友好 |
| `NAVIGATION` | HDMI | 低延迟 + 叠加同步 |
| `ROOM_PC` / `LAPTOP` | HDMI | 灵活、示教常用 |
| `RECORDER_PLAYBACK` | HDMI/SDI | 回放质量优先 |
| `EXTERNAL_IN` | 任意 | 按能力动态判定 |

### 6.2 策略矩阵（默认建议）

| SourceType | 首选通路 | 备选 | 理由 |
|------------|----------|------|------|
| `ENDOSCOPE` | **Matrix 直达主显** | SDVoE（仅当矩阵端口不足/远端屏） | 主刀屏延迟最敏感，尽量少跳 |
| `SURGICAL_CAM` | SDVoE | Matrix | 需多目标分发，IP 组播更合适 |
| `ULTRASOUND` | Matrix（近场） / SDVoE（跨室） | 另一侧 | 看物理距离与桥接质量 |
| `CARM_FLUORO` | Matrix | SDVoE lossless 模式 | 灰阶与实时性优先 |
| `PATIENT_MONITOR` | SDVoE | Matrix | 非关键手术视野，IP 更灵活 |
| `PACS_WORKSTATION` | SDVoE | Matrix | 高分静态，带宽可调度 |
| `NAVIGATION` | Matrix 或 SDVoE genlock | — | 需与主术野时间对齐时优先同 Fabric |
| `ROOM_PC` | SDVoE | Matrix | 示教/投屏场景多变 |
| `RECORDER_*` | 跟随被录源同路径镜像 | — | 避免二次转码/桥接劣化 |

### 6.3 策略判定伪流程

```text
输入: intent(source, sink), source.profile, sink.capabilities, topology.health
1. 加载 SourceTypeStrategy(source.type)
2. 计算候选 Fabrics = strategy.preferred ∩ reachable(source,sink)
3. 若 sink 要求 ultra_low_latency 且 source 为 ENDOSCOPE/CARM → 强制抬高 Matrix 权重
4. 若 sink 数量 > 1（一源多显）→ 抬高 SDVoE multicast 权重
5. 若路径需 >1 次 Matrix↔SDVoE 桥接 → 惩罚或拒绝
6. 选出代价最低路径；失败则按 fallbackChain 降级
7. 生成 RoutePlan 并加锁关键路由（可选）
```

### 6.4 策略配置示例（YAML）

见 `config/routing-policies.yaml`。

---

## 7. 关键用例时序

### 7.1 单路切换：腔镜 → 主吊臂

```text
UI → RouteService.createIntent(endoscope, boom_main)
   → PolicyEngine.decide()  => prefer MATRIX
   → PathPlanner.plan()     => matrix.in[3] → matrix.out[1] → boom_main
   → Orchestrator.execute()
        MatrixAdapter.switch(3,1)
   → StateStore.commit(route)
   → WS notify UI + Audit
```

### 7.2 一源多显：术野相机 → 3 块屏 + 录播

```text
Policy => prefer SDVoE (multicast)
Plan:
  cam → enc-7 join group G
  dec-A/B/C + recorder_enc subscribe G
若 recorder 仅接矩阵：
  增加 bridge: SDVoE_dec_bridge → matrix.in[x] → matrix.out[rec]
```

### 7.3 场景预设：开台

```text
Scene "OR1_START":
  - endoscope → boom_main   (matrix, locked)
  - endoscope → recorder    (mirror)
  - surgical_cam → wall_left (sdvoe)
  - pacs → wall_right       (sdvoe)
Apply 时按事务提交；任一步失败则补偿回滚到上一 Scene。
```

---

## 8. 模块边界与接口

### 8.1 Adapter 接口（统一）

```text
FabricAdapter
  - discover(): Endpoint[]
  - getHealth(): FabricHealth
  - applySteps(steps: FabricStep[]): Result
  - release(routeId)
  - subscribeEvents(handler)
```

Matrix 特有步骤：`SwitchCrosspoint(in, out)`  
SDVoE 特有步骤：`SetStream(enc, mode)`, `Subscribe(dec, streamId)`, `SetGenlock(...)`

### 8.2 Policy 接口

```text
RoutingPolicy
  - match(source, sink, context) -> PolicyDecision
PolicyDecision
  - preferredFabrics: FabricKind[]
  - fallbackFabrics: FabricKind[]
  - constraints: { maxLatencyMs, maxBridgeHops, requireLossless, requireGenlock }
  - priority: number
```

---

## 9. 状态、锁与安全

1. **路由锁**：主刀主屏路由可 `softLock`（需确认）或 `hardLock`（仅巡回护士角色可改）。
2. **冲突策略**：`reject` | `steal_with_confirm` | `queue`。
3. **黑场检测**：切换后 N ms 内无信号 → 自动 fallback。
4. **双活控制面**（可选）：主备 Controller，下发幂等 key = routeId+version。
5. **审计**：保留 source/sink/fabric/operator/timestamp/result。

---

## 10. 部署建议

```text
[OR Touch UI] ──LAN──► [OR Controller (本服务)]
                              │
              ┌───────────────┼────────────────┐
              ▼               ▼                ▼
        Matrix API      SDVoE Manager     Device Mgmt NMS
              │               │
              └────── OR AV Network ──────┘
```

- Controller 可按手术室 1:1 部署，中央再挂 Cluster Orchestrator 做跨室示教。
- 配置中心持有 `routing-policies.yaml` + 拓扑 inventory。
- 与 HIS/手术排程对接：按手术类型自动加载 Scene 模板。

---

## 11. 演进路线

1. **P0**：统一模型 + Matrix Adapter + 手动路由 API + 策略配置表  
2. **P1**：SDVoE Adapter + PathPlanner + Scene 预设 + 健康监测回退  
3. **P2**：混合桥接最优路径、一源多显优化、角色锁、跨室路由  
4. **P3**：基于实测延迟/丢包的自适应策略、数字孪生拓扑可视化

---

## 12. 非目标（首期不做）

- 不替代厂商 SDVoE 管理软件的全部底层调试能力
- 不做通用视频会议 MCU
- 不在控制面内做重编码（转码交给专用网关设备）
