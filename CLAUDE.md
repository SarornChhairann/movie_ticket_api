# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

### Build
```bash
./mvnw clean package -DskipTests
```

### Run
```bash
./mvnw spring-boot:run
```

### Test
```bash
./mvnw test
# Single test class:
./mvnw test -Dtest=ClassName
```

## Database

Requires a running PostgreSQL instance. Default config in `application.properties`:
- DB: `mtms` on `localhost:5432`
- Credentials: overridable via `DB_URL`, `DB_USER`, `DB_PASSWORD` env vars
- DDL: `hibernate.ddl-auto=update` (Hibernate auto-creates/updates schema)

On startup, `SchemaMigrationRunner` runs a raw SQL migration to ensure `bookings.user_id` is nullable (supports guest checkouts).

Admin credentials are seeded on startup via `AdminSeeder` using `APP_ADMIN_EMAIL` / `APP_ADMIN_PASSWORD` / `APP_ADMIN_NAME` from `application.properties`.

## Architecture

Spring Boot 3.5 MVC application (packaged as WAR) with Thymeleaf templates and Spring Security.

**Package structure:** `co.tkm.ticketmanagementsystem`
- `config/` — Security, admin seeding, schema migration, web config, role-based redirect after login
- `controller/` — MVC controllers (one per domain area)
- `service/` — Business logic layer
- `repository/` — Spring Data JPA repositories
- `model/` — JPA entities
- `dto/` — `CheckoutData`, `RegisterForm`
- `enums/` — `Role`, `BookingStatus`, `PaymentStatus`, `TicketStatus`, `SeatType`, `ShowtimeSeatStatus`, `MovieStatus`

**Domain model:**
- `Hall` has many `Seat`s (with `SeatType`: NORMAL or COUPLE)
- `Movie` has many `Showtime`s; each `Showtime` belongs to a `Hall` and has `normalPrice` / `couplePrice`
- `ShowtimeSeat` is the join between `Showtime` and `Seat`, tracking `ShowtimeSeatStatus` (AVAILABLE/BOOKED)
- `Booking` belongs to a `User` (nullable for guests) and a `Showtime`; triggers `Payment` and `Ticket` creation
- `Ticket` is issued per seat per booking with a `ticketCode`

**Booking flow:**
1. User selects seats on seat map (`/bookings/seats/{showtimeId}`)
2. Checkout preview validates seat availability and calculates total (`/bookings/checkout/{showtimeId}`)
3. POST to confirm: seats are locked with `SELECT FOR UPDATE`, booking saved, mock payment processed, tickets issued
4. Redirect to confirmation page

**Couple seat validation:** COUPLE seats must be selected in adjacent odd-even pairs (e.g., C1+C2, C3+C4). Logic lives in `BookingService`.

**Auth & roles:**
- Two roles: `ADMIN`, `USER`
- `RoleBasedAuthenticationSuccessHandler` redirects ADMIN to `/admin/dashboard`, USER to `/`
- Public routes: `/`, `/movies/**`, `/bookings/seats/**`, `/bookings/checkout/**`, `/bookings/confirmation/**`, `/tickets/**`, `/register`, `/login`
- `/admin/**` requires `ROLE_ADMIN`

**Templates:** Thymeleaf with two layouts — `layout/base.html` (public-facing) and `layout/admin-base.html` (AdminLTE-based admin panel). Movie images are uploaded to `uploads/movies/` and served via `WebConfig`.
