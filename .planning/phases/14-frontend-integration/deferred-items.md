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

