# Architecture

## Problem framing

The product is a URL shortener. The assignment differentiator is a **stateful agentic SDLC orchestrator** that turns a requirement into a gated, auditable engineering outcome. Agents execute specialist stages; humans own high-impact approvals and final quality.

## Components

```
┌──────────────┐     JWT      ┌─────────────────────┐
│  Clients /   │─────────────▶│  API + OpenAPI      │
│  Reviewers   │              └─────────┬───────────┘
└──────────────┘                        │
                    ┌───────────────────┼───────────────────┐
                    │                   │                   │
            URL shortener        Orchestration         Identity
            cache + analytics    DAG engine            JWT/RBAC
                    │                   │                   │
                    └─────────┬─────────┴─────────┬─────────┘
                              │                   │
                         Sybase ASE            Redis
```

### URL shortener

- `POST /api/v1/urls` creates a short code after SSRF checks.
- `GET /s/{code}` is public, Redis-cached, and records hashed-IP click events.
- Custom alias, expiration, and CSV export are **feature flags** so brownfield work is a real runtime change, not a story about code that already shipped with every flag on.

### Agentic orchestration

`WorkflowEngine` loads an explicit DAG from `ScenarioCatalog`:

- **Entry/exit gates** on each node (dependency completion in, artifact + status out).
- **Parallel paths** (`IMPLEMENTATION` // `TEST_PLANNING`) executed concurrently on a virtual-thread executor, then a **join** at `TEST_EXECUTION`.
- **Reviewable change sets**: implementation writes source/config under `workbench/{workflowId}` and stores a unified diff for reviewer inspection.
- **Human approval** for change control, clarification, and release.
- **Bounded retries** with backoff, **validation-driven repair**, then **reduced-scope fallback**, then **rollback** of feature flags + workbench files.
- **Safe-stop** and **selective replan** (reset changed upstream stage + transitive dependents).
- **Decision lineage** (`decision_records`) and **audit events**.
- **Metrics**: success rate, retries, rollbacks, e2e latency, MTTR.

Specialist agents are deterministic and in-process (no hidden LLM call). That keeps the prototype runnable, testable, and honest about autonomy boundaries. The agent interface is pluggable if an LLM adapter is added later.

## Control flow

1. Engineer submits a scenario + requirement.
2. Engine persists a `WorkflowInstance` and `StageExecution` rows.
3. Ready stages (all dependencies `COMPLETED`) run on virtual threads.
4. High-impact nodes create `ApprovalRequest` and pause (`WAITING_APPROVAL`).
5. Reviewer approves or rejects. Approval resumes topological execution.
6. Failure after retries triggers compensating rollback and `ROLLED_BACK`.

## Key decisions

| Decision | Rationale |
| --- | --- |
| Modular monolith | Orchestrator must validate the live shortener in one process with shared transactions |
| Feature flags for brownfield | Demonstrates change control, impact, and rollback on a running system |
| JWT + RBAC | Engineers execute; reviewers approve; public redirect stays unauthenticated |
| Redis cache | Redirect is the hot path; flags and counts are read-mostly |
| Sybase dialect + jTDS | Matches the required production datastore; H2 keeps tests fast (H2 2.x dropped Sybase mode) |
| Deterministic agents | Orchestration quality can be evaluated without an external model key |

## Security and reliability

- Private/loopback/metadata URLs are rejected.
- Click analytics store a hash of IP, not the raw address.
- Actuator health/info is public; other actuator endpoints require auth.
- Rate limit on create.
- Correlation ID on every request (`X-Correlation-ID`).
