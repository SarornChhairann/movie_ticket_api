# Sprint 5 — Food and promo codes

## Goal

An admin can sell concession items and publish a percent-off code.

Depends on: sprint 4. Analytics food and promos rows.

## Acceptance

- [ ] Food item save is multipart. `stockQuantity` of 0 is labeled unlimited. Unavailable items stay in the admin list and are hidden from customers by the existing API.
- [ ] `GET /admin/food-orders` is a read-only table: booking, customer, movie, item, quantity, line total.
- [ ] Promo form fields match the entity: `code`, `discountType`, `discountPercent` (1–100), `minPurchase`, `validUntil`, `maxUses` (0 = unlimited), `active`.
- [ ] The form states that the percent reduces checkout subtotal only.
- [ ] `usedCount` is visible and not editable.

## Steps

1. Routes: `/food`, `/food-orders`, `/promos`.
2. Promo create and update are `POST /admin/promo-codes` with JSON. Include `id` on update.
3. Do not apply the promo in the browser.

## Testing

Create `SAVE10` at 10 percent with a minimum purchase. Quote a booking on the customer app above and below that minimum.

## Out of scope

A fixed-amount checkout path. `discountType` may be stored, but checkout today applies `discountPercent` only. Label that on the form.
