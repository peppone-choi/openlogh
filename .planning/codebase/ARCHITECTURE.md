# Architecture

**Analysis Date:** 2026-03-31

## Pattern Overview

**Overall:** Multi-process modular monolith with API gateway pattern

**Key Characteristics:**
- Two separate Spring Boot JVM processes: `gateway-app` (auth, routing, orchestration) and `game-app` (game logic, turn engine, domain)
- Gateway proxies authenticated requests to game-app instances via HTTP (WebClient)
- Game-app instances are version-pinned and can run multiple worlds per JVM
- Frontend is a standalone Next.js 15 SPA communicating via REST + WebSocket (STOMP/SockJS)
- Shared Kotlin library (`shared` module) provides DTOs, JWT verification, and game constants across both apps
- Legacy PHP codebase (`legacy-core/`) serves as parity target -- not runtime code

## Layers

**Gateway Layer (gateway-app):**
- Purpose: Authentication, authorization, world management, request routing to game instances
- Location: `backend/gateway-app/src/main/kotlin/com/opensam/gateway/`
- Contains: Auth controllers, JWT filter, game proxy, world orchestration, admin endpoints
- Depends on: `shared` module, PostgreSQL, Redis
- Used by: Frontend (all `/api/**` requests hit gateway first)

**Game Layer (game-app):**
- Purpose: All game domain logic -- commands, turn engine, war, economy, NPC AI, scenarios
- Location: `backend/game-app/src/main/kotlin/com/opensam/`
- Contains: Controllers, services, entities, repositories, engine, commands
- Depends on: `shared` module, PostgreSQL, Redis, Flyway migrations
- Used by: Gateway (proxied requests), Turn daemon (internal scheduler)

**Shared Layer:**
- Purpose: Cross-cutting DTOs, JWT token verifier, game constants, scenario data model
- Location: `backend/shared/src/main/kotlin/com/opensam/shared/`
- Contains: `dto/` (AuthDtos, AccountDtos, AdminDtos, WorldDtos, JwtUserPrincipal), `error/ErrorResponse`, `model/ScenarioData`, `security/JwtTokenVerifier`, `GameConstants`
- Depends on: Jackson, JJWT, Jakarta Validation
- Used by: Both gateway-app and game-app

**Frontend Layer:**
- Purpose: Player-facing SPA -- game UI, lobby, auth, admin panel, tutorial
- Location: `frontend/src/`
- Contains: Next.js App Router pages, React components, Zustand stores, API clients, types
- Depends on: Next.js 15, React, Zustand, Axios, shadcn/ui, STOMP.js, SockJS
- Used by: Players via browser

**Game Data Layer:**
- Purpose: Static game reference data (scenarios, maps, items, officer ranks, general pool)
- Location: `backend/shared/src/main/resources/data/`
- Contains: JSON files for scenarios (80+), maps (9), items, officer ranks, general pool
- Depends on: Nothing (pure data)
- Used by: `ScenarioService`, `MapService`, `OfficerRankService`, `ItemService`, `GameConstService`

## Data Flow

**Player Command Execution (Turn-based):**

1. Frontend calls `POST /api/commands/execute` with action code and args (via `gameApi.commandApi.execute()`)
2. Gateway's `GameApiCatchAllController` or `GameProxyController` proxies the request to the active game-app instance (resolved via `WorldRouteRegistry`)
3. Game-app `CommandController` receives request, resolves the authenticated general
4. `CommandExecutor.executeGeneralCommand()` creates command via `CommandRegistry`, hydrates context (city, nation, dest entities), checks constraints
5. Command's `run()` method executes game logic, returns `CommandResult` with logs and entity mutations
6. `CommandResultApplicator` applies JSON delta to entities; modified entities saved via `JpaWorldPortFactory`
7. Response with logs returned to frontend through gateway proxy

**Turn Processing (Scheduled):**

1. `TurnDaemon` (Spring `@Scheduled`) checks all active worlds for overdue turns every 5 seconds
2. `TurnCoordinator.processWorld()` orchestrates the turn lifecycle: LOADING -> PROCESSING -> PERSISTING -> PUBLISHING
3. `WorldStateLoader` loads all entities for the world into `InMemoryWorldState`
4. `InMemoryTurnProcessor` runs `TurnPipeline` -- an ordered chain of `TurnStep` beans:
   - `AdvanceMonthStep` (order 100) -- advance year/month
   - `EconomyPreUpdateStep` (200) -- tax collection, trade
   - `ExecuteGeneralCommandsStep` (300) -- process queued general commands
   - `DiplomacyStep` (400) -- diplomacy state transitions
   - `GeneralMaintenanceStep` (500) -- aging, loyalty, death
   - `EconomyPostUpdateStep` (600) -- supply, population growth
   - `DisasterAndTradeStep` (700) -- random events
   - `WarFrontRecalcStep` (800) -- recalculate war fronts
   - `UnificationCheckStep` (900) -- check for game end
   - `WorldSnapshotStep` / `YearbookSnapshotStep` / `TrafficSnapshotStep` -- record keeping
5. `WorldStatePersister` writes dirty entities back to PostgreSQL via `DirtyTracker`
6. `GameEventService.broadcastTurnAdvance()` publishes STOMP message to `/topic/world/{id}/turn`
7. Frontend `useWebSocket` hook receives turn event and refreshes stores

**Authentication Flow:**

1. Frontend `authStore.login()` calls `POST /api/auth/login` on gateway
2. Gateway `AuthController` validates credentials, issues JWT (HS256, 24h expiry)
3. Token stored in `localStorage`; Axios interceptor attaches `Authorization: Bearer {token}` to all subsequent requests
4. Gateway `JwtAuthenticationFilter` validates token on every request
5. Game-app also has its own `JwtAuthenticationFilter` for direct requests (same shared secret)

**World Lifecycle:**

1. Admin creates world via gateway `WorldController` (scenario + map selection)
2. `ScenarioService` loads scenario JSON, creates cities/nations/generals in DB
3. Admin activates world -- gateway `GameProcessOrchestrator` spawns game-app JVM process on a free port (9001-9999)
4. `WorldRouteRegistry` maps worldId -> `http://127.0.0.1:{port}`
5. All game API calls for that world are proxied to the assigned game-app instance
6. Deactivation stops the process and removes the route

**State Management (Frontend):**
- `useAuthStore` (Zustand): JWT token, user info, auth state -- persisted in `localStorage`
- `useWorldStore` (Zustand + persist): Current world, world list -- persisted in `sessionStorage`
- `useGeneralStore` (Zustand + persist): Current player's general data -- persisted in `sessionStorage`
- `useGameStore` (Zustand): Transient game state (cities, nations, etc.) -- not persisted
- WebSocket (`useWebSocket` hook): STOMP/SockJS connection for real-time turn/battle/diplomacy events

## Key Abstractions

**Command System:**
- Purpose: Encapsulates all player actions (55 general commands + 38 nation commands)
- Examples: `backend/game-app/src/main/kotlin/com/opensam/command/general/che_농지개간.kt`, `backend/game-app/src/main/kotlin/com/opensam/command/nation/che_선전포고.kt`
- Pattern: Strategy pattern -- `BaseCommand` abstract class with `run()`, `getCost()`, `getPreReqTurn()`, `getPostReqTurn()`. All commands registered in `CommandRegistry` via factory lambdas. `CommandExecutor` orchestrates constraint checking, cooldown, multi-turn stacking, and entity persistence.
- Korean names used for command classes and action codes (legacy parity with PHP)

**Constraint System:**
- Purpose: Validates command preconditions (resources, permissions, war state, distance, etc.)
- Examples: `backend/game-app/src/main/kotlin/com/opensam/command/constraint/Constraint.kt`, `ConstraintChain.kt`, `ConstraintHelper.kt`
- Pattern: Chain of responsibility -- each `Constraint` returns `Pass` or `Fail(reason)`. Commands declare `fullConditionConstraints` and `minConditionConstraints` lists.

**CQRS Turn Engine:**
- Purpose: Process monthly turns with in-memory state for performance, then persist dirty entities
- Examples: `backend/game-app/src/main/kotlin/com/opensam/engine/turn/cqrs/TurnCoordinator.kt`, `InMemoryWorldPorts.kt`, `DirtyTracker.kt`
- Pattern: Load all world state into memory (`InMemoryWorldState`), process through `TurnPipeline` steps, track mutations via `DirtyTracker`, bulk-persist only changed entities via `WorldStatePersister`
- Lifecycle states: IDLE -> LOADING -> PROCESSING -> PERSISTING -> PUBLISHING -> IDLE (or FAILED)

**World Port Abstraction:**
- Purpose: Abstract data access for both real-time commands (JPA) and turn processing (in-memory)
- Examples: `backend/game-app/src/main/kotlin/com/opensam/engine/turn/cqrs/port/WorldReadPort.kt`, `WorldWritePort.kt`
- Pattern: Port/Adapter -- `WorldReadPort` and `WorldWritePort` interfaces with two implementations: `JpaWorldPorts` (for live command execution) and `InMemoryWorldPorts` (for turn processing)

**Action Modifier System:**
- Purpose: Apply stat/action bonuses from nation type, personality, specials, items
- Examples: `backend/game-app/src/main/kotlin/com/opensam/engine/modifier/ModifierService.kt`, `ActionModifier`
- Pattern: Decorator/Pipeline -- `ModifierService.getModifiers()` collects applicable modifiers, injected into commands via `CommandExecutor`

**Game Orchestrator:**
- Purpose: Manage game-app JVM process lifecycle from gateway
- Examples: `backend/gateway-app/src/main/kotlin/com/opensam/gateway/orchestrator/GameProcessOrchestrator.kt`, `GameContainerOrchestrator.kt`
- Pattern: Strategy -- `GameOrchestrator` interface with `GameProcessOrchestrator` (dev: spawn local JVM) and `GameContainerOrchestrator` (prod: Docker containers). Selected via `@ConditionalOnProperty("gateway.docker.enabled")`

## Entry Points

**Gateway Application:**
- Location: `backend/gateway-app/src/main/kotlin/com/opensam/gateway/GatewayApplication.kt`
- Triggers: `./gradlew :gateway-app:bootRun` or Docker
- Responsibilities: Boot Spring context, run admin bootstrap, start proxy routing
- Port: 8080 (default)

**Game Application:**
- Location: `backend/game-app/src/main/kotlin/com/opensam/` (main class not shown but follows Spring Boot convention)
- Triggers: Spawned by gateway orchestrator on port 9001+, or `./gradlew :game-app:bootRun`
- Responsibilities: Boot Spring context, run Flyway migrations, start turn daemon scheduler
- Port: 9001 (default, configurable)

**Frontend Application:**
- Location: `frontend/src/app/layout.tsx` (root layout)
- Triggers: `pnpm dev` or Docker (standalone output)
- Responsibilities: Next.js App Router entry, theme provider, Toaster

**Game Dashboard (Player Home):**
- Location: `frontend/src/app/(game)/page.tsx` -> delegates to `frontend/src/components/game/game-dashboard.tsx`
- Triggers: Authenticated player navigates to `/`
- Responsibilities: Main game view after login and world selection

**Turn Daemon:**
- Location: `backend/game-app/src/main/kotlin/com/opensam/engine/TurnDaemon.kt`
- Triggers: Spring `@Scheduled` every 5 seconds (`app.turn.interval-ms: 5000`)
- Responsibilities: Check all active worlds, trigger `TurnCoordinator.processWorld()` for overdue turns

## Error Handling

**Strategy:** Layered error handling with global exception handler + per-layer catches

**Patterns:**
- Backend global: `backend/game-app/src/main/kotlin/com/opensam/config/GlobalExceptionHandler.kt` -- catches exceptions and returns structured `ErrorResponse`
- Command failures: `CommandResult(success = false, logs = [...])` -- commands never throw; they return failure results with Korean-language log messages
- Turn pipeline fault tolerance: Each `TurnStep` is wrapped in try-catch; failures are logged but pipeline continues to next step (legacy daemon.ts parity)
- Frontend API errors: Axios interceptor in `frontend/src/lib/api.ts` -- 401 triggers logout/redirect; `api-error.ts` extracts structured error messages
- Frontend auth errors: `frontend/src/lib/auth-error.ts` handles auth-specific error formatting

## Cross-Cutting Concerns

**Logging:**
- Backend: SLF4J via `LoggerFactory.getLogger()` (standard Spring Boot)
- Key loggers: `TurnPipeline`, `TurnCoordinator`, `GameProcessOrchestrator`
- Command results carry structured log messages with color tags (e.g., `<R>...</>` for red)

**Validation:**
- Backend: Spring Validation (`@Valid`, Jakarta annotations) for DTOs
- Command args: `ArgSchema` system -- each command declares its argument schema; `ArgSchema.parse()` validates and coerces types before execution
- Frontend: Form-level validation in page components

**Authentication:**
- JWT (HS256) with shared secret between gateway and game-app (via `shared` module `JwtTokenVerifier`)
- Gateway: `JwtAuthenticationFilter` -> `SecurityConfig` (Spring Security, stateless sessions)
- Game-app: Same `JwtAuthenticationFilter` pattern for direct access
- OAuth support: Kakao provider via `AccountOAuthController` / `AccountOAuthService`
- OTP support: Two-factor authentication flow

**Authorization:**
- Role-based: `USER`, `ADMIN` roles in JWT claims
- Game-level: `officerLevel` determines nation command access (0=wanderer, 1+=member, 2+=secret access, 20=chief)
- Admin: `AdminAuthorizationService` / `GatewayAdminAuthorizationService` check admin grade

**Real-time Communication:**
- STOMP over SockJS WebSocket
- Game-app config: `backend/game-app/src/main/kotlin/com/opensam/config/WebSocketConfig.kt`
- Topics: `/topic/world/{id}/turn`, `/topic/world/{id}/battle`, `/topic/world/{id}/diplomacy`, `/topic/world/{id}/message`
- Frontend: `frontend/src/lib/websocket.ts` (STOMP client), `frontend/src/hooks/useWebSocket.ts` (React hook)

**Database Migrations:**
- Flyway with SQL migrations: `backend/game-app/src/main/resources/db/migration/V1__core_tables.sql` through `V27__add_general_position.sql`
- DDL validation mode (`ddl-auto: validate`) -- schema managed entirely by Flyway

**Caching:**
- Redis used for NPC token management (`SelectNpcTokenService`)
- Zustand stores use `sessionStorage` persistence for world/general state across page navigations

---

*Architecture analysis: 2026-03-31*
