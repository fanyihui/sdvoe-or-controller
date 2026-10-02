package com.or.sdvoe.config;

import java.nio.file.Path;

/** 解析环境变量覆盖后的数据库设置。 */
public final class DatabaseSettingsFactory {

    private DatabaseSettingsFactory() {
    }

    public static DatabaseSettings resolve(DatabaseSettings base) {
        String typeEnv = System.getenv("OR_DESK_DB_TYPE");
        String pathEnv = System.getenv("OR_DESK_DB");
        String jdbcEnv = System.getenv("OR_DESK_JDBC_URL");
        String userEnv = System.getenv("OR_DESK_DB_USER");
        String passEnv = System.getenv("OR_DESK_DB_PASSWORD");
        String backupEnv = System.getenv("OR_DESK_BACKUP_DIR");

        DatabaseSettings.Type type = base.getType();
        if (typeEnv != null && !typeEnv.isBlank()) {
            type = switch (typeEnv.trim().toLowerCase()) {
                case "postgres", "postgresql", "pg" -> DatabaseSettings.Type.POSTGRES;
                case "jdbc" -> DatabaseSettings.Type.JDBC;
                default -> DatabaseSettings.Type.SQLITE;
            };
        } else if (jdbcEnv != null && !jdbcEnv.isBlank()) {
            type = jdbcEnv.toLowerCase().contains("postgres")
                    ? DatabaseSettings.Type.POSTGRES
                    : DatabaseSettings.Type.JDBC;
        }

        String path = pathEnv != null && !pathEnv.isBlank() ? pathEnv : base.getPath();
        if (type == DatabaseSettings.Type.SQLITE) {
            path = Path.of(path).toAbsolutePath().normalize().toString();
        }
        String jdbcUrl = jdbcEnv != null && !jdbcEnv.isBlank() ? jdbcEnv : base.getJdbcUrl();
        String username = userEnv != null ? userEnv : base.getUsername();
        String password = passEnv != null ? passEnv : base.getPassword();
        String backupDir = backupEnv != null && !backupEnv.isBlank()
                ? Path.of(backupEnv).toAbsolutePath().normalize().toString()
                : Path.of(base.getBackupDir()).toAbsolutePath().normalize().toString();

        return new DatabaseSettings(
                type,
                path,
                jdbcUrl,
                username,
                password,
                base.isRestoreRoutesOnStartup(),
                base.isAutoBackup(),
                backupDir);
    }
}
