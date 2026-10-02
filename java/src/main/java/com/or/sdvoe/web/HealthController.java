package com.or.sdvoe.web;

import com.or.sdvoe.config.DatabaseSettings;
import com.or.sdvoe.config.OrControllerConfig;
import com.or.sdvoe.persistence.RouteRepository;
import com.or.sdvoe.workspace.WorkspaceRoutingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HealthController {

    private final OrControllerConfig config;
    private final DatabaseSettings databaseSettings;
    private final RouteRepository routeRepository;
    private final WorkspaceRoutingService routingService;

    public HealthController(
            OrControllerConfig config,
            DatabaseSettings databaseSettings,
            RouteRepository routeRepository,
            WorkspaceRoutingService routingService) {
        this.config = config;
        this.databaseSettings = databaseSettings;
        this.routeRepository = routeRepository;
        this.routingService = routingService;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ok");
        body.put("framework", "spring-boot");
        body.put("store", routeRepository.storageLabel());
        body.put("remoteServer", databaseSettings.isRemoteServer());
        body.put("backupDir", routeRepository.getBackupDir().toString());
        body.put("persistedRoutes", routeRepository.countByOr(config.getOperatingRoom().getId()));
        body.put("activeRoutes", routingService.listAllRoutes().size());
        body.put("orId", config.getOperatingRoom().getId());
        return body;
    }
}
