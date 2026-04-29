package co.tkm.ticketmanagementsystem.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SchemaMigrationRunner implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        // Ensure guest checkouts can persist bookings with null user_id.
        jdbcTemplate.execute("ALTER TABLE bookings ALTER COLUMN user_id DROP NOT NULL");
    }
}
