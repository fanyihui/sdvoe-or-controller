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
# 应用1：本手术室 SDVoE 设备清单
mvn -q compile exec:java -Dexec.mainClass=com.or.sdvoe.app.ListOrSdvoeDevicesApp

# 仅 Encoder / JSON
mvn -q exec:java -Dexec.mainClass=com.or.sdvoe.app.ListOrSdvoeDevicesApp -Dexec.args="--role ENCODER"
mvn -q exec:java -Dexec.mainClass=com.or.sdvoe.app.ListOrSdvoeDevicesApp -Dexec.args="--json"

# HTTP API
mvn -q exec:java -Dexec.mainClass=com.or.sdvoe.app.OrInventoryHttpServer -Dexec.args="8080"
```

配置：`src/main/resources/or-controller.yaml`（或 `config/or-controller.yaml`）

说明：[docs/APP1_DEVICE_INVENTORY.md](docs/APP1_DEVICE_INVENTORY.md)

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