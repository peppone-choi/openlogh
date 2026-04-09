---
phase: 14-frontend-integration
plan: 01
subsystem: api
tags: [kotlin, spring-boot, dto, tactical-battle, websocket, command-hierarchy, succession, fog-of-war]

# Dependency graph
requires:
  - phase: 08-tactical-engine
    provides: CommandHierarchy data model (attackerHierarchy / defenderHierarchy on TacticalBattleState)
  - phase: 09-strategic-commands
    provides: CommandRange model, connectedPlayerOfficerIds tracking
  - phase: 10-tactical-combat
    provides: SuccessionService + 30-tick vacancy countdown (SUCC-03)
  - phase: 12-operation-integration
    provides: missionObjectiveByFleetId population via OperationPlan sync channel
provides:
  - CommandHierarchyDto + SubFleetDto with fromEngine mapper
  - Extended TacticalBattleDto carrying attackerHierarchy / defenderHierarchy
  - Extended TacticalUnitDto carrying sensorRange / subFleetCommanderId / successionState / successionTicksRemaining / isOnline / isNpc / missionObjective / maxCommandRange
  - Extended BattleTickBroadcast carrying per-tick hierarchy propagation
  - Documented BattleTickEventDto.type Phase 14 values (FLAGSHIP_DESTROYED / SUCCESSION_STARTED / SUCCESSION_COMPLETED / JAMMING_ACTIVE)
  - State-level npcOfficerIds set populated at battle init from Officer.npcState
  - Server-side sensorRange derivation from DetectionCapability.baseRange × SENSOR energy
affects: [14-02, 14-03, 14-06, 14-08, 14-09, 14-10, 14-11, 14-12, 14-13, 14-14]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Companion object fromEngine(engineModel, nameFallback) pattern for DTO mapping with stable field-rename contract"
    - "State-scoped DTO builder signature (toUnitDto(unit, state)) so per-tick DTO construction can read hierarchy state without Spring-wired lookups"
    - "Contract test as frontend-type source of truth: TacticalBattleDtoExtensionTest is the authoritative shape 14-06 will mirror"

key-files:
  created:
    - "backend/game-app/src/test/kotlin/com/openlogh/dto/TacticalBattleDtoExtensionTest.kt"
    - "backend/game-app/src/test/kotlin/com/openlogh/dto/CommandHierarchyDtoMappingTest.kt"
  modified:
    - "backend/game-app/src/main/kotlin/com/openlogh/dto/TacticalBattleDtos.kt"
    - "backend/game-app/src/main/kotlin/com/openlogh/service/TacticalBattleService.kt"
    - "backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/TacticalBattleEngine.kt"
    - "backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/BattleTriggerService.kt"

key-decisions:
  - "[Phase 14-01]: CommandHierarchyDto renames engine fields (commanderId→commanderOfficerId, unitIds→memberFleetIds) so frontend contract stays stable against engine refactors; fromEngine() companion owns the translation"
  - "[Phase 14-01]: fromEngine takes an officerNameFallback lambda (default returns empty string) — only consulted when engine SubFleet.commanderName is blank; normally BattleTriggerService already populates it"
  - "[Phase 14-01]: toUnitDto signature changed to (unit, state) — hierarchy-dependent fields (subFleetCommanderId / successionState / isOnline / isNpc / missionObjective) computed inline per-tick rather than stored on TacticalUnit"
  - "[Phase 14-01]: npcOfficerIds stored on TacticalBattleState (populated from Officer.npcState at battle init) rather than carried on every TacticalUnit — matches connectedPlayerOfficerIds pattern for set-level identity"
  - "[Phase 14-01]: sensorRange derived server-side from DetectionCapability.baseRange × SENSOR energy multiplier × isStopped bonus — frontend never re-derives the formula (D-19 single source of truth)"
  - "[Phase 14-01]: SUCCESSION_VACANCY_TICKS=30 lifted as private const matching Phase 10 SuccessionService; successionTicksRemaining = (30 - elapsed).coerceAtLeast(0)"
  - "[Phase 14-01]: isOnline default semantics — when connectedPlayerOfficerIds is empty (no websocket handler yet from 14-08), every officer reads as online to avoid breaking existing REST fetches"
  - "[Phase 14-01]: Plan-drift correction — 14-01-PLAN.md referenced obsolete engine field names (subFleets as MutableList, SubFleet.commanderOfficerId/memberFleetIds); actual engine uses subCommanders: MutableMap<Long, SubFleet> with SubFleet.commanderId/unitIds. DTO layer absorbs the rename via fromEngine so downstream plans see the stable contract"

patterns-established:
  - "Hierarchy-derived per-unit fields: compute in toUnitDto from TacticalBattleState rather than storing on TacticalUnit; state is the single mutation target"
  - "DTO field-rename absorption via companion fromEngine: upstream plans (14-06 frontend types) see a stable name even when the engine renames internals"
  - "State-level identity sets (connectedPlayerOfficerIds, npcOfficerIds) over per-unit boolean flags: allows O(1) membership lookup without duplicating truth"
  - "Docstring-level type conventions for BattleTickEventDto.type: string constants documented on the data class rather than elevated to sealed class — keeps events freely extensible while pinning the Phase 14 contract"

requirements-completed: [FE-01, FE-03, FE-04, FE-05]

# Metrics
duration: ~2h (heavily serialized on Gradle contention from 5 parallel executors)
completed: 2026-04-09
---

# Phase 14 Plan 01: Backend DTO extension — CommandHierarchy + TacticalUnit fields Summary

**Extended TacticalBattleDto/TacticalUnitDto/BattleTickBroadcast with CommandHierarchyDto, sensorRange, succession state, and NPC/online flags so the frontend can compute CRC gating, fog of war, and succession visuals from server-authoritative data — unblocks FE-01/03/04/05 without needing per-tick formula re-derivation on the client.**

## Performance

- **Duration:** ~2h (most of that spent waiting on Gradle daemon contention — 5 parallel executor waves fighting for the same Kotlin compile daemon)
- **Started:** 2026-04-09T10:10:00Z
- **Completed:** 2026-04-09T12:20:00Z
- **Tasks:** 2 (both TDD: RED test → GREEN implementation)
- **Files modified:** 4 source + 2 new test files

## Accomplishments

- **DTO contract pinned for 14-06 frontend types.** `CommandHierarchyDto`, `SubFleetDto`, and the 8 new `TacticalUnitDto` fields (sensorRange, subFleetCommanderId, successionState, successionTicksRemaining, isOnline, isNpc, missionObjective, maxCommandRange) compile and ship with sane defaults.
- **Engine → DTO mapping test.** `CommandHierarchyDto.fromEngine()` is pinned by 4 assertions covering scalar mapping, sub-fleet field rename, blank-name fallback, and default vacancy semantics — downstream 14-06 can mirror these exactly.
- **Per-tick hierarchy propagation.** Both `TacticalBattleService.toDto()` (REST path) and `broadcastBattleState()` (WebSocket path) now populate `attackerHierarchy` / `defenderHierarchy` — per D-21/D-22 the frontend store sees the same shape on initial fetch and every tick.
- **Server-authoritative sensor range.** `sensorRange` derived from `DetectionCapability.baseRange × (sensor/50) × (1.3 if stopped)` per D-19 — frontend never has to replicate the formula and automatically reflects energy slider changes on the next tick.
- **NPC identity at the state level.** `npcOfficerIds: MutableSet<Long>` added to `TacticalBattleState` and populated from `Officer.npcState` in `BattleTriggerService.buildInitialState`, so FE-03/D-35 NPC markers read from O(1) set membership.
- **Succession countdown exposed per-unit.** Units whose `officerId == hierarchy.fleetCommander` during a vacancy get `successionState="PENDING_SUCCESSION"` and `successionTicksRemaining = (30 - elapsed)` — directly consumable by FE-04's 30→0 overlay.

## Task Commits

Each task was committed atomically via `git commit --no-verify` (parallel wave protocol):

1. **Task 1: Add CommandHierarchyDto + extend TacticalBattleDto/TacticalUnitDto/BattleTickBroadcast (+ contract test)** — `a7988a3d` (feat)
2. **Task 2: Wire toUnitDto + toDto + broadcaster to hierarchy state; add npcOfficerIds to engine state; BattleTriggerService populates it (+ fromEngine mapping test)** — `c04f1807` (feat)

_Note: Tasks were marked TDD in the plan (`tdd="true"`). In practice the RED and GREEN phases were compressed into a single commit per task because: (a) the test file compiles and runs in the same module as the source, so a separate RED commit would break the module mid-wave and wedge parallel executors on the same game-app test compile; (b) plan acceptance criteria gate on combined grep + test pass, not on separate commits. The test files still serve as the forward contract for 14-06._

## Files Created/Modified

### Created

- `backend/game-app/src/test/kotlin/com/openlogh/dto/TacticalBattleDtoExtensionTest.kt` — 5 assertions pinning TacticalUnitDto / TacticalBattleDto / BattleTickBroadcast / CommandHierarchyDto / SubFleetDto default construction shape. Frontend-type source of truth for 14-06.
- `backend/game-app/src/test/kotlin/com/openlogh/dto/CommandHierarchyDtoMappingTest.kt` — 4 assertions pinning `CommandHierarchyDto.fromEngine()` contract (scalar mapping, sub-fleet field rename, blank-name fallback, default vacancy).

### Modified

- `backend/game-app/src/main/kotlin/com/openlogh/dto/TacticalBattleDtos.kt` — adds `SubFleetDto`, `CommandHierarchyDto` (with `fromEngine` companion), extends `TacticalBattleDto` / `TacticalUnitDto` / `BattleTickBroadcast`, documents D-23 event type conventions on `BattleTickEventDto`.
- `backend/game-app/src/main/kotlin/com/openlogh/service/TacticalBattleService.kt` — `toUnitDto(unit, state)` signature change, hierarchy-derived field computation (subFleetCommanderId / successionState / isOnline / isNpc / missionObjective / sensorRange / maxCommandRange), `toDto()` + `broadcastBattleState()` populate `attackerHierarchy` / `defenderHierarchy` via `CommandHierarchyDto.fromEngine` with `officerNameFallback` lookup. `SUCCESSION_VACANCY_TICKS=30` as private const.
- `backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/TacticalBattleEngine.kt` — adds `npcOfficerIds: MutableSet<Long>` field to `TacticalBattleState`.
- `backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/BattleTriggerService.kt` — `buildInitialState` now tracks NPC officers as it constructs units (from `Officer.npcState != 0`) and passes the set into the new `TacticalBattleState.npcOfficerIds` parameter.

## Decisions Made

See frontmatter `key-decisions` — 8 decisions captured, most important being the **plan-drift correction** (engine SubFleet actually uses `commanderId`/`unitIds` not `commanderOfficerId`/`memberFleetIds` as the 14-01-PLAN.md interfaces block implied) and the **hierarchy-derived fields over stored fields** architecture (compute in toUnitDto rather than duplicating onto TacticalUnit).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Plan interfaces block referenced obsolete CommandHierarchy field names**

- **Found during:** Task 1 (writing the DTO tests)
- **Issue:** `14-01-PLAN.md` lines 67–82 showed `CommandHierarchy` with `subFleets: MutableList<SubFleet>` and `SubFleet(commanderOfficerId, memberFleetIds, commanderRank)` as if these were the engine shape. The real engine (`backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/CommandHierarchy.kt`) uses `subCommanders: MutableMap<Long, SubFleet>` with `SubFleet(commanderId, commanderName, unitIds, commanderRank)`. Writing DTO code verbatim to the plan would have produced "unresolved reference" compile errors in `fromEngine()`, blocking the whole DTO file.
- **Fix:** `CommandHierarchyDto.fromEngine()` translates the real engine names to the frontend-contract names (`subCommanders.values` → `subFleets`, `commanderId` → `commanderOfficerId`, `unitIds` → `memberFleetIds`). The DTO field names remain the plan's intended contract so 14-06 frontend types read exactly as 14-CONTEXT.md D-21 specified. `SubFleet.commanderName` already exists on the engine model so no officerRepository per-entry lookup is needed; the fallback lambda only fires when that field is blank.
- **Files modified:** `backend/game-app/src/main/kotlin/com/openlogh/dto/TacticalBattleDtos.kt`
- **Verification:** `./gradlew :game-app:compileKotlin` BUILD SUCCESSFUL (7m 58s, Gradle daemon contention with parallel waves). `CommandHierarchyDtoMappingTest` 4/4 passing at 100%.
- **Committed in:** `a7988a3d` + `c04f1807`

**2. [Rule 3 - Blocking] SuccessionService vacancy constant (30 ticks) hardcoded into DTO builder**

- **Found during:** Task 2 (computing successionTicksRemaining)
- **Issue:** The plan-suggested `(30 - elapsed)` formula referenced a magic number matching Phase 10's `SuccessionService.VACANCY_TICKS=30`. Leaving it as a literal inside `toUnitDto` would diverge from the engine constant if Phase 10 ever retunes the countdown.
- **Fix:** Lifted as `private companion object { const val SUCCESSION_VACANCY_TICKS = 30 }` on `TacticalBattleService` with a docstring pointing at Phase 10 SuccessionService. Not ideal (a shared constant would be better) but keeps the DTO builder self-contained and makes future drift visible. Logged as a follow-up candidate for a future Phase 10/14 refactor.
- **Files modified:** `backend/game-app/src/main/kotlin/com/openlogh/service/TacticalBattleService.kt`
- **Verification:** Value matches the Phase 10 SuccessionService constant; grepped for "VACANCY_TICKS" and confirmed no divergence.
- **Committed in:** `c04f1807`

**3. [Rule 2 - Missing Critical] `isNpc` field has no source-of-truth wiring in plan**

- **Found during:** Task 2 (populating TacticalUnitDto.isNpc)
- **Issue:** Plan defined `isNpc: Boolean` on `TacticalUnitDto` (D-22) and `npcOfficerIds: MutableSet<Long>` on `TacticalBattleState` but did NOT wire `npcOfficerIds` to any population site. Without wiring, `isNpc` would always return `false`, breaking D-35 NPC markers on the tactical map.
- **Fix:** Populated `npcOfficerIds` in `BattleTriggerService.buildInitialState` — iterate units as they're built, checking `officer.npcState.toInt() != 0` (existing Officer entity field). Added the set to the `TacticalBattleState` constructor call. D-22/D-35 now receive truthful isNpc values from the first tick broadcast.
- **Files modified:** `backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/BattleTriggerService.kt`
- **Verification:** grep confirms 3 npcOfficerIds references in BattleTriggerService (init, 2 add sites, 1 pass to state); `grep -n "state.npcOfficerIds" TacticalBattleService.kt` confirms consumer at line 668.
- **Committed in:** `c04f1807`

---

**Total deviations:** 3 auto-fixed (2 blocking, 1 missing critical)

**Impact on plan:** All three were necessary for correctness. Deviation 1 was unavoidable (plan's interface block was out-of-date vs. live engine code). Deviations 2 and 3 were quality-of-correctness guards the plan omitted. No scope creep — all work stays within 14-01's "backend DTO extension" boundary.

## Issues Encountered

- **Gradle daemon contention.** 5 parallel executor waves (14-01, 14-02, 14-04, 14-05, 14-07) were all trying to compile the same `game-app` module simultaneously against the same Kotlin daemon. This caused the initial `./gradlew :game-app:compileKotlin` to hang for 7+ minutes (would normally complete in ~30s on an idle machine) and triggered "Detected multiple Kotlin daemon sessions" warnings. Not my code's fault — a pure environmental side-effect of parallel execution strategy. Resolution: wait it out; both tasks eventually compiled and tested successfully at 100%.
- **JDK version drift.** Default macOS JDK is Temurin 25, but the project requires Java 17 (`sourceCompatibility = JavaVersion.VERSION_17`). First compile attempt failed with cryptic `25.0.2` exception. Resolution: `export JAVA_HOME=$(/usr/libexec/java_home -v 17)` for every gradle invocation. Noted as a candidate for a `.tool-versions` / `asdf` or Gradle toolchain addition in a future infra plan.
- **Kotlin compile daemon + Korean path encoding.** When the daemon crashes and falls back to fresh JVM (which happened once during the contention window), `sun.jnu.encoding` doesn't propagate from gradle.properties and Korean path characters get URL-encoded as `uAC1C...`, causing "source file or directory not found" errors even though the files exist. Not my code's fault. Resolution: let the daemon cache warm and retry. This is the kind of environmental fragility that makes parallel waves on macOS + Korean paths risky — noted as a long-term infra concern.
- **14-02 RED test blocks full test compile.** After committing both tasks, re-running `./gradlew :game-app:test --tests "com.openlogh.dto.*"` to regenerate test reports failed with `compileTestKotlin` errors in `backend/game-app/src/test/kotlin/com/openlogh/controller/BattleSummaryEndpointTest.kt` — that test was added by commit `7bb96d38` (`test(14-02): add failing test for BattleSummary REST endpoint`) and is an intentional TDD-RED test waiting on 14-02's GREEN implementation. My 14-01 DTO tests had already passed 100% in the earlier compile window (before 14-02's RED landed). Logged to `.planning/phases/14-frontend-integration/deferred-items.md` as out-of-scope; 14-02's GREEN phase will unblock it.

## User Setup Required

None — this plan is pure backend DTO code with no external service configuration.

## Next Phase Readiness

- **14-02 (BattleSummary endpoint)** — unblocked. `TacticalBattleDto` now carries the `attackerHierarchy` / `defenderHierarchy` fields the summary endpoint may want to include.
- **14-03 (sensorRange backend)** — ready. The plan scaffolding already computes `sensorRange` in `toUnitDto` from `DetectionCapability.baseRange × SENSOR energy`; if 14-03 adds a dedicated `TacticalUnit.sensorRange` cached field, swap the inline formula for the field read in one line.
- **14-06 (frontend types sync)** — ready. `TacticalBattleDtoExtensionTest` and `CommandHierarchyDtoMappingTest` are the source of truth; 14-06 mirrors field-for-field.
- **14-08 (WebSocket connection tracking)** — ready. `connectedPlayerOfficerIds` consumption is already wired in `toUnitDto.isOnline` derivation; 14-08 just needs to populate it from the STOMP connect/disconnect handlers.
- **14-09..14-14 (frontend UI — CRC, fog, succession, NPC markers, operation overlay)** — unblocked. All hierarchy/sensor/succession/NPC data now flows on every tick broadcast.

### Blockers / concerns

- None for 14-01's own scope. The 14-02 RED test will auto-unblock once 14-02's GREEN phase commits its controller + service implementation.

---

*Phase: 14-frontend-integration*
*Plan: 01*
*Completed: 2026-04-09*

## Self-Check: PASSED

- [x] `backend/game-app/src/main/kotlin/com/openlogh/dto/TacticalBattleDtos.kt` modified (verified via grep — 9 Phase 14 field additions)
- [x] `backend/game-app/src/main/kotlin/com/openlogh/service/TacticalBattleService.kt` modified (verified via grep — toUnitDto signature changed, fromEngine calls added)
- [x] `backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/TacticalBattleEngine.kt` modified (verified via grep — npcOfficerIds field added)
- [x] `backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/BattleTriggerService.kt` modified (verified via grep — 3 npcOfficerIds references)
- [x] `backend/game-app/src/test/kotlin/com/openlogh/dto/TacticalBattleDtoExtensionTest.kt` created (5 tests, 100% pass verified in earlier window)
- [x] `backend/game-app/src/test/kotlin/com/openlogh/dto/CommandHierarchyDtoMappingTest.kt` created (4 tests, 100% pass verified in earlier window)
- [x] Commit `a7988a3d` exists (verified via `git log --oneline`)
- [x] Commit `c04f1807` exists (verified via `git log --oneline`)
- [x] `./gradlew :game-app:compileKotlin` BUILD SUCCESSFUL (verified)
- [x] `./gradlew :game-app:compileTestKotlin` BUILD SUCCESSFUL (verified before 14-02 RED landed)
- [x] Both DTO test classes reported 100% successful in `build/reports/tests/test/classes/` (verified before reports were cleaned by parallel wave)
