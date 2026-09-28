package com.or.sdvoe.app;

import com.or.sdvoe.config.OrControllerConfig;
import com.or.sdvoe.discovery.SdvoeDiscoveryFactory;
import com.or.sdvoe.domain.DeviceOnlineStatus;
import com.or.sdvoe.domain.SdvoeDevice;
import com.or.sdvoe.domain.SdvoeDeviceInventory;
import com.or.sdvoe.domain.SdvoeDeviceRole;
import com.or.sdvoe.service.SdvoeDeviceInventoryService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 应用 1：获取本手术室内全部 SDVoE 设备清单。
 *
 * <pre>
 *   mvn -q exec:java -Dexec.mainClass=com.or.sdvoe.app.ListOrSdvoeDevicesApp
 *   mvn -q exec:java -Dexec.mainClass=com.or.sdvoe.app.ListOrSdvoeDevicesApp -Dexec.args="--role ENCODER"
 *   mvn -q exec:java -Dexec.mainClass=com.or.sdvoe.app.ListOrSdvoeDevicesApp -Dexec.args="--json"
 * </pre>
 */
public final class ListOrSdvoeDevicesApp {

    private ListOrSdvoeDevicesApp() {
    }

    public static void main(String[] args) {
        CliOptions options = CliOptions.parse(args);
        OrControllerConfig config = loadConfig(options.configPath);
        SdvoeDeviceInventoryService service = new SdvoeDeviceInventoryService(
                config.getOperatingRoom(),
                SdvoeDiscoveryFactory.create(config));

        SdvoeDeviceInventory inventory = options.role == null
                ? service.listCurrentOrDevices()
                : service.listByRole(options.role);

        if (options.json) {
            System.out.println(toPrettyJson(inventory.toMap()));
            return;
        }

        printTable(inventory);
    }

    private static OrControllerConfig loadConfig(String configPath) {
        if (configPath != null) {
            return OrControllerConfig.load(Path.of(configPath));
        }
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

    private static void printTable(SdvoeDeviceInventory inventory) {
        System.out.printf(
                "手术室: %s (%s)%n",
                inventory.getOperatingRoom().getName(),
                inventory.getOperatingRoom().getId());
        System.out.printf(
                "发现源: %s | 时间: %s%n",
                inventory.getDiscoverySource(),
                inventory.getDiscoveredAt());
        System.out.printf(
                "合计: %d | 在线: %d | 角色分布: %s%n%n",
                inventory.getDevices().size(),
                inventory.onlineCount(),
                inventory.countByRole().entrySet().stream()
                        .map(e -> e.getKey().name() + "=" + e.getValue())
                        .collect(Collectors.joining(", ")));

        System.out.printf(
                "%-14s %-8s %-18s %-15s %-10s %-8s %-16s %s%n",
                "ID", "ROLE", "NAME", "IP", "STATUS", "SIGNAL", "LOCATION", "STREAM");
        System.out.println("-".repeat(120));
        for (SdvoeDevice d : inventory.getDevices()) {
            System.out.printf(
                    "%-14s %-8s %-18s %-15s %-10s %-8s %-16s %s%n",
                    d.getId(),
                    d.getRole().name(),
                    truncate(d.getName(), 18),
                    nullToDash(d.getIpAddress()),
                    d.getStatus().name(),
                    d.isSignalPresent() ? "YES" : "NO",
                    truncate(nullToDash(d.getLocation()), 16),
                    nullToDash(d.getStreamId()));
        }

        long offline = inventory.getDevices().stream()
                .filter(d -> d.getStatus() != DeviceOnlineStatus.ONLINE)
                .count();
        if (offline > 0) {
            System.out.printf("%n注意: 有 %d 台设备非 ONLINE 状态，请检查链路/供电。%n", offline);
        }
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static String truncate(String value, int max) {
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max - 1) + "…";
    }

    /** 轻量 JSON（避免引入额外依赖）。 */
    @SuppressWarnings("unchecked")
    static String toPrettyJson(Object value) {
        StringBuilder sb = new StringBuilder();
        writeJson(sb, value, 0);
        sb.append('\n');
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void writeJson(StringBuilder sb, Object value, int indent) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String s) {
            sb.append('"').append(escape(s)).append('"');
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);
        } else if (value instanceof Map<?, ?> map) {
            sb.append("{\n");
            int i = 0;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                pad(sb, indent + 2);
                sb.append('"').append(escape(String.valueOf(e.getKey()))).append("\": ");
                writeJson(sb, e.getValue(), indent + 2);
                if (++i < map.size()) {
                    sb.append(',');
                }
                sb.append('\n');
            }
            pad(sb, indent);
            sb.append('}');
        } else if (value instanceof Iterable<?> it) {
            sb.append("[\n");
            int i = 0;
            int size = 0;
            for (Object ignored : it) {
                size++;
            }
            for (Object item : it) {
                pad(sb, indent + 2);
                writeJson(sb, item, indent + 2);
                if (++i < size) {
                    sb.append(',');
                }
                sb.append('\n');
            }
            pad(sb, indent);
            sb.append(']');
        } else {
            sb.append('"').append(escape(String.valueOf(value))).append('"');
        }
    }

    private static void pad(StringBuilder sb, int n) {
        sb.append(" ".repeat(Math.max(0, n)));
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static final class CliOptions {
        private String configPath;
        private SdvoeDeviceRole role;
        private boolean json;

        static CliOptions parse(String[] args) {
            CliOptions o = new CliOptions();
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--config", "-c" -> o.configPath = args[++i];
                    case "--role", "-r" -> o.role = SdvoeDeviceRole.valueOf(args[++i].toUpperCase());
                    case "--json", "-j" -> o.json = true;
                    case "--help", "-h" -> {
                        printHelp();
                        System.exit(0);
                    }
                    default -> throw new IllegalArgumentException("Unknown arg: " + args[i]);
                }
            }
            return o;
        }

        private static void printHelp() {
            System.out.println("""
                    ListOrSdvoeDevicesApp — 获取本手术室 SDVoE 设备清单

                    Options:
                      -c, --config <path>   配置文件路径（默认 classpath:or-controller.yaml）
                      -r, --role <ROLE>     过滤角色: ENCODER|DECODER|TRANSCEIVER|SWITCH
                      -j, --json            JSON 输出
                      -h, --help            帮助
                    """);
        }
    }
}
