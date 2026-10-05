# Admin portal (web)

React + Vite back office for the Ticket-Management-System API. Plan: `../sprints/README.md`.

```
npm install
npm run dev        # http://localhost:8081, calls http://localhost:8010
```

The API must be running (`./mvnw spring-boot:run` from the repo root, with `docker compose up -d db keycloak`).
Override the API address with `VITE_API_BASE_URL` in `.env` (see `.env.example`).

Demo admin: `admin@gmail.com` / `admin123`.

## Layout

| Path | Role |
| --- | --- |
| `src/api/client.js` | The one HTTP client: JSON, Bearer token, refresh-once on 401 |
| `src/api/session.js` | Tokens in memory + `sessionStorage` |
| `src/auth/` | Login/logout and the admin route guard |
| `src/screens/` | Login, Access denied, Shell |
| `src/nav.js` | Side nav entries, one per sprint |
