# Project Research Summary

**Project:** Open LOGH v2.1 — Tactical Command Chain & AI
**Domain:** Real-time tactical command hierarchy + AI integration into existing gin7-faithful web MMO strategy game
**Researched:** 2026-04-07
**Confidence:** HIGH

## Executive Summary

Open LOGH v2.1 is a brownfield milestone adding gin7's organizational simulation mechanics to the existing tactical battle engine. The codebase already contains most of the necessary infrastructure — `CommandRange`, `PersonalityTrait`, `UtilityScorer`, and a working tick-based `TacticalBattleEngine` — but these components were built in isolation and are not yet wired together into a command hierarchy. The core work is integration and extension of existing systems, not greenfield construction. Crucially, zero new library dependencies are needed: all spatial math, state machines, and AI logic are implementable with Kotlin stdlib and the existing Spring Boot 3 stack.

The recommended approach builds features in strict dependency order: command chain data model first (everything else depends on knowing which officer controls which units), then authority enforcement and succession mechanics, then tactical AI behavior, then strategic AI enhancement, and finally frontend visualization. The critical dependency chain is: Unit Command Distribution -> Command Range Circle (per-officer) -> Operation Plan Linkage -> Tactical AI -> Command Succession -> Delegation. Skipping this order causes rework.

The dominant risk is the dual-engine problem: the codebase contains two tactical engines (`TacticalBattleEngine` in `engine/tactical/` wired to production, `TacticalCombatEngine` in `engine/war/` with richer models but not connected to anything). A decision on engine strategy must be made before any v2.1 code is written; building on the wrong engine wastes the entire milestone. Secondary risks are WebSocket broadcast storms when hierarchy data is added to per-tick payloads, and race conditions between the tick loop and WebSocket command handlers during flagship succession — both solvable with delta broadcasting and a command buffer pattern respectively.

---

## Key Findings

### Recommended Stack

No new dependencies. The entire v2.1 feature set builds on the existing `build.gradle.kts` and `package.json`. Spatial range checks use `kotlin.math.sqrt` on a grid with at most 120 units — no spatial index library is justified. The 4-state succession machine uses a Kotlin `sealed class` — no Spring Statemachine. Tactical AI uses utility scoring extending the existing `UtilityScorer`/`PersonalityWeights` pattern — no behavior tree library. Command hierarchy serializes as JSONB alongside the existing `TacticalBattleState` — no new tables needed for the hierarchy itself.

The only schema changes required are: (1) a new `OperationPlan` entity (V45+ Flyway migration) to connect strategic operation plans to tactical battle initialization, and (2) two columns on `tactical_battle` (`operation_plan_id`, `operation_objective`).

**Core technologies (all existing — do not add to):**
- `kotlin.math` + Kotlin `sealed class`: Spatial distance checks and 4-state succession FSM — replaces need for any external library
- `TacticalBattleEngine` + `TickEngine`: Existing 1-second tick loop with stateless engine pattern — extend by injecting `CommandChainManager` and `TacticalAI` as dependencies
- `CommandRange.kt`: Per-officer circle model with `tick()`, `resetOnCommand()`, `isInRange()` — already correct API, needs to shift from per-unit to per-officer scope
- `PersonalityTrait` + `PersonalityWeights`: 5 personality types with stat multipliers — reuse for tactical AI behavior modifiers
- `UnitCrew` + `CrewSlotRole`: 10-slot crew structure (COMMANDER through ADJUTANT) — already models the fleet command hierarchy foundation
- React Three Fiber + `@stomp/stompjs`: Existing frontend stack handles CRC circle rendering and WebSocket command chain updates with no additions

### Expected Features

**Must have (table stakes — missing breaks the organizational simulation core value):**
- Unit command distribution: 60 units split among 10 crew members by priority (online > rank > evaluation > merit)
- Command range circle mechanics (per-officer): flagship CRC expands over time, resets to 0 on command issue, gates which units can receive orders
- Operation plan to tactical AI linkage: gin7 p.37-38 operation purpose (CAPTURE/DEFEND/SWEEP) determines tactical AI behavior
- Tactical AI for offline/NPC units: mission-objective-driven + personality-modulated behavior
- Command succession on flagship destruction: 30-tick vacancy gap then rank-based auto-promotion (replace current `maxByOrNull { it.ships }`)
- CRC UI visualization: players must see command circles to understand why orders are not reaching units

**Should have (differentiators):**
- Real-time command delegation/reassignment mid-battle (conditions: target unit outside CRC + stopped)
- Personality-driven tactical variety (AGGRESSIVE charges, CAUTIOUS holds distance — Reinhard vs Yang feel)
- Concentrated/distributed attack target selection based on personality and situation
- Strategic AI auto-operation-planning (FactionAI creates operation plans when at war)
- Sub-fleet formation commands per sub-commander (independent energy/formation per officer)
- Communication jamming effects (supreme commander cannot issue fleet-wide orders)

**Defer to future milestone:**
- Battle replay with command chain visualization (high effort, low urgency)
- AI learning or ML-based tactical decisions (overkill; use utility scoring)
- Player-controlled individual ship movement (breaks organizational simulation core value)
- Complex custom formation editor (4 existing formations are sufficient)

### Architecture Approach

New components are injected into the existing tick loop as stateless dependencies — the same pattern already used for `MissileWeaponSystem`, `DetectionService`, and `FortressGunSystem`. `CommandChainManager` is inserted at tick step 1.5 (after per-unit command range update, before movement). `TacticalAI` is inserted at tick step 4.5 (after combat). The strategic AI (`AiCommandBridge`) and tactical AI (`TacticalAI`) are kept completely separate systems — strategic AI uses `CommandExecutor` with CP/cooldown/PositionCard validation; tactical AI directly mutates in-memory `TacticalBattleState`. Crossing this boundary is the most common architectural mistake in this domain.

**Major components (new):**
1. `CommandChainManager` — resolves authority (who commands whom), manages succession timers, handles unit reassignment; stateless, injected into `TacticalBattleEngine`
2. `SubFleetAssignment` — distributes 60 units among 10 crew members at battle initialization in `BattleTriggerService`
3. `CommandSuccessionService` — 30-tick vacancy state machine on flagship destruction; replaces current instant `maxByOrNull { it.ships }` logic
4. `TacticalAI` — per-tick NPC/offline unit decisions (energy, stance, formation, target, retreat) driven by `MissionObjective` + `PersonalityTrait`; pure function, no DB access
5. `ThreatAssessment` — evaluates retreat conditions, target selection, energy recommendations per AI-controlled unit per tick
6. `BattleWebSocketController` authority gate — validates `officerId` against `CommandChainManager.canCommand()` before accepting any tactical command; new endpoints `/delegate` and `/fleet-order`
7. Frontend `CommandRangeCircle.tsx` (modified) — renders one circle per commander officer, not per unit; new sub-fleet assignment panel shows hierarchy

### Critical Pitfalls

1. **Dual engine divergence** — `TacticalCombatEngine` (`engine/war/`) has richer models including `CommandRange` and `CommandAuthority` but is NOT wired to production. `TacticalBattleEngine` (`engine/tactical/`) IS wired. Avoid: decide engine strategy before writing any code; port `CommandRange`/`CommandAuthority` models into the live `engine/tactical/` pipeline; delete or deprecate the unused engine immediately.

2. **WebSocket broadcast storm** — adding command hierarchy data to every 1-second full-state broadcast inflates payloads 30-50%. At 2,000 spectators per battle this becomes ~20MB/s outbound per battle. Avoid: delta broadcasting (only changed units per tick), separate command chain event channel for infrequent hierarchy events, broadcast full state every 5 ticks not every tick.

3. **Flagship destruction race condition** — WebSocket commands arrive asynchronously and mutate `TacticalBattleState` concurrently with the tick loop. During the 30-tick vacancy period, commands targeting the dead commander cause silent failures or double-succession. Avoid: command buffer pattern — WebSocket commands go into a `ConcurrentLinkedQueue` per battle, drained at the start of each tick; no direct state mutation from WebSocket handlers.

4. **O(N^2) command range checks per tick** — 10 commanders * 60 units = 600 distance calculations per fleet per tick, stacked on the existing detection O(N^2). Avoid: compare `distanceSquared` vs `range * range` (skip `sqrt`); cache static sub-fleet assignments (only recompute in-range checks, not who-commands-whom); stagger recalculation across even/odd ticks.

5. **Tactical AI accessing repositories** — injecting `OfficerRepository` or `FleetRepository` into `TacticalAI` for per-tick queries blocks the tick engine with DB I/O. Avoid: `TacticalAI` must be a pure function operating only on `TacticalBattleState`; all officer/fleet data loaded once at battle start by `BattleTriggerService`.

---

## Implications for Roadmap

The feature dependency chain dictates a 6-phase structure. The critical constraint is that unit command distribution must be fully working before CRC gating, succession, and AI can function correctly. Phases 4 and 5 can be parallelized if capacity allows.

### Phase 1: Engine Unification + Command Chain Data Model

**Rationale:** The dual-engine problem is a pre-existing structural issue that must be resolved before any hierarchy code is written. Every subsequent phase builds on the data structures and concurrency patterns defined here. Getting the data model wrong here requires rewriting all later phases.
**Delivers:** Resolved engine strategy (port `CommandRange`/`CommandAuthority` from `engine/war/` into `engine/tactical/` pipeline, deprecate `TacticalCombatEngine`); `CommandAuthority`, `CommandChain`, `PendingSuccession` data classes; `SubFleetAssignment` priority-sort logic; `OperationPlan` entity + V45+ Flyway migration; `TacticalBattleState` and `TacticalUnit` extended with `commanderId`, `unitSlotIndex`, `isUncontrolled` fields; command buffer pattern (`ConcurrentLinkedQueue`) replacing direct WebSocket-to-state mutation; `TacticalBattleSimulator` deterministic test harness.
**Addresses:** Unit command distribution foundation, operation plan entity (enables AI linkage in Phase 3)
**Avoids:** Pitfall 1 (dual engine), Pitfall 3 (race condition), Pitfall 10 (testing gaps)

### Phase 2: CommandChainManager + Authority Enforcement + Succession

**Rationale:** Once data structures exist, wiring authority checks into the tick loop and WebSocket layer makes command hierarchy a live runtime concept. Succession state machine belongs here because it uses the same authority data structures.
**Delivers:** `CommandChainManager` with `canCommand()`, `tick()` (circle expansion), `processDestruction()` (30-tick delay), `reassignUnits()` (with precondition checks); authority gate on all `TacticalBattleService` command methods; new WebSocket endpoints `/delegate` and `/fleet-order`; rank-based succession replacing current `maxByOrNull { it.ships }`; `CommandChain` persisted to Redis on change for crash recovery; delta broadcasting designed (full broadcast every 5 ticks, command chain changes on separate channel).
**Addresses:** CRC mechanics (per-officer), command succession, real-time delegation
**Avoids:** Pitfall 2 (broadcast storm — delta strategy applied here), Pitfall 4 (O(N^2) — `distanceSquared` and staggered recalculation), Pitfall 6 (hierarchy persistence to Redis), Pitfall 8 (latency cascade — flat propagation for direct player commands), Pitfall 9 (unified tactical command DTO schema), Pitfall 12 (hardcoded flagship selection)

### Phase 3: Tactical AI

**Rationale:** Depends on Phase 2 knowing which units are NPC/uncontrolled. `TacticalAI` reads `MissionObjective` from battle state (populated by Phase 1's `OperationPlan` entity) and `PersonalityTrait` from officer data loaded at battle start.
**Delivers:** `TacticalAI` service (pure function, no DB); `ThreatAssessment` (retreat/target/energy decisions); `TacticalPersonalityModifier` (maps existing `PersonalityTrait` to tactical-specific parameters — engagement distance, retreat threshold, formation preference, target prioritization); tiered AI evaluation frequency (CRITICAL every tick, ACTIVE every 3-5 ticks, IDLE every 10-20 ticks); wired into `TacticalBattleEngine` at tick step 4.5.
**Addresses:** Tactical AI for offline/NPC units, personality-driven variety, concentrated/distributed attack, communication jamming
**Avoids:** Pitfall 5 (AI frequency starvation — tiered evaluation), Pitfall 7 (mixing strategic/tactical AI — separate service boundary), Pitfall 11 (personality weight mismatch — new `TacticalPersonalityModifier` separate from strategic `PersonalityWeights`)

### Phase 4: Strategic AI Enhancement

**Rationale:** `FactionAI` improvement is independent of tactical AI (it runs at strategic tick cadence, every 100 ticks) but feeds operation plans into the tactical AI system built in Phase 3. Can be parallelized with Phase 5 if capacity allows.
**Delivers:** `FactionAI` auto-creates operation plans when at war (CAPTURE for weak enemy systems, DEFEND for threatened own systems, SWEEP for raider fleets); `OfficerAI` moves assigned officers toward operation targets using existing movement logic; fleet composition selection appropriate for operation type.
**Addresses:** Strategic AI auto-operation-planning (differentiator feature); ensures NPC factions fight purposefully after v2.1 ships

### Phase 5: WebSocket Broadcast Optimization

**Rationale:** Must be in production before scale testing. Delta broadcasting is designed in Phase 2 but implemented here as a focused pass. Batches well with Phase 4 as a stability consolidation step before frontend work.
**Delivers:** Delta-only unit state broadcasts for intermediate ticks (full state every 5 ticks); separate `/topic/world/{sessionId}/battle/chain` topic for command hierarchy change events (infrequent, event-driven, not per-tick); fog-of-war filtering applied to tick broadcast (~50% payload reduction — only send detected enemies); ZGC JVM flag (`-XX:+UseZGC`) added to game-app startup for short-lived message object GC.
**Addresses:** Scale requirement (2,000 concurrent spectators per battle)
**Avoids:** Pitfall 2 (broadcast storm — full implementation of delta strategy)

### Phase 6: Frontend Integration

**Rationale:** All backend contracts must be stable before UI work to avoid churn from changing DTOs and broadcast formats. Phase 6 is the final integration pass — individual UI components can be prototyped earlier, but this phase hardens them against the stable backend.
**Delivers:** `CommandRangeCircle.tsx` rewritten to render per-commander circles (centered on officer's flagship position, color-coded by sub-fleet); sub-fleet assignment panel showing crew hierarchy with officer names and unit counts; authority-aware command controls (buttons disabled when logged-in officer lacks authority over target units); succession visual feedback ("지휘 승계 중" countdown indicator, flash on flagship destruction); unit color/opacity coding by sub-fleet (out-of-range units dimmed); `CommandChainInfo` and `CommandAuthorityInfo` type extensions in `tactical.ts`.
**Addresses:** CRC UI visualization (table stakes), sub-fleet formation commands (differentiator)
**Avoids:** UI built on unstable backend contracts

### Phase Ordering Rationale

- Phase 1 before all others because data structures are the foundation; dual-engine resolution unblocks everything.
- Phase 2 before Phase 3 because tactical AI must know which units are NPC/uncontrolled — that requires the command chain to be live in the tick loop.
- Phase 3 before Phase 4 because strategic AI operation planning feeds the `MissionObjective` that tactical AI reads; building the consumer before the producer wastes effort.
- Phase 5 before Phase 6 because broadcast payload format must be stable before frontend subscription code is written.
- Phase 6 last because all backend DTOs and channel topics must be finalized.

### Research Flags

Phases likely needing deeper research during planning:
- **Phase 1 (engine unification):** The exact model differences between `TacticalCombatEngine.TacticalUnit` and `TacticalBattleEngine.TacticalUnit` require a field-by-field comparison to scope the port accurately. Porting scope is unclear without a detailed diff of both engines. Recommend running the diff at Phase 1 kickoff before task estimation.
- **Phase 2 (WebSocket authority + concurrency):** The interaction between the command buffer pattern, Spring STOMP's `@MessageMapping` task executor, and the existing `ConcurrentHashMap<Long, TacticalBattleState>` needs concurrency design review. Whether command queue drain should happen on the tick thread or the STOMP dispatcher thread has correctness implications.

Phases with standard patterns (skip research-phase):
- **Phase 3 (Tactical AI):** The utility scoring pattern is proven in `UtilityScorer.kt`. Tiered AI frequency is a well-documented game-dev pattern. Extension is mechanical.
- **Phase 4 (Strategic AI):** `FactionAI` and `OfficerAI` infrastructure exists. Auto-operation-planning is a rule-based conditional extension.
- **Phase 5 (Broadcast optimization):** Delta broadcasting and fog-of-war filtering are well-documented. The implementation follows the existing `detectionMatrix` pattern.
- **Phase 6 (Frontend):** Circle rendering in R3F and Konva follow existing patterns in `CommandRangeCircle.tsx` and `BattleMap.tsx`.

---

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Verified against actual `build.gradle.kts` and `package.json`. No new dependencies confirmed by direct code analysis. |
| Features | HIGH | gin7 manual (101-page PDF) is the primary source. Pages 37-38, 45-47 directly specify command range circle, operation plans, unit command distribution priority. Codebase cross-referenced for what already exists vs. what is missing. |
| Architecture | HIGH | Based on direct analysis of 15+ production Kotlin files. Component boundaries derived from what is actually wired in `TickEngine`, `TacticalBattleService`, and `TacticalBattleEngine`. Dual-engine finding is a concrete structural observation, not inference. |
| Pitfalls | HIGH | Dual-engine problem is a concrete structural finding. Race condition is observable from the `ConcurrentHashMap` + direct mutation pattern. Broadcast storm is calculable from unit count * field count * subscriber count. Performance estimates based on actual unit counts (60 per fleet, 120 per battle). |

**Overall confidence:** HIGH

### Gaps to Address

- **Engine merge scope:** Exact field differences between `TacticalCombatEngine.TacticalUnit` and `TacticalBattleEngine.TacticalUnit` need a diff before Phase 1 estimation is accurate. Run the comparison at Phase 1 kickoff.
- **OperationPlan entity design:** `OperationPlanCommand` currently stores plan metadata in `nation.meta` JSONB (not a first-class entity). Phase 1 must decide: new `operation_plan` table (cleaner for querying, requires migration) vs. enriched JSONB structure (less migration, harder to query). Recommend new table.
- **CRC rendering layer:** Frontend uses both `BattleMap.tsx` (SVG/Konva) and `TacticalMapR3F.tsx` (Three.js/R3F). `CommandRangeCircle.tsx` is a Konva component. The authoritative tactical map rendering layer must be confirmed before Phase 6 UI work to avoid building circles in the wrong renderer.
- **Online player detection for succession priority:** gin7 succession priority is (online > rank > evaluation > merit). `TacticalBattleState` does not currently track which player `officerId` values are connected via WebSocket. A `connectedOfficerIds: Set<Long>` field must be maintained in `TacticalBattleService` and passed to `CommandChainManager.processDestruction()`.

---

## Sources

### Primary (HIGH confidence)
- **gin7 Official Manual (101-page PDF)** — p.37-38 (operation plans: purpose/scale/duration/merit bonuses), p.45-47 (tactical battle entry, command range circle mechanics, unit command distribution, assignment priority) — authoritative gin7 domain rules
- **Direct codebase analysis** — `TacticalBattleEngine.kt`, `TacticalCombatEngine.kt`, `TacticalBattleService.kt`, `BattleWebSocketController.kt`, `CommandRange.kt`, `CommandAuthority.kt`, `UtilityScorer.kt`, `PersonalityTrait.kt`, `AiCommandBridge.kt`, `FactionAI.kt`, `OfficerAI.kt`, `TickEngine.kt`, `BattleTriggerService.kt`, `UnitCrew.kt`, `CrewSlotRole.kt`, `UnitType.kt`, `tactical.ts`, `CommandRangeCircle.tsx` — all HIGH confidence, directly read
- **v2.1 milestone scope** (project memory `project_v21_milestone_scope.md`) — feature requirements

### Secondary (MEDIUM confidence)
- [Chain of command in cooperative agents for RTS games](https://link.springer.com/article/10.1007/s40692-018-0119-8) — hierarchical AI command patterns, command depth latency effects
- [Hierarchical control of multi-agent RL in RTS games](https://www.sciencedirect.com/science/article/abs/pii/S0957417421010897) — multi-level decision making, strategic-to-tactical handoff
- [Total War Shogun 2 Morale System](https://shogun2-encyclopedia.com/how_to_play/052_enc_manual_battle_conflict_morale.html) — command radius mechanics, general death and succession patterns
- [Spring WebSocket STOMP scaling](https://websocket.org/guides/frameworks/spring-boot/) — broadcast storm prevention, GC tuning for message objects

### Tertiary (LOW confidence)
- [RTS devlog: Optimizing for 1000 units](https://www.gamedev.net/blogs/entry/2274556-rts-devlog-7-optimizing-performance-for-1000-units/) — spatial partitioning strategies (LOW: designed for 10x the unit count of this project)
- [AI for large numbers of RTS units](https://gamedev.net/forums/topic/698291-ai-for-large-numbers-of-rts-like-units/) — tiered AI frequency patterns (LOW: scaled for larger unit counts than 120)

---

*Research completed: 2026-04-07*
*Ready for roadmap: yes*
