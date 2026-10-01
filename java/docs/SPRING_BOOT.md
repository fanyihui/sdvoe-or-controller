# Spring Boot 版 OR Desk

主入口：`com.or.sdvoe.OrDeskApplication`

## 运行

```bash
cd java
mvn -q spring-boot:run

# 或
mvn -q -DskipTests package
java -jar target/sdvoe-or-controller-0.1.0-SNAPSHOT.jar
```

打开 http://127.0.0.1:8080

## 模块

| 包 | 职责 |
|----|------|
| `com.or.sdvoe` | Spring Boot 启动类 |
| `com.or.sdvoe.config` | Bean 装配、DB/业务 YAML、Web MVC |
| `com.or.sdvoe.web` | REST Controllers + 异常处理 + SPA 转发 |
| `com.or.sdvoe.workspace` / `schedule` / `persistence`… | 原有业务核心（Spring 注入） |

## 配置

- `application.yml` — 端口、静态资源
- `or-controller.yaml` / `schedule.yaml` / `routing-policies.yaml` — 业务配置（classpath）
- 环境变量：`OR_DESK_DB`、`OR_DESK_JDBC_URL` 等（同前）

## API（保持兼容）

- `GET /health`
- `GET /api/v1/or/schedule`
- `GET /api/v1/or/cases/{id}/workspace`
- `POST /api/v1/or/cases/{id}/routes`
- `GET /api/v1/or/mosaic/layouts` · `.../cases/{id}/mosaics` · push/cells
- `GET /api/v1/or/routes` / `export` / `backup` / `import`

详见 [MOSAIC.md](./MOSAIC.md)。
