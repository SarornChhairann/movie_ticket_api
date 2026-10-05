# Sprint 4 — Bookings and check-in

## Goal

Staff can find a booking, cancel it without the customer time window, and scan a ticket at the door.

Depends on: sprint 3. Analytics bookings and check-in rows. System analytics section 3.6.

## Acceptance

- [ ] `GET /admin/bookings` lists movie, hall, show date and time, customer name, total, status, and `bookedAt`.
- [ ] A search box sends `?q=`.
- [ ] Cancel calls `POST /admin/bookings/{id}/cancel`. It is offered for `CONFIRMED` bookings inside the customer window as well as outside it.
- [ ] After cancel, status is `CANCELLED`. The page tells the operator that seats are free and points are not returned.
- [ ] Check-in posts `{ "ticketCode" }` to `/admin/checkin`. Success shows `USED` and `scannedAt`.
- [ ] A second scan of the same code, and a scan of a cancelled code, show the server error and do not change the row.

## Steps

1. Routes: `/bookings`, `/checkin`.
2. Check-in is a single field and a submit button, sized for a scanner that types the code and Enter.
3. Do not add a refund endpoint.

## Testing

Cancel a confirmed booking inside two hours of the show and confirm the API allows it. Scan that booking's ticket and confirm the API rejects it.

## Out of scope

Customer self-service cancel, QR rendering.
