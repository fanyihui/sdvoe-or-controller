package com.or.sdvoe.app;

import com.or.sdvoe.config.OrControllerConfig;
import com.or.sdvoe.discovery.SdvoeDiscoveryFactory;
import com.or.sdvoe.domain.SdvoeDeviceInventory;
import com.or.sdvoe.domain.SdvoeDeviceRole;
import com.or.sdvoe.domain.SurgeryWorkspace;
import com.or.sdvoe.schedule.ScheduleRepository;
import com.or.sdvoe.schedule.ScheduleService;
import com.or.sdvoe.service.SdvoeDeviceInventoryService;
import com.or.sdvoe.workspace.SurgeryWorkspaceService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.NoSuchElementException;
import java.util.concurrent.Executors;

/**
 * OR Desk 统一控制台：排班首页 API + 手术工作空间 API + 静态前端。
 *
 * <pre>
 *   GET  /                         → web UI
 *   GET  /api/v1/or
 *   GET  /api/v1/or/schedule
 *   GET  /api/v1/or/cases/{id}
 *   GET  /api/v1/or/cases/{id}/workspace
 *   GET  /api/v1/or/sdvoe/devices
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
        SurgeryWorkspaceService workspaceService =
                new SurgeryWorkspaceService(config.getOperatingRoom(), scheduleService, deviceService);

        Path webRoot = resolveWebRoot();
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/health", ex -> writeJson(ex, 200, "{\"status\":\"ok\"}"));

        server.createContext("/api/v1/or/schedule", ex -> {
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                writeJson(ex, 405, "{\"error\":\"method not allowed\"}");
                return;
            }
            writeJson(ex, 200, JsonUtil.toPrettyJson(scheduleService.listTodaySchedule()));
        });

        server.createContext("/api/v1/or/cases", ex -> {
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                writeJson(ex, 405, "{\"error\":\"method not allowed\"}");
                return;
            }
            try {
                String path = ex.getRequestURI().getPath();
                // /api/v1/or/cases/{id} or /api/v1/or/cases/{id}/workspace
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
                    SurgeryWorkspace workspace = workspaceService.getWorkspace(caseId);
                    writeJson(ex, 200, JsonUtil.toPrettyJson(workspace.toMap()));
                } else {
                    writeJson(ex, 200, JsonUtil.toPrettyJson(scheduleService.getCase(caseId).toDetailMap()));
                }
            } catch (NoSuchElementException e) {
                writeJson(ex, 404, "{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}");
            } catch (Exception e) {
                writeJson(ex, 500, "{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}");
            }
        });

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
                writeJson(ex, 400, "{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}");
            }
        });

        server.createContext("/api/v1/or", ex -> {
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                writeJson(ex, 405, "{\"error\":\"method not allowed\"}");
                return;
            }
            // Avoid shadowing /api/v1/or/schedule etc. — HttpServer matches longest prefix,
            // so more specific contexts win. This handles exact /api/v1/or.
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
                        + "  GET /api/v1/or/schedule%n"
                        + "  GET /api/v1/or/cases/{id}/workspace%n"
                        + "  OR=%s (%s)%n",
                port,
                config.getOperatingRoom().getName(),
                config.getOperatingRoom().getId());
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
        // SPA fallback for /workspace/...
        if (reqPath.startsWith("/workspace")) {
            reqPath = "/index.html";
        }
        Path resolved = webRoot.resolve("." + reqPath).normalize();
        if (!resolved.startsWith(webRoot) || !Files.isRegularFile(resolved)) {
            // try classpath web/
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

    private static Path resolveWebRoot() {
        Path p1 = Path.of("web").toAbsolutePath().normalize();
        if (Files.isDirectory(p1)) {
            return p1;
        }
        Path p2 = Path.of("../web").toAbsolutePath().normalize();
        if (Files.isDirectory(p2)) {
            return p2;
        }
        // fallback: empty dir under target — classpath serving still works
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
