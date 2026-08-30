# Docker

Build the application image:

```sh
docker build -t ticket-management-system .
```

Run the full local stack with PostgreSQL:

```sh
docker compose up --build
```

Optional: copy `.env.example` to `.env` and change database credentials, admin credentials, mail settings, or exposed ports before running Compose.

The app is available at:

```text
http://localhost:8010
```

The Compose stack creates two persistent volumes:

- `postgres_data` for PostgreSQL data
- `app_uploads` for uploaded movie and food item images

To change the host ports:

```sh
APP_PORT=8080 POSTGRES_PORT=5434 docker compose up --build
```

To run only the app image against a database already running on your host:

```sh
docker run --rm -p 8010:8010 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5433/spring_boot_ticket \
  -e SPRING_DATASOURCE_USERNAME=myuser \
  -e SPRING_DATASOURCE_PASSWORD=MySecretPassword \
  -v ticket-management-uploads:/app/uploads \
  ticket-management-system:latest
```

For production, set strong values for `APP_SECURITY_JWT_SECRET`, `APP_ADMIN_PASSWORD`, and the database password.
