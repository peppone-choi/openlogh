---
phase: 13-ai
plan: 01
subsystem: strategic-ai
tags: [ai, strategic, scoring, pure-object, kotlin]
requirements: [SAI-02]
dependency_graph:
  requires:
    - "engine.ai.PersonalityTrait (existing 5-trait enum)"
    - "engine.tactical.ai.MissionObjective (existing CONQUEST/DEFENSE/SWEEP enum)"
    - "entity.Fleet (currentUnits, planetId)"
    - "entity.Officer (command, leadership, intelligence, planetId)"
    - "entity.Planet (frontState, orbitalDefense, fortress, production, commerce, tradeRoute, population)"
  provides:
    - "engine.ai.strategic.StrategicPowerScorer.evaluatePower()"
    - "engine.ai.strategic.FogOfWarEstimator.hasIntelligenceAgent() / applyFogNoise()"
    - "engine.ai.strategic.OperationTargetSelector.selectTargets()"
    - "engine.ai.strategic.FleetAllocator.allocateFleets()"
  affects:
    - "Phase 13 Plan 02 (FactionAI atWar branch replacement) — will compose these 4 scorers"
tech-stack:
  added: []
  patterns:
    - "Pure object scorer (no Spring DI) — same as UtilityScorer / SuccessionService / CommandHierarchyService"
    - "Greedy allocation (not knapsack) for fleet picking"
    - "Symmetric noise injection for fog-of-war estimation"
key-files:
  created:
    - backend/game-app/src/main/kotlin/com/openlogh/engine/ai/strategic/StrategicPowerScorer.kt
    - backend/game-app/src/main/kotlin/com/openlogh/engine/ai/strategic/FogOfWarEstimator.kt
    - backend/game-app/src/main/kotlin/com/openlogh/engine/ai/strategic/OperationTargetSelector.kt
    - backend/game-app/src/main/kotlin/com/openlogh/engine/ai/strategic/FleetAllocator.kt
    - backend/game-app/src/test/kotlin/com/openlogh/engine/ai/strategic/StrategicPowerScorerTest.kt
    - backend/game-app/src/test/kotlin/com/openlogh/engine/ai/strategic/FogOfWarEstimatorTest.kt
    - backend/game-app/src/test/kotlin/com/openlogh/engine/ai/strategic/OperationTargetSelectorTest.kt
    - backend/game-app/src/test/kotlin/com/openlogh/engine/ai/strategic/FleetAllocatorTest.kt
  modified: []
decisions:
  - "Use Fleet.currentUnits * 300 (gin7 SHIPS_PER_UNIT) for total ship counts — Fleet entity has no .ships field"
  - "compositeScore = ships*0.5 + commander*30.0 + defense*20.0 (raw additive — normalization happens at comparison time)"
  - "Commander score = MEAN of (command+leadership)/2 across stationed officers (not sum), so a few elite officers can match many average ones"
  - "DEFENSE_THREAT_RATIO = 0.7 — own front-line system is threatened only when nearby enemy power exceeds 70% of own power"
  - "Selector takes pre-built maps (ownFleetsByPlanet etc.) so the planner can supply genuine adjacency data via galaxy graph queries before invoking"
metrics:
  duration_minutes: 6
  tasks_completed: 2
  files_created: 8
  tests_added: 21
  tests_passing: 21
  completed_date: 2026-04-09
---

# Phase 13 Plan 01: Strategic AI Scorers Summary

Pure-object scoring foundation for SAI-02 — composite power evaluation, fog-of-war noise, operation target selection (CONQUEST/DEFENSE/SWEEP) with personality bias, and greedy fleet allocation, all under `com.openlogh.engine.ai.strategic`.

## What Was Built

Four stateless Kotlin `object` scorers that the upcoming FactionAI replacement (Plan 13-02) will compose to drive strategic operation planning:

1. **`StrategicPowerScorer`** — Combines fleet ship counts, average commander ability (command + leadership), and planet defense (orbitalDefense + fortress) into a single composite score. The raw additive formula (`ships*0.5 + commander*30 + defense*20`) puts the three dimensions into a comparable magnitude range.
2. **`FogOfWarEstimator`** — Per D-02 / D-03, the strategic AI only sees true enemy power for systems where a friendly officer with `intelligence ≥ 70` is stationed. Otherwise the estimate is multiplied by `1 ± random(0.4)`, creating exploitable AI mistakes.
3. **`OperationTargetSelector`** — Iterates own and enemy planets to produce three kinds of `OperationCandidate`:
   - CONQUEST for enemy front-line systems weak relative to their strategic value (production + commerce + tradeRoute*100 + population/100)
   - DEFENSE for own front-line systems where adjacent enemy power exceeds 70% of own power
   - SWEEP for own-territory planets harboring enemy fleets
   Per D-10, the sovereign's `PersonalityTrait` biases the scores: AGGRESSIVE × 1.5 for CONQUEST, DEFENSIVE × 1.5 for DEFENSE, CAUTIOUS × 0.8 for SWEEP.
4. **`FleetAllocator`** — Greedy 1.3x superiority allocator. Sorts fleets by power descending, adds them until cumulative reaches `requiredEnemyPower * 1.3`, then stops — leaving the remainder as the defense reserve (D-09).

All four classes are pure Kotlin `object`s with no Spring DI, matching the established `UtilityScorer` / `SuccessionService` / `CommandHierarchyService` pattern. They take pre-loaded entity collections so they can be unit-tested in milliseconds without a Spring context.

## Why This Approach

- **Pure object pattern** reuses the convention every other AI utility class follows. It also lets Plan 13-02 wire the scorers without adding constructor parameters to FactionAI beyond the already-required `CommandExecutor`.
- **Greedy allocation over knapsack** because game AI needs "good enough" — the optimal knapsack solution buys marginal benefit at significantly higher complexity. Greedy also mirrors how a real commander picks the strongest available fleets first.
- **Symmetric noise injection** at the scoring boundary keeps the fog-of-war contained: only the planner sees the noisy estimate, never persistent state. Uses an injected `kotlin.random.Random` so tests can seed for determinism.
- **Personality bias as multiplicative score factor** instead of distinct decision branches. This composes cleanly with the existing `PersonalityWeights` philosophy and lets future traits be added as data, not new code paths.

## Tasks

| # | Task                                              | Commit     | Status |
| - | ------------------------------------------------- | ---------- | ------ |
| 1 | Create 4 pure object strategic AI scorers         | `72a481df` | done   |
| 2 | Create unit tests for all 4 strategic AI scorers  | `f428c3e4` | done   |

## Verification

Plan-defined verification command:
```bash
cd backend && ./gradlew :game-app:test --tests "com.openlogh.engine.ai.strategic.*" -x :gateway-app:test
```

Result: BUILD SUCCESSFUL, 21/21 tests passing across 4 test classes:

| Test class                       | Tests | Passed | Failed | Time   |
| -------------------------------- | ----- | ------ | ------ | ------ |
| StrategicPowerScorerTest         | 3     | 3      | 0      | 0.003s |
| FogOfWarEstimatorTest            | 7     | 7      | 0      | 0.025s |
| OperationTargetSelectorTest      | 6     | 6      | 0      | 0.023s |
| FleetAllocatorTest               | 5     | 5      | 0      | 0.085s |
| **Total**                        | **21**| **21** | **0**  | -      |

All acceptance criteria from the plan satisfied: every required class/data class/constant/method exists, no `@Service` or `@Component` annotations, all four files in package `com.openlogh.engine.ai.strategic`.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Plan interface stub mismatched actual Fleet entity**
- **Found during:** Task 1 implementation
- **Issue:** The plan's `<interfaces>` block describes `Fleet.ships: Int` and `Fleet.officerId: Long`, but the real entity (`backend/game-app/src/main/kotlin/com/openlogh/entity/Fleet.kt`) has `currentUnits: Int` and `leaderOfficerId: Long` instead. There is no `ships` field on Fleet. Similarly the plan stub claimed `Planet.tradeRoute: Short`, `orbitalDefense: Short`, `fortress: Short`, but the real fields are all `Int`.
- **Fix:** Computed `totalShips = fleets.sumOf { currentUnits * 300 }` using the gin7 ships-per-unit constant from `CLAUDE.md` (battleship/cruiser/destroyer/etc. all carry 300 ships per unit), and exposed it as `StrategicPowerScorer.SHIPS_PER_UNIT = 300`. The OperationTargetSelector SWEEP path uses the same constant. All Planet field accesses were left as `Int`.
- **Files modified:** StrategicPowerScorer.kt, OperationTargetSelector.kt
- **Commit:** `72a481df`

**2. [Rule 3 - Blocking] Task ordering — production code committed before tests**
- **Found during:** Plan analysis
- **Issue:** The plan marks both Task 1 (production) and Task 2 (tests) with `tdd="true"`, but Task 1's `<done>` block requires "All unit tests pass" while the test files only get created in Task 2. Strict TDD RED→GREEN ordering would deadlock.
- **Fix:** Wrote production code first under Task 1 (verified by `compileKotlin`), then wrote tests under Task 2 (verified by `:game-app:test`). The plan's per-task `<verify>` commands are consistent with this ordering — Task 1 verifies compilation only, Task 2 verifies tests. Both are committed atomically.
- **Files modified:** None (process change)
- **Commit:** `72a481df`, `f428c3e4`

**3. [Rule 3 - Blocking] JAVA_HOME pointed to JDK 25, project requires JDK 17**
- **Found during:** First gradle compile attempt
- **Issue:** Default `java_home` returned JDK 25 which Spring Boot 3.4.2 / Kotlin 2.1.0 cannot use (`* What went wrong: 25.0.2`). Build failed immediately with no useful diagnostic.
- **Fix:** Exported `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home` for all gradle invocations. JDK 17 is documented in `CLAUDE.md` Tech Stack as the required runtime and was already installed. No project files were modified — this is a per-shell environment fix.
- **Files modified:** None
- **Commit:** N/A

### Authentication Gates

None — fully automated execution.

## Key Decisions

- **Fleet ship count uses `currentUnits * 300`**: Fleet has no `ships` field so the plan's "sum of fleet.ships" was interpreted via the gin7 SHIPS_PER_UNIT constant from CLAUDE.md. Exposed as a public `const val SHIPS_PER_UNIT = 300` for callers and tests.
- **Composite formula uses raw additive weights, not normalized**: `ships*0.5 + commander*30 + defense*20`. The 30/20 multipliers compensate for commander/defense raw magnitudes being much smaller than ship counts. Normalization is left to comparison time so absolute scores remain interpretable for logging.
- **Commander score is averaged across officers, not summed**: A pair of elite officers (avg 90) outscores six average ones (avg 50) — deliberate. Reflects gin7's "few exceptional commanders" gameplay over zerg.
- **OperationTargetSelector takes pre-built `Map<planetId, List<Fleet>>`**: This pushes data shaping (and any galaxy-graph adjacency filtering) into the planner side, keeping the selector pure and easily mockable.
- **`DEFENSE_THREAT_RATIO = 0.7`**: An own front-line system is "threatened" only when nearby enemy estimated power exceeds 70% of own power. Avoids spamming low-priority DEFENSE candidates for systems with ample buffer.
- **`AGGRESSIVE_CONQUEST_BIAS = 1.5`, `DEFENSIVE_DEFENSE_BIAS = 1.5`, `CAUTIOUS_SWEEP_BIAS = 0.8`**: Multiplicative score adjustments per D-10. Exposed as `const val` so the upcoming planner test (Plan 13-02) can assert on them.
- **All public data classes named `StarSystemPower`, `OperationCandidate`, `AllocationResult`**: Concrete domain nouns, not generic `Result`. Avoids collisions with `kotlin.Result`.

## Files Touched

**Created (8):**
- `backend/game-app/src/main/kotlin/com/openlogh/engine/ai/strategic/StrategicPowerScorer.kt` (82 lines)
- `backend/game-app/src/main/kotlin/com/openlogh/engine/ai/strategic/FogOfWarEstimator.kt` (53 lines)
- `backend/game-app/src/main/kotlin/com/openlogh/engine/ai/strategic/OperationTargetSelector.kt` (159 lines)
- `backend/game-app/src/main/kotlin/com/openlogh/engine/ai/strategic/FleetAllocator.kt` (60 lines)
- `backend/game-app/src/test/kotlin/com/openlogh/engine/ai/strategic/StrategicPowerScorerTest.kt` (95 lines)
- `backend/game-app/src/test/kotlin/com/openlogh/engine/ai/strategic/FogOfWarEstimatorTest.kt` (87 lines)
- `backend/game-app/src/test/kotlin/com/openlogh/engine/ai/strategic/OperationTargetSelectorTest.kt` (242 lines)
- `backend/game-app/src/test/kotlin/com/openlogh/engine/ai/strategic/FleetAllocatorTest.kt` (87 lines)

**Modified:** none

## Known Stubs

None. All four scorers implement their full intended behavior with no placeholders. The OperationTargetSelector contains a documented simplification — it treats every enemy front-line planet as "adjacent" for DEFENSE threat scanning, deferring true adjacency filtering to the upstream planner that owns the galaxy graph (Plan 13-02). This is documented inline in the selector, is the intended composition boundary, and is exercised correctly in the DEFENSE unit test (which keeps the test scenario to a single enemy planet).

## Self-Check: PASSED

All 8 source/test files present on disk. Both task commits (`72a481df`, `f428c3e4`) found in `git log --all`. Strategic test suite reports 21/21 passing under JDK 17. No untracked files generated by the build that would need `.gitignore` updates.
