package com.or.sdvoe.web;

import com.or.sdvoe.config.DatabaseSettings;
import com.or.sdvoe.config.OrControllerConfig;
import com.or.sdvoe.persistence.RouteRepository;
import com.or.sdvoe.workspace.ActiveRoute;
import com.or.sdvoe.workspace.WorkspaceRoutingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/or/routes")
public class RouteStoreApiController {

    private final OrControllerConfig config;
    private final DatabaseSettings databaseSettings;
    private final RouteRepository routeRepository;
    private final WorkspaceRoutingService routingService;

    public RouteStoreApiController(
            OrControllerConfig config,
            DatabaseSettings databaseSettings,
            RouteRepository routeRepository,
            WorkspaceRoutingService routingService) {
        this.config = config;
        this.databaseSettings = databaseSettings;
        this.routeRepository = routeRepository;
        this.routingService = routingService;
    }

    @GetMapping
    public Map<String, Object> list() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orId", config.getOperatingRoom().getId());
        body.put("store", routeRepository.storageLabel());
        body.put("remoteServer", databaseSettings.isRemoteServer());
        body.put("backupDir", routeRepository.getBackupDir().toString());
        body.put("routes", routingService.listAllRoutes().stream().map(ActiveRoute::toMap).toList());
        return body;
    }

    @GetMapping("/export")
    public Map<String, Object> export() {
        return routeRepository.exportBundle(config.getOperatingRoom().getId());
    }

    @PostMapping("/backup")
    public Map<String, Object> backup() {
        Path backup = routeRepository.createBackup(config.getOperatingRoom().getId());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("backup", backup.toString());
        body.put("backupDir", routeRepository.getBackupDir().toString());
        return body;
    }

    @PostMapping("/import")
    public Map<String, Object> importRoutes(@RequestBody Map<String, Object> bundle) {
        String orId = config.getOperatingRoom().getId();
        int imported = routeRepository.importBundle(orId, bundle);
        int[] stats = routingService.restorePersistedRoutes();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("imported", imported);
        body.put("restoredOk", stats[0]);
        body.put("restoredFailed", stats[1]);
        body.put("routes", routingService.listAllRoutes().stream().map(ActiveRoute::toMap).toList());
        return body;
    }
}
