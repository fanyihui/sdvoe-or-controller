# SDVoE OR Controller

手术室 **SDVoE + 视频矩阵** 统一路由控制框架（**Spring Boot 3**）。

## Features

- Spring Boot Web API + OR Desk 前端
- 首页：当日手术排班；工作空间：患者信息 / 视频源 / 输出目的地
- 拖拽路由 + 服务端持久化（SQLite / PostgreSQL）+ 启动自动恢复

## Quick start

```bash
cd java
mvn -q spring-boot:run
# http://127.0.0.1:8080
```

```bash
curl -s http://127.0.0.1:8080/health
curl -s http://127.0.0.1:8080/api/v1/or/schedule
```

## Layout

| Path | Description |
|------|-------------|
| `java/` | Spring Boot 主工程 |
| `web/` | 前端源码（同时打包到 `classpath:/static/`） |
| `docs/` | 架构与应用框架设计 |
| `config/` | 业务 YAML 副本 |
| `src/` / `examples/` | 早期 Python 对照骨架 |

## Docs

- [Spring Boot](java/docs/SPRING_BOOT.md)
- [App framework](docs/APP_FRAMEWORK.md)
- [Server route store](java/docs/ROUTE_SERVER_STORE.md)
- [Architecture](docs/ARCHITECTURE.md)

## License

MIT
