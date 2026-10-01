package com.or.sdvoe.persistence;

import com.or.sdvoe.config.DatabaseSettings;
import com.or.sdvoe.workspace.ActiveMosaic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/** 拼屏配置持久化（与路由同库）。 */
public final class MosaicRepository implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(MosaicRepository.class);

    private final DatabaseSettings settings;
    private final Connection connection;
    private final boolean postgresDialect;

    public MosaicRepository(DatabaseSettings settings) {
        this.settings = Objects.requireNonNull(settings);
        this.postgresDialect = settings.getType() == DatabaseSettings.Type.POSTGRES
                || (settings.getJdbcUrl() != null
                        && settings.getJdbcUrl().toLowerCase().contains("postgres"));
        try {
            this.connection = openConnection(settings);
            this.connection.setAutoCommit(true);
            if (settings.getType() == DatabaseSettings.Type.SQLITE) {
                try (Statement st = connection.createStatement()) {
                    st.execute("PRAGMA journal_mode=WAL");
                    st.execute("PRAGMA synchronous=FULL");
                    st.execute("PRAGMA busy_timeout=5000");
                }
            }
            initSchema();
            log.info("Mosaic store ready: {}", settings.describe());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to open mosaic store: " + settings.describe(), e);
        }
    }

    private static Connection openConnection(DatabaseSettings settings) throws Exception {
        return switch (settings.getType()) {
            case SQLITE -> {
                Path path = Path.of(settings.getPath()).toAbsolutePath().normalize();
                Files.createDirectories(path.getParent() == null ? Path.of(".") : path.getParent());
                Class.forName("org.sqlite.JDBC");
                yield DriverManager.getConnection("jdbc:sqlite:" + path);
            }
            case POSTGRES, JDBC -> {
                String url = settings.getJdbcUrl();
                if (url == null || url.isBlank()) {
                    throw new IllegalStateException("database.jdbc_url is required");
                }
                if (settings.getType() == DatabaseSettings.Type.POSTGRES) {
                    Class.forName("org.postgresql.Driver");
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

    private void initSchema() throws SQLException {
        String idColumn = postgresDialect
                ? "id BIGSERIAL PRIMARY KEY"
                : "id INTEGER PRIMARY KEY AUTOINCREMENT";
        try (Statement st = connection.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS workspace_mosaics (
                      %s,
                      mosaic_id TEXT NOT NULL UNIQUE,
                      or_id TEXT NOT NULL,
                      case_id TEXT NOT NULL,
                      layout_id TEXT NOT NULL,
                      layout_name TEXT,
                      rows_n INTEGER NOT NULL,
                      cols_n INTEGER NOT NULL,
                      name TEXT,
                      cells_json TEXT NOT NULL,
                      destination_id TEXT,
                      destination_name TEXT,
                      output_stream_id TEXT,
                      route_id TEXT,
                      status TEXT NOT NULL,
                      operator TEXT,
                      created_at TEXT NOT NULL,
                      updated_at TEXT NOT NULL
                    )
                    """.formatted(idColumn));
            st.execute("""
                    CREATE INDEX IF NOT EXISTS idx_workspace_mosaics_or_case
                    ON workspace_mosaics(or_id, case_id)
                    """);
        }
    }

    public void upsert(String orId, ActiveMosaic mosaic) {
        String excluded = postgresDialect ? "EXCLUDED" : "excluded";
        String sql = """
                INSERT INTO workspace_mosaics (
                  mosaic_id, or_id, case_id, layout_id, layout_name, rows_n, cols_n, name,
                  cells_json, destination_id, destination_name, output_stream_id, route_id,
                  status, operator, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (mosaic_id) DO UPDATE SET
                  layout_id=%1$s.layout_id,
                  layout_name=%1$s.layout_name,
                  rows_n=%1$s.rows_n,
                  cols_n=%1$s.cols_n,
                  name=%1$s.name,
                  cells_json=%1$s.cells_json,
                  destination_id=%1$s.destination_id,
                  destination_name=%1$s.destination_name,
                  output_stream_id=%1$s.output_stream_id,
                  route_id=%1$s.route_id,
                  status=%1$s.status,
                  operator=%1$s.operator,
                  updated_at=%1$s.updated_at
                """.formatted(excluded);
        String now = Instant.now().toString();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, mosaic.getMosaicId());
            ps.setString(2, orId);
            ps.setString(3, mosaic.getCaseId());
            ps.setString(4, mosaic.getLayoutId());
            ps.setString(5, mosaic.getLayoutName());
            ps.setInt(6, mosaic.getRows());
            ps.setInt(7, mosaic.getCols());
            ps.setString(8, mosaic.getName());
            ps.setString(9, cellsToJson(mosaic.getCells()));
            ps.setString(10, mosaic.getDestinationId());
            ps.setString(11, mosaic.getDestinationName());
            ps.setString(12, mosaic.getOutputStreamId());
            ps.setString(13, mosaic.getRouteId());
            ps.setString(14, mosaic.getStatus().name());
            ps.setString(15, mosaic.getOperator());
            ps.setString(16, mosaic.getCreatedAt().toString());
            ps.setString(17, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to persist mosaic " + mosaic.getMosaicId(), e);
        }
    }

    public void delete(String orId, String mosaicId) {
        String sql = "DELETE FROM workspace_mosaics WHERE or_id=? AND mosaic_id=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, orId);
            ps.setString(2, mosaicId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete mosaic " + mosaicId, e);
        }
    }

    public List<ActiveMosaic> findByOr(String orId) {
        String sql = """
                SELECT mosaic_id, case_id, layout_id, layout_name, rows_n, cols_n, name,
                       cells_json, destination_id, destination_name, output_stream_id, route_id,
                       status, operator, created_at, updated_at
                FROM workspace_mosaics
                WHERE or_id=?
                ORDER BY created_at ASC
                """;
        List<ActiveMosaic> list = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, orId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load mosaics for OR " + orId, e);
        }
        return list;
    }

    public List<ActiveMosaic> findByCase(String orId, String caseId) {
        return findByOr(orId).stream().filter(m -> m.getCaseId().equals(caseId)).toList();
    }

    private static ActiveMosaic mapRow(ResultSet rs) throws SQLException {
        Instant createdAt = parseInstant(rs.getString("created_at"));
        Instant updatedAt = parseInstant(rs.getString("updated_at"));
        ActiveMosaic.Status status;
        try {
            status = ActiveMosaic.Status.valueOf(rs.getString("status"));
        } catch (Exception e) {
            status = ActiveMosaic.Status.DRAFT;
        }
        return new ActiveMosaic(
                rs.getString("mosaic_id"),
                rs.getString("case_id"),
                rs.getString("layout_id"),
                rs.getString("layout_name"),
                rs.getInt("rows_n"),
                rs.getInt("cols_n"),
                rs.getString("name"),
                cellsFromJson(rs.getString("cells_json")),
                rs.getString("destination_id"),
                rs.getString("destination_name"),
                rs.getString("output_stream_id"),
                rs.getString("route_id"),
                status,
                rs.getString("operator"),
                createdAt,
                updatedAt);
    }

    private static Instant parseInstant(String raw) {
        try {
            return Instant.parse(raw);
        } catch (Exception e) {
            return Instant.now();
        }
    }

    static String cellsToJson(List<ActiveMosaic.Cell> cells) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (ActiveMosaic.Cell c : cells) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("index", c.index());
            m.put("row", c.row());
            m.put("col", c.col());
            m.put("rowSpan", c.rowSpan());
            m.put("colSpan", c.colSpan());
            if (c.sourceId() != null) {
                m.put("sourceId", c.sourceId());
            }
            if (c.sourceName() != null) {
                m.put("sourceName", c.sourceName());
            }
            if (c.deviceId() != null) {
                m.put("deviceId", c.deviceId());
            }
            if (c.streamId() != null) {
                m.put("streamId", c.streamId());
            }
            list.add(m);
        }
        return RouteRepository.toCompactJson(list);
    }

    @SuppressWarnings("unchecked")
    static List<ActiveMosaic.Cell> cellsFromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        Object parsed = parseJson(json.trim());
        if (!(parsed instanceof List<?> list)) {
            return List.of();
        }
        List<ActiveMosaic.Cell> cells = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> raw)) {
                continue;
            }
            Map<String, Object> m = (Map<String, Object>) raw;
            cells.add(new ActiveMosaic.Cell(
                    toInt(m.get("index")),
                    toInt(m.get("row")),
                    toInt(m.get("col")),
                    Math.max(1, toInt(m.getOrDefault("rowSpan", 1))),
                    Math.max(1, toInt(m.getOrDefault("colSpan", 1))),
                    str(m.get("sourceId")),
                    str(m.get("sourceName")),
                    str(m.get("deviceId")),
                    str(m.get("streamId"))));
        }
        return cells;
    }

    private static int toInt(Object v) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v == null) {
            return 0;
        }
        try {
            return Integer.parseInt(v.toString());
        } catch (Exception e) {
            return 0;
        }
    }

    private static String str(Object v) {
        return v == null ? null : v.toString();
    }

    /** Minimal JSON array/object parser for cell payloads. */
    static Object parseJson(String s) {
        return new MiniJson(s).parseValue();
    }

    private static final class MiniJson {
        private final String s;
        private int i;

        MiniJson(String s) {
            this.s = s;
        }

        Object parseValue() {
            skipWs();
            if (i >= s.length()) {
                return null;
            }
            char c = s.charAt(i);
            if (c == '{') {
                return parseObject();
            }
            if (c == '[') {
                return parseArray();
            }
            if (c == '"') {
                return parseString();
            }
            if (c == 't' || c == 'f' || c == 'n' || c == '-' || Character.isDigit(c)) {
                return parseLiteral();
            }
            throw new IllegalArgumentException("bad json at " + i);
        }

        private Map<String, Object> parseObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            i++; // {
            skipWs();
            if (peek('}')) {
                i++;
                return map;
            }
            while (true) {
                skipWs();
                String key = parseString();
                skipWs();
                expect(':');
                Object value = parseValue();
                map.put(key, value);
                skipWs();
                if (peek('}')) {
                    i++;
                    break;
                }
                expect(',');
            }
            return map;
        }

        private List<Object> parseArray() {
            List<Object> list = new ArrayList<>();
            i++; // [
            skipWs();
            if (peek(']')) {
                i++;
                return list;
            }
            while (true) {
                list.add(parseValue());
                skipWs();
                if (peek(']')) {
                    i++;
                    break;
                }
                expect(',');
            }
            return list;
        }

        private String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\' && i < s.length()) {
                    char n = s.charAt(i++);
                    sb.append(switch (n) {
                        case '"', '\\', '/' -> n;
                        case 'n' -> '\n';
                        case 'r' -> '\r';
                        case 't' -> '\t';
                        default -> n;
                    });
                } else {
                    sb.append(c);
                }
            }
            throw new IllegalArgumentException("unterminated string");
        }

        private Object parseLiteral() {
            int start = i;
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == ',' || c == '}' || c == ']' || Character.isWhitespace(c)) {
                    break;
                }
                i++;
            }
            String token = s.substring(start, i);
            if ("true".equals(token)) {
                return true;
            }
            if ("false".equals(token)) {
                return false;
            }
            if ("null".equals(token)) {
                return null;
            }
            if (token.contains(".") || token.contains("e") || token.contains("E")) {
                return Double.parseDouble(token);
            }
            return Long.parseLong(token);
        }

        private void skipWs() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }

        private boolean peek(char c) {
            return i < s.length() && s.charAt(i) == c;
        }

        private void expect(char c) {
            skipWs();
            if (i >= s.length() || s.charAt(i) != c) {
                throw new IllegalArgumentException("expected '" + c + "' at " + i);
            }
            i++;
        }
    }

    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            log.warn("Failed to close mosaic database: {}", e.getMessage());
        }
    }
}
