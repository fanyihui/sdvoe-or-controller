package com.or.sdvoe.web;

import com.or.sdvoe.domain.SdvoeDeviceRole;
import com.or.sdvoe.service.SdvoeDeviceInventoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/or/sdvoe/devices")
public class DeviceApiController {

    private final SdvoeDeviceInventoryService deviceInventoryService;

    public DeviceApiController(SdvoeDeviceInventoryService deviceInventoryService) {
        this.deviceInventoryService = deviceInventoryService;
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(required = false) String role) {
        if (role == null || role.isBlank()) {
            return deviceInventoryService.listCurrentOrDevices().toMap();
        }
        return deviceInventoryService.listByRole(SdvoeDeviceRole.valueOf(role.toUpperCase())).toMap();
    }
}
