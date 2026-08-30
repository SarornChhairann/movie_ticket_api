# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A Spring Boot 3.5.9 / Java 17 movie ticket booking and cinema management system ("TicketManagementSystem", package `co.tkm.ticketmanagementsystem`). Packaged as a WAR. PostgreSQL in production/dev, H2 (Postgres-compatible mode) for tests. It is a **pure JSON REST API** — every controller is a `@RestController` documented through springdoc-openapi/Swagger UI. There is no server-rendered UI; the Thymeleaf templates were removed when the controllers were converted to JSON.

## Common commands

Run everything through the Maven wrapper (`mvnw.cmd` on Windows / `./mvnw` elsewhere) — do not assume a global `mvn` install.

```
mvnw.cmd clean install          # full build + tests
mvnw.cmd test                   # run all tests
mvnw.cmd test -Dtest=BookingServiceTest                 # single test class
mvnw.cmd test -Dtest=BookingServiceTest#someMethodName   # single test method
mvnw.cmd spring-boot:run         # run the app locally (port 8010 by default)
```

```
docker compose up -d db keycloak    # Postgres on 5433 + Keycloak on 8080, both needed to run the app
```

Configuration is YAML: `src/main/resources/application.yml` (dev) and `src/test/resources/application.yml` (tests). Tests run against H2 in Postgres-compatibility mode and need neither Postgres nor Keycloak. The dev datasource points at `jdbc:postgresql://localhost:5433/spring_boot_ticket`, and auth needs a Keycloak at `http://localhost:8080` serving the `ticket-realm` — both are in `docker-compose.yml`. Note `app.seed.force=true` in the dev config: every start wipes and reseeds the demo data.

Swagger UI is available at `/swagger-ui.html` (`/v3/api-docs` for the raw OpenAPI doc) once the app is running.

## Architecture

Standard layered package structure under `co.tkm.ticketmanagementsystem`:

- `config/` — Spring `@Configuration` classes (security, web/CORS/static resources, WebSocket, Swagger/OpenAPI, JPA) plus startup data machinery (see Data seeding below).
- `controller/` — All `@RestController` (JSON in/out). Business-domain controllers are tagged with springdoc `@Tag`/`@Operation` so they appear grouped in Swagger UI.
- `service/` — Business logic; controllers stay thin and delegate here.
- `repository/` — Spring Data JPA repositories.
- `model/` — JPA entities. Most extend `AuditableEntity` for created/updated tracking.
- `dto/` — Cross-cutting request payloads not tied to one controller (e.g. `CheckoutData`, `RegisterForm`). Most controllers instead nest small `record` request/response types directly in the controller class — prefer that pattern for new endpoint-local payloads rather than adding to `dto/`.
- `enums/` — Domain status/type enums (`BookingStatus`, `TicketStatus`, `PaymentStatus`, `Role`, `SeatType`, `MovieStatus`, etc.).
- `security/` — `EncryptedStringConverter`, a JPA `AttributeConverter` used to transparently encrypt sensitive entity fields at rest.

### Controller conventions (read before adding/editing an endpoint)

Every controller returns JSON. Two important patterns to preserve:

- **Never serialize JPA entities that carry uninitialized lazy relations.** Most `@ManyToOne` associations are `FetchType.LAZY` and `spring.jpa.open-in-view=false`, so Jackson serializing a raw entity outside a transaction throws `LazyInitializationException`. Endpoints that touch related data return purpose-built `record` response DTOs mapped inside the controller (see `BookingController`, `TicketController`, `AdminController`), often under a method-level `@Transactional(readOnly = true)` so the mapping runs while the session is open. Returning a bare entity is only safe when it has no lazy relations that get walked. Simple flat entities (`Movie`, `Cinema`, `FoodItem`, `PromoCode`, `Notification`, `SystemSetting`) are returned directly.
- **Endpoint-local request/response DTOs are `record` types nested in the controller.** Follow the existing controllers rather than creating top-level DTO classes.

Errors are handled centrally by `ApiExceptionHandler` (`@RestControllerAdvice`): `IllegalArgumentException` → 400, `IllegalStateException` → 409, `FoodItemImageStorageException`/multipart size issues → 400/413. Throw those from controllers/services instead of returning error payloads by hand. (`GlobalExceptionHandler` still exists but is vestigial now that there are no views.)

### Security (Keycloak)

Authentication is **Keycloak**, not this app — see [KEYCLOAK.md](KEYCLOAK.md) for the full picture. `SecurityConfig` makes the app an **OAuth2 resource server**: stateless, no session, no HTTP Basic, no local password hash. Clients call `POST /login` (also `/api/auth/login`), which runs Keycloak's password grant behind the scenes and returns `access_token`/`refresh_token`; every other request carries `Authorization: Bearer <access_token>`. `POST /register` creates the account through Keycloak's Admin REST API. Also available: `POST /api/auth/refresh` and `POST /logout` (revokes the refresh token).

- **Roles.** Keycloak realm roles are upper-cased into authorities, so realm `admin` → `ROLE_ADMIN` and realm `customer` → `ROLE_CUSTOMER`; `hasRole("ADMIN")` rules are unchanged. The realm lives in `keycloak/realm-export.json` and is imported by the `keycloak` service in `docker-compose.yml`.
- **The principal name is the user's email**, on purpose: every controller/service resolves the current user with `authentication.getName()` against `users.email`, so that could not change.
- **`users` is a mirror of the realm.** `KeycloakUserSyncFilter` runs right after token validation and creates/realigns the local row (so an account made straight in the Keycloak console works), and rejects `SUSPENDED`/`BANNED` accounts — the check that used to live in `CustomUserDetailsService`. `points` and `status` are the only user columns this app still owns; `password` holds a placeholder and is never read.
- **Keycloak is only reached from `KeycloakAdminClient`**; `AuthService` holds the flows (login, register, refresh, change password, admin bootstrap) and mirrors the result locally. Both turn Keycloak failures into `ResponseStatusException`, which `ApiExceptionHandler` renders in the usual `{error, type}` shape.
- Public routes: `/`, `/movies/**`, booking browsing/checkout/confirmation, `/tickets/**`, `POST /login|/register|/logout`, `/api/auth/**`, static assets, `/uploads/**`, `/api/**`, `/ws/**`, and the Swagger paths. `/bookings/my-bookings`, `/bookings/cancel/**`, and `/profile/**` require authentication; `/admin/**` requires `ROLE_ADMIN`.
- **CSRF is disabled globally** — a bearer-token request carries no ambient credential to forge, the app has no HTML forms, and cross-origin JSON is gated by the CORS allow-list in `WebConfig`. Spring Security's default `LogoutFilter` is disabled too, or it would answer `POST /logout` before `AuthController` does.

### Data seeding & schema

Three cooperating pieces run at startup, controlled by `app.seed.*` properties in `application.yml`:
- `AdminSeeder` — ensures the admin account (`APP_ADMIN_EMAIL`/`APP_ADMIN_PASSWORD`) exists in **Keycloak** (realigning its password and `admin` role) and mirrors it into `users`. The Keycloak half is skipped when `keycloak.bootstrap-admin=false` and is non-fatal if Keycloak is unreachable.
- `DemoDataSeeder` / `DatabaseSeederService` — generate demo movies, halls, showtimes, and customers (via `javafaker`) when `app.seed.enabled=true`; `app.seed.force=true` reseeds even if data exists. Seed volume/shape is tunable via `app.seed.halls`, `app.seed.days`, `app.seed.showtimesPerHallPerDay`, `app.seed.customers`, `app.seed.randomSeed`.
- `SchemaMigrationRunner` — hand-rolled startup migration logic (Hibernate `ddl-auto=update` handles additive schema changes; this covers the rest).

### Real-time seats & WebSocket

`WebSocketConfig` exposes a STOMP endpoint at `/ws` (SockJS fallback) with a simple broker on `/topic`. `SeatRealtimeService` and `SeatHoldApiController`/`SeatHoldRepository` implement short-lived seat holds during checkout so two users can't book the same seat concurrently; seat status changes are pushed over `/topic`.

### File uploads

Movie posters and food item images are stored on disk under `app.uploads.movies-dir` / `app.uploads.food-items-dir` (defaults `uploads/movies`, `uploads/food-items`) and served back via resource handlers registered in `WebConfig` at `/uploads/movies/**` and `/uploads/food-items/**`. `FoodItemImageStorageService` / `FoodItemImageStorageException` handle validation (size capped by `app.uploads.food-items.max-bytes` and `spring.servlet.multipart.max-file-size`).

### Other notables

- QR codes for tickets: `QrCodeService` (zxing). Ticket PDFs: `PdfTicketService` (Apache PDFBox).
- `WebConfig` also registers permissive CORS for `http://localhost:8081` — relevant if a separate frontend dev server is involved.
- Email (booking confirmations, etc.) goes through `EmailService` / the `JavaMailSender` bean in `WebConfig`; real delivery requires `spring.mail.username`/`password` (blank by default, so mail is a no-op locally unless configured).
