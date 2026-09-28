# SDVoE OR Controller

手术室 **SDVoE + 视频矩阵** 统一路由控制框架。

首个应用：获取本手术室内全部 SDVoE 设备清单。

## Features

- 统一领域模型（Source / Sink / Route / Scene）
- Matrix / SDVoE / Bridge 适配器抽象
- 按视频源类型选择 Matrix 或 SDVoE 的策略引擎
- **应用 1**：本手术室 SDVoE 设备清单（CLI + HTTP API）

## Quick start (Java)

```bash
cd java
mvn -q compile exec:java -Dexec.mainClass=com.or.sdvoe.app.ListOrSdvoeDevicesApp
```

HTTP API:

```bash
mvn -q exec:java -Dexec.mainClass=com.or.sdvoe.app.OrInventoryHttpServer -Dexec.args="8080"
curl -s http://127.0.0.1:8080/api/v1/or/sdvoe/devices
```

## Layout

| Path | Description |
|------|-------------|
| `java/` | Java 21 Maven 主工程（推荐） |
| `docs/` | 架构与策略设计 |
| `config/` | 路由策略与手术室配置 |
| `src/` / `examples/` | 早期 Python 对照骨架 |

## Docs

- [Architecture](docs/ARCHITECTURE.md)
- [App1: SDVoE device inventory](java/docs/APP1_DEVICE_INVENTORY.md)
- [Policy flow](docs/POLICY_FLOW.md)

## License

MIT
