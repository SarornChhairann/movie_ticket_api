# Admin portal sprints

Build the browser back office one slice at a time. The API already exists. A sprint adds screens that call it, plus a backend change only when the sprint names one.

Read first:

1. `admin-portal/ADMIN_ANALYTICS.md`
2. `movie_ticket_mobile/system_analytics/SYSTEM_ANALYTICS.md` sections 3.6, 3.7, 3.8, and 8
3. This file, then only the current `SPRINT.md`

## Method

| Step | Output |
| --- | --- |
| 1 | Read the sprint and the analytics rows it names |
| 2 | Call the sprint's endpoints in Swagger (`http://localhost:8010/swagger-ui.html`) and match the JSON |
| 3 | Build the screen: loading, empty, error, data |
| 4 | Change the API only for a gap listed in that sprint |
| 5 | Test the screen against a running API with the admin account |

Rules:

- Finish sprint `NN` before opening `NN+1`.
- Do not invent price math, seat statuses, or roles.
- Every `/admin/**` call sends `Authorization: Bearer`. A 401 clears the session and returns to login. A 403 on a customer token stays on an access-denied page.
- Money is shown from the server. The portal does not add tax or discounts.
- Create and update use the same POST. Send `id` only when editing.

## Stack

| Part | Choice |
| --- | --- |
| UI | React + Vite in `admin-portal/web`, dev server port **8081** |
| HTTP | One client, base URL `http://localhost:8010` |
| Auth | Access and refresh tokens in memory plus `sessionStorage`. Refresh via `POST /api/auth/refresh` with `{ "refresh_token" }` |
| API | `Ticket-Management-System` on port **8010** |

Do not add a second admin inside the Flutter customer app.

## Sequence

| Sprint | Folder | Demo |
| --- | --- | --- |
| 0 | `sprint-00-foundation` | Admin can sign in and see an empty shell |
| 1 | `sprint-01-dashboard` | Counts, revenue, recent bookings |
| 2 | `sprint-02-catalog` | Movies and cinemas |
| 3 | `sprint-03-schedule` | Halls and showtimes |
| 4 | `sprint-04-bookings` | Search, admin cancel, door check-in |
| 5 | `sprint-05-concessions` | Food, orders, promo codes |
| 6 | `sprint-06-people` | Users, broadcast, pricing settings |
| 7 | `sprint-07-reports` | Report page and CSV download |

## Where code goes

| Part | Path |
| --- | --- |
| Portal | `Ticket-Management-System/admin-portal/web` |
| API | `Ticket-Management-System/src` |
| This plan | `Ticket-Management-System/admin-portal/sprints` |
