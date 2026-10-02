package com.or.sdvoe.persistence;

import com.or.sdvoe.config.DatabaseSettings;
import com.or.sdvoe.workspace.ActiveRecording;
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
import java.util.List;
import java.util.Objects;
import java.util.Properties;

/** 录制会话持久化（与路由同库）。 */
public final class RecordingRepository implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(RecordingRepository.class);

    private final DatabaseSettings settings;
    private final Connection connection;
    private final boolean postgresDialect;

    public RecordingRepository(DatabaseSettings settings) {
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
            log.info("Recording store ready: {}", settings.describe());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to open recording store: " + settings.describe(), e);
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
                    CREATE TABLE IF NOT EXISTS workspace_recordings (
                      %s,
                      session_id TEXT NOT NULL UNIQUE,
                      or_id TEXT NOT NULL,
                      case_id TEXT NOT NULL,
                      source_id TEXT NOT NULL,
                      source_name TEXT,
                      device_id TEXT,
                      stream_id TEXT,
                      mode TEXT NOT NULL,
                      recorder_target_id TEXT,
                      status TEXT NOT NULL,
                      artifact_uri TEXT,
                      artifact_bytes BIGINT,
                      operator TEXT,
                      error_message TEXT,
                      started_at TEXT NOT NULL,
                      stopped_at TEXT,
                      updated_at TEXT NOT NULL
                    )
                    """.formatted(idColumn));
            st.execute("""
                    CREATE INDEX IF NOT EXISTS idx_workspace_recordings_or_case
                    ON workspace_recordings(or_id, case_id)
                    """);
        }
    }

    public void upsert(String orId, ActiveRecording recording) {
        String excluded = postgresDialect ? "EXCLUDED" : "excluded";
        String sql = """
                INSERT INTO workspace_recordings (
                  session_id, or_id, case_id, source_id, source_name, device_id, stream_id,
                  mode, recorder_target_id, status, artifact_uri, artifact_bytes,
                  operator, error_message, started_at, stopped_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (session_id) DO UPDATE SET
                  source_id=%1$s.source_id,
                  source_name=%1$s.source_name,
                  device_id=%1$s.device_id,
                  stream_id=%1$s.stream_id,
                  mode=%1$s.mode,
                  recorder_target_id=%1$s.recorder_target_id,
                  status=%1$s.status,
                  artifact_uri=%1$s.artifact_uri,
                  artifact_bytes=%1$s.artifact_bytes,
                  operator=%1$s.operator,
                  error_message=%1$s.error_message,
                  stopped_at=%1$s.stopped_at,
                  updated_at=%1$s.updated_at
                """.formatted(excluded);
        String now = Instant.now().toString();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, recording.getSessionId());
            ps.setString(2, orId);
            ps.setString(3, recording.getCaseId());
            ps.setString(4, recording.getSourceId());
            ps.setString(5, recording.getSourceName());
            ps.setString(6, recording.getDeviceId());
            ps.setString(7, recording.getStreamId());
            ps.setString(8, recording.getMode().name());
            ps.setString(9, recording.getRecorderTargetId());
            ps.setString(10, recording.getStatus().name());
            ps.setString(11, recording.getArtifactUri());
            if (recording.getArtifactBytes() == null) {
                ps.setObject(12, null);
            } else {
                ps.setLong(12, recording.getArtifactBytes());
            }
            ps.setString(13, recording.getOperator());
            ps.setString(14, recording.getErrorMessage());
            ps.setString(15, recording.getStartedAt().toString());
            ps.setString(16, recording.getStoppedAt() == null ? null : recording.getStoppedAt().toString());
            ps.setString(17, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to persist recording " + recording.getSessionId(), e);
        }
    }

    public List<ActiveRecording> findByOr(String orId) {
        String sql = """
                SELECT session_id, case_id, source_id, source_name, device_id, stream_id, mode,
                       recorder_target_id, status, artifact_uri, artifact_bytes, operator,
                       error_message, started_at, stopped_at, updated_at
                FROM workspace_recordings
                WHERE or_id=?
                ORDER BY started_at ASC
                """;
        List<ActiveRecording> list = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, orId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load recordings for OR " + orId, e);
        }
        return list;
    }

    public List<ActiveRecording> findByCase(String orId, String caseId) {
        return findByOr(orId).stream().filter(r -> r.getCaseId().equals(caseId)).toList();
    }

    private static ActiveRecording mapRow(ResultSet rs) throws SQLException {
        ActiveRecording.Status status;
        try {
            status = ActiveRecording.Status.valueOf(rs.getString("status"));
        } catch (Exception e) {
            status = ActiveRecording.Status.FAILED;
        }
        ActiveRecording.Mode mode;
        try {
            mode = ActiveRecording.Mode.valueOf(rs.getString("mode"));
        } catch (Exception e) {
            mode = ActiveRecording.Mode.SOFTWARE;
        }
        Long bytes = rs.getObject("artifact_bytes") == null ? null : rs.getLong("artifact_bytes");
        return new ActiveRecording(
                rs.getString("session_id"),
                rs.getString("case_id"),
                rs.getString("source_id"),
                rs.getString("source_name"),
                rs.getString("device_id"),
                rs.getString("stream_id"),
                mode,
                rs.getString("recorder_target_id"),
                status,
                rs.getString("artifact_uri"),
                bytes,
                rs.getString("operator"),
                rs.getString("error_message"),
                parseInstant(rs.getString("started_at")),
                rs.getString("stopped_at") == null ? null : parseInstant(rs.getString("stopped_at")),
                parseInstant(rs.getString("updated_at")));
    }

    private static Instant parseInstant(String raw) {
        try {
            return Instant.parse(raw);
        } catch (Exception e) {
            return Instant.now();
        }
    }

    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            log.warn("Failed to close recording database: {}", e.getMessage());
        }
    }
}
