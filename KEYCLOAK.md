# Authentication with Keycloak

Keycloak owns authentication for this app. It holds the credentials, issues the tokens,
and decides who is an admin; the app is an **OAuth2 resource server** that only ever
verifies the signed JWT it is handed.

There is no session cookie and no HTTP Basic any more, and no password hash is stored in
the `users` table.

## The shape of it

```
        POST /login  (email + password)
client ──────────────────────────────▶ app ──password grant──▶ Keycloak
        ◀──── access_token, refresh_token ────────────────────
        Authorization: Bearer <access_token>
client ──────────────────────────────▶ app ──verifies signature with the realm's
                                             public keys, reads realm_access.roles
```

- `POST /login` and `POST /register` exist so a client never has to know Keycloak's
  URL. Under the hood they are Keycloak's password grant and Admin REST API.
- Every other endpoint expects `Authorization: Bearer <access_token>`.
- Access tokens are short-lived (5 minutes by default). `POST /api/auth/refresh` trades
  a refresh token for a new one; `POST /logout` ends the Keycloak session.

## Roles

The realm has two roles, `customer` and `admin`. Spring Security maps each realm role
onto an authority by upper-casing it, so `admin` becomes `ROLE_ADMIN` and the existing
`hasRole("ADMIN")` rules keep working untouched.

| Realm role | Authority       | Reaches                        |
| ---------- | --------------- | ------------------------------ |
| `customer` | `ROLE_CUSTOMER` | `/profile/**`, `/bookings/**`  |
| `admin`    | `ROLE_ADMIN`    | everything, including `/admin/**` |

Self-registration through `POST /register` always grants `customer`. Making someone an
admin is done in the Keycloak console (Users → Role mapping → assign `admin`), or by
pointing `APP_ADMIN_EMAIL`/`APP_ADMIN_PASSWORD` at the account and restarting the app.

## The users table is a mirror

Bookings, tickets, points and loyalty all point at a row in `users`, so every Keycloak
account gets one. The row is created the first time the app sees a token for that
account - whether the account was registered through the API or created by hand in the
Keycloak console - and its role is realigned from the token on every request
(`KeycloakUserSyncFilter`).

Two columns stay owned by this app rather than by Keycloak, because they are application
state and not identity:

- `points` - loyalty balance.
- `status` - `ACTIVE` / `SUSPENDED` / `BANNED`. Suspending a user in the back office
  rejects their requests here **and** disables the account in Keycloak, so it stops
  being issued tokens at all.

The `password` column still exists (it is `NOT NULL`) but holds a fixed placeholder;
nothing reads it. Changing a password goes to Keycloak: the current one is verified by
logging in with it, then the new one is written through the Admin API.

## Configuration

| Setting | Purpose |
| ------- | ------- |
| `spring.security.oauth2.resourceserver.jwt.issuer-uri` | Must equal the `iss` claim in every token. Spring discovers the realm's public keys from it. |
| `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` | Only needed when the app reaches Keycloak under a different hostname than the issuer names (app in Docker, tokens issued to `localhost`). |
| `keycloak.server-url` | Where the app calls Keycloak for login, registration and user administration. |
| `keycloak.realm` | `ticket-realm` |
| `keycloak.login-client-id` | `ticket-app` - public client, password grant. |
| `keycloak.admin-client-id` / `admin-client-secret` | `ticket-backend` - confidential client whose service account holds `realm-management: manage-users`. |
| `keycloak.bootstrap-admin` | Whether startup pushes `APP_ADMIN_*` into the realm. Off in tests. |

All of them are overridable by the `KEYCLOAK_*` environment variables listed in
`.env.example`.

## Running it

```sh
docker compose up -d db keycloak     # Keycloak on :8080, Postgres on :5433
mvnw.cmd spring-boot:run             # app on :8010, defaults already point at :8080
```

`keycloak/realm-export.json` is imported on a fresh Keycloak container and defines the
realm, both clients, the two roles, and two demo accounts:

| Account | Password | Roles |
| ------- | -------- | ----- |
| `admin@gmail.com` | `admin123` | `admin`, `customer` |
| `customer@gmail.com` | `customer123` | `customer` |

Keycloak's own console is at http://localhost:8080 (`admin` / `admin`).

## Trying it

```sh
# 1. get a token
curl -s -X POST http://localhost:8010/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@gmail.com","password":"admin123"}'

# 2. call something with it
curl -s http://localhost:8010/admin/users \
  -H "Authorization: Bearer <access_token>"

# 3. register a customer
curl -s -X POST http://localhost:8010/register \
  -H 'Content-Type: application/json' \
  -d '{"name":"Sok Dara","email":"sok.dara@example.com","password":"secret123"}'
```

In Swagger UI (http://localhost:8010/swagger-ui.html), press **Authorize** and paste the
`access_token` - every "try it out" call is then sent with the bearer header.

## What each failure looks like

Every error has the same body shape as the rest of the API, `{"error": ..., "type": ...}`:

| Status | When |
| ------ | ---- |
| 401 | No token, or one that is malformed, expired, or not signed by this realm. Also a wrong email/password on `POST /login`. |
| 403 | The token is valid but lacks the role - or the account has been suspended or banned here. |
| 409 | Registering an email that already exists. |
| 502 | Keycloak is unreachable, or the issuer it stamps into tokens does not match `issuer-uri`. |

## Where the code lives

| File | Role |
| ---- | ---- |
| `config/SecurityConfig` | Resource server setup, role mapping, route rules, JSON 401/403. |
| `config/KeycloakProperties` | The `keycloak.*` settings. |
| `controller/AuthController` | `/login`, `/register`, `/logout`, `/api/auth/**`. |
| `service/AuthService` | The flows: login, register, refresh, change password, admin bootstrap. |
| `service/KeycloakAdminClient` | The only class that speaks Keycloak's HTTP API. |
| `security/KeycloakClaims` | What the app knows about the shape of a token. |
| `security/KeycloakUserSyncFilter` | Token → `users` row, and the suspended/banned check. |
