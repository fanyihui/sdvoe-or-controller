# 多源拼屏（Mosaic）

将多个视频源按布局合成一路画面，再推送到输出目的地（吊臂/墙显/录播等）。

## 布局预设

| ID | 说明 |
|----|------|
| `1x1` | 单画面 |
| `2x1` | 左右分屏 |
| `1x2` | 上下分屏 |
| `2x2` | 四宫格 |
| `1+3` | 左侧大画面 + 右侧三路 |
| `3x3` | 九宫格 |
| `4x4` | 十六宫格 |

## API

- `GET /api/v1/or/mosaic/layouts` — 布局列表
- `GET /api/v1/or/cases/{caseId}/mosaics` — 本台手术拼屏
- `POST /api/v1/or/cases/{caseId}/mosaics` — 创建 `{ layoutId, name?, cells? }`
- `PUT /api/v1/or/cases/{caseId}/mosaics/{id}/cells` — 绑定源 `{ cells:[{index,sourceId}] }`
- `POST /api/v1/or/cases/{caseId}/mosaics/{id}/push` — 推送到目的地 `{ destinationId, confirmed? }`
- `DELETE .../push` — 停止推送
- `DELETE .../mosaics/{id}` — 删除拼屏

工作空间 `GET .../workspace` 额外返回 `activeMosaics`、`mosaicLayouts`；目的地若接收拼屏则 `mosaic=true`。

## 下发流程

1. 各格子源编码器 `set_stream`
2. SDVoE `configure_mosaic`（布局 + 格子 stream → `output_stream_id`）
3. 目的地解码器 `subscribe` 合成流 `mosaic-{mosaicId}`

配置持久化在服务端库表 `workspace_mosaics`，启动时随路由一并恢复。
