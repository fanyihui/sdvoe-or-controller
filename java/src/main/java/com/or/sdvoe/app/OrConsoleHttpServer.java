package com.or.sdvoe.app;

import com.or.sdvoe.config.OrControllerConfig;
import com.or.sdvoe.discovery.SdvoeDiscoveryFactory;
import com.or.sdvoe.domain.SdvoeDeviceInventory;
import com.or.sdvoe.domain.SdvoeDeviceRole;
import com.or.sdvoe.domain.SurgeryWorkspace;
import com.or.sdvoe.persistence.RouteRepository;
import com.or.sdvoe.policy.PolicyEngine;
import com.or.sdvoe.schedule.ScheduleRepository;
import com.or.sdvoe.schedule.ScheduleService;
import com.or.sdvoe.service.FabricOrchestrator;
import com.or.sdvoe.service.SdvoeDeviceInventoryService;
import com.or.sdvoe.workspace.ActiveRoute;
import com.or.sdvoe.workspace.RouteConflictException;
import com.or.sdvoe.workspace.SurgeryWorkspaceService;
import com.or.sdvoe.workspace.WorkspaceRoutingService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.Executors;

/**
 * OR Desk 统一控制台：排班、工作空间、拖拽路由 API + 静态前端。
 *
 * <pre>
 *   GET    /api/v1/or/schedule
 *   GET    /api/v1/or/cases/{id}/workspace
 *   POST   /api/v1/or/cases/{id}/routes
 *   DELETE /api/v1/or/cases/{id}/routes/{destinationId}
 * </pre>
 */
public final class OrConsoleHttpServer {

    private OrConsoleHttpServer() {
    }

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        OrControllerConfig config = loadConfig();
        SdvoeDeviceInventoryService deviceService = new SdvoeDeviceInventoryService(
                config.getOperatingRoom(),
                SdvoeDiscoveryFactory.create(config));
        ScheduleRepository scheduleRepo = loadSchedule();
        ScheduleService scheduleService = new ScheduleService(config.getOperatingRoom(), scheduleRepo);
        PolicyEngine policyEngine = loadPolicyEngine();
        FabricOrchestrator orchestrator = WorkspaceRoutingService.createDefaultOrchestrator();

        Path dbPath = resolveDatabasePath(config.getDatabasePath());
        RouteRepository routeRepository = new RouteRepository(dbPath);
        Runtime.getRuntime().addShutdownHook(new Thread(routeRepository::close));

        WorkspaceRoutingService routingService = new WorkspaceRoutingService(
                config.getOperatingRoom().getId(),
                scheduleService,
                deviceService,
                policyEngine,
                orchestrator,
                routeRepository);
        SurgeryWorkspaceService workspaceService = new SurgeryWorkspaceService(
                config.getOperatingRoom(), scheduleService, deviceService, routingService);

        int[] restoreStats = new int[] {0, 0};
        if (config.isRestoreRoutesOnStartup()) {
            restoreStats = routingService.restorePersistedRoutes();
        }

        Path webRoot = resolveWebRoot();
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        final int restoredOk = restoreStats[0];
        final int restoredFail = restoreStats[1];
        server.createContext("/health", ex -> {
            Map<String, Object> health = new LinkedHashMap<>();
            health.put("status", "ok");
            health.put("database", dbPath.toString());
            health.put("persistedRoutes", routeRepository.countByOr(config.getOperatingRoom().getId()));
            health.put("restoredOk", restoredOk);
            health.put("restoredFailed", restoredFail);
            writeJson(ex, 200, JsonUtil.toPrettyJson(health));
        });

        server.createContext("/api/v1/or/routes", ex -> {
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                writeJson(ex, 405, "{\"error\":\"method not allowed\"}");
                return;
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("orId", config.getOperatingRoom().getId());
            body.put("database", dbPath.toString());
            body.put("routes", routingService.listAllRoutes().stream().map(ActiveRoute::toMap).toList());
            writeJson(ex, 200, JsonUtil.toPrettyJson(body));
        });
        server.createContext("/api/v1/or/schedule", ex -> {
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                writeJson(ex, 405, "{\"error\":\"method not allowed\"}");
                return;
            }
            writeJson(ex, 200, JsonUtil.toPrettyJson(scheduleService.listTodaySchedule()));
        });

        server.createContext("/api/v1/or/cases", ex -> handleCases(
                ex, scheduleService, workspaceService, routingService));

        server.createContext("/api/v1/or/sdvoe/devices", ex -> {
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                writeJson(ex, 405, "{\"error\":\"method not allowed\"}");
                return;
            }
            try {
                String query = ex.getRequestURI().getQuery();
                SdvoeDeviceInventory inventory;
                if (query != null && query.contains("role=")) {
                    String role = query.replaceAll(".*role=([^&]+).*", "$1").toUpperCase();
                    inventory = deviceService.listByRole(SdvoeDeviceRole.valueOf(role));
                } else {
                    inventory = deviceService.listCurrentOrDevices();
                }
                writeJson(ex, 200, JsonUtil.toPrettyJson(inventory.toMap()));
            } catch (Exception e) {
                writeJson(ex, 400, errorJson(e.getMessage()));
            }
        });

        server.createContext("/api/v1/or", ex -> {
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                writeJson(ex, 405, "{\"error\":\"method not allowed\"}");
                return;
            }
            if (!"/api/v1/or".equals(ex.getRequestURI().getPath())) {
                writeJson(ex, 404, "{\"error\":\"not found\"}");
                return;
            }
            var or = config.getOperatingRoom();
            writeJson(ex, 200, JsonUtil.toPrettyJson(java.util.Map.of(
                    "id", or.getId(),
                    "name", or.getName(),
                    "building", or.getBuilding() == null ? "" : or.getBuilding(),
                    "floor", or.getFloor() == null ? "" : or.getFloor())));
        });

        server.createContext("/", ex -> serveStatic(ex, webRoot));

        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.printf(
                "OR Desk console listening on http://127.0.0.1:%d%n"
                        + "  UI  /%n"
                        + "  GET  /api/v1/or/schedule%n"
                        + "  GET  /api/v1/or/cases/{id}/workspace%n"
                        + "  POST /api/v1/or/cases/{id}/routes%n"
                        + "  GET  /api/v1/or/routes%n"
                        + "  DB  %s%n"
                        + "  Restore ok=%d failed=%d%n"
                        + "  OR=%s (%s)%n",
                port,
                dbPath,
                restoredOk,
                restoredFail,
                config.getOperatingRoom().getName(),
                config.getOperatingRoom().getId());
    }

    private static Path resolveDatabasePath(String configured) {
        String override = System.getenv("OR_DESK_DB");
        Path path = Path.of(override != null && !override.isBlank() ? override : configured);
        return path.toAbsolutePath().normalize();
    }

    private static void handleCases(
            HttpExchange ex,
            ScheduleService scheduleService,
            SurgeryWorkspaceService workspaceService,
            WorkspaceRoutingService routingService) throws IOException {
        String method = ex.getRequestMethod();
        if ("OPTIONS".equalsIgnoreCase(method)) {
            ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            ex.getResponseHeaders().add("Access-Control-Allow-Methods", "GET,POST,DELETE,OPTIONS");
            ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
            ex.sendResponseHeaders(204, -1);
            ex.close();
            return;
        }

        try {
            String path = ex.getRequestURI().getPath();
            String rest = path.substring("/api/v1/or/cases".length());
            if (rest.startsWith("/")) {
                rest = rest.substring(1);
            }
            if (rest.isBlank()) {
                writeJson(ex, 400, "{\"error\":\"case id required\"}");
                return;
            }
            String[] parts = rest.split("/");
            String caseId = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);

            if (parts.length >= 2 && "workspace".equals(parts[1])) {
                if (!"GET".equalsIgnoreCase(method)) {
                    writeJson(ex, 405, "{\"error\":\"method not allowed\"}");
                    return;
                }
                SurgeryWorkspace workspace = workspaceService.getWorkspace(caseId);
                writeJson(ex, 200, JsonUtil.toPrettyJson(workspace.toMap()));
                return;
            }

            if (parts.length >= 2 && "routes".equals(parts[1])) {
                if ("GET".equalsIgnoreCase(method)) {
                    writeJson(ex, 200, JsonUtil.toPrettyJson(Map.of(
                            "caseId", caseId,
                            "routes", routingService.listRoutes(caseId).stream()
                                    .map(ActiveRoute::toMap)
                                    .toList())));
                    return;
                }
                if ("POST".equalsIgnoreCase(method)) {
                    Map<String, Object> body = readJsonBody(ex);
                    String sourceId = stringField(body, "sourceId");
                    String destinationId = stringField(body, "destinationId");
                    String operator = stringField(body, "operator");
                    boolean confirmed = Boolean.TRUE.equals(body.get("confirmed"));
                    if (sourceId == null || destinationId == null) {
                        writeJson(ex, 400, "{\"error\":\"sourceId and destinationId required\"}");
                        return;
                    }
                    try {
                        ActiveRoute route = routingService.route(
                                caseId, sourceId, destinationId, operator, confirmed);
                        Map<String, Object> resp = new LinkedHashMap<>();
                        resp.put("ok", true);
                        resp.put("route", route.toMap());
                        resp.put("workspace", workspaceService.getWorkspace(caseId).toMap());
                        writeJson(ex, 200, JsonUtil.toPrettyJson(resp));
                    } catch (RouteConflictException conflict) {
                        Map<String, Object> resp = new LinkedHashMap<>();
                        resp.put("ok", false);
                        resp.put("conflict", true);
                        resp.put("error", conflict.getMessage());
                        resp.put("existing", conflict.getExisting().toMap());
                        writeJson(ex, 409, JsonUtil.toPrettyJson(resp));
                    }
                    return;
                }
                if ("DELETE".equalsIgnoreCase(method)) {
                    if (parts.length < 3) {
                        writeJson(ex, 400, "{\"error\":\"destinationId required\"}");
                        return;
                    }
                    String destinationId = URLDecoder.decode(parts[2], StandardCharsets.UTF_8);
                    routingService.clearRoute(caseId, destinationId);
                    Map<String, Object> resp = new LinkedHashMap<>();
                    resp.put("ok", true);
                    resp.put("workspace", workspaceService.getWorkspace(caseId).toMap());
                    writeJson(ex, 200, JsonUtil.toPrettyJson(resp));
                    return;
                }
                writeJson(ex, 405, "{\"error\":\"method not allowed\"}");
                return;
            }

            if ("GET".equalsIgnoreCase(method)) {
                writeJson(ex, 200, JsonUtil.toPrettyJson(scheduleService.getCase(caseId).toDetailMap()));
                return;
            }
            writeJson(ex, 405, "{\"error\":\"method not allowed\"}");
        } catch (NoSuchElementException e) {
            writeJson(ex, 404, errorJson(e.getMessage()));
        } catch (IllegalStateException e) {
            writeJson(ex, 422, errorJson(e.getMessage()));
        } catch (Exception e) {
            writeJson(ex, 500, errorJson(e.getMessage()));
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readJsonBody(HttpExchange ex) throws IOException {
        String raw = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (raw.isEmpty()) {
            return Map.of();
        }
        Object loaded = new Yaml().load(raw);
        if (loaded instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private static String stringField(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? null : v.toString();
    }

    private static String errorJson(String message) {
        String safe = message == null ? "error" : message.replace("\"", "'");
        return "{\"error\":\"" + safe + "\"}";
    }

    private static void serveStatic(HttpExchange ex, Path webRoot) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())
                && !"HEAD".equalsIgnoreCase(ex.getRequestMethod())) {
            writeJson(ex, 405, "{\"error\":\"method not allowed\"}");
            return;
        }
        String reqPath = ex.getRequestURI().getPath();
        if (reqPath == null || reqPath.isBlank() || "/".equals(reqPath)) {
            reqPath = "/index.html";
        }
        if (reqPath.startsWith("/workspace")) {
            reqPath = "/index.html";
        }
        Path resolved = webRoot.resolve("." + reqPath).normalize();
        if (!resolved.startsWith(webRoot) || !Files.isRegularFile(resolved)) {
            String classpathPath = "web" + (reqPath.startsWith("/") ? reqPath : "/" + reqPath);
            InputStream in = OrConsoleHttpServer.class.getClassLoader().getResourceAsStream(classpathPath);
            if (in == null && reqPath.startsWith("/workspace")) {
                in = OrConsoleHttpServer.class.getClassLoader().getResourceAsStream("web/index.html");
            }
            if (in == null) {
                writeJson(ex, 404, "{\"error\":\"not found\"}");
                return;
            }
            byte[] bytes = in.readAllBytes();
            ex.getResponseHeaders().add("Content-Type", contentType(reqPath));
            ex.sendResponseHeaders(200, bytes.length);
            if (!"HEAD".equalsIgnoreCase(ex.getRequestMethod())) {
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(bytes);
                }
            } else {
                ex.close();
            }
            return;
        }
        byte[] bytes = Files.readAllBytes(resolved);
        ex.getResponseHeaders().add("Content-Type", contentType(reqPath));
        // Avoid stale UI during development
        ex.getResponseHeaders().add("Cache-Control", "no-store");
        ex.sendResponseHeaders(200, bytes.length);
        if (!"HEAD".equalsIgnoreCase(ex.getRequestMethod())) {
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
        } else {
            ex.close();
        }
    }

    private static String contentType(String path) {
        if (path.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (path.endsWith(".js")) {
            return "application/javascript; charset=utf-8";
        }
        if (path.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (path.endsWith(".png")) {
            return "image/png";
        }
        if (path.endsWith(".json")) {
            return "application/json; charset=utf-8";
        }
        return "text/html; charset=utf-8";
    }

    private static OrControllerConfig loadConfig() {
        Path local = Path.of("config/or-controller.yaml");
        if (Files.isRegularFile(local)) {
            return OrControllerConfig.load(local);
        }
        Path sibling = Path.of("../config/or-controller.yaml");
        if (Files.isRegularFile(sibling)) {
            return OrControllerConfig.load(sibling);
        }
        return OrControllerConfig.loadClasspath("or-controller.yaml");
    }

    private static ScheduleRepository loadSchedule() {
        Path local = Path.of("config/schedule.yaml");
        if (Files.isRegularFile(local)) {
            return ScheduleRepository.load(local);
        }
        Path sibling = Path.of("../config/schedule.yaml");
        if (Files.isRegularFile(sibling)) {
            return ScheduleRepository.load(sibling);
        }
        return ScheduleRepository.loadClasspath("schedule.yaml");
    }

    private static PolicyEngine loadPolicyEngine() {
        Path local = Path.of("config/routing-policies.yaml");
        if (Files.isRegularFile(local)) {
            return new PolicyEngine(local);
        }
        Path sibling = Path.of("../config/routing-policies.yaml");
        if (Files.isRegularFile(sibling)) {
            return new PolicyEngine(sibling);
        }
        InputStream in = OrConsoleHttpServer.class.getClassLoader()
                .getResourceAsStream("routing-policies.yaml");
        if (in == null) {
            throw new IllegalStateException("routing-policies.yaml not found");
        }
        return new PolicyEngine(in);
    }

    private static Path resolveWebRoot() {
        Path p1 = Path.of("web").toAbsolutePath().normalize();
        if (Files.isDirectory(p1)) {
            return p1;
        }
        Path p2 = Path.of("../web").toAbsolutePath().normalize();
        if (Files.isDirectory(p2)) {
            return p2;
        }
        return p1;
    }

    private static void writeJson(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
