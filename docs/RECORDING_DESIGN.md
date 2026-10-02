# 单路视频源录制 — 设计方案

> 目标：用户在手术工作空间中**选择一路视频源进行录制**。  
> **已选型：选项 B — 软件落盘**（Recording Worker 订阅源流写文件）。

---

## 0. 决策记录（已确认）

| 项 | 决定 | 说明 |
|----|------|------|
| 媒体路径 | **B 软件落盘** | Worker 订 `streamId` 落盘；不依赖录播机 API |
| `mode` | `SOFTWARE` | 会话字段固定为此模式 |
| 与 `dst-recorder` | **解耦** | 开录不强制占用录播路由；监看仍可用拖拽 |
| 并发 | **同时仅 1 路** | 同 case 仅一个 `RECORDING` |
| UI（建议默认） | **U1 源卡片录制按钮** | 可再加顶栏「录制中」条 |
| 无信号（建议默认） | **禁止开录** | 源 OFFLINE/无信号 → 422 |
| 文件落点（建议默认） | **本地/NAS 路径** | `artifact` 存 URI；对象存储留二期 |
| 换台（建议默认） | **硬停** | 结束/换台前自动 stop |
| 隐私（建议默认） | MVP 不限制类型 | 配置项预留 `recording.denied_source_types` |
| 拼屏录制 | **二期** | Worker 接口预留 `streamId`，不绑死「仅逻辑源」 |

> 标「建议默认」的项若你有不同意见，实现前改一下即可。

---

## 1. 问题定义

### 1.1 本期范围（建议 MVP）

| 纳入 | 不纳入（后续） |
|------|----------------|
| 选中**一路**逻辑视频源开始/停止录制 | 多路同录、拼屏画面录制 |
| 录制绑定当前手术 `caseId` | 跨手术室集中媒资库 |
| 显示录制状态（空闲 / 录制中 / 失败） | 剪辑、打点、字幕、术后剪辑台 |
| 录制会话可查询、可持久化 | 完整 PACS/HIS 回写 |
| 软件 Worker 启停与文件 URI | 远端示教直播；强制占用录播路由 |

### 1.2 与现有能力的关系

当前系统已有：

- 逻辑源 `WorkspaceSource` → 物理 Encoder
- 逻辑目的地 `dst-recorder`（`SinkRole.RECORDER`）
- 拖拽路由：源 → 任意目的地（含录播）
- 策略：`RECORDER` 可配置为镜像主吊臂等

**缺口**：路由到「录播输入」≠「正在录制」。录制是**有生命周期的会话**（开始/停止/文件/元数据），需要独立概念。

---

## 2. 核心概念

```text
RecordingSession（录制会话）
├── sessionId
├── caseId                 # 所属手术
├── sourceId               # 被录的逻辑源（本期仅 1 路）
├── sourceName / streamId  # 快照，便于审计
├── mode                   # SOFTWARE（已定 B）
├── recorderTargetId       # 软件录制器/Worker 实例 ID（非 dst-recorder）
├── status                 # IDLE→PREPARING→RECORDING→STOPPING→STOPPED|FAILED
├── startedAt / stoppedAt
├── operator
├── artifact               # 文件路径 / 对象存储 key / 外部任务 ID（按选型）
└── errorMessage?
```

约束建议（MVP）：

1. **同一手术同一时刻最多 1 个 RECORDING 会话**（可配置放宽）。
2. 源设备 OFFLINE / 无信号时禁止开始（或仅警告，由策略决定）。
3. 停止后会话只读；再录创建新会话。
4. 换台 / 结束手术时：未停止的会话自动 STOP 或阻断结束（需产品规则）。

---

## 3. 架构选项（请选型）

录制「画面从哪来、文件谁写」决定整条链路。三条主路径可并存演进，建议先定一条作为 MVP。

### 选项 A — 路由到硬件录播机（控制面轻）

```text
选源 → OR Desk 建立 Route(source → dst-recorder)
     → 调用录播机 API：start/stop（或 RS-232/GPIO）
     → 文件留在录播机本地 / NAS
```

| 优点 | 缺点 |
|------|------|
| 与现有拖拽路由天然契合 | 依赖厂商录播 API 能力差异大 |
| 画质/延迟由专业硬件保证 | 元数据、文件回收、改名对接复杂 |
| OR Desk 几乎不做媒体处理 | 难以统一「文件在哪」的体验 |

**适配点**：`RecorderAdapter`（厂商 SDK / HTTP / 串口）+ 路由复用 `WorkspaceRoutingService`。

适合：已有独立录播主机、且 API 可用的现场。

---

### 选项 B — SDVoE/IP 流软件落盘（控制面自建录制器）

```text
选源 → 确保 Encoder 发流（multicast/unicast）
     → Recording Worker 订阅 streamId
     → FFmpeg / GStreamer / 厂商 SDK 写文件
     → 文件存本地盘 / NAS / 对象存储
```

| 优点 | 缺点 |
|------|------|
| 不依赖特定录播机品牌 | 需部署 Worker（同机或旁路） |
| 文件命名、元数据、存储策略完全可控 | 需评估带宽、CPU、磁盘、丢包 |
| 与拼屏合成流后续可复用同一 Worker | 医疗合规（存储加密、审计）要自建 |

**适配点**：`RecordingWorker`（进程/容器）+ `RecordingService` 下发 job；可选仍路由一份到 `dst-recorder` 做监看。

适合：希望 OR Desk 统一管文件、现场以 SDVoE 为主。

---

### 选项 C — 混合：路由到录播输入 + 软件旁路录一份

```text
选源 → Route → dst-recorder（给录播机/监看）
     → 同时 Worker 旁路订阅同 stream 落盘（归档）
```

| 优点 | 缺点 |
|------|------|
| 兼容旧录播操作习惯 | 两套落点，状态要对齐 |
| 归档路径可标准化 | 复杂度最高 |

适合：过渡期（老录播机仍在用，同时要统一归档）。

---

### 选项对照（决策表）

| 决策项 | A 硬件录播 | B 软件落盘 | C 混合 |
|--------|------------|------------|--------|
| 媒体处理位置 | 录播机 | Recording Worker | 两者 |
| OR Desk 职责 | 路由 + 启停命令 | 路由(可选) + job 编排 | 两者 |
| 文件归属 | 厂商设备/NAS | 自建存储 | 双份或择一主存 |
| 实现侵入度 | 低～中 | 中 | 高 |
| 厂商锁定 | 高 | 低 | 中 |
| 与拼屏后续扩展 | 一般 | 好（可录 mosaic 流） | 好 |

**建议默认讨论起点**：若现场已有录播机且要快上线 → **A**；若要产品化文件与元数据 → **B**；若两者都要 → **C（二期）**。

---

## 4. 领域分层（与现有代码对齐）

无论选 A/B/C，上层模型建议统一，下层用 Adapter 切换：

```text
UI / REST
    │
    ▼
RecordingService          # 会话生命周期、冲突、校验
    │
    ├── WorkspaceRoutingService   # 可选：保证源→recorder 通路
    ├── RecorderControlPort       # 接口：start/stop/status/queryFile
    │       ├── HardwareRecorderAdapter   # 选项 A
    │       └── SoftwareRecorderAdapter   # 选项 B（调 Worker）
    └── RecordingRepository       # 会话持久化（同路由库或同库新表）
```

```text
RecordingRepository 表建议：workspace_recordings
  session_id PK, or_id, case_id, source_id, source_name,
  stream_id, mode, target_id, status, artifact_uri,
  operator, error_message, started_at, stopped_at, created_at, updated_at
```

启动恢复：与路由/拼屏一致 —— `RECORDING` 状态会话尝试向 Adapter 探活；失联则标 `FAILED` 或保持 `RECORDING` 待人工处理（策略可选）。

---

## 5. 交互设计（工作空间）

### 5.1 入口（二选一或并存）

**方案 U1 — 源卡片上的录制按钮（推荐 MVP）**

- 每个在线视频源卡片增加「录制」操作
- 点击 → 确认 → 开始；录制中该源高亮，显示计时
- 全局只允许一路：其它源的「录制」禁用或提示「先停止当前录制」

**方案 U2 — 独立「录制」面板**

- 下拉选择源 + 大按钮 开始/停止
- 更清晰，适合触摸屏；占纵向空间

**方案 U3 — 拖到「录播」目的地即开录**

- 与现有拖拽一致，但语义过载（路由 ≠ 录制）
- 不推荐作为唯一入口；可作为「仅路由到录播机监看」

### 5.2 状态呈现

| UI 状态 | 含义 |
|---------|------|
| 空闲 | 无进行中会话 |
| 准备中 | 正在建路由 / 通知录播机 / Worker 拉流 |
| 录制中 | 显示源名 + 已录时长 |
| 已停止 | Toast：已保存（若有 artifact 路径/任务号） |
| 失败 | 错误原因 + 可重试 |

### 5.3 线框（U1）

```text
┌ 视频源 ─────────────────────────────┐
│ 腔镜主输出  ONLINE 有信号            │
│  [拖拽路由]  [● 录制]                │
├─────────────────────────────────────┤
│ 术野摄像机  ONLINE                  │
│  [拖拽路由]  [录制]  ← 禁用（已有录制）│
└─────────────────────────────────────┘

顶栏或浮层：● 录制中 · 腔镜主输出 · 00:12:36  [停止]
```

---

## 6. API 草案（选型后微调）

```http
GET    /api/v1/or/cases/{caseId}/recordings
POST   /api/v1/or/cases/{caseId}/recordings
       { "sourceId": "src-endo", "operator": "..." }
POST   /api/v1/or/cases/{caseId}/recordings/{sessionId}/stop
GET    /api/v1/or/cases/{caseId}/recordings/active   # 当前录制中（可无）
```

工作空间聚合增加：

```json
{
  "activeRecording": {
    "sessionId": "...",
    "sourceId": "src-endo",
    "sourceName": "腔镜主输出",
    "status": "RECORDING",
    "startedAt": "...",
    "elapsedSec": 756
  }
}
```

错误：

- `409` 已有进行中录制
- `422` 源离线/无信号/录制器不可用
- `404` 源或会话不存在

---

## 7. 策略与安全

| 规则 | 说明 |
|------|------|
| 源类型限制 | 可选：禁止录 PACS/含患者浏览器画面（隐私） |
| 关键源锁定 | 录制中可 soft-lock 源路由，防误切导致黑场 |
| 与拼屏冲突 | MVP：允许录原源、不录 mosaic；二期再支持录合成流 |
| 审计 | 记录 operator、起止时间、source、artifact、失败原因 |
| 存储合规 | 路径加密、保留周期、是否允许本地下载 — 按院方要求 |

现有 policy 中 `RECORDER: mirror_of PRIMARY` 与「用户自选源录制」可能冲突：  
**建议**：用户显式开录时，**以用户选择覆盖镜像策略**；镜像策略仅用于「未开录时的默认预览路由」。

---

## 8. 生命周期与边界情况

```text
[请求开始]
   → 校验 case / source / 无冲突会话 / 源在线有信号
   → 解析 streamId（确保 Encoder 在发流；必要时 set_stream）
   → SoftwareRecorderAdapter.start(session, streamId, outputPath)
   → Worker 订阅并写文件 → status=RECORDING，持久化
[请求停止]
   → Adapter.stop(session) → 返回 artifact URI / 时长 / 大小
   → status=STOPPED，持久化
[异常]
   → 源掉线 / Worker 崩溃 / 磁盘满 → FAILED + 告警
[进程重启]
   → 加载 RECORDING 会话 → probe Worker → 对齐或 FAILED
```

换台规则（已定建议）：**硬停** — 结束本案 / 进入下一台前自动 stop。

### 选项 B 落地形态（实现时）

```text
OR Desk (Spring Boot)
  RecordingService
       │ HTTP/gRPC/本地进程
       ▼
Recording Worker
  - input: streamId / multicast 组 / 源描述
  - output: /data/recordings/{orId}/{caseId}/{sessionId}.mp4
  - 健康检查 / 磁盘水位
```

MVP 可用 **Stub Worker**（只记会话与假文件路径），P1 再接真实 FFmpeg/GStreamer。

---

## 9. 决策清单状态

| # | 项 | 状态 |
|---|----|------|
| 1 | 媒体路径 B | **已定** |
| 2 | UI U1 | 建议默认（可改） |
| 3 | 同时仅 1 路 | **已定（随 B 配套）** |
| 4 | 无信号禁止开录 | 建议默认 |
| 5 | 不强制占用 dst-recorder | **已定（随 B）** |
| 6 | 本地/NAS 文件 URI | 建议默认 |
| 7 | 换台硬停 | 建议默认 |
| 8 | MVP 不限 SourceType | 建议默认 |
| 9 | 拼屏录制二期 | **已定** |

---

## 10. 落地节奏（按 B）

| 阶段 | 内容 |
|------|------|
| P0 | `RecordingSession` + API + UI 启停 + DB；`SoftwareRecorderAdapter` stub |
| P1 | 真实 Worker（FFmpeg/GStreamer）订 SDVoE 流落盘 + 健康检查 |
| P2 | 启动恢复、审计、文件列表、换台硬停 |
| P3 | 拼屏流录制、多路、对象存储、HIS/PACS 回写 |

---

## 11. 与现有模块的挂载点（实现时）

| 模块 | 改动 |
|------|------|
| `workspace/` | 新增 `RecordingService`、`ActiveRecording` |
| `persistence/` | `RecordingRepository`（同 SQLite/Postgres） |
| `adapter/` | `RecorderControlPort` + `SoftwareRecorderAdapter`（→ Worker） |
| `web/` | `RecordingApiController`；workspace 增加 `activeRecording` |
| `static/` | 源卡片「录制」+ 顶栏录制态 |
| 配置 | `recording.output_dir`、Worker 地址、磁盘水位 |

---

*媒体路径已锁定为 B。其余「建议默认」无异议即可进入 P0 实现。*
