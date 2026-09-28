# 策略决策流（简图）

```mermaid
flowchart TD
  A[RouteIntent source→sink] --> B[加载 SourceType 策略]
  B --> C{Sink 角色覆盖?}
  C -->|是 主刀屏+腔镜| D[强制 MATRIX / 禁桥接 / hardLock]
  C -->|否| E{一源多显?}
  E -->|是| F[抬高 SDVoE multicast]
  E -->|否| G{近场/跨室规则}
  G --> H[生成 PolicyDecision]
  D --> H
  F --> H
  H --> I[PathPlanner 在拓扑图寻路]
  I --> J{找到路径?}
  J -->|否| K[按 fallbackFabrics 重试]
  K --> J
  J -->|是| L[FabricOrchestrator 事务下发]
  L --> M[MatrixAdapter / SDVoEAdapter / Bridge]
  M --> N[状态提交 + 审计 + WS 通知]
```

## 混合通路示例

```text
腔镜(SDI) ──► Matrix IN ──► Matrix OUT ──► 主吊臂          （主路径，低延迟）
                │
                └──► Bridge Decoder/Encoder ──► SDVoE ──► 示教屏/远端
```

示教分发允许 1 次桥接；主刀屏策略 `max_bridge_hops: 0`。
