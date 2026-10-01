# Java 版 SDVoE OR Controller

对应 Python 骨架的 Java 实现（Maven / Java 21）。

## 包结构

| 包 | 职责 |
|----|------|
| `com.or.sdvoe.domain` | Source/Sink/Route/Policy 领域模型 |
| `com.or.sdvoe.policy` | 按源类型决策 Matrix / SDVoE |
| `com.or.sdvoe.planner` | 跨 Fabric 拓扑寻路 |
| `com.or.sdvoe.adapter` | Matrix / SDVoE / Bridge 适配器 |
| `com.or.sdvoe.service` | 编排与 RouteService |
| `com.or.sdvoe.demo` | 可运行演示 |

策略配置：`src/main/resources/routing-policies.yaml`

## 运行

```bash
cd java
# OR Desk 控制台（排班首页 + 手术工作空间 UI/API）
mvn -q compile exec:java -Dexec.mainClass=com.or.sdvoe.app.OrConsoleHttpServer -Dexec.args="8080"
# http://127.0.0.1:8080

# 应用1：本手术室 SDVoE 设备清单 CLI
mvn -q exec:java -Dexec.mainClass=com.or.sdvoe.app.ListOrSdvoeDevicesApp
```

配置：

- `src/main/resources/or-controller.yaml` — 手术室与 SDVoE 设备
- `src/main/resources/schedule.yaml` — 当日排班与逻辑源/目的地
- 前端：仓库根目录 `web/`（亦打包进 `classpath:web/`）

说明：

- [应用框架](../docs/APP_FRAMEWORK.md)
- [设备清单](docs/APP1_DEVICE_INVENTORY.md)

## 包结构

| 包 | 职责 |
|----|------|
| `com.or.sdvoe.domain` | Source/Sink/Route/SdvoeDevice 领域模型 |
| `com.or.sdvoe.policy` | 按源类型决策 Matrix / SDVoE |
| `com.or.sdvoe.planner` | 跨 Fabric 拓扑寻路 |
| `com.or.sdvoe.adapter` | Matrix / SDVoE / Bridge 适配器 |
| `com.or.sdvoe.discovery` | SDVoE 设备发现（inventory/mock/http） |
| `com.or.sdvoe.service` | 编排、RouteService、设备清单服务 |
| `com.or.sdvoe.app` | 可运行应用入口 |
| `com.or.sdvoe.demo` | 路由演示 |