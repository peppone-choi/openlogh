# Domain Pitfalls: Adding Command Chain + AI to Existing Tactical Engine

**Domain:** Real-time tactical command hierarchy and AI integration into existing gin7 web strategy game
**Researched:** 2026-04-07
**Confidence:** HIGH (based on direct codebase analysis + domain research)

---

## Critical Pitfalls

Mistakes that cause rewrites, data corruption, or system-wide failures.

---

### Pitfall 1: Dual Engine Divergence (TacticalBattleEngine vs TacticalCombatEngine)

**What goes wrong:** The codebase contains TWO tactical engines with overlapping but incompatible data models:
- `TacticalBattleEngine` (in `engine/tactical/`) -- the one actually wired into `TacticalBattleService` and the tick loop via `TickEngine.processTick() -> processSessionBattles()`
- `TacticalCombatEngine` (in `engine/war/`) -- has richer models (`CommandRange`, `CommandAuthority`, `DetectionEngine`, weapon cooldowns, debuffs) but is NOT wired into the live tick loop

Both define their own `TacticalUnit` data class with different fields. Adding command chain features to the wrong engine, or trying to merge them mid-implementation, causes massive rework.

**Why it happens:** `TacticalCombatEngine` was built with v2.1 features in mind (it already has `CommandRange`, `CommandAuthority`, `factionCommanders` parameter, `commandTransfers` in `TickResult`). `TacticalBattleEngine` is the one that actually runs battles. A developer naturally gravitates toward the richer model but then discovers it is not connected to anything.

**Consequences:**
- Building on `TacticalCombatEngine` means re-wiring the entire `TacticalBattleService`, `TickEngine` integration, WebSocket broadcasting, and all existing tests
- Building on `TacticalBattleEngine` means re-implementing `CommandRange`, `CommandAuthority`, and detection models that already exist in `engine/war/`
- Attempting to use both creates a permanent source of bugs where state drifts between the two

**Prevention:**
- **Decision MUST be made before any coding begins:** Either migrate `TacticalBattleService` to use `TacticalCombatEngine`, or port `CommandRange`/`CommandAuthority` models into the existing `TacticalBattleEngine` pipeline
- The pragmatic choice is to port models from `engine/war/` into the live `engine/tactical/` pipeline, because `TacticalBattleService` has the full lifecycle (start, tick, command, end, broadcast, DB persistence) already working
- Delete or clearly deprecate the unused engine after migration

**Detection:** If you find yourself importing from both `engine.tactical` and `engine.war` in the same service, you have hit this pitfall.

**Phase to address:** Phase 1 (before any command chain work)

---

### Pitfall 2: WebSocket Broadcast Storm at Scale

**What goes wrong:** `broadcastBattleState()` sends the FULL battle state (all units with all fields) to ALL subscribers every single tick (1 second). With 60 units per fleet, two opposing fleets = 120 units. Each `TacticalUnitDto` has 20+ fields. At 2,000 concurrent players observing a battle, this is 2,000 * ~10KB = 20MB/second of outbound WebSocket traffic PER BATTLE. Multiple simultaneous battles multiply this linearly.

**Why it happens:** The current code in `TacticalBattleService.processBattleTick()` calls `broadcastBattleState()` unconditionally every tick:
```
engine.processTick(state)
// ... check end ...
broadcastBattleState(battle.sessionId, state, battle)
```
Adding command chain data (commander assignments, sub-fleet ownership, command range circles for every officer) increases each broadcast payload by 30-50%.

**Consequences:**
- Spring STOMP simple broker backs up, creating GC pressure from thousands of short-lived `TextMessage` objects (confirmed by Spring WebSocket documentation)
- Client-side: 120-unit state parse at 1Hz causes UI jank, especially on mobile
- Server OutOfMemory under sustained multi-battle load
- Reconnection storms when clients disconnect and reconnect simultaneously

**Prevention:**
- **Delta broadcasting:** Only send changed unit states per tick, not full state. Track dirty flags per `TacticalUnit`
- **Tick broadcast throttling:** Broadcast full state every 5 ticks, delta-only for intermediate ticks. The existing `GameTimeConstants.TICK_BROADCAST_INTERVAL` pattern (used in `TickEngine` line 100) should be applied to tactical battles too
- **Fog of war filtering:** Only send units that the subscribing player's faction has detected (use existing `detectionMatrix`). This cuts payload by ~50% since each side only sees detected enemies
- **Separate command chain channel:** Command hierarchy changes (succession, reassignment) are infrequent events -- broadcast them on a separate topic, not embedded in every tick state
- Use ZGC (`-XX:+UseZGC`) for the game-app JVM to handle short-lived message object allocation without long GC pauses

**Detection:** Monitor WebSocket outbound message queue depth. If it exceeds 100 pending messages per connection, you are in storm territory.

**Phase to address:** Phase 2 (when integrating command chain data into broadcasts), but design for it in Phase 1

---

### Pitfall 3: Race Condition in Flagship Destruction and Command Succession

**What goes wrong:** When a flagship is destroyed in the tick loop (line 226-258 of `TacticalBattleEngine`), the current code does simple "pick unit with most ships" replacement. The v2.1 spec requires a 30-tick vacancy period before succession. During this vacancy, a WebSocket command from a player might target the now-dead commander, or another flagship could be destroyed simultaneously, creating a double-succession race.

**Why it happens:** The current tick processing is single-threaded within `processTick()`, but player commands arrive asynchronously via WebSocket (`TacticalBattleController.handleBattleCommand()`) and mutate `TacticalUnit` fields directly on the same in-memory `TacticalBattleState` object in `ConcurrentHashMap<Long, TacticalBattleState>`. There is no synchronization between the tick loop's unit destruction and the WebSocket handler's unit mutation.

Specific race window:
1. Tick N: Engine marks unit as `isAlive = false` (mid-tick, step 5)
2. Between tick N and N+1: WebSocket command arrives targeting the dead officer
3. `TacticalBattleService.setEnergyAllocation()` finds the unit by `officerId` but it is now dead
4. Current code throws `IllegalArgumentException` -- but with command chain, the command might be "transfer authority from dead commander" which is a valid succession action

**Consequences:**
- Commands silently fail or throw exceptions during the 30-tick vacancy
- Double succession: two units both flagged as `isFlagship = true`
- Orphaned sub-fleet units that lose their commander reference but the system does not detect this

**Prevention:**
- **Synchronize tick processing and command handling** on the `TacticalBattleState` object. Use `synchronized(state)` blocks or process commands as queued events within the tick loop (command buffer pattern)
- **Command buffer pattern (recommended):** WebSocket commands go into a `ConcurrentLinkedQueue<BattleCommand>` per battle. At the START of each tick, drain the queue and apply commands. This eliminates mid-tick mutation entirely
- **Succession state machine:** Model succession as explicit states: `ACTIVE -> VACANCY(ticksRemaining) -> SUCCESSION_RESOLVED`. The vacancy state rejects new commands to the dead commander and queues them for the successor
- **Test with concurrent command injection during flagship destruction** -- this is the exact scenario that will break

**Detection:** Log warnings when `setEnergyAllocation`/`setFormation` etc. are called for a dead unit. If you see these in production logs, the race exists.

**Phase to address:** Phase 1 (command buffer pattern), Phase 2 (succession state machine)

---

### Pitfall 4: O(N^2) Command Range Distance Checks per Tick

**What goes wrong:** Command range checking requires computing distance from each commander to each unit they might command. With the v2.1 hierarchy (1 fleet commander + up to 9 sub-commanders, each with their own circle), checking "which units are in whose command range" is O(commanders * units) per tick. For a full fleet: 10 commanders * 60 units = 600 distance calculations. Two opposing fleets: 1,200. This runs every tick (1 second) alongside combat, detection, movement, and morale calculations.

The existing `detectionService.updateDetectionMatrix()` already does O(N^2) per-unit detection sweeps. Adding command range on top doubles the spatial query cost.

**Why it happens:** Naive implementation: for each commander, iterate all friendly units and compute `sqrt((ax-bx)^2 + (ay-by)^2)`. The existing `processMovement()` and `processCombat()` already do similar O(N) scans per unit. Stacking another O(N^2) pass makes the tick budget exceed 1 second under load.

**Consequences:**
- Tick processing exceeds 1-second budget, causing battle simulation to fall behind real time
- Players see stuttering/laggy battles
- TickDaemon falls behind, compounding the delay across all sessions

**Prevention:**
- **Spatial partitioning:** Use a grid-based spatial hash (the battlefield is 1000x600, divide into 100x100 cells). Command range lookup becomes O(nearby cells) instead of O(all units). This also benefits detection and target finding
- **Skip sqrt:** Compare `distanceSquared` against `range * range`. The existing code uses `sqrt` everywhere (lines 349, 393, 521-522). Eliminating sqrt from hot loops is a simple 30% speedup for distance-heavy code
- **Stagger AI and command range updates:** Not every unit needs command range recalculated every tick. Update command range for half the units on even ticks, half on odd ticks. The visual difference at 1Hz is imperceptible
- **Cache commander-to-unit assignments:** Once sub-fleet assignments are made, the unit list per commander is static until reassignment. Only recompute "in range?" checks, not "who commands whom?"

**Detection:** Profile `processTick()` duration. If it exceeds 50ms for a single battle, optimization is needed (target: <10ms per battle to leave headroom for multiple concurrent battles).

**Phase to address:** Phase 1 (spatial hash), applied throughout all subsequent phases

---

### Pitfall 5: AI Decision Frequency Starving the Tick Budget

**What goes wrong:** The v2.1 spec requires tactical AI for every offline/NPC unit (up to 60 units per fleet that are not player-controlled). If the AI evaluates all units every tick, with each evaluation doing threat assessment, target selection, energy/formation adjustment, and movement planning, the AI alone can consume the entire tick budget.

The current strategic AI (`OfflinePlayerAIService`) runs every 100 ticks (line 136 of `TickEngine`). But tactical AI needs much faster response -- a unit under fire cannot wait 100 ticks to react. The temptation is to run tactical AI every tick for every unit.

**Why it happens:** Developers conflate "real-time responsiveness" with "evaluate every unit every tick." In reality, human players issue maybe 1 command per 5-10 seconds per unit. AI that reacts every tick is both unrealistically fast AND computationally expensive.

**Consequences:**
- AI evaluation for 60 units * 2 sides = 120 AI decisions per tick. Each decision involves scanning enemies, evaluating threats, choosing targets -- at minimum 10-20 distance calculations each. Total: 1,200-2,400 operations per tick just for AI
- Combined with combat, detection, command range, and broadcasting, total tick processing exceeds budget
- If AI and tick run synchronously (current architecture), the entire game simulation slows down

**Prevention:**
- **Tiered AI frequency:**
  - CRITICAL (under fire, flagship destroyed): Evaluate every tick
  - ACTIVE (in combat zone, detected enemies): Evaluate every 3-5 ticks
  - IDLE (no enemies detected, en route): Evaluate every 10-20 ticks
- **Budgeted AI:** Process at most N AI decisions per tick (e.g., 20). Round-robin through units, prioritizing those in CRITICAL state
- **Reactive triggers instead of polling:** Instead of evaluating all units on a timer, trigger AI re-evaluation when something changes: "unit took damage," "enemy detected," "commander issued order," "entered/left command range"
- **Precomputed behavior tables:** For simple decisions (energy allocation based on stance, formation based on enemy range), use lookup tables instead of scoring functions. The existing `UtilityScorer` pattern works for strategic decisions but is too heavy for per-tick tactical choices

**Detection:** Measure time spent in AI evaluation vs. total tick time. AI should not exceed 30% of the tick budget.

**Phase to address:** Phase 3 (tactical AI implementation), but the frequency architecture must be designed in Phase 1

---

## Moderate Pitfalls

Mistakes that cause significant rework or bugs but not full rewrites.

---

### Pitfall 6: Sub-Fleet Assignment Without Persistence Causes State Loss on Crash

**What goes wrong:** Sub-fleet assignments (which officer commands which units within a 60-unit fleet) are planned as in-memory runtime state in `TacticalBattleState`. If the game-app JVM crashes or restarts during a battle, all sub-fleet assignments are lost. The battle can be reconstructed from DB (`TacticalBattle` entity), but the command hierarchy cannot.

**Why it happens:** The current `activeBattles = ConcurrentHashMap<Long, TacticalBattleState>()` is purely in-memory. `TacticalBattle` entity only stores `participants` (a JSON map of fleet IDs), not the detailed command hierarchy. The existing code saves tick count to DB every 10 ticks (line 134) but not unit state.

**Consequences:**
- After crash recovery, all units revert to "commander direct control" with no sub-fleet assignments
- Players who carefully organized their fleet lose all their work
- AI units lose their behavioral context (what order they were following)

**Prevention:**
- **Persist command hierarchy to Redis** (already in the tech stack). Key: `battle:{battleId}:hierarchy`. Update on every assignment change (infrequent). Redis survives JVM restart
- **Alternatively, add a `command_hierarchy` JSONB column to `TacticalBattle` entity** and persist it alongside tick count every 10 ticks
- **Design the hierarchy as reconstructible:** If the hierarchy is lost, it can be auto-reconstructed from the `UnitCrew` table (which officer has which slot role) as a degraded fallback

**Detection:** Kill the game-app during an active battle and check if hierarchy survives restart.

**Phase to address:** Phase 2 (when implementing sub-fleet assignments)

---

### Pitfall 7: Mixing Strategic AI (AiCommandBridge) with Tactical AI Creates Circular Dependencies

**What goes wrong:** The existing `AiCommandBridge` executes strategic commands through `CommandExecutor` with full CP deduction, cooldowns, and DB persistence. If tactical AI tries to reuse this pipeline for tactical decisions (e.g., "AI officer wants to change formation"), it triggers the entire command validation chain including CP checks, position card gating, and cooldown enforcement -- none of which apply in tactical combat.

**Why it happens:** Code reuse instinct: "We already have an AI command system, let's extend it for tactical commands." But the strategic command pipeline (`CommandExecutor -> CommandRegistry -> CommandGating`) is fundamentally different from tactical commands (`TacticalBattleService.executeUnitCommand()`).

**Consequences:**
- Tactical AI actions fail CP validation (tactical commands do not cost CP)
- AI tactical decisions go through DB round-trips (CommandExecutor persists results) when they should be pure in-memory state changes
- Circular dependency: TacticalBattleService -> TacticalAI -> AiCommandBridge -> CommandExecutor -> (potentially) TacticalBattleService

**Prevention:**
- **Create a separate `TacticalAIService`** that operates ONLY on in-memory `TacticalBattleState`. It should call `TacticalBattleService.executeUnitCommand()` or directly mutate `TacticalUnit` fields -- never go through `CommandExecutor`
- The tactical AI output should be `UnitCommandRequest` objects (MOVE, ATTACK, FORMATION_CHANGE, etc.), not gin7 command names
- Strategic AI (`AiCommandBridge`) should only decide whether to ENTER a battle (operation planning). Once in battle, tactical AI takes over completely

**Detection:** If your tactical AI class imports from `com.openlogh.command.*`, you have crossed the boundary.

**Phase to address:** Phase 3 (tactical AI implementation)

---

### Pitfall 8: Command Chain Depth Creates Latency Cascades in Order Propagation

**What goes wrong:** The v2.1 spec defines a 3-level hierarchy: Supreme Commander -> Fleet Commander -> Sub-fleet officer -> Individual units. When the supreme commander issues an order, it should propagate down the chain. If each level requires the command range circle to reach the next level, there is a propagation delay: supreme commander's circle expands to reach fleet commander (N ticks), then fleet commander's circle resets and expands to reach sub-fleet officer (M ticks), then sub-fleet officer's circle resets and expands to reach units (K ticks). Total delay: N+M+K ticks, potentially 30-60 seconds before a unit at the end of the chain acts.

**Why it happens:** Literal interpretation of the command range mechanic as a cascading delay. Each `commandRange.resetOnCommand()` creates a propagation chain.

**Consequences:**
- Players feel the game is unresponsive -- they issue an order but units do not react for 30+ seconds
- Makes coordinated fleet maneuvers nearly impossible
- Creates a massive advantage for smaller fleets (shorter chains, faster response)

**Prevention:**
- **Flat command propagation for player orders:** When a player (human) issues a direct command, it reaches all units within THEIR command range immediately. The 3-level chain adds flavor (sub-commanders can give independent orders) but should not create cascading delays for direct player commands
- **Chain delay only for AI-to-AI propagation:** The cascading delay makes sense when AI commanders are relaying the supreme commander's strategic intent to their subordinates. This creates emergent behavior (some sub-fleets react faster than others based on their commander's command stat)
- **Implement as "order queue" not "signal propagation":** Each unit has an order queue. Player commands go directly into the queue. AI commander orders propagate through command range but are queued, not blocked

**Detection:** Playtest with a full 60-unit fleet. If the player issues "all fleet advance" and the last unit starts moving 30+ seconds later, this pitfall has been hit.

**Phase to address:** Phase 2 (command range integration)

---

### Pitfall 9: BattleCommandRequest DTO Does Not Support Command Chain Operations

**What goes wrong:** The current `BattleCommandRequest` only supports 3 command types: `energy`, `formation`, `retreat`. The `TacticalBattleController` WebSocket handler has a simple `when` block for these 3 types. Adding command chain operations (assign units, transfer authority, delegate to sub-commander, broadcast fleet-wide order) requires significant DTO and controller expansion, but doing it incrementally creates a messy API with inconsistent patterns.

Current WebSocket API:
```
/app/battle/{sessionId}/command -> BattleCommandRequest(battleId, officerId, commandType, ...)
```

The existing `UnitCommandRequest` (11 commands) is dispatched through a separate code path via `executeUnitCommand()`, not through the WebSocket controller.

**Why it happens:** The WebSocket controller and the unit command system were built at different times with different patterns. Command chain operations do not fit cleanly into either.

**Consequences:**
- Piecemeal additions create an inconsistent API that is hard for the frontend to consume
- Some commands go through WebSocket, others through REST, creating timing inconsistencies
- No unified authorization check for "can this officer command this unit?"

**Prevention:**
- **Unify all tactical commands through a single WebSocket endpoint** with a discriminated union pattern:
  ```kotlin
  sealed class TacticalCommand {
      data class UnitCommand(val unitId: Long, ...) : TacticalCommand()
      data class HierarchyCommand(val type: String, ...) : TacticalCommand()
      data class FleetCommand(val targetUnits: List<Long>, ...) : TacticalCommand()
  }
  ```
- **Add authorization middleware:** Before executing any command, verify the requesting officer has command authority over the target units. This is a new cross-cutting concern that does not exist in the current code
- **Design the command schema in Phase 1**, implement incrementally in later phases

**Detection:** If the frontend needs to call 3+ different endpoints/message types to execute a single "delegate sub-fleet and issue order" action, the API is fragmented.

**Phase to address:** Phase 1 (API design), Phase 2 (implementation)

---

### Pitfall 10: Testing Concurrent Real-Time Battles is Nearly Impossible Without Infrastructure

**What goes wrong:** The current test suite has `TacticalBattleEngineTest` (unit tests for tick processing) and `TacticalBattleIntegrationTest` (tests for service-level operations). Neither tests concurrent WebSocket commands during tick processing, multiple simultaneous battles, or the race conditions described in Pitfall 3.

**Why it happens:** Testing real-time concurrent systems requires either:
1. Deterministic replay (inject a fixed `Random` and a command schedule) -- partially supported (the engine accepts `rng: Random`)
2. Stress testing with simulated clients -- not set up
3. Race condition detection via thread sanitizers -- not available on JVM without specialized tools

**Consequences:**
- Command chain bugs only manifest in production under load
- Succession race conditions are virtually undetectable without concurrent test harnesses
- Refactoring the tick loop (needed for command buffer pattern) has no safety net

**Prevention:**
- **Build a `TacticalBattleSimulator` test harness** that:
  - Runs N ticks deterministically with a seeded `Random`
  - Injects commands at specific ticks (simulating player input)
  - Asserts on intermediate states (not just final outcomes)
  - Supports concurrent command injection from multiple threads
- **Property-based testing for invariants:**
  - "At most one flagship per side per faction at any tick"
  - "Total ships across all units never increases"
  - "Every alive unit has exactly one commander in the hierarchy"
  - "No unit is commanded by a dead officer"
- **The `rng: Random` parameter on `processTick()` is already testability-friendly** -- extend this pattern to all new systems

**Detection:** If you cannot write a test that reproduces a reported bug, you lack test infrastructure.

**Phase to address:** Phase 1 (test harness), ongoing through all phases

---

## Minor Pitfalls

Issues that cause friction but are recoverable.

---

### Pitfall 11: PersonalityTrait Weights Are Strategic, Not Tactical

**What goes wrong:** The existing `PersonalityTrait` system (AGGRESSIVE, DEFENSIVE, BALANCED, CAUTIOUS, RECKLESS) weights `CommandGroup` utility scores (OPERATIONS, LOGISTICS, etc.). These are strategic personality dimensions. Tactical AI needs different personality expressions: AGGRESSIVE in tactics means "rush forward, focus fire, ignore casualties," not "prefer OPERATIONS commands."

**Prevention:** Create a separate `TacticalPersonalityProfile` that maps traits to tactical behavior parameters (engagement distance, retreat threshold, target prioritization, formation preference). Derive it from the existing `PersonalityTrait` but with tactical-specific weights.

**Phase to address:** Phase 3

---

### Pitfall 12: Hardcoded Flagship Selection Logic

**What goes wrong:** Current flagship succession (line 248 of `TacticalBattleEngine`) picks the unit with the most ships: `maxByOrNull { it.ships }`. The v2.1 spec requires succession by rank -> evaluation -> merit (same as `CommandAuthority.compareTo()`). Forgetting to update this logic means flagship succession ignores the command authority hierarchy.

**Prevention:** Replace the inline succession logic with `CommandAuthority.resolveCommander()` or equivalent. The model already exists in `CommandAuthority.kt`.

**Phase to address:** Phase 2 (succession implementation)

---

### Pitfall 13: TickEngine Single-Threaded Bottleneck with Multiple Battles

**What goes wrong:** `TickEngine.processTick()` calls `tacticalBattleService.processSessionBattles()` which iterates ALL active battles sequentially. If 5 battles are active simultaneously (plausible in a large session), and each battle tick takes 50ms (with AI + command range + combat), the total is 250ms just for battles -- 25% of the 1-second tick budget consumed by tactical battles alone.

**Prevention:**
- Process battles in parallel using `CompletableFuture` or Kotlin coroutines. Each battle's state is independent (separate `TacticalBattleState` in the `ConcurrentHashMap`)
- Set a per-battle tick budget limit (e.g., 100ms) with logging when exceeded
- The current code already uses `ConcurrentHashMap` for `activeBattles`, so parallel processing is safe at the battle level (but NOT within a single battle's tick)

**Phase to address:** Phase 1 (architecture), optimize in Phase 4 if needed

---

## Phase-Specific Warnings

| Phase Topic | Likely Pitfall | Mitigation |
|-------------|---------------|------------|
| Phase 1: Engine unification + command buffer | Pitfall 1 (dual engine), Pitfall 3 (race conditions) | Decide engine strategy first. Implement command buffer before any hierarchy code |
| Phase 2: Sub-fleet assignment + command range | Pitfall 4 (O(N^2)), Pitfall 6 (persistence), Pitfall 8 (latency cascade), Pitfall 9 (DTO design) | Spatial hash from day 1. Persist hierarchy to Redis. Design flat propagation for player commands |
| Phase 3: Tactical AI | Pitfall 5 (AI frequency), Pitfall 7 (circular deps), Pitfall 11 (personality mismatch) | Tiered AI frequency. Separate TacticalAIService. Tactical personality profiles |
| Phase 4: Succession + integration | Pitfall 2 (broadcast storm), Pitfall 12 (flagship logic), Pitfall 10 (testing gaps) | Delta broadcasting. Use CommandAuthority for succession. Full concurrent test suite |
| All phases | Pitfall 13 (tick budget) | Profile every tick. Budget: <10ms per battle, <500ms total for all tick processing |

---

## Codebase-Specific Risk Map

| Existing Code | Risk When Modified | Why |
|---|---|---|
| `TacticalBattleEngine.processTick()` | HIGH | Adding command chain processing to the tick loop. Must stay under 1-second budget |
| `TacticalBattleService.activeBattles` (ConcurrentHashMap) | HIGH | Concurrent access from tick loop + WebSocket handlers. Command buffer pattern needed |
| `broadcastBattleState()` | HIGH | Payload size explosion with hierarchy data. Delta broadcasting needed |
| `TacticalBattleController.handleBattleCommand()` | MEDIUM | Only handles 3 command types. Must be expanded for hierarchy operations |
| `UtilityScorer` | LOW | Strategic AI only. Do not reuse for tactical decisions |
| `AiCommandBridge` | MEDIUM | Tempting to extend for tactical AI but fundamentally wrong abstraction |
| `CommandRange` / `CommandAuthority` models | LOW | Well-designed, ready to use. Port into `engine/tactical/` pipeline |
| `UnitCrew` entity + `CrewSlotRole` | LOW | Already models the 10-slot crew structure. Use as basis for sub-fleet assignment |

---

## Sources

- Direct codebase analysis of `TacticalBattleEngine.kt`, `TacticalCombatEngine.kt`, `TacticalBattleService.kt`, `TacticalBattleController.kt`, `TickEngine.kt`, `AiCommandBridge.kt`, `UtilityScorer.kt`, `FactionAI.kt`, `CommandRange.kt`, `CommandAuthority.kt`, `UnitType.kt`, `CrewSlotRole.kt`, `UnitCrew.kt`
- v2.1 milestone scope document (`.claude/projects/*/memory/project_v21_milestone_scope.md`)
- [Spring WebSocket STOMP scaling](https://websocket.org/guides/frameworks/spring-boot/) -- broadcast storm prevention, GC tuning for message objects
- [Hierarchical control of multi-agent RL in RTS games](https://www.sciencedirect.com/science/article/abs/pii/S0957417421010897) -- decomposing command hierarchy into strategic + tactical levels
- [RTS devlog: Optimizing for 1000 units](https://www.gamedev.net/blogs/entry/2274556-rts-devlog-7-optimizing-performance-for-1000-units/) -- spatial partitioning, tick budget management
- [AI for large numbers of RTS units](https://gamedev.net/forums/topic/698291-ai-for-large-numbers-of-rts-like-units/) -- tiered AI frequency, budgeted evaluation
- [Handling race conditions in distributed systems](https://www.geeksforgeeks.org/computer-networks/handling-race-condition-in-distributed-system/) -- synchronization patterns for concurrent state mutation
- [State synchronization events vs deltas](https://www.gamedev.net/forums/topic/707681-state-synchronization-events-vs-deltas/5429992/) -- delta broadcasting vs full state approaches
