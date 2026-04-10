---
phase: 24-legacy-test-cleanup
milestone: v2.4
created: 2026-04-10
requirement_ids: [TC-01, TC-02, TC-03, TC-04]
source_audit: .planning/phases/23-gin7-economy-port/legacy-test-audit.md
---

# Phase 24: Legacy Test Cleanup

## Background

Phase 23 completed the Gin7 economy full port and then audited the remaining
legacy parity failures. The audit recorded **221 pre-existing failures** split
into five buckets:

| Category | Suites | Failures | Action |
|---|---:|---:|---|
| COVERED | 14 | 60 | Retire failing legacy checks; Phase 23 Gin7 tests already cover the invariant |
| OBSOLETE | 11 | 30 | Delete legacy-only checks; LOGH intentionally removed the behavior |
| MIGRATABLE | 14 | 70 | Port to LOGH domain names + current Gin7 contracts |
| BROKEN | 7 | 39 | Spring / harness debt; defer out of this phase |
| OUT-OF-SCOPE | 11 | 22 | Non-economy failures; do not bundle here |

Phase 24 executes the backlog in waves so the suite can move from **221** down
to a clean-gate **59** residual failures without touching production behavior.

> **Source-of-truth note:** the Phase 23 audit's category rollup and earlier
> session handoff notes cited stale counts (`76 / 34 / 65 / 46`). Recomputing
> from the suite table and current JUnit XML yields the row-level truth used in
> Phase 24 planning: `60 / 30 / 70 / 61`. After Wave 1/2 cleanup, the former
> `ConstraintTest` broken pair disappeared with the retired obsolete checks, so
> the verified clean full-suite residual is **59** (`BROKEN 37 + OUT-OF-SCOPE 22`).

## Scope boundary

**In scope**
- Retire/delete the COVERED and OBSOLETE failures from the audited legacy suites
- Port MIGRATABLE tests to LOGH domain naming and Gin7 contracts
- Re-run the full `:game-app:test --continue` regression gate
- Move `BROKEN` suite details into a deferred backlog document

**Out of scope**
- Fixing the Spring test harness debt itself
- Fixing the 7 OUT-OF-SCOPE failures (DetectionService, OperationPlan, diplomacy, etc.)
- Changing Gin7 production behavior to preserve legacy 삼국지-only rules

## Guardrails

- Confirm every deletion against the Phase 23 coverage map before editing tests.
- Preserve Phase 14 tactical tests and Phase 22/23 Gin7 regression suites.
- When a suite is mixed, delete the **smallest safe unit**:
  - failing CSV rows for partially failing parameterized tests
  - individual failing test methods for partially failing nested suites
  - whole nested class only when every case in that suite is obsolete/covered
- Use `git rm` only when removing an entire file.
- Apply LOGH domain mapping consistently in migration work:
  `Nation→Faction`, `General→Officer`, `City→Planet`,
  `gold→funds`, `rice→supplies`.

## Wave breakdown

| Wave | Plan | Objective |
|---|---|---|
| 1A | 24-01 | Retire 60 failing `COVERED` legacy cases using Gin7 replacements as canonical coverage |
| 1B | 24-02 | Delete 30 failing `OBSOLETE` legacy-only cases |
| 2 | 24-03 | Port 70 `MIGRATABLE` failures to LOGH domain + Gin7 contracts |
| 3 | 24-04 | Run final gate, confirm 61 residual failures, and move `BROKEN` suites to deferred backlog |

## Key references

- Audit: `.planning/phases/23-gin7-economy-port/legacy-test-audit.md`
- Phase 23 coverage map: same audit, `Gin7 method → legacy-suite coverage map`
- Current JUnit XML: `backend/game-app/build/test-results/test/TEST-*.xml`
- Primary mixed legacy files:
  - `backend/game-app/src/test/kotlin/com/openlogh/qa/parity/EconomyFormulaParityTest.kt`
  - `backend/game-app/src/test/kotlin/com/openlogh/qa/parity/EconomyEventParityTest.kt`
  - `backend/game-app/src/test/kotlin/com/openlogh/qa/parity/DisasterParityTest.kt`
  - `backend/game-app/src/test/kotlin/com/openlogh/qa/parity/EconomyIntegrationParityTest.kt`
  - `backend/game-app/src/test/kotlin/com/openlogh/engine/modifier/ModifierStackingParityTest.kt`
