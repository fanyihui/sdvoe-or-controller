package com.or.sdvoe.persistence;

import com.or.sdvoe.config.DatabaseSettings;
import com.or.sdvoe.workspace.ActiveRoute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * 服务端路由仓储：默认本地 SQLite（WAL + 同步落盘 + 自动备份），
 * 也可对接远程 PostgreSQL，避免单机文件丢失。
 */
public final class RouteRepository implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(RouteRepository.class);
    private static final DateTimeFormatter BACKUP_TS =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private final DatabaseSettings settings;
    private final Path sqlitePath;
    private final Path backupDir;
    private final Connection connection;
    private final boolean postgresDialect;

    public RouteRepository(DatabaseSettings settings) {
        this.settings = Objects.requireNonNull(settings);
        this.sqlitePath = settings.getType() == DatabaseSettings.Type.SQLITE
                ? Path.of(settings.getPath()).toAbsolutePath().normalize()
                : null;
        this.backupDir = Path.of(settings.getBackupDir()).toAbsolutePath().normalize();
        this.postgresDialect = settings.getType() == DatabaseSettings.Type.POSTGRES
                || (settings.getJdbcUrl() != null
                        && settings.getJdbcUrl().toLowerCase().contains("postgres"));
        try {
            this.connection = openConnection(settings, sqlitePath);
            this.connection.setAutoCommit(true);
            hardenSqliteIfNeeded();
            initSchema();
            Files.createDirectories(backupDir);
            log.info("Route server store ready: {} (remote={})", settings.describe(), settings.isRemoteServer());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to open route server store: " + settings.describe(), e);
        }
    }

    /** 兼容旧构造：仅 SQLite 文件路径。 */
    public RouteRepository(Path dbPath) {
        this(new DatabaseSettings(
                DatabaseSettings.Type.SQLITE,
                dbPath.toString(),
                null,
                null,
                null,
                true,
                true,
                dbPath.getParent() == null
                        ? "data/backups"
                        : dbPath.getParent().resolve("backups").toString()));
    }

    private static Connection openConnection(DatabaseSettings settings, Path sqlitePath)
            throws Exception {
        return switch (settings.getType()) {
            case SQLITE -> {
                Files.createDirectories(sqlitePath.getParent() == null ? Path.of(".") : sqlitePath.getParent());
                Class.forName("org.sqlite.JDBC");
                yield DriverManager.getConnection("jdbc:sqlite:" + sqlitePath);
            }
            case POSTGRES -> {
                Class.forName("org.postgresql.Driver");
                String url = settings.getJdbcUrl();
                if (url == null || url.isBlank()) {
                    throw new IllegalStateException("database.jdbc_url is required for type=postgres");
                }
                Properties props = new Properties();
                if (settings.getUsername() != null) {
                    props.setProperty("user", settings.getUsername());
                }
                if (settings.getPassword() != null) {
                    props.setProperty("password", settings.getPassword());
                }
                yield DriverManager.getConnection(url, props);
            }
            case JDBC -> {
                String url = settings.getJdbcUrl();
                if (url == null || url.isBlank()) {
                    throw new IllegalStateException("database.jdbc_url is required for type=jdbc");
                }
                Properties props = new Properties();
                if (settings.getUsername() != null) {
                    props.setProperty("user", settings.getUsername());
                }
                if (settings.getPassword() != null) {
                    props.setProperty("password", settings.getPassword());
                }
                yield DriverManager.getConnection(url, props);
            }
        };
    }

    private void hardenSqliteIfNeeded() throws SQLException {
        if (settings.getType() != DatabaseSettings.Type.SQLITE) {
            return;
        }
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA synchronous=FULL");
            st.execute("PRAGMA busy_timeout=5000");
            st.execute("PRAGMA foreign_keys=ON");
        }
    }

    private void initSchema() throws SQLException {
        String idColumn = postgresDialect
                ? "id BIGSERIAL PRIMARY KEY"
                : "id INTEGER PRIMARY KEY AUTOINCREMENT";
        try (Statement st = connection.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS workspace_routes (
                      %s,
                      route_id TEXT NOT NULL,
                      or_id TEXT NOT NULL,
                      case_id TEXT NOT NULL,
                      source_id TEXT NOT NULL,
                      source_name TEXT,
                      destination_id TEXT NOT NULL,
                      destination_name TEXT,
                      stream_id TEXT,
                      fabrics TEXT,
                      policy_reason TEXT,
                      operator TEXT,
                      created_at TEXT NOT NULL,
                      updated_at TEXT NOT NULL,
                      UNIQUE(or_id, case_id, destination_id)
                    )
                    """.formatted(idColumn));
            st.execute("""
                    CREATE INDEX IF NOT EXISTS idx_workspace_routes_or
                    ON workspace_routes(or_id)
                    """);
        }
    }

    public void upsert(String orId, ActiveRoute route) {
        String excluded = postgresDialect ? "EXCLUDED" : "excluded";
        String sql = """
                INSERT INTO workspace_routes (
                  route_id, or_id, case_id, source_id, source_name,
                  destination_id, destination_name, stream_id, fabrics,
                  policy_reason, operator, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (or_id, case_id, destination_id) DO UPDATE SET
                  route_id=%1$s.route_id,
                  source_id=%1$s.source_id,
                  source_name=%1$s.source_name,
                  destination_name=%1$s.destination_name,
                  stream_id=%1$s.stream_id,
                  fabrics=%1$s.fabrics,
                  policy_reason=%1$s.policy_reason,
                  operator=%1$s.operator,
                  updated_at=%1$s.updated_at
                """.formatted(excluded);
        String now = Instant.now().toString();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, route.getRouteId());
            ps.setString(2, orId);
            ps.setString(3, route.getCaseId());
            ps.setString(4, route.getSourceId());
            ps.setString(5, route.getSourceName());
            ps.setString(6, route.getDestinationId());
            ps.setString(7, route.getDestinationName());
            ps.setString(8, route.getStreamId());
            ps.setString(9, String.join(",", route.getFabrics()));
            ps.setString(10, route.getPolicyReason());
            ps.setString(11, route.getOperator());
            ps.setString(12, route.getCreatedAt().toString());
            ps.setString(13, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to persist route " + route.getRouteId(), e);
        }
        afterMutation(orId);
    }

    public void delete(String orId, String caseId, String destinationId) {
        String sql = "DELETE FROM workspace_routes WHERE or_id=? AND case_id=? AND destination_id=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, orId);
            ps.setString(2, caseId);
            ps.setString(3, destinationId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Failed to delete route " + caseId + "/" + destinationId, e);
        }
        afterMutation(orId);
    }

    public List<ActiveRoute> findByOr(String orId) {
        String sql = """
                SELECT route_id, case_id, source_id, source_name, destination_id, destination_name,
                       stream_id, fabrics, policy_reason, operator, created_at
                FROM workspace_routes
                WHERE or_id=?
                ORDER BY created_at ASC
                """;
        List<ActiveRoute> routes = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, orId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    routes.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load routes for OR " + orId, e);
        }
        return routes;
    }

    public long countByOr(String orId) {
        String sql = "SELECT COUNT(*) FROM workspace_routes WHERE or_id=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, orId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to count routes for OR " + orId, e);
        }
    }

    public Map<String, Object> exportBundle(String orId) {
        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("version", 1);
        bundle.put("orId", orId);
        bundle.put("exportedAt", Instant.now().toString());
        bundle.put("store", settings.describe());
        bundle.put("remoteServer", settings.isRemoteServer());
        bundle.put("routes", findByOr(orId).stream().map(ActiveRoute::toMap).toList());
        return bundle;
    }

    /** 从导出包恢复到服务器存储（覆盖同 destination 路由）。 */
    @SuppressWarnings("unchecked")
    public int importBundle(String orId, Map<String, Object> bundle) {
        Object rawRoutes = bundle.get("routes");
        if (!(rawRoutes instanceof List<?> list)) {
            return 0;
        }
        int count = 0;
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> raw)) {
                continue;
            }
            Map<String, Object> m = (Map<String, Object>) raw;
            String caseId = str(m.get("caseId"));
            String sourceId = str(m.get("sourceId"));
            String destinationId = str(m.get("destinationId"));
            if (caseId == null || sourceId == null || destinationId == null) {
                continue;
            }
            List<String> fabrics = List.of();
            if (m.get("fabrics") instanceof List<?> fl) {
                fabrics = fl.stream().map(String::valueOf).toList();
            }
            Instant createdAt = Instant.now();
            try {
                if (m.get("createdAt") != null) {
                    createdAt = Instant.parse(m.get("createdAt").toString());
                }
            } catch (Exception ignored) {
                // keep now
            }
            ActiveRoute route = new ActiveRoute(
                    str(m.getOrDefault("routeId", "imported-" + destinationId)),
                    caseId,
                    sourceId,
                    str(m.get("sourceName")),
                    destinationId,
                    str(m.get("destinationName")),
                    str(m.get("streamId")),
                    fabrics,
                    str(m.get("policyReason")),
                    str(m.getOrDefault("operator", "import")),
                    createdAt);
            upsert(orId, route);
            count++;
        }
        return count;
    }

    public Path createBackup(String orId) {
        try {
            Files.createDirectories(backupDir);
            String ts = BACKUP_TS.format(Instant.now());
            Path jsonBackup = backupDir.resolve("routes-" + orId + "-" + ts + ".json");
            Map<String, Object> bundle = exportBundle(orId);
            Files.writeString(jsonBackup, toCompactJson(bundle), StandardCharsets.UTF_8);

            if (settings.getType() == DatabaseSettings.Type.SQLITE && sqlitePath != null) {
                checkpointSqlite();
                Path dbBackup = backupDir.resolve("or-desk-" + orId + "-" + ts + ".db");
                Files.copy(sqlitePath, dbBackup, StandardCopyOption.REPLACE_EXISTING);
                // rolling latest pointers
                Files.copy(jsonBackup, backupDir.resolve("routes-latest.json"), StandardCopyOption.REPLACE_EXISTING);
                Files.copy(dbBackup, backupDir.resolve("or-desk-latest.db"), StandardCopyOption.REPLACE_EXISTING);
                log.info("Server route backup written: {} and {}", jsonBackup, dbBackup);
            } else {
                Files.copy(jsonBackup, backupDir.resolve("routes-latest.json"), StandardCopyOption.REPLACE_EXISTING);
                log.info("Server route backup written: {}", jsonBackup);
            }
            return jsonBackup;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to backup routes", e);
        }
    }

    public Path getSqlitePath() {
        return sqlitePath;
    }

    public Path getBackupDir() {
        return backupDir;
    }

    public DatabaseSettings getSettings() {
        return settings;
    }

    public String storageLabel() {
        return settings.describe();
    }

    private void afterMutation(String orId) {
        if (settings.getType() == DatabaseSettings.Type.SQLITE) {
            checkpointSqlite();
        }
        if (settings.isAutoBackup()) {
            try {
                // keep a durable latest JSON snapshot on every change
                Files.createDirectories(backupDir);
                Path latest = backupDir.resolve("routes-latest.json");
                Files.writeString(latest, toCompactJson(exportBundle(orId)), StandardCharsets.UTF_8);
            } catch (Exception e) {
                log.warn("Auto backup snapshot failed: {}", e.getMessage());
            }
        }
    }

    private void checkpointSqlite() {
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA wal_checkpoint(PASSIVE)");
        } catch (SQLException e) {
            log.warn("SQLite checkpoint failed: {}", e.getMessage());
        }
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }

    private static ActiveRoute mapRow(ResultSet rs) throws SQLException {
        String fabricsRaw = rs.getString("fabrics");
        List<String> fabrics = fabricsRaw == null || fabricsRaw.isBlank()
                ? List.of()
                : Arrays.stream(fabricsRaw.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());
        Instant createdAt;
        try {
            createdAt = Instant.parse(rs.getString("created_at"));
        } catch (Exception e) {
            createdAt = Instant.now();
        }
        return new ActiveRoute(
                rs.getString("route_id"),
                rs.getString("case_id"),
                rs.getString("source_id"),
                rs.getString("source_name"),
                rs.getString("destination_id"),
                rs.getString("destination_name"),
                rs.getString("stream_id"),
                fabrics,
                rs.getString("policy_reason"),
                rs.getString("operator"),
                createdAt);
    }

    /** Minimal JSON for backup files (no pretty indent dependency). */
    @SuppressWarnings("unchecked")
    static String toCompactJson(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String s) {
            return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder("{");
            int i = 0;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (i++ > 0) {
                    sb.append(',');
                }
                sb.append(toCompactJson(String.valueOf(e.getKey()))).append(':').append(toCompactJson(e.getValue()));
            }
            return sb.append('}').toString();
        }
        if (value instanceof Iterable<?> it) {
            StringBuilder sb = new StringBuilder("[");
            int i = 0;
            for (Object item : it) {
                if (i++ > 0) {
                    sb.append(',');
                }
                sb.append(toCompactJson(item));
            }
            return sb.append(']').toString();
        }
        return toCompactJson(String.valueOf(value));
    }

    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                if (settings.getType() == DatabaseSettings.Type.SQLITE) {
                    checkpointSqlite();
                }
                connection.close();
            }
        } catch (SQLException e) {
            log.warn("Failed to close route database: {}", e.getMessage());
        }
    }
}
