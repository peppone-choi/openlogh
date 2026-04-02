---
phase: 07-command-parity
plan: 01
subsystem: command
tags: [parity, golden-value, test, civil, military]
dependency_graph:
  requires: []
  provides: [civil-command-parity-tests, military-command-parity-tests]
  affects: [command-system, turn-engine]
tech_stack:
  added: []
  patterns: [golden-value-testing, deterministic-seed-rng]
key_files:
  created: []
  modified:
    - backend/game-app/src/test/kotlin/com/opensam/command/GeneralCivilCommandTest.kt
    - backend/game-app/src/test/kotlin/com/opensam/command/GeneralMilitaryCommandTest.kt
decisions:
  - "Golden seed 'golden_parity_seed' used for all deterministic parity tests"
  - "Constraint failure tests integrated into each command parity test (not separate)"
  - "Kotlin-only commands (순찰, 요격, 좌표이동) verify entity mutation + log generation, not PHP golden values"
metrics:
  duration: 34min
  completed: "2026-04-02T02:31:00Z"
---

# Phase 07 Plan 01: Civil/Military Command Golden Value Parity Summary

Golden value parity tests for 12 civil + 15 military commands using deterministic LiteHashDRBG seed, plus basic operation tests for 3 Kotlin-only commands.

## What Was Done

### Task 1: Civil Command Golden Value Parity Tests

Added 22 parity test methods to `GeneralCivilCommandTest.kt` covering all 12 non-economy civil commands:

- **Golden value tests** (12): 정착장려, 주민선정, 기술연구, 모병, 징병, 훈련, 사기진작, 소집해제, 숙련전환, 물자조달, 단련, 군량매매
- **Constraint failure tests** (10): Each command has 1-2 constraint failure assertions integrated
- **Determinism verification**: Same seed produces identical JSON message output
- **Log color tag parity**: `<R>`, `<S>`, `<C>`, `<G>`, `<1>` tags verified in exact position

Each test uses fixed seed `golden_parity_seed` with `LiteHashDRBG.build()`, asserts exact JSON delta values (gold, rice, experience, dedication, stat exp, city changes), critical result string, and log message color tags.

### Task 2: Military Command Golden Value Parity Tests + Kotlin-only Tests

Added 30 test methods to `GeneralMilitaryCommandTest.kt`:

- **Golden value tests** (15): 출병, 귀환, 접경귀환, 강행, 거병, 전투태세, 화계, 첩보, 선동, 탈취, 파괴, 요양, 방랑, 집합
- **Constraint failure tests** (9): Per-command constraint validation
- **Kotlin-only tests** (6): 순찰, 요격, 좌표이동 with entity mutation + log generation verification
- **HIGH-RISK commands**: 출병 (3 tests), 접경귀환 (2 tests), 화계 (2 tests), 첩보 (2 tests)

## Decisions Made

1. **Golden seed**: `golden_parity_seed` chosen as canonical seed for all command parity tests
2. **Integrated constraints**: Constraint failure tests placed alongside golden value tests per command (not in separate test class) per plan D-04 decision
3. **Kotlin-only strategy**: 순찰/요격/좌표이동 test entity mutations (lastTurn, destX/destY) and log generation rather than PHP comparison (per D-07)

## Deviations from Plan

None - plan executed exactly as written.

## Test Results

- `GeneralCivilCommandTest`: 22 parity tests + existing tests = all GREEN
- `GeneralMilitaryCommandTest`: 30 parity tests + existing tests = all GREEN  
- `CommandParityTest`: all existing tests = GREEN (no regression)

## Commits

| Task | Commit | Description |
|------|--------|-------------|
| 1 | 34fc977 | Golden value parity tests for 12 civil commands |
| 2 | 1b8396f | Golden value parity tests for 15 military + 3 Kotlin-only commands |

## Known Stubs

None - all tests produce verified golden value assertions with deterministic output.

## Self-Check: PASSED
