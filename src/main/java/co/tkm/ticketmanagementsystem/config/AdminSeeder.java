package co.tkm.ticketmanagementsystem.config;
import co.tkm.ticketmanagementsystem.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = "app.seed.admin.enabled", havingValue = "true", matchIfMissing = true)
public class AdminSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final UserService userService;

    @Value("${APP_ADMIN_NAME:Admin}")
    private String adminName;

    @Value("${APP_ADMIN_EMAIL:admin@mtms.local}")
    private String adminEmail;

    @Value("${APP_ADMIN_PASSWORD:admin123}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        log.info("Ensuring admin user exists: {}", adminEmail);
        userService.createAdminIfMissing(adminName, adminEmail, adminPassword);
    }
}
