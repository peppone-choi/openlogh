---
phase: 14-frontend-integration
plan: 03
subsystem: engine
tags: [kotlin, spring-boot, tactical-battle, fog-of-war, sensor-range, tdd]

# Dependency graph
requires:
  - phase: 14-01
    provides: TacticalUnitDto.sensorRange field + temporary inline derivation in TacticalBattleService.toUnitDto
  - phase: 10-tactical-combat
    provides: TacticalBattleEngine tick loop structure with updateCommandRange step
  - phase: 08-tactical-engine
    provides: TacticalUnit data class in engine package
provides:
  - Cached TacticalUnit.sensorRange field (server-authoritative per-tick fog-of-war visibility radius)
  - SensorRangeFormula private object encoding the D-19 formula anchors
  - Top-level computeSensorRange(unit) helper for same-package test access
  - Tick-loop integration — sensorRange recomputes immediately after commandRange update
  - BattleTriggerService seeds initial sensorRange at battle start
  - TacticalBattleService.toUnitDto now reads the cached field (replaces 14-01 inline derivation)
affects: [14-06, 14-11]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Cached-field computation: store derived values on the runtime model when recomputed every tick (vs. re-deriving in every DTO build)"
    - "Top-level wrapper function over internal object for same-package test access (avoids reflection, keeps formula private)"
    - "Formula pinning via 5-anchor TDD test (default, high, zero, injured, dead) — becomes the migration guard when the formula is tuned"

key-files:
  created:
    - "backend/game-app/src/test/kotlin/com/openlogh/engine/tactical/SensorRangeComputationTest.kt"
  modified:
    - "backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/TacticalBattleEngine.kt"
    - "backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/BattleTriggerService.kt"
    - "backend/game-app/src/main/kotlin/com/openlogh/service/TacticalBattleService.kt"
    - ".planning/phases/14-frontend-integration/deferred-items.md"

key-decisions:
  - "[Phase 14-03]: SensorRangeFormula as internal object in TacticalBattleEngine.kt (same package as TacticalUnit) rather than a top-level class file — keeps the formula co-located with the consumer and avoids a new file for a 30-line constant+function pair"
  - "[Phase 14-03]: Top-level `internal fun computeSensorRange(unit)` wrapper exposes the formula to same-package tests without promoting the object to public or using reflection"
  - "[Phase 14-03]: Default slider DEFAULT_SENSOR_SLIDER=17 matches 100/6 rounded down — aligns with the plan's 'balanced' anchor so sensor=17 yields exactly BASE_SENSOR_RANGE=150.0"
  - "[Phase 14-03]: Tick-loop placement: sensorRange recompute runs in Step 1 (after updateCommandRange, inside the same aliveUnits loop) so it's visible to Step 2.5 DetectionService without an extra pass"
  - "[Phase 14-03]: BattleTriggerService.buildInitialState seeds sensorRange explicitly via SensorRangeFormula.compute(unit) rather than relying on the TacticalUnit data-class default (150.0), so units that start with non-default energy allocations get a correct first-tick broadcast radius"
  - "[Phase 14-03]: 14-01 inline formula in TacticalBattleService.toUnitDto (using DetectionCapability.baseRange × sensor/50 × isStopped) replaced by a simple unit.sensorRange read — the new formula is simpler, energy-centric, and matches the plan's D-19 anchors; the DetectionCapability.baseRange-based formula remained only as a 14-01 placeholder and is no longer referenced anywhere"
  - "[Phase 14-03]: TDD RED+GREEN compressed into one commit (matching 14-01 Wave 1 precedent) because game-app tests share a single compile module — a separate RED commit would wedge parallel Wave 2 executors compiling against an intentionally-broken test tree"

patterns-established:
  - "Cached derivation over inline derivation: when a per-tick formula feeds both the DTO builder and downstream in-engine consumers, cache the result on the runtime model and read it everywhere"
  - "internal object + top-level wrapper: expose private engine logic to same-package tests without using reflection or promoting the object's visibility"
  - "Formula anchor tests: pin 5 canonical inputs (default / high / zero / injured / dead) as the migration guard whenever a tuning value is later retuned"

requirements-completed: [FE-05]

# Metrics
duration: ~40 min (dominated by Gradle compile + test cycle)
completed: 2026-04-09
---

# Phase 14 Plan 03: Backend — sensorRange field + computation + DTO wiring Summary

**Cached TacticalUnit.sensorRange field derived from energy.sensor every tick via SensorRangeFormula, replacing the 14-01 temporary inline derivation and pinning the fog-of-war formula with a 5-anchor JUnit test so FE-05 (Phase 14 fog of war, plan 14-11) reads server-authoritative visibility radius directly from the DTO without replicating the formula on the client.**

## Performance

- **Duration:** ~40 min (most of it waiting on Gradle daemon + Kotlin compile; the edits themselves were 5 minutes)
- **Started:** 2026-04-09T19:50:00Z
- **Completed:** 2026-04-09T20:10:00Z
- **Tasks:** 1 (TDD: RED test → GREEN implementation)
- **Files modified:** 3 source + 1 new test file + 1 deferred-items log entry

## Accomplishments

- **TacticalUnit.sensorRange is now a cached runtime field.** Default 150.0 (= BASE_SENSOR_RANGE), recomputed on every tick in the engine's Step 1 loop, and seeded at battle init in BattleTriggerService so the very first tick broadcast carries a real value.
- **SensorRangeFormula object encodes the D-19 contract.** Five named constants (`BASE_SENSOR_RANGE=150.0`, `MIN_SENSOR_RANGE=30.0`, `MAX_SENSOR_RANGE=500.0`, `DEFAULT_SENSOR_SLIDER=17`, `INJURY_MODIFIER=0.7`, `INJURY_HP_THRESHOLD=0.30`) make future tuning obvious and grep-friendly.
- **DTO builder now reads the cached field.** `TacticalBattleService.toUnitDto` dropped the 14-01 inline DetectionCapability-based formula (`baseRange × sensor/50 × isStopped`) in favor of a single `sensorRange = unit.sensorRange` assignment — the engine is the single source of truth per D-19, and the DTO path stays cheap.
- **5-anchor pinning test passing.** `SensorRangeComputationTest` verifies: default slider → 150.0, high allocation scales to the 400-500 band (capped), zero allocation clamps to 30.0, HP<30% triggers the 0.7 injury modifier (150→105), dead unit returns 0.0. Passes in 0.066s on a fresh Kotlin daemon.
- **Wave 2 parallel-safe commit.** Single atomic commit (`8532a648`, `--no-verify`) to avoid pre-commit hook contention with other Wave 2 executors (14-06, 14-08) and to avoid a standalone RED commit that would have wedged concurrent test compiles.

## Task Commits

Task 1 was committed atomically via `git commit --no-verify` (parallel wave protocol):

1. **Task 1: Add sensorRange field + per-tick computation + pinning test** — `8532a648` (feat)

_Note: The task was marked TDD in the plan (`tdd="true"`). In practice the RED and GREEN phases were compressed into a single commit for the same reason 14-01 did so: the test file and the source file share one Kotlin compile module, and a standalone RED commit would break `:game-app:compileTestKotlin` mid-wave, wedging every other Wave 2 parallel executor that runs gradle tests. The test file is still the forward contract: any future tuning of the formula constants is guarded by the 5 anchor assertions._

## Files Created/Modified

### Created

- `backend/game-app/src/test/kotlin/com/openlogh/engine/tactical/SensorRangeComputationTest.kt` — 5-test JUnit pinning suite: default slider → 150.0, high allocation → 400-500 band (capped at 500), zero allocation → 30.0 min-clamp, injured (HP<30%) → 0.7× modifier (150→105), dead unit → 0.0.

### Modified

- `backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/TacticalBattleEngine.kt`:
  - Added `var sensorRange: Double = 150.0` to the `TacticalUnit` data class constructor (line 67, directly after `commandRange`).
  - Added `internal object SensorRangeFormula` with the 5 constants + `compute(unit): Double` function (new block after `BattleTickEvent` data class).
  - Added `internal fun computeSensorRange(unit): Double` top-level wrapper so same-package tests can pin the formula without reflection.
  - Added `unit.sensorRange = SensorRangeFormula.compute(unit)` inside the Step 1 (`updateCommandRange`) loop.
- `backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/BattleTriggerService.kt`:
  - Added a `for (unit in units) unit.sensorRange = SensorRangeFormula.compute(unit)` pass at the end of `buildInitialState` so the first-tick broadcast carries a real sensorRange (not the 150.0 data-class default).
- `backend/game-app/src/main/kotlin/com/openlogh/service/TacticalBattleService.kt`:
  - Removed the 14-01 inline derivation (`val sensorEnergyMultiplier = (unit.energy.sensor / 50.0) ...`) from `toUnitDto`.
  - Replaced `sensorRange = sensorRange` (local var) with `sensorRange = unit.sensorRange` (cached field read) at line 718.
- `.planning/phases/14-frontend-integration/deferred-items.md`:
  - Appended a pre-existing-test-failure log entry for `DetectionServiceTest > commandRange increases by expansionRate each tick up to maxRange` — 1/247 failing, unrelated to 14-03 scope (see "Issues Encountered" below).

## Decisions Made

See frontmatter `key-decisions` — 7 decisions captured, most important being:

1. **SensorRangeFormula as internal object co-located in TacticalBattleEngine.kt** — not a new file; keeps the 30-line constant+function pair next to the TacticalUnit data class that consumes it.
2. **Top-level `computeSensorRange()` wrapper** — exposes the private object to same-package tests without promoting it to public or using reflection (simpler than the plan's suggested `SensorRangeFormulaTestAccess` helper).
3. **TDD RED+GREEN compressed into one commit** — matches 14-01's Wave 1 precedent; a standalone RED commit would wedge the shared `:game-app:compileTestKotlin` task for every parallel Wave 2 executor.
4. **DEFAULT_SENSOR_SLIDER=17** — `100/6 ≈ 17` and the plan's acceptance criterion is "default slider → 150.0", so using 17 as the denominator makes `sensor=17 → multiplier=1.0 → range=150.0` exact (not 150.33 or similar drift).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] 14-01 inline sensorRange formula had to be fully removed (not just the local `val`) to keep toUnitDto compilable**

- **Found during:** Task 1, Step 5 (updating `toUnitDto` to read `unit.sensorRange`).
- **Issue:** 14-01 Wave 1 had populated the TacticalUnitDto.sensorRange field by computing `val sensorRange = unit.detectionCapability.baseRange × (sensor/50) × (1.3 if stopped else 1.0)` inline in `toUnitDto` (around lines 691-696). The plan's Step 5 said "replace the temporary `sensorRange = 0.0` placeholder with `sensorRange = unit.sensorRange`" — but there was no `0.0` placeholder (14-01 already filled it in). Leaving both the inline `val sensorRange` AND the new `unit.sensorRange` read would cause a Kotlin "variable is never used" warning AND the old formula would still run every DTO build even though nothing consumed the result.
- **Fix:** Deleted the entire 14-01 inline derivation block (6 lines) and replaced it with a docstring comment explaining that the engine is now the single source of truth per D-19. Changed the `sensorRange = sensorRange` call-site to `sensorRange = unit.sensorRange`.
- **Files modified:** `backend/game-app/src/main/kotlin/com/openlogh/service/TacticalBattleService.kt`
- **Verification:** Grep confirms `grep -n "sensorRange = unit.sensorRange"` returns exactly 1 match at line 718; the old `DetectionCapability.baseRange × sensorEnergyMultiplier` line is gone. Full :game-app:compileKotlin + :game-app:compileTestKotlin BUILD SUCCESSFUL in 1m 10s.
- **Committed in:** `8532a648`

**2. [Rule 2 - Missing Critical] Plan acceptance criterion `TacticalBattleServiceTest` does not exist in the repository**

- **Found during:** Post-Task-1 verification pass.
- **Issue:** The plan's verification block said "`./gradlew :game-app:test --tests "com.openlogh.service.TacticalBattleServiceTest"` still passes (no regression)" — but `find backend -name "TacticalBattleServiceTest*"` returns zero hits. The file simply doesn't exist. The plan's acceptance criterion was impossible to execute verbatim.
- **Fix:** Substituted a broader regression sweep: `./gradlew :game-app:test --tests "com.openlogh.engine.tactical.*"` (247 tests covering every file in the tactical engine package, which is the subsystem actually touched by this plan). 246/247 passed. The one failure is in `DetectionServiceTest > commandRange increases by expansionRate each tick up to maxRange` — see "Issues Encountered" below; pre-existing, not a regression.
- **Files modified:** None (verification-only substitution).
- **Verification:** See the "Issues Encountered" section below.
- **Committed in:** N/A

---

**Total deviations:** 2 auto-fixed (1 blocking, 1 missing-critical verification substitution)

**Impact on plan:** Both are housekeeping. Deviation 1 was forced because the 14-01 inline formula had already filled in what the plan expected as a `0.0` placeholder (plan drift between the 14-01 actual and the 14-03 expected starting state). Deviation 2 was a verification-substitution — the intended regression guard still runs, just against a different test target. No scope creep; all work stays inside the 14-03 "sensorRange field + formula + DTO wiring" boundary.

## Issues Encountered

- **Pre-existing test failure: `DetectionServiceTest > commandRange increases by expansionRate each tick up to maxRange`.** Discovered during the regression verification pass (247 tactical-package tests; 246 pass, 1 fails). The test sets `CommandRange(currentRange=0, maxRange=100, expansionRate=1.0)` on a unit and expects `currentRange` to cap at 100 after 301 ticks — but only reaches 6.0. **Root cause (suspected):** Phase 9's `processOutOfCrcUnits` step (which runs BEFORE Step 1 `updateCommandRange`) calls `CommandRange.resetOnCommand()` on units whose commander is out of CRC, zeroing `currentRange` before it can accumulate. The test is from Phase 03 (commit `79b98122 feat(phase-03): DetectionService + detectionMatrix + commandRangeMax 완성`) and was never updated for Phase 9's CRC gating refactor. **Why not fixed here:** Per GSD scope boundary, only auto-fix issues DIRECTLY caused by current task's changes. My 14-03 edits added `unit.sensorRange = SensorRangeFormula.compute(unit)` AFTER the existing `updateCommandRange(unit)` call — the commandRange expansion path is entirely untouched. The failure is pre-existing, not a regression. Logged to `.planning/phases/14-frontend-integration/deferred-items.md` with full root-cause analysis and suggested ownership (future `/gsd:debug` session or Phase 9 follow-up plan).
- **Gradle daemon contention with parallel Wave 2 executors.** During the initial `:game-app:test` run, 7 Java/Kotlin processes (including a Temurin 25 daemon from a previous session, a Temurin 17 daemon for the current run, and the Kotlin compile daemon) were all alive simultaneously. The single-test run (`SensorRangeComputationTest`) took ~70 seconds because of daemon warmup + other-wave compile contention on the shared `:shared:compileKotlin` → `:game-app:compileKotlin` chain. Same environmental side-effect documented in 14-01's SUMMARY — not a 14-03 regression. Resolution: let it finish; both the single-test and the 247-test regression run eventually completed with only the pre-existing failure noted above.
- **Java 17 toolchain:** Had to explicitly set `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home` because the default shell picks up Temurin 25, which the Gradle wrapper rejects. Pre-existing environmental concern (noted in 14-01's SUMMARY too).

## User Setup Required

None — this plan is pure backend Kotlin code with no external service configuration, no DB migrations, and no new dependencies.

## Next Phase Readiness

- **14-06 (frontend types sync) — already merged at HEAD.** The `TacticalUnitDto.sensorRange: Double` field was already in the frontend type mirror (14-06 committed `426ee9d0` before the deferred-items check but after my 14-03 commit). Since the DTO field name and type did not change, 14-06 needs no update.
- **14-08 (WebSocket battle channel) — ready.** When the WebSocket STOMP handlers ship, every tick broadcast will automatically include the cached sensorRange as part of `TacticalUnitDto`. No additional wiring needed.
- **14-11 (fog of war frontend) — unblocked.** FE can now read `sensorRange` off every `TacticalUnitDto` and draw a per-friendly-unit visibility circle. Any enemy fleet position farther than the max friendly `sensorRange` on the player's side becomes a "stale last-known position" per D-20. The server guarantees that `sensorRange` reflects the current `energy.sensor` slider value on the NEXT tick after the slider changes (so UI feedback is tight).
- **Future retuning:** If the formula constants are ever retuned (e.g., `INJURY_MODIFIER=0.5` for a harsher combat feel), the 5-anchor `SensorRangeComputationTest` will fail loudly and the re-tuner can update the test assertions in the same commit as the constant change.

### Blockers / concerns

- None for 14-03's own scope.
- The pre-existing `DetectionServiceTest.commandRange*` failure is tracked in `deferred-items.md` but does NOT block 14-03 completion. 14-03's own test (`SensorRangeComputationTest`) passes 5/5.

---

*Phase: 14-frontend-integration*
*Plan: 03*
*Completed: 2026-04-09*

## Self-Check: PASSED

- [x] `backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/TacticalBattleEngine.kt` modified — verified via `grep -n "var sensorRange: Double"` → line 67; `grep -c "SensorRangeFormula"` → 5 matches; tick-loop call at line 356
- [x] `backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/BattleTriggerService.kt` modified — verified via `grep -n "unit.sensorRange = SensorRangeFormula"` → line 193 (inside buildInitialState)
- [x] `backend/game-app/src/main/kotlin/com/openlogh/service/TacticalBattleService.kt` modified — verified via `grep -n "sensorRange = unit.sensorRange"` → line 718
- [x] `backend/game-app/src/test/kotlin/com/openlogh/engine/tactical/SensorRangeComputationTest.kt` created — verified via file existence; `head -5` of `TEST-com.openlogh.engine.tactical.SensorRangeComputationTest.xml` shows `tests="5" skipped="0" failures="0" errors="0"`
- [x] Commit `8532a648` exists — verified via `git log --oneline 8532a648~0..8532a648` returns `8532a648 feat(14-03): add TacticalUnit.sensorRange field + per-tick formula (FE-05/D-19)`
- [x] `./gradlew :game-app:compileKotlin :game-app:compileTestKotlin` BUILD SUCCESSFUL — verified; implicitly runs as part of the test task which reported BUILD SUCCESSFUL in 1m 10s
- [x] `./gradlew :game-app:test --tests "*SensorRange*"` exits 0 with 5 tests passing — verified via test XML report (`tests="5" skipped="0" failures="0" errors="0" time="0.066"`)
- [x] Acceptance grep 1: `var sensorRange: Double` returns at least 1 match — PASS (1 match at line 67)
- [x] Acceptance grep 2: `SensorRangeFormula` returns at least 2 matches — PASS (5 matches: object definition, test wrapper, tick-loop call, buildInitialState call, docstring)
- [x] Acceptance grep 3: `unit.sensorRange = SensorRangeFormula` returns at least 1 match in tick loop — PASS (1 match at TacticalBattleEngine.kt:356, another at BattleTriggerService.kt:193)
- [x] Acceptance grep 4: `sensorRange = unit.sensorRange` returns 1 match in TacticalBattleService.kt — PASS (line 718)
- [x] No pre-existing test regresses — 246/247 tactical-package tests pass; the 1 failure (`DetectionServiceTest.commandRange*`) is pre-existing (last touched `ec65d2f4`, before any 14-03 work) and documented in deferred-items.md as out-of-scope.
