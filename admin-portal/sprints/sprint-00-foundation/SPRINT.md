# Sprint 0 — Foundation

## Goal

A React app on port 8081 can sign in as an admin and land on an empty shell. A customer token cannot.

Depends on: `ADMIN_ANALYTICS.md` section "Who can enter".

## Acceptance

- [ ] `admin-portal/web` runs at `http://localhost:8081` and calls `http://localhost:8010`.
- [ ] Login posts to `/api/auth/login` and stores `access_token` and `refresh_token`.
- [ ] A response whose `role` is not `ADMIN` shows access denied and stores nothing.
- [ ] The shell has a side nav for later sprints and a logout that calls `/api/auth/logout`.
- [ ] A 401 from any later call tries refresh once, then returns to login.

## Steps

1. Scaffold Vite + React in `admin-portal/web`. Set the dev server port to 8081.
2. One API module: JSON by default, `Authorization: Bearer` when a token exists.
3. Screens: Login, Access denied, Shell with an empty main area.
4. No new backend route. CORS already allows `http://localhost:8081`.

## Testing

Sign in as `admin@gmail.com`. Sign in as a customer and confirm the shell does not open.

## Out of scope

Dashboard numbers, catalog forms.
