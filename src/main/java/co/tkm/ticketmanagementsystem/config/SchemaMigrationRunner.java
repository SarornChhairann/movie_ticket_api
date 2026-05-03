package co.tkm.ticketmanagementsystem.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.PriorityOrdered;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class SchemaMigrationRunner implements BeanPostProcessor, PriorityOrdered {

    private static final Logger log = LoggerFactory.getLogger(SchemaMigrationRunner.class);

    private volatile boolean migrated;

    @Override
    public int getOrder() {
        return PriorityOrdered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (migrated) {
            return bean;
        }
        if (bean instanceof javax.sql.DataSource dataSource) {
            migrated = true;
            runMigrations(dataSource);
        }
        return bean;
    }

    private void runMigrations(javax.sql.DataSource dataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        String databaseProductName;
        try (java.sql.Connection connection = dataSource.getConnection()) {
            databaseProductName = connection.getMetaData().getDatabaseProductName();
        } catch (java.sql.SQLException ex) {
            throw new IllegalStateException("Unable to determine database product", ex);
        }

        if (databaseProductName == null || !databaseProductName.toLowerCase().contains("postgres")) {
            log.info("Skipping schema migrations (db={})", databaseProductName);
            return;
        }

        log.info("Running schema migrations (db={})", databaseProductName);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS schema_migrations (
                    id VARCHAR(120) PRIMARY KEY,
                    applied_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                )
                """);

        applyIfNotApplied(jdbcTemplate, "2026_05_03_001_bookings_user_id_nullable", () -> {
            if (!tableExists(jdbcTemplate, "bookings")) {
                return;
            }
            if (isColumnNotNull(jdbcTemplate, "bookings", "user_id")) {
                jdbcTemplate.execute("ALTER TABLE bookings ALTER COLUMN user_id DROP NOT NULL");
            }
        });

        applyIfNotApplied(jdbcTemplate, "2026_05_03_002_bookings_guest_columns", () -> {
            if (!tableExists(jdbcTemplate, "bookings")) {
                return;
            }
            jdbcTemplate.execute("ALTER TABLE bookings ADD COLUMN IF NOT EXISTS guest_name VARCHAR(150)");
            jdbcTemplate.execute("ALTER TABLE bookings ADD COLUMN IF NOT EXISTS guest_phone VARCHAR(30)");
            jdbcTemplate.execute("ALTER TABLE bookings ADD COLUMN IF NOT EXISTS guest_email VARCHAR(180)");
        });

        applyIfNotApplied(jdbcTemplate, "2026_05_03_003_users_phone_column", () -> {
            if (!tableExists(jdbcTemplate, "users")) {
                return;
            }
            jdbcTemplate.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS phone VARCHAR(30)");
        });

        applyIfNotApplied(jdbcTemplate, "2026_05_03_004_system_settings_setting_key", () -> {
            if (!tableExists(jdbcTemplate, "system_settings")) {
                return;
            }
            boolean hasKey = columnExists(jdbcTemplate, "system_settings", "key");
            boolean hasSettingKey = columnExists(jdbcTemplate, "system_settings", "setting_key");
            if (hasKey && !hasSettingKey) {
                jdbcTemplate.execute("ALTER TABLE system_settings RENAME COLUMN key TO setting_key");
            }
        });

        applyIfNotApplied(jdbcTemplate, "2026_05_03_005_system_settings_setting_value", () -> {
            if (!tableExists(jdbcTemplate, "system_settings")) {
                return;
            }
            boolean hasValue = columnExists(jdbcTemplate, "system_settings", "value");
            boolean hasSettingValue = columnExists(jdbcTemplate, "system_settings", "setting_value");
            if (hasValue && !hasSettingValue) {
                jdbcTemplate.execute("ALTER TABLE system_settings RENAME COLUMN value TO setting_value");
            }
        });

        applyIfNotApplied(jdbcTemplate, "2026_05_03_006_users_role_status_backfill", () -> {
            if (!tableExists(jdbcTemplate, "users")) {
                return;
            }
            if (columnExists(jdbcTemplate, "users", "role")) {
                jdbcTemplate.execute("UPDATE users SET role = 'CUSTOMER' WHERE role IS NULL");
                jdbcTemplate.execute("ALTER TABLE users ALTER COLUMN role SET DEFAULT 'CUSTOMER'");
                if (!isColumnNotNull(jdbcTemplate, "users", "role")) {
                    jdbcTemplate.execute("ALTER TABLE users ALTER COLUMN role SET NOT NULL");
                }
            }
            if (columnExists(jdbcTemplate, "users", "status")) {
                jdbcTemplate.execute("UPDATE users SET status = 'ACTIVE' WHERE status IS NULL");
                jdbcTemplate.execute("ALTER TABLE users ALTER COLUMN status SET DEFAULT 'ACTIVE'");
                if (!isColumnNotNull(jdbcTemplate, "users", "status")) {
                    jdbcTemplate.execute("ALTER TABLE users ALTER COLUMN status SET NOT NULL");
                }
            }
        });

        applyIfNotApplied(jdbcTemplate, "2026_05_03_007_users_missing_columns", () -> {
            if (!tableExists(jdbcTemplate, "users")) {
                return;
            }

            if (!columnExists(jdbcTemplate, "users", "points")) {
                jdbcTemplate.execute("ALTER TABLE users ADD COLUMN points INT NOT NULL DEFAULT 0");
            }

            if (!columnExists(jdbcTemplate, "users", "role")) {
                jdbcTemplate.execute("ALTER TABLE users ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER'");
            }

            if (!columnExists(jdbcTemplate, "users", "status")) {
                jdbcTemplate.execute("ALTER TABLE users ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'");
            }
        });
    }

    private void applyIfNotApplied(JdbcTemplate jdbcTemplate, String id, Runnable migration) {
        Integer appliedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM schema_migrations WHERE id = ?",
                Integer.class,
                id
        );
        if (appliedCount != null && appliedCount > 0) {
            log.debug("Migration already applied: {}", id);
            return;
        }
        try {
            log.info("Applying migration: {}", id);
            migration.run();
            jdbcTemplate.update("INSERT INTO schema_migrations(id) VALUES (?)", id);
            log.info("Applied migration: {}", id);
        } catch (RuntimeException ex) {
            log.error("Migration failed: {}", id, ex);
            throw ex;
        }
    }

    private boolean tableExists(JdbcTemplate jdbcTemplate, String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = current_schema() AND table_name = ?",
                Integer.class,
                tableName
        );
        return count != null && count > 0;
    }

    private boolean columnExists(JdbcTemplate jdbcTemplate, String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = ? AND column_name = ?",
                Integer.class,
                tableName,
                columnName
        );
        return count != null && count > 0;
    }

    private boolean isColumnNotNull(JdbcTemplate jdbcTemplate, String tableName, String columnName) {
        String isNullable = jdbcTemplate.queryForObject(
                "SELECT is_nullable FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = ? AND column_name = ?",
                String.class,
                tableName,
                columnName
        );
        return isNullable != null && isNullable.equalsIgnoreCase("NO");
    }
}
