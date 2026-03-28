# Architecture

**Analysis Date:** 2026-03-28

## Pattern Overview

**Overall:** Multi-process distributed game engine with client-server separation

**Key Characteristics:**

- Multi-JVM process isolation: Gateway (port 8080) + versioned Game App instances (9001-9999)
- Session-centric world management: Each game world maps to a `SessionState` entity
- Command-centric action system: All player actions flow through CQRS-style command dispatch
- Turn-based strategic gameplay + Real-time tactical combat via WebSocket
- Event-driven architecture with CQRS event sourcing in turn engine

## Layers

**Gateway Layer (gateway-app):**

- Purpose: HTTP API gateway, world lifecycle management, authentication, process orchestration
- Location: `backend/gateway-app/src/main/kotlin/com/openlogh/gateway/`
- Contains: Controllers for auth, world management, admin, proxying to game-app instances
- Depends on: PostgreSQL (entities), Redis (sessions), game-app (HTTP proxy)
- Used by: Frontend, external clients

**Game Engine Layer (game-app):**

- Purpose: Core strategic turn engine, command execution, event processing, real-time battle coordination
- Location: `backend/game-app/src/main/kotlin/com/openlogh/`
- Contains: Commands, services, entities, repositories, turn engine, WebSocket controllers
- Depends on: PostgreSQL (game state), Redis (cache), shared library (models, DTOs)
- Used by: Gateway (reverse proxy), Frontend (WebSocket)

**Shared Library:**

- Purpose: Cross-process models, DTOs, error definitions, security utilities
- Location: `backend/shared/src/main/kotlin/com/openlogh/`
- Contains: Entity definitions, shared DTOs, security models, error handling
- Depends on: Nothing
- Used by: gateway-app, game-app

**Frontend Layer (Next.js):**

- Purpose: User interface for lobby, game management, real-time game display
- Location: `frontend/src/`
- Contains: React components, Zustand stores, API client, WebSocket integration
- Depends on: Backend APIs (gateway-app), WebSocket (game-app)
- Used by: Browser clients

## Data Flow

**World Activation Flow:**

1. Frontend calls `POST /api/worlds/{id}/activate` (gateway-app)
2. Gateway's `ProcessOrchestratorController` receives request
3. `GameProcessOrchestrator` ensures game-app version is running on available port (9001-9999)
4. Game instance's `/internal/health` endpoint is polled until ready
5. `WorldRouteRegistry` maps worldId → game-app baseUrl for future requests
6. Frontend receives activation confirmation with game-app URL

**Command Execution Flow:**

1. Frontend user initiates action (e.g., move officer, declare war)
2. WebSocket message sent to `CommandWebSocketController` via `/app/command/{sessionId}/execute`
3. Controller immediately acknowledges receipt via `GameEventService.broadcastCommandResult`
4. Command is queued in turn system (`OfficerTurn` entity) for next turn processing
5. Turn engine processes commands in `CommandExecutor` with constraint validation
6. Event log stored in database, broadcast to all connected clients via WebSocket
7. Frontend updates Zustand stores with new game state

**Turn Progression:**

1. Turn timer expires or manual turn advance triggered
2. Turn engine processes all queued commands for current faction
3. `CommandExecutor` validates each command (CP cost, position cards, cooldowns)
4. Strategic engines execute (economy, diplomacy, espionage, etc.)
5. Battle resolution if combat occurred
6. Events aggregated and persisted
7. All clients notified via WebSocket to update UI

**State Management:**

- **Persistent State:** PostgreSQL entities (Officer, Planet, Faction, Fleet, SessionState, etc.)
- **Session State:** SessionStorage (frontend) via Zustand for currentWorld, authenticated user
- **Real-time State:** Redis for connected client tracking (optional), game-app memory for active turn processing
- **Cache Invalidation:** Event broadcast triggers frontend store updates via WebSocket

## Key Abstractions

**World (SessionState):**

- Purpose: Container for an entire game instance with independent state
- Examples: `backend/game-app/src/main/kotlin/com/openlogh/entity/SessionState.kt`
- Pattern: One SessionState per game world; isolation via `session_id` foreign keys on all entities

**Officer:**

- Purpose: Controllable character unit (player or NPC)
- Examples: `backend/game-app/src/main/kotlin/com/openlogh/entity/Officer.kt` (10KB, 8-stat system with abilities)
- Pattern: Links to Faction, Planet, Fleet; carries metadata for actions, position cards, stats

**Fleet:**

- Purpose: Military unit grouping ships for combat
- Examples: `backend/game-app/src/main/kotlin/com/openlogh/entity/Fleet.kt`
- Pattern: Belongs to Officer, contains ship count by class (battleship, cruiser, destroyer, etc.)

**Planet:**

- Purpose: Territorial unit with resources and defenses
- Examples: `backend/game-app/src/main/kotlin/com/openlogh/entity/Planet.kt`
- Pattern: Owned by Faction, generates funds/supplies, can be fortified with orbital/ground defense

**Command (CQRS):**

- Purpose: Encapsulates single player action with validation rules
- Examples: `backend/game-app/src/main/kotlin/com/openlogh/command/general/` (move, attack, recruit)
- Pattern: Registered in `CommandRegistry`, executed by `CommandExecutor`, validated against constraints

**Engine Services (Strategic):**

- Purpose: Stateless calculators for specific game systems
- Examples:
    - `backend/game-app/src/main/kotlin/com/openlogh/engine/EconomyService.kt` (resource calculation)
    - `backend/game-app/src/main/kotlin/com/openlogh/engine/DiplomacyService.kt` (faction relations)
    - `backend/game-app/src/main/kotlin/com/openlogh/engine/strategic/` (turn processing)
- Pattern: Injected into command handlers; pure functions operating on entities

**Game Event:**

- Purpose: Immutable record of game outcome (for logging and replay)
- Examples: `backend/game-app/src/main/kotlin/com/openlogh/entity/Event.kt`
- Pattern: Broadcast via WebSocket to all connected clients; persisted to database

## Entry Points

**Gateway Application:**

- Location: `backend/gateway-app/src/main/kotlin/com/openlogh/gateway/GatewayApplication.kt`
- Triggers: Spring Boot startup (port 8080)
- Responsibilities: HTTP API, authentication, world lifecycle orchestration, process management

**Game Application:**

- Location: `backend/game-app/src/main/kotlin/com/openlogh/OpenloghApplication.kt`
- Triggers: Spawned by `GameProcessOrchestrator` as separate JVM process (port 9001+)
- Responsibilities: Game logic execution, WebSocket event distribution, turn engine

**Frontend Entry:**

- Location: `frontend/src/app/layout.tsx` (root), `frontend/src/app/(game)/layout.tsx` (game)
- Triggers: Browser navigation to `/`
- Responsibilities: Auth routing, game initialization, store hydration

**World Activation Bootstrap:**

- Location: `backend/gateway-app/src/main/kotlin/com/openlogh/gateway/orchestrator/WorldActivationBootstrap.kt`
- Triggers: On gateway startup
- Responsibilities: Restore previously active worlds by reattaching to running game instances or spawning new ones

## Error Handling

**Strategy:** Hierarchical exception mapping with user-friendly messages

**Patterns:**

- **Business Logic Errors:** `CommandResult` (success flag + log messages) for command validation failures
- **API Errors:** Custom `ApiException` (code + message) caught by `@ControllerAdvice` for HTTP response mapping
- **WebSocket Errors:** Broadcast via `GameEventService` as client-side events
- **Validation Errors:** Position card gating in `CommandGating`, CP cost validation, cooldown checks
- **Process Errors:** `GameProcessOrchestrator` retries failed game-app startups with exponential backoff
- **Session Errors:** Missing `SessionState` returns 404; invalid session_id FK constraints prevent orphaned data

## Cross-Cutting Concerns

**Logging:**

- Backend: SLF4J via Kotlin `LoggerFactory.getLogger()` pattern
- Game-app logs written to `logs/game-{commitSha}.log` by `GameProcessOrchestrator`
- Frontend: Console logging in stores/components, no centralized aggregation

**Validation:**

- **Command Validation:** Constraints checked in `CommandExecutor.executeGeneralCommand()` before CP consumption
- **Entity Validation:** JPA `@NotNull`, `@Column(nullable=false)` at database level
- **Position Card Gating:** `CommandGating.canExecuteCommand()` checks command group against held cards
- **Resource Constraints:** CP cost, cooldown, CP pool size enforced at execution time

**Authentication:**

- **Gateway:** JWT token validation in `AuthService`, stored in `Authorization: Bearer` header
- **Game-app:** Session-scoped via `sessionId` path variable (WebSocket) or request parameter (HTTP)
- **Frontend:** Token stored in SessionStorage, refreshed via `AccountOAuthService` (Kakao OAuth)
- **Admin Routes:** Role-based access via `GatewayAdminAuthorizationService.isAdmin()` check

**WebSocket Communication:**

- **Command Channel:** `/app/command/{sessionId}/execute` → immediate ACK + async processing
- **Event Channel:** `/topic/world/{sessionId}/events` → broadcast to all connected clients
- **Battle Channel:** `/topic/world/{sessionId}/battle` → real-time tactical combat updates
- **Protocol:** STOMP over SockJS, managed by Spring WebSocket auto-configuration

---

_Architecture analysis: 2026-03-28_
