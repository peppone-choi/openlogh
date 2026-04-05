# Codebase Structure

**Analysis Date:** 2026-04-05

## Directory Layout

```
openlogh/
├���─ backend/                        # Spring Boot backend (Kotlin, Gradle multi-module)
│   ├── build.gradle.kts            # Root Gradle config
│   ├── settings.gradle.kts         # Module includes: shared, gateway-app, game-app
│   ├── shared/                     # Cross-process library (DTOs, JWT, errors)
│   ├── gateway-app/                # API gateway + process orchestrator (port 8080)
│   └── game-app/                   # Game engine + turn processor (ports 9001-9999)
├── frontend/                       # Next.js 15 frontend (TypeScript)
│   ├── package.json                # pnpm dependencies
│   ├── next.config.ts              # Next.js configuration
│   └── src/                        # Source code
├── docs/                           # Game design docs and reference materials
│   └── reference/                  # Original gin7 manual and Korean reference
├── docker-compose.yml              # Local dev services (PostgreSQL, Redis)
├── .github/workflows/              # CI/CD pipelines
└── CLAUDE.md                       # Project instructions
```

## Directory Purposes

### Backend: shared

**Location:** `backend/shared/src/main/kotlin/com/openlogh/`

- Purpose: Cross-process models shared between gateway-app and game-app
- Contains: DTOs, JWT verification, error definitions, game constants, Korean text utilities
- Key files:
  - `shared/dto/AccountDtos.kt` - Account-related DTOs
  - `shared/dto/AuthDtos.kt` - Authentication DTOs
  - `shared/dto/WorldDtos.kt` - World state DTOs
  - `shared/dto/AdminDtos.kt` - Admin panel DTOs
  - `shared/dto/JwtUserPrincipal.kt` - JWT user principal model
  - `shared/error/ErrorResponse.kt` - Standardized error response
  - `shared/security/JwtTokenVerifier.kt` - JWT token verification
  - `shared/GameConstants.kt` - Game-wide constants
  - `shared/model/ScenarioData.kt` - Scenario definitions
  - `util/JosaUtil.kt` - Korean postposition (josa) utility

### Backend: gateway-app

**Location:** `backend/gateway-app/src/main/kotlin/com/openlogh/gateway/`

- Purpose: HTTP API gateway, authentication, world lifecycle, process orchestration
- Contains: Controllers, services, entities, orchestrators, config

**Sub-packages:**
- `bootstrap/` - Startup hooks
  - `AdminUserBootstrap.kt` - Create default admin user on first run
- `config/` - Spring configuration
  - `GameProxyFilter.kt` - Catch-all `/api/**` reverse proxy to game-app (also proxies `/uploads/**`)
  - `JwtAuthenticationFilter.kt` - JWT auth filter for gateway
  - `JwtUtil.kt` - JWT utility for gateway
  - `SecurityConfig.kt` - Spring Security configuration
  - `WebClientConfig.kt` - WebFlux WebClient builder
- `controller/` - REST API endpoints
  - `AuthController.kt` - Login/register/token refresh
  - `AccountOAuthController.kt` - Kakao OAuth flow
  - `WorldController.kt` - World CRUD and lifecycle
  - `GameProxyController.kt` - Authenticated proxy to game-app
  - `PublicProxyController.kt` - Unauthenticated proxy endpoints
  - `AdminGameProxyController.kt` - Admin proxy to game-app
  - `AdminSystemController.kt` - System settings management
  - `AdminUsersController.kt` - User management
  - `ProcessOrchestratorController.kt` - Game process management API
  - `RouteRegistryController.kt` - World route inspection
  - `GameVersionController.kt` - Game version management
  - `StaticDataController.kt` - Static game data endpoints
  - `HealthController.kt` - Gateway health check
- `dto/` - Gateway-specific DTOs
  - `OrchestratorDtos.kt` - Process orchestration DTOs
  - `RouteDtos.kt` - Routing DTOs
  - `WorldStateResponse.kt` - World state response
- `entity/` - Gateway JPA entities
  - `AppUser.kt` - User account entity
  - `WorldState.kt` - World state entity (gateway copy)
  - `SystemSetting.kt` - System settings key-value entity
- `orchestrator/` - Game process management
  - `GameOrchestrator.kt` - Orchestrator interface
  - `GameProcessOrchestrator.kt` - JVM process spawner (non-Docker mode)
  - `GameContainerOrchestrator.kt` - Docker container orchestrator
  - `WorldActivationBootstrap.kt` - Restore active worlds on startup
- `repository/` - Spring Data JPA repositories
  - `AppUserRepository.kt`, `WorldStateRepository.kt`, `SystemSettingRepository.kt`
- `service/` - Business logic
  - `AuthService.kt` - Authentication logic
  - `AccountOAuthService.kt` - OAuth provider integration
  - `WorldService.kt` - World CRUD operations
  - `WorldRouteRegistry.kt` - In-memory world-to-gameapp-URL mapping
  - `GatewayAdminAuthorizationService.kt` - Admin role checks
  - `SystemSettingsService.kt` - System settings CRUD

### Backend: game-app

**Location:** `backend/game-app/src/main/kotlin/com/openlogh/`

- Purpose: Core game engine - all game logic, turn processing, commands, battles, AI
- Contains: Commands, engine services, entities, repositories, controllers, DTOs, config

**Sub-packages:**

- `command/` - Command pattern implementation (93 total commands)
  - `BaseCommand.kt` - Abstract base class (constraint checking, log helpers, game data helpers)
  - `GeneralCommand.kt` - Abstract general (individual) command
  - `NationCommand.kt` - Abstract nation (faction-level) command
  - `CommandExecutor.kt` - Orchestrates command execution pipeline
  - `CommandRegistry.kt` - Factory registry for all commands (Korean action code keys)
  - `CommandResult.kt` - Command result with success/fail + logs + JSON delta
  - `CommandResultApplicator.kt` - Applies JSON delta to entities
  - `CommandEnv.kt` - Command environment context (year, month, worldId, gameStor)
  - `CommandServices.kt` - Service dependencies injected into commands
  - `ArgSchema.kt` / `ArgSchemas.kt` - Command argument validation schemas
  - `LastTurn.kt` - Last turn data (multi-turn command tracking)
  - `constraint/` - Reusable validation constraints
    - `Constraint.kt` - Constraint interface and built-in constraints
    - `ConstraintChain.kt` - Constraint composition
    - `ConstraintHelper.kt` - Constraint builder helpers
  - `general/` - 55 general command implementations (Korean-named files)
    - Civil: `che_농지개간.kt`, `che_상업투자.kt`, `che_치안강화.kt`, `che_모병.kt`, `che_훈련.kt`, etc.
    - Military: `출병.kt`, `이동.kt`, `집합.kt`, `귀환.kt`, `화계.kt`, `첩보.kt`, etc.
    - Political: `등용.kt`, `임관.kt`, `건국.kt`, `선양.kt`, `모반시도.kt`, etc.
    - NPC/Special: `NPC능동.kt`, `CR건국.kt`, `CR맹훈련.kt`
    - Domestic: `DomesticCommand.kt`, `DomesticUtils.kt`
  - `nation/` - 38 nation command implementations
    - Resource: `che_포상.kt`, `che_몰수.kt`, `che_천도.kt`, `che_발령.kt`, etc.
    - Diplomacy: `che_선전포고.kt`, `che_종전제의.kt`, `che_불가침제의.kt`, etc.
    - Strategic: `che_급습.kt`, `che_수몰.kt`, `che_초토화.kt`, etc.
    - Research: `event_극병연구.kt`, `event_대검병연구.kt`, etc.
    - Emperor: `che_칭제.kt`, `che_천자맞이.kt`, `che_선양요구.kt`, etc.
  - `util/` - Command utilities
    - `CommandEventHandler.kt`, `StatChangeUtil.kt`, `UniqueItemLotteryUtil.kt`

- `engine/` - Game engine services
  - `TurnDaemon.kt` - Scheduled turn processor (5s interval)
  - `TurnService.kt` - Legacy turn processing (non-CQRS path)
  - `RealtimeService.kt` - Real-time command point mode
  - `EconomyService.kt` - Economic calculations (income, tax, trade)
  - `DiplomacyService.kt` - Diplomacy state transitions
  - `StatChangeService.kt` - Stat level-up calculations
  - `EventService.kt` / `EventActionService.kt` - Scheduled event system
  - `GeneralMaintenanceService.kt` - Officer aging, injury, NPC lifecycle
  - `UnificationService.kt` - Victory condition checking
  - `NpcSpawnService.kt` - NPC general spawning
  - `DistanceService.kt` - Map distance calculations
  - `SpecialAssignmentService.kt` - Special assignment handling
  - `TournamentBattle.kt` - Tournament battle logic
  - `UniqueLotteryService.kt` - Unique item lottery
  - `YearbookService.kt` - Yearbook snapshot generation
  - `DeterministicRng.kt` / `LiteHashDRBG.kt` / `RandUtil.kt` - Deterministic RNG
  - `CrewTypeAvailability.kt` - Crew type availability rules
  - `EmperorConstants.kt` - Emperor-related constants
  - `ai/` - NPC AI system
    - `GeneralAI.kt` - Individual NPC decision-making
    - `NationAI.kt` - Nation-level AI strategy
    - `NpcPolicy.kt` - NPC policy configuration
    - `AIContext.kt` - AI decision context
    - `DiplomacyState.kt` - AI diplomacy state
  - `event/` - Event action system
    - `EventAction.kt` - Event action interface
    - `EventActionRegistry.kt` - Registry of event actions
    - `actions/` - Event action implementations (organized by domain)
      - `betting/` - Betting events
      - `control/` - Game control events (city change, scout block, compound actions)
      - `economy/` - Economic events (income, disaster, trade)
      - `game/` - Game lifecycle events (new year, item loss, speciality assignment)
      - `misc/` - Log and notice actions
      - `npc/` - NPC creation and management events
  - `map/` - Map and movement
    - `MapDataService.kt` - Map topology data
    - `MovementService.kt` - Movement calculations
    - `ProximityDetector.kt` - Proximity detection
    - `TerrainType.kt` - Terrain type definitions
  - `modifier/` - Action modifier system
    - `ActionModifier.kt` - Modifier interface
    - `ModifierService.kt` - Modifier aggregation
    - `ItemModifiers.kt` - Equipment-based modifiers
    - `NationTypeModifiers.kt` - Nation type bonuses
    - `PersonalityModifiers.kt` - Personality trait modifiers
    - `SpecialModifiers.kt` - Special ability modifiers
    - `OfficerLevelModifier.kt` - Rank-based modifiers
    - `InheritBuffModifier.kt` - Inheritance buff modifiers
    - `TraitSelector.kt` / `TraitSpec.kt` - Trait selection
  - `trigger/` - Battle triggers
    - `GeneralTrigger.kt` - General-specific combat triggers
    - `TriggerCaller.kt` - Trigger invocation
  - `turn/` - Turn pipeline system
    - `TurnPipeline.kt` - Pipeline orchestrator (collects + orders steps)
    - `TurnStep.kt` - Step interface + `TurnContext` data class
    - `steps/` - 17 pipeline step implementations (ordered execution)
    - `cqrs/` - CQRS turn processing
      - `TurnCoordinator.kt` - CQRS lifecycle manager
      - `TurnLifecycleState.kt` - State enum (IDLE, LOADING, PROCESSING, PERSISTING, PUBLISHING, FAILED)
      - `TurnResult.kt` - Turn processing result with events
      - `TurnStatusService.kt` - Turn status tracking
      - `memory/` - In-memory world state
        - `InMemoryWorldState.kt` - Snapshot data classes (GeneralSnapshot, CitySnapshot, etc.)
        - `InMemoryWorldPorts.kt` - Read/write ports backed by in-memory maps
        - `InMemoryTurnProcessor.kt` - Pipeline executor with in-memory ports
        - `DirtyTracker.kt` - Tracks mutated entity IDs
        - `WorldStateLoader.kt` - Loads DB entities into snapshots
      - `persist/` - Persistence layer
        - `JpaBulkWriter.kt` - Bulk entity persistence
        - `JpaWorldPorts.kt` / `JpaWorldPortFactory.kt` - JPA-backed read/write ports
        - `CachingWorldPorts.kt` - Caching decorator
        - `WorldStatePersister.kt` - Persists dirty entities from in-memory state
        - `SnapshotEntityMapper.kt` - Snapshot <-> Entity mapping
        - `SequenceIdAllocator.kt` - ID allocation
      - `port/` - Port interfaces
        - `WorldReadPort.kt` / `WorldWritePort.kt` - Hexagonal port interfaces
        - `IdAllocator.kt` - ID allocator interface
  - `war/` - Battle engine
    - `BattleEngine.kt` - Core battle calculation engine
    - `BattleService.kt` - Battle orchestration
    - `BattleTrigger.kt` - Battle trigger system
    - `FieldBattleService.kt` / `FieldBattleTrigger.kt` - Field battle logic
    - `WarAftermath.kt` - Post-battle consequences
    - `WarFormula.kt` - Battle formulas
    - `WarUnit.kt` / `WarUnitCity.kt` / `WarUnitGeneral.kt` - War unit abstractions
    - `WarUnitTrigger.kt` - War unit trigger interface
    - `trigger/` - Combat trigger implementations (8 triggers)

- `entity/` - JPA entities (34 entities)
  - Core: `WorldState.kt`, `General.kt`, `Nation.kt`, `City.kt`, `Troop.kt`
  - Auth: `AppUser.kt`
  - Diplomacy: `Diplomacy.kt`
  - Turn: `GeneralTurn.kt`, `NationTurn.kt`
  - Event: `Event.kt`
  - History: `GameHistory.kt`, `WorldHistory.kt`, `YearbookHistory.kt`
  - Emperor: `Emperor.kt`
  - Ranking: `RankData.kt`, `HallOfFame.kt`, `Record.kt`, `GeneralRecord.kt`
  - Communication: `Message.kt`, `Board.kt`, `BoardComment.kt`
  - Economy: `Auction.kt`, `AuctionBid.kt`, `Betting.kt`, `BetEntry.kt`
  - NPC: `SelectPool.kt`, `OldGeneral.kt`, `OldNation.kt`
  - Other: `NationFlag.kt`, `Vote.kt`, `VoteCast.kt`, `TrafficSnapshot.kt`, `Tournament.kt`, `GeneralAccessLog.kt`

- `repository/` - Spring Data JPA repositories (34 repositories, one per entity)

- `service/` - Application services (41 services)
  - Key services: `CommandService.kt`, `GameEventService.kt`, `GeneralService.kt`, `CityService.kt`, `NationService.kt`, `WorldService.kt`, `AuthService.kt`, `AccountService.kt`, `MapService.kt`, `TurnManagementService.kt`, `TroopService.kt`, `AuctionService.kt`, etc.

- `controller/` - REST API controllers (34 controllers)
  - Key controllers: `CommandController.kt`, `GeneralController.kt`, `CityController.kt`, `NationController.kt`, `WorldController.kt`, `AuthController.kt`, `MapController.kt`, `TurnController.kt`, `RealtimeController.kt`, `TroopController.kt`, `AdminController.kt`, etc.
  - `InternalHealthController.kt` - Health check for gateway polling

- `dto/` - Data transfer objects (22 DTO files)
- `config/` - Spring configuration
  - `SecurityConfig.kt`, `WebConfig.kt`, `WebSocketConfig.kt`, `JwtAuthenticationFilter.kt`, `JwtUtil.kt`, `GlobalExceptionHandler.kt`
- `model/` - Game data models
  - `CityConst.kt` - Map city topology constants
  - `CrewType.kt` - Crew type definitions
  - `ScenarioData.kt` - Scenario data model
- `bootstrap/` - Startup hooks
  - `BootstrapExitRunner.kt` - Bootstrap exit handling
- `util/` - Utilities
  - `JosaUtil.kt` - Korean postposition utility

### Frontend

**Location:** `frontend/src/`

- `app/` - Next.js App Router pages (route groups)
  - `layout.tsx` - Root layout (global providers, theme)
  - `globals.css` - Global CSS styles
  - `(auth)/` - Authentication routes
    - `layout.tsx` - Auth layout
    - `login/page.tsx` - Login page
    - `register/page.tsx` - Registration
    - `account/page.tsx` - Account settings
    - `auth/kakao/callback/page.tsx` - Kakao OAuth callback
    - `privacy/page.tsx`, `terms/page.tsx` - Legal pages
  - `(lobby)/` - Lobby routes
    - `layout.tsx` - Lobby layout
    - `lobby/page.tsx` - World selection lobby
    - `lobby/join/page.tsx` - Join game
    - `lobby/select-npc/page.tsx` - NPC selection
    - `lobby/select-pool/page.tsx` - Character pool selection
  - `(game)/` - Main game routes (40+ pages)
    - `layout.tsx` - Game layout (auth guard, sidebar, WebSocket, hotkeys)
    - `page.tsx` - Game dashboard
    - `commands/page.tsx` - Command input
    - `map/page.tsx` - Game map
    - `city/page.tsx` - City detail
    - `general/page.tsx` - Player general info
    - `generals/page.tsx` - General list
    - `generals/[id]/page.tsx` - General detail (dynamic route)
    - `nation/page.tsx` - Nation detail
    - `nations/page.tsx` - Nation list
    - `diplomacy/page.tsx` - Diplomacy management
    - `global-diplomacy/page.tsx` - Global diplomacy overview
    - `troop/page.tsx` - Troop management
    - `board/page.tsx` - Discussion board
    - `messages/page.tsx` - Private messages
    - `battle/page.tsx` - Battle monitoring
    - `battle-simulator/page.tsx` - Battle simulation
    - `battle-center/page.tsx` - Battle center
    - `vote/page.tsx` - Voting
    - `vote/[id]/page.tsx` - Vote detail
    - `auction/page.tsx` - Item auction
    - `tournament/page.tsx` - Tournament
    - `betting/page.tsx` - Betting
    - Plus: `chief/`, `spy/`, `personnel/`, `internal-affairs/`, `npc-control/`, `npc-list/`, `hall-of-fame/`, `history/`, `inherit/`, `my-page/`, `dynasty/`, `emperor/`, `best-generals/`, `traffic/`, `processing/`, `superior/`, `nation-betting/`, `nation-cities/`, `nation-generals/`, `nation-finance/`
  - `(admin)/` - Admin routes
    - `layout.tsx` - Admin layout
    - `admin/page.tsx` - Admin dashboard
    - `admin/members/page.tsx`, `admin/users/page.tsx` - User management
    - `admin/game-versions/page.tsx` - Game version management
    - `admin/diplomacy/page.tsx` - Diplomacy admin
    - `admin/logs/page.tsx` - Log viewer
    - `admin/select-pool/page.tsx` - Select pool management
    - `admin/statistics/page.tsx` - Statistics
  - `(tutorial)/` - Tutorial routes
    - `tutorial/layout.tsx` - Tutorial layout
    - `tutorial/page.tsx` - Tutorial entry
    - Plus: `main/`, `create/`, `command/`, `city/`, `nation/`, `diplomacy/`, `battle/`, `complete/`

- `components/` - React components
  - `app-sidebar.tsx` - Main sidebar navigation
  - `top-bar.tsx` - Top navigation bar
  - `mobile-menu-sheet.tsx` - Mobile menu
  - `responsive-sheet.tsx` - Responsive sheet wrapper
  - `game/` - Game-specific components (40+ components)
    - `game-dashboard.tsx` - Main game dashboard
    - `command-panel.tsx` - Command submission panel
    - `command-select-form.tsx` - Command selection form
    - `command-arg-form.tsx` - Command argument form
    - `map-viewer.tsx` - 2D map viewer (Konva canvas)
    - `map-canvas.tsx` - Canvas rendering
    - `map-3d/` - 3D map components (Three.js / React Three Fiber)
      - `scene/Map3dScene.tsx` - 3D scene setup
      - `terrain/TerrainMesh.tsx` - Terrain rendering
      - `city/CityModel.tsx` - City 3D models
      - `nation/NationOverlay.tsx` - Nation territory overlay
      - `camera/CameraController.tsx` - Camera controls
      - `effects/SeasonEffects.tsx` - Seasonal visual effects
      - `interaction/HoverTooltip.tsx` - Hover tooltips
      - `units/UnitMarkers3d.tsx` - Unit markers
    - `general-basic-card.tsx` - General info card
    - `city-basic-card.tsx` - City info card
    - `nation-basic-card.tsx` - Nation info card
    - `turn-timer.tsx` - Turn countdown timer
    - `battle-log-entry.tsx` - Battle log display
    - `tiptap-editor.tsx` - Rich text editor (TipTap)
    - `record-zone.tsx` - Activity record display
    - Plus: faction-flag, equipment-browser, crew-type-browser, stat-bar, sammo-bar, etc.
  - `tutorial/` - Tutorial overlay components
    - `tutorial-provider.tsx`, `guide-overlay.tsx`, `step-controller.tsx`, `tutorial-spotlight.tsx`, `tutorial-tooltip.tsx`
  - `auth/` - Auth components
    - `server-status-card.tsx` - Server status display
  - `ui/` - Base UI components (Radix-based)
    - Standard: `button.tsx`, `card.tsx`, `dialog.tsx`, `input.tsx`, `select.tsx`, `table.tsx`, `tabs.tsx`, `sheet.tsx`, `sidebar.tsx`, `tooltip.tsx`, etc.
    - `8bit/` - Retro/8-bit themed UI variant (full component set with `styles/retro.css`)

- `stores/` - Zustand state management
  - `authStore.ts` - Authentication state (JWT token, user, login/logout)
  - `worldStore.ts` - World selection state (currentWorld, world list)
  - `generalStore.ts` - Player general state (myGeneral, fetchMyGeneral)
  - `gameStore.ts` - Game data cache (cities, nations, generals, diplomacy, mapData)
  - `tutorialStore.ts` - Tutorial flow state

- `hooks/` - React hooks
  - `useWebSocket.ts` - WebSocket connection management (STOMP)
  - `useHotkeys.ts` - Keyboard shortcut bindings
  - `useMap3d.ts` - 3D map state hook
  - `useSoundEffects.ts` - Audio feedback
  - `useDebouncedCallback.ts` - Debounce utility
  - `use-mobile.ts` - Mobile detection

- `lib/` - Utility functions and API client
  - `api.ts` - Axios HTTP client configuration (base URL, auth interceptor)
  - `gameApi.ts` - Game API endpoints (typed wrappers for all REST endpoints)
  - `websocket.ts` - WebSocket connection setup (STOMP + SockJS)
  - `utils.ts` - General utilities (cn, clsx)
  - `game-utils.ts` - Game-specific utilities
  - `formatLog.ts` - Log message formatting
  - `formatBattleLog.ts` - Battle log formatting
  - `image.ts` - Image URL helpers
  - `josa.ts` - Korean postposition utility (frontend)
  - `map-constants.ts` - Map rendering constants
  - `map-pathfinding.ts` - Client-side pathfinding
  - `map-3d-utils.ts` - 3D map utilities
  - `income-calc.ts` - Income calculation (client-side)
  - `interception-utils.ts` - Interception calculation utils
  - `api-error.ts` - API error handling
  - `auth-error.ts` - Auth error handling
  - `auth-features.ts` - Auth feature detection
  - `gameLogDate.ts` - Game date formatting
  - `tutorial-api-guard.ts` - Tutorial mode API guard

- `types/` - TypeScript type definitions
  - `index.ts` - All shared types (User, WorldState, Nation, City, General, Troop, Diplomacy, etc.)

- `contexts/` - React contexts
  - `AdminWorldContext.tsx` - Admin world selection context
  - `sheet-context.tsx` - Sheet/modal state context

- `data/` - Static data
  - `tutorial/` - Tutorial mock data and step definitions

## Key File Locations

**Entry Points:**
- `backend/gateway-app/src/main/kotlin/com/openlogh/gateway/GatewayApplication.kt`: Gateway Spring Boot main
- `backend/game-app/src/main/kotlin/com/openlogh/OpensamApplication.kt`: Game-app Spring Boot main
- `frontend/src/app/layout.tsx`: Frontend root layout
- `frontend/src/app/(game)/layout.tsx`: Game layout (auth + state hydration)

**Configuration:**
- `backend/build.gradle.kts`: Root Gradle config
- `backend/settings.gradle.kts`: Module includes (shared, gateway-app, game-app)
- `backend/gateway-app/src/main/resources/application.yml`: Gateway Spring config
- `backend/game-app/src/main/resources/application.yml`: Game Spring config
- `backend/game-app/src/main/resources/db/migration/`: Flyway SQL migrations
- `frontend/next.config.ts`: Next.js config
- `frontend/tsconfig.json`: TypeScript config
- `docker-compose.yml`: PostgreSQL + Redis for local dev

**Core Game Logic:**
- `backend/game-app/src/main/kotlin/com/openlogh/command/CommandExecutor.kt`: Command execution pipeline
- `backend/game-app/src/main/kotlin/com/openlogh/command/CommandRegistry.kt`: Command factory registry
- `backend/game-app/src/main/kotlin/com/openlogh/engine/TurnDaemon.kt`: Turn scheduler
- `backend/game-app/src/main/kotlin/com/openlogh/engine/turn/TurnPipeline.kt`: Turn pipeline
- `backend/game-app/src/main/kotlin/com/openlogh/engine/turn/cqrs/TurnCoordinator.kt`: CQRS turn coordinator
- `backend/game-app/src/main/kotlin/com/openlogh/service/GameEventService.kt`: WebSocket event hub

**Testing:**
- `backend/game-app/src/test/kotlin/com/openlogh/`: Backend tests (50+ test files)
- `frontend/src/**/*.test.ts` / `*.test.tsx`: Frontend tests (co-located)

## Naming Conventions

**Files (Backend - Kotlin):**
- PascalCase for classes: `CommandExecutor.kt`, `TurnPipeline.kt`
- Korean names for command implementations: `출병.kt`, `che_농지개간.kt`, `Nation휴식.kt`
- `che_` prefix: Legacy parity commands from OpenSamguk
- `event_` prefix: Event-triggered commands
- `CR` prefix: Special NPC commands
- Snapshot data classes: `GeneralSnapshot`, `CitySnapshot`, `NationSnapshot`

**Files (Frontend - TypeScript):**
- kebab-case for components: `command-panel.tsx`, `game-dashboard.tsx`
- PascalCase for 3D components: `CityModel.tsx`, `TerrainMesh.tsx`
- camelCase for hooks: `useWebSocket.ts`, `useHotkeys.ts`
- camelCase for stores: `gameStore.ts`, `authStore.ts`
- camelCase for utilities: `gameApi.ts`, `formatLog.ts`

**Directories:**
- Backend: lowercase package names (`command/`, `engine/`, `entity/`, `service/`)
- Frontend: kebab-case (`map-3d/`, `battle-center/`)
- Route groups: parenthesized `(game)/`, `(auth)/`, `(lobby)/`, `(admin)/`, `(tutorial)/`

## Where to Add New Code

**New General Command:**
- Implementation: `backend/game-app/src/main/kotlin/com/openlogh/command/general/{name}.kt`
- Register in: `backend/game-app/src/main/kotlin/com/openlogh/command/CommandRegistry.kt` (init block)
- Add ArgSchema if needed: `backend/game-app/src/main/kotlin/com/openlogh/command/ArgSchemas.kt`
- Test: `backend/game-app/src/test/kotlin/com/openlogh/command/`

**New Nation Command:**
- Implementation: `backend/game-app/src/main/kotlin/com/openlogh/command/nation/{name}.kt`
- Register in: `backend/game-app/src/main/kotlin/com/openlogh/command/CommandRegistry.kt` (init block)
- Test: `backend/game-app/src/test/kotlin/com/openlogh/command/`

**New Turn Pipeline Step:**
- Implementation: `backend/game-app/src/main/kotlin/com/openlogh/engine/turn/steps/{Name}Step.kt`
- Implement `TurnStep` interface with `order` property for execution position
- Auto-registered via Spring `@Component` annotation
- Test: `backend/game-app/src/test/kotlin/com/openlogh/engine/`

**New Engine Service:**
- Implementation: `backend/game-app/src/main/kotlin/com/openlogh/engine/{ServiceName}.kt`
- Use `@Service` or `@Component` annotation
- Test: `backend/game-app/src/test/kotlin/com/openlogh/engine/`

**New JPA Entity:**
- Entity: `backend/game-app/src/main/kotlin/com/openlogh/entity/{Name}.kt`
- Repository: `backend/game-app/src/main/kotlin/com/openlogh/repository/{Name}Repository.kt`
- Migration: `backend/game-app/src/main/resources/db/migration/V{next}__description.sql`
- Add CQRS snapshot if needed: `backend/game-app/src/main/kotlin/com/openlogh/engine/turn/cqrs/memory/InMemoryWorldState.kt`

**New REST API Endpoint:**
- Controller: `backend/game-app/src/main/kotlin/com/openlogh/controller/{Name}Controller.kt`
- Service: `backend/game-app/src/main/kotlin/com/openlogh/service/{Name}Service.kt`
- DTO: `backend/game-app/src/main/kotlin/com/openlogh/dto/{Name}Dtos.kt`

**New Frontend Page:**
- Page: `frontend/src/app/(game)/{route-name}/page.tsx`
- Add navigation item in `frontend/src/app/(game)/layout.tsx` (navSections array)
- Components: `frontend/src/components/game/{component-name}.tsx`
- Tests: co-located as `{component-name}.test.ts` or `{component-name}.test.tsx`

**New Frontend Component:**
- Game component: `frontend/src/components/game/{component-name}.tsx`
- UI primitive: `frontend/src/components/ui/{component-name}.tsx`
- 8-bit UI variant: `frontend/src/components/ui/8bit/{component-name}.tsx`
- Test: co-located as `{component-name}.test.tsx`

**New Zustand Store:**
- Store: `frontend/src/stores/{name}Store.ts`
- Test: `frontend/src/stores/{name}Store.test.ts`

**New Custom Hook:**
- Hook: `frontend/src/hooks/{hookName}.ts`
- Test: `frontend/src/hooks/{hookName}.test.ts`

**New API Endpoint (Frontend):**
- Add typed wrapper in: `frontend/src/lib/gameApi.ts`
- Add types in: `frontend/src/types/index.ts`

**Shared DTOs (cross-process):**
- Location: `backend/shared/src/main/kotlin/com/openlogh/shared/dto/{Name}Dtos.kt`

**New Event Action:**
- Implementation: `backend/game-app/src/main/kotlin/com/openlogh/engine/event/actions/{category}/{Name}Action.kt`
- Register in: `backend/game-app/src/main/kotlin/com/openlogh/engine/event/EventActionRegistry.kt`

**New Modifier:**
- Implementation: `backend/game-app/src/main/kotlin/com/openlogh/engine/modifier/{Name}Modifiers.kt`
- Register in: `backend/game-app/src/main/kotlin/com/openlogh/engine/modifier/ModifierService.kt`

## Special Directories

**`backend/game-app/src/main/resources/db/migration/`:**
- Purpose: Flyway SQL migration scripts
- Generated: No (hand-written)
- Committed: Yes
- Naming: `V{version}__{description}.sql`

**`docs/reference/`:**
- Purpose: Original gin7 (Galactic Heroes VII) manual and Korean reference materials
- Generated: No
- Committed: Yes
- Used for: Game mechanics fidelity reference

**`frontend/src/components/ui/8bit/`:**
- Purpose: Retro 8-bit themed UI component variants (full shadcn/ui re-skin)
- Generated: No (customized)
- Committed: Yes
- Includes: `styles/retro.css` for pixel-art styling

**`frontend/src/data/tutorial/`:**
- Purpose: Tutorial mock data and step definitions for offline tutorial mode
- Generated: No
- Committed: Yes

---

*Structure analysis: 2026-04-05*
