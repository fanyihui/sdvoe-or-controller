package com.or.sdvoe.web;

import com.or.sdvoe.schedule.ScheduleService;
import com.or.sdvoe.workspace.ActiveRoute;
import com.or.sdvoe.workspace.SurgeryWorkspaceService;
import com.or.sdvoe.workspace.WorkspaceRoutingService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/or/cases")
public class CaseApiController {

    private final ScheduleService scheduleService;
    private final SurgeryWorkspaceService workspaceService;
    private final WorkspaceRoutingService routingService;

    public CaseApiController(
            ScheduleService scheduleService,
            SurgeryWorkspaceService workspaceService,
            WorkspaceRoutingService routingService) {
        this.scheduleService = scheduleService;
        this.workspaceService = workspaceService;
        this.routingService = routingService;
    }

    @GetMapping("/{caseId}")
    public Map<String, Object> caseDetail(@PathVariable String caseId) {
        return scheduleService.getCase(caseId).toDetailMap();
    }

    @GetMapping("/{caseId}/workspace")
    public Map<String, Object> workspace(@PathVariable String caseId) {
        return workspaceService.getWorkspace(caseId).toMap();
    }

    @GetMapping("/{caseId}/routes")
    public Map<String, Object> listRoutes(@PathVariable String caseId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("caseId", caseId);
        body.put("routes", routingService.listRoutes(caseId).stream().map(ActiveRoute::toMap).toList());
        return body;
    }

    @PostMapping("/{caseId}/routes")
    public Map<String, Object> createRoute(
            @PathVariable String caseId, @RequestBody RouteCreateRequest request) {
        if (request.sourceId() == null || request.sourceId().isBlank()
                || request.destinationId() == null || request.destinationId().isBlank()) {
            throw new IllegalArgumentException("sourceId and destinationId required");
        }
        ActiveRoute route = routingService.route(
                caseId,
                request.sourceId(),
                request.destinationId(),
                request.operator(),
                Boolean.TRUE.equals(request.confirmed()));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("route", route.toMap());
        body.put("workspace", workspaceService.getWorkspace(caseId).toMap());
        return body;
    }

    @DeleteMapping("/{caseId}/routes/{destinationId}")
    public Map<String, Object> clearRoute(
            @PathVariable String caseId, @PathVariable String destinationId) {
        routingService.clearRoute(caseId, destinationId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("workspace", workspaceService.getWorkspace(caseId).toMap());
        return body;
    }

    public record RouteCreateRequest(
            @NotBlank String sourceId,
            @NotBlank String destinationId,
            String operator,
            Boolean confirmed) {
    }
}
