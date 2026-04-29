# Movie Ticket Management System — Implementation Plan

> **Stack:** Java Spring Boot · Thymeleaf · PostgreSQL  
> **Methodology:** Agile / Scrum (2 Sprints)  
> **Reference:** MTMS SDLC Analysis Document

---

## Prerequisites

Before starting, make sure the following are ready:

- Java 17+ installed
- Maven installed
- PostgreSQL installed and running
- IDE ready (IntelliJ IDEA recommended)
- Create a new Spring Boot project via [start.spring.io](https://start.spring.io) with dependencies:
  - Spring Web
  - Spring Data JPA
  - Spring Security
  - Thymeleaf
  - Thymeleaf Extras Spring Security
  - PostgreSQL Driver
  - Lombok

---

## Sprint 1 — Core Foundation (Days 1–5)

### Step 1 — Project Setup & Database Configuration

- Configure `application.properties` with PostgreSQL connection
- Set `spring.jpa.hibernate.ddl-auto=update`
- Create the database `mtms` in PostgreSQL
- Verify the application starts without errors

---

### Step 2 — Enums

Create a package `enums` with the following:

- `Role` → `ADMIN`, `CUSTOMER`
- `MovieStatus` → `NOW_SHOWING`, `COMING_SOON`
- `SeatType` → `NORMAL`, `COUPLE`
- `ShowtimeSeatStatus` → `AVAILABLE`, `BOOKED`
- `BookingStatus` → `PENDING`, `CONFIRMED`, `CANCELLED`
- `PaymentStatus` → `PENDING`, `PAID`, `FAILED`
- `TicketStatus` → `ACTIVE`, `USED`, `CANCELLED`

---

### Step 3 — Entity Classes

Create a package `model` with the following JPA entities:

- `User` — id, name, email, password, role
- `Movie` — id, title, description, genre, durationMinutes, status
- `Hall` — id, name, totalSeats (default 60)
- `Seat` — id, hall (FK), seatCode, seatType, rowNumber
- `Showtime` — id, movie (FK), hall (FK), showDate, showTime, normalPrice, couplePrice
- `ShowtimeSeat` — id, showtime (FK), seat (FK), status
- `Booking` — id, user (FK), showtime (FK), totalAmount, status, bookedAt
- `Payment` — id, booking (FK, OneToOne), amount, method, status, paidAt
- `Ticket` — id, booking (FK), seat (FK), ticketCode, status, issuedAt

---

### Step 4 — Repository Interfaces

Create a package `repository` with one JPA repository per entity:

- `UserRepository` — `findByEmail(String email)`
- `MovieRepository` — `findByStatus(MovieStatus status)`
- `HallRepository`
- `SeatRepository` — `findByHall(Hall hall)`, `findByHallAndSeatType(Hall hall, SeatType type)`
- `ShowtimeRepository` — `findByMovie(Movie movie)`
- `ShowtimeSeatRepository` — `findByShowtime(Showtime showtime)`, `findByShowtimeAndSeat(Showtime s, Seat seat)`
- `BookingRepository` — `findByUser(User user)`
- `PaymentRepository`
- `TicketRepository` — `findByTicketCode(String code)`, `findByBooking(Booking booking)`

---

### Step 5 — Spring Security Configuration

- Create `CustomUserDetailsService` that loads user by email and maps role to Spring Security authority
- Create `SecurityConfig` that:
  - Permits public access to `/`, `/movies`, `/movies/**`, `/register`, `/login`, `/css/**`, `/js/**`
  - Restricts `/admin/**` to `ROLE_ADMIN`
  - Restricts all other routes to authenticated users
  - Sets `/login` as the login page
  - Configures logout redirect to `/login?logout`
  - Registers `BCryptPasswordEncoder` as a bean

---

### Step 6 — Service Layer (Auth, Movie, Hall)

- `UserService` — register new customer (encode password, set role CUSTOMER, save)
- `MovieService` — save, findAll, findById, findByStatus, update, delete, count
- `HallService`:
  - Save hall
  - After saving a hall, auto-generate 60 `Seat` records:
    - Rows 1–2: `SeatType.COUPLE` (e.g. A1–A10, B1–B10)
    - Rows 3 onwards: `SeatType.NORMAL`
    - Seat code format: row letter + seat number (A1, B3, C5…)

---

### Step 7 — Auth Controllers & Pages

- `AuthController`:
  - `GET /login` → login page
  - `GET /register` → register page
  - `POST /register` → save user, redirect to `/login`
- Pages:
  - `auth/login.html` — email + password form
  - `auth/register.html` — name, email, password form

---

### Step 8 — Movie Browsing (Public Pages)

- `MovieController`:
  - `GET /movies` → list all NOW_SHOWING movies
  - `GET /movies/{id}` → movie detail + list of available showtimes grouped by date
- Pages:
  - `movies/list.html` — movie cards with title, genre, duration
  - `movies/detail.html` — full movie info + showtime list with date, time, hall, prices, and Select button

---

### Step 9 — Admin: Movies, Halls & Showtimes

- `ShowtimeService`:
  - Save showtime
  - After saving a showtime, auto-generate 60 `ShowtimeSeat` records (one per seat in the hall), all set to `AVAILABLE`
  - findByMovie, findAll, deleteById

- `AdminController`:
  - `GET /admin/dashboard` → pass totals to view
  - Movie CRUD: list, new form, save, edit form, update, delete
  - Hall CRUD: list, new form, save, edit form, update, delete
  - Showtime CRUD: list, new form (select movie + hall + date + time + prices), save, delete
  - `GET /admin/bookings` → all bookings
  - `GET /admin/users` → all users

- Pages:
  - `admin/dashboard.html`
  - `admin/movie-list.html`, `admin/movie-form.html`
  - `admin/hall-list.html`, `admin/hall-form.html`
  - `admin/showtime-list.html`, `admin/showtime-form.html`
  - `admin/booking-list.html`
  - `admin/user-list.html`

---

## Sprint 2 — Seat Selection, Booking & E-Ticket (Days 6–12)

### Step 10 — Seat Map Page

- `BookingController`:
  - `GET /bookings/seats/{showtimeId}` → fetch all 60 ShowtimeSeats for the showtime, pass to view

- Page `booking/seat-map.html`:
  - Render a CSS grid showing all 60 seats
  - Visually distinguish: NORMAL seat, COUPLE seat, BOOKED seat (greyed out, not clickable)
  - Allow customer to click and select multiple seats
  - Show running total price as seats are selected (normal price vs couple price)
  - Submit selected seat IDs to checkout

---

### Step 11 — Booking & Payment Service

- `BookingService`:
  - Validate all selected seats are still `AVAILABLE` for the showtime (reject if any is BOOKED)
  - Validate couple seats are selected in pairs (reject single couple seat)
  - Calculate total amount (count normal seats × normalPrice + count couple seats × couplePrice)
  - Save booking with status `PENDING`
  - Return saved booking

- `PaymentService`:
  - Create mock payment linked to booking (method: `MOCK_CARD`, status: `PAID`, paidAt: now)
  - Update booking status to `CONFIRMED`

- `TicketService`:
  - For each selected seat, generate one `Ticket` with a unique UUID ticket code, status `ACTIVE`, issuedAt now
  - Update each `ShowtimeSeat` status to `BOOKED`

---

### Step 12 — Checkout & Confirmation Flow

- `BookingController`:
  - `POST /bookings/checkout/{showtimeId}` → receive selected seat IDs → run booking + payment + ticket generation → redirect to confirmation
  - `GET /bookings/confirmation/{bookingId}` → show confirmation page
  - `GET /bookings/my-bookings` → list all bookings for logged-in user

- Pages:
  - `booking/checkout.html` — seat summary, mock card form (card number, expiry, CVV — display only), total price, Confirm Payment button
  - `booking/confirmation.html` — success message, booking reference, movie, showtime, seats, total paid, link to My Bookings
  - `booking/my-bookings.html` — table of all user bookings with movie, showtime, seats, status, View Ticket links

---

### Step 13 — E-Ticket Page

- `TicketController`:
  - `GET /tickets/{ticketCode}` → find ticket by code, load booking, showtime, movie, seat, user

- Page `ticket/view.html` — printable layout showing:
  - Movie title, showtime date and time, hall name
  - Customer name
  - Seat code and seat type (Normal / Couple)
  - Ticket code (large and prominent)
  - Booking reference
  - Print button using `window.print()`
  - `@media print` CSS to hide navbar and buttons when printing

---

### Step 14 — Shared Layout & Navigation

- Create `layout/base.html` as a Thymeleaf fragment:
  - Navbar: Movies, My Bookings (customers), Admin (admins), Login / Logout
  - Bootstrap 5 CDN in `<head>`
  - All pages use `th:replace` or `th:insert` to include this layout
- Apply consistent Bootstrap styling across all pages
- Style the seat map with a CSS grid (use color coding for seat types and availability)

---

### Step 15 — Final Testing & Cleanup

- Test full customer flow: register → login → browse movies → select showtime → seat map → checkout → confirmation → e-ticket → print
- Test full admin flow: login → create movie → create hall → create showtime → view bookings → view dashboard
- Test seat locking: after booking, confirm booked seats appear unavailable to the next customer
- Test couple seat validation: selecting a single couple seat should be rejected
- Test access control: customer cannot reach `/admin/**`, guest cannot reach `/bookings/**`
- Add basic error pages (`404.html`, `error.html`)
- Clean up unused code
- Push final code to GitHub

---

## File Structure Reference

```
src/main/java/com/yourapp/mtms/
  ├── config/
  │   └── SecurityConfig.java
  ├── controller/
  │   ├── AuthController.java
  │   ├── MovieController.java
  │   ├── AdminController.java
  │   ├── BookingController.java
  │   └── TicketController.java
  ├── enums/
  │   ├── Role.java
  │   ├── MovieStatus.java
  │   ├── SeatType.java
  │   ├── ShowtimeSeatStatus.java
  │   ├── BookingStatus.java
  │   ├── PaymentStatus.java
  │   └── TicketStatus.java
  ├── model/
  │   ├── User.java
  │   ├── Movie.java
  │   ├── Hall.java
  │   ├── Seat.java
  │   ├── Showtime.java
  │   ├── ShowtimeSeat.java
  │   ├── Booking.java
  │   ├── Payment.java
  │   └── Ticket.java
  ├── repository/
  │   ├── UserRepository.java
  │   ├── MovieRepository.java
  │   ├── HallRepository.java
  │   ├── SeatRepository.java
  │   ├── ShowtimeRepository.java
  │   ├── ShowtimeSeatRepository.java
  │   ├── BookingRepository.java
  │   ├── PaymentRepository.java
  │   └── TicketRepository.java
  └── service/
      ├── CustomUserDetailsService.java
      ├── UserService.java
      ├── MovieService.java
      ├── HallService.java
      ├── ShowtimeService.java
      ├── BookingService.java
      ├── PaymentService.java
      └── TicketService.java

src/main/resources/
  ├── templates/
  │   ├── layout/
  │   │   └── base.html
  │   ├── auth/
  │   │   ├── login.html
  │   │   └── register.html
  │   ├── movies/
  │   │   ├── list.html
  │   │   └── detail.html
  │   ├── admin/
  │   │   ├── dashboard.html
  │   │   ├── movie-list.html
  │   │   ├── movie-form.html
  │   │   ├── hall-list.html
  │   │   ├── hall-form.html
  │   │   ├── showtime-list.html
  │   │   ├── showtime-form.html
  │   │   ├── booking-list.html
  │   │   └── user-list.html
  │   ├── booking/
  │   │   ├── seat-map.html
  │   │   ├── checkout.html
  │   │   ├── confirmation.html
  │   │   └── my-bookings.html
  │   └── ticket/
  │       └── view.html
  └── application.properties
```

---

## Build Order Summary

| Step | What to Build | Sprint |
|------|--------------|--------|
| 1 | Project setup & DB config | 1 |
| 2 | Enums | 1 |
| 3 | Entity classes | 1 |
| 4 | Repository interfaces | 1 |
| 5 | Spring Security config | 1 |
| 6 | UserService, MovieService, HallService (with auto seat generation) | 1 |
| 7 | Auth controllers & pages | 1 |
| 8 | Public movie browsing pages | 1 |
| 9 | Admin movie, hall & showtime management | 1 |
| 10 | Seat map page | 2 |
| 11 | BookingService, PaymentService, TicketService | 2 |
| 12 | Checkout & confirmation flow | 2 |
| 13 | E-ticket page | 2 |
| 14 | Shared layout & navigation | 2 |
| 15 | Final testing & cleanup | 2 |
