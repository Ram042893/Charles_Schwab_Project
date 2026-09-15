# Scenarios

Each scenario is a first-class workflow with its own DAG. Start it, inspect stages/decisions, then approve gates as a reviewer.

## 1. Greenfield — new URL shortener

**Requirement example:** “Build a URL shortener with auth, analytics, cache and reliability.”

**Decomposition (happy path):**

```
REQUIREMENT_ANALYSIS
        ↓
TASK_DECOMPOSITION
        ↓
ARCHITECTURE_DESIGN
       / \
IMPLEMENTATION   TEST_PLANNING     ← parallel
       \ /
    TEST_EXECUTION                 ← sync join
       / \
DOCUMENTATION  SECURITY_COMPLIANCE ← parallel
       \ /
 RELEASE_APPROVAL_GATE             ← human
        ↓
 RELEASE_READINESS
```

**What the agents do**

- Normalize the core capability set (shorten, redirect, analytics, JWT, Redis, Sybase).
- Verify live beans (`urlShortenerService`, auth, flags).
- Keep advanced flags **off**.
- Run in-process tests: public shorten, SSRF reject, analytics increment.

**Validation:** workflow waits at `RELEASE_APPROVAL_GATE`; reviewer approval completes `RELEASE_READINESS`.

## 2. Brownfield — enhance the running system

**Requirement example:** “Add custom aliases, expiration and analytics export.”

**Extra nodes:** `IMPACT_ANALYSIS` → `CHANGE_CONTROL_GATE` (human) before design/implementation; `ROLLBACK_PLAN_VALIDATION` after tests.

**Impacted modules:** `UrlShortenerService`, `FeatureFlagService`, `UrlController`, Redis cache keys, API contract.

**Runtime change:** after change-control approval, `ImplementationAgent` enables the three flags. Tests then create an aliased expiring link and export CSV. Rollback disables the flags.

## 3. Ambiguous — underspecified intent

**Requirement example:** “Make the shortener better and more enterprise ready.”

**Extra nodes:** `AMBIGUITY_DETECTION` → `CLARIFICATION_GATE` → `DYNAMIC_REPLAN`.

The ambiguity agent records open questions (`better`, `faster`, `enterprise ready`) and proposed assumptions. The workflow **does not guess silently**. A reviewer accepts assumptions; the replan agent writes the chosen plan into lineage; downstream stages continue under that context.

## Shared governance

| Control | Behavior |
| --- | --- |
| Human gates | Reviewer/Admin only |
| Reviewable diff | `GET /workflows/{id}/changeset` returns unified diff + content hash |
| Parallel waves | Ready stages run concurrently on virtual-thread executor |
| Repair | Failed validation regenerates a corrected change set once |
| Fallback | After retries, reduced-scope implementation runs before rollback |
| Replan | `POST /workflows/{id}/replan` with optional `changedStageId` resets transitive dependents only |
| Safe-stop | `POST /workflows/{id}/safe-stop` blocks later approvals |
| Metrics | `GET /api/v1/orchestration/metrics` |
| Lineage | `GET /api/v1/orchestration/workflows/{id}` |
