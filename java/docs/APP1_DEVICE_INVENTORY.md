# 应用 1：本手术室 SDVoE 设备清单

## 目标

Controller 启动后，能按 **当前手术室（operating_room.id）** 拉取全部 SDVoE 设备（Encoder / Decoder / Transceiver / Switch），供 UI、路由规划与运维使用。

## 用法

### CLI

```bash
cd java
mvn -q compile exec:java
# 或指定主类
mvn -q exec:java -Dexec.mainClass=com.or.sdvoe.app.ListOrSdvoeDevicesApp

# 仅 Encoder
mvn -q exec:java -Dexec.mainClass=com.or.sdvoe.app.ListOrSdvoeDevicesApp -Dexec.args="--role ENCODER"

# JSON
mvn -q exec:java -Dexec.mainClass=com.or.sdvoe.app.ListOrSdvoeDevicesApp -Dexec.args="--json"
```

### HTTP API

```bash
mvn -q exec:java -Dexec.mainClass=com.or.sdvoe.app.OrInventoryHttpServer -Dexec.args="8080"

curl -s http://127.0.0.1:8080/api/v1/or/sdvoe/devices
curl -s 'http://127.0.0.1:8080/api/v1/or/sdvoe/devices?role=DECODER'
```

## 发现模式（`or-controller.yaml`）

| mode | 说明 |
|------|------|
| `inventory` | 读取配置中的交付清单（默认，适合首期上线） |
| `mock` | 内置模拟设备，无硬件联调 |
| `http` | 调用 SDVoE Manager：`GET {base}/api/v1/rooms/{orId}/devices` |

## 核心类

- `SdvoeDeviceInventoryService#listCurrentOrDevices()`
- `SdvoeDiscoveryFactory` / `InventoryFileDiscovery` / `MockSdvoeDiscovery` / `HttpSdvoeManagerDiscovery`
- `ListOrSdvoeDevicesApp` / `OrInventoryHttpServer`
