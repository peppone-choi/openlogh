# Phase 8: 엔진 통합 + 커맨드 버퍼 - Research

**Researched:** 2026-04-07
**Domain:** Kotlin game engine refactoring, concurrent command buffering, data model design
**Confidence:** HIGH

## Summary

Phase 8 consolidates two parallel tactical battle engines (TacticalBattleEngine in `engine/tactical/` and TacticalCombatEngine in `engine/war/`) into a single engine, introduces a command buffer pattern to eliminate race conditions between WebSocket command handlers and the tick loop, and adds a CommandHierarchy data model to TacticalBattleState for future phases (9-10).

The codebase analysis reveals a clear picture: TacticalBattleEngine is the "live" engine actually invoked by TacticalBattleService, while TacticalCombatEngine is an independently developed engine with richer type models (CommandRange object, weaponCooldowns map, debuffs map, DetectionCapability per unit) but is not wired into the runtime. The merge is primarily about importing TacticalCombatEngine's additional fields into TacticalBattleEngine's TacticalUnit, then deleting war/ package files. The command buffer is a straightforward ConcurrentLinkedQueue pattern where WebSocket handlers enqueue sealed-class commands instead of directly mutating state. CommandHierarchy is a data-model-only addition for Phase 8.

**Primary recommendation:** Merge TacticalCombatEngine fields into TacticalBattleEngine.TacticalUnit, introduce TacticalCommand sealed class with ConcurrentLinkedQueue in TacticalBattleState, refactor BattleWebSocketController to enqueue commands, add CommandHierarchy data class, then delete war/ package and duplicate controllers.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** TacticalBattleEngine(engine/tactical/) is the base for unification. TacticalCombatEngine(engine/war/) additional fields (weaponCooldowns, debuffs, DetectionCapability, CommandRange object) are merged into TacticalBattleEngine.TacticalUnit.
- **D-02:** war/ package services (DetectionEngine, PlanetCaptureProcessor, BattleTrigger) are merged into tactical/ package. tactical/ already has DetectionService, BattleTriggerService, PlanetConquestService -- merge functionality then delete war/.
- **D-03:** All WebSocket tactical commands (energy/stance/retreat/attack-target/formation etc.) are buffered in ConcurrentLinkedQueue. Drained at tick start, batch-applied, then tick processes. No immediate application.
- **D-04:** ConcurrentLinkedQueue lives as commandBuffer field inside TacticalBattleState. Per-battle isolation + lifecycle sync.
- **D-05:** CommandHierarchy pre-models Phase 9-10 fields: fleetCommander, subCommanders(Map<Long, SubFleet>), successionQueue, crcRadius(Map<Long, Double>), commJammed flag. Phase 8 = data model only, no logic.
- **D-06:** buildInitialState() in BattleTriggerService auto-generates CommandHierarchy from Fleet entities. Commander = Fleet's officerId, units = Fleet's ShipUnits, succession queue initialized by rank order.
- **D-07:** After merge, engine/war/ package is fully deleted: TacticalCombatEngine.kt, DetectionEngine.kt, PlanetCaptureProcessor.kt, BattleTrigger.kt.
- **D-08:** Duplicate controllers deleted: BattleRestController, TacticalBattleController, TacticalBattleRestController. Only BattleWebSocketController + TacticalBattleService remain.

### Claude's Discretion
- TacticalUnit field name conflict resolution (e.g., commandRange Double vs CommandRange object)
- war/ services to tactical/ services concrete merge strategy
- TacticalCommand sealed class design
- Test strategy and migration order

### Deferred Ideas (OUT OF SCOPE)
None -- discussion stayed within phase scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| ENGINE-01 | Dual tactical engines unified into single engine | Field diff analysis complete; TacticalBattleEngine is the live engine, TacticalCombatEngine fields to merge: supplies, weaponCooldowns, debuffs, detectionCapability, CommandRange object, ShipSubtype enum, stanceChangeTicksRemaining |
| ENGINE-02 | Command buffer pattern for tick-WebSocket concurrency | ConcurrentLinkedQueue pattern researched; BattleWebSocketController has 6 @MessageMapping endpoints that currently call TacticalBattleService methods directly -- all must enqueue instead |
| ENGINE-03 | CommandHierarchy data model in TacticalBattleState | Fleet entity has leaderOfficerId, factionId, maxUnits, maxCrew; Officer entity has rank/stats for succession ordering; buildInitialState() is the insertion point |
</phase_requirements>

## Project Constraints (from CLAUDE.md)

- Backend: Spring Boot 3 (Kotlin), JUnit 5 for testing
- Architecture: gateway-app + game-app JVM separation
- Battle state is in-memory (ConcurrentHashMap<Long, TacticalBattleState>), DB stores metadata only
- WebSocket: STOMP over SockJS
- officerId is in payload (not JWT principal)
- GSD workflow enforcement required

## Architecture Patterns

### Current Code Path (before Phase 8)

```
BattleWebSocketController
  -> @MessageMapping handlers
    -> TacticalBattleService.setEnergyAllocation() etc. (DIRECT state mutation)
      -> activeBattles[battleId].units.find { ... }.energy = allocation

TickEngine.processTick()
  -> TacticalBattleService.processSessionBattles(sessionId)
    -> processBattleTick(battleId)
      -> TacticalBattleEngine.processTick(state)  // READS same state concurrently
```

**Race condition:** WebSocket handlers mutate TacticalUnit fields while tick loop reads them. No synchronization.

### Target Code Path (after Phase 8)

```
BattleWebSocketController
  -> @MessageMapping handlers
    -> state.commandBuffer.offer(TacticalCommand.SetEnergy(...))  // ENQUEUE only

TacticalBattleEngine.processTick(state)
  -> Step 0: drainCommandBuffer(state)  // DRAIN + APPLY at tick start
    -> state.commandBuffer.drain().forEach { applyCommand(it, state) }
  -> Step 1-6: existing tick logic (movement, detection, combat, etc.)
```

### TacticalUnit Field Diff (war/ vs tactical/)

| Field | tactical/ TacticalUnit | war/ TacticalUnit | Resolution |
|-------|------------------------|-------------------|------------|
| commandRange | `Double` (simple radius) | `CommandRange` object (currentRange, maxRange, expansionRate, hasCommandRange) | Replace Double with CommandRange object. Update all references. |
| commandRangeMax | `Double` | (inside CommandRange) | Remove -- absorbed into CommandRange.maxRange |
| ticksSinceLastOrder | `Int` | (implicit in CommandRange.tick()) | Remove -- CommandRange.tick() handles expansion |
| supplies | absent | `Int` | Add to TacticalUnit |
| weaponCooldowns | absent | `MutableMap<TacticalWeaponType, Int>` | Add to TacticalUnit |
| debuffs | absent | `MutableMap<String, Int>` | Add to TacticalUnit |
| detectionCapability | absent | `DetectionCapability` | Add to TacticalUnit |
| shipSubtype | `String` | `ShipSubtype?` (enum) | Keep ShipSubtype enum (richer type) |
| stanceChangeTicksRemaining | absent (uses ticksSinceStanceChange Int) | `Int` | Reconcile: war/ counts DOWN, tactical/ counts UP. Choose count-down (war/ approach) for clarity |
| commandStat/attackStat/etc. | named `command`, `attack`, etc. | named `commandStat`, `attackStat`, etc. | Keep tactical/ naming (shorter, already used throughout) |
| isStopped | absent (computed) | `val isStopped get() = velX == 0.0 && velY == 0.0` | Add computed property |
| retreatProgress | `Double` | absent | Keep (tactical/ feature) |
| missileCount | `Int` | absent | Keep (tactical/ feature) |
| isFlagship | `Boolean` | absent | Keep (tactical/ feature) |
| groundUnitsEmbark | `Int` | absent | Keep (tactical/ feature) |
| isOrbiting | `Boolean` | absent | Keep (tactical/ feature) |
| targetFleetId | `Long?` | absent | Keep (tactical/ feature) |

### Service Merge Map (war/ -> tactical/)

| war/ File | tactical/ Target | Merge Strategy |
|-----------|------------------|----------------|
| `DetectionEngine.kt` | Move to `tactical/DetectionEngine.kt` | Move file, update package declaration. DetectionService already imports it -- just change import paths. |
| `DetectorUnit`, `DetectionTarget` (in DetectionEngine.kt) | Move with DetectionEngine | Same file, same move. |
| `PlanetCaptureProcessor.kt` | Move to `tactical/PlanetCaptureProcessor.kt` | Move file. PlanetConquestService already imports it. |
| `BattleTrigger.kt` | Delete entirely | Stub-only interface + empty registry. Only referenced by ItemModifiers.kt (also a stub). Remove both references. |
| `TacticalCombatEngine.kt` | Delete after field merge | All unique fields absorbed into TacticalBattleEngine.TacticalUnit. |

### Recommended Project Structure (post-merge)

```
engine/tactical/
  TacticalBattleEngine.kt      # Unified engine (with merged fields)
  TacticalBattleState           # (inside TacticalBattleEngine.kt, with commandBuffer field)
  TacticalUnit                  # (inside TacticalBattleEngine.kt, with merged fields)
  TacticalCommand.kt            # NEW: sealed class for command buffer
  CommandHierarchy.kt            # NEW: data model for Phase 9-10
  BattleTriggerService.kt       # Existing (add CommandHierarchy init)
  DetectionService.kt            # Existing
  DetectionEngine.kt             # MOVED from war/
  PlanetCaptureProcessor.kt     # MOVED from war/
  PlanetConquestService.kt      # Existing
  FortressGunSystem.kt           # Existing
  MissileWeaponSystem.kt         # Existing
  GroundBattleEngine.kt          # Existing
  GroundBattleState.kt           # Existing (or wherever it lives)
engine/war/                      # DELETED entirely
```

### Pattern: TacticalCommand Sealed Class

```kotlin
// Source: Design based on CONTEXT.md D-03/D-04 decisions
sealed class TacticalCommand {
    abstract val battleId: Long
    abstract val officerId: Long
    
    data class SetEnergy(
        override val battleId: Long,
        override val officerId: Long,
        val allocation: EnergyAllocation,
    ) : TacticalCommand()
    
    data class SetStance(
        override val battleId: Long,
        override val officerId: Long,
        val stance: UnitStance,
    ) : TacticalCommand()
    
    data class SetFormation(
        override val battleId: Long,
        override val officerId: Long,
        val formation: Formation,
    ) : TacticalCommand()
    
    data class Retreat(
        override val battleId: Long,
        override val officerId: Long,
    ) : TacticalCommand()
    
    data class SetAttackTarget(
        override val battleId: Long,
        override val officerId: Long,
        val targetFleetId: Long,
    ) : TacticalCommand()
    
    data class UnitCommand(
        override val battleId: Long,
        override val officerId: Long,
        val command: String,
        val dirX: Double = 0.0,
        val dirY: Double = 0.0,
        val speed: Double = 1.0,
        val targetFleetId: Long? = null,
        val formation: String? = null,
    ) : TacticalCommand()
    
    data class PlanetConquest(
        override val battleId: Long,
        override val officerId: Long,
        val request: ConquestRequest,
    ) : TacticalCommand()
}
```

### Pattern: CommandHierarchy Data Model

```kotlin
// Source: CONTEXT.md D-05/D-06 decisions
data class CommandHierarchy(
    /** Fleet commander officer ID */
    val fleetCommander: Long,
    /** Sub-commanders: officerId -> SubFleet mapping */
    val subCommanders: MutableMap<Long, SubFleet> = mutableMapOf(),
    /** Succession queue ordered by rank (descending), then evaluation score */
    val successionQueue: MutableList<Long> = mutableListOf(),
    /** CRC radius per officer: officerId -> radius */
    val crcRadius: MutableMap<Long, Double> = mutableMapOf(),
    /** Communication jammed flag */
    var commJammed: Boolean = false,
)

data class SubFleet(
    val commanderId: Long,
    val commanderName: String,
    val unitFleetIds: List<Long>,
    val commanderRank: Int,
)
```

### Anti-Patterns to Avoid

- **Direct state mutation from WebSocket handlers:** This is the current bug being fixed. After Phase 8, ALL command handlers MUST go through commandBuffer.offer(). Never call unit.energy = X directly from a WebSocket handler.
- **Synchronizing on activeBattles map:** Don't add synchronized blocks or locks. The command buffer pattern (ConcurrentLinkedQueue + drain-at-tick-start) eliminates the need for locks entirely.
- **Implementing CommandHierarchy logic in Phase 8:** D-05 explicitly says data model only. Don't add succession logic, CRC enforcement, or comm jamming behavior.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Thread-safe command queue | Custom lock-based queue | `java.util.concurrent.ConcurrentLinkedQueue` | Lock-free, wait-free, proven. JDK standard. |
| Command type dispatch | String-based when() on command names | Kotlin sealed class + when() exhaustive match | Compile-time safety, no missing cases |
| Atomic drain | Manual poll loop with size check | `ConcurrentLinkedQueue` drain idiom: poll() in while loop until null | Standard pattern, no race conditions |

## Common Pitfalls

### Pitfall 1: Forgetting to drain commandBuffer before tick processing
**What goes wrong:** Commands sit in the queue and are never applied. Players see no response to their inputs.
**Why it happens:** processTick() doesn't call drainCommandBuffer() at the start.
**How to avoid:** drainCommandBuffer() MUST be Step 0 in processTick(), before any other processing.
**Warning signs:** Player commands appear to be ignored during battle.

### Pitfall 2: commandRange field type change breaks compilation
**What goes wrong:** Replacing `commandRange: Double` with `commandRange: CommandRange` breaks ~15 references across TacticalBattleEngine, TacticalBattleService, and tests.
**Why it happens:** The Double field is used in comparisons, assignments, and DTO mapping throughout the codebase.
**How to avoid:** Search for ALL references to `commandRange` and `commandRangeMax` and `ticksSinceLastOrder` before starting the refactor. Update TacticalUnitDto to expose `commandRange.currentRange` as a Double for backward compatibility.
**Warning signs:** Compilation errors mentioning type mismatch Double vs CommandRange.

### Pitfall 3: war/ test files import classes that no longer exist
**What goes wrong:** 13+ test files in `engine/war/` import from `com.openlogh.engine.war.*`. After deletion, all fail to compile.
**Why it happens:** Tests reference TacticalCombatEngine, DetectionEngine, BattleTrigger etc. from war/ package.
**How to avoid:** Categorize war/ tests into: (a) tests for TacticalCombatEngine specifically -- delete or port to TacticalBattleEngine tests, (b) tests for DetectionEngine -- update imports to new tactical/ location, (c) tests for legacy BattleTrigger stubs -- delete.
**Warning signs:** Mass compilation failures in test suite after war/ deletion.

### Pitfall 4: BattleWebSocketController still directly calling TacticalBattleService methods
**What goes wrong:** Some command paths bypass the buffer, re-introducing race conditions.
**Why it happens:** Partial refactor -- some handlers updated to enqueue, others forgotten.
**How to avoid:** After refactoring, grep for all `tacticalBattleService.set*` and `tacticalBattleService.retreat` and `tacticalBattleService.execute*` calls from controllers. The ONLY controller methods that should call TacticalBattleService directly are query methods (getActiveBattles, getBattleState).
**Warning signs:** Any `tacticalBattleService.setEnergyAllocation` call from a controller class.

### Pitfall 5: ItemModifiers.kt references BattleTrigger from war/
**What goes wrong:** Compilation error in ItemModifiers.kt after war/ deletion.
**Why it happens:** `engine/modifier/ItemModifiers.kt` imports `BattleTrigger` and `BattleTriggerRegistry` from war/.
**How to avoid:** Check this file and remove/stub the references since BattleTrigger is just a stub interface.
**Warning signs:** Compilation errors in modifier package.

## Code Examples

### Command Buffer Drain Pattern

```kotlin
// Source: Standard ConcurrentLinkedQueue drain idiom
fun drainCommandBuffer(state: TacticalBattleState) {
    var cmd = state.commandBuffer.poll()
    while (cmd != null) {
        applyCommand(cmd, state)
        cmd = state.commandBuffer.poll()
    }
}

private fun applyCommand(cmd: TacticalCommand, state: TacticalBattleState) {
    val unit = state.units.find { it.officerId == cmd.officerId && it.isAlive } ?: return
    when (cmd) {
        is TacticalCommand.SetEnergy -> {
            unit.energy = cmd.allocation
            unit.commandRange = unit.commandRange.resetOnCommand()
        }
        is TacticalCommand.SetStance -> {
            if (unit.stanceChangeTicksRemaining <= 0) {
                unit.stance = cmd.stance
                unit.stanceChangeTicksRemaining = STANCE_CHANGE_COOLDOWN
                unit.commandRange = unit.commandRange.resetOnCommand()
            }
        }
        // ... exhaustive when for sealed class
    }
}
```

### CommandHierarchy Initialization in buildInitialState()

```kotlin
// Source: CONTEXT.md D-06
fun buildCommandHierarchy(
    fleetId: Long,
    leaderOfficerId: Long,
    units: List<TacticalUnit>,
    officerRanks: Map<Long, Int>,  // officerId -> rank level
): CommandHierarchy {
    val sameFleetUnits = units.filter { true /* all units in this fleet */ }
    val sortedByRank = sameFleetUnits
        .sortedByDescending { officerRanks[it.officerId] ?: 0 }
    
    return CommandHierarchy(
        fleetCommander = leaderOfficerId,
        successionQueue = sortedByRank.map { it.officerId }.toMutableList(),
        crcRadius = mutableMapOf(leaderOfficerId to (units.first { it.officerId == leaderOfficerId }.commandRange.maxRange)),
    )
}
```

### WebSocket Handler After Refactor

```kotlin
// BattleWebSocketController -- enqueue pattern
@MessageMapping("/battle/{sessionId}/{battleId}/energy")
fun updateEnergy(
    @DestinationVariable sessionId: Long,
    @DestinationVariable battleId: Long,
    @Payload payload: EnergyAllocationRequest,
) {
    try {
        val allocation = EnergyAllocation(
            beam = payload.beam, gun = payload.gun, shield = payload.shield,
            engine = payload.engine, warp = payload.warp, sensor = payload.sensor,
        )
        val state = activeBattles[battleId] ?: return
        state.commandBuffer.offer(TacticalCommand.SetEnergy(battleId, payload.officerId, allocation))
    } catch (e: Exception) {
        log.error("Error enqueuing energy command: {}", e.message)
    }
}
```

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 (Jupiter) |
| Config file | `backend/game-app/build.gradle.kts` |
| Quick run command | `cd backend && ./gradlew :game-app:test --tests "com.openlogh.engine.tactical.*" -x bootJar` |
| Full suite command | `cd backend && ./gradlew :game-app:test -x bootJar` |

### Phase Requirements -> Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| ENGINE-01 | Single engine processes ticks, war/ engine removed | unit | `./gradlew :game-app:test --tests "com.openlogh.engine.tactical.TacticalBattleEngineTest" -x bootJar` | Exists (needs update) |
| ENGINE-01 | DetectionEngine moved to tactical/, imports updated | unit | `./gradlew :game-app:test --tests "com.openlogh.engine.tactical.DetectionServiceTest" -x bootJar` | Exists (needs import update) |
| ENGINE-02 | Commands buffered, not directly applied | unit | `./gradlew :game-app:test --tests "com.openlogh.engine.tactical.CommandBufferTest" -x bootJar` | Wave 0 |
| ENGINE-02 | Buffer drained at tick start, batch applied | unit | `./gradlew :game-app:test --tests "com.openlogh.engine.tactical.CommandBufferTest" -x bootJar` | Wave 0 |
| ENGINE-03 | CommandHierarchy auto-generated in buildInitialState | unit | `./gradlew :game-app:test --tests "com.openlogh.engine.tactical.CommandHierarchyTest" -x bootJar` | Wave 0 |
| ENGINE-03 | Succession queue ordered by rank | unit | `./gradlew :game-app:test --tests "com.openlogh.engine.tactical.CommandHierarchyTest" -x bootJar` | Wave 0 |

### Sampling Rate
- **Per task commit:** `cd backend && ./gradlew :game-app:test --tests "com.openlogh.engine.tactical.*" -x bootJar`
- **Per wave merge:** `cd backend && ./gradlew :game-app:test -x bootJar`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `CommandBufferTest.kt` -- covers ENGINE-02 (buffer + drain + batch apply)
- [ ] `CommandHierarchyTest.kt` -- covers ENGINE-03 (auto-generation, rank ordering)
- [ ] Update `TacticalBattleEngineTest.kt` -- verify merged fields work in tick processing
- [ ] Update `DetectionServiceTest.kt` -- verify imports after DetectionEngine move
- [ ] Cleanup/delete war/ test files that reference deleted classes

### Existing Test Files Affected

**war/ tests that need action (22 files):**

| Test File | Action | Reason |
|-----------|--------|--------|
| `TacticalCombatEngineTest.kt` | Port or delete | Tests the engine being removed |
| `DetectionEngineTest.kt` | Update imports | DetectionEngine moves to tactical/ |
| `BattleTriggerTest.kt` | Delete | Tests stub-only interface |
| `BattleEngineTest.kt` | Review | May test legacy battle engine (not tactical) |
| `BattleEngineParityTest.kt` | Review | May test legacy parity |
| 17 other war/ test files | Review | Legacy OpenSamguk battle trigger tests -- likely unrelated to tactical engine |

**tactical/ tests that need update (6 files):**
- `TacticalBattleEngineTest.kt` -- add tests for merged fields
- `TacticalBattleIntegrationTest.kt` -- verify end-to-end with command buffer
- `DetectionServiceTest.kt` -- update DetectionEngine import path
- `FortressGunSystemTest.kt` -- likely unaffected
- `MissileWeaponSystemTest.kt` -- likely unaffected
- `PlanetConquestServiceTest.kt` -- update PlanetCaptureProcessor import path

## Dependency Analysis

### Files to Delete (7)
1. `engine/war/TacticalCombatEngine.kt`
2. `engine/war/DetectionEngine.kt` (after move to tactical/)
3. `engine/war/PlanetCaptureProcessor.kt` (after move to tactical/)
4. `engine/war/BattleTrigger.kt`
5. `controller/BattleRestController.kt`
6. `controller/TacticalBattleController.kt`
7. `controller/TacticalBattleRestController.kt`

### Files to Move (2)
1. `engine/war/DetectionEngine.kt` -> `engine/tactical/DetectionEngine.kt`
2. `engine/war/PlanetCaptureProcessor.kt` -> `engine/tactical/PlanetCaptureProcessor.kt`

### Files to Create (2)
1. `engine/tactical/TacticalCommand.kt` -- sealed class
2. `engine/tactical/CommandHierarchy.kt` -- data model

### Files to Modify (5+)
1. `engine/tactical/TacticalBattleEngine.kt` -- merge fields, add drainCommandBuffer
2. `service/TacticalBattleService.kt` -- move command logic to applyCommand, expose activeBattles or provide enqueue method
3. `controller/BattleWebSocketController.kt` -- change to enqueue pattern
4. `engine/tactical/BattleTriggerService.kt` -- add CommandHierarchy init in buildInitialState
5. `engine/modifier/ItemModifiers.kt` -- remove BattleTrigger/BattleTriggerRegistry imports

### Import Chain Impact
- `DetectionService.kt` imports `DetectionEngine`, `DetectorUnit`, `DetectionTarget` from `engine.war` -- update to `engine.tactical`
- `PlanetConquestService.kt` imports `CaptureProcessingInput`, `CaptureProcessingResult`, `PlanetCaptureProcessor` from `engine.war` -- update to `engine.tactical`
- `ItemModifiers.kt` imports `BattleTrigger`, `BattleTriggerRegistry` from `engine.war` -- remove entirely

## Open Questions

1. **BattleWebSocketController access to activeBattles map**
   - What we know: Currently controllers call TacticalBattleService methods that access the private activeBattles map. After refactoring, controllers need to enqueue to TacticalBattleState.commandBuffer.
   - What's unclear: Should BattleWebSocketController get access to activeBattles directly, or should TacticalBattleService expose an `enqueueCommand(battleId, command)` method?
   - Recommendation: TacticalBattleService exposes `fun enqueueCommand(battleId: Long, command: TacticalCommand)` that does `activeBattles[battleId]?.commandBuffer?.offer(command)`. This keeps activeBattles encapsulated.

2. **BattleRestController functionality after deletion**
   - What we know: BattleRestController provides POST /start (test utility) and GET /active, GET /{battleId}. TacticalBattleRestController provides GET /active and GET /{battleId} (duplicate).
   - What's unclear: Do we keep REST query endpoints somewhere?
   - Recommendation: Merge query endpoints into a single REST controller or keep them in TacticalBattleService (already has the methods). The POST /start for testing can move to BattleSimController which already exists.

3. **war/ test file categorization**
   - What we know: 22 test files exist in engine/war/. Some test legacy OpenSamguk battle engine, some test tactical features.
   - What's unclear: Which tests are still relevant and which are purely legacy.
   - Recommendation: Quick audit each test file at implementation time. Port relevant tests, delete legacy ones.

## Sources

### Primary (HIGH confidence)
- Direct code analysis of all files in `engine/tactical/` and `engine/war/` packages
- Direct code analysis of `BattleWebSocketController.kt`, `TacticalBattleService.kt`
- Direct code analysis of `BattleTriggerService.kt`, `Fleet.kt` entity
- CONTEXT.md decisions D-01 through D-08

### Secondary (MEDIUM confidence)
- Java ConcurrentLinkedQueue documentation (JDK standard, well-known pattern)

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - pure Kotlin/Spring refactoring, no new libraries needed
- Architecture: HIGH - clear code path analysis, all files read and diffed
- Pitfalls: HIGH - identified via direct import chain and reference analysis

**Research date:** 2026-04-07
**Valid until:** 2026-05-07 (stable -- internal refactoring, no external dependencies)
