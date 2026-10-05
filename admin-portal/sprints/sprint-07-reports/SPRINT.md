# Sprint 7 — Reports

## Goal

The report page matches the dashboard numbers and downloads the CSV the API already builds.

Depends on: sprint 1 and sprint 6.

## Acceptance

- [ ] `GET /admin/reports` shows revenue today, week, and month, top movies, and daily stats.
- [ ] Those revenue figures match the dashboard for the same day.
- [ ] Export calls `GET /admin/reports/export` with the bearer token and saves `report.csv`.
- [ ] A failed export shows the server message. It does not download an empty file.
- [ ] Nav links from sprint 0 all open a real page. No placeholder routes remain.

## Steps

1. Route: `/reports`.
2. Download with the same API client so the bearer header is present. Do not open the CSV URL in a new tab without the token.
3. Walk every admin invariant in `ADMIN_ANALYTICS.md` once.

## Testing

Export the CSV and confirm it contains the same revenue period the page shows. Sign in as a customer and confirm `/reports` is never requested.

## Out of scope

New report columns, charts that invent a metric the payload does not have.
