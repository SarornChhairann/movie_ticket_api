# Docker

Two Compose files, one image:

| File | For | Ports on the host |
|---|---|---|
| `docker-compose.yml` | local development (demo data, everything published) | 8010 app, 5433 Postgres, 5672/15672 RabbitMQ, 6379 Redis |
| `docker-compose.prod.yml` | a production server | 80 and 8080 (Caddy, HTTP on `PUBLIC_IP`) |

`Dockerfile` is shared: a multi-stage build (Maven cache, Spring Boot layers, non-root JRE runtime with a readiness `HEALTHCHECK` on `/actuator/health/readiness`).

## Development

```bash
cp .env.example .env
docker compose up -d db rabbitmq redis   # then ./mvnw spring-boot:run
docker compose up -d --build                      # or the app in a container too
```

`.env` has `APP_SEED_FORCE=true`: every app start wipes and reseeds the demo data.

## Production

### Layout

```
internet ──80──▶ caddy ──(edge)──▶ app :8010 ──(backend, internal: no internet)──▶ db, redis
```

- **Networks.** `ticket-prod-edge` joins Caddy to the app. `ticket-prod-backend` is `internal: true`, so Postgres and Redis have no route out and nothing from outside can reach them. The app is on both: that is how it reaches SMTP and PayWay.
- **Ports.** Only Caddy publishes anything: port 80 is the API, plain HTTP on `PUBLIC_IP`. There is no DNS name and no Let's Encrypt certificate.
- **Volumes.** These are named volumes with fixed names, so they survive `docker compose down` and a project rename:

  | Volume | Holds |
  |---|---|
  | `ticket-prod-postgres` | app data (users, bookings, refresh tokens) |
  | `ticket-prod-uploads` | posters, food images |
  | `ticket-prod-redis` | cache, lookup throttle |
  | `ticket-prod-caddy-data` | TLS certificates |

  Only `down -v` deletes them.
- **Hardening.** Every service gets `no-new-privileges`, CPU/memory limits, rotated JSON logs (5 × 10 MB) and `restart: unless-stopped`. The app also runs `read_only` with all capabilities dropped, under tini (`init: true`), and shuts down gracefully (`server.shutdown=graceful`, 30 s grace).
- **Auth** is built into the app: passwords are BCrypt hashes in `users`, access tokens are JWTs signed with `APP_JWT_SECRET`, and refresh tokens are stored hashed in `refresh_tokens`. There is no identity server to run. Changing `APP_JWT_SECRET` signs everyone out.

### Server preparation

1. Get a Linux host with Docker Engine 24+ and the Compose plugin. 2 vCPU / 4 GB RAM covers the limits set here.
2. Set `PUBLIC_IP` in `.env.prod` to the server's public IPv4. Do not set a domain name.
3. Open only 22 and 80 in the firewall, for example `ufw allow OpenSSH && ufw allow 80/tcp && ufw enable`.

### First deploy

```bash
git clone <repo> && cd Ticket-Management-System
cp .env.prod.example .env.prod && chmod 600 .env.prod
# fill in every REQUIRED value; generate secrets with:
openssl rand -base64 32 | tr -d '/+=' | cut -c1-32
# APP_JWT_SECRET needs at least 32 bytes:
openssl rand -base64 48

docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
docker compose -f docker-compose.prod.yml --env-file .env.prod ps     # all "healthy"
```

On every start the app creates `APP_ADMIN_EMAIL` (or resets its password and admin role to the values in `.env.prod`). No demo data is seeded.

Tip: `alias dcp='docker compose -f docker-compose.prod.yml --env-file .env.prod'`.

### Updates

```bash
git pull
dcp build app && dcp up -d app      # only the app is recreated; data stays
dcp logs -f --tail=200 app
```

To ship images from CI instead, push `APP_IMAGE:APP_TAG`, set both in `.env.prod`, and run `dcp pull app && dcp up -d app`. To roll back, set the previous `APP_TAG` and run `up -d app` again.

### Backups

Postgres holds all the app's data, including accounts:

```bash
dcp exec -T db sh -c 'pg_dumpall -U "$POSTGRES_USER"' | gzip > backup-$(date +%F).sql.gz
# uploads
docker run --rm -v ticket-prod-uploads:/data -v "$PWD":/out alpine tar czf /out/uploads-$(date +%F).tgz -C /data .
```

Run both from cron and copy the files off the server. To restore, run `gunzip -c backup.sql.gz | dcp exec -T db sh -c 'psql -U "$POSTGRES_USER" -d postgres'` on an empty volume.

Redis only holds a cache and a throttle, so it needs no backup.

The production stack runs no RabbitMQ (it is sized for a 1 GB server): `APP_MAIL_QUEUE_ENABLED=false`, so booking mail is sent directly from the app.

### Operations

| Task | Command |
|---|---|
| Health | `dcp ps`; `dcp exec app bash -c 'exec 3<>/dev/tcp/127.0.0.1/8010; printf "GET /actuator/health HTTP/1.0\r\n\r\n" >&3; cat <&3'` |
| Redis | `dcp exec redis sh -c 'REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli info memory'` |
| psql | `dcp exec db sh -c 'psql -U "$POSTGRES_USER" "$POSTGRES_DB"'` |

### Notes

- `SPRING_JPA_HIBERNATE_DDL_AUTO=update` creates and extends tables on start, since this project has no migration tool yet. Take a backup before deploying a release that changes entities.
- Swagger UI (`/swagger-ui.html`) is still public on the API domain. Block it in `deploy/caddy/Caddyfile` if the API should not be browsable.
- Secrets live in `.env.prod` (mode 600, git-ignored, never in the image). For stricter setups, move them to Docker secrets or a vault.
