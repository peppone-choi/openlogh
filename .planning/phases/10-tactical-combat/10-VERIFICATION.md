---
phase: 10-tactical-combat
verified: 2026-04-07T00:00:00Z
status: gaps_found
score: 0/6 must-haves verified
gaps:
  - truth: "사령관이 사전에 후계자를 지명할 수 있고, 부상 시 지휘력 저하와 함께 지휘권 위임이 가능하다 (SUCC-01, SUCC-02)"
    status: failed
    reason: "No successor designation endpoint, command, or service exists. InjuryEvent model records injury but never triggers command capability reduction or succession delegation."
    artifacts:
      - path: "backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/CommandHierarchyService.kt"
        issue: "No designateSuccessor(), setCommandCapability(), or delegateCommand() methods"
      - path: "backend/game-app/src/main/kotlin/com/openlogh/model/DeathInjurySystem.kt"
        issue: "InjuryEvent records severity but no code path reads it to reduce command capability or trigger succession"
    missing:
      - "Successor designation command/WebSocket endpoint (e.g., /battle/{sessionId}/{battleId}/designate-successor)"
      - "CommandHierarchyService.designateSuccessor() method"
      - "Injury severity -> command capability reduction logic in TacticalBattleEngine"
      - "Command delegation trigger when commander is injured"

  - truth: "사령관 사망(기함 격침) 시 30틱 공백 후 사전 지명자가 승계하며, 지명자 부재/사망 시 차순위 계급자가 자동 승계한다 (SUCC-03, SUCC-04)"
    status: failed
    reason: "CommandHierarchy.successionQueue field exists but is never read or processed in TacticalBattleEngine. No 30-tick vacancy countdown. No flagship-destruction-triggers-succession logic."
    artifacts:
      - path: "backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/CommandHierarchy.kt"
        issue: "successionQueue field declared but zero code reads it to execute succession"
      - path: "backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/TacticalBattleEngine.kt"
        issue: "grep for 'successionQueue' or 'successionTick' returns no matches — queue is populated at init but never processed"
    missing:
      - "30-tick vacancy counter in TacticalBattleState"
      - "CommandHierarchyService.executeSuccession() — pop queue and transfer command"
      - "Flagship destruction -> vacancy start logic in TacticalBattleEngine.processTick()"
      - "Fallback to rank-ordered successionQueue when designated successor is also dead"

  - truth: "분함대장 지휘 불가 시 해당 유닛이 사령관 직할로 복귀한다 (SUCC-05)"
    status: failed
    reason: "No code path detects subfleet commander incapacitation and returns their units to fleet commander direct control."
    artifacts:
      - path: "backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/CommandHierarchyService.kt"
        issue: "assignSubFleet() and resolveCommanderForUnit() exist but no returnUnitsToDirectCommand() or subCommanderIncapacitatedCheck()"
      - path: "backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/TacticalBattleEngine.kt"
        issue: "Unit death handling does not check if dead unit is a subfleet commander and reassign their units"
    missing:
      - "CommandHierarchyService.returnUnitsToDirectCommand(subCommanderId) method"
      - "TacticalBattleEngine: on unit death, check if unit.officerId == any SubFleet.commanderId and trigger return"

  - truth: "모든 사령관 지휘 불가 시 지휘 체계가 붕괴하여 각 유닛이 독립 AI로 행동한다 (SUCC-06)"
    status: failed
    reason: "No command breakdown detection or independent AI transition exists anywhere in the codebase."
    artifacts:
      - path: "backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/TacticalBattleEngine.kt"
        issue: "No check for 'all commanders incapacitated' condition, no INDEPENDENT_AI unit state, no OutOfCrcBehavior fallback triggered by succession failure"
      - path: "backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/OutOfCrcBehavior.kt"
        issue: "File exists (out-of-CRC AI behavior) but is not invoked when succession chain is exhausted"
    missing:
      - "CommandHierarchy.isCommandBroken() check in TacticalBattleEngine per-tick"
      - "Transition to OutOfCrcBehavior for all units when command is broken"
      - "BattleTickEvent for command breakdown broadcast to frontend"
---

# Phase 10: Tactical Combat Verification Report

**Phase Goal (ROADMAP):** 사령관 부상/사망 시 지휘권이 규칙에 따라 승계되며, 체계 붕괴 시 유닛이 독립 AI로 전환된다
**Phase Directory:** 10-tactical-combat
**Verified:** 2026-04-07
**Status:** GAPS FOUND
**Re-verification:** No — initial verification

---

## Critical Finding: Goal / Implementation Mismatch

The phase plans (10-01 through 10-04) implement a **tactical combat UI/engine** system
(energy allocation, formations, fortress guns, battle map frontend). The ROADMAP Phase 10
goal and REQUIREMENTS.md both define Phase 10 as **지휘 승계 (Command Succession)**
with requirements SUCC-01 through SUCC-06.

The tactical combat system built is **genuinely substantive and fully wired** — it is real,
committed code — but it addresses a scope different from Phase 10's contracted requirements.
The SUCC requirements remain unimplemented.

Additionally, the plans reference TAC-01 through TAC-04 requirements, which **do not exist
anywhere in REQUIREMENTS.md**. These are orphaned requirement IDs with no specification.

---

## Goal Achievement

### Observable Truths (from ROADMAP Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|---------|
| 1 | 사령관이 사전에 후계자를 지명할 수 있고, 부상 시 지휘력 저하와 함께 지휘권 위임이 가능하다 | ✗ FAILED | No designateSuccessor() method, no injury-to-capability-reduction path |
| 2 | 사령관 사망(기함 격침) 시 30틱 공백 후 사전 지명자가 승계하며, 지명자 부재/사망 시 차순위 계급자가 자동 승계한다 | ✗ FAILED | successionQueue exists on CommandHierarchy but is never processed in TacticalBattleEngine |
| 3 | 분함대장 지휘 불가 시 해당 유닛이 사령관 직할로 복귀한다 | ✗ FAILED | Unit death handling does not check subfleet commander status |
| 4 | 모든 사령관 지휘 불가 시 지휘 체계가 붕괴하여 각 유닛이 독립 AI로 행동한다 | ✗ FAILED | No command-broken check, OutOfCrcBehavior.kt exists but is not triggered by succession exhaustion |

**Score: 0/4 truths verified**

---

## Required Artifacts (per PLAN frontmatter — Tactical Combat scope)

The following artifacts were claimed by the plans. All exist and are substantive.
They implement the *tactical combat* system, not the *succession* system.

| Artifact | Status | Details |
|----------|--------|---------|
| `backend/.../model/EnergyAllocation.kt` | ✓ VERIFIED | 6 channels, sum=100 enforced via `init { require(total() == 100) }`, multiplier methods present |
| `backend/.../model/Formation.kt` | ✓ VERIFIED | 4 formations with distinct attackModifier/defenseModifier/speedModifier |
| `backend/.../model/BattlePhase.kt` | ✓ VERIFIED | PREPARING/ACTIVE/PAUSED/ENDED lifecycle |
| `backend/.../model/TacticalUnitState.kt` | ✓ VERIFIED | Exists in model directory |
| `backend/.../entity/TacticalBattle.kt` | ✓ VERIFIED | JPA entity with JSONB battleState, participants, all plan fields present |
| `backend/.../repository/TacticalBattleRepository.kt` | ✓ VERIFIED | File exists |
| `backend/.../db/migration/V37__tactical_battle.sql` | ✓ VERIFIED | Creates tactical_battle table with correct columns and 3 indexes |
| `backend/.../engine/tactical/TacticalBattleEngine.kt` | ✓ VERIFIED | Substantive — TacticalUnit, TacticalBattleState, energy/formation/damage/movement logic present |
| `backend/.../engine/tactical/FortressGunSystem.kt` | ✓ VERIFIED | Line-of-fire with friendly fire per gin7 manual, 4 gun types |
| `backend/.../engine/tactical/BattleTriggerService.kt` | ✓ VERIFIED | createBattle(), buildInitialState() present |
| `backend/.../dto/TacticalBattleDtos.kt` | ✓ VERIFIED | File exists |
| `backend/.../service/TacticalBattleService.kt` | ✓ VERIFIED | Full lifecycle management with ConcurrentHashMap, setEnergyAllocation, setFormation, retreat |
| `backend/.../controller/BattleWebSocketController.kt` | ✓ VERIFIED (name differs from plan) | Plan specified TacticalBattleController; actual file is BattleWebSocketController.kt with @MessageMapping for energy/stance/retreat/attack-target |
| `backend/.../controller/TacticalBattleRestController.kt` | ✓ VERIFIED | GET /api/v1/battle/{sessionId}/active, /{battleId}, /{battleId}/history |
| `frontend/src/types/tactical.ts` | ✓ VERIFIED | TypeScript types file exists |
| `frontend/src/lib/tacticalApi.ts` | ✓ VERIFIED | API client exists |
| `frontend/src/stores/tacticalStore.ts` | ✓ VERIFIED | Zustand store with loadActiveBattles, loadBattle, onBattleTick, setEnergy, setFormation |
| `frontend/src/components/tactical/BattleMap.tsx` | ✓ VERIFIED | React Konva (not SVG as SUMMARY claimed) battle map with unit icons, grid, stars |
| `frontend/src/components/tactical/EnergyPanel.tsx` | ✓ VERIFIED | 6-channel sliders with auto-redistribution maintaining sum=100 |
| `frontend/src/components/tactical/FormationSelector.tsx` | ✓ VERIFIED | File exists |
| `frontend/src/components/tactical/BattleStatus.tsx` | ✓ VERIFIED | File exists |
| `frontend/src/app/(game)/tactical/page.tsx` | ✓ VERIFIED | Composed with BattleMap, EnergyPanel, FormationSelector, BattleStatus, WebSocket subscription |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| TacticalBattleService | TacticalBattleEngine | `engine.processTick()` | ✓ WIRED | engine injected in constructor, processTick called in processBattleTick |
| TacticalBattleService | TacticalBattleRepository | save/findById | ✓ WIRED | repository injected, save called on battle state |
| BattleWebSocketController | TacticalBattleService | setEnergyAllocation/setFormation | ✓ WIRED | controller calls service methods |
| TacticalBattleRestController | TacticalBattleService | getActiveBattles/getBattleState | ✓ WIRED | controller delegates to service |
| TickEngine | TacticalBattleService | processSessionBattles(worldId) | ✓ WIRED | TickEngine.kt line 76 calls processSessionBattles each game tick |
| tactical/page.tsx | tacticalStore | useTacticalStore hook | ✓ WIRED | page uses store, WebSocket subscription wired |
| tacticalStore | tacticalApi | getActiveBattles/getBattleState | ✓ WIRED | loadActiveBattles and loadBattle call tacticalApi |
| EnergyPanel | tacticalStore | setEnergy | ✓ WIRED | EnergyPanel.onChange calls setEnergy in page |

---

## Requirements Coverage

### Requirements Declared in Plans

Plans 10-01 through 10-04 reference: TAC-01, TAC-02, TAC-03, TAC-04

| Requirement | Source Plan | Description | Status |
|-------------|------------|-------------|--------|
| TAC-01 | 10-01, 10-02, 10-03, 10-04 | Not defined in REQUIREMENTS.md | ✗ ORPHANED — no specification found |
| TAC-02 | 10-01, 10-02, 10-03, 10-04 | Not defined in REQUIREMENTS.md | ✗ ORPHANED — no specification found |
| TAC-03 | 10-01, 10-02 | Not defined in REQUIREMENTS.md | ✗ ORPHANED — no specification found |
| TAC-04 | 10-02, 10-04 | Not defined in REQUIREMENTS.md | ✗ ORPHANED — no specification found |

TAC-01 through TAC-04 do not appear in REQUIREMENTS.md. They are phantom requirement IDs.

### Requirements Mapped to Phase 10 by ROADMAP.md and REQUIREMENTS.md

| Requirement | Description | Status | Evidence |
|-------------|-------------|--------|---------|
| SUCC-01 | 사령관이 사전에 후계자를 지명할 수 있다 | ✗ NOT IMPLEMENTED | No designateSuccessor endpoint or service method |
| SUCC-02 | 사령관 부상 시 지휘력 저하, 후계자 지명으로 지휘권 위임 가능 | ✗ NOT IMPLEMENTED | InjuryEvent records severity but no command-capability-reduction path |
| SUCC-03 | 사령관 사망 시 30틱 공백 후 사전 지명자 승계 | ✗ NOT IMPLEMENTED | successionQueue field exists but is never processed |
| SUCC-04 | 사전 지명자 없거나 사망 시 차순위 계급자 자동 승계 | ✗ NOT IMPLEMENTED | No fallback rank-order succession logic |
| SUCC-05 | 분함대장 지휘 불가 시 해당 유닛이 사령관 직할로 복귀 | ✗ NOT IMPLEMENTED | Unit death handler does not check subfleet commander status |
| SUCC-06 | 모든 사령관 지휘 불가 시 지휘 체계 붕괴, 유닛 독립 AI 전환 | ✗ NOT IMPLEMENTED | OutOfCrcBehavior.kt exists but not triggered by succession exhaustion |

**SUCC coverage: 0/6**

---

## Anti-Patterns Found

| File | Pattern | Severity | Impact |
|------|---------|----------|--------|
| `CommandHierarchy.kt` | `successionQueue: MutableList<Long>` declared but never read in engine | ⚠️ Warning | Queue is a stub data field — populated at init, never consumed for succession |
| `REQUIREMENTS.md` Phase 10 traceability | Shows SUCC-01 through SUCC-06 as "Pending" for Phase 10 but ROADMAP progress table shows Phase 10 as "Complete" | ✗ Blocker | Requirements.md and ROADMAP.md are contradictory — progress table says complete, requirements say pending |

---

## Behavioral Spot-Checks

| Behavior | Result | Status |
|----------|--------|--------|
| V37 migration SQL validity | Valid CREATE TABLE with correct columns, 3 indexes | ✓ PASS |
| EnergyAllocation sum=100 constraint | `require(total() == 100)` in init block | ✓ PASS |
| Formation has 4 types | WEDGE/BY_CLASS/MIXED/THREE_COLUMN with distinct modifiers confirmed | ✓ PASS |
| TickEngine wires TacticalBattleService | `tacticalBattleService.processSessionBattles(world.id.toLong())` at line 76 | ✓ PASS |
| Successor designation endpoint | No `/designate-successor` or equivalent endpoint found | ✗ FAIL |
| 30-tick succession vacancy | No vacancyTick, successionTick, or tick countdown for succession found | ✗ FAIL |

---

## Human Verification Required

None required for automated checks — all gaps are deterministically absent from the codebase.

---

## Gaps Summary

Phase 10's ROADMAP goal is **지휘 승계 (Command Succession)** with six requirements
(SUCC-01 through SUCC-06). The phase directory instead implements a complete real-time
tactical combat system (energy allocation panels, formation selector, fortress guns,
battle map UI, WebSocket engine) which is substantive and fully wired.

This is a **scope substitution**: the tactical combat system may be valuable work (it
delivers a real feature), but it does not satisfy any of the six contracted SUCC
requirements. SUCC-01 through SUCC-06 require:

1. A **successor designation** command that a fleet commander can issue before combat.
2. An **injury-to-capability-reduction** path where damaged commanders lose command effectiveness.
3. A **30-tick vacancy countdown** after flagship destruction before succession activates.
4. A **rank-order fallback** when designated successor is also incapacitated.
5. **Subfleet unit return** to direct fleet command when subfleet commander is lost.
6. **Command breakdown detection** that transitions units to OutOfCrcBehavior independent AI.

The `successionQueue` field on `CommandHierarchy` and `InjuryEvent` model exist as data
structures but have no consuming logic. `OutOfCrcBehavior.kt` exists but is not triggered
by succession chain exhaustion.

The plans also reference TAC-01 through TAC-04 requirements which do not exist anywhere
in REQUIREMENTS.md — these are orphaned phantom IDs.

---

*Verified: 2026-04-07*
*Verifier: Claude (gsd-verifier)*
