---
phase: 13-ai
verified: 2026-04-09T05:35:00Z
status: passed
score: 8/8 must-haves verified
re_verification: false
---

# Phase 13: 전략 AI Verification Report

**Phase Goal:** AI 진영이 전쟁 상태에서 자동으로 작전계획을 수립하고 전력 평가에 따라 적절한 작전 유형을 선택한다
**Verified:** 2026-04-09T05:35:00Z
**Status:** PASSED
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

| #  | Truth                                                                              | Status     | Evidence                                                                                               |
|----|------------------------------------------------------------------------------------|------------|--------------------------------------------------------------------------------------------------------|
| 1  | 성계별 복합 전력 점수(함선+사령관능력+방어력)가 계산된다                           | VERIFIED   | `StrategicPowerScorer.evaluatePower()` — compositeScore = totalShips*0.5 + commanderScore*30 + defenseScore*20 |
| 2  | 첩보원이 없는 성계의 적 전력 추정치에 노이즈가 적용된다                           | VERIFIED   | `FogOfWarEstimator.applyFogNoise()` with ±40% noise when no agent; `INTELLIGENCE_THRESHOLD=70`        |
| 3  | 적 전선 성계 중 약하고 가치 높은 곳이 CONQUEST 대상으로 선택된다                 | VERIFIED   | `OperationTargetSelector` CONQUEST branch: `frontState>0`, score = strategicValue/(enemyPower+1)      |
| 4  | 위협받는 아군 전선 성계가 DEFENSE 대상으로 선택된다                               | VERIFIED   | DEFENSE branch: `maxEnemyThreat > ownPower * DEFENSE_THREAT_RATIO(0.7)`                               |
| 5  | 아군 영역 내 적 함대 존재 성계가 SWEEP 대상으로 선택된다                          | VERIFIED   | SWEEP branch: `enemyFleetsByPlanet[own.id]` non-empty                                                 |
| 6  | 성격(PersonalityTrait)에 따라 작전 유형 경향이 달라진다                           | VERIFIED   | AGGRESSIVE→CONQUEST×1.5, DEFENSIVE→DEFENSE×1.5, CAUTIOUS→SWEEP×0.8                                   |
| 7  | AI 진영이 교전 중일 때 FactionAI가 자동으로 OperationPlan을 생성한다              | VERIFIED   | `FactionAI.executeStrategicOperations()` called in `atWar` branch; executes `작전계획` via CommandExecutor |
| 8  | 삼국지 잔재(급습/의병모집/필사즉생, strategicCmdLimit) 코드가 FactionAI에서 제거됨 | VERIFIED   | grep on `FactionAI.kt` returns NONE for all four patterns                                             |

**Score:** 8/8 truths verified

---

### Required Artifacts

| Artifact                                                                              | Expected                      | Status   | Details                                                                                    |
|---------------------------------------------------------------------------------------|-------------------------------|----------|--------------------------------------------------------------------------------------------|
| `engine/ai/strategic/StrategicPowerScorer.kt`                                         | `object StrategicPowerScorer` | VERIFIED | 81 lines; `StarSystemPower`, `evaluatePower()`, formula constants present; no Spring DI   |
| `engine/ai/strategic/FogOfWarEstimator.kt`                                            | `object FogOfWarEstimator`    | VERIFIED | 52 lines; `INTELLIGENCE_THRESHOLD=70`, `NOISE_RANGE=0.4`, both methods; no Spring DI      |
| `engine/ai/strategic/OperationTargetSelector.kt`                                      | `object OperationTargetSelector` | VERIFIED | 156 lines; `OperationCandidate`, all three objectives, personality bias constants          |
| `engine/ai/strategic/FleetAllocator.kt`                                               | `object FleetAllocator`       | VERIFIED | 63 lines; `SUPERIORITY_MARGIN=1.3`, `AllocationResult`, greedy loop stops at threshold    |
| `engine/ai/FactionAI.kt`                                                              | atWar → OperationPlan AI      | VERIFIED | 604 lines; new constructor params, `executeStrategicOperations()`, all key calls present   |
| `test/engine/ai/strategic/StrategicPowerScorerTest.kt`                                | 2+ tests                      | VERIFIED | 3 tests, all green                                                                         |
| `test/engine/ai/strategic/FogOfWarEstimatorTest.kt`                                   | 4+ tests                      | VERIFIED | 7 tests, all green                                                                         |
| `test/engine/ai/strategic/OperationTargetSelectorTest.kt`                             | 5 tests                       | VERIFIED | 6 tests, all green                                                                         |
| `test/engine/ai/strategic/FleetAllocatorTest.kt`                                      | 3 tests                       | VERIFIED | 5 tests, all green                                                                         |
| `test/engine/ai/NationAITest.kt`                                                      | Updated for new constructor   | VERIFIED | 13 tests, all green; includes 3 new atWar tests                                            |

---

### Key Link Verification

| From                       | To                          | Via                                                | Status  | Details                                                            |
|----------------------------|-----------------------------|----------------------------------------------------|---------|--------------------------------------------------------------------|
| `OperationTargetSelector`  | `StrategicPowerScorer`      | `evaluatePower()` call per star system             | WIRED   | Called in both CONQUEST and DEFENSE branches (lines 67, 99, 110)  |
| `OperationTargetSelector`  | `FogOfWarEstimator`         | `applyFogNoise()` on enemy power estimates         | WIRED   | Called in CONQUEST (line 70) and DEFENSE (line 113) branches      |
| `FleetAllocator`           | `StrategicPowerScorer`      | `SHIPS_PER_UNIT` constant reuse in SWEEP branch    | WIRED   | `StrategicPowerScorer.SHIPS_PER_UNIT` used in FactionAI synthetic fleet construction |
| `FactionAI`                | `CommandExecutor`           | `executeOfficerCommand(actionCode="작전계획", ...)`| WIRED   | Line 249 in `FactionAI.kt`; wrapped in `runBlocking {}`           |
| `FactionAI`                | `OperationTargetSelector`   | `selectTargets()` for operation candidates         | WIRED   | Line 201 in `FactionAI.kt`                                        |
| `FactionAI`                | `FleetAllocator`            | `allocateFleets()` per candidate                   | WIRED   | Line 232 in `FactionAI.kt`                                        |
| `FactionAI`                | `OperationPlanRepository`   | `findBySessionIdAndFactionIdAndStatusIn`           | WIRED   | Line 216 in `FactionAI.kt`; filters PENDING+ACTIVE ops            |

---

### Data-Flow Trace (Level 4)

Not applicable — all artifacts are pure objects (Wave 1) or AI service logic (Wave 2) with no rendering components. Data flows from JPA repositories through the AI pipeline to `CommandExecutor.executeOfficerCommand`, which persists the `OperationPlan`. The pipeline is exercised end-to-end by `NationAITest.decideNationAction creates operation plan when at war with available fleets`.

---

### Behavioral Spot-Checks

| Behavior                                             | Command                                                                                  | Result           | Status  |
|------------------------------------------------------|------------------------------------------------------------------------------------------|------------------|---------|
| `game-app` Kotlin sources compile without errors     | `./gradlew :game-app:compileKotlin -x :gateway-app:compileKotlin`                        | BUILD SUCCESSFUL | PASS    |
| Strategic scorer unit tests pass (21 tests)          | `./gradlew :game-app:test --tests "com.openlogh.engine.ai.strategic.*"`                   | BUILD SUCCESSFUL | PASS    |
| NationAITest passes (13 tests)                       | `./gradlew :game-app:test --tests "com.openlogh.engine.ai.NationAITest"`                  | BUILD SUCCESSFUL | PASS    |

**Strategic test breakdown:**
- `StrategicPowerScorerTest`: 3/3 passed
- `FogOfWarEstimatorTest`: 7/7 passed
- `OperationTargetSelectorTest`: 6/6 passed
- `FleetAllocatorTest`: 5/5 passed
- `NationAITest`: 13/13 passed (includes 3 new atWar tests + 10 pre-existing non-war tests)

---

### Requirements Coverage

| Requirement | Source Plan | Description                                                       | Status    | Evidence                                                                                              |
|-------------|-------------|-------------------------------------------------------------------|-----------|-------------------------------------------------------------------------------------------------------|
| SAI-01      | 13-02       | AI 진영이 매 턴 OperationTargetSelector를 통해 작전 대상 성계를 선정한다 | SATISFIED | `FactionAI.executeStrategicOperations()` calls `OperationTargetSelector.selectTargets()` every `atWar` tick |
| SAI-02      | 13-01, 13-02 | 전력 평가 기반으로 작전 유형(점령/방어/소탕)을 선택한다             | SATISFIED | `StrategicPowerScorer` + `FogOfWarEstimator` feed into `OperationTargetSelector`; CONQUEST/DEFENSE/SWEEP with personality bias |

Both requirements marked `Complete` with `Phase 13` traceability in `.planning/REQUIREMENTS.md` (lines 50-51, 111-112).

---

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `engine/ai/OfficerAI.kt` | 795-796 | `strategicCmdLimit` + `급습/필사즉생/의병모집` in `OfficerAI.chooseNationTurn()` | Info | Pre-existing code in `OfficerAI`, not `FactionAI`. D-08 scoped removal to FactionAI's atWar block only. `chooseNationTurn()` is not wired into the turn pipeline per existing TODO comment. |
| `command/ArgSchemas.kt` | 317, 321, 324 | `급습`/`필사즉생`/`의병모집` as command argument schema entries | Info | Legitimate command definitions in the command registry — these are playable commands for human players. Not a Phase 13 concern. |
| `entity/Faction.kt` | 58 | `strategicCmdLimit: Short` field | Info | Entity field retained for backward-compat (used by `ConstraintHelper`, `InMemoryTurnProcessor`, frontend DTOs). Not required to be removed by Phase 13. |
| `engine/ai/FactionAI.kt` | — | None found | — | Clean: no `급습`, `의병모집`, `필사즉생`, or `strategicCmdLimit` |

No blockers. All anti-pattern hits are pre-existing or outside Phase 13 scope.

---

### Human Verification Required

None. All phase goals are verifiable programmatically. The `작전계획` command execution is tested by `NationAITest` with a `StubCommandExecutor` that records invocations and verifies `actionCode`, `generalId`, and `arg` contents.

---

### Notes

#### Messy Commit (flag, not a gap)

Commit `567b2d82 "fix(galaxy): size Stage from real container before first paint"` was bundled with Task 2 (NationAITest) changes due to a parallel-agent commit collision: pre-existing uncommitted frontend work (`frontend/src/`) was staged alongside the NationAITest changes at session start. The NationAITest changes are confirmed present in git history — `NationAITest.xml` shows 13 tests passing including all three new atWar tests (`creates operation plan when at war with available fleets`, `Nation휴식 when at war but no sovereign`, `Nation휴식 when at war but all fleets committed`). The frontend `BattleMap.tsx` and `map-canvas.tsx` changes in that commit are unrelated to Phase 13 AI work.

#### Pre-existing NpcPolicyTest Failure (do not count as Phase 13 gap)

`NpcPolicyTest.default priority lists match expected order` fails with `assertEquals("전시전략", nationPolicy.priority.last())` — actual last element is `"NPC몰수"`. This failure originates from commit `613125d4 fix(phase-01): remove remaining 삼국지 CrewType references in NpcPolicy`, which removed legacy priority entries without updating the test assertion. Phase 13 did not touch `NpcPolicy.kt`. Fix deferred per `.planning/phases/13-ai/deferred-items.md`: update assertion to `"NPC몰수"` or restore `"전시전략"` to the priority list pending product decision.

---

## Gaps Summary

No gaps. All 8 must-have truths verified, all 10 artifacts exist and are substantive, all 7 key links confirmed wired, 34 tests green, SAI-01 and SAI-02 both satisfied with traceability in REQUIREMENTS.md.

---

_Verified: 2026-04-09T05:35:00Z_
_Verifier: Claude (gsd-verifier)_
