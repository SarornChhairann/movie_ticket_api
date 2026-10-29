# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A Spring Boot 3.5.9 / Java 17 movie ticket booking and cinema management system ("TicketManagementSystem", package `co.tkm.ticketmanagementsystem`). Packaged as a WAR. PostgreSQL in production/dev, H2 (Postgres-compatible mode) for tests. It is a **pure JSON REST API** — every controller is a `@RestController` documented through springdoc-openapi/Swagger UI. There is no server-rendered UI; the Thymeleaf templates were removed when the controllers were converted to JSON.

## Common commands

Run everything through the Maven wrapper (`./mvnw` on macOS/Linux, `mvnw.cmd` on Windows) — do not assume a global `mvn` install. There is no linter/formatter configured.

```
./mvnw clean install                                   # full build + tests
./mvnw test                                            # run all tests
./mvnw test -Dtest=BookingServiceTest                  # single test class
./mvnw test -Dtest=BookingServiceTest#someMethodName   # single test method
./mvnw spring-boot:run                                 # run the app locally (port 8010 by default)
```

Tests live under `src/test/java/.../service/` (`BookingServiceTest`, `SeatHoldServiceTest`, `ShowtimeServiceAdminRulesTest`) plus a context-load test.

```
docker compose up -d db rabbitmq redis   # Postgres 5433, RabbitMQ 5672 (UI 15672), Redis 6379
docker compose up --build           # full stack including the app container (see README-Docker.md)
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build   # production: Caddy TLS, internal data network (README-Docker.md)
```

Configuration lives in **`.env` at the project root** — one file read by both `docker compose` (for `${VAR}` substitution in `docker-compose.yml`) and the app itself, since `application.yml` declares `spring.config.import: optional:file:./.env[.properties]`. `src/main/resources/application.yml` is now only the wiring: every value is a `${VAR:default}` placeholder pointing at a key in `.env`, with the same value as its default so a checkout without an `.env` still boots. Real environment variables outrank the file, which is how the container overrides `localhost` with `db:`/`redis:`. `.env` is git-ignored; `.env.example` is the committed template. Tests use `src/test/resources/application.yml`, read no `.env`, and run against H2 in Postgres-compatibility mode, so they need no Postgres. The dev datasource points at `jdbc:postgresql://localhost:5433/spring_boot_ticket` (see `docker-compose.yml`). Note `APP_SEED_FORCE=true` in `.env`: every start wipes and reseeds the demo data.

Swagger UI is available at `/swagger-ui.html` (`/v3/api-docs` for the raw OpenAPI doc) once the app is running.

## Architecture

Standard layered package structure under `co.tkm.ticketmanagementsystem`:

- `config/` — Spring `@Configuration` classes (security, web/CORS/static resources, WebSocket, Swagger/OpenAPI, JPA) plus startup data machinery (see Data seeding below).
- `controller/` — All `@RestController` (JSON in/out). Admin endpoints (`/admin/**`) live in the `controller/admin/` subpackage, one controller per domain. Business-domain controllers are tagged with springdoc `@Tag`/`@Operation` so they appear grouped in Swagger UI.
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

### Security (built-in JWT auth)

The app does its own authentication; there is no external identity server. `SecurityConfig` makes it an **OAuth2 resource server** over its own tokens: stateless, no session, no HTTP Basic. Clients call `POST /login` (also `/api/auth/login`) with email/password and get `access_token`/`refresh_token`/`expires_in`/`token_type` back (OAuth2 field names, which the mobile app and admin portal read); every other request carries `Authorization: Bearer <access_token>`. Also: `POST /register`, `POST /api/auth/refresh`, `POST /logout` (revokes the refresh token), `POST /profile/change-password`.

- **Passwords** are BCrypt hashes in `users.password` (`PasswordEncoder` bean in `JwtConfig`). `User.NO_PASSWORD` (`"!"`) marks an account that cannot log in (e.g. seeded demo customers; only `customer@gmail.com`/`customer123` is seeded with a real password).
- **Access tokens** are HS256 JWTs issued by `TokenService` and verified by the `JwtDecoder` in `JwtConfig`, both keyed by `app.auth.jwt-secret` (`APP_JWT_SECRET`, ≥ 32 bytes; startup fails otherwise). Claims are in `TokenClaims`: `sub` = email, `roles` = `["ADMIN"]` / `["CUSTOMER"]`, mapped to `ROLE_*` authorities. TTL `app.auth.access-token-ttl` (15m).
- **Refresh tokens** are random opaque strings stored as SHA-256 hashes in `refresh_tokens` (`RefreshToken`), single-use (rotated on every refresh), TTL `app.auth.refresh-token-ttl` (7d). Logout deletes one; a password change or a SUSPENDED/BANNED status deletes all of a user's.
- **The principal name is the user's email**, on purpose: every controller/service resolves the current user with `authentication.getName()` against `users.email`.
- `AccountStatusFilter` runs right after token validation and rejects tokens whose user no longer exists (401) or is `SUSPENDED`/`BANNED` (403, `code: ACCOUNT_BLOCKED`), since an access token stays valid until it expires.
- `AuthService` holds the flows (login, register, refresh, logout, change password, admin bootstrap) and throws `ResponseStatusException`/`ApiException`, which `ApiExceptionHandler` renders in the usual `{error, type, code?}` shape.
- Public routes: `/`, `/movies/**`, booking browsing/checkout/confirmation (confirmation is still `permitAll`, but `BookingService.findForReader` answers only the owning user or the creating `X-Session-Id`, else 404 `BOOKING_NOT_FOUND`), `/tickets/**`, `POST /login|/register|/logout`, `/api/auth/**`, static assets, `/uploads/**`, `/api/**`, `/ws/**`, and the Swagger paths. `/bookings/my-bookings`, `/bookings/cancel/**`, and `/profile/**` require authentication; `/admin/**` requires `ROLE_ADMIN`.
- **CSRF is disabled globally** — a bearer-token request carries no ambient credential to forge, the app has no HTML forms, and cross-origin JSON is gated by the CORS allow-list in `WebConfig`. Spring Security's default `LogoutFilter` is disabled too, or it would answer `POST /logout` before `AuthController` does.

### Data seeding & schema

Three cooperating pieces run at startup, controlled by `app.seed.*` properties in `application.yml`:
- `AdminSeeder` — ensures the admin account (`APP_ADMIN_EMAIL`/`APP_ADMIN_PASSWORD`) exists in `users`, realigning its password, `ADMIN` role and `ACTIVE` status on every start (`AuthService.ensureAdminAccount`).
- `DemoDataSeeder` / `DatabaseSeederService` — generate demo movies, halls, showtimes, and customers (via `javafaker`) when `app.seed.enabled=true`; `app.seed.force=true` reseeds even if data exists. Seed volume/shape is tunable via `app.seed.halls`, `app.seed.days`, `app.seed.showtimesPerHallPerDay`, `app.seed.customers`, `app.seed.randomSeed`.
- `SchemaMigrationRunner` — hand-rolled startup migration logic (Hibernate `ddl-auto=update` handles additive schema changes; this covers the rest).

### Real-time seats & WebSocket

`WebSocketConfig` exposes a STOMP endpoint at `/ws` (SockJS fallback) with a simple broker on `/topic`. `SeatRealtimeService` and `SeatHoldApiController`/`SeatHoldRepository` implement short-lived seat holds during checkout so two users can't book the same seat concurrently; seat status changes are pushed over `/topic`.

### File uploads

Movie posters and food item images are stored on disk under `app.uploads.movies-dir` / `app.uploads.food-items-dir` (defaults `uploads/movies`, `uploads/food-items`) and served back via resource handlers registered in `WebConfig` at `/uploads/movies/**` and `/uploads/food-items/**`. `FoodItemImageStorageService` / `FoodItemImageStorageException` handle validation (size capped by `app.uploads.food-items.max-bytes` and `spring.servlet.multipart.max-file-size`).

### Other notables

- QR codes for tickets: `QrCodeService` (zxing). Ticket PDFs: `PdfTicketService` (Apache PDFBox).
- The client is a separate React app on `http://localhost:8081` (see `ARCHITECTURE.md`). `WebConfig`'s CORS allow-list comes from `app.cors.allowed-origins` (default `http://localhost:8081`) — add origins there, not in code.
- `MovieBrowseCacheService` (1h TTL) behind `MovieBrowseApiController` lives in **Redis** (`movie-browse:*` JSON keys, shared by every instance) when `app.redis.enabled=true`, else, or while Redis is unreachable, in memory. `MovieService`, `HallService`, and `ShowtimeService` call `evictAll()`; any other write that changes what the browse endpoints return must evict it too, or stale data is served for up to an hour. `LookupRateLimiter` keeps its misses in Redis too (`lookup-failures:{client}` sorted sets), with the same memory fallback.
- Booking mail goes **through RabbitMQ**: `BookingService` calls `BookingMailPublisher` after commit with only the booking id; `BookingMailListener` consumes `booking.mail` and `BookingMailHandler` reloads the booking and calls `EmailService`. A failed send is retried by the listener (`spring.rabbitmq.listener.simple.retry`) and then dead-lettered to `booking.mail.dlq`. With `app.mail.queue.enabled=false` (tests) or the broker down, the publisher sends in-process; mail never fails a booking. The confirmation is HTML with one inline (`cid:`) QR per ticket; a guest must give a valid email at checkout (`lockAndValidate`). Real delivery requires `spring.mail.username`/`password` (blank by default, so nothing is sent locally); `From` is the username.

## Companion docs

- `ARCHITECTURE.md` — DFDs and how UI / API / Postgres fit together (older sections still describe the former Keycloak setup).
- `README-Docker.md` — Compose stack.
- `postman/Ticket-Auth.postman_collection.json` — auth flow requests; `index.html` — static API payload reference page.
