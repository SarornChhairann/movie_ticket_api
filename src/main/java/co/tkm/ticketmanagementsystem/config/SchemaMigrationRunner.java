package co.tkm.ticketmanagementsystem.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.PriorityOrdered;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SchemaMigrationRunner implements BeanPostProcessor, PriorityOrdered {

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
            return;
        }

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
    }

    private void applyIfNotApplied(JdbcTemplate jdbcTemplate, String id, Runnable migration) {
        Integer appliedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM schema_migrations WHERE id = ?",
                Integer.class,
                id
        );
        if (appliedCount != null && appliedCount > 0) {
            return;
        }
        migration.run();
        jdbcTemplate.update("INSERT INTO schema_migrations(id) VALUES (?)", id);
    }

    private boolean tableExists(JdbcTemplate jdbcTemplate, String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = current_schema() AND table_name = ?",
                Integer.class,
                tableName
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
