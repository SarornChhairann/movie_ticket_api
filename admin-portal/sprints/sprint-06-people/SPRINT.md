# Sprint 6 — Users, broadcast, and settings

## Goal

An admin can block an account, adjust points, send one broadcast, and change the six pricing and timing settings.

Depends on: sprint 5. Analytics users, notifications, and settings rows. System analytics section 3.8.

## Acceptance

- [ ] User list shows name, email, phone, role, points, and status.
- [ ] Status posts `{ "status": "ACTIVE" | "SUSPENDED" | "BANNED" }` to `/admin/users/{id}/status`.
- [ ] Points posts `{ "delta": <integer> }` to `/admin/users/{id}/points`. The field is a change, not a replacement total. Negative delta is allowed by the API.
- [ ] User detail lists that user's bookings.
- [ ] Notification composer sends `{ "title", "message" }` to `POST /admin/notifications/send` and then shows the sent list. There is no movie-target control.
- [ ] Settings form loads `GET /admin/settings` and saves a JSON object of the six keys: `hold_minutes`, `cancellation_hours`, `service_fee`, `tax_rate`, `weekend_surcharge_rate`, `morning_discount_rate`.
- [ ] Rates are decimals (`0.10` means 10 percent). The labels say so. Hold minutes and cancellation hours are whole numbers.

## Steps

1. Routes: `/users`, `/users/:id`, `/notifications`, `/settings`.
2. Do not put a password field on the user form.
3. Saving settings does not reprice old bookings.

## Testing

Set the service fee, then open a new customer quote and see the new fee. Suspend a customer and confirm that account cannot log in.

## Out of scope

Creating staff accounts in Keycloak, per-movie notifications.
