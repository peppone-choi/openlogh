# Codebase Concerns

**Analysis Date:** 2026-04-05

## Tech Debt

### OpenSamguk Legacy Entity Names (Critical - Blocks LOGH Domain)

- Issue: All core entities still use Three Kingdoms naming (`General`, `City`, `Nation`, `Troop`, `Emperor`) instead of LOGH domain names (`Officer`, `Planet`, `Faction`, `Fleet`, `Sovereign`). DB tables match (`general`, `city`, `nation`, `troop`, `emperor`). The CLAUDE.md domain mapping exists but is not applied.
- Files:
  - `backend/game-app/src/main/kotlin/com/openlogh/entity/General.kt`
  - `backend/game-app/src/main/kotlin/com/openlogh/entity/City.kt`
  - `backend/game-app/src/main/kotlin/com/openlogh/entity/Nation.kt`
  - `backend/game-app/src/main/kotlin/com/openlogh/entity/Troop.kt`
  - `backend/game-app/src/main/kotlin/com/openlogh/entity/Emperor.kt`
  - `backend/game-app/src/main/kotlin/com/openlogh/entity/WorldState.kt` (should be `SessionState`)
  - `frontend/src/types/index.ts` (all TS interfaces use legacy names)
- Impact: Every future feature must mentally map between two naming systems. New developers will be confused. The LOGH theme is invisible in code.
- Fix approach: Rename entities in stages: (1) Add `@Table(name = "general")` to preserve DB compatibility while renaming Kotlin classes. (2) Create Flyway migrations to rename tables. (3) Rename TS types with aliased re-exports for backward compat.

### 5-Stat System Still Active (Critical - Blocks gin7 Mechanics)

- Issue: `General` entity uses the OpenSamguk 5-stat system (`leadership`, `strength`, `intel`, `politics`, `charm`) with 5 dex fields (`dex1`-`dex5` for footman/archer/cavalry/wizard/siege). LOGH requires 8 stats (`leadership`, `command`, `intelligence`, `politics`, `administration`, `mobility`, `attack`, `defense`) and different dex categories.
- Files:
  - `backend/game-app/src/main/kotlin/com/openlogh/entity/General.kt` (lines 55-95)
  - `backend/game-app/src/main/kotlin/com/openlogh/engine/turn/cqrs/memory/InMemoryWorldState.kt` (`GeneralSnapshot` duplicates all 5-stat fields)
  - `frontend/src/types/index.ts` (lines 126-211, comment explicitly says "5-stat system")
  - `frontend/src/components/game/general-basic-card.tsx` (renders 5 stats and 5 dex types)
  - `backend/game-app/src/main/kotlin/com/openlogh/engine/war/WarUnit.kt` (uses `leadership`, `strength`, `intel`)
  - `backend/game-app/src/main/kotlin/com/openlogh/engine/war/BattleEngine.kt` (combat formulas based on 5-stat)
  - `backend/game-app/src/main/kotlin/com/openlogh/engine/war/WarFormula.kt` (dex thresholds for 5 arm types)
- Impact: All combat, domestic, and AI logic is built around 5 stats. Adding 3 new stats requires schema migration, formula rebalancing, and full frontend UI rework.
- Fix approach: (1) Add new stat columns via Flyway migration with defaults. (2) Implement stat mapping layer that translates 5-stat to 8-stat for backward compat during transition. (3) Update formulas incrementally.

### CrewType Enum is Three Kingdoms Military (Critical - Blocks LOGH Theme)

- Issue: `CrewType` enum defines 39 Three Kingdoms troop types (보병/궁병/기병/귀병/차병) with Chinese city/region requirements (`낙양`, `성도`, `흉노`). LOGH needs ship classes (battleship/cruiser/destroyer/carrier/transport/hospital/fortress).
- Files:
  - `backend/game-app/src/main/kotlin/com/openlogh/model/CrewType.kt` (528 lines, entire file)
  - `backend/game-app/src/main/kotlin/com/openlogh/engine/CrewTypeAvailability.kt`
  - `frontend/src/components/game/crew-type-browser.tsx`
- Impact: The entire combat system, recruitment, and tech tree is built around medieval army composition. Replacing with space fleet ship classes requires complete rewrite of `CrewType`, all battle formulas, and all related UI.
- Fix approach: Create new `ShipClass` enum parallel to `CrewType`. Implement adapter during transition. Replace progressively.

### OpenSamguk Resource Naming Throughout Codebase

- Issue: Resources use Three Kingdoms names: `gold`/`rice` (should be `funds`/`supplies`), `crew` (should be `ships`), `crewType` (should be `shipClass`), `atmos` (should be `morale`), `train` (should be `training`). These names permeate entities, DTOs, services, commands, and frontend.
- Files:
  - `backend/game-app/src/main/kotlin/com/openlogh/entity/General.kt` (lines 118-133)
  - `backend/game-app/src/main/kotlin/com/openlogh/entity/City.kt` (agri, comm, secu, trust, def, wall)
  - `backend/game-app/src/main/kotlin/com/openlogh/entity/Nation.kt` (gold, rice, bill, rate)
  - `frontend/src/types/index.ts` (331+ occurrences of legacy resource names across 20 files)
  - All 55+ general commands in `backend/game-app/src/main/kotlin/com/openlogh/command/general/`
  - All 38+ nation commands in `backend/game-app/src/main/kotlin/com/openlogh/command/nation/`
- Impact: 331+ frontend occurrences and all backend services reference legacy names. Renaming is a massive cross-cutting change.
- Fix approach: Add field aliases in entities (Kotlin `@get:JsonProperty`). Migrate DTOs to use new names. Frontend can use type aliases during transition.

### Korean Command Names Hardcoded as Registry Keys

- Issue: All 93 commands are registered with Korean Three Kingdoms names (e.g., `농지개간`, `출병`, `선전포고`). These serve as both internal keys and user-facing labels. LOGH needs space-themed command names.
- Files:
  - `backend/game-app/src/main/kotlin/com/openlogh/command/CommandRegistry.kt` (178 lines)
  - `backend/game-app/src/main/kotlin/com/openlogh/command/general/*.kt` (55+ command files with Korean filenames)
  - `backend/game-app/src/main/kotlin/com/openlogh/command/nation/*.kt` (38+ command files)
- Impact: Command keys are used in database (`GeneralTurn.actionCode`, `NationTurn.actionCode`), WebSocket messages, frontend routing, and AI decision logic. Changing keys requires data migration.
- Fix approach: Add a command code aliasing layer. Map legacy Korean keys to new LOGH command codes. Keep backward compat via alias lookup.

### Emperor Entity is Denormalized Flat Table

- Issue: `Emperor` entity has 143 lines of flat string columns (`l5name`, `l5pic`, `l6name`, `l6pic`... through `l12name`, `l12pic`) plus `tiger`, `eagle`, `gen` as strings. This is a legacy PHP-era data dump pattern.
- Files:
  - `backend/game-app/src/main/kotlin/com/openlogh/entity/Emperor.kt`
- Impact: Extremely rigid schema. Adding new officer levels or changing the structure requires migration. No type safety on the l-prefix columns.
- Fix approach: Normalize into a `sovereign_officer` join table or use JSONB for the officer hierarchy.

## Known Bugs

### InMemoryTurnProcessor Does Not Execute Commands

- Symptoms: The CQRS in-memory turn processor (`executeGeneralCommandsUntil`) only dequeues command turns and updates `turnTime` but does NOT actually execute the command logic. It sets `lastTurn` with `"queuedInMemory" to true` but never calls `CommandExecutor`.
- Files:
  - `backend/game-app/src/main/kotlin/com/openlogh/engine/turn/cqrs/memory/InMemoryTurnProcessor.kt` (lines 203-297)
- Trigger: Enable CQRS mode (`opensam.cqrs.enabled=true`)
- Workaround: CQRS mode is disabled by default (`opensam.cqrs.enabled:false` in `TurnDaemon.kt` line 23). The legacy `TurnService` path is used.

### Incomplete Battle Trigger TODOs

- Symptoms: Several combat special abilities are stubbed with TODO comments and do not function.
- Files:
  - `backend/game-app/src/main/kotlin/com/openlogh/engine/modifier/SpecialModifiers.kt` (lines 301, 376, 398, 402, 422, 423)
- Trigger: Using officers with `위압`, `저격`, `격노`, `반목`, `무쌍` special abilities
- Workaround: These abilities are silently ignored in combat.

## Security Considerations

### Admin Endpoints Lack Consistent Authorization

- Risk: `LegacyParityController` endpoints like `/api/generals/{generalId}/logs/old` and `/api/generals/{generalId}/simulator-export` accept `generalId` as path variable without verifying the caller owns that general. The `/api/admin/raise-event` checks `loginId` but authorization is delegated to `AdminEventService`.
- Files:
  - `backend/game-app/src/main/kotlin/com/openlogh/controller/LegacyParityController.kt` (lines 38-46, 62-68)
- Current mitigation: Spring Security filters on the gateway layer may restrict access, but game-app endpoints assume trusted proxying.
- Recommendations: Add ownership verification for general-specific endpoints. Ensure admin endpoints have role checks at the controller level.

### Untyped JSONB Meta Fields

- Risk: All entities (`General.meta`, `City.meta`, `Nation.meta`, `Nation.spy`, `WorldState.config`) use `MutableMap<String, Any>` for JSONB columns. Values are cast with `as?` throughout the codebase. A malicious or buggy client could inject unexpected types.
- Files:
  - Every entity in `backend/game-app/src/main/kotlin/com/openlogh/entity/`
  - `backend/game-app/src/main/kotlin/com/openlogh/command/CommandExecutor.kt` (heavy use of `readIntValue`, `readStringAnyMap`, `readBooleanValue` helpers)
  - `backend/game-app/src/main/kotlin/com/openlogh/engine/TurnDaemon.kt` (reads `gatewayActive`, `locked`, `opentime` from untyped meta)
- Current mitigation: Defensive `as?` casts with null fallbacks. Many helper functions for type coercion.
- Recommendations: Define typed data classes for meta payloads. Use Jackson deserialization instead of `Map<String, Any>`.

### Token in SessionStorage

- Risk: Frontend stores JWT in SessionStorage, which is accessible to any JavaScript on the same origin (XSS vector).
- Files:
  - `frontend/src/` (SessionStorage-based auth pattern)
- Current mitigation: CSP headers not observed in config. XSS prevention relies on React's default escaping.
- Recommendations: Consider HttpOnly cookies for token storage. Add CSP headers.

## Performance Bottlenecks

### buildConstraintEnv Loads Entire World State Per Command

- Problem: Every command execution calls `buildConstraintEnv` which loads ALL cities, ALL generals, and ALL diplomacies via `ports.allCities()`, `ports.allGenerals()`, `ports.activeDiplomacies()`.
- Files:
  - `backend/game-app/src/main/kotlin/com/openlogh/command/CommandExecutor.kt` (lines 398-478)
- Cause: The constraint system needs global context (map adjacency, nation memberships, war status). Each call triggers multiple `SELECT * FROM general WHERE world_id = ?` queries.
- Improvement path: Cache the constraint environment per turn tick (it only changes between turns). Use the in-memory CQRS world state when available.

### AuthService iterates ALL users for admin operations

- Problem: `AuthService` has methods that call `userRepository.findAll()` in loops.
- Files:
  - `backend/game-app/src/main/kotlin/com/openlogh/service/AuthService.kt` (lines 470, 487, 503)
  - `backend/game-app/src/main/kotlin/com/openlogh/service/AdminService.kt` (line 382)
- Cause: Admin functions that scan all users without pagination.
- Improvement path: Add pagination, or batch processing for admin operations.

### GeneralAI is 4,109 Lines

- Problem: `GeneralAI.kt` is the largest file in the codebase at 4,109 lines. Contains all NPC decision logic in a single class.
- Files:
  - `backend/game-app/src/main/kotlin/com/openlogh/engine/ai/GeneralAI.kt` (4,109 lines)
- Cause: Direct port from PHP `GeneralAI.php`. No decomposition into strategy pattern or sub-modules.
- Improvement path: Extract into strategy classes per NPC behavior type (domestic, military, diplomatic, resource management).

## Fragile Areas

### In-Memory Turn Processing State

- Files:
  - `backend/game-app/src/main/kotlin/com/openlogh/engine/turn/cqrs/memory/InMemoryWorldState.kt`
  - `backend/game-app/src/main/kotlin/com/openlogh/engine/turn/cqrs/memory/InMemoryTurnProcessor.kt`
  - `backend/game-app/src/main/kotlin/com/openlogh/engine/turn/cqrs/TurnCoordinator.kt`
- Why fragile: The `InMemoryWorldState` holds the entire game state as mutable data classes in memory. The `DirtyTracker` pattern tracks changes for persistence. If any step in the pipeline throws after mutations but before persist, state is partially corrupted. The `TurnCoordinator` catches the top-level exception but does not roll back in-memory changes.
- Safe modification: Always test turn processing with full integration tests. Never add mutable shared state without dirty tracking.
- Test coverage: `TurnCoordinatorTest` exists but CQRS path is disabled in production.

### TurnDaemon Single-Thread Sequential Processing

- Files:
  - `backend/game-app/src/main/kotlin/com/openlogh/engine/TurnDaemon.kt`
- Why fragile: `TurnDaemon.tick()` processes ALL worlds sequentially in a single `@Scheduled` thread. The `@Volatile` state variables (`state`, `stateReason`, `requestId`) provide visibility but no atomicity for compound state transitions. If one world's turn takes too long, all other worlds stall.
- Safe modification: The `state != DaemonState.IDLE` guard prevents re-entry but `manualRun()` could race with `tick()`.
- Test coverage: `TurnDaemonTest` covers basic flows. No concurrency tests.

### Command Result JSON Message Parsing

- Files:
  - `backend/game-app/src/main/kotlin/com/openlogh/command/CommandExecutor.kt` (lines 480-584, `ensureNationContextForFounding`)
- Why fragile: Command results pass a `message` string that is parsed as JSON to extract nation-founding side effects. This is a stringly-typed protocol between commands and the executor. Any malformed JSON silently fails via `runCatching`.
- Safe modification: Replace string-based message passing with typed sealed classes for command side effects.
- Test coverage: Limited. The JSON parsing failure path is silently swallowed.

### BattleEngine 965-Line Monolith

- Files:
  - `backend/game-app/src/main/kotlin/com/openlogh/engine/war/BattleEngine.kt` (965 lines)
  - `backend/game-app/src/main/kotlin/com/openlogh/engine/war/BattleTrigger.kt` (917 lines)
  - `backend/game-app/src/main/kotlin/com/openlogh/engine/war/BattleService.kt` (731 lines)
- Why fragile: Battle resolution is a deeply nested loop with stateful mutation on `WarUnit` objects. Trigger processing relies on side-effect chains. Changing any formula affects all combat outcomes.
- Safe modification: Use the existing `BattleSimService` to regression-test any formula changes. Run the `DuelSimulationTest` after modifications.
- Test coverage: `BattleServiceTest`, `DuelSimulationTest`, `FieldBattleTriggerTest`, `RageTriggerTest` exist. Coverage is moderate.

## Scaling Limits

### Single JVM Per World

- Current capacity: One game-app JVM process handles one or more worlds sequentially via `TurnDaemon`.
- Limit: At 2,000 concurrent players per session (target from CLAUDE.md), a single JVM may not handle WebSocket connections and turn processing simultaneously.
- Scaling path: The gateway-app + game-app split exists but game-app instances are spawned per `commitSha`, not per world. Need per-world isolation or horizontal scaling.

### Full World Load for Constraint Checks

- Current capacity: Works for small worlds (< 500 generals, < 100 cities).
- Limit: Loading all generals and cities into memory per command execution will degrade linearly with world size.
- Scaling path: Implement the CQRS in-memory path fully (currently incomplete). Cache constraint environments per turn.

## Dependencies at Risk

### Dual Turn Processing Paths

- Risk: Two parallel turn processing implementations exist: legacy `TurnService` (1,306 lines) and CQRS `TurnCoordinator` + `InMemoryTurnProcessor`. The CQRS path is incomplete (does not execute commands). Both must be maintained or one must be deprecated.
- Files:
  - `backend/game-app/src/main/kotlin/com/openlogh/engine/TurnService.kt` (1,306 lines, active)
  - `backend/game-app/src/main/kotlin/com/openlogh/engine/turn/cqrs/` (entire directory, inactive)
- Impact: Any turn logic change must consider both paths. Risk of divergence.
- Migration plan: Complete the CQRS path or remove it. The `TurnPipeline` + `TurnStep` pattern in the CQRS path is cleaner but non-functional for command execution.

### `@Suppress("UNCHECKED_CAST")` Proliferation

- Risk: 60+ occurrences of `@Suppress("UNCHECKED_CAST")` across test and production code, indicating widespread use of untyped maps and generic casts.
- Files:
  - `backend/game-app/src/main/kotlin/com/openlogh/service/CommandService.kt` (line 545)
  - `backend/game-app/src/main/kotlin/com/openlogh/engine/ai/GeneralAI.kt` (lines 1379-1383)
  - `backend/game-app/src/main/kotlin/com/openlogh/service/ScenarioService.kt` (line 985)
  - 50+ test files
- Impact: Runtime ClassCastException risk. Makes refactoring harder since the compiler cannot catch type mismatches.
- Migration plan: Replace `Map<String, Any>` patterns with typed data classes. Use sealed interfaces for command args.

## Missing Critical Features

### No Real-Time Battle System (WebSocket RTS)

- Problem: CLAUDE.md specifies real-time tactical fleet battles via WebSocket, but the current combat system is fully turn-based (`BattleEngine.resolveBattle` is synchronous).
- Blocks: The gin7 core gameplay of real-time fleet combat with energy allocation and formations.
- Files:
  - `backend/game-app/src/main/kotlin/com/openlogh/engine/war/BattleEngine.kt` (synchronous turn-based)
  - No WebSocket battle controller exists

### No Position Card / Command Point System (gin7 Core)

- Problem: The gin7 command system uses Position Cards (직무권한카드) to gate what actions a player can take based on rank. The current system has `officerLevel` and `permission` fields but no card-based gating.
- Blocks: The core gin7 organizational simulation where rank determines available actions.
- Files:
  - `backend/game-app/src/main/kotlin/com/openlogh/entity/General.kt` (`officerLevel`, `permission` fields exist but no card system)
  - `backend/game-app/src/main/kotlin/com/openlogh/command/constraint/ConstraintHelper.kt` (1,362 lines of constraints, but no card-based checks)

### No Fleet Organization Structure

- Problem: gin7 has complex fleet organization (60 units per fleet, 10 officer slots, patrol/transport/ground units). Current `Troop` entity is a minimal wrapper with leader + name + meta.
- Blocks: Multi-player fleet coordination, chain of command, officer assignment.
- Files:
  - `backend/game-app/src/main/kotlin/com/openlogh/entity/Troop.kt` (33 lines, minimal)

### No Ship Energy Allocation System

- Problem: gin7 combat requires energy allocation across BEAM/GUN/SHIELD/ENGINE/WARP/SENSOR. No such system exists.
- Blocks: Tactical depth in fleet combat.

## Test Coverage Gaps

### Command Execution Logic

- What's not tested: Individual command files (55 general + 38 nation) have no dedicated unit tests. Testing occurs only through integration-level `CommandServiceTest`.
- Files: All files in `backend/game-app/src/main/kotlin/com/openlogh/command/general/*.kt` and `backend/game-app/src/main/kotlin/com/openlogh/command/nation/*.kt`
- Risk: Command formula changes could silently break game balance.
- Priority: Medium (covered by integration tests)

### CQRS Turn Path

- What's not tested: The `InMemoryTurnProcessor` command execution is a no-op, so there are no tests validating actual command execution through the CQRS path.
- Files: `backend/game-app/src/main/kotlin/com/openlogh/engine/turn/cqrs/memory/InMemoryTurnProcessor.kt`
- Risk: Enabling CQRS mode would silently break all command processing.
- Priority: High (blocks CQRS adoption)

### Frontend Game Components

- What's not tested: Most game components have test files but tests are shallow (render-only, no interaction). Map 3D components have minimal test coverage.
- Files:
  - `frontend/src/components/game/map-3d/` (only `CastleLoader.test.ts` and `CityModel.test.ts`)
  - `frontend/src/components/game/game-dashboard.test.ts` and `game-dashboard.test.tsx` (duplicate test files)
- Risk: UI regressions during LOGH theme conversion.
- Priority: Low (UI is expected to be rewritten)

---

*Concerns audit: 2026-04-05*
