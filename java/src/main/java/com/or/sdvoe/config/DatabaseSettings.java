package com.or.sdvoe.config;

import java.util.Map;
import java.util.Objects;

/** 服务端路由存储配置：本地 SQLite 或远程 PostgreSQL/JDBC。 */
public final class DatabaseSettings {

    public enum Type {
        SQLITE,
        POSTGRES,
        JDBC
    }

    private final Type type;
    private final String path;
    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final boolean restoreRoutesOnStartup;
    private final boolean autoBackup;
    private final String backupDir;

    public DatabaseSettings(
            Type type,
            String path,
            String jdbcUrl,
            String username,
            String password,
            boolean restoreRoutesOnStartup,
            boolean autoBackup,
            String backupDir) {
        this.type = type != null ? type : Type.SQLITE;
        this.path = path != null ? path : "data/or-desk.db";
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        this.restoreRoutesOnStartup = restoreRoutesOnStartup;
        this.autoBackup = autoBackup;
        this.backupDir = backupDir != null ? backupDir : "data/backups";
    }

    @SuppressWarnings("unchecked")
    public static DatabaseSettings fromRoot(Map<String, Object> root) {
        Map<String, Object> database =
                (Map<String, Object>) root.getOrDefault("database", Map.of());
        String typeRaw = Objects.toString(database.getOrDefault("type", "sqlite")).trim().toLowerCase();
        Type type = switch (typeRaw) {
            case "postgres", "postgresql", "pg" -> Type.POSTGRES;
            case "jdbc" -> Type.JDBC;
            default -> Type.SQLITE;
        };
        return new DatabaseSettings(
                type,
                Objects.toString(database.getOrDefault("path", "data/or-desk.db")),
                database.get("jdbc_url") == null ? null : database.get("jdbc_url").toString(),
                database.get("username") == null ? null : database.get("username").toString(),
                database.get("password") == null ? null : database.get("password").toString(),
                !Boolean.FALSE.equals(database.get("restore_routes_on_startup")),
                !Boolean.FALSE.equals(database.get("auto_backup")),
                Objects.toString(database.getOrDefault("backup_dir", "data/backups")));
    }

    public Type getType() {
        return type;
    }

    public String getPath() {
        return path;
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public boolean isRestoreRoutesOnStartup() {
        return restoreRoutesOnStartup;
    }

    public boolean isAutoBackup() {
        return autoBackup;
    }

    public String getBackupDir() {
        return backupDir;
    }

    public boolean isRemoteServer() {
        return type == Type.POSTGRES || type == Type.JDBC;
    }

    public String describe() {
        return switch (type) {
            case SQLITE -> "sqlite:" + path;
            case POSTGRES, JDBC -> jdbcUrl == null ? type.name().toLowerCase() : jdbcUrl;
        };
    }
}
