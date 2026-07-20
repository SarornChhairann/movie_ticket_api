package co.tkm.ticketmanagementsystem.config;

import co.tkm.ticketmanagementsystem.service.DatabaseSeederService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = "app.seed.enabled", havingValue = "true")
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final DatabaseSeederService databaseSeederService;

    @Value("${app.seed.force:false}")
    private boolean force;

    @Override
    public void run(String... args) {
        if (force) {
            databaseSeederService.rollback();
        }
        try {
            databaseSeederService.seed();
        } catch (Exception e) {
            log.error("Failed to seed database: ", e);
        }
    }
}