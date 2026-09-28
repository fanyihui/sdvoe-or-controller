package com.or.sdvoe.app;

import com.or.sdvoe.config.OrControllerConfig;
import com.or.sdvoe.discovery.SdvoeDiscoveryFactory;
import com.or.sdvoe.domain.SdvoeDeviceInventory;
import com.or.sdvoe.domain.SdvoeDeviceRole;
import com.or.sdvoe.service.SdvoeDeviceInventoryService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;

/**
 * 轻量 HTTP 服务：本手术室 SDVoE 设备清单 API。
 *
 * <pre>
 *   GET /api/v1/or/sdvoe/devices
 *   GET /api/v1/or/sdvoe/devices?role=ENCODER
 *   GET /api/v1/or
 *   GET /health
 * </pre>
 */
public final class OrInventoryHttpServer {

    private OrInventoryHttpServer() {
    }

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        OrControllerConfig config = loadConfig();
        SdvoeDeviceInventoryService service = new SdvoeDeviceInventoryService(
                config.getOperatingRoom(),
                SdvoeDiscoveryFactory.create(config));

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/health", ex -> writeJson(ex, 200, "{\"status\":\"ok\"}"));
        server.createContext("/api/v1/or", ex -> {
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                writeJson(ex, 405, "{\"error\":\"method not allowed\"}");
                return;
            }
            var or = service.currentOr();
            String body = ListOrSdvoeDevicesApp.toPrettyJson(java.util.Map.of(
                    "id", or.getId(),
                    "name", or.getName(),
                    "building", or.getBuilding() == null ? "" : or.getBuilding(),
                    "floor", or.getFloor() == null ? "" : or.getFloor()));
            writeJson(ex, 200, body);
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
                    inventory = service.listByRole(SdvoeDeviceRole.valueOf(role));
                } else {
                    inventory = service.listCurrentOrDevices();
                }
                writeJson(ex, 200, ListOrSdvoeDevicesApp.toPrettyJson(inventory.toMap()));
            } catch (Exception e) {
                writeJson(ex, 400, "{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}");
            }
        });
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.printf(
                "OR SDVoE inventory API listening on http://127.0.0.1:%d%n"
                        + "  GET /api/v1/or/sdvoe/devices%n"
                        + "  OR=%s (%s) discovery=%s%n",
                port,
                config.getOperatingRoom().getName(),
                config.getOperatingRoom().getId(),
                config.getDiscoveryMode());
    }

    private static OrControllerConfig loadConfig() {
        Path local = Path.of("config/or-controller.yaml");
        if (Files.isRegularFile(local)) {
            return OrControllerConfig.load(local);
        }
        return OrControllerConfig.loadClasspath("or-controller.yaml");
    }

    private static void writeJson(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
