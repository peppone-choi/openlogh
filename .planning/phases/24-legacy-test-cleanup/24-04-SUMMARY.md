---
phase: 24-legacy-test-cleanup
plan: 24-04-regression-gate-and-deferred-backlog
milestone: v2.4
subsystem: tests/planning
wave: 3
tags: [legacy-test-cleanup, regression-gate, deferred-backlog]
requirements: [TC-04]
completed: 2026-04-10
---

# Phase 24 Plan 04: Final regression gate + deferred backlog Summary

## One-liner

Ran a clean Java 21 full-suite gate after the Phase 24 cleanup passes and
reduced the residual baseline from 221 failures to **59 failures**, with no
remaining `COVERED`, `OBSOLETE`, or `MIGRATABLE` suites.

## Verification

```text
cd backend
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :game-app:cleanTest :game-app:test --continue

1838 tests completed, 59 failed, 1 skipped
```

## Residual breakdown

- `BROKEN`: 37
- `OUT-OF-SCOPE`: 22

See `deferred-items.md` for the suite-by-suite backlog.

## Acceptance criteria

- [x] Full-suite failure count is 59
- [x] No `COVERED` failures remain
- [x] No `OBSOLETE` failures remain
- [x] No `MIGRATABLE` failures remain
- [x] Remaining suites are documented in `deferred-items.md`
