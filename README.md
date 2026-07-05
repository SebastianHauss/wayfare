# Wayfare

> A clean, full-stack URL shortener.

Wayfare turns long URLs into short, shareable links with a polished dashboard,
QR codes, click tracking, and optional account-based link management.

[Open Wayfare](https://wyfr.link)

![](docs/wayfare-dashboard.png)

## Highlights

- Shorten links without creating an account.
- Anonymous users can keep a small local list for quick one-off links.
- Signed-in users get unlimited saved links, custom aliases, stats, and analytics.
- Generate QR codes for shortened links.
- Create links with optional expiration by date or click count.
- Manage links in a responsive dashboard with pagination and manual refresh.
- Track clicks, referrers, countries, devices, and daily activity for saved links.

## Tech Stack

- Backend: Java 21, Spring Boot, Spring Security, Spring Data JPA
- Data: PostgreSQL, Redis, Flyway migrations
- Frontend: React, TypeScript, Vite, Tailwind CSS
- Auth: email/password, JWT cookies, refresh tokens, email verification
- Utilities: ZXing QR generation, click metadata parsing

## Local Development

Start Postgres and Redis:

```bash
docker compose up -d
```

Run the backend:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Run the frontend:

```bash
cd frontend
npm install
npm run dev
```

The frontend runs at `http://localhost:5173` and proxies API requests to
`http://localhost:8080`.

## Why This Project

Wayfare is intentionally small but complete: it shows product thinking,
authentication, persistence, caching, analytics, API design, and a user-facing
React dashboard without hiding the core workflow behind unnecessary complexity.
