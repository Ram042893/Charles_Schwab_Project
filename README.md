# Agentic URL Shortener

Production-style URL shortener plus an **agentic SDLC orchestration layer**. The assignment is not only to ship short links; it is to turn a requirement into a reviewable engineering outcome with task decomposition, gated execution, validation, and human oversight.

## Tech stack

| Layer | Choice |
| --- | --- |
| Runtime | JDK 21, virtual threads |
| Framework | Spring Boot 4.1.1 |
| Build | Maven 3.9.16 (wrapper) |
| Database | Sybase ASE (`SybaseASEDialect` + jTDS) in Docker; H2 (T-SQL compatibility) for tests |
| Cache | Redis (redirect hot path, feature flags, analytics) |
| Security | Spring Security, JWT access/refresh, RBAC |
| Containers | Docker + Compose (Sybase, Redis, app) |

## What is in the prototype

1. **URL shortener APIs**: create, redirect, analytics, deactivate, optional custom alias / expiration / export (feature-flagged brownfield capabilities).
2. **Agentic orchestrator**: explicit DAG, entry/exit gates, parallel stages with a join, human approval, bounded retries, rollback, safe-stop, replan, audit lineage, and reliability metrics.
3. **Three scenarios**: greenfield, brownfield, and ambiguous — each executable over HTTP.

## Quick start (tests, no Sybase required)

```powershell
.\mvnw.cmd -B test
.\mvnw.cmd -B spring-boot:run "-Dspring-boot.run.profiles=local"
```

`local` uses H2 with Sybase compatibility mode. Start Redis for cache:

```powershell
docker compose up -d redis
```

If Redis is not running, switch cache off by using the `test` profile instead.

## Docker (Sybase + Redis + app)

```powershell
docker compose up --build
```

- App: http://localhost:8080
- OpenAPI: http://localhost:8080/swagger-ui.html
- Sybase: `sa` / `myPassword` on port `5000`
- Redis: port `6379`

ASE images are large and slow to become ready. The app waits on Redis health and Sybase start. Tests always run against H2 so evaluation is not blocked by ASE boot time.

## Demo users

| User | Password | Roles |
| --- | --- | --- |
| engineer | Engineer@123 | ENGINEER |
| reviewer | Reviewer@123 | REVIEWER |
| admin | Admin@123 | ADMIN, REVIEWER, ENGINEER |

Engineers can start workflows. Reviewers/admins own high-impact approvals.

## Core API examples

```powershell
# Login
$login = Invoke-RestMethod -Method POST http://localhost:8080/api/v1/auth/login -ContentType application/json -Body '{"username":"engineer","password":"Engineer@123"}'
$token = $login.accessToken
$headers = @{ Authorization = "Bearer $token" }

# Shorten (core)
Invoke-RestMethod -Method POST http://localhost:8080/api/v1/urls -Headers $headers -ContentType application/json -Body '{"targetUrl":"https://example.com/docs"}'

# Redirect
# GET http://localhost:8080/s/{code}
```

## Run the three assignment scenarios

```powershell
$eng = (Invoke-RestMethod -Method POST http://localhost:8080/api/v1/auth/login -ContentType application/json -Body '{"username":"engineer","password":"Engineer@123"}').accessToken
$rev = (Invoke-RestMethod -Method POST http://localhost:8080/api/v1/auth/login -ContentType application/json -Body '{"username":"reviewer","password":"Reviewer@123"}').accessToken
$eh = @{ Authorization = "Bearer $eng" }
$rh = @{ Authorization = "Bearer $rev" }

# Greenfield
$g = Invoke-RestMethod -Method POST http://localhost:8080/api/v1/orchestration/scenarios/greenfield -Headers $eh -ContentType application/json -Body '{"requirement":"Build a URL shortener with auth, analytics, cache and reliability."}'
Invoke-RestMethod -Method POST "http://localhost:8080/api/v1/orchestration/workflows/$($g.workflow.id)/approve" -Headers $rh -ContentType application/json -Body '{"comment":"release approved"}'

# Brownfield
$b = Invoke-RestMethod -Method POST http://localhost:8080/api/v1/orchestration/scenarios/brownfield -Headers $eh -ContentType application/json -Body '{"requirement":"Add custom aliases, expiration and analytics export."}'
Invoke-RestMethod -Method POST "http://localhost:8080/api/v1/orchestration/workflows/$($b.workflow.id)/approve" -Headers $rh -ContentType application/json -Body '{"comment":"change control approved"}'
Invoke-RestMethod -Method POST "http://localhost:8080/api/v1/orchestration/workflows/$($b.workflow.id)/approve" -Headers $rh -ContentType application/json -Body '{"comment":"release approved"}'

# Ambiguous
$a = Invoke-RestMethod -Method POST http://localhost:8080/api/v1/orchestration/scenarios/ambiguous -Headers $eh -ContentType application/json -Body '{"requirement":"Make the shortener better and more enterprise ready."}'
Invoke-RestMethod -Method POST "http://localhost:8080/api/v1/orchestration/workflows/$($a.workflow.id)/approve" -Headers $rh -ContentType application/json -Body '{"comment":"assumptions accepted"}'
Invoke-RestMethod -Method POST "http://localhost:8080/api/v1/orchestration/workflows/$($a.workflow.id)/approve" -Headers $rh -ContentType application/json -Body '{"comment":"release approved"}'
```

Supporting documents:

- [Architecture](docs/ARCHITECTURE.md)
- [Scenarios](docs/SCENARIOS.md)
- [Engineering summary](docs/ENGINEERING_SUMMARY.md)
