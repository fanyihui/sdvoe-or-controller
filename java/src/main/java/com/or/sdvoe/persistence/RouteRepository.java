package com.or.sdvoe.persistence;

import com.or.sdvoe.workspace.ActiveRoute;
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
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** SQLite 持久化：手术工作空间活动路由。 */
public final class RouteRepository implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(RouteRepository.class);

    private final Path dbPath;
    private final Connection connection;

    public RouteRepository(Path dbPath) {
        this.dbPath = Objects.requireNonNull(dbPath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.dbPath.getParent() == null
                    ? Path.of(".")
                    : this.dbPath.getParent());
            Class.forName("org.sqlite.JDBC");
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + this.dbPath);
            this.connection.setAutoCommit(true);
            initSchema();
            log.info("Route database ready: {}", this.dbPath);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to open route database: " + this.dbPath, e);
        }
    }

    private void initSchema() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS workspace_routes (
                      id INTEGER PRIMARY KEY AUTOINCREMENT,
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
                    """);
            st.execute("""
                    CREATE INDEX IF NOT EXISTS idx_workspace_routes_or
                    ON workspace_routes(or_id)
                    """);
        }
    }

    public void upsert(String orId, ActiveRoute route) {
        String sql = """
                INSERT INTO workspace_routes (
                  route_id, or_id, case_id, source_id, source_name,
                  destination_id, destination_name, stream_id, fabrics,
                  policy_reason, operator, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(or_id, case_id, destination_id) DO UPDATE SET
                  route_id=excluded.route_id,
                  source_id=excluded.source_id,
                  source_name=excluded.source_name,
                  destination_name=excluded.destination_name,
                  stream_id=excluded.stream_id,
                  fabrics=excluded.fabrics,
                  policy_reason=excluded.policy_reason,
                  operator=excluded.operator,
                  updated_at=excluded.updated_at
                """;
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

    public Path getDbPath() {
        return dbPath;
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

    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            log.warn("Failed to close route database: {}", e.getMessage());
        }
    }
}
