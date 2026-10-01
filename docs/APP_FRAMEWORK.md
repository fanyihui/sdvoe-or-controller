# 手术室应用框架（OR Console）

## 1. 产品目标

面向单间手术室控制屏 / 护士站工作站：

1. **首页**：展示本手术室当日排班患者手术列表  
2. **手术工作空间**：选中一台手术后进入，集中管理  
   - 患者与手术信息  
   - 可用视频源  
   - 输出目的地（显示/录播/示教）  
   - （后续）路由切换、场景预设、锁定

产品名建议：**OR Desk**（术间台）

---

## 2. 信息架构

```text
OR Desk
├── 首页 /schedule
│   └── 当日手术卡片列表（按开台时间）
│       ├── 患者姓名 / 性别年龄
│       ├── 术式 · 主刀
│       ├── 计划时间 · 状态（待接台/进行中/已完成）
│       └── CTA：进入工作空间
│
└── 手术工作空间 /cases/:caseId
    ├── 顶栏：返回列表 · 手术室 · 病例号 · 状态
    ├── 患者与手术信息（Patient & Case）
    ├── 视频源（Sources）—— 逻辑源绑定物理 Encoder/Matrix In
    └── 输出目的地（Destinations）—— 吊臂/墙显/录播 Decoder/Matrix Out
```

导航原则：

- 首页只有「排班列表」一件事，不堆设备运维入口  
- 工作空间内三个主面板并列，路由操作为面板内交互（后续迭代）  
- 设备运维（全量 SDVoE 清单）作为次要入口，不抢首页

---

## 3. 页面线框

### 3.1 首页

```text
┌──────────────────────────────────────────────────┐
│  OR Desk          1号手术室 · 外科楼3F    2026-10-01 │
│  今日手术排班                                       │
│  本室当日共 4 台 · 进行中 1 · 待接台 2                │
├──────────────────────────────────────────────────┤
│  08:30  进行中                                      │
│  张××  男 52岁     腹腔镜胆囊切除术                   │
│  主刀 李××          麻醉 王××          [进入工作空间] │
├──────────────────────────────────────────────────┤
│  10:30  待接台                                      │
│  ...                                               │
└──────────────────────────────────────────────────┘
```

### 3.2 手术工作空间

```text
┌────────────────────────────────────────────────────────────┐
│ ← 返回排班   OR Desk · 张×× · 腹腔镜胆囊切除术 · 进行中      │
├──────────────┬─────────────────────┬───────────────────────┤
│ 患者与手术    │ 视频源               │ 输出目的地              │
│ 姓名/ID/血型  │ ○ 腔镜主输出 (有信号) │ ○ 主吊臂显示            │
│ 术式/主刀     │ ○ 术野相机           │ ○ 辅吊臂                │
│ 过敏/备注     │ ○ 超声               │ ○ 左/右墙显             │
│ 计划/实际时间 │ ○ PACS               │ ○ 录播 In1             │
│              │  [设为主源]          │  [切换至此]            │
└──────────────┴─────────────────────┴───────────────────────┘
```

---

## 4. 领域模型

| 实体 | 说明 |
|------|------|
| `OperatingRoom` | 本控制器绑定的手术室 |
| `Patient` | 患者基本信息（脱敏展示） |
| `SurgeryCase` | 一台手术排班实例 |
| `CaseStatus` | SCHEDULED / PREP / IN_PROGRESS / CLOSING / DONE / CANCELLED |
| `LogicalVideoSource` | 业务视频源（腔镜/术野/超声…） |
| `LogicalDestination` | 业务输出（主吊臂/墙显/录播…） |
| `SurgeryWorkspace` | 某台手术的工作空间聚合视图 |

`SurgeryWorkspace` = Case + Patient + Sources[] + Destinations[] +（可选）ActiveRoutes[]

逻辑源/目的地通过 `endpointRef` / `deviceId` 关联 SDVoE 设备或矩阵端口（复用 App1 设备清单）。

---

## 5. API

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/v1/or` | 当前手术室 |
| GET | `/api/v1/or/schedule?date=YYYY-MM-DD` | 当日排班列表（默认今天） |
| GET | `/api/v1/or/cases/{caseId}` | 手术摘要 |
| GET | `/api/v1/or/cases/{caseId}/workspace` | 工作空间聚合 |
| GET | `/api/v1/or/sdvoe/devices` | 设备清单（已有） |

---

## 6. 模块划分

```text
com.or.sdvoe
├── domain          Patient, SurgeryCase, Workspace...
├── schedule        ScheduleRepository / ScheduleService
├── workspace       SurgeryWorkspaceService
├── discovery       SDVoE 设备发现（已有）
├── service         设备清单等
└── app
    ├── OrConsoleHttpServer   # 统一控制台：API + 静态前端
    └── ListOrSdvoeDevicesApp # 运维 CLI

web/                # OR Desk 前端（首页 + 工作空间）
config/schedule.yaml
```

---

## 7. 拖拽路由（已实现）

工作空间内：**拖拽视频源卡片 → 放到输出目的地**。

| Method | Path | 说明 |
|--------|------|------|
| POST | `/api/v1/or/cases/{id}/routes` | body: `{sourceId, destinationId, confirmed?, operator?}` |
| DELETE | `/api/v1/or/cases/{id}/routes/{destinationId}` | 清除该目的地路由 |
| GET | `/api/v1/or/cases/{id}/routes` | 当前活动路由列表 |

行为：

1. 策略引擎按源类型给出 Matrix/SDVoE 偏好（写入 `policyReason`）  
2. 经 `SDVoEAdapter` 下发 `set_stream` + `subscribe`  
3. 目的地已被占用时返回 **409**，前端确认后 `confirmed=true` 覆盖  
4. `workspace.destinations[].routedSourceName` 展示当前路由  

---

## 8. 演进

1. **P0**：排班首页 + 工作空间聚合 + 前端原型  
2. **P1（本迭代）**：工作空间拖拽源→目的地完成路由  
3. **P2**：场景预设（开台/示教）、主屏锁定、与 HIS 排班同步  
4. **P3**：跨室示教、录播联动、审计回放
