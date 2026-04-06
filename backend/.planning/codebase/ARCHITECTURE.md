# Architecture

**Analysis Date:** 2026-04-05

## Pattern Overview

**Overall:** Multi-process gateway + versioned game-app architecture with CQRS turn engine

**Key Characteristics:**
- Gateway-app (port 8080) orchestrates and proxies to one or more game-app JVM instances (ports 9001-9999)
- Each game world (`WorldState`) is assigned to a specific game-app process by commit SHA
- Turn-based strategic mode with scheduled daemon + optional real-time command-point mode
- Command pattern with constraint validation for all player actions
- CQRS in-memory turn processing with dirty-tracking and bulk persistence
- WebSocket (STOMP over SockJS) for real-time event broadcasting

## Layers

**Gateway Layer (gateway-app):**
- Purpose: HTTP API gateway, authentication, world lifecycle management, process orchestration, reverse proxy
- Location: `backend/gateway-app/src/main/kotlin/com/openlogh/gateway/`
- Contains: Auth controllers, world management, OAuth, admin endpoints, game-app proxy
- Depends on: PostgreSQL (AppUser, WorldState, SystemSetting entities), game-app instances (HTTP proxy)
- Used by: Frontend clients, admin dashboard

**Game Logic Layer (game-app):**
- Purpose: Core game engine - turn processing, command execution, battle system, AI, event system
- Location: `backend/game-app/src/main/kotlin/com/openlogh/`
- Contains: Commands, engine services, entities, repositories, controllers, WebSocket handlers
- Depends on: PostgreSQL (all game entities), Redis (cache), shared library
- Used by: Gateway (reverse proxy), Frontend (WebSocket direct)

**Shared Library (shared):**
- Purpose: Cross-process DTOs, JWT verification, error definitions, game constants
- Location: `backend/shared/src/main/kotlin/com/openlogh/`
- Contains: `shared/dto/` (AccountDtos, AuthDtos, WorldDtos, JwtUserPrincipal), `shared/error/ErrorResponse.kt`, `shared/security/JwtTokenVerifier.kt`, `shared/GameConstants.kt`, `shared/model/ScenarioData.kt`, `util/JosaUtil.kt`
- Depends on: Nothing (pure library)
- Used by: gateway-app, game-app

**Frontend Layer:**
- Purpose: Browser UI for lobby, game management, real-time game display
- Location: `frontend/src/`
- Contains: Next.js App Router pages, React components, Zustand stores, API client, WebSocket integration
- Depends on: Backend APIs (via gateway), WebSocket (via game-app through gateway)
- Used by: Browser clients

## Data Flow

**Request Routing (Frontend -> Game Logic):**

1. Frontend sends HTTP request to gateway (port 8080)
2. Gateway explicit controllers handle auth, world management, admin routes
3. Unmatched `/api/**` requests are caught by `GameApiCatchAllController` (`backend/gateway-app/src/main/kotlin/com/openlogh/gateway/config/GameProxyFilter.kt`)
4. Controller looks up game-app base URL from `WorldRouteRegistry` (`backend/gateway-app/src/main/kotlin/com/openlogh/gateway/service/WorldRouteRegistry.kt`)
5. Request is proxied to game-app via Spring WebClient (WebFlux reactive HTTP client)
6. Game-app controller processes request and returns response

**World Activation Flow:**

1. Gateway starts, `WorldActivationBootstrap` (`backend/gateway-app/src/main/kotlin/com/openlogh/gateway/orchestrator/WorldActivationBootstrap.kt`) runs
2. Loads all active `WorldState` records from PostgreSQL
3. For each world, `GameProcessOrchestrator` (`backend/gateway-app/src/main/kotlin/com/openlogh/gateway/orchestrator/GameProcessOrchestrator.kt`) spawns game-app JVM process
4. Process started with `java -jar artifacts/game-app-{commitSha}.jar --server.port={port}`
5. Health check polling on `/internal/health` until ready (up to 120s)
6. Port registered in `WorldRouteRegistry` as `worldId -> http://127.0.0.1:{port}`
7. World IDs attached to the instance, proxy routing enabled

**Command Execution Pipeline:**

1. Player submits command via REST API or WebSocket
2. `CommandService` (`backend/game-app/src/main/kotlin/com/openlogh/service/CommandService.kt`) receives command
3. `CommandExecutor` (`backend/game-app/src/main/kotlin/com/openlogh/command/CommandExecutor.kt`) orchestrates execution:
   a. `CommandRegistry` (`backend/game-app/src/main/kotlin/com/openlogh/command/CommandRegistry.kt`) looks up factory by action code (Korean string key)
   b. `ArgSchema` validates input arguments
   c. Cooldown check (per-general or per-nation meta map, keyed by turn index)
   d. `BaseCommand.checkFullCondition()` runs constraint chain (`backend/game-app/src/main/kotlin/com/openlogh/command/constraint/`)
   e. Pre-req turn check (multi-turn commands accumulate term count)
   f. `command.run(rng)` executes the action, returns `CommandResult` with JSON delta
   g. `CommandResultApplicator` applies delta to entities (General, City, Nation)
   h. `StatChangeService.checkStatChange()` post-hook
   i. Modified entities saved via `JpaWorldPorts` snapshot pattern
   j. Post-req cooldown applied

**Turn Processing (Scheduled Daemon):**

1. `TurnDaemon` (`backend/game-app/src/main/kotlin/com/openlogh/engine/TurnDaemon.kt`) ticks every 5s (configurable)
2. Loads all `WorldState` records matching this process's `commitSha`
3. Filters: skip pre-open, locked, and inactive worlds
4. For each world, either:
   - **Turn-based mode**: `TurnCoordinator` (CQRS) or legacy `TurnService`
   - **Realtime mode**: `RealtimeService` processes completed commands and regenerates CP

**CQRS Turn Processing (TurnCoordinator):**

1. `TurnCoordinator` (`backend/game-app/src/main/kotlin/com/openlogh/engine/turn/cqrs/TurnCoordinator.kt`) manages lifecycle: LOADING -> PROCESSING -> PERSISTING -> PUBLISHING -> IDLE
2. **LOADING**: `WorldStateLoader` loads all entities into `InMemoryWorldState` (`backend/game-app/src/main/kotlin/com/openlogh/engine/turn/cqrs/memory/InMemoryWorldState.kt`) - snapshot data classes (GeneralSnapshot, CitySnapshot, NationSnapshot, etc.)
3. **PROCESSING**: `InMemoryTurnProcessor` wraps `TurnPipeline` execution with `InMemoryWorldPorts` + `DirtyTracker`
4. **PERSISTING**: `WorldStatePersister` bulk-writes only dirty entities via `JpaBulkWriter`
5. **PUBLISHING**: Broadcasts turn advance events via `GameEventService`

**Turn Pipeline Steps (ordered):**

The `TurnPipeline` (`backend/game-app/src/main/kotlin/com/openlogh/engine/turn/TurnPipeline.kt`) collects all `TurnStep` beans and executes them by `order`. Steps are located at `backend/game-app/src/main/kotlin/com/openlogh/engine/turn/steps/`:
- `AdvanceMonthStep` - Advance game calendar
- `PreMonthEventStep` - Pre-month scheduled events
- `ExecuteGeneralCommandsStep` - Execute all queued general commands
- `EconomyPreUpdateStep` / `EconomyPostUpdateStep` - Economic calculations
- `DiplomacyStep` - Diplomacy state transitions
- `DisasterAndTradeStep` - Random disasters and trade route updates
- `GeneralMaintenanceStep` - Officer aging, injury recovery, NPC lifecycle
- `MonthEventStep` - Scheduled game events
- `OnlineOverheadStep` - Online player overhead
- `StrategicLimitResetStep` - Reset strategic command limits
- `UnificationCheckStep` - Victory condition checks
- `WarFrontRecalcStep` - War front state recalculation
- `TrafficSnapshotStep` / `WorldSnapshotStep` / `YearbookSnapshotStep` / `YearlyStatisticsStep` - Record keeping

Each step is fault-tolerant: failures are logged but don't halt the pipeline (legacy parity).

**State Management (Frontend):**

- `useAuthStore` (`frontend/src/stores/authStore.ts`) - JWT token, user info, login/logout
- `useWorldStore` (`frontend/src/stores/worldStore.ts`) - Current world selection, world list
- `useGeneralStore` (`frontend/src/stores/generalStore.ts`) - Player's general data
- `useGameStore` (`frontend/src/stores/gameStore.ts`) - Cities, nations, generals, diplomacy, map data (bulk load)
- `useTutorialStore` (`frontend/src/stores/tutorialStore.ts`) - Tutorial flow state

## Key Abstractions

**WorldState (Session Container):**
- Purpose: Container for an entire game instance with independent state
- Examples: `backend/game-app/src/main/kotlin/com/openlogh/entity/WorldState.kt`
- Pattern: Each world has `id`, `commitSha`, `currentYear`/`currentMonth`, `tickSeconds`, `realtimeMode`, JSONB `config` and `meta` maps. All game entities reference `worldId` FK for isolation.

**General (Officer/Player Character):**
- Purpose: Controllable character unit - player or NPC
- Examples: `backend/game-app/src/main/kotlin/com/openlogh/entity/General.kt`
- Pattern: Links to Nation (`nationId`), City (`cityId`), Troop (`troopId`). Carries 5 stats (leadership, strength, intel, politics, charm), crew/army data, equipment codes, experience system, JSONB `meta`/`lastTurn`/`penalty` maps.

**Nation (Faction):**
- Purpose: Political entity controlling territories
- Examples: `backend/game-app/src/main/kotlin/com/openlogh/entity/Nation.kt`
- Pattern: Has `capitalCityId`, `chiefGeneralId` (leader), resources (gold, rice), tax/rate settings, tech level, type code. JSONB `spy` and `meta` maps.

**City (Territory):**
- Purpose: Territorial unit with resources, population, defenses
- Examples: `backend/game-app/src/main/kotlin/com/openlogh/entity/City.kt`
- Pattern: Owned by Nation (`nationId`), has `mapCityId` for map topology, resource stats (pop, agri, comm, secu, def, wall), JSONB `conflict` and `meta` maps.

**Command (Player Action):**
- Purpose: Encapsulates a single player action with validation and execution
- Base class: `backend/game-app/src/main/kotlin/com/openlogh/command/BaseCommand.kt`
- General commands: `backend/game-app/src/main/kotlin/com/openlogh/command/GeneralCommand.kt` (55 registered)
- Nation commands: `backend/game-app/src/main/kotlin/com/openlogh/command/NationCommand.kt` (38 registered)
- Pattern: Factory-registered in `CommandRegistry` with Korean action code keys (e.g., "출병", "농지개간"). Each command defines constraints, cost, pre/post-req turns, and `run()` method returning `CommandResult` with JSON delta message.

**Constraint (Validation Rule):**
- Purpose: Reusable validation predicate for command conditions
- Examples: `backend/game-app/src/main/kotlin/com/openlogh/command/constraint/Constraint.kt`, `ConstraintChain.kt`
- Pattern: `Constraint.test(ConstraintContext) -> ConstraintResult (Pass | Fail)`. Commands compose constraints in `fullConditionConstraints` list.

**TurnStep (Pipeline Stage):**
- Purpose: Single step in monthly turn processing
- Interface: `backend/game-app/src/main/kotlin/com/openlogh/engine/turn/TurnStep.kt`
- Implementations: `backend/game-app/src/main/kotlin/com/openlogh/engine/turn/steps/`
- Pattern: Spring `@Component` with `order` property. Auto-collected by `TurnPipeline` and sorted.

**InMemoryWorldState (CQRS Snapshot):**
- Purpose: In-memory copy of all game entities for a world during turn processing
- Examples: `backend/game-app/src/main/kotlin/com/openlogh/engine/turn/cqrs/memory/InMemoryWorldState.kt`
- Pattern: Contains `MutableMap<Long, XxxSnapshot>` for generals, cities, nations, troops, diplomacies. `DirtyTracker` records mutations. Only dirty entities are bulk-persisted after processing.

**ActionModifier (Stat/Effect Modifier):**
- Purpose: Modifies command calculations based on nation type, personality, items, specials
- Examples: `backend/game-app/src/main/kotlin/com/openlogh/engine/modifier/ActionModifier.kt`, `ModifierService.kt`
- Pattern: Stacked modifiers from multiple sources (item, nation type, personality, officer level, special, inherit buff). Injected into commands by `CommandExecutor`.

## Entry Points

**Gateway Application:**
- Location: `backend/gateway-app/src/main/kotlin/com/openlogh/gateway/GatewayApplication.kt`
- Triggers: Spring Boot startup (port 8080)
- Responsibilities: Auth, world lifecycle, admin, reverse proxy to game-app

**Game Application:**
- Location: `backend/game-app/src/main/kotlin/com/openlogh/OpensamApplication.kt`
- Triggers: Spawned by `GameProcessOrchestrator` as separate JVM (port 9001+)
- Responsibilities: Game logic, turn engine, command execution, WebSocket events

**Frontend Root Layout:**
- Location: `frontend/src/app/layout.tsx`
- Triggers: Browser navigation
- Responsibilities: Global providers, theme, fonts

**Frontend Game Layout:**
- Location: `frontend/src/app/(game)/layout.tsx`
- Triggers: Navigation to game routes
- Responsibilities: Auth guard, world/general store hydration, WebSocket connection, sidebar navigation, hotkeys

**TurnDaemon (Scheduler):**
- Location: `backend/game-app/src/main/kotlin/com/openlogh/engine/TurnDaemon.kt`
- Triggers: `@Scheduled(fixedDelay=5000ms)` - polls every 5 seconds
- Responsibilities: Iterate worlds, dispatch to TurnCoordinator or RealtimeService

**WorldActivationBootstrap:**
- Location: `backend/gateway-app/src/main/kotlin/com/openlogh/gateway/orchestrator/WorldActivationBootstrap.kt`
- Triggers: On gateway startup
- Responsibilities: Restore previously active worlds by spawning game-app instances

## Error Handling

**Strategy:** Layered error handling with fail-safe turn processing

**Patterns:**
- **Command Execution**: `CommandResult(success=false, logs=[...])` for business logic failures. Constraint validation returns `ConstraintResult.Fail(reason)`. Commands that fail fall back to "rest" action.
- **Turn Pipeline**: Each `TurnStep` is wrapped in try-catch. Failures are logged but pipeline continues to next step (legacy parity from daemon.ts).
- **CQRS Lifecycle**: `TurnCoordinator` transitions to `FAILED` state on exception, then resets to `IDLE`.
- **Gateway Proxy**: Returns HTTP 502 (Bad Gateway) when no game instance available or proxy error occurs.
- **API Layer**: `GlobalExceptionHandler` (`backend/game-app/src/main/kotlin/com/openlogh/config/GlobalExceptionHandler.kt`) catches exceptions and maps to HTTP responses.
- **Frontend**: Try-catch in async store actions, `console.error` logging, toast notifications for user-facing errors.

## Cross-Cutting Concerns

**Logging:**
- Backend: SLF4J via `LoggerFactory.getLogger()` pattern throughout
- Game-app stdout redirected to `logs/game-{commitSha}.log` by orchestrator
- Frontend: `console.error()` in catch blocks, toast notifications via Sonner

**Validation:**
- `ArgSchema` system (`backend/game-app/src/main/kotlin/com/openlogh/command/ArgSchema.kt`) validates command arguments
- `Constraint` chain validates game state preconditions per command
- `CommandGating` checks position card permissions
- JPA `@Column(nullable=false)` at database level
- Frontend: Zod schemas + React Hook Form for form validation

**Authentication:**
- Gateway: JWT token validation via `JwtAuthenticationFilter` -> `JwtUtil`
- Game-app: Separate `JwtAuthenticationFilter` -> `JwtUtil` (duplicated pattern)
- Shared: `JwtTokenVerifier` in shared module for cross-process verification
- Frontend: Token stored in SessionStorage via `authStore`, sent as `Authorization: Bearer` header
- OAuth: Kakao OAuth flow via `AccountOAuthService` / `AccountOAuthController`

**WebSocket Event Broadcasting:**
- `GameEventService` (`backend/game-app/src/main/kotlin/com/openlogh/service/GameEventService.kt`) is the central event hub
- Uses Spring `SimpMessagingTemplate` for STOMP messaging
- Typed events: `BattleEvent`, `DiplomacyEvent`, `TurnEvent`, `NationEvent`, `GeneralEvent`, `CommandEvent`
- Events persisted to `WorldHistory` table via `@EventListener` + broadcast via WebSocket
- Channels: `/topic/world/{worldId}/update`, `/topic/world/{worldId}/battle`, `/topic/world/{worldId}/turn`, `/topic/world/{worldId}/command`, `/topic/general/{generalId}`
- Frontend: `useWebSocket` hook (`frontend/src/hooks/useWebSocket.ts`) connects via `@stomp/stompjs` + SockJS, subscribes to world/general topics

**Entity Relationships:**
```
WorldState (1) ---< Nation (many)     [nation.worldId]
WorldState (1) ---< City (many)       [city.worldId]
WorldState (1) ---< General (many)    [general.worldId]
Nation (1) ---< City (many)           [city.nationId]
Nation (1) ---< General (many)        [general.nationId]
Nation (1) --- General (chief)        [nation.chiefGeneralId]
Nation (1) --- City (capital)         [nation.capitalCityId]
City (1) ---< General (many)          [general.cityId]
General (1) ---< Troop (leader)       [troop.leaderGeneralId]
Troop (1) ---< General (members)      [general.troopId]
Nation (1) ---< Diplomacy (src/dest)  [diplomacy.srcNationId, destNationId]
General (1) ---< GeneralTurn (queue)  [generalTurn.generalId]
Nation+Level ---< NationTurn (queue)  [nationTurn.nationId, officerLevel]
WorldState (1) ---< WorldHistory      [worldHistory.worldId]
General (1) --- AppUser (optional)    [general.userId]
```

---

*Architecture analysis: 2026-04-05*
