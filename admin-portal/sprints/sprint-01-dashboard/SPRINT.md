# Sprint 1 — Dashboard

## Goal

The home page shows the live back-office summary.

Depends on: sprint 0. Analytics dashboard row.

## Acceptance

- [ ] `GET /admin/dashboard` fills movie, hall, showtime, booking, and user counts.
- [ ] Revenue today, this week, and this month render as money, not raw arithmetic.
- [ ] Top movies and the 14-day series render from `topMovies` and `dailyStats`.
- [ ] Five recent bookings show movie, hall, customer, total, and status.
- [ ] Empty lists show an empty state, not a blank card.

## Steps

1. Confirm the JSON in Swagger before writing the page.
2. Dashboard route is the shell index.
3. Do not compute revenue in the browser. If a field is missing, show a dash.
4. No backend change unless the call returns 500. Then fix that bug only.

## Testing

Load the page against seeded data. Reload and confirm the counts match a second Swagger call.

## Out of scope

CSV export (sprint 7), booking cancel (sprint 4).
