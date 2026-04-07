---
phase: 08-scenario-character-system
verified: 2026-04-07T10:30:00Z
status: passed
score: 3/3 must-haves verified
re_verification: false
---

# Phase 8: 엔진 통합 + 커맨드 버퍼 Verification Report

**Phase Goal:** 듀얼 전술 엔진이 단일 엔진으로 통합되고, 커맨드 버퍼로 tick-WebSocket 동시성이 보장되며, 지휘 계층 데이터 모델이 전투 상태에 포함된다
**Verified:** 2026-04-07T10:30:00Z
**Status:** passed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| #  | Truth                                                                                                                                          | Status     | Evidence                                                                                                                  |
|----|------------------------------------------------------------------------------------------------------------------------------------------------|------------|---------------------------------------------------------------------------------------------------------------------------|
| 1  | TacticalCombatEngine(engine/war/)이 제거 또는 비활성화되고 TacticalBattleEngine 하나만 전술전을 처리한다                                       | ✓ VERIFIED | `engine/` has `tactical/` but no `war/` directory. `grep TacticalCombatEngine` in main sources returns only a comment line (line 88) in TacticalBattleEngine.kt — no live class. Commit b8e590a9 deleted the war/ package. |
| 2  | WebSocket으로 수신된 전술 명령이 ConcurrentLinkedQueue에 버퍼링되어 tick 시작 시점에 일괄 처리된다 (직접 상태 변경 없음)                        | ✓ VERIFIED | All 6 WebSocket handlers in BattleWebSocketController call `tacticalBattleService.enqueueCommand()`. `enqueueCommand()` calls `commandBuffer.offer()` (ConcurrentLinkedQueue). `processTick()` calls `drainCommandBuffer()` as Step 0 before any other processing. Old direct-mutation methods are `@Deprecated`. |
| 3  | TacticalBattleState에 CommandHierarchy(사령관-유닛 매핑, 승계 대기열)가 포함되어 전투 초기화 시 자동 생성된다                                 | ✓ VERIFIED | `TacticalBattleState` has `attackerHierarchy: CommandHierarchy?` and `defenderHierarchy: CommandHierarchy?` fields. `BattleTriggerService.buildInitialState()` calls `buildCommandHierarchy()` for both sides and passes them to the `TacticalBattleState` constructor. |

**Score:** 3/3 truths verified

### Required Artifacts

| Artifact                                                                                    | Expected                                               | Status     | Details                                                                                     |
|---------------------------------------------------------------------------------------------|--------------------------------------------------------|------------|---------------------------------------------------------------------------------------------|
| `engine/tactical/TacticalCommand.kt`                                                        | Sealed class with 7 subtypes, abstract battleId/officerId | ✓ VERIFIED | 73 lines. `sealed class TacticalCommand` with 7 data class subtypes: SetEnergy, SetStance, SetFormation, Retreat, SetAttackTarget, UnitCommand, PlanetConquest. Imports EnergyAllocation, Formation, UnitStance. |
| `engine/tactical/CommandHierarchy.kt`                                                       | Data model with fleetCommander, subCommanders, successionQueue, crcRadius, commJammed | ✓ VERIFIED | 45 lines. `data class CommandHierarchy` with all 5 fields. `data class SubFleet` as top-level class. Pure data — no Spring dependencies. |
| `engine/tactical/TacticalBattleEngine.kt`                                                   | Unified TacticalUnit, commandBuffer field, drainCommandBuffer() in processTick | ✓ VERIFIED | 694 lines. TacticalUnit has all merged fields (supplies, weaponCooldowns, debuffs, detectionCapability, stanceChangeTicksRemaining). `TacticalBattleState` has `commandBuffer: ConcurrentLinkedQueue<TacticalCommand>`. `processTick()` calls `drainCommandBuffer()` as Step 0. |
| `controller/BattleWebSocketController.kt`                                                   | All 6 handlers use enqueueCommand, no direct state mutation | ✓ VERIFIED | 191 lines. All 6 handlers (energy, stance, retreat, attack-target, planet-conquest, unit-command) call `tacticalBattleService.enqueueCommand()` with typed TacticalCommand subtypes. |
| `engine/tactical/BattleTriggerService.kt`                                                   | buildInitialState() initializes CommandHierarchy per side | ✓ VERIFIED | 249 lines. `buildInitialState()` builds attacker and defender hierarchies and passes both to `TacticalBattleState(attackerHierarchy=..., defenderHierarchy=...)`. `buildCommandHierarchyStatic` companion method exists for test isolation. |
| `engine/war/` (deleted)                                                                     | Must not exist                                         | ✓ VERIFIED | `ls engine/` output shows only: ai, event, map, modifier, tactical, trigger, turn. No `war/` directory. |

### Key Link Verification

| From                            | To                                           | Via                                                           | Status  | Details                                                                                         |
|---------------------------------|----------------------------------------------|---------------------------------------------------------------|---------|-------------------------------------------------------------------------------------------------|
| BattleWebSocketController       | TacticalBattleService.enqueueCommand()       | `tacticalBattleService.enqueueCommand(battleId, TacticalCommand.*)` | WIRED   | All 6 handlers verified to call enqueueCommand with typed subtype                              |
| TacticalBattleService.enqueueCommand | TacticalBattleState.commandBuffer       | `activeBattles[battleId]?.commandBuffer?.offer(command)`      | WIRED   | Line 162 in TacticalBattleService.kt                                                            |
| TacticalBattleEngine.processTick | drainCommandBuffer()                         | Step 0 call before aliveUnits filter                          | WIRED   | Line 216: `drainCommandBuffer(state)` is first action after tick counter increment             |
| drainCommandBuffer()             | applyCommand() → TacticalUnit mutation       | `commandBuffer.poll()` loop                                   | WIRED   | Polls until null; dispatches via exhaustive `when(cmd)` to all 7 subtypes                      |
| BattleTriggerService.buildInitialState | TacticalBattleState hierarchy fields  | `attackerHierarchy = buildCommandHierarchy(...)`, `defenderHierarchy = buildCommandHierarchy(...)` | WIRED   | Both hierarchies passed to TacticalBattleState constructor (lines 199-209 in BattleTriggerService.kt) |

### Data-Flow Trace (Level 4)

Not applicable for this phase. Artifacts are engine/service code, not data-rendering components. The command buffer is a processing pipeline, not a data display layer.

### Behavioral Spot-Checks

Step 7b: SKIPPED — no runnable entry points available without starting the Spring Boot JVM. All checks confirmed via static code analysis.

### Requirements Coverage

| Requirement | Source Plan | Description                                              | Status      | Evidence                                                                                        |
|-------------|-------------|----------------------------------------------------------|-------------|-------------------------------------------------------------------------------------------------|
| ENGINE-01   | 08-02       | 듀얼 전술 엔진이 단일 엔진으로 통합된다                 | ✓ SATISFIED | engine/war/ deleted (commit b8e590a9). TacticalCombatEngine class no longer exists in codebase. Only `// ── Merged from TacticalCombatEngine ──` comment remains as documentation. |
| ENGINE-02   | 08-03       | 커맨드 버퍼 패턴으로 tick 루프와 WebSocket 명령의 동시성이 보장된다 | ✓ SATISFIED | ConcurrentLinkedQueue in TacticalBattleState. enqueueCommand() uses lock-free offer(). drainCommandBuffer() is Step 0 of processTick(). |
| ENGINE-03   | 08-01/03    | 지휘 계층 데이터 모델(CommandHierarchy)이 전투 상태에 포함된다 | ✓ SATISFIED | CommandHierarchy data class exists. TacticalBattleState has attackerHierarchy + defenderHierarchy. buildInitialState() auto-generates both at battle creation. |

All 3 ENGINE requirements are satisfied. No orphaned requirements (REQUIREMENTS.md traceability table marks ENGINE-01/02/03 as Phase 8, Complete).

### Anti-Patterns Found

| File                              | Line | Pattern                                           | Severity | Impact                                                                                    |
|-----------------------------------|------|---------------------------------------------------|----------|-------------------------------------------------------------------------------------------|
| TacticalBattleEngine.kt           | 274  | `returnPlanetId = 0L` with comment "플레이스홀더" | ℹ️ Info  | Pre-existing: InjuryEvent placeholder filled by TacticalBattleService in a later step. Not a stub — the comment explains it's replaced by DB lookup post-battle. |
| TacticalBattleService.kt (multiple) | 169+ | `@Deprecated` direct mutation methods still present | ℹ️ Info  | Intentional backward-compat during transition. Marked with ReplaceWith annotations. Not blocking. |

No blocker anti-patterns found. The `@Deprecated` methods are an explicit design decision (documented in 08-03-SUMMARY.md decisions) to maintain backward compatibility during the v2.1 transition period.

### Human Verification Required

None. All three success criteria are fully verifiable through static code analysis:
- Engine unification: file system + grep confirms war/ deleted and TacticalCombatEngine class absent
- Command buffer: code path from WebSocket handler through enqueue to tick drain is traceable without execution
- CommandHierarchy: field presence in TacticalBattleState and initialization in buildInitialState() are code-level facts

### Gaps Summary

No gaps. All 3 phase success criteria are met by substantive, wired code:

1. **ENGINE-01 (engine unification)**: `engine/war/` is deleted. `TacticalCombatEngine` as a class does not exist anywhere in main sources. The only occurrence is a comment marker documenting where its fields were merged into `TacticalUnit`. All 6 controller handlers that previously called duplicate war/ controllers are unified under `BattleWebSocketController` → `TacticalBattleService` → `TacticalBattleEngine`.

2. **ENGINE-02 (command buffer)**: The pipeline is complete and correct: `BattleWebSocketController` enqueues via `TacticalCommand` sealed subtypes → `TacticalBattleService.enqueueCommand()` offers to `ConcurrentLinkedQueue` → `TacticalBattleEngine.processTick()` drains buffer as Step 0 before movement/detection/combat. Thread safety is guaranteed by `ConcurrentLinkedQueue` (lock-free). Old direct mutation methods are deprecated, not deleted, which is appropriate for a transition period.

3. **ENGINE-03 (CommandHierarchy in state)**: `TacticalBattleState` has `attackerHierarchy` and `defenderHierarchy` fields. `BattleTriggerService.buildInitialState()` auto-generates both hierarchies using `buildCommandHierarchyStatic()` which creates a rank-ordered succession queue and initializes CRC radius from the commander's `commandRange.maxRange`. A companion object static method enables unit testing without Spring context. All 9 tests (5 CommandBufferTest + 4 CommandHierarchyTest) were enabled and passing per 08-03-SUMMARY.md.

---

_Verified: 2026-04-07T10:30:00Z_
_Verifier: Claude (gsd-verifier)_
