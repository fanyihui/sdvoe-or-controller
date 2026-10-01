package com.or.sdvoe.web;

import com.or.sdvoe.config.OrControllerConfig;
import com.or.sdvoe.schedule.ScheduleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/or")
public class OrApiController {

    private final OrControllerConfig config;
    private final ScheduleService scheduleService;

    public OrApiController(OrControllerConfig config, ScheduleService scheduleService) {
        this.config = config;
        this.scheduleService = scheduleService;
    }

    @GetMapping
    public Map<String, Object> currentOr() {
        var or = config.getOperatingRoom();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", or.getId());
        body.put("name", or.getName());
        body.put("building", or.getBuilding() == null ? "" : or.getBuilding());
        body.put("floor", or.getFloor() == null ? "" : or.getFloor());
        return body;
    }

    @GetMapping("/schedule")
    public Map<String, Object> schedule() {
        return scheduleService.listTodaySchedule();
    }
}
