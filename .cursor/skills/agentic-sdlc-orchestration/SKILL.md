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
- Failures after `app.orchestration.max-retries` trigger feature-flag rollback.
- Replan resets downstream completed stages from task decomposition onward.
- Do not log secrets; `PolicyGuardrail` rejects password/token patterns in requirements.
- Specialist agents stay behind `SpecialistAgent`; do not put orchestration logic in controllers.

## Graphs

Definitions live in `ScenarioCatalog`. Keep explicit `dependsOn` edges. Parallel work must join before release.

## Demo users

- engineer / Engineer@123
- reviewer / Reviewer@123
- admin / Admin@123
