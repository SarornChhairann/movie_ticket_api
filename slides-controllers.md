<!--
Presentation deck: Controllers & Endpoints
Slides are separated by "---". View as slides with the "Marp for VS Code" extension,
or just scroll it as a normal document.
-->

# 🎟️ Ticket Management System
## Controllers & REST Endpoints

A Spring Boot **JSON REST API** — no HTML pages, every response is JSON.

**25 controllers · 70+ endpoints · documented in Swagger UI**

---

## How every request flows

```
HTTP Request  →  Controller  →  Service  →  Repository  →  Database
                (JSON in/out)  (logic)     (Spring Data)   (PostgreSQL)
```

**Conventions we follow**
- `@RestController` + `@RequestMapping("/base-path")` on every controller
- `@GetMapping / @PostMapping / @DeleteMapping` map HTTP verbs to methods
- Controllers stay **thin** — all business logic lives in the `service/` layer
- Every endpoint appears automatically in **Swagger UI** at `/swagger-ui.html`

---

## Controllers at a glance

| Area | Controllers | Access |
|:--|:--|:--|
| 🌐 Public browsing | Home, Movies, Movie Browse, Showtimes, Reminders | Anyone |
| 🔐 Auth & Tickets | Auth, Tickets | Anyone |
| 🎫 Booking flow | Bookings, Seat Holds | Public + logged-in |
| 👤 User profile | Profile | Logged-in |
| 🛠️ Admin back-office | Dashboard, Movies, Halls, Cinemas, Showtimes, Bookings, Users, Food, Promo, Reports, Notifications, Settings, Check-In | `ROLE_ADMIN` |

---

# 🌐 Public — Browsing

---

## Home & Movies

**`HomeController`** — landing page data
| HTTP | Path | Purpose |
|:--|:--|:--|
| `GET` | `/` | Home data: hero, now-showing, coming-soon, cinemas |

**`MovieController`** — `/movies`
| HTTP | Path | Purpose |
|:--|:--|:--|
| `GET` | `/movies` | List movies (filter by genre / language / format / rating / search / city) |
| `GET` | `/movies/{id}` | Movie detail + showtimes grouped by date |

---

## Movie Browse & Showtimes (helper APIs)

**`MovieBrowseApiController`** — `/api/movies`
| HTTP | Path | Purpose |
|:--|:--|:--|
| `GET` | `/api/movies?type=` | Browse *now-showing* (by date) or *coming-soon* (by month), cached |

**`ShowtimeApiController`** — `/api/showtimes`
| HTTP | Path | Purpose |
|:--|:--|:--|
| `GET` | `/api/showtimes/search` | Find showtimes for a movie + date (by cinema or city) |
| `GET` | `/api/showtimes/available-movies` | Movie IDs that have showtimes on a date |

---

# 🔐 Public — Auth & Tickets

---

## Authentication & Reminders

**`AuthController`** — session-cookie login
| HTTP | Path | Purpose |
|:--|:--|:--|
| `POST` | `/login` | Log in with email + password (starts a session) |
| `POST` | `/register` | Register a new customer account |

**`MovieReminderApiController`** — `/api/reminders`
| HTTP | Path | Purpose |
|:--|:--|:--|
| `POST` | `/api/reminders` | "Remind me" by email when a coming-soon movie releases |

---

## Tickets — `/tickets`

**`TicketController`**
| HTTP | Path | Purpose |
|:--|:--|:--|
| `GET` | `/tickets?code=` | Find a ticket by its code (query param) |
| `GET` | `/tickets/{ticketCode}` | Get a ticket by its code (path param) |
| `GET` | `/tickets/{ticketCode}/qr` | Ticket QR code as a **PNG image** |
| `GET` | `/tickets/{ticketCode}/pdf` | Download the ticket as a **PDF** |

---

# 🎫 Booking Flow

---

## Bookings — `/bookings`

**`BookingController`**
| HTTP | Path | Purpose |
|:--|:--|:--|
| `GET`  | `/bookings/seats/{showtimeId}` | Seat map for a showtime (shows held seats) |
| `GET`  | `/bookings/checkout/{showtimeId}` | Preview totals + place a temporary seat hold |
| `POST` | `/bookings/promo/validate` | Validate a promo code, return discount % |
| `POST` | `/bookings/checkout/{showtimeId}` | Confirm the booking for held seats |
| `POST` | `/bookings/cancel/{bookingId}` | Cancel one of my bookings |
| `GET`  | `/bookings/confirmation/{bookingId}` | Confirmation details + issued tickets |
| `GET`  | `/bookings/my-bookings` | List my bookings |

---

## Seat Holds (real-time) — `/api/seat-holds`

**`SeatHoldApiController`** — stops two people booking the same seat

| HTTP | Path | Purpose |
|:--|:--|:--|
| `POST` | `/api/seat-holds/hold` | Hold seats while I check out |
| `POST` | `/api/seat-holds/release` | Release seats I no longer want |
| `GET`  | `/api/seat-holds/state` | Which seats are held (by me vs others) |

> Seat changes are pushed live to other users over WebSocket (`/ws` → `/topic`).

---

# 👤 User Profile

---

## Profile — `/profile`

**`UserController`** *(login required)*
| HTTP | Path | Purpose |
|:--|:--|:--|
| `GET`  | `/profile` | Get my profile |
| `POST` | `/profile` | Update my name & phone |
| `POST` | `/profile/change-password` | Change my password |

---

# 🛠️ Admin Back-Office
### Every route below requires `ROLE_ADMIN`

*(The old giant `AdminController` was split into one focused controller per resource.)*

---

## Dashboard & Catalog (1/2)

**`AdminDashboardController`** — `/admin/dashboard`
| HTTP | Path | Purpose |
|:--|:--|:--|
| `GET` | `/admin/dashboard` | Counts, revenue, top movies, recent bookings |

**`AdminMovieController`** — `/admin/movies`
| HTTP | Path | Purpose |
|:--|:--|:--|
| `GET` | `/admin/movies` | List all movies |
| `GET` | `/admin/movies/{id}` | Get a movie |
| `POST` | `/admin/movies` | Create / update (+ poster & banner upload) |
| `DELETE` | `/admin/movies/{id}` | Delete a movie |

---

## Dashboard & Catalog (2/2)

**`AdminHallController`** — `/admin/halls`  ·  **`AdminCinemaController`** — `/admin/cinemas`
| HTTP | Path | Purpose |
|:--|:--|:--|
| `GET / POST / DELETE` | `/admin/halls` , `/admin/halls/{id}` | CRUD for halls |
| `GET / POST / DELETE` | `/admin/cinemas` , `/admin/cinemas/{id}` | CRUD for cinemas (can't delete if it has halls) |

**`AdminShowtimeController`** — `/admin/showtimes`
| HTTP | Path | Purpose |
|:--|:--|:--|
| `GET` | `/admin/showtimes` , `/{id}` | List (with booking counts) / get one |
| `POST` | `/admin/showtimes` | Create / update (+ optional broadcast) |
| `DELETE` | `/admin/showtimes/{id}` | Delete (only if blocked & booking-free) |
| `POST` | `/admin/showtimes/{id}/block` | Toggle blocked / published |
| `POST` | `/admin/showtimes/{id}/copy-next-week` | Duplicate to next week |

---

## Operations — Bookings, Users, Check-In

**`AdminBookingController`** — `/admin/bookings`
| HTTP | Path | Purpose |
|:--|:--|:--|
| `GET` | `/admin/bookings` | List / search bookings |
| `POST` | `/admin/bookings/{id}/cancel` | Cancel a booking |
| `GET` | `/admin/bookings/export` | Export bookings as CSV |

**`AdminUserController`** — `/admin/users`
| HTTP | Path | Purpose |
|:--|:--|:--|
| `GET` | `/admin/users` , `/{id}` | List / search · profile + history |
| `POST` | `/admin/users/{id}/status` | Set account status (active/suspended/banned) |
| `POST` | `/admin/users/{id}/points` | Adjust loyalty points |

**`CheckInController`** — `/admin/checkin`
| HTTP | Path | Purpose |
|:--|:--|:--|
| `POST` | `/admin/checkin` | Check a customer in by ticket code |

---

## Food, Promo & Reports

**`AdminFoodItemController`** `/admin/food-items` · **`AdminFoodOrderController`** `/admin/food-orders` · **`FoodItemImageApiController`** `/admin/api/food-items`
| HTTP | Path | Purpose |
|:--|:--|:--|
| `GET / POST / DELETE` | `/admin/food-items` , `/{id}` | CRUD for food items (+ image) |
| `GET` | `/admin/food-orders` | Food orders for a given date |
| `POST / DELETE` | `/admin/api/food-items/images` | Upload / delete a food image |

**`AdminPromoCodeController`** `/admin/promo-codes` · **`AdminReportController`** `/admin/reports`
| HTTP | Path | Purpose |
|:--|:--|:--|
| `GET / POST / DELETE` | `/admin/promo-codes` , `/{id}` | CRUD for promo codes |
| `GET` | `/admin/reports` | Revenue / booking reports + daily stats |
| `GET` | `/admin/reports/export` | Export report as CSV |

---

## Communication & Configuration

**`AdminNotificationController`** — `/admin/notifications`
| HTTP | Path | Purpose |
|:--|:--|:--|
| `GET` | `/admin/notifications` | List sent notifications |
| `POST` | `/admin/notifications/send` | Broadcast a notification to all users |
| `DELETE` | `/admin/notifications/{id}` | Delete a notification |

**`AdminSettingController`** — `/admin/settings`
| HTTP | Path | Purpose |
|:--|:--|:--|
| `GET` | `/admin/settings` | List all system settings |
| `POST` | `/admin/settings` | Save settings (key → value) |

---

## 🔍 Anatomy of a controller — `AdminMovieController`

```java
@RestController
@RequestMapping("/admin/movies")     // base path for every endpoint
@RequiredArgsConstructor
public class AdminMovieController {

    private final MovieService movieService;   // logic lives in the service

    @GetMapping                               // GET /admin/movies
    public ResponseEntity<List<Movie>> movies() {
        return ResponseEntity.ok(movieService.findAll());
    }

    @GetMapping("/{id}")                      // GET /admin/movies/{id}
    public ResponseEntity<Movie> movie(@PathVariable Long id) {
        return ResponseEntity.ok(movieService.findById(id));
    }

    @PostMapping                              // POST /admin/movies
    public ResponseEntity<Movie> save(@ModelAttribute Movie movie) {
        return ResponseEntity.ok(movieService.saveWithImages(movie, null, null));
    }

    @DeleteMapping("/{id}")                    // DELETE /admin/movies/{id}
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        movieService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
```

---

# ✅ Summary

- **25 REST controllers**, grouped by resource — one controller = one job
- **Layered design:** Controller → Service → Repository → Database
- **Security:** public browsing is open, `/profile` needs login, `/admin/**` needs admin
- **Self-documenting:** all endpoints live in **Swagger UI** (`/swagger-ui.html`)

### Thank you 🎬
