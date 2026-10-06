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

        applyIfNotApplied(jdbcTemplate, "2026_05_03_008_movies_string_columns_type_fix", () -> {
            if (!tableExists(jdbcTemplate, "movies")) {
                return;
            }

            fixByteaToVarchar(jdbcTemplate, "movies", "title", 200);
            fixByteaToText(jdbcTemplate, "movies", "description");
            fixByteaToVarchar(jdbcTemplate, "movies", "genre", 100);
            fixByteaToVarchar(jdbcTemplate, "movies", "director", 120);
            fixByteaToVarchar(jdbcTemplate, "movies", "language", 60);
            fixByteaToVarchar(jdbcTemplate, "movies", "format", 20);
            fixByteaToText(jdbcTemplate, "movies", "movie_cast");
            fixByteaToVarchar(jdbcTemplate, "movies", "trailer_url", 500);
            fixByteaToVarchar(jdbcTemplate, "movies", "rating", 10);
            fixByteaToVarchar(jdbcTemplate, "movies", "image_path", 500);
            fixByteaToVarchar(jdbcTemplate, "movies", "banner_path", 500);
        });

        applyIfNotApplied(jdbcTemplate, "2026_05_03_009_food_items_image_url_column", () -> {
            if (!tableExists(jdbcTemplate, "food_items")) {
                return;
            }

            boolean hasImageUrlSnake = columnExists(jdbcTemplate, "food_items", "image_url");
            boolean hasImageUrlCamel = columnExists(jdbcTemplate, "food_items", "imageurl");
            if (hasImageUrlCamel && !hasImageUrlSnake) {
                jdbcTemplate.execute("ALTER TABLE food_items RENAME COLUMN imageurl TO image_url");
                hasImageUrlSnake = true;
            }

            if (!hasImageUrlSnake) {
                jdbcTemplate.execute("ALTER TABLE food_items ADD COLUMN image_url VARCHAR(500)");
            }

            fixByteaToVarchar(jdbcTemplate, "food_items", "image_url", 500);
        });

        applyIfNotApplied(jdbcTemplate, "2026_05_03_010_movies_release_date_column", () -> {
            if (!tableExists(jdbcTemplate, "movies")) {
                return;
            }
            if (!columnExists(jdbcTemplate, "movies", "release_date")) {
                jdbcTemplate.execute("ALTER TABLE movies ADD COLUMN release_date DATE");
            }
        });

        applyIfNotApplied(jdbcTemplate, "2026_05_03_011_movie_reminders_table", () -> {
            if (!tableExists(jdbcTemplate, "movies")) {
                return;
            }
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS movie_reminders (
                        id BIGSERIAL PRIMARY KEY,
                        movie_id BIGINT NOT NULL REFERENCES movies(id) ON DELETE CASCADE,
                        email VARCHAR(180) NOT NULL,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                    )
                    """);
            jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS ux_movie_reminders_movie_email ON movie_reminders(movie_id, email)");
        });

        applyIfNotApplied(jdbcTemplate, "2026_05_10_001_cinemas_table", () -> {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS cinemas (
                        id UUID PRIMARY KEY,
                        name VARCHAR(140) NOT NULL,
                        address VARCHAR(300),
                        opening_hours VARCHAR(50),
                        total_halls INT,
                        latitude DOUBLE PRECISION,
                        longitude DOUBLE PRECISION
                    )
                    """);
        });

        applyIfNotApplied(jdbcTemplate, "2026_05_10_002_halls_cinema_columns", () -> {
            if (!tableExists(jdbcTemplate, "halls")) {
                return;
            }
            jdbcTemplate.execute("ALTER TABLE halls ADD COLUMN IF NOT EXISTS cinema_id UUID");
            jdbcTemplate.execute("ALTER TABLE halls ADD COLUMN IF NOT EXISTS type VARCHAR(40)");
            jdbcTemplate.execute("ALTER TABLE halls ADD COLUMN IF NOT EXISTS capacity INT");
            jdbcTemplate.execute("ALTER TABLE halls ADD COLUMN IF NOT EXISTS has_dolby_atmos BOOLEAN NOT NULL DEFAULT FALSE");
            boolean hasScreenx = columnExists(jdbcTemplate, "halls", "has_screenx");
            boolean hasScreenX = columnExists(jdbcTemplate, "halls", "has_screen_x");
            if (hasScreenx && !hasScreenX) {
                jdbcTemplate.execute("ALTER TABLE halls RENAME COLUMN has_screenx TO has_screen_x");
            }
            jdbcTemplate.execute("ALTER TABLE halls ADD COLUMN IF NOT EXISTS has_screen_x BOOLEAN NOT NULL DEFAULT FALSE");

            jdbcTemplate.execute("""
                    DO $$
                    BEGIN
                        IF NOT EXISTS (
                            SELECT 1 FROM information_schema.table_constraints
                            WHERE table_schema = current_schema()
                              AND table_name = 'halls'
                              AND constraint_type = 'FOREIGN KEY'
                              AND constraint_name = 'fk_halls_cinema_id'
                        ) THEN
                            ALTER TABLE halls
                                ADD CONSTRAINT fk_halls_cinema_id
                                FOREIGN KEY (cinema_id) REFERENCES cinemas(id);
                        END IF;
                    END $$;
                    """);
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS ix_halls_cinema_id ON halls(cinema_id)");
        });

        applyIfNotApplied(jdbcTemplate, "2026_05_10_003_halls_has_screenx_rename", () -> {
            if (!tableExists(jdbcTemplate, "halls")) {
                return;
            }
            boolean hasScreenx = columnExists(jdbcTemplate, "halls", "has_screenx");
            boolean hasScreenX = columnExists(jdbcTemplate, "halls", "has_screen_x");
            if (hasScreenx && !hasScreenX) {
                jdbcTemplate.execute("ALTER TABLE halls RENAME COLUMN has_screenx TO has_screen_x");
            }
            jdbcTemplate.execute("ALTER TABLE halls ADD COLUMN IF NOT EXISTS has_screen_x BOOLEAN NOT NULL DEFAULT FALSE");
        });

        // Sprint 12 (G25): the session that created a booking is one of its two readers.
        // Older guest bookings paid through PayWay take it from their payment row.
        applyIfNotApplied(jdbcTemplate, "2026_10_06_001_bookings_session_id", () -> {
            if (!tableExists(jdbcTemplate, "bookings")) {
                return;
            }
            jdbcTemplate.execute("ALTER TABLE bookings ADD COLUMN IF NOT EXISTS session_id VARCHAR(200)");
            if (tableExists(jdbcTemplate, "payments") && columnExists(jdbcTemplate, "payments", "session_id")) {
                jdbcTemplate.execute("""
                        UPDATE bookings b SET session_id = p.session_id
                        FROM payments p
                        WHERE p.booking_id = b.id AND b.session_id IS NULL AND p.session_id IS NOT NULL
                        """);
            }
        });

        // Sprint 12 (G28): Hibernate wrote a CHECK listing the old PaymentStatus values when it
        // created the table, and ddl-auto=update never widens it, so REFUND_PENDING would fail.
        applyIfNotApplied(jdbcTemplate, "2026_10_06_002_payments_status_check_drop", () -> {
            if (!tableExists(jdbcTemplate, "payments")) {
                return;
            }
            jdbcTemplate.execute("ALTER TABLE payments DROP CONSTRAINT IF EXISTS payments_status_check");
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

    private String columnUdtName(JdbcTemplate jdbcTemplate, String tableName, String columnName) {
        return jdbcTemplate.queryForObject(
                "SELECT udt_name FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = ? AND column_name = ?",
                String.class,
                tableName,
                columnName
        );
    }

    private void fixByteaToVarchar(JdbcTemplate jdbcTemplate, String tableName, String columnName, int length) {
        if (!columnExists(jdbcTemplate, tableName, columnName)) {
            return;
        }
        String udt = columnUdtName(jdbcTemplate, tableName, columnName);
        if (udt != null && udt.equalsIgnoreCase("bytea")) {
            jdbcTemplate.execute(
                    "ALTER TABLE " + tableName + " ALTER COLUMN " + columnName
                    + " TYPE VARCHAR(" + length + ") USING convert_from(" + columnName + ", 'UTF8')"
            );
        }
    }

    private void fixByteaToText(JdbcTemplate jdbcTemplate, String tableName, String columnName) {
        if (!columnExists(jdbcTemplate, tableName, columnName)) {
            return;
        }
        String udt = columnUdtName(jdbcTemplate, tableName, columnName);
        if (udt != null && udt.equalsIgnoreCase("bytea")) {
            jdbcTemplate.execute(
                    "ALTER TABLE " + tableName + " ALTER COLUMN " + columnName
                    + " TYPE TEXT USING convert_from(" + columnName + ", 'UTF8')"
            );
        }
    }
}
