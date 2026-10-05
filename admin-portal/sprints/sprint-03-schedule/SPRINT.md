# Sprint 3 — Halls and showtimes

## Goal

An admin can attach a hall to a cinema and put a movie on the schedule.

Depends on: sprint 2. Analytics halls and showtimes rows. Customer pricing in system analytics section 3.4.

## Acceptance

- [ ] Hall form picks an existing cinema and sets name, city, type, capacity, Dolby Atmos, and ScreenX.
- [ ] Showtime list comes from `GET /admin/showtimes` and shows movie, hall, date, time, both prices, blocked, and `editLocked`.
- [ ] The form cannot save when `editLocked` is true. The lock message uses `editLockHours` from the list payload.
- [ ] `normalPrice` is labeled per seat. `couplePrice` is labeled per pair.
- [ ] Block calls `POST /admin/showtimes/{id}/block`. Copy calls `POST /admin/showtimes/{id}/copy-next-week`.
- [ ] Delete is refused by the UI when `activeBookingCount` is greater than zero, and the API error is shown if the server also refuses.

## Steps

1. Routes: `/halls`, `/showtimes`, `/showtimes/:id`.
2. Showtime body is JSON: `movieId`, `hallId`, `showDate`, `showTime`, `normalPrice`, `couplePrice`, `publishNow`, `sendNotification`.
3. Do not draw individual seats. The hall save creates the template.

## Testing

Create a showtime, block it, and confirm a customer movie detail no longer lists it. Copy next week and see the new row.

## Out of scope

Changing a price after tickets are sold. The lock exists for that.
