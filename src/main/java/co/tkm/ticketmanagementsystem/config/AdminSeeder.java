package co.tkm.ticketmanagementsystem.config;

import co.tkm.ticketmanagementsystem.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminSeeder implements CommandLineRunner {

    private final UserService userService;

    @Value("${APP_ADMIN_NAME:Admin}")
    private String adminName;

    @Value("${APP_ADMIN_EMAIL:admin@mtms.local}")
    private String adminEmail;

    @Value("${APP_ADMIN_PASSWORD:admin123}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        userService.createAdminIfMissing(adminName, adminEmail, adminPassword);
    }
}
