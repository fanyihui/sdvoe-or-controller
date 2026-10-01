# 服务端路由存储（防丢失）

路由配置保存在 **OR Desk 服务器端**，不依赖浏览器本地存储。

## 默认：本机 SQLite（服务端磁盘）

```yaml
database:
  type: sqlite
  path: data/or-desk.db
  backup_dir: data/backups
  auto_backup: true
  restore_routes_on_startup: true
```

可靠性措施：

1. `WAL + synchronous=FULL` 同步落盘  
2. 每次路由变更自动写 `data/backups/routes-latest.json`  
3. `POST /api/v1/or/routes/backup` 生成带时间戳的 JSON + DB 副本  
4. 启动时从服务器存储恢复并重新下发  

生产部署请把 `data/` 挂到持久化卷（Docker volume / 宿主机目录）。

## 推荐：远程 PostgreSQL（多机/容灾）

```yaml
database:
  type: postgres
  jdbc_url: jdbc:postgresql://db.hospital.local:5432/or_desk
  username: or_desk
  password: change-me
  restore_routes_on_startup: true
  auto_backup: true
  backup_dir: data/backups
```

或环境变量：

```bash
export OR_DESK_DB_TYPE=postgres
export OR_DESK_JDBC_URL=jdbc:postgresql://db.hospital.local:5432/or_desk
export OR_DESK_DB_USER=or_desk
export OR_DESK_DB_PASSWORD=change-me
```

## API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/or/routes` | 当前服务器中的活动路由 |
| GET | `/api/v1/or/routes/export` | 导出完整备份包（JSON） |
| POST | `/api/v1/or/routes/backup` | 在服务器 `backup_dir` 落盘备份 |
| POST | `/api/v1/or/routes/import` | 从备份包导入并重新下发 |

```bash
curl -s http://127.0.0.1:8080/api/v1/or/routes/export > routes-backup.json
curl -s -X POST http://127.0.0.1:8080/api/v1/or/routes/backup
curl -s -X POST http://127.0.0.1:8080/api/v1/or/routes/import \
  -H 'Content-Type: application/json' --data-binary @routes-backup.json
```
