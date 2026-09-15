---
name: agentic-sdlc-orchestration
description: Run and extend the Schwab URL shortener agentic SDLC orchestrator. Use when starting greenfield, brownfield, or ambiguous workflows, approving gates, replanning, measuring orchestration metrics, or changing DAG/governance behavior.
---

# Agentic SDLC orchestration

## When to use

User asks to run scenarios, add a stage, change approval rules, inspect lineage, or reason about retries/rollback.

## How workflows run

1. `POST /api/v1/orchestration/scenarios/{greenfield|brownfield|ambiguous}` with `{ "requirement": "..." }` as an ENGINEER.
2. Engine executes ready DAG stages, including parallel waves, until a human gate or terminal state.
3. `POST /api/v1/orchestration/workflows/{id}/approve` as REVIEWER/ADMIN.
4. Inspect `GET /api/v1/orchestration/workflows/{id}` and `GET /api/v1/orchestration/metrics`.

## Invariants

- High-impact actions stay behind approval gates.
- Implementation generates a reviewable unified diff under `workbench/{workflowId}` and persists it as a change set.
- Failures retry with backoff; testing failures attempt validation-driven repair; then a reduced-scope fallback runs before rollback.
- Parallel-ready DAG waves execute on `orchestrationExecutor` (virtual threads).
- Replan selectively resets the changed upstream stage and its transitive dependents (`changedStageId`).
- Do not log secrets; `PolicyGuardrail` rejects password/token patterns in requirements.
- Specialist agents stay behind `SpecialistAgent`; do not put orchestration logic in controllers.

## Graphs

Definitions live in `ScenarioCatalog`. Keep explicit `dependsOn` edges. Parallel work must join before release.

## Demo users

- engineer / Engineer@123
- reviewer / Reviewer@123
- admin / Admin@123
