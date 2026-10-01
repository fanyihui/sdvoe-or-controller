# 路由持久化与启动恢复

## 行为

1. 工作空间拖拽创建/覆盖路由时，写入 SQLite `workspace_routes`
2. 清除路由时从库中删除
3. 进程启动时读取本手术室全部路由，重新下发到 SDVoE Adapter
4. 若设备暂时离线导致下发失败，库中记录保留，UI 仍展示意图路由，下次启动重试

## 配置

`or-controller.yaml`：

```yaml
database:
  path: data/or-desk.db
  restore_routes_on_startup: true
```

环境变量：

```bash
export OR_DESK_DB=/var/lib/or-desk/or-desk.db
```

## 表结构

`workspace_routes`：按 `(or_id, case_id, destination_id)` 唯一，保存 source/destination/stream/fabrics/operator/时间戳。

## 验证

```bash
# 建路由后
curl -s http://127.0.0.1:8080/health
# persistedRoutes > 0

# 重启服务后再查
curl -s http://127.0.0.1:8080/health
# restoredOk == 先前条数
curl -s http://127.0.0.1:8080/api/v1/or/routes
```
