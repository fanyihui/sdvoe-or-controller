# SDVoE OR Controller

手术室 **SDVoE + 视频矩阵** 统一路由控制框架。

首个应用：获取本手术室内全部 SDVoE 设备清单。

## Features

- 统一领域模型（Source / Sink / Route / Scene）
- Matrix / SDVoE / Bridge 适配器抽象
- 按视频源类型选择 Matrix 或 SDVoE 的策略引擎
- **应用 1**：本手术室 SDVoE 设备清单（CLI + HTTP API）

## Quick start — OR Desk 控制台

```bash
cd java
mvn -q compile exec:java -Dexec.mainClass=com.or.sdvoe.app.OrConsoleHttpServer -Dexec.args="8080"
# 浏览器打开 http://127.0.0.1:8080
```

- 首页：本手术室当日排班患者手术列表  
- 进入工作空间：患者/手术信息 · 视频源 · 输出目的地  

```bash
curl -s http://127.0.0.1:8080/api/v1/or/schedule
curl -s http://127.0.0.1:8080/api/v1/or/cases/case-20261001-001/workspace
```

设备清单 CLI：

```bash
mvn -q exec:java -Dexec.mainClass=com.or.sdvoe.app.ListOrSdvoeDevicesApp
```

## Layout

| Path | Description |
|------|-------------|
| `web/` | OR Desk 前端（首页 + 手术工作空间） |
| `java/` | Java 21 Maven 主工程 |
| `docs/` | 架构与应用框架设计 |
| `config/` | 手术室、排班、路由策略配置 |
| `src/` / `examples/` | 早期 Python 对照骨架 |

## Docs

- [App framework（排班→工作空间）](docs/APP_FRAMEWORK.md)
- [Architecture](docs/ARCHITECTURE.md)
- [App1: SDVoE device inventory](java/docs/APP1_DEVICE_INVENTORY.md)
- [Policy flow](docs/POLICY_FLOW.md)

## License

MIT
