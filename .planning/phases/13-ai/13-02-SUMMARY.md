---
phase: 13-ai
plan: 02
subsystem: strategic-ai
tags: [ai, strategic, faction, operation-plan, kotlin, spring, mockito]
dependency_graph:
  requires:
    - "13-01 strategic scorers (StrategicPowerScorer, OperationTargetSelector, FleetAllocator)"
    - "12-01/02/03/04 OperationPlan entity + service + CommandExecutor wiring"
    - "Phase 11 PersonalityTrait 5-trait enum"
  provides:
    - "FactionAI.executeStrategicOperations() — atWar branch producing OperationPlans through CommandExecutor"
    - "Synthetic enemy Fleet construction from Officer.ships via SHIPS_PER_UNIT"
    - "StubCommandExecutor test double pattern for suspend-function mocking without mockito-kotlin"
  affects:
    - "Phase 14 (strategic AI UI) — will read OperationPlans created by faction AI"
    - "Future tactical AI phases — AI operations now visible to BattleTriggerService"
tech-stack:
  added: []
  patterns:
    - "Subclass-based test double for Kotlin suspend functions (mockito-kotlin absent from classpath)"
    - "Synthetic Fleet materialization from Officer.ships when enemy fleet data not directly queryable"
    - "Sovereign (Faction.chiefOfficerId) as AI-acting officer for faction-level commands"
key-files:
  created:
    - .planning/phases/13-ai/13-02-SUMMARY.md
    - .planning/phases/13-ai/deferred-items.md
  modified:
    - backend/game-app/src/main/kotlin/com/openlogh/engine/ai/FactionAI.kt
    - backend/game-app/src/test/kotlin/com/openlogh/engine/ai/NationAITest.kt
key-decisions:
  - "FactionAI constructor now takes CommandExecutor + FleetRepository + OperationPlanRepository — matches Phase 12 CommandExecutor injection pattern"
  - "Synthetic enemy Fleet built from officer.ships / SHIPS_PER_UNIT (300, clamped to ≥1) — enemy fleet entities not directly queryable by FactionAI without new port method"
  - "Sovereign loaded via ports.officer(chiefOfficerId) not fleetRepository — leverages existing PartialJpaWorldPorts path"
  - "Legacy 삼국지 constants (급습/의병모집/필사즉생/strategicCmdLimit) fully removed per D-08"
  - "Plan interfaces block described Fleet.ships/officerId but real entity uses currentUnits/leaderOfficerId (matching Plan 13-01 Deviation 1 pattern) — adapted synthetic fleet construction accordingly"
  - "StubCommandExecutor subclass pattern reused from CommandProposalServiceTest — Mockito cannot stub Kotlin suspend functions without mockito-kotlin (absent from :game-app classpath per Phase 12)"
  - "Test stubs officerRepository.findById for sovereign lookup because PartialJpaWorldPorts.officer(id) goes through findById, not findBySessionIdAndFactionId"
requirements-completed: [SAI-01, SAI-02]
metrics:
  duration_minutes: 9
  tasks_completed: 2
  files_modified: 2
  files_created: 2
  tests_added: 3
  tests_modified: 2
  tests_passing: 13
  completed_date: 2026-04-09
---

# Phase 13 Plan 02: FactionAI 전략 AI 통합 Summary

Replaced FactionAI's atWar branch with an OperationPlan-based strategic AI that composes the Plan 13-01 scorers (StrategicPowerScorer / OperationTargetSelector / FleetAllocator) behind the live CommandExecutor pipeline, fully removing the remaining 삼국지 잔재 (급습/의병모집/필사즉생/strategicCmdLimit).

## Performance

- **Duration:** ~9 min
- **Started:** 2026-04-09T14:15:00Z (approx)
- **Completed:** 2026-04-09T14:24:00Z
- **Tasks:** 2
- **Files modified:** 2
- **Files created:** 2

## Accomplishments

- **SAI-01:** AI 진영이 전쟁 상태일 때 자동으로 OperationPlan을 생성한다. FactionAI.executeStrategicOperations() iterates OperationTargetSelector candidates, greedy-allocates available fleets, and calls `commandExecutor.executeOfficerCommand("작전계획", sovereign, env, arg)` for each. Unlimited simultaneous operations per tick (D-05) bounded only by fleet availability.
- **SAI-02:** Power-based operation-type selection wired. PersonalityTrait bias (AGGRESSIVE→CONQUEST, DEFENSIVE→DEFENSE, CAUTIOUS→SWEEP×0.8) lands through the sovereign's personality. Fog-of-war noise injection only bypassed when a friendly officer with intelligence ≥ 70 is stationed at the target.
- **삼국지 잔재 전량 제거:** `급습`, `의병모집`, `필사즉생` warActions list and `strategicCmdLimit` gate deleted. `grep` over both FactionAI.kt and NationAITest.kt returns zero matches.
- **Non-war logic untouched:** 발령, 증축, 포상, 불가침제의, 선전포고, 천도 paths preserved verbatim. All 11 non-war NationAITest cases still green without any test-side modification beyond the new constructor shape.
- **Committed fleet exclusion (D-09):** `operationPlanRepository.findBySessionIdAndFactionIdAndStatusIn(sessionId, factionId, [PENDING, ACTIVE])` is queried before allocation; participantFleetIds flattened into a set and subtracted from the available fleet pool.

## Task Commits

1. **Task 1: Replace FactionAI atWar branch with strategic OperationPlan AI** — `705945d0` (feat)
   - `FactionAI.kt`: +162 lines / -7 lines. New `executeStrategicOperations()` + `buildCommandEnv()` helper. Constructor expanded with CommandExecutor, FleetRepository, OperationPlanRepository.
2. **Task 2: Update NationAITest for new FactionAI constructor and atWar behavior** — bundled into `567b2d82` (commit labeled `fix(galaxy)` — see Issues Encountered)
   - `NationAITest.kt`: +207 lines / -24 lines. New StubCommandExecutor test double, three new atWar tests, createNation() takes `chiefOfficerId`, legacy `strategicCmdLimit` parameter removed.

## Files Created/Modified

- `backend/game-app/src/main/kotlin/com/openlogh/engine/ai/FactionAI.kt` — atWar branch replaced with executeStrategicOperations(); loads sovereign, builds enemy-fleet map from officer.ships, selects targets, greedy-allocates, executes 작전계획 through CommandExecutor. (604 lines)
- `backend/game-app/src/test/kotlin/com/openlogh/engine/ai/NationAITest.kt` — StubCommandExecutor test double (overrides executeOfficerCommand and records invocations), new tests for "creates operation plan when at war", "no sovereign", "all fleets committed", createNation(chiefOfficerId=) helper. (440 lines)
- `.planning/phases/13-ai/deferred-items.md` — pre-existing NpcPolicyTest failure logged as out-of-scope (introduced by Phase 01 legacy cleanup, not Phase 13).
- `.planning/phases/13-ai/13-02-SUMMARY.md` — this file.

## Decisions Made

- **Synthetic enemy Fleet construction.** FactionAI has no efficient way to query enemy factions' Fleet entities (FleetRepository has only `findBySessionIdAndFactionId(sessionId, factionId)` — one call per enemy faction would be N+1). Instead, built synthetic `Fleet(id=-officer.id, currentUnits=(officer.ships/300).coerceAtLeast(1))` from enemy officers' `ships` field. Negative ids guarantee no collision with real committed fleet ids. This is consistent with StrategicPowerScorer's `currentUnits * SHIPS_PER_UNIT` formula, so own and enemy totals remain comparable.
- **Sovereign loaded via `ports.officer(chiefOfficerId)`.** This goes through `PartialJpaWorldPorts.officer(id)` → `officerRepository.findById(id)`, which keeps the existing port-based access pattern. Tests stub `findById` with `Optional.of(sovereign)` for the happy-path scenario.
- **StubCommandExecutor instead of `Mockito.when().thenReturn()`.** Mockito cannot stub Kotlin `suspend` functions without the mockito-kotlin library, which is deliberately NOT on the :game-app classpath (Phase 12 D-17). The codebase already has a `SuccessCommandExecutor` in CommandProposalServiceTest using the subclass approach; NationAITest adopts the same pattern as `StubCommandExecutor` but exposes an `invocations` list so tests can assert on args without needing Mockito verify on suspend fn.
- **createNation() helper rewired.** Removed legacy `strategicCmdLimit: Short = 0` parameter and added `chiefOfficerId: Long = 0`. Non-war tests set `chiefOfficerId=0` by default (sovereign absent → atWar check never enters the new code path).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 — Blocking] Plan interfaces stub misrepresented Fleet entity**
- **Found during:** Task 1 implementation of executeStrategicOperations()
- **Issue:** Plan 13-02's `<interfaces>` block described `Fleet(ships, officerId)` and the synthetic fleet construction snippet used `ships = officer.ships, officerId = officer.id`. The real Fleet entity (`backend/game-app/src/main/kotlin/com/openlogh/entity/Fleet.kt`) uses `currentUnits: Int` and `leaderOfficerId: Long` instead. There is no `Fleet.ships` or `Fleet.officerId` field. This is the exact same drift that Plan 13-01 Deviation 1 documented.
- **Fix:** Construct synthetic enemy fleets as `Fleet(id=-officer.id, sessionId=sessionId, leaderOfficerId=officer.id, factionId=officer.factionId, planetId=officer.planetId, currentUnits=(officer.ships/StrategicPowerScorer.SHIPS_PER_UNIT).coerceAtLeast(1))`. The `coerceAtLeast(1)` floor ensures officers with small ship counts (<300) still contribute a visible unit for scoring, matching the intent of D-01.
- **Files modified:** FactionAI.kt
- **Verification:** `./gradlew :game-app:compileKotlin` green; 13/13 NationAITest cases pass.
- **Committed in:** `705945d0`

**2. [Rule 3 — Blocking] Test needed officerRepository.findById stub**
- **Found during:** First run of `decideNationAction creates operation plan when at war with available fleets`
- **Issue:** Test produced `Nation휴식` instead of `작전계획`. Root cause: `FactionAI.executeStrategicOperations()` calls `ports.officer(sovereignId)` which goes through `PartialJpaWorldPorts.officer(id)` → `officerRepository.findById(id)`. The test only stubbed `findBySessionIdAndFactionId` and `findBySessionId`, so `findById` returned `Optional.empty()`, causing the early "no sovereign" return.
- **Fix:** Added `when(officerRepository.findById(1L)).thenReturn(Optional.of(sovereign))` to both "creates operation plan when at war" and "all fleets committed" tests. Imported `java.util.Optional`.
- **Files modified:** NationAITest.kt
- **Verification:** 13/13 NationAITest cases pass.
- **Committed in:** `567b2d82` (alongside Task 2 test changes)

**3. [Rule 3 — Blocking] JAVA_HOME pointed to JDK 25**
- **Found during:** First gradle invocation
- **Issue:** Default `java_home` returned JDK 25 (Spring Boot 3.4.2 + Kotlin 2.1.0 cannot use it), producing cryptic "* What went wrong: 25.0.2" from Gradle.
- **Fix:** `export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home` prefixed to every gradle invocation. Same fix as Plan 13-01 Deviation 3; no project files touched.
- **Files modified:** none
- **Committed in:** n/a

---

**Total deviations:** 3 auto-fixed (all Rule 3 — blocking). **Impact:** All three deviations were necessary to get the implementation and tests compiling/running. Deviation 1 adapts to the real entity schema, Deviation 2 fixes a test setup gap uncovered only by running the test, Deviation 3 is environmental. No deviations introduced new functionality beyond what SAI-01/SAI-02 require, and none changed the plan's behavioral contract.

## Issues Encountered

### NpcPolicyTest pre-existing failure (out of scope)

During `./gradlew :game-app:test --tests "com.openlogh.engine.ai.*"`, one test fails: `NpcPolicyTest > default priority lists match expected order` at line 40. The assertion expects `NpcNationPolicy.DEFAULT_NATION_PRIORITY.last()` to be `"전시전략"`, but the list's current last element is `"NPC몰수"` and the string `"전시전략"` does not appear anywhere in `NpcPolicy.kt` main source.

This is a pre-existing failure: `NpcPolicy.kt` was last modified by commit `613125d4 fix(phase-01): remove remaining 삼국지 CrewType references in NpcPolicy + SpecialAssignmentService`, which removed legacy 삼국지 priority entries without updating this test. Documented separately in `.planning/phases/13-ai/deferred-items.md` for a future cleanup. Out of scope per the "Only auto-fix issues DIRECTLY caused by the current task's changes" rule.

183/184 :game-app AI tests green. 100% of Phase 13 target tests (NationAITest 13/13 + strategic.* 21/21) are green.

### Parallel-agent commit collision on NationAITest.kt

The NationAITest.kt Task 2 changes were picked up by a parallel agent's commit (`567b2d82 fix(galaxy): size Stage from real container before first paint`) rather than being committed separately under a `test(13-ai-02):` label. The code is functionally correct and the `567b2d82` commit does contain the full NationAITest.kt delta, but the commit message is misleading. No re-commit because splitting the commit would require `git reset` on a parallel branch state.

## User Setup Required

None — no external services, no config file edits, no new environment variables.

## Next Phase Readiness

- **Phase 14 (strategic AI UI):** AI-created OperationPlans are now discoverable via `operationPlanRepository.findBySessionIdAndStatus(sessionId, ...)`. UI can filter `issuedByOfficerId == faction.chiefOfficerId` to distinguish AI vs player operations.
- **Phase 11 tactical AI integration:** BattleTriggerService already consumes OperationPlans (Phase 12 D-08 sync channel). AI-created operations will flow through the same path as player operations — no additional wiring.
- **Known concern:** Enemy fleet approximation from `officer.ships` under-represents any faction that has organized its forces into real Fleet entities but parked them away from officer home planets. A future refinement could add a `fleetRepository.findBySessionId(sessionId)` call and group by both faction and planet. Not blocking for Phase 13 scope — the synthetic approximation is good enough for AI decision-making at current game scale.

## Known Stubs

None. The synthetic enemy Fleet construction is documented as an approximation (see "Synthetic enemy fleets built from enemy officer ship counts" inline comment), but it is a fully-implemented, deterministic computation — not a placeholder. No hardcoded empty collections, no "coming soon" strings, no skipped branches.

## Self-Check: PASSED

- FOUND: `backend/game-app/src/main/kotlin/com/openlogh/engine/ai/FactionAI.kt`
- FOUND: `backend/game-app/src/test/kotlin/com/openlogh/engine/ai/NationAITest.kt`
- FOUND: `.planning/phases/13-ai/13-02-SUMMARY.md`
- FOUND: `.planning/phases/13-ai/deferred-items.md`
- FOUND: commit `705945d0` (Task 1 FactionAI)
- FOUND: commit `567b2d82` (Task 2 NationAITest bundled with galaxy fix)

Test results on HEAD:
- `./gradlew :game-app:compileKotlin -x :gateway-app:compileKotlin` → BUILD SUCCESSFUL
- `./gradlew :game-app:test --tests "com.openlogh.engine.ai.NationAITest"` → 13/13 pass, BUILD SUCCESSFUL
- `./gradlew :game-app:test --tests "com.openlogh.engine.ai.strategic.*"` → 21/21 pass (unchanged from Plan 13-01)
- `./gradlew :game-app:test --tests "com.openlogh.engine.ai.*"` → 183/184 pass; one pre-existing failure (NpcPolicyTest) logged to deferred-items.md

---
*Phase: 13-ai*
*Plan: 02*
*Completed: 2026-04-09*
