package co.tkm.ticketmanagementsystem.config;

import co.tkm.ticketmanagementsystem.model.Cinema;
import co.tkm.ticketmanagementsystem.model.Hall;
import co.tkm.ticketmanagementsystem.repository.CinemaRepository;
import co.tkm.ticketmanagementsystem.repository.HallRepository;
import co.tkm.ticketmanagementsystem.service.AuthService;
import co.tkm.ticketmanagementsystem.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = "app.seed.admin.enabled", havingValue = "true", matchIfMissing = true)
public class AdminSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final UserService userService;
    private final AuthService authService;
    private final KeycloakProperties keycloakProperties;
    private final HallRepository hallRepository;
    private final CinemaRepository cinemaRepository;

    // Set in .env; the defaults here are the same values, so a checkout without an
    // .env file still boots with the documented admin account.
    @Value("${APP_ADMIN_NAME:System Admin}")
    private String adminName;

    @Value("${APP_ADMIN_EMAIL:admin@gmail.com}")
    private String adminEmail;

    @Value("${APP_ADMIN_PASSWORD:admin123}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        log.info("Ensuring admin user exists: {}", adminEmail);
        ensureKeycloakAdmin();
        userService.createAdminIfMissing(adminName, adminEmail.trim().toLowerCase());
        backfillCinemasFromHalls();
    }

    /**
     * Keycloak, not this app, decides whether the APP_ADMIN_* credentials work, so the
     * account is pushed there as well as mirrored locally. It is deliberately not fatal:
     * the realm import already ships an admin, and a Keycloak that is still starting up
     * (or switched off, as in tests) should not stop the app from booting.
     */
    private void ensureKeycloakAdmin() {
        if (!keycloakProperties.bootstrapAdmin()) {
            log.info("keycloak.bootstrap-admin is off; leaving the Keycloak realm untouched.");
            return;
        }
        try {
            authService.ensureAdminAccount(adminName, adminEmail, adminPassword);
            log.info("Keycloak admin account is in place: {}", adminEmail);
        } catch (RuntimeException e) {
            log.warn("Could not bootstrap the Keycloak admin account ({}). "
                    + "Log in with an account that already exists in the realm.", e.getMessage());
        }
    }

    private void backfillCinemasFromHalls() {
        try {
            if (cinemaRepository.count() > 0) {
                return;
            }
            List<Hall> halls = hallRepository.findAll();
            if (halls.isEmpty()) {
                return;
            }

            Map<String, Cinema> byKey = new HashMap<>();
            for (Hall h : halls) {
                if (h.getCinema() != null) continue;
                String city = (h.getCity() == null) ? "" : h.getCity().trim();
                String addr = (h.getAddress() == null) ? "" : h.getAddress().trim();
                String key = (city + "|" + addr).toLowerCase();
                Cinema cinema = byKey.get(key);
                if (cinema == null) {
                    cinema = new Cinema();
                    cinema.setName(city.isBlank() ? (h.getName() + " Cinema") : city);
                    cinema.setAddress(addr.isBlank() ? null : addr);
                    cinema.setTotalHalls(0);
                    byKey.put(key, cinema);
                }
                cinema.setTotalHalls((cinema.getTotalHalls() == null ? 0 : cinema.getTotalHalls()) + 1);
            }

            if (byKey.isEmpty()) return;
            cinemaRepository.saveAll(byKey.values());

            for (Hall h : halls) {
                if (h.getCinema() != null) continue;
                String city = (h.getCity() == null) ? "" : h.getCity().trim();
                String addr = (h.getAddress() == null) ? "" : h.getAddress().trim();
                String key = (city + "|" + addr).toLowerCase();
                Cinema cinema = byKey.get(key);
                if (cinema != null) {
                    h.setCinema(cinema);
                    if (h.getCapacity() == null && h.getTotalSeats() != null) {
                        h.setCapacity(h.getTotalSeats());
                    }
                }
            }
            hallRepository.saveAll(halls);
            log.info("Backfilled {} cinemas and linked {} halls.", byKey.size(), halls.size());
        } catch (Exception ex) {
            log.warn("Cinema backfill skipped due to error: {}", ex.getMessage());
        }
    }
}
