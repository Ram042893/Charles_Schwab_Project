---
name: url-shortener-engineering
description: Conventions for the production URL shortener APIs, Sybase persistence, Redis cache, JWT security, and SSRF guardrails. Use when changing shorten/redirect/analytics behavior or Docker/Sybase configuration.
---

# URL shortener engineering

## API

- Create: `POST /api/v1/urls` (JWT)
- Redirect: `GET /s/{code}` (public)
- Analytics: `GET /api/v1/urls/{code}/analytics`
- Export and custom alias/expiration require feature flags

## Rules

- Validate targets with `UrlSafetyGuard` before persist.
- Cache resolved mappings in `url-mappings`; evict on deactivate.
- Store hashed IP only in `click_events`.
- Brownfield capabilities must stay behind `FeatureFlagService` so orchestration can enable/rollback them.
- Docker profile speaks Sybase through jTDS; tests use H2 with T-SQL compatibility.
- Do not make redirect require authentication.

## Flags

- `CUSTOM_ALIAS`
- `EXPIRATION`
- `ANALYTICS_EXPORT`

Default off. Greenfield keeps them off. Brownfield/ambiguous enable after approval.
