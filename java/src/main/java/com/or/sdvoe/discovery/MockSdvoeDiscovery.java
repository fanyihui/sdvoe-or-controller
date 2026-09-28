package com.or.sdvoe.discovery;

import com.or.sdvoe.domain.DeviceOnlineStatus;
import com.or.sdvoe.domain.OperatingRoom;
import com.or.sdvoe.domain.SdvoeDevice;
import com.or.sdvoe.domain.SdvoeDeviceRole;

import java.time.Instant;
import java.util.List;

/**
 * 演示用发现器：模拟本手术室一组典型 SDVoE 设备。
 * 无真实 Manager 时用于联调第一个应用。
 */
public final class MockSdvoeDiscovery implements SdvoeDeviceDiscovery {

    @Override
    public String sourceName() {
        return "mock";
    }

    @Override
    public List<SdvoeDevice> discover(OperatingRoom room) {
        Instant now = Instant.now();
        String orId = room.getId();
        return List.of(
                SdvoeDevice.builder("enc-endo-01", orId)
                        .name("腔镜编码器")
                        .role(SdvoeDeviceRole.ENCODER)
                        .ipAddress("10.10.1.11")
                        .macAddress("00:0A:1B:10:01:11")
                        .model("SDVoE-ENC-4K")
                        .firmware("2.4.1")
                        .location("吊塔-视频输入柜")
                        .status(DeviceOnlineStatus.ONLINE)
                        .signalPresent(true)
                        .streamId("stream-endo-main")
                        .lastSeenAt(now)
                        .tag("source", "endoscope")
                        .build(),
                SdvoeDevice.builder("enc-cam-02", orId)
                        .name("术野相机编码器")
                        .role(SdvoeDeviceRole.ENCODER)
                        .ipAddress("10.10.1.12")
                        .macAddress("00:0A:1B:10:01:12")
                        .model("SDVoE-ENC-4K")
                        .firmware("2.4.1")
                        .location("吊塔-视频输入柜")
                        .status(DeviceOnlineStatus.ONLINE)
                        .signalPresent(true)
                        .streamId("stream-cam")
                        .lastSeenAt(now)
                        .tag("source", "surgical_cam")
                        .build(),
                SdvoeDevice.builder("enc-us-03", orId)
                        .name("超声编码器")
                        .role(SdvoeDeviceRole.ENCODER)
                        .ipAddress("10.10.1.13")
                        .macAddress("00:0A:1B:10:01:13")
                        .model("SDVoE-ENC-HD")
                        .firmware("2.3.0")
                        .location("设备带-超声侧")
                        .status(DeviceOnlineStatus.ONLINE)
                        .signalPresent(false)
                        .streamId("stream-ultrasound")
                        .lastSeenAt(now)
                        .tag("source", "ultrasound")
                        .build(),
                SdvoeDevice.builder("dec-boom-01", orId)
                        .name("主吊臂解码器")
                        .role(SdvoeDeviceRole.DECODER)
                        .ipAddress("10.10.1.21")
                        .macAddress("00:0A:1B:10:02:21")
                        .model("SDVoE-DEC-4K")
                        .firmware("2.4.1")
                        .location("主吊臂后方")
                        .status(DeviceOnlineStatus.ONLINE)
                        .signalPresent(true)
                        .streamId("stream-endo-main")
                        .lastSeenAt(now)
                        .tag("sink", "boom_main")
                        .build(),
                SdvoeDevice.builder("dec-wall-l", orId)
                        .name("左墙显解码器")
                        .role(SdvoeDeviceRole.DECODER)
                        .ipAddress("10.10.1.22")
                        .macAddress("00:0A:1B:10:02:22")
                        .model("SDVoE-DEC-4K")
                        .firmware("2.4.1")
                        .location("左侧墙显")
                        .status(DeviceOnlineStatus.ONLINE)
                        .signalPresent(true)
                        .streamId("stream-cam")
                        .lastSeenAt(now)
                        .tag("sink", "wall_left")
                        .build(),
                SdvoeDevice.builder("dec-wall-r", orId)
                        .name("右墙显解码器")
                        .role(SdvoeDeviceRole.DECODER)
                        .ipAddress("10.10.1.23")
                        .macAddress("00:0A:1B:10:02:23")
                        .model("SDVoE-DEC-4K")
                        .firmware("2.4.0")
                        .location("右侧墙显")
                        .status(DeviceOnlineStatus.DEGRADED)
                        .signalPresent(false)
                        .lastSeenAt(now)
                        .tag("sink", "wall_right")
                        .build(),
                SdvoeDevice.builder("dec-rec-01", orId)
                        .name("录播输入解码器")
                        .role(SdvoeDeviceRole.DECODER)
                        .ipAddress("10.10.1.31")
                        .macAddress("00:0A:1B:10:03:31")
                        .model("SDVoE-DEC-HD")
                        .firmware("2.3.8")
                        .location("控制室机柜")
                        .status(DeviceOnlineStatus.ONLINE)
                        .signalPresent(true)
                        .streamId("stream-endo-main")
                        .lastSeenAt(now)
                        .tag("sink", "recorder")
                        .build(),
                SdvoeDevice.builder("sw-or-core", orId)
                        .name("手术室 SDVoE 接入交换机")
                        .role(SdvoeDeviceRole.SWITCH)
                        .ipAddress("10.10.1.1")
                        .macAddress("00:0A:1B:10:00:01")
                        .model("AV-SW-48P")
                        .firmware("1.12.0")
                        .location("控制室机柜")
                        .status(DeviceOnlineStatus.ONLINE)
                        .lastSeenAt(now)
                        .tag("role", "fabric")
                        .build());
    }
}
