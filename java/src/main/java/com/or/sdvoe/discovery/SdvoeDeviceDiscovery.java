package com.or.sdvoe.discovery;

import com.or.sdvoe.domain.OperatingRoom;
import com.or.sdvoe.domain.SdvoeDevice;

import java.util.List;

/** 从 SDVoE 管理层或本地清单发现设备。 */
public interface SdvoeDeviceDiscovery {

    /** 发现源标识，写入清单快照便于排查。 */
    String sourceName();

    /**
     * 发现指定手术室的 SDVoE 设备。
     * 实现方可返回多室设备，由调用方再过滤；推荐在实现内按 orId 过滤。
     */
    List<SdvoeDevice> discover(OperatingRoom room);
}
