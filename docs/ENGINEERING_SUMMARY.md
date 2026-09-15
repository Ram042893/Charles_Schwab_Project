# Engineering summary

## Plan and rationale

The assignment asks for a working URL shortener **and** an agentic execution model that can carry a requirement through architecture, implementation, testing, documentation, and release readiness with controlled autonomy.

Plan:

1. Treat the shortener as the production system under change.
2. Treat orchestration as a first-class bounded context with a persisted DAG, not a linear script.
3. Encode greenfield / brownfield / ambiguous as different graphs and policies, not as three README stories.
4. Keep humans on the high-impact path: change control, assumption acceptance, release.

## Artifacts

- Runnable Spring Boot 4.1 application (JDK 21, Maven wrapper 3.9.16)
- Docker Compose: Sybase ASE, Redis, app
- OpenAPI at `/swagger-ui.html`
- Tests for safety guards, shortener APIs, and all three orchestration scenarios
- Architecture, scenario, and setup docs

## Risks, trade-offs, validation

| Risk | Mitigation | Residual |
| --- | --- | --- |
| Unofficial Sybase Docker images are slow/fragile | Production/docker profile uses jTDS + `SybaseASEDialect`; tests/local use H2 | Full ASE soak not proven in every environment |
| Agents without an LLM can look “scripted” | Interface is pluggable; value is governance, DAG, lineage, rollback | No generative design variation |
| Feature-flag rollback does not drop data | Intentional: flags reverse behavior without destroying links | Schema rollback would need versioned migrations |
| DNS SSRF checks can flake offline | Disabled in `test` profile; enabled in docker/local | Host allowlists would be stronger in a bank network |
| In-process workflow advance can be long | Fine for prototype; production would queue stage workers | No cluster-wide lock yet |

Validation actually executed by `TestingAgent` against live services, plus JUnit/MockMvc covering APIs and the three DAGs.

## Assumptions

- Demo users are acceptable for the interview prototype (replace with IdP in a Schwab environment).
- A modular monolith is the right first architecture; extracting “redirect” later is straightforward because the API and cache boundaries are already explicit.
- Deterministic specialist agents are sufficient to evaluate orchestration quality.
- Brownfield “implementation” is a controlled runtime change (feature flags), which is closer to production change management than regenerating the repository.

## Limitations

- No LLM provider is wired; orchestration is the product.
- Sybase schema is Hibernate `ddl-auto=update` (acceptable for prototype, not for regulated prod).
- Rate limiting is in-memory per node (Redis token bucket would be the HA follow-up).
- Geo analytics are not implemented; click records keep hashed IP, user agent, referrer.

## Autonomy boundaries

Agents may analyze, decompose, implement flag changes, test, and document. They may **not** skip change-control or release gates. Reviewers own those decisions. That is the operating principle of the assignment: agents execute under defined autonomy; humans own oversight and final quality.
