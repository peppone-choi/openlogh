# Architecture

**Analysis Date:** 2026-03-31

## Pattern Overview

**Overall:** Multi-process distributed game engine with reverse-proxy gateway and session-isolated game worlds

**Key Characteristics:**

- Multi-JVM process isolation: Gateway (port 8080) spawns/manages versioned Game App JVM instances (ports 9001-9999)
- Two orchestration modes: `GameProcessOrchestrator` (local JVM, `gateway.docker.enabled=false`) and `GameContainerOrchestrator` (Docker containers, `gateway.docker.enabled=true`)
- Session-centric world management: Each game world is a `SessionState` entity; all game entities carry `session_id` FK for logical isolation
- Command-pattern action system: 100+ commands registered in `CommandRegistry`, dispatched by `CommandExecutor` with CP/cooldown/position-card gating
- Dual game modes: Turn-based strategic gameplay (scheduled tick via `TurnDaemon`) + Real-time tactical combat (WebSocket via STOMP)
- CQRS turn processing pipeline: Load → Process (in-memory) → Persist → Publish (via `TurnCoordinator`)
- Legacy compatibility layer: Type aliases (`General = Officer`, `City = Planet`, `Nation = Faction`) and deprecated field aliases maintain backward compatibility with the OpenSamguk fork

## Layers

**Gateway Layer (gateway-app, port 8080):**

- Purpose: Public HTTP API gateway, user authentication, world lifecycle management, game-app process orchestration, reverse proxy
- Location: `backend/gateway-app/src/main/kotlin/com/openlogh/gateway/`
- Contains:
  - Controllers: `AuthController`, `WorldController`, `GameProxyController`, `AdminSystemController`, `ProcessOrchestratorController`, `AccountOAuthController`, `StaticDataController`
  - Services: `AuthService` (JWT), `WorldService` (CRUD), `WorldRouteRegistry` (worldId-to-baseUrl mapping), `AccountOAuthService` (Kakao OAuth), `GatewayAdminAuthorizationService`
  - Orchestrators: `GameOrchestrator` (interface), `GameProcessOrchestrator` (local JVM), `GameContainerOrchestrator` (Docker), `WorldActivationBootstrap` (startup restore)
  - Entity: `WorldState` (gateway's own JPA entity for `world_state` table)
- Depends on: PostgreSQL (world metadata), Redis (session config), game-app instances (HTTP proxy via WebClient)
- Used by: Frontend (all `/api/` calls), Nginx (upstream)

**Game Engine Layer (game-app, port 9001+):**

- Purpose: Core game simulation — command execution, turn processing, real-time battle coordination, WebSocket event broadcasting
- Location: `backend/game-app/src/main/kotlin/com/openlogh/`
- Contains:
  - `controller/` — 38 REST controllers (CommandController, OfficerController, PlanetController, FactionController, FleetController, etc.)
  - `websocket/` — 3 WebSocket controllers (CommandWebSocketController, BattleWebSocketController, TacticalWebSocketController)
  - `command/` — CQRS command system (CommandRegistry, CommandExecutor, 78 general commands, 34 nation commands)
  - `engine/` — 90+ game logic files across sub-packages (strategic, tactical, turn, war, ai, espionage, fleet, planet, modifier, doctrine, trigger, organization)
  - `service/` — 40+ business services (GameEventService, ScenarioService, WorldService, CharacterCreationService, etc.)
  - `entity/` — 39 JPA entities (Officer, Planet, Faction, Fleet, SessionState, Event, PositionCard, etc.)
  - `repository/` — 39 JPA repositories
  - `config/` — SecurityConfig, WebSocketConfig, JwtAuthenticationFilter, GlobalExceptionHandler
- Depends on: PostgreSQL (all game state), Redis (cache), shared library (DTOs, JWT verifier)
- Used by: Gateway (reverse proxy), Frontend (WebSocket direct via Nginx)

**Shared Library (shared):**

- Purpose: Cross-process contracts — DTOs, security utilities, constants, error types
- Location: `backend/shared/src/main/kotlin/com/openlogh/`
- Contains:
  - `shared/dto/` — `AccountDtos.kt`, `AdminDtos.kt`, `AuthDtos.kt`, `WorldDtos.kt`, `JwtUserPrincipal.kt`
  - `shared/model/` — `ScenarioData.kt`
  - `shared/security/` — `JwtTokenVerifier.kt`
  - `shared/error/` — `ErrorResponse.kt`
  - `shared/GameConstants.kt` — Global game constants
  - `util/JosaUtil.kt` — Korean grammar particle utility
- Depends on: Nothing (pure library)
- Used by: gateway-app, game-app (Gradle dependency)

**Frontend Layer (Next.js 15):**

- Purpose: Browser-based game client — lobby, game management, real-time game display, 3D model viewer
- Location: `frontend/src/`
- Contains:
  - `app/` — Next.js App Router with 4 route groups: `(auth)`, `(lobby)`, `(game)` (50+ pages), `(admin)`
  - `stores/` — 7 Zustand stores (authStore, worldStore, gameStore, officerStore, generalStore, battleStore)
  - `lib/` — API client (`api.ts` base + `gameApi.ts` with 30+ API modules), WebSocket (`websocket.ts`), utilities
  - `hooks/` — Custom hooks (useWebSocket, useHotkeys, useSoundEffects, useTacticalWebSocket)
  - `components/` — UI components (game/, auth/, ui/ shadcn primitives)
  - `types/` — TypeScript type definitions (`index.ts`, `tactical.ts`)
  - `contexts/` — React contexts (AdminWorldContext, sheet-context)
- Depends on: Gateway (all REST calls via Axios to `localhost:8080/api`), Game-app (WebSocket via STOMP to `/ws-stomp`)
- Used by: Browser clients

**Nginx (Reverse Proxy):**

- Purpose: Single entry point for production; routes traffic to appropriate upstream
- Location: `nginx/nginx.conf`
- Routes:
  - `/api/` → gateway:8080
  - `/ws/` and `/ws-stomp` → game:9001 (WebSocket upgrade)
  - `/internal/` → gateway:8080
  - `/_next/` → frontend:3000 (with cache headers)
  - `/` (catch-all) → frontend:3000

## Data Flow

**World Creation Flow:**

1. Frontend calls `POST /api/worlds` → gateway `WorldController.createWorld()`
2. Gateway calls `gameOrchestrator.ensureVersion()` to start/reuse a game-app instance for the requested `commitSha`/`gameVersion`
3. Gateway calls `POST $gameBaseUrl/api/worlds` on the game-app instance to create world data (entities, scenario loading)
4. Gateway calls `gameOrchestrator.attachWorld()` to register worldId → baseUrl in `WorldRouteRegistry`
5. Gateway persists world metadata (commitSha, gameVersion, gatewayActive flag) in its own `world_state` table
6. Frontend receives `WorldStateResponse` with the created world

**World Activation Flow (on gateway startup):**

1. `WorldActivationBootstrap.run()` fires as `ApplicationRunner`
2. Queries all `WorldState` entities with `meta.gatewayActive = true`
3. Groups worlds by (commitSha, gameVersion) and warms up game instances in parallel threads
4. Attaches each world to its game instance via `gameOrchestrator.attachWorld()`
5. Retries failed attachments up to 3 times with 30s delay

**HTTP Request Proxy Flow (game API calls):**

1. Frontend calls `GET/POST /api/game/{worldId}/officers/...` → Nginx → gateway
2. `GameProxyController.proxyToGame()` extracts `worldId`, resolves baseUrl from `WorldRouteRegistry`
3. Strips `/api/game/{worldId}` prefix, forwards to `$baseUrl/api/officers/...` via `WebClient`
4. Copies request headers (minus `host`, `content-length`), proxies response back to frontend

**Command Execution Flow (REST, real-time mode):**

1. Frontend calls `POST /api/game/{worldId}/api/officers/{id}/execute` with `{actionCode, arg}`
2. Proxied to game-app → `CommandController.executeCommand()`
3. `RealtimeService.submitCommand()` validates: officer exists, world in realtime mode, no command already in progress
4. Builds `CommandEnv` (year, month, worldId, realtimeMode)
5. `CommandExecutor.executeGeneralCommand()` checks:
   - Cooldown: `officer.meta["next_execute"][actionCode]` vs current year/month
   - Position card gating: `PositionCardService.getHeldCardCodes()` → `CommandGating.canExecuteCommand()`
   - CP consumption: `CommandPointService.consume(officer, cpType, cpCost)`
6. `CommandRegistry.createGeneralCommand()` instantiates the command class
7. `cmd.run(rng)` executes game logic, returns `CommandResult`
8. `applyMessageChanges()` applies stat deltas to Officer/Planet/Faction entities
9. Result returned to frontend

**Command Execution Flow (WebSocket):**

1. Frontend sends STOMP message to `/app/command/{sessionId}/execute`
2. `CommandWebSocketController.handleCommandExecution()` receives `CommandExecuteMessage`
3. Immediately broadcasts ACK via `GameEventService.broadcastCommandResult()` to `/topic/world/{sessionId}/event`
4. Actual command queued in `OfficerTurn` entity for turn-engine processing

**Turn Processing Flow (TurnDaemon):**

1. `TurnDaemon.tick()` runs on fixed rate (default 1000ms, configurable via `game.tick-rate`)
2. Queries all `SessionState` entities matching current `commitSha`
3. For each world, checks skip conditions (locked, not yet open, gatewayActive=false)
4. Depending on mode:
   - `realtimeMode=true` → `RealtimeService.processCompletedCommands()` + `regenerateCommandPoints()`
   - `cqrsEnabled=true` → `TurnCoordinator.processWorld()`
   - Default → `TurnService.processWorld()`
5. On year/month change, broadcasts turn advance via `GameEventService.broadcastTurnAdvance()`

**CQRS Turn Processing (TurnCoordinator):**

1. **LOADING**: `WorldStateLoader.loadWorldState()` reads all entities for the world into `InMemoryWorldState`
2. **PROCESSING**: `InMemoryTurnProcessor.process()` executes all queued commands against in-memory state, tracked by `DirtyTracker`
3. **PERSISTING**: `WorldStatePersister.persist()` writes only dirty entities back to PostgreSQL
4. **PUBLISHING**: `GameEventService.broadcastTurnAdvance()` notifies all WebSocket subscribers
5. Lifecycle tracked via `TurnStatusService` (IDLE → LOADING → PROCESSING → PERSISTING → PUBLISHING → IDLE)

**WebSocket Real-time Flow (Battle):**

1. Frontend subscribes to `/topic/world/{worldId}/battle` via STOMP client
2. Player sends battle command to `/app/battle/{sessionId}/command` with `BattleCommandMessage`
3. `BattleWebSocketController.handleBattleCommand()` validates officer, planet, faction alignment, ship count
4. `BattleService.executeBattle(officer, planet, world)` runs combat resolution
5. `BattleEventResponse` sent to `/topic/world/{sessionId}/battle` (broadcast to all subscribers)

**Frontend WebSocket Subscription:**

1. `useWebSocket` hook connects STOMP client to `ws://host/ws-stomp` when a `currentWorld` exists
2. Subscribes to 4 topics per world:
   - `/topic/world/{worldId}/turn` → triggers store refresh (`fetchWorld`, `fetchMyOfficer`)
   - `/topic/world/{worldId}/battle` → shows battle toast
   - `/topic/world/{worldId}/diplomacy` → shows diplomacy toast
   - `/topic/world/{worldId}/message` → shows message notification
3. Reconnects automatically (5s delay) via `@stomp/stompjs`

**State Management:**

- **Persistent State:** PostgreSQL entities (39 entity types in game-app, `world_state` in gateway-app)
- **Client State:** Zustand stores persisted to `sessionStorage` (worldStore, gameStore, authStore with key prefixes `openlogh:world`, `openlogh:game`)
- **Real-time State:** In-memory in game-app (TurnDaemon cycle, `InMemoryWorldState` during CQRS processing)
- **Route State:** `WorldRouteRegistry` — `ConcurrentHashMap<Long, String>` mapping worldId → game-app baseUrl

## Key Abstractions

**World (SessionState):**

- Purpose: Container for an entire game instance with independent state
- Location: `backend/game-app/src/main/kotlin/com/openlogh/entity/SessionState.kt` (game-app), `backend/gateway-app/src/main/kotlin/com/openlogh/gateway/entity/WorldState.kt` (gateway)
- Pattern: One SessionState per game world; all game entities carry `session_id` FK. Config stored as JSONB (`config`, `meta` maps). Gateway has its own JPA mapping of the same `world_state` table for metadata (commitSha, gameVersion, gatewayActive).
- Key fields: `currentYear`, `currentMonth`, `tickSeconds`, `realtimeMode`, `commandPointRegenRate`, `commitSha`, `config` (JSONB), `meta` (JSONB)

**Officer (aliased as General):**

- Purpose: Player-controllable character unit (player or NPC officer)
- Location: `backend/game-app/src/main/kotlin/com/openlogh/entity/Officer.kt`
- Pattern: Links to Faction (`factionId`), Planet (`planetId`), carries 8-stat system (leadership, command, intelligence, politics, administration, mobility, attack, defense), equipment slots (flagshipCode, equipCode, engineCode, accessoryCode), rank, experience, meta JSONB
- Key fields: `rank`, `commandPoints`, `commandEndTime`, `shipClass`, `ships`, `training`, `morale`, `funds`, `supplies`

**Fleet:**

- Purpose: Military unit grouping ships for organized combat
- Location: `backend/game-app/src/main/kotlin/com/openlogh/entity/Fleet.kt`
- Pattern: Belongs to Officer (commander), has ship counts by class, linked to Faction

**Planet (aliased as City):**

- Purpose: Territorial unit with resources, defenses, population
- Location: `backend/game-app/src/main/kotlin/com/openlogh/entity/Planet.kt`
- Pattern: Owned by Faction (`factionId`), generates funds/supplies via `commerce`/`production`, has `orbitalDefense`, `fortress`, `security`, `approval`, `population`

**Faction (aliased as Nation):**

- Purpose: Political entity (Empire, Alliance, Fezzan, Rebel)
- Location: `backend/game-app/src/main/kotlin/com/openlogh/entity/Faction.kt`
- Pattern: Has sovereign (leader), capital planet, treasury (funds/supplies), policies (taxRate, conscriptionRate), spy intel map

**Command System (CQRS):**

- Purpose: Encapsulates all player actions with validation, gating, and CP consumption
- Location:
  - Registry: `backend/game-app/src/main/kotlin/com/openlogh/command/CommandRegistry.kt` (78 general + 34 nation commands)
  - Executor: `backend/game-app/src/main/kotlin/com/openlogh/command/CommandExecutor.kt`
  - General commands: `backend/game-app/src/main/kotlin/com/openlogh/command/general/` (Korean names: `휴식`, `워프항행`, `정찰`, etc.)
  - Nation commands: `backend/game-app/src/main/kotlin/com/openlogh/command/nation/` (`승진`, `강등`, `선전포고`, etc.)
- Pattern: Factory pattern via `GeneralCommandFactory = (General, CommandEnv, Map<String, Any>?) -> BaseCommand`. Commands registered as lambdas in `CommandRegistry`. Executor validates cooldown → position cards → CP cost → creates command → runs → applies stat changes.
- Optimistic locking: `CommandExecutor.withOptimisticRetry()` catches `OptimisticLockingFailureException`, retries up to 3 times

**Position Card System:**

- Purpose: Role-based command authorization (gin7 "job card" mechanic)
- Location: `backend/game-app/src/main/kotlin/com/openlogh/engine/organization/PositionCardSystem.kt`
- Pattern: Officers hold position cards (`PositionCard` entity) that grant access to command groups (diplomacy, logistics, operations, personnel, politics). `CommandGating.canExecuteCommand()` checks held cards against required command group.

**GameOrchestrator (Process Management):**

- Purpose: Manages game-app JVM instances lifecycle
- Location: `backend/gateway-app/src/main/kotlin/com/openlogh/gateway/orchestrator/GameOrchestrator.kt` (interface)
- Implementations:
  - `GameProcessOrchestrator` (local JVM, ports 9001-9999, enabled when `gateway.docker.enabled=false`)
  - `GameContainerOrchestrator` (Docker containers, enabled when `gateway.docker.enabled=true`)
- Pattern: `ensureVersion()` starts or reuses instance, `attachWorld()` registers worldId routing, `detachWorld()` removes routing, health check polling on `/internal/health`

**Tactical Battle Engine:**

- Purpose: Real-time fleet combat simulation with grid-based positioning
- Location: `backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/` (20+ files)
- Key files: `TacticalBattleEngine.kt`, `TacticalBattleSession.kt`, `TacticalGrid.kt`, `TacticalFleet.kt`, `TacticalUnit.kt`, `EnergyAllocation.kt`, `FormationEffect.kt`, `WeaponType.kt`
- Pattern: `TacticalSessionManager` manages active battle sessions, `TacticalTurnScheduler` ticks battle rounds, `TacticalResultWriteback` persists battle outcomes to entities

## Entry Points

**Gateway Application:**

- Location: `backend/gateway-app/src/main/kotlin/com/openlogh/gateway/GatewayApplication.kt`
- Port: 8080
- Triggers: `./gradlew :gateway-app:bootRun`
- Responsibilities: HTTP API (auth, world CRUD, admin), reverse proxy to game-app, game-app process lifecycle

**Game Application:**

- Location: `backend/game-app/src/main/kotlin/com/openlogh/OpenloghApplication.kt`
- Port: 9001 (configurable via `--server.port`)
- Triggers: Spawned by `GameProcessOrchestrator` as `java -jar game-app-{commitSha}.jar --server.port={port} --game.commit-sha={sha} --game.version={ver}`
- Responsibilities: Game logic, turn engine (`TurnDaemon` at 1s tick), command processing, WebSocket broadcasting

**Bootstrap Exit Runner:**

- Location: `backend/game-app/src/main/kotlin/com/openlogh/bootstrap/BootstrapExitRunner.kt`
- Triggers: Docker `bootstrap` service with `--app.bootstrap.exit-on-ready=true`
- Responsibilities: Run Flyway migrations and exit (database schema setup before gateway starts)

**Frontend Entry:**

- Location: `frontend/src/app/layout.tsx` (root layout), `frontend/src/app/(game)/layout.tsx` (game layout)
- Port: 3000
- Triggers: `pnpm dev`
- Responsibilities: Auth routing, game context (WebSocket, sidebar, navigation), store hydration

**World Activation Bootstrap:**

- Location: `backend/gateway-app/src/main/kotlin/com/openlogh/gateway/orchestrator/WorldActivationBootstrap.kt`
- Triggers: Gateway startup (implements `ApplicationRunner`)
- Responsibilities: Asynchronously restore previously active worlds on daemon thread with retry logic (3 attempts, 30s delay)

**Turn Daemon:**

- Location: `backend/game-app/src/main/kotlin/com/openlogh/engine/TurnDaemon.kt`
- Triggers: `@Scheduled(fixedRateString = "${game.tick-rate:1000}")` — every 1 second by default
- Responsibilities: Process all active worlds for this game-app instance, advance turns, regenerate CP

## Error Handling

**Strategy:** Layered error handling with Korean user-facing messages in game context

**Patterns:**

- **Command Validation Errors:** `CommandResult(success=false, logs=["message"])` — returned inline with Korean messages (e.g., "커맨드 포인트가 부족합니다", "해당 직무권한이 없습니다")
- **Optimistic Lock Conflicts:** `CommandExecutor.withOptimisticRetry()` retries 3 times on `OptimisticLockingFailureException` for CP race conditions
- **API Errors:** `GlobalExceptionHandler` (`@ControllerAdvice`) in game-app maps exceptions to HTTP responses
- **Gateway Proxy Errors:** `GameProxyController` returns 502 BAD_GATEWAY when `WorldRouteRegistry.resolve()` returns null
- **World Controller Errors:** Catches `IllegalArgumentException` (400), `IllegalStateException` (409), `WebClientResponseException` (proxied status codes)
- **WebSocket Errors:** `BattleWebSocketController` returns `BattleEventResponse(type="battle_error", message=...)` for validation failures
- **Process Errors:** `GameProcessOrchestrator` kills process on health check timeout, `WorldActivationBootstrap` retries 3 times
- **Frontend Auth Errors:** Axios interceptor catches 401, removes token, redirects to `/login` (skips for `/auth/` endpoints)

## Cross-Cutting Concerns

**Logging:**

- Backend: SLF4J via `LoggerFactory.getLogger(ClassName::class.java)` in companion objects
- Game-app process logs: `logs/game-{commitSha}.log` (redirected stdout/stderr by `GameProcessOrchestrator`)
- Frontend: `console.error()` in catch blocks, no centralized aggregation

**Validation:**

- **Command Validation:** 3-layer check in `CommandExecutor.executeGeneralCommand()`: cooldown → position card gating → CP consumption
- **Entity Validation:** JPA annotations (`@Column(nullable=false)`) at database level
- **Position Card Gating:** `CommandGating.canExecuteCommand(heldCards, commandGroup)` — maps command codes to groups via `StrategicCommandRegistry` or prefix-based matching
- **Resource Constraints:** CP cost via `CommandPointService.consume()`, rank checks (nation commands require rank >= 9)

**Authentication:**

- **Gateway:** JWT token validation via `JwtAuthenticationFilter`, issued by `AuthService` with HS256 signing
- **Game-app:** JWT validation via shared `JwtTokenVerifier` from shared library
- **Frontend:** JWT stored in `localStorage` (key: `token`), attached via Axios request interceptor (`Authorization: Bearer {token}`)
- **Admin Routes:** `GatewayAdminAuthorizationService.isAdmin()` checks user grade
- **OAuth:** Kakao OAuth integration via `AccountOAuthService` (configurable, default disabled)

**WebSocket Communication:**

- **Endpoints:** `/ws` (SockJS fallback) and `/ws-stomp` (raw STOMP) registered in `WebSocketConfig`
- **Broker:** Simple in-memory broker for `/topic` and `/queue` prefixes
- **Application prefix:** `/app` — client sends to `/app/command/{sessionId}/execute`, `/app/battle/{sessionId}/command`
- **Subscription topics:**
  - `/topic/world/{worldId}/turn` — turn advance notifications (year, month)
  - `/topic/world/{worldId}/event` — general game events + command results
  - `/topic/world/{worldId}/battle` — battle results
  - `/topic/world/{worldId}/diplomacy` — diplomacy events
  - `/topic/world/{worldId}/message` — new message notifications
- **Protocol:** STOMP over WebSocket, `@stomp/stompjs` client on frontend, Spring `SimpMessagingTemplate` on backend

**Type Aliases (Legacy Compatibility):**

- Location: `backend/game-app/src/main/kotlin/com/openlogh/entity/TypeAliases.kt`
- Mappings: `General = Officer`, `City = Planet`, `Nation = Faction`, `WorldState = SessionState`, `GeneralTurn = OfficerTurn`, `NationTurn = FactionTurn`
- Purpose: Allow command code (Korean-named classes) to reference old OpenSamguk type names without mass-refactor

---

*Architecture analysis: 2026-03-31*
