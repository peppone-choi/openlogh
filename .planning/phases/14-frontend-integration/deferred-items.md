# Phase 14 — Deferred Items

> Out-of-scope findings logged during execution. Not fixed here; flagged for planner.

## Pre-existing Vitest Failures (found during 14-05 execution)

Discovered while running full `pnpm test --run` suite for Wave 0 scaffold sanity check.
All 7 failures are in files NOT touched by plan 14-05.

| File | Failure Count | Issue |
| ---- | ------------- | ----- |
| `frontend/src/components/game/command-select-form.test.ts` | 1 | `command-select-form disabled command styling > onClick does not gate on cmd.enabled` |
| `frontend/src/components/game/game-dashboard.test.tsx` | 3 | Source-scan assertions for legacy fields: `joinMode (참가 모드)`, `officerCity (관할 행성)`, `bill (봉급)` — these are 삼국지 legacy tests still scanning for terms removed during gin7 rewrite |
| `frontend/src/components/game/record-zone.test.ts` | 3 | `stripYear` regex `/\d+년\s*/` does NOT match the UC-prefixed LOGH date format `UC 795.9` — the tests still expect 삼국지 `"200년 1월"` format |

### Why not fixed here

Plan 14-05 scope is strictly "create Wave 0 test scaffold stubs". Per the GSD scope boundary:
> Only auto-fix issues DIRECTLY caused by the current task's changes. Pre-existing warnings, linting errors, or failures in unrelated files are out of scope.

### Suggested owner

The `record-zone.test.ts` and `game-dashboard.test.tsx` failures look like date-format / field-rename leftovers from the gin7 rewrite (삼국지 `"200년 1월"` → LOGH `"UC 795.9"`). They should be folded into a maintenance plan or `/gsd:debug` session — likely a 15-minute batch fix.

The `command-select-form.test.ts` failure probably needs the same gin7 command-enabled flag contract as the upcoming plan 14-13 canCommandUnit work.


## Pre-existing compileTestKotlin Break (found during 14-01 execution)

Discovered while re-running `./gradlew :game-app:test --tests "com.openlogh.dto.*"` after committing 14-01 Task 1 + Task 2. The initial :game-app:compileKotlin + :game-app:compileTestKotlin pair had previously succeeded in 14-01's own execution window (both DTO tests passed 100%), but a concurrent wave has since landed an intentional TDD-RED test that now blocks the full test task.

| File | Commit | Issue |
| ---- | ------ | ----- |
| `backend/game-app/src/test/kotlin/com/openlogh/controller/BattleSummaryEndpointTest.kt` | 7bb96d38 `test(14-02): add failing test for BattleSummary REST endpoint` | 13 `Unresolved reference` errors referencing `operationMultiplier`, `baseMerit`, `totalMerit`, `rows`, `buildBattleSummary`, `getBattleSummary` — these are the plan 14-02 RED phase, waiting on its own GREEN commit. |

### Why not fixed here

Plan 14-01 scope is strictly "backend DTO extensions + toDto builder wiring". The 14-02 controller/service shape is a separate plan on the same wave. Per the GSD scope boundary:
> Only auto-fix issues DIRECTLY caused by the current task's changes. Pre-existing warnings, linting errors, or failures in unrelated files are out of scope.

### Suggested owner

Plan 14-02 owns the GREEN phase. No handoff needed — the next 14-02 executor will unblock compileTestKotlin automatically. The 14-01 DTO test classes (`TacticalBattleDtoExtensionTest`, `CommandHierarchyDtoMappingTest`) had already been verified passing at 100% in the 14-01 execution window before 14-02's RED commit arrived.


## Pre-existing `pnpm verify:parity` failure (found during 14-06 execution)

Discovered while running the plan 14-06 `<verification>` block. `frontend/package.json`'s `verify:parity` script runs `node ../scripts/verify/frontend-parity.mjs`, which asserts the existence of 17 OpenSamguk legacy Next.js routes and 5 Playwright parity specs:

| Missing | Category | Count |
| ------- | -------- | ----- |
| `frontend/src/app/(game)/page.tsx` + 16 other `route:*` entries | OpenSamguk legacy routes | 17 |
| `frontend/e2e/parity/0{1..5}-*.spec.ts` | OpenSamguk legacy E2E parity specs | 5 |

None of these assets exist in the current LOGH codebase — they were removed during the gin7 rewrite (v2.0). The script itself (`scripts/verify/frontend-parity.mjs`) is stale legacy tooling from the OpenSamguk fork that nobody has updated to the new LOGH route layout.

### Why not fixed here

Plan 14-06 scope is strictly "TypeScript type sync with Phase 14 DTO extensions" — extending `frontend/src/types/tactical.ts` to mirror the backend Kotlin DTOs. The `frontend-parity.mjs` script checks OpenSamguk route/spec file existence, not Kotlin↔TS DTO field parity (those are two completely different things).

The actual type-parity verification the plan cares about is `verify-type-parity` skill (listed in 14-VALIDATION.md line 141) + `pnpm typecheck` (line 32). Both are green after 14-06 changes.

Per the GSD scope boundary:
> Only auto-fix issues DIRECTLY caused by the current task's changes. Pre-existing warnings, linting errors, or failures in unrelated files are out of scope.

### Suggested owner

Either:
1. A maintenance plan that rewrites `scripts/verify/frontend-parity.mjs` to check the actual LOGH route layout (`frontend/src/app/**/page.tsx` directory walk), or
2. Deletion of the script entirely if it no longer serves a purpose after the OpenSamguk → LOGH rewrite.

This is pre-existing breakage from before Phase 14 started — not a regression introduced by Wave 2 type-sync work.

