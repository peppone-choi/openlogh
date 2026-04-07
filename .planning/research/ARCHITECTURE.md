# Architecture Patterns: v2.1 Tactical Command Chain + AI Integration

**Domain:** gin7 전술전 지휘체계 + 전술/전략 AI -- 기존 TacticalBattleEngine/WebSocket/AI 시스템 확장
**Researched:** 2026-04-07
**Sources:** 실제 코드베이스 직접 분석 (TacticalBattleEngine.kt, BattleWebSocketController.kt, TacticalBattleService.kt, CommandRange.kt, TacticalUnitState.kt, UtilityScorer.kt, AiCommandBridge.kt, OfficerAI.kt, FactionAI.kt, PersonalityTrait.kt, TickEngine.kt, BattleTriggerService.kt, Fleet.kt, UnitType.kt, CrewSlotRole.kt, CommandRangeCircle.tsx, tactical.ts)

---

## Current Architecture Baseline

### What Already Exists (DO NOT REBUILD)

```
TickEngine.processTick(world)            -- 1초 tick = 24 game-seconds
  |
  +-- tacticalBattleService.processSessionBattles(sessionId)
       |
       +-- for each activeBattle:
            engine.processTick(state)    -- TacticalBattleEngine (stateless, pure)
            engine.checkBattleEnd(state) -- returns BattleOutcome?
            broadcastBattleState()       -- STOMP -> /topic/world/{sid}/tactical-battle/{bid}

TacticalBattleService:
  activeBattles: ConcurrentHashMap<Long, TacticalBattleState>  -- ALL in-memory
  engine: TacticalBattleEngine (stateless engine with subsystems)

TacticalBattleEngine.processTick(state):
  1. updateCommandRange(unit)       -- commandRange grows per tick, resets on order
  2. processMovement(unit)          -- move toward nearest enemy (no command chain)
  3. detectionService.update()      -- SENSOR-based detection matrix
  4. processCombat(unit)            -- BEAM/GUN/MISSILE/FIGHTER damage
  5. fortressGunSystem              -- fortress gun fire
  6. Remove destroyed units         -- flagship destruction -> injury + replacement
  7. Ground battle tick             -- GroundBattleEngine
  8. updateMorale(unit)             -- morale effects

BattleWebSocketController:
  /energy, /stance, /retreat, /attack-target, /planet-conquest, /unit-command
  -- ALL use officerId in payload (no command chain filtering)

AI System:
  UtilityScorer (pure object)       -- scores CommandGroups by officer stats + personality
  AiCommandBridge (@Service)        -- ranks candidates, executes top-3 via CommandExecutor
  FactionAI (@Service)              -- strategic faction-level decisions (war, assignment, etc.)
  PersonalityTrait (5 types)        -- AGGRESSIVE/DEFENSIVE/BALANCED/POLITICAL/CAUTIOUS
  OfflinePlayerAIService            -- processes offline players every 100 ticks

CommandRange model:
  currentRange, maxRange, expansionRate, hasCommandRange
  tick() -> expand toward max
  resetOnCommand() -> set to 0
  isInRange(distance) -> boolean

Frontend:
  CommandRangeCircle.tsx            -- Konva animated circle (expanding + max boundary)
  BattleMap.tsx                     -- dot-style rendering
  tactical.ts types                 -- TacticalUnit has commandRange field
```

### Critical Observation: What Is MISSING

The current architecture has these gaps that v2.1 must fill:

1. **No command chain hierarchy** -- every officer controls their own unit directly. No concept of "commander issues order to subordinate units."
2. **No sub-fleet assignment** -- 60 units in a fleet are not split among the 10 crew members.
3. **No authorization check in WebSocket controller** -- any officerId in payload can control any unit. No "is this officer the commander of this unit?" check.
4. **Movement AI is primitive** -- `processMovement()` just chases the nearest enemy. No mission-objective-driven behavior.
5. **No command succession** -- flagship destruction replaces isFlagship to the largest-ships unit, but there is no delay, no chain-of-command logic, and no "units lose control" period.
6. **No tactical AI** -- NPC/offline units in battle have no behavior at all. The AI system (UtilityScorer/AiCommandBridge) only handles strategic commands, not tactical actions.
7. **CommandRange exists per-unit but is not used for command authority** -- it resets on ANY order to that unit, but does not gate whether a commander can reach subordinate units.

---

## Recommended Architecture: New Components

### Component Boundary Map

```
NEW COMPONENTS (create from scratch):
  engine/tactical/command/
    |-- CommandChainManager.kt         -- core: who commands whom, authority resolution
    |-- SubFleetAssignment.kt          -- 60 units split among 10 crew members
    |-- CommandSuccessionService.kt    -- flagship death -> delay -> auto-successor
    |-- CommandAuthority.kt            -- data class: officerId, assignedUnitIndices, commandRange
  engine/tactical/ai/
    |-- TacticalAI.kt                  -- entry point: decide actions for NPC/offline units
    |-- TacticalAIBehavior.kt          -- mission-objective-driven behaviors
    |-- ThreatAssessment.kt            -- evaluate threats, decide retreat
    |-- TacticalPersonalityModifier.kt -- personality -> tactical preferences
  dto/
    |-- CommandChainDtos.kt            -- WebSocket DTOs for delegation/reassignment

MODIFIED COMPONENTS (extend existing):
  TacticalBattleEngine.kt             -- inject CommandChainManager into tick loop
  TacticalBattleService.kt            -- add sub-fleet mgmt, authority checks, AI tick
  BattleWebSocketController.kt        -- add delegation endpoints, authority filtering
  BattleTriggerService.kt             -- build command chain on battle init
  TacticalBattleState                  -- add commandChain field
  TacticalUnit                         -- add commanderId, subFleetId fields
  CommandRangeCircle.tsx               -- show per-commander circles (not per-unit)
  tactical.ts                          -- add commanderId, subFleetId to TacticalUnit type
```

---

## Component 1: CommandChainManager

**Purpose:** Resolves "who can command whom" at every tick. This is the heart of v2.1.

```kotlin
// engine/tactical/command/CommandChainManager.kt

/**
 * Manages the command hierarchy within a tactical battle.
 *
 * Hierarchy:
 *   FleetCommander (사령관, CrewSlotRole.COMMANDER)
 *     +-- ViceCommander (부사령관) -> assigned unit indices
 *     +-- ChiefOfStaff (참모장) -> assigned unit indices
 *     +-- StaffOfficer1..6 -> assigned unit indices
 *     +-- Adjutant (부관) -> no units (aide only)
 *     +-- Unassigned units -> commander's direct control
 *
 * Each sub-commander has their OWN CommandRange circle.
 * Commander can only issue orders to units within their circle.
 * Units outside all circles continue last command or fall to AI.
 */
data class CommandAuthority(
    val officerId: Long,
    val crewSlotRole: CrewSlotRole,
    val assignedUnitIndices: Set<Int>,   // slot indices within fleet (0..59)
    val commandRange: CommandRange,       // this officer's own command range circle
    val isAlive: Boolean = true,
    val isOnline: Boolean = false,        // player online status
    val rankPriority: Int = 0,           // for succession: rank -> eval -> merit
)

class CommandChainManager {

    /**
     * Build initial command chain from Fleet crew roster.
     * Called once at battle start by BattleTriggerService.
     */
    fun buildChain(
        fleetId: Long,
        crewMembers: List<CrewMember>,     // officer + slot role
        totalUnitCount: Int,
        onlineOfficerIds: Set<Long>,
    ): CommandChain

    /**
     * Check: can this officer issue a command to this unit?
     * Returns true if:
     *   1. Officer is the assigned commander of this unit's sub-fleet
     *   2. The unit is within the officer's current command range circle
     */
    fun canCommand(chain: CommandChain, officerId: Long, unitIndex: Int, unitPosition: Position): Boolean

    /**
     * Process flagship destruction.
     * Returns updated chain with succession delay applied.
     * - destroyed officer's units become uncontrolled for SUCCESSION_DELAY ticks
     * - after delay, next-in-line officer inherits units
     */
    fun processDestruction(
        chain: CommandChain, destroyedOfficerId: Long, currentTick: Int,
    ): CommandChain

    /**
     * Reassign units between sub-commanders.
     * Preconditions: target unit must be outside all other command circles + stationary.
     */
    fun reassignUnits(
        chain: CommandChain, fromOfficerId: Long, toOfficerId: Long, unitIndices: Set<Int>,
        unitPositions: Map<Int, Position>,
    ): CommandChain

    /**
     * Per-tick update: expand all command range circles, check succession timers.
     */
    fun tick(chain: CommandChain, currentTick: Int): CommandChain
}

data class CommandChain(
    val fleetId: Long,
    val side: BattleSide,
    val authorities: Map<Long, CommandAuthority>,  // officerId -> authority
    val successionQueue: List<Long>,               // ordered by rank/eval/merit
    val pendingSuccessions: List<PendingSuccession>,
)

data class PendingSuccession(
    val destroyedOfficerId: Long,
    val successorOfficerId: Long,
    val activateAtTick: Int,             // currentTick + SUCCESSION_DELAY (30 ticks)
    val orphanedUnitIndices: Set<Int>,   // units without commander during delay
)

companion object {
    const val SUCCESSION_DELAY_TICKS = 30  // 30 seconds real-time
}
```

### Integration Point: TacticalBattleState

```kotlin
// Add to TacticalBattleState:
data class TacticalBattleState(
    // ... existing fields ...

    /** Command chains per fleet (one per side, or one per fleet if multi-fleet) */
    val commandChains: MutableMap<Long, CommandChain> = mutableMapOf(),
)

// Add to TacticalUnit:
data class TacticalUnit(
    // ... existing fields ...

    /** The officerId of this unit's current commander (not necessarily the same as officerId) */
    var commanderId: Long = 0,

    /** Sub-fleet slot index within the fleet (0..59) */
    var unitSlotIndex: Int = 0,

    /** Whether this unit is currently uncontrolled (succession gap) */
    var isUncontrolled: Boolean = false,
)
```

---

## Component 2: TacticalBattleEngine Tick Loop Modification

**The existing tick loop must be extended, not replaced.** Insert command chain processing at step 1.5 (after command range update, before movement).

```
CURRENT TICK LOOP:                    NEW TICK LOOP:
1. updateCommandRange(unit)           1. updateCommandRange(unit)
                                      1.5 commandChainManager.tick(chain)
                                          -- expand all commander circles
                                          -- process pending successions
                                          -- update unit.isUncontrolled flags
2. processMovement(unit)              2. processMovement(unit)
                                          -- IF unit.isUncontrolled: tacticalAI.decideMovement()
                                          -- IF unit has commander in range: follow orders
                                          -- IF unit out of range: continue last command
3. detectionService.update()          3. detectionService.update()
4. processCombat(unit)                4. processCombat(unit)
                                      4.5 tacticalAI.processNpcActions(state)
                                          -- NPC/offline units: energy/stance/target decisions
5. fortress gun                       5. fortress gun
6. remove destroyed units             6. remove destroyed units
   -- flagship replacement                -- commandChainManager.processDestruction()
7. ground battle                      7. ground battle
8. morale                             8. morale
```

**Key architectural decision:** The TacticalBattleEngine remains stateless. CommandChainManager is injected as a dependency, same pattern as MissileWeaponSystem, DetectionService, etc.

```kotlin
class TacticalBattleEngine(
    private val missileSystem: MissileWeaponSystem = MissileWeaponSystem(),
    private val fortressGunSystem: FortressGunSystem = FortressGunSystem(),
    private val detectionService: DetectionService = DetectionService(),
    private val shipStatRegistry: ShipStatRegistry? = null,
    // NEW:
    private val commandChainManager: CommandChainManager = CommandChainManager(),
    private val tacticalAI: TacticalAI = TacticalAI(),
)
```

---

## Component 3: BattleWebSocketController Authority Gate

**Current problem:** Any officerId in a WebSocket payload can control any unit. No permission check.

**Solution:** Add an authority check layer between the WebSocket controller and TacticalBattleService.

```kotlin
// In TacticalBattleService, modify ALL command methods:

fun setEnergyAllocation(battleId: Long, officerId: Long, allocation: EnergyAllocation) {
    val state = activeBattles[battleId] ?: throw ...

    // NEW: find which units this officer can control
    val controllableUnits = findControllableUnits(state, officerId)
    val unit = controllableUnits.find { it.officerId == officerId }
        ?: throw IllegalArgumentException("Officer $officerId has no authority in battle $battleId")

    // If this officer is a sub-commander, apply to ALL assigned units:
    val targetUnits = if (isSubCommander(state, officerId)) {
        getAssignedUnits(state, officerId)
    } else {
        listOf(unit)
    }

    for (u in targetUnits) {
        u.energy = allocation
        u.commandRange = 0.0
        u.ticksSinceLastOrder = 0
    }
}
```

### New WebSocket Endpoints

```kotlin
// BattleWebSocketController -- NEW endpoints:

/** 유닛 재배정 (사령관 -> 참모에게 유닛 위임) */
@MessageMapping("/battle/{sessionId}/{battleId}/delegate")
fun delegateUnits(payload: DelegateUnitsRequest)

/** 전군 명령 (사령관이 모든 유닛에 명령) */
@MessageMapping("/battle/{sessionId}/{battleId}/fleet-order")
fun fleetOrder(payload: FleetOrderRequest)

// DTOs:
data class DelegateUnitsRequest(
    val commanderOfficerId: Long,       // must be fleet commander
    val targetOfficerId: Long,           // sub-commander to receive units
    val unitSlotIndices: List<Int>,      // which units to delegate
)

data class FleetOrderRequest(
    val commanderOfficerId: Long,
    val orderType: String,               // ADVANCE/RETREAT/HOLD/ATTACK_TARGET
    val targetX: Double = 0.0,
    val targetY: Double = 0.0,
    val targetFleetId: Long? = null,
)
```

---

## Component 4: Tactical AI

**Architecture:** The tactical AI is a NEW system, separate from the existing strategic AI (UtilityScorer/AiCommandBridge). It operates entirely within the tactical battle tick loop and never touches the database.

```kotlin
// engine/tactical/ai/TacticalAI.kt

class TacticalAI {

    /**
     * Called once per tick for all NPC/offline units in the battle.
     * Pure function: reads state, returns list of actions to apply.
     */
    fun decideActions(
        state: TacticalBattleState,
        npcUnitIds: Set<Long>,          // fleetIds of NPC/offline units
        missionObjective: MissionObjective,
    ): List<TacticalAction>
}

enum class MissionObjective {
    CAPTURE,        // 점령 -> move toward planet, aggressive
    DEFEND,         // 방어 -> hold position, defensive
    SWEEP,          // 소탕 -> pursue enemies, aggressive
    RETREAT_ORDER,  // 전면 퇴각
    AUTONOMOUS,     // 지휘체계 붕괴 -> survival mode
}

sealed class TacticalAction {
    data class SetEnergy(val fleetId: Long, val allocation: EnergyAllocation) : TacticalAction()
    data class SetStance(val fleetId: Long, val stance: UnitStance) : TacticalAction()
    data class SetTarget(val fleetId: Long, val targetFleetId: Long) : TacticalAction()
    data class Move(val fleetId: Long, val dirX: Double, val dirY: Double) : TacticalAction()
    data class Retreat(val fleetId: Long) : TacticalAction()
    data class ChangeFormation(val fleetId: Long, val formation: Formation) : TacticalAction()
}
```

### Personality -> Tactical Behavior Mapping

Reuses existing `PersonalityTrait` enum. No new enum needed.

```
AGGRESSIVE:
  - Energy bias: beam=30, gun=25, shield=10, engine=20, warp=5, sensor=10
  - Formation preference: WEDGE
  - Retreat threshold: HP < 10%, morale < 15%
  - Target selection: weakest enemy (finish kills)
  - Stance: COMBAT early

DEFENSIVE:
  - Energy bias: beam=15, gun=15, shield=30, engine=15, warp=15, sensor=10
  - Formation preference: THREE_COLUMN
  - Retreat threshold: HP < 30%, morale < 40%
  - Target selection: closest enemy (minimize exposure)
  - Stance: STATIONED when possible

BALANCED:
  - Default energy allocation
  - Formation: MIXED
  - Standard thresholds

CAUTIOUS:
  - Energy bias: beam=15, gun=10, shield=25, engine=15, warp=15, sensor=20
  - Formation: BY_CLASS
  - Retreat threshold: HP < 25%, morale < 35%
  - Target selection: detected enemies only (high sensor weight)

POLITICAL:
  - Treated as BALANCED in tactical (no political advantage in battle)
```

### Threat Assessment

```kotlin
// engine/tactical/ai/ThreatAssessment.kt

object ThreatAssessment {

    /**
     * Should this unit retreat?
     * Based on HP ratio, morale, nearby enemy count, personality.
     */
    fun shouldRetreat(unit: TacticalUnit, state: TacticalBattleState, trait: PersonalityTrait): Boolean

    /**
     * Select best target from detected enemies.
     * Personality affects preference: weakest vs closest vs highest-value.
     */
    fun selectTarget(
        unit: TacticalUnit,
        detectedEnemies: List<TacticalUnit>,
        trait: PersonalityTrait,
    ): TacticalUnit?

    /**
     * Evaluate energy allocation based on situation.
     * Close range -> more beam/gun. Long range -> more engine/sensor.
     * Low HP -> more shield/warp.
     */
    fun recommendEnergy(
        unit: TacticalUnit,
        nearestEnemyDist: Double,
        trait: PersonalityTrait,
    ): EnergyAllocation
}
```

---

## Component 5: Command Succession

### Data Flow on Flagship Destruction

```
CURRENT (line 248 TacticalBattleEngine.kt):
  flagship destroyed -> immediately replace with largest-ships unit
  NO delay, NO command chain impact

NEW:
  flagship destroyed
    -> CommandChainManager.processDestruction(chain, officerId, tick)
       -> orphanedUnitIndices = destroyed officer's assigned units
       -> PendingSuccession created (activateAtTick = tick + 30)
       -> orphaned units marked isUncontrolled = true
    -> During 30-tick gap:
       -> orphaned units run TacticalAI.AUTONOMOUS behavior
       -> no player can control them
    -> After 30 ticks:
       -> successor officer inherits orphaned units
       -> successor's command range resets to 0 (must expand again)
       -> units become controllable again
```

### Succession Priority Resolution

```kotlin
fun resolveSuccessor(chain: CommandChain, destroyedOfficerId: Long): Long? {
    // Priority: online > rank > evaluation point > merit
    return chain.successionQueue
        .filter { it != destroyedOfficerId }
        .filter { chain.authorities[it]?.isAlive == true }
        .sortedWith(compareByDescending<Long> { chain.authorities[it]?.isOnline == true }
            .thenByDescending { chain.authorities[it]?.rankPriority ?: 0 })
        .firstOrNull()
}
```

### All-commanders-destroyed Edge Case

```
If ALL officers in succession queue are destroyed:
  -> CommandChain enters COLLAPSED state
  -> ALL units in this fleet run TacticalAI.AUTONOMOUS
  -> No player can issue orders to this fleet
  -> Units act independently based on personality
  -> This is the "지휘체계 붕괴" scenario from gin7
```

---

## Component 6: Mission Objective Connection (Strategic -> Tactical)

**How 작전계획 connects to tactical AI:**

The strategic game's operation plan (점령/방어/소탕) is already decided before the battle starts. The connection point is `BattleTriggerService.buildInitialState()`.

```kotlin
// Modify BattleTriggerService.buildInitialState():

fun buildInitialState(battle: TacticalBattle): TacticalBattleState {
    // ... existing unit construction ...

    // NEW: resolve mission objective from strategic operation plan
    val missionObjective = resolveMissionObjective(battle)

    return TacticalBattleState(
        // ... existing fields ...
        missionObjective = missionObjective,         // NEW
        commandChains = buildCommandChains(battle),   // NEW
    )
}

private fun resolveMissionObjective(battle: TacticalBattle): MissionObjective {
    // Check if there's an active operation plan targeting this star system
    val operationPlan = operationPlanRepository.findActiveByStarSystem(
        battle.sessionId, battle.starSystemId
    )
    return when (operationPlan?.objective) {
        "점령" -> MissionObjective.CAPTURE
        "방어" -> MissionObjective.DEFEND
        "소탕" -> MissionObjective.SWEEP
        else -> MissionObjective.CAPTURE  // default: assume offensive
    }
}
```

**Note:** OperationPlan entity does not exist yet. It needs a DB migration (V45+) and a new entity. This is a dependency that must be built in Phase 1 before the tactical AI can use mission objectives.

---

## Data Flow: Complete Tick with Command Chain

```
TickEngine.processTick(world)
  |
  +-- tacticalBattleService.processSessionBattles(sessionId)
       |
       +-- for each activeBattle:
            |
            +-- 1. engine.processTick(state)
            |    |
            |    +-- 1.0 updateCommandRange(unit)  -- per-unit (existing)
            |    +-- 1.5 commandChainManager.tick(chain)
            |    |        -- expand all commander circles
            |    |        -- process pending successions (30-tick delays)
            |    |        -- update isUncontrolled flags
            |    +-- 2.0 processMovement(unit)
            |    |        -- controlled units: follow last order
            |    |        -- uncontrolled units: tacticalAI basic movement
            |    +-- 2.5 detectionService.update()
            |    +-- 3.0 processCombat(unit)
            |    +-- 4.0 tacticalAI.processNpcActions(state)
            |    |        -- all NPC/offline: energy/stance/target/retreat
            |    +-- 5.0 fortressGun, destroyUnits, groundBattle, morale
            |    |
            |    +-- 5.5 ON DESTRUCTION:
            |             commandChainManager.processDestruction() instead of simple replacement
            |
            +-- 2. broadcastBattleState()
                 -- includes commandChains in broadcast for UI
```

---

## Frontend Changes

### TacticalUnit Type Extension

```typescript
// tactical.ts additions:
export interface TacticalUnit {
    // ... existing fields ...
    commanderId?: number;        // officerId of commanding officer
    unitSlotIndex?: number;      // 0..59 within fleet
    isUncontrolled?: boolean;    // succession gap
    subFleetLabel?: string;      // "제2분함대" etc for display
}

export interface CommandChainInfo {
    fleetId: number;
    authorities: CommandAuthorityInfo[];
    isCollapsed: boolean;
}

export interface CommandAuthorityInfo {
    officerId: number;
    officerName: string;
    crewSlotRole: string;
    assignedUnitCount: number;
    commandRange: number;
    commandRangeMax: number;
    isAlive: boolean;
    isOnline: boolean;
}
```

### CommandRangeCircle.tsx Changes

Current: renders one circle per unit.
New: render one circle per **commander** (officer who has assigned units).

```
Per commander in CommandChain:
  - Animated expanding circle (existing component, reused)
  - Centered on the commander's flagship unit position
  - Only show for the player's own fleet (or spectator mode)
  - Color: brighter for own, dimmer for enemy (if detected)
  - Units outside circle: render with dimmed opacity to indicate "out of command"
```

### New UI: Sub-Fleet Assignment Panel

```
+----------------------------------+
| 함대 지휘구조                      |
+----------------------------------+
| 사령관: 라인하르트 (직할 40유닛)     |
|   부사령관: 키르히아이스 (10유닛)    |
|   참모장: 오베르슈타인 (0유닛)      |
|   참모1: 비텐펠트 (5유닛)          |
|   참모2: 미텐마이어 (5유닛)         |
+----------------------------------+
| [유닛 재배정] [전군 명령]           |
+----------------------------------+
```

---

## Scalability Consideration

### Performance Impact of Command Chain

| Concern | Current (no chain) | With chain | Mitigation |
|---------|-------------------|------------|------------|
| Per-tick processing | O(n) units | O(n) units + O(k) commanders | k is at most 10 per fleet -- negligible |
| Memory per battle | ~1KB per unit | +~200 bytes per unit (commanderId, etc) | Negligible for 120 units max |
| WebSocket payload | units[] only | units[] + commandChains[] | commandChains is small (~1KB), send every 5 ticks not every tick |
| AI decisions | None in battle | O(m) NPC units per tick | m decisions are simple conditionals, no DB access |
| Succession events | Instant | 30-tick delay state | One PendingSuccession object per death |

**Verdict:** No performance risk. The command chain adds O(k) work where k<=10 commanders, and the tactical AI is pure in-memory computation with no I/O.

### When to Consider Separation

If a single battle exceeds 100 units per side (200 total), consider moving the tactical battle tick to a separate `@Scheduled(fixedDelay=100)` loop. This is NOT needed for v2.1 -- the current synchronous approach within TickEngine handles 60+60 units comfortably.

---

## Anti-Patterns to Avoid

### Anti-Pattern 1: Command Chain in Database
**What:** Persisting CommandChain to PostgreSQL or Redis every tick.
**Why bad:** Command chain changes every tick (range expansion). DB writes per tick destroy performance.
**Instead:** Keep CommandChain in-memory within TacticalBattleState. Only persist the final state when battle ends.

### Anti-Pattern 2: Tactical AI Accessing Repositories
**What:** TacticalAI calling OfficerRepository or FleetRepository during battle ticks.
**Why bad:** DB queries inside the 1-second tick loop block the entire TickEngine.
**Instead:** TacticalAI is a pure function that operates on TacticalBattleState only. All officer/fleet data is loaded once at battle start by BattleTriggerService.

### Anti-Pattern 3: Mixing Strategic and Tactical AI
**What:** Having AiCommandBridge (strategic) call tactical actions, or TacticalAI issue strategic commands.
**Why bad:** Strategic commands go through CommandExecutor with CP/cooldown/PositionCard validation. Tactical actions are immediate in-memory mutations. Mixing them creates validation confusion.
**Instead:** Two completely separate AI systems:
  - Strategic: AiCommandBridge -> CommandExecutor -> DB (every 100 ticks)
  - Tactical: TacticalAI -> TacticalBattleState mutations (every tick, in-memory only)

### Anti-Pattern 4: One CommandRange per Unit
**What:** Keeping the current pattern where every TacticalUnit has its own commandRange.
**Why bad:** In gin7, command range belongs to the COMMANDER, not to individual units. A commander's circle covers all their assigned units.
**Instead:** TacticalUnit keeps commandRange for backward compat (own unit control), but the AUTHORITY check uses the commander's CommandAuthority.commandRange. The per-unit commandRange becomes "time since last order" only for the unit's own direct pilot (solo ships).

### Anti-Pattern 5: Broadcasting Full CommandChain Every Tick
**What:** Including the full CommandChain object in every 1-second tick broadcast.
**Why bad:** CommandChain is mostly static between orders. Wastes bandwidth.
**Instead:** Broadcast CommandChain only on changes (delegation, succession, destruction). Include only commandRange values in tick broadcast (small delta updates).

---

## Suggested Build Order

Dependencies between components dictate this order:

```
Phase 1: Command Chain Data Model (no behavior yet)
  - CommandAuthority, CommandChain, PendingSuccession data classes
  - SubFleetAssignment: logic for splitting 60 units among crew
  - OperationPlan entity + V45 migration (for mission objective)
  - Modify BattleTriggerService.buildInitialState() to build chains
  - Modify TacticalBattleState + TacticalUnit with new fields
  WHY FIRST: Everything else depends on these data structures.

Phase 2: CommandChainManager (authority + succession)
  - canCommand() authority check
  - tick() for circle expansion
  - processDestruction() with 30-tick delay
  - reassignUnits() with precondition checks
  - Modify TacticalBattleEngine to call commandChainManager.tick()
  WHY SECOND: WebSocket auth and AI both depend on command chain logic.

Phase 3: WebSocket Authority Gate
  - Modify TacticalBattleService command methods to check canCommand()
  - Add /delegate and /fleet-order endpoints
  - Add error responses for unauthorized commands
  WHY THIRD: Must validate command chain before AI can work alongside players.

Phase 4: Tactical AI
  - TacticalAI core: mission-objective-driven behavior
  - ThreatAssessment: retreat/target/energy decisions
  - TacticalPersonalityModifier: personality -> tactical preferences
  - Wire into TacticalBattleEngine tick loop (step 4.5)
  WHY FOURTH: Requires command chain (Phase 2) to know which units are NPC/uncontrolled.

Phase 5: Strategic AI Enhancement
  - FactionAI: auto-create operation plans when at war
  - AiCommandBridge: connect operation plan to BattleTriggerService
  WHY FIFTH: Builds on tactical AI. Operation plans feed mission objectives.

Phase 6: Frontend Integration
  - CommandRangeCircle.tsx: per-commander circles
  - Sub-fleet assignment panel
  - Authority-aware command controls (disable buttons for units you cannot command)
  - Succession visual feedback (flash, "지휘 승계 중" indicator)
  WHY LAST: All backend must be stable before UI work.
```

---

## Sources

- Direct code analysis of all files listed at top
- gin7 manual references: p.37-38 (작전계획), p.46 (기함/지휘권/커맨드레인지서클)
- v2.1 milestone scope from project memory
- Existing v2.0 ARCHITECTURE.md research (previous milestone)
