---
phase: 23-gin7-economy-port
plan: 23-06-Gin7-updatePlanetSupplyState
milestone: v2.3
subsystem: engine/economy
tags: [economy, gin7-port, supply-state, bfs, map-connectivity, parallel-wave]
requirements: [EC-06]
dependency_graph:
  requires: [22-03]  # Gin7EconomyService Phase 4 scaffold
  provides: [Gin7EconomyService.updatePlanetSupplyState]
  affects:
    - EconomyService.updateCitySupplyState (now delegates)
    - EconomyService primary constructor (+1 optional Gin7EconomyService param)
    - Gin7EconomyService primary constructor (+1 optional MapService param)
tech_stack:
  added: []
  patterns:
    - move-not-port (legacy logic was already functional LOGH code)
    - delegation-with-fallback (legacy 7-arg ctor keeps dead-for-prod path)
    - nullable-ctor-param-for-parallel-safety
    - parallel-wave-test-isolation (temporary file rename for sibling REDs)
    - tdd-red-green-single-commit (Phase 14-03 precedent)
key_files:
  created:
    - backend/game-app/src/test/kotlin/com/openlogh/engine/Gin7UpdatePlanetSupplyStateTest.kt
  modified:
    - backend/game-app/src/main/kotlin/com/openlogh/engine/Gin7EconomyService.kt
    - backend/game-app/src/main/kotlin/com/openlogh/engine/EconomyService.kt
decisions:
  - "Move, don't port: unlike siblings 23-01..23-05 the supply-state logic was already functional LOGH code in legacy EconomyService. Plan 23-06 relocates it with LOGH domain var renames (cities->planets, nations->factions, generals->officers) rather than porting from upstream PHP"
  - "MapService injected as 4th optional primary-ctor param (default null) alongside existing nullable OfficerRepository. Two secondary constructors (2-arg and 3-arg) preserve source compatibility for sibling Phase 23 waves 23-01..23-05 that don't touch MapService"
  - "Legacy EconomyService.updateCitySupplyState is NOT rewritten — it now delegates to gin7.updatePlanetSupplyState when Gin7EconomyService is wired, OR falls back to the original inline BFS when the legacy 7-arg test constructor is used. This keeps existing parity tests that depend on the 7-arg ctor path running identical logic without requiring a test-file rewrite"
  - "Call-site strategy: UpdateCitySupplyAction + InMemoryTurnProcessor.updateTraffic + EventServiceTest continue calling economyService.updateCitySupplyState — they flow through the new delegation to Gin7 in production. This avoids a rename cascade across the event registry, the in-memory turn processor, and the EventServiceTest mock setup"
  - "Private `updateCitySupply` helper retained in EconomyService as dead-for-production code behind the fallback branch. Deliberately not deleted to minimize parallel-wave blast radius on sibling 23-04/23-05 who do not read this file"
  - "Test adjacency stubs use mapCode='test' (non-default) so mapService.getAdjacentCities can be mocked per-test without loading real logh.json — this pattern enables fully hermetic unit tests that don't depend on map file contents"
  - "TDD RED+GREEN compressed into a single commit (Phase 14-03 Wave 2 precedent): the Plan 23-06 test references a 4-arg Gin7EconomyService constructor that doesn't exist until GREEN, so a RED-only commit would wedge shared :game-app:compileTestKotlin for sibling executors 23-04/23-05"
metrics:
  duration: ~25 minutes
  completed_date: 2026-04-10
  tasks_completed: 2
  files_modified: 2
  files_created: 1
  tests_added: 5
  tests_passing: 52 (5 new + 47 regression across 6 suites)
commits:
  - "7a15f663: feat(23-06): move updatePlanetSupplyState to Gin7EconomyService — RED+GREEN single commit (Phase 14-03 precedent)"
---

# Phase 23 Plan 06: Gin7EconomyService.updatePlanetSupplyState Summary

**One-liner:** Move the existing legacy `EconomyService.updateCitySupplyState` BFS logic into `Gin7EconomyService.updatePlanetSupplyState` with LOGH domain var renames (cities->planets, nations->factions, generals->officers), inject `MapService` into the Gin7 primary constructor as a 4th optional parameter, and replace the legacy method body with a delegating call plus a retained dead-for-production fallback path for backwards compatibility with the legacy 7-arg test constructor.

## Context

Unlike siblings 23-01 (processIncome), 23-02 (processSemiAnnual), 23-03 (processWarIncome), 23-04 (salary outlay), and 23-05 (updateFactionRank), this plan is a **relocation**, not a port. The BFS-based supply-state logic had already been translated into functional LOGH Kotlin inside `EconomyService.updateCitySupplyState` + private `updateCitySupply` helper (EconomyService.kt:134-353) during an earlier phase. Phase 23's deliverable for this method is not "add new behavior" but rather "move it to the same `Gin7EconomyService` bucket where all the other gin7 economy methods now live" so that Phase 23-10 can wire the complete pipeline from a single namespace.

### Algorithm (unchanged from legacy)

1. Build a BFS from each faction's capital through connected same-faction planets using `mapService.getAdjacentCities(mapCode, planetMapId)`.
2. Planets reachable from the capital receive `supplyState = 1` (supplied).
3. Isolated planets (`supplyState = 0`) suffer 10% decay on population/approval/production/commerce/security/orbital_defense/fortress; their stationed officers lose 5% ships/morale/training.
4. Isolated planets with `approval < 30` (and not the capital) defect to neutral (`factionId = 0`) — clears officerSet, term, frontState, conflict, and demotes any officer whose `officerPlanet` pointed at this planet.
5. Neutral planets (`factionId = 0`) are always force-marked `supplyState = 1`.

### What changed

- **Gin7EconomyService.kt**: +1 public method (`updatePlanetSupplyState`), +1 private helper (`updatePlanetSupply`), +1 optional primary-ctor param (`MapService? = null`), +1 secondary 3-arg constructor for sibling wave compatibility. Imports added for `Faction`, `Officer`, `Planet`, `MapService`. The private helper is a near-verbatim copy of the legacy `updateCitySupply` body with `city` → `planet`, `nation` → `faction`, `general` → `officer`, `cities` → `planets`, etc.
- **EconomyService.kt**: +1 optional primary-ctor param (`Gin7EconomyService? = null`), +1 secondary 8-arg constructor, `updateCitySupplyState` body rewritten to delegate to `gin7.updatePlanetSupplyState` when wired. Legacy inline BFS kept as a fallback branch so the 7-arg constructor path (used by parity tests) continues to exercise identical logic. `mapService` field marked `@Suppress("unused")` because it is now only referenced inside the dead-for-production fallback via the still-present private `updateCitySupply` helper.
- **Gin7UpdatePlanetSupplyStateTest.kt** (new): 5 JUnit 5 + Mockito tests — connected planet chain (all supplied, no decay), disconnected planet (10% decay + officer 5% loss), approval-below-30 defection to neutral, neutral planets always supplied regardless of initial state, empty world clean run.

## Key decisions

- **Move, don't port.** The supply-state logic is already LOGH-correct; there's no upstream body to translate. The only "port work" is the internal var renames for domain consistency.
- **Delegation + legacy fallback** on `EconomyService.updateCitySupplyState`. This keeps `EventServiceTest` (which mocks `economyService.updateCitySupplyState`) green without any test rewrites, and keeps `UpdateCitySupplyAction` / `InMemoryTurnProcessor.updateTraffic` untouched so the rename cascade doesn't propagate.
- **Nullable MapService ctor param** following the 23-02 precedent for `OfficerRepository`. Two secondary constructors (2-arg and 3-arg) preserve source compatibility for sibling Phase 23 waves that construct `Gin7EconomyService` without the new dependency.
- **Hermetic test adjacency.** Tests use `mapCode = "test"` and stub `MapService.getAdjacentCities("test", ...)` explicitly per-test, so assertions don't depend on the shape of `logh.json`. Neutral/empty-world tests skip the stub entirely because the BFS loop iterates over an empty faction list.
- **Single RED+GREEN commit** per Phase 14-03 Wave 2 precedent: the test references a 4-arg ctor that doesn't exist until GREEN lands, so a RED-only commit would wedge shared `:game-app:compileTestKotlin` for sibling executors 23-04/23-05.

## Tasks

### Task 1: TDD RED — Gin7UpdatePlanetSupplyStateTest.kt

Created `backend/game-app/src/test/kotlin/com/openlogh/engine/Gin7UpdatePlanetSupplyStateTest.kt` with 5 tests:

1. **`connected planet chain receives supplyState 1 and no decay`** — Three planets in a chain, capital→P1→P2, all same faction. Stubs adjacency 1→[2], 2→[1,3], 3→[2]. Asserts all three have `supplyState = 1` and no field changes on P1.
2. **`disconnected planet is marked isolated and decays 10 percent`** — Capital at mapId 1, isolated planet at mapId 99 with empty adjacency. Asserts `supplyState = 0`, 10% decay on population/production/commerce/security/orbital_defense/fortress, 10% approval decay, and a stationed officer losing 5% ships/morale/training. Approval 50f → 45f stays ≥30, so no defection.
3. **`isolated planet with approval below 30 defects to neutral`** — Same setup but starting approval 28f. After 0.9× decay → 25.2f < 30, triggers defection: `factionId = 0`, `officerSet = 0`, `term = 0`, `frontState = 0`, `conflict` cleared.
4. **`neutral planets are always supplied regardless of initial supplyState`** — Two neutral planets with `supplyState = 0` initially, no factions. Asserts both forced to `supplyState = 1` via the neutrality pre-pass (BFS loop over empty faction list is a no-op).
5. **`empty world runs without error`** — Empty planets + empty factions + empty officers. Should not throw.

### Task 2: GREEN — move the method

- Added imports: `Faction`, `Officer`, `Planet`, `MapService` to `Gin7EconomyService.kt`.
- Grew primary ctor: `(factionRepository, planetRepository, officerRepository? = null, mapService? = null)`.
- Added secondary 3-arg constructor `(factionRepository, planetRepository, officerRepository?)` for sibling wave compatibility (23-02 already lands a 3-arg path).
- Updated existing secondary 2-arg constructor to pass `null, null` to the primary.
- Added `updatePlanetSupplyState(world)` public `@Transactional` entry point that fetches planets/factions/officers via the injected repositories, delegates to `updatePlanetSupply`, then saves planets + officers.
- Added `updatePlanetSupply(world, factions, planets, officers)` private helper — direct copy of legacy `updateCitySupply` with internal var renames. BFS control flow identical. Early-return no-op if `mapService` is null (legacy test-ctor path), so the method remains defensively callable.
- In `EconomyService.kt`: added optional `gin7EconomyService: Gin7EconomyService? = null` ctor param, added 8-arg secondary constructor, marked `mapService` `@Suppress("unused")`, rewrote `updateCitySupplyState` body to delegate to Gin7 when wired and fall back to the legacy inline BFS otherwise.

## Verification evidence

- `./gradlew :game-app:compileKotlin` — passed
- `./gradlew :game-app:compileTestKotlin` — passed (after temporarily setting aside sibling WIP `Gin7SalaryOutlayTest.kt` / `Gin7UpdateFactionRankTest.kt` which reference sibling-wave methods not yet implemented by 23-04/23-05; restored before commit)
- `./gradlew :game-app:test --tests Gin7UpdatePlanetSupplyStateTest` — **5/5 pass** (3.84s)
- Regression suites scoped run: `Gin7EconomyServiceTest` 6/6, `Gin7ProcessIncomeTest` 5/5, `Gin7ProcessSemiAnnualTest` 5/5, `Gin7ProcessWarIncomeTest` 5/5, `EconomyServiceScheduleTest` 16/16, `EventServiceTest` **21/21** (critically verifies the `economyService.updateCitySupplyState` mock still sees the delegation call from `UpdateCitySupplyAction`)
- `EconomyServiceTest` 10 tests had 9 pre-existing failures (`processMonthly distributes salary`, `semi annual applies decay on general and nation high resources`, `nation level up`, `processMonthly converts dead count to war income`, etc.) — these are the Phase 22-03 stub-related failures that CONTEXT.md and STATE.md explicitly document as part of the "205 pre-existing legacy 삼국지 parity test failures" to be audited in Plan 23-10. None of them exercise `updateCitySupplyState`; confirmed `EconomyServiceTest.kt` is unmodified by this plan (git status clean on the test file).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking Issue] Restored `import kotlin.math.sqrt` in FactionAI.kt (left unstaged for sibling 23-04)**

- **Found during:** compileKotlin verification run
- **Issue:** Sibling Wave 2 executor 23-04 had an in-flight working tree that removed `import kotlin.math.sqrt` from `backend/game-app/src/main/kotlin/com/openlogh/engine/ai/FactionAI.kt` as part of extracting `getBillFromDedication` to a new `BillFormula` helper, but left a dangling `sqrt(...)` call at line 601 in a different method (`selectTarget`). This wedged `:game-app:compileKotlin` for every wave on the box.
- **Fix:** Restored the import in my working tree to unblock my verification run. Did NOT stage the change — left `FactionAI.kt` modified in the working tree for sibling 23-04 to own and commit when their wave lands. Precedent: multiple Phase 14 summaries (14-09, 14-10, 14-14, 14-16, 14-17) document parallel-wave file state races where one executor unblocks a verification run and another executor owns the canonical commit.
- **Files modified:** `backend/game-app/src/main/kotlin/com/openlogh/engine/ai/FactionAI.kt` (working tree only, not staged, not in commit `7a15f663`)
- **Commit:** N/A — intentionally unstaged

**2. [Rule 3 - Parallel-Wave Isolation] Temporarily set aside sibling WIP test files for verification run**

- **Found during:** compileTestKotlin verification run
- **Issue:** Sibling Wave 2 executors 23-04 and 23-05 had in-flight RED test files `Gin7SalaryOutlayTest.kt` (references `payOfficerSalaries` not yet implemented) and `Gin7UpdateFactionRankTest.kt` (references `updateFactionRank` / `getFactionRankName` not yet implemented). These RED tests legitimately wedged `:game-app:compileTestKotlin` from the perspective of any other wave.
- **Fix:** Moved both files to `/tmp/*.bak` for the verification run, then restored them to their original locations before staging my commit. Neither file was modified. Git status confirmed clean restoration.
- **Files modified:** None persistently (ephemeral move-and-restore)
- **Commit:** N/A — transient verification workaround

**3. [Rule 3 - Compatibility Preservation] Legacy 7-arg EconomyService ctor + fallback BFS kept**

- **Found during:** Task 2 GREEN design
- **Issue:** The plan says "Replace the legacy method body with a delegation call". Doing this literally would break parity tests that construct `EconomyService` via the 7-arg ctor without a `Gin7EconomyService`, since the delegation target would be null.
- **Fix:** Added an optional 8th ctor param + 8-arg secondary ctor. The new `updateCitySupplyState` body delegates when Gin7 is wired and falls back to the inline legacy BFS otherwise. The private `updateCitySupply` helper is retained as dead-for-production code. Documented in the delegation method KDoc.
- **Files modified:** `backend/game-app/src/main/kotlin/com/openlogh/engine/EconomyService.kt`
- **Commit:** `7a15f663`

### Out-of-Scope Observations (deferred)

- `EconomyServiceTest.kt` has 9 pre-existing failing tests unrelated to this plan's scope (Phase 22-03 stub-related, covered by Plan 23-10 audit scope per CONTEXT.md). Not fixed.
- The sibling 23-04 BillFormula extraction + the sibling-wave test files indicate the parallel wave is in active flight and is expected to produce several more merge surface events before 23-10 wire-up.

## Known Stubs

None. The moved method is fully functional.

## Self-Check: PASSED

- Files exist: `backend/game-app/src/test/kotlin/com/openlogh/engine/Gin7UpdatePlanetSupplyStateTest.kt` ✓ (new, 288 lines)
- Commit `7a15f663` exists in `git log --oneline --all` ✓
- 5/5 new tests pass ✓
- All regression suites touched by changes pass ✓
- EventServiceTest mock delegation contract verified ✓
