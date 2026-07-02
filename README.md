# Wayfare — MVP Scope

> A URL shortener, built to eventually grow into a small link-management platform.
> The MVP intentionally covers only the core loop: shorten a URL, redirect through it.
> Everything else on the roadmap depends on this foundation working well first.

## Tagline

*"Wayfare finds the shortest path to where you're going."*

## Core idea

Take a long URL, generate a short, unique code for it, and redirect anyone who
visits the short link to the original destination — fast, and without losing
the click.

## MVP scope (what ships first)

The MVP is deliberately narrow. No auth, no dashboard, no persistence beyond
"does the redirect work reliably and fast."

### In scope

- `POST /api/shorten` — accepts a long URL, returns a short code + short URL
- `GET /{code}` — redirects (302) to the original URL
- Base62-encoded short codes derived from the Postgres auto-increment ID
  (no collision handling needed — every ID is unique by definition)
- Cache-aside reads through Redis (Upstash) — check cache, fall back to
  Postgres on a miss, repopulate cache with a TTL
- Basic click counting (a single indexed `UPDATE` per redirect)
- Input validation (must be a valid `http(s)://` URL)
- Dockerized, deployable to a free-tier host (Render/Fly.io)

### Explicitly out of scope for MVP

- User accounts / authentication
- A frontend / dashboard (API-only for now)
- Any of the "Morgen" features below

## Tech stack

| Layer | Choice |
|---|---|
| Backend | Java 21, Spring Boot |
| Database | PostgreSQL (Neon, free tier, scale-to-zero) |
| Cache | Redis (Upstash, free tier) |
| Code generation | Base62 encoding of the DB auto-increment ID |
| Deployment | Docker container → Render / Fly.io |

## Data model (MVP)

```
short_urls
├── id             BIGINT (PK, auto-increment)
├── short_code     VARCHAR(16) UNIQUE
├── original_url   VARCHAR(2048)
├── created_at     TIMESTAMP
└── click_count    BIGINT
```

## API sketch (MVP)

```
POST /api/shorten
  body:   { "url": "https://example.com/some/long/path" }
  201 →   { "shortCode": "cb", "shortUrl": "https://wayfare.app/cb", "originalUrl": "..." }

GET /{code}
  302 → Location: <originalUrl>
  404 → { "error": "No URL found for short code: ..." }
```

## Roadmap — "Morgen" (post-MVP, not yet scoped in detail)

These are noted so the MVP's architecture doesn't accidentally block them —
but none of them are designed or built yet:

- [ ] **Analytics** — clicks over time, referrers, geo/device breakdown
- [ ] **QR codes** — generate a QR code per short link
- [ ] **API keys** — programmatic access with per-key rate limits
- [ ] **Teams** — shared workspaces, multiple users per org
- [ ] **Link expiration** — TTL per link, auto-disable after a date/click count
- [ ] **Custom domains** — bring-your-own-domain for short links
- [ ] **Rate limiting** — per-IP/per-key request limits (Redis `INCR` + `EXPIRE`)
- [ ] **Password protection** — require a password before redirecting

## Architectural notes for future-proofing

A few small decisions in the MVP are made with the roadmap in mind, without
over-building for features that don't exist yet:

- `short_urls` table has room to grow (owner/team columns can be added later
  without touching the core shorten/redirect logic)
- Cache-aside pattern with Redis is already in place — rate limiting can
  reuse the same Redis instance later
- Click counting is a single `UPDATE`; swapping this for a proper analytics
  pipeline (event log + async processing) is a clean, isolated change later