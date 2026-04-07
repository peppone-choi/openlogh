# Technology Stack: v2.1 Command Chain + Tactical AI

**Project:** Open LOGH v2.1 -- Tactical Command Chain & AI
**Researched:** 2026-04-07
**Overall confidence:** HIGH

## Executive Summary

The v2.1 milestone (command chain hierarchy, command range circle, succession, tactical AI) requires **zero new library dependencies**. The existing Spring Boot 3 + Kotlin stack already provides everything needed. The new features are pure domain logic -- spatial algorithms, state machines, and decision trees -- all implementable with Kotlin stdlib and `kotlin.math`.

This is a "build, don't buy" situation. The tactical battle map is a bounded 1000x600 grid with at most ~120 units. No spatial indexing library, behavior tree framework, or state machine library is justified at this scale.

---

## New Libraries: NONE

No new dependencies should be added to `build.gradle.kts` or `package.json` for v2.1. Here is why for each capability the milestone needs:

| Capability Needed | Why No Library | What to Use Instead |
|---|---|---|
| Spatial range queries (command circle) | Grid is 1000x600, max ~120 units. O(n) scan is <1us per tick. | `kotlin.math.sqrt` + distance check (already in `TacticalBattleEngine`) |
| State machine (succession) | Only 3-4 states per command chain. Sealed class is clearer than a framework. | Kotlin `sealed class` / `enum` + `when` exhaustive matching |
| Behavior trees (tactical AI) | Tactical AI has ~10 decision points. A BT library adds indirection for no gain. | Utility scoring (already have `UtilityScorer`) + simple priority functions |
| Hierarchical command tree | Tree depth is at most 3 (commander -> sub-commander -> unit). | Kotlin data classes with parent references |
| Timer/scheduler (succession delay) | Already have 1-second tick engine. Count ticks, not wall-clock time. | `ticksSinceSuccession: Int` counter in state |
| Command circle rendering | R3F `<circleGeometry>` already available in existing Three.js stack | `<mesh>` + `<circleGeometry>` + transparent material |
| WebSocket command events | Existing STOMP channel handles battle broadcast | Add new event types to existing `BattleTickEvent` |

---

## Why NOT These Libraries

### Spatial Libraries (JTS, KD-Tree, QuadTree)
**Verdict: DO NOT ADD**
- JTS Topology Suite or custom spatial indexes are for GIS workloads with 10K+ points
- The tactical grid has max ~120 units (60 per side maximum per gin7 fleet composition)
- Current `distance()` function in `TacticalBattleEngine` (line 521) already does O(n) range checks per tick
- At 120 units, checking "which units are within command circle radius R" is 120 distance calculations = trivially fast
- Adding JTS would increase JAR size by ~2MB for zero measurable performance gain

### State Machine Libraries (Spring Statemachine, Squirrel, EasyFlow)
**Verdict: DO NOT ADD**
- Spring Statemachine is heavyweight (XML config or builder DSL, persistence adapter, event bus)
- Command succession has exactly 4 states: `STABLE -> VACANT -> TRANSITIONING -> SUCCEEDED` (or `COLLAPSED`)
- Sub-commander control has 2 states: `ASSIGNED -> UNASSIGNED`
- Kotlin `sealed class` with `when` provides compile-time exhaustiveness checking, which is superior to runtime state machine validation
- The existing codebase already uses this pattern extensively (`UnitStance`, `BattlePhase`, `CommandState`)

### Behavior Tree Libraries (GdxAI, BTML, behaviortree4j)
**Verdict: DO NOT ADD**
- GdxAI is tied to libGDX game framework -- wrong ecosystem for Spring Boot server
- Behavior trees shine when AI has 50+ decision nodes with complex sequencing and parallel branches
- Tactical AI here has ~10 decisions: stance choice, energy allocation, formation pick, target selection, retreat check, movement direction, focus/spread fire
- The existing `UtilityScorer` + `PersonalityWeights` pattern is a better fit: score each option, pick highest
- Utility AI is already proven in the codebase (`AiCommandBridge` uses it for strategic commands)

### Event Sourcing Libraries (Axon, EventStore)
**Verdict: DO NOT ADD**
- Command chain events (succession, delegation) are local to a single battle instance
- The existing `BattleTickEvent` list in `TacticalBattleState` already handles event broadcasting
- Adding Axon for 3-4 new event types would be massive overengineering

### Graph Libraries (JGraphT, Neo4j)
**Verdict: DO NOT ADD**
- Command hierarchy is a 3-level tree: fleet commander -> sub-commanders (max 9) -> units (max 60)
- A `Map<Long, List<Int>>` (officerId -> assigned unit slot indices) is sufficient
- No graph traversal, shortest path, or cycle detection is needed

---

## Existing Stack Components to Leverage

### Backend (Already in build.gradle.kts -- use as-is)

| Component | Version | Use for v2.1 |
|---|---|---|
| Kotlin stdlib + kotlin.math | 2.1.0 | All spatial math: `sqrt`, `pow`, distance, circle containment |
| kotlinx-coroutines-core | (Spring managed) | Async tick processing in turn engine |
| Spring WebSocket + STOMP | (Spring Boot 3.4.2) | Broadcasting command chain updates via existing `/topic/world/{sessionId}/battle` |
| Spring Data JPA + Hibernate 6 | (Spring Boot 3.4.2) | Persisting updated `TacticalBattle.battleState` JSONB |
| Jackson Kotlin Module | (Spring Boot 3.4.2) | Serializing `CommandHierarchy` in JSONB |

### Frontend (Already in package.json -- use as-is)

| Component | Version | Use for v2.1 |
|---|---|---|
| React Three Fiber + Drei | 9.5.0 / 10.7.7 | `TacticalMapR3F.tsx` -- render command range circles as transparent circle meshes |
| @stomp/stompjs | 7.3.0 | Receive command chain updates via existing battle WebSocket channel |
| Zustand | 5.0.11 | `tacticalStore.ts` -- extend with command hierarchy state |
| TypeScript | 5.x | Type-safe command chain interfaces in `tactical.ts` |

---

## Existing Code to Extend (NOT Replace)

These files already exist and contain the foundation for v2.1. The work is extension, not greenfield.

| Existing Code | Location | What It Does Now | What v2.1 Adds |
|---|---|---|---|
| `CommandRange` | `model/CommandRange.kt` | Per-unit circle with `tick()`, `resetOnCommand()`, `isInRange()` | Use as-is for sub-commander circles. The model is already correct. |
| `TacticalUnit` | `engine/tactical/TacticalBattleEngine.kt` | Has `commandRange`, `commandRangeMax`, `ticksSinceLastOrder` | Add `commanderId: Long?`, `subCommanderOf: Long?`, `isAiControlled: Boolean` |
| `TacticalBattleState` | `engine/tactical/TacticalBattleEngine.kt` | Holds all units + tick events + detection matrix | Add `commandHierarchy: CommandHierarchy` data structure |
| `TacticalBattleEngine.updateCommandRange()` | Line 318-324 | Grows circle per tick based on command stat | Extend to check hierarchy: only propagate commands to units within commander's circle |
| `PersonalityTrait` | `engine/ai/PersonalityTrait.kt` | 5 types (AGGRESSIVE/DEFENSIVE/BALANCED/POLITICAL/CAUTIOUS) with stat weights | Reuse directly for tactical AI personality-based decisions |
| `PersonalityWeights` | `engine/ai/PersonalityTrait.kt` | Stat multipliers per trait (attack, defense, mobility, etc.) | Use to weight tactical action scores |
| `UtilityScorer` | `engine/ai/UtilityScorer.kt` | Scores CommandGroups by officer stats + personality for strategic AI | Create parallel `TacticalUtilityScorer` for tactical actions using same pattern |
| `AiCommandBridge` | `engine/ai/AiCommandBridge.kt` | Executes AI strategic commands through CommandExecutor | Create parallel `TacticalAiBridge` for tactical commands |
| `BattleTickEvent` | `engine/tactical/TacticalBattleEngine.kt` | Event types: damage, destroy, retreat, fortress_fire, flagship_destroyed, flagship_transfer | Add: `command_issued`, `succession_start`, `succession_complete`, `delegation`, `chain_collapsed` |
| `TacticalBattleService` | `service/TacticalBattleService.kt` | Battle lifecycle, player commands, in-memory state | Add command chain setup on `startBattle()`, succession logic on flagship destruction |
| `TacticalUnit` frontend | `types/tactical.ts` | Has `commandRange`, `isFlagship` | Add `commanderId`, `isAiControlled`, `subCommanderName` |
| `BattleCommand` frontend | `types/tactical.ts` | Command types: energy, formation, retreat | Add: `delegate`, `target`, `stance`, `reassign_units` |

---

## New Code Architecture (Pure Kotlin, Zero Dependencies)

### 1. Command Hierarchy Data Model

```kotlin
// New file: model/CommandHierarchy.kt
data class CommandHierarchy(
    val commanderId: Long,                              // Fleet commander officerId
    val subCommanders: Map<Long, SubCommanderAssignment>,  // officerId -> assignment
    val unitAssignments: Map<Int, Long>,                // slotIndex -> controlling officerId
    val successionOrder: List<Long>,                    // officerId list by priority (rank -> eval -> merit)
    val successionState: SuccessionState = SuccessionState.Stable,
)

sealed class SuccessionState {
    object Stable : SuccessionState()
    data class Vacant(val vacantSinceTick: Int, val ticksRemaining: Int = 30) : SuccessionState()
    data class Transitioning(val newCommanderId: Long, val ticksLeft: Int) : SuccessionState()
    object Collapsed : SuccessionState()  // all officers destroyed
}

data class SubCommanderAssignment(
    val officerId: Long,
    val officerName: String,
    val assignedUnitSlots: List<Int>,   // which unit slots (0-59) this sub-commander controls
    val commandRange: CommandRange,      // independent circle per sub-commander
    val isOnline: Boolean,              // player is connected
)
```

### 2. Tactical AI Decision Model

```kotlin
// New file: engine/tactical/TacticalAI.kt
// Follows existing UtilityScorer pattern -- score actions, pick highest

enum class TacticalAction {
    ADVANCE, HOLD_POSITION, RETREAT, FOCUS_FIRE, SPREAD_FIRE,
    CHANGE_FORMATION, CHANGE_ENERGY, CHANGE_STANCE,
}

data class TacticalDecision(
    val action: TacticalAction,
    val score: Double,
    val params: Map<String, Any> = emptyMap(),  // e.g., targetFleetId, newFormation
)

object TacticalUtilityScorer {
    fun scoreActions(
        unit: TacticalUnit,
        enemies: List<TacticalUnit>,
        allies: List<TacticalUnit>,
        trait: PersonalityTrait,
        operationObjective: OperationObjective,  // from strategic operation plan
    ): List<TacticalDecision> {
        val weights = PersonalityWeights.forTrait(trait)
        // Score each action using unit stats * personality weights * situation assessment
        // Return sorted by score descending
    }
}
```

### 3. Spatial Algorithm (Already Exists -- Just Wrap)

```kotlin
// Circle containment -- reuses existing distance() pattern from TacticalBattleEngine
fun getUnitsInCommandCircle(
    commander: TacticalUnit,
    allUnits: List<TacticalUnit>,
): List<TacticalUnit> {
    return allUnits.filter { unit ->
        val dx = commander.posX - unit.posX
        val dy = commander.posY - unit.posY
        val dist = sqrt(dx * dx + dy * dy)
        dist <= commander.commandRange
    }
}
// At 60 units per fleet: 60 sqrt operations per tick = negligible
```

### 4. Succession State Machine (Sealed Class)

```kotlin
// In TacticalBattleEngine.processTick() -- extend step 5 (flagship destruction)
fun processSuccession(state: TacticalBattleState): TacticalBattleState {
    val hierarchy = state.commandHierarchy ?: return state
    
    when (val succession = hierarchy.successionState) {
        is SuccessionState.Stable -> { /* no action */ }
        is SuccessionState.Vacant -> {
            if (succession.ticksRemaining <= 0) {
                // Find next in succession order who is still alive
                val next = hierarchy.successionOrder.firstOrNull { id ->
                    state.units.any { it.officerId == id && it.isAlive }
                }
                if (next != null) {
                    // Transition to new commander
                    state.commandHierarchy = hierarchy.copy(
                        successionState = SuccessionState.Transitioning(next, ticksLeft = 5)
                    )
                } else {
                    state.commandHierarchy = hierarchy.copy(
                        successionState = SuccessionState.Collapsed
                    )
                }
            } else {
                state.commandHierarchy = hierarchy.copy(
                    successionState = succession.copy(ticksRemaining = succession.ticksRemaining - 1)
                )
            }
        }
        is SuccessionState.Transitioning -> { /* count down, then set Stable with new commander */ }
        is SuccessionState.Collapsed -> { /* each unit runs independent AI */ }
    }
    return state
}
```

---

## Frontend Changes (No New Libraries)

### Command Range Circle Rendering (R3F)

```typescript
// In TacticalMapR3F.tsx -- add circle mesh per commander unit
{commanderUnits.map(unit => (
  <mesh key={unit.fleetId} position={[unit.posX, unit.posY, -0.1]}>
    <circleGeometry args={[unit.commandRange, 64]} />
    <meshBasicMaterial color={factionColor} transparent opacity={0.15} />
  </mesh>
))}
```

### Extended TypeScript Types

```typescript
// Extend existing types/tactical.ts
interface CommandHierarchy {
  commanderId: number;
  subCommanders: Record<number, SubCommanderInfo>;
  unitAssignments: Record<number, number>;  // slotIndex -> officerId
  successionState: 'STABLE' | 'VACANT' | 'TRANSITIONING' | 'COLLAPSED';
  successionTicksRemaining?: number;
}

// Extend BattleCommand union
type BattleCommandType = 'energy' | 'formation' | 'retreat' | 'delegate' | 'target' | 'stance' | 'reassign';

// New event types broadcast via existing BattleTickBroadcast
type CommandChainEventType = 'command_issued' | 'succession_start' | 'succession_complete' 
  | 'delegation' | 'chain_collapsed' | 'unit_reassigned';
```

---

## What NOT to Add

| Technology | Why Not | Use Instead |
|---|---|---|
| R-tree / QuadTree spatial index | 120 units max. Linear scan faster than index overhead. | Inline `kotlin.math.sqrt` distance check |
| Spring Statemachine 4.x | 4-state succession FSM does not warrant a framework. | Kotlin `sealed class SuccessionState` |
| GdxAI / behavior tree lib | Wrong ecosystem (libGDX). Over-abstraction for 10 decisions. | Extend `UtilityScorer` pattern |
| Axon / event sourcing | Battle events are ephemeral, not audit-logged. | Existing `BattleTickEvent` list |
| Redis Pub/Sub for commands | Commands are intra-battle, intra-JVM. | Direct method calls + STOMP broadcast |
| Additional WebSocket channels | Reuse `/topic/world/{sessionId}/battle`. | Add event types to existing channel |
| Neo4j / JGraphT | 3-level tree. `Map<Long, List<Int>>` suffices. | Kotlin data class hierarchy |
| AI/ML libraries | Rule-based utility scoring, not machine learning. | `TacticalUtilityScorer` (pure Kotlin) |
| pathfinding library (A*) | No obstacles on tactical grid. Direct movement. | Vector math toward target |
| `immer` / immutable.js | Zustand handles immutable updates natively. | Zustand `set()` with spread |
| Additional animation libs | `useFrame` in R3F handles game-loop animations. | Existing R3F animation loop |

---

## Database Schema Impact

Minimal. The command hierarchy is battle-scoped and lives in the existing JSONB column:

```sql
-- The battleState JSONB in tactical_battle already stores TacticalBattleState.
-- CommandHierarchy serializes as part of that JSONB.
-- No new tables needed for hierarchy itself.

-- For operation plan linkage (connects strategic -> tactical):
ALTER TABLE tactical_battle ADD COLUMN IF NOT EXISTS operation_plan_id BIGINT;
ALTER TABLE tactical_battle ADD COLUMN IF NOT EXISTS operation_objective VARCHAR(32);
```

---

## Performance Analysis

| Operation | Frequency | Cost | Concern? |
|---|---|---|---|
| Circle containment (all units) | Every tick (1/sec) | O(n), n<=120 | NO -- ~120 sqrt calls < 1us |
| Succession priority sort | On flagship destruction | O(n log n), n<=10 | NO -- sorting 10 officers |
| Tactical AI decision per unit | Every tick for AI units | O(m), m<=10 actions | NO -- 10 score calculations |
| Command propagation | On command issue | O(n), n<=60 | NO -- single fleet scan |
| Hierarchy serialization to JSONB | Every tick (state save) | O(1) -- hierarchy is ~2KB | NO -- Jackson is fast |
| WebSocket broadcast | Every tick | Already happening | NO -- adding ~200 bytes per tick |

---

## Confidence Assessment

| Area | Confidence | Reason |
|---|---|---|
| No new backend libraries | HIGH | Verified `TacticalBattleEngine` already has spatial math, tick loop, event broadcast. `CommandRange` model already exists with correct API. |
| No new frontend libraries | HIGH | R3F circle geometry and existing STOMP channel handle all needs. Types extend naturally. |
| Utility AI over behavior trees | HIGH | Existing `UtilityScorer` + `PersonalityWeights` proven in codebase; tactical decisions map directly to same pattern. |
| Sealed class over state machine lib | HIGH | Kotlin exhaustive `when` provides compile-time safety; 4 states is too few for a framework. Consistent with existing codebase patterns. |
| JSONB storage for hierarchy | HIGH | `TacticalBattleState` already stored as JSONB. Adding `commandHierarchy` field is trivial Jackson serialization. |

---

## Sources

- **Primary: Existing codebase analysis** (all HIGH confidence):
  - `TacticalBattleEngine.kt` -- spatial math, tick loop, command range update, flagship destruction handling
  - `CommandRange.kt` -- circle model with `tick()`, `resetOnCommand()`, `isInRange()`, `isInMaxRange()`
  - `UtilityScorer.kt` -- utility AI scoring pattern with CommandGroup stat drivers
  - `PersonalityTrait.kt` -- 5 personality types with `PersonalityWeights` multipliers
  - `AiCommandBridge.kt` -- AI-to-command execution bridge pattern
  - `UnitType.kt` -- 6 unit types with crew slot definitions (FLEET has 10 crew = commander hierarchy)
  - `CrewSlotRole.kt` -- COMMANDER, VICE_COMMANDER, CHIEF_OF_STAFF, STAFF_OFFICER_1-6, ADJUTANT
  - `TacticalBattleService.kt` -- battle lifecycle, in-memory state management
  - `build.gradle.kts` -- current dependency list (no additions needed)
  - `types/tactical.ts` -- frontend tactical types with commandRange field
- **v2.1 milestone scope** (project memory) -- feature requirements
- **gin7 manual references** (p.37-38 operation plans, p.46 command range circle) -- domain rules
