package com.sekai.song.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SongSchemaMaintenance implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;

    public SongSchemaMaintenance(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureColumn("sekai_song_track", "local_audio_url", "VARCHAR(800)");
        ensureColumn("sekai_song_track", "local_video_url", "VARCHAR(800)");
        ensureColumn("sekai_song_user", "license_plan", "VARCHAR(32)");
        ensureColumn("sekai_song_user", "license_expire_time", "DATETIME");
        ensureColumn("sekai_song_user", "license_code", "VARCHAR(64)");
        ensureBusinessLeadTable();
        ensureBusinessOrderTable();
        ensureLicenseCodeTable();
        markExistingUsersAsLifetime();
    }

    private void ensureColumn(String tableName, String columnName, String definition) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                        "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                Integer.class,
                tableName,
                columnName);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + definition);
    }

    private void ensureBusinessLeadTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS sekai_song_business_lead (
                    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    name VARCHAR(80) NOT NULL,
                    contact VARCHAR(160) NOT NULL,
                    plan VARCHAR(80) NOT NULL,
                    budget VARCHAR(80) NULL,
                    message VARCHAR(1000) NULL,
                    status VARCHAR(24) NOT NULL DEFAULT 'NEW',
                    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    INDEX idx_sekai_song_business_lead_status_time(status, create_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
    }

    private void ensureLicenseCodeTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS sekai_song_license_code (
                    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    code VARCHAR(64) NOT NULL UNIQUE,
                    plan VARCHAR(32) NOT NULL DEFAULT 'PRO',
                    duration_days INT NULL,
                    max_activations INT NOT NULL DEFAULT 1,
                    used_count INT NOT NULL DEFAULT 0,
                    enabled TINYINT(1) NOT NULL DEFAULT 1,
                    note VARCHAR(200) NULL,
                    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    INDEX idx_sekai_song_license_code_plan_time(plan, create_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
    }

    private void ensureBusinessOrderTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS sekai_song_business_order (
                    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    customer_name VARCHAR(80) NOT NULL,
                    contact VARCHAR(160) NOT NULL,
                    service_plan VARCHAR(80) NOT NULL,
                    total_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
                    paid_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
                    payment_status VARCHAR(24) NOT NULL DEFAULT 'UNPAID',
                    status VARCHAR(24) NOT NULL DEFAULT 'QUOTED',
                    due_date DATE NULL,
                    note VARCHAR(1000) NULL,
                    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    INDEX idx_sekai_song_business_order_status_time(status, create_time),
                    INDEX idx_sekai_song_business_order_payment_time(payment_status, create_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
    }

    private void markExistingUsersAsLifetime() {
        jdbcTemplate.update("""
                UPDATE sekai_song_user
                SET license_plan = 'LIFETIME', license_expire_time = NULL
                WHERE license_plan IS NULL OR license_plan = ''
                """);
    }
}
