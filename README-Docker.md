# Docker

The Compose stack is three services: the app, PostgreSQL, and **Keycloak**, which owns
authentication (see [KEYCLOAK.md](KEYCLOAK.md) for how auth works).

Build the application image:

```sh
docker build -t ticket-management-system .
```

Run the full local stack:

```sh
docker compose up --build
```

Optional: copy `.env.example` to `.env` and change database credentials, Keycloak
settings, admin credentials, mail settings, or exposed ports before running Compose.

| Service  | URL                                        |
| -------- | ------------------------------------------ |
| App      | http://localhost:8010                      |
| Swagger  | http://localhost:8010/swagger-ui.html      |
| Keycloak | http://localhost:8080 (console: `admin` / `admin`) |
| Postgres | localhost:5433                             |

The app waits for both PostgreSQL and Keycloak to report healthy before it starts, so
the first `up` takes as long as Keycloak needs to import the realm (about 30 seconds).

## Getting a token

```sh
curl -s -X POST http://localhost:8010/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@gmail.com","password":"admin123"}'
```

Then send `Authorization: Bearer <access_token>` with every other request. The seeded
accounts are `admin@gmail.com` / `admin123` and `customer@gmail.com` / `customer123`.

## Volumes

- `postgres_data` — PostgreSQL data
- `app_uploads` — uploaded movie and food item images
- `keycloak_data` — Keycloak's dev-mode database, so users registered through the API
  survive a `docker compose down`

`docker compose down -v` drops all three; the realm is then re-imported from
`keycloak/realm-export.json` on the next start, and any user registered since is gone.

## Changing ports

```sh
APP_PORT=8090 POSTGRES_PORT=5434 docker compose up --build
```

Keycloak's port is different: tokens carry the issuer URL inside them, so moving it
means moving three settings together.

```sh
KEYCLOAK_PORT=8181 \
KEYCLOAK_PUBLIC_URL=http://localhost:8181 \
KEYCLOAK_ISSUER_URI=http://localhost:8181/realms/ticket-realm \
docker compose up --build
```

## Running only some of the stack

Keycloak and the database in Docker, the app from your IDE (`mvnw.cmd spring-boot:run`) —
this is the usual development loop. The app's defaults already point at
`http://localhost:8080`, so nothing else needs setting:

```sh
docker compose up -d db keycloak
```

Only the app image, against services already running on the host:

```sh
docker run --rm -p 8010:8010 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5433/spring_boot_ticket \
  -e SPRING_DATASOURCE_USERNAME=myuser \
  -e SPRING_DATASOURCE_PASSWORD=MySecretPassword \
  -e KEYCLOAK_SERVER_URL=http://host.docker.internal:8080 \
  -e KEYCLOAK_ISSUER_URI=http://localhost:8080/realms/ticket-realm \
  -e SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI=http://host.docker.internal:8080/realms/ticket-realm/protocol/openid-connect/certs \
  -v ticket-management-uploads:/app/uploads \
  ticket-management-system:latest
```

`KEYCLOAK_ISSUER_URI` is what the token says (`localhost`), while `KEYCLOAK_SERVER_URL`
and the JWK set URI are how this container actually reaches Keycloak — they differ
whenever the app and the token's audience see Keycloak under different hostnames.

## Production notes

Set strong values for `APP_ADMIN_PASSWORD`, `KEYCLOAK_ADMIN_PASSWORD`,
`KEYCLOAK_ADMIN_CLIENT_SECRET` (it must match the client secret in the realm) and the
database password. Keycloak runs here as `start-dev` with an embedded database, which
is fine for coursework and demos but is not a production configuration — a real
deployment needs `start`, a real database, and HTTPS.
