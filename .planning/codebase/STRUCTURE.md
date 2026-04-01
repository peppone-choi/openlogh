# Codebase Structure

**Analysis Date:** 2026-03-31

## Directory Layout

```
openlogh/
├── backend/                               # Spring Boot 3 (Kotlin) multi-module backend
│   ├── shared/                            # Cross-app shared library (DTOs, JWT, constants)
│   ├── gateway-app/                       # HTTP API gateway (port 8080)
│   ├── game-app/                          # Game engine (port 9001+, spawned by gateway)
│   ├── build.gradle.kts                   # Root Gradle config (Kotlin 2.1, Spring Boot 3.4.2)
│   ├── settings.gradle.kts                # Module includes: shared, gateway-app, game-app
│   └── gradlew / gradlew.bat             # Gradle wrapper
├── frontend/                              # Next.js 15 React application
│   ├── src/
│   │   ├── app/                           # App Router (4 route groups, 50+ pages)
│   │   ├── components/                    # React components (game/, auth/, ui/)
│   │   ├── stores/                        # Zustand state management (7 stores)
│   │   ├── lib/                           # API clients, utilities, WebSocket
│   │   ├── hooks/                         # Custom React hooks
│   │   ├── types/                         # TypeScript type definitions
│   │   └── contexts/                      # React context providers
│   ├── e2e/                               # Playwright E2E tests
│   ├── package.json                       # Dependencies
│   ├── next.config.ts                     # Next.js config
│   ├── tsconfig.json                      # TypeScript config (path alias: @/* -> ./src/*)
│   └── vitest.config.ts                   # Vitest config
├── nginx/                                 # Reverse proxy config
│   └── nginx.conf                         # Routes: /api/ -> gateway, /ws* -> game, / -> frontend
├── docker-compose.yml                     # Full stack: postgres, redis, bootstrap, gateway, game, frontend, nginx
├── docs/                                  # Design & reference documentation
│   ├── 01-plan/                           # Game design documents
│   ├── 03-analysis/                       # Technical analysis
│   └── reference/                         # Original gin7 manual, LOGH reference
├── logh-game/                             # Original gin7 game data files (reference assets)
├── .planning/                             # GSD planning artifacts
│   ├── codebase/                          # Codebase analysis documents (ARCHITECTURE.md, etc.)
│   └── phases/                            # Phase plans and execution records
├── CLAUDE.md                              # Project instructions for AI assistants
└── README.md                              # Project overview
```

## Backend Module: shared

**Location:** `backend/shared/src/main/kotlin/com/openlogh/`

**Purpose:** Cross-process contracts shared by both gateway-app and game-app. Pure library, no Spring Boot auto-config.

**Package structure:**
```
com.openlogh/
├── shared/
│   ├── dto/
│   │   ├── AccountDtos.kt                # Account-related request/response types
│   │   ├── AdminDtos.kt                  # Admin panel DTOs
│   │   ├── AuthDtos.kt                   # Login/register DTOs
│   │   ├── JwtUserPrincipal.kt           # JWT-decoded user principal
│   │   └── WorldDtos.kt                  # CreateWorldRequest, ResetWorldRequest
│   ├── model/
│   │   └── ScenarioData.kt               # Scenario definition model
│   ├── security/
│   │   └── JwtTokenVerifier.kt           # JWT validation (used by both apps)
│   ├── error/
│   │   └── ErrorResponse.kt              # Standard error response shape
│   └── GameConstants.kt                  # Global game constants
└── util/
    └── JosaUtil.kt                       # Korean grammar particle logic (조사)
```

**Key files:**
- `shared/dto/WorldDtos.kt`: `CreateWorldRequest`, `ResetWorldRequest` — used by gateway to create/reset worlds via game-app
- `shared/security/JwtTokenVerifier.kt`: Shared JWT verification logic used by both gateway and game-app
- `shared/GameConstants.kt`: Game-wide constants

## Backend Module: gateway-app

**Location:** `backend/gateway-app/src/main/kotlin/com/openlogh/gateway/`

**Purpose:** Public HTTP API entry point. Handles authentication, world lifecycle management, game-app process orchestration, and reverse-proxying game API calls to the correct game-app instance.

**Package structure:**
```
com.openlogh.gateway/
├── controller/
│   ├── AuthController.kt                 # POST /api/auth/login, /register, /me
│   ├── AccountOAuthController.kt         # Kakao OAuth integration endpoints
│   ├── WorldController.kt                # CRUD /api/worlds, activate/deactivate/reset
│   ├── GameProxyController.kt            # Reverse proxy: /api/game/{worldId}/** -> game-app
│   ├── PublicProxyController.kt          # Public game data proxy (no auth required)
│   ├── AdminGameProxyController.kt       # Admin proxy to game-app
│   ├── AdminSystemController.kt          # System admin (settings, maintenance)
│   ├── AdminUsersController.kt           # User management admin
│   ├── ProcessOrchestratorController.kt  # Game instance management API
│   ├── GameVersionController.kt          # Game version listing
│   ├── RouteRegistryController.kt        # World route debugging
│   ├── StaticDataController.kt           # Static game data endpoints
│   └── HealthController.kt               # /internal/health
├── service/
│   ├── AuthService.kt                    # JWT issuance, password verification
│   ├── AccountOAuthService.kt            # Kakao OAuth flow
│   ├── WorldService.kt                   # World CRUD, activation metadata
│   ├── WorldRouteRegistry.kt             # ConcurrentHashMap<worldId, baseUrl> routing table
│   ├── GatewayAdminAuthorizationService.kt # Admin role checking
│   └── SystemSettingsService.kt          # System-wide settings
├── orchestrator/
│   ├── GameOrchestrator.kt               # Interface: attachWorld, ensureVersion, detachWorld, statuses
│   ├── GameProcessOrchestrator.kt        # Local JVM impl (ports 9001-9999, enabled when docker.enabled=false)
│   ├── GameContainerOrchestrator.kt      # Docker container impl (enabled when docker.enabled=true)
│   └── WorldActivationBootstrap.kt       # ApplicationRunner: restore active worlds on startup
├── entity/
│   └── WorldState.kt                     # JPA entity for world_state table (gateway's view)
├── repository/                            # Gateway-local JPA repositories
├── config/                                # Security, JWT, WebClient config
├── dto/                                   # Gateway-specific DTOs (AttachWorldProcessRequest, GameInstanceStatus, etc.)
└── bootstrap/                             # Admin account bootstrap
```

**Key files:**
- `controller/GameProxyController.kt`: The reverse proxy — extracts worldId, resolves baseUrl from `WorldRouteRegistry`, forwards via `WebClient`
- `controller/WorldController.kt`: World CRUD — create delegates to game-app, then registers route
- `orchestrator/GameProcessOrchestrator.kt`: Spawns game-app as `java -jar` process, polls `/internal/health`, manages ports 9001-9999
- `orchestrator/GameContainerOrchestrator.kt`: Spawns game-app as Docker container via `docker run`, health-checks via container DNS
- `orchestrator/WorldActivationBootstrap.kt`: Restores active worlds on gateway startup with parallel warmup and retry
- `service/WorldRouteRegistry.kt`: In-memory `ConcurrentHashMap<Long, String>` mapping worldId to game-app baseUrl

**Configuration:** `backend/gateway-app/src/main/resources/application.yml`
- Port: 8080
- DB: PostgreSQL (`jdbc:postgresql://localhost:5432/openlogh`)
- Redis: localhost:6379
- JWT: HS256 with configurable secret and 24h expiration
- Flyway: disabled (game-app runs migrations)
- Orchestrator: configurable health timeout (180s), restore retries (3), retry delay (30s)

## Backend Module: game-app

**Location:** `backend/game-app/src/main/kotlin/com/openlogh/`

**Purpose:** Core game simulation engine. Each instance handles one or more game worlds (identified by `commitSha`). Contains all game logic, command execution, turn processing, and WebSocket broadcasting.

**Package structure:**
```
com.openlogh/
├── entity/                                # 39 JPA entities (game state)
│   ├── Officer.kt                        # Player/NPC character (8-stat system, equipment, rank)
│   ├── Planet.kt                         # Territory unit (resources, defenses, population)
│   ├── Faction.kt                        # Political entity (treasury, policies, spy intel)
│   ├── Fleet.kt                          # Military unit (ships by class)
│   ├── SessionState.kt                   # Game world container (year/month, config, meta)
│   ├── Event.kt                          # Immutable game event record
│   ├── PositionCard.kt                   # Officer job authority card
│   ├── OfficerTurn.kt                    # Queued turn action
│   ├── FactionTurn.kt                    # Queued faction-level action
│   ├── Diplomacy.kt                      # Faction-to-faction relations
│   ├── Sovereign.kt                      # Faction leader entity
│   ├── Board.kt / BoardComment.kt        # In-game bulletin board
│   ├── Message.kt                        # Player-to-player messaging
│   ├── Vote.kt / VoteCast.kt            # In-game voting
│   ├── Auction.kt / AuctionBid.kt       # Item auction system
│   ├── Tournament.kt                     # Tournament system
│   ├── Betting.kt / BetEntry.kt         # Betting system
│   ├── SelectPool.kt                     # NPC selection pool
│   ├── TypeAliases.kt                    # Legacy aliases: General=Officer, City=Planet, Nation=Faction
│   ├── EntityCompat.kt                   # Backward-compatibility field accessors
│   └── ... (AppUser, RankData, Record, GameHistory, etc.)
├── repository/                            # 39 JPA repositories (one per entity)
│   ├── OfficerRepository.kt             # findBySessionId, findByNationId, findBySessionIdAndCommandEndTimeBefore
│   ├── PlanetRepository.kt              # findBySessionId
│   ├── FactionRepository.kt             # findBySessionId
│   ├── SessionStateRepository.kt        # findByCommitSha
│   ├── PositionCardRepository.kt        # Position card queries
│   ├── RepositoryCompat.kt              # Legacy repository aliases
│   └── ... (39 total)
├── controller/                            # 38 REST controllers
│   ├── CommandController.kt              # Turn reservation, command tables, real-time execute
│   ├── OfficerController.kt             # Officer CRUD, stats, search
│   ├── PlanetController.kt              # Planet info, resources
│   ├── FactionController.kt             # Faction info, members
│   ├── FleetController.kt               # Fleet management
│   ├── CharacterController.kt           # Character creation/deletion
│   ├── DiplomacyController.kt           # Diplomatic actions
│   ├── BoardController.kt               # Bulletin board CRUD
│   ├── MessageController.kt             # Player messaging
│   ├── WorldCreationController.kt       # POST /api/worlds (called by gateway)
│   ├── ScenarioController.kt            # Scenario listing
│   ├── InternalHealthController.kt      # GET /internal/health (polled by gateway)
│   ├── AdminController.kt               # Admin game management
│   ├── RealtimeController.kt            # Real-time mode status/control
│   └── ... (38 total: Auction, Battle, Fleet, History, Map, NPC, Org, Position, etc.)
├── websocket/                             # 3 WebSocket controllers (STOMP)
│   ├── CommandWebSocketController.kt     # /app/command/{sessionId}/execute -> ACK + queue
│   ├── BattleWebSocketController.kt      # /app/battle/{sessionId}/command -> battle resolution -> broadcast
│   └── TacticalWebSocketController.kt   # Real-time tactical battle commands
├── command/                               # CQRS command system (112 commands total)
│   ├── CommandRegistry.kt                # Factory registry: 78 general + 34 nation command lambdas
│   ├── CommandExecutor.kt                # Dispatch: cooldown -> position card -> CP -> create -> run -> apply
│   ├── CommandEnv.kt                     # Execution context (year, month, worldId, realtimeMode)
│   ├── CommandResult.kt                  # Result type (success, logs, message)
│   ├── BaseCommand.kt                    # Abstract base for general commands
│   ├── NationCommand.kt                  # Abstract base for nation commands
│   ├── CommandServices.kt                # Injectable service bundle for commands
│   ├── general/                          # 78 general (officer-level) command classes
│   │   ├── 휴식.kt                       # Default rest action
│   │   ├── che_워프항행.kt               # Warp navigation
│   │   ├── che_정찰.kt                   # Reconnaissance
│   │   ├── 발령.kt                       # Officer assignment
│   │   ├── 부대결성.kt                   # Fleet formation
│   │   └── ... (78 total, Korean names)
│   ├── nation/                           # 34 nation (faction-level) command classes
│   │   ├── 승진.kt                       # Promotion
│   │   ├── 선전포고.kt                   # Declaration of war
│   │   ├── 국가목표설정.kt               # National goal setting
│   │   └── ... (34 total, Korean names)
│   └── constraint/                       # Command validation constraints
├── engine/                                # 90+ game logic engine files
│   ├── TurnDaemon.kt                     # @Scheduled tick (1s) — processes all worlds
│   ├── TurnService.kt                    # Legacy turn processing (non-CQRS)
│   ├── RealtimeService.kt               # Real-time command execution + CP regen
│   ├── CommandPointService.kt            # PCP/MCP consumption and regen
│   ├── EconomyService.kt                # Resource calculations
│   ├── DiplomacyService.kt              # Faction relations
│   ├── EventService.kt                  # Game event creation/persistence
│   ├── EventActionRegistry.kt           # Event type to handler mapping
│   ├── DistanceService.kt               # Planet distance calculations
│   ├── UnificationService.kt            # Victory condition checking
│   ├── NpcSpawnService.kt               # NPC officer spawning
│   ├── CoupExecutionService.kt          # Rebellion/coup mechanics
│   ├── OfficerMaintenanceService.kt     # Officer upkeep processing
│   ├── AgeGrowthService.kt              # Officer aging
│   ├── SafeZoneService.kt               # Safe zone enforcement
│   ├── SpecialAssignmentService.kt      # Special mission handling
│   ├── StatChangeService.kt             # Stat modification engine
│   ├── TournamentBattle.kt              # Tournament combat
│   ├── UniqueLotteryService.kt          # Unique item lottery
│   ├── FezzanNeutralityService.kt       # Fezzan neutral faction rules
│   ├── YearbookService.kt               # Historical yearbook generation
│   ├── SovereignConstants.kt            # Sovereign/ruler constants
│   ├── CrewTypeAvailability.kt          # Ship class availability rules
│   ├── GridEntryValidator.kt            # Grid entry validation
│   ├── DeterministicRng.kt              # Deterministic RNG for replay
│   ├── LiteHashDRBG.kt                  # Hash-based deterministic RNG
│   ├── RandUtil.kt                       # Random utilities
│   ├── EngineCompat.kt                  # Legacy compatibility
│   ├── turn/                             # CQRS turn processing pipeline
│   │   └── cqrs/
│   │       ├── TurnCoordinator.kt        # Orchestrates: LOAD -> PROCESS -> PERSIST -> PUBLISH
│   │       ├── TurnLifecycleState.kt     # IDLE, LOADING, PROCESSING, PERSISTING, PUBLISHING, FAILED
│   │       ├── TurnResult.kt             # Turn processing result
│   │       ├── TurnStatusService.kt      # Lifecycle state tracking
│   │       ├── memory/
│   │       │   ├── InMemoryTurnProcessor.kt  # Processes commands against in-memory state
│   │       │   ├── InMemoryWorldState.kt     # Loaded world entities in memory
│   │       │   ├── WorldStateLoader.kt       # Loads all entities from DB for a world
│   │       │   └── DirtyTracker.kt           # Tracks modified entities for selective persist
│   │       └── persist/
│   │           ├── WorldStatePersister.kt    # Writes dirty entities back to DB
│   │           └── JpaWorldPortFactory.kt    # JPA port for world state access
│   ├── tactical/                         # Real-time tactical battle system (20 files)
│   │   ├── TacticalBattleEngine.kt       # Core battle simulation
│   │   ├── TacticalBattleSession.kt      # Active battle session state
│   │   ├── TacticalSessionManager.kt     # Battle session lifecycle
│   │   ├── TacticalGrid.kt              # Grid-based positioning
│   │   ├── TacticalFleet.kt             # Fleet in tactical mode
│   │   ├── TacticalUnit.kt              # Individual unit in battle
│   │   ├── TacticalOrder.kt             # Battle orders
│   │   ├── TacticalModels.kt            # Battle data models
│   │   ├── TacticalShipClass.kt         # Ship class stats for battle
│   │   ├── TacticalTurnScheduler.kt     # Battle round scheduling
│   │   ├── TacticalResultWriteback.kt   # Persist battle results to entities
│   │   ├── TacticalGameSession.kt       # Game session for tactical mode
│   │   ├── TacticalAI.kt               # AI for NPC fleets in battle
│   │   ├── TacticalSystems.kt           # Battle subsystems
│   │   ├── EnergyAllocation.kt          # BEAM/GUN/SHIELD/ENGINE/WARP/SENSOR allocation
│   │   ├── FormationEffect.kt           # Formation combat bonuses
│   │   ├── WeaponType.kt                # Weapon type definitions
│   │   ├── BattleEvent.kt               # Battle event types
│   │   ├── GroundAssault.kt             # Ground combat
│   │   ├── LineOfSightChecker.kt        # LOS calculations
│   │   ├── CommandDistributionService.kt # Command distribution to subordinates
│   │   ├── CommandRangeService.kt        # Command range calculations
│   │   ├── CommandTimingService.kt       # Command timing/delays
│   │   └── TurnResult.kt                # Tactical turn result
│   ├── war/                              # Strategic war/combat resolution
│   │   ├── BattleEngine.kt              # Strategic battle resolution
│   │   ├── BattleService.kt             # Battle orchestration
│   │   ├── BattleResult.kt              # Battle outcome data
│   │   ├── BattleTrigger.kt             # Battle trigger conditions
│   │   ├── BattleExperienceService.kt   # Post-battle XP awards
│   │   ├── WarAftermath.kt              # Post-war consequences
│   │   ├── WarFormula.kt                # Combat formulas
│   │   └── WarUnit.kt                   # Combat unit abstraction
│   ├── ai/                               # NPC AI system
│   │   ├── FactionAI.kt                 # Faction-level AI decisions
│   │   ├── OfficerAI.kt                 # Officer-level AI actions
│   │   ├── NpcPolicy.kt                 # NPC behavior policies
│   │   ├── AIContext.kt                  # AI decision context
│   │   └── AICompat.kt                  # AI compatibility layer
│   ├── espionage/                        # Spy/intelligence system
│   │   ├── EspionageService.kt          # Spy operation execution
│   │   ├── EspionageSystem.kt           # Espionage system rules
│   │   └── ArrestAuthorityService.kt    # Arrest/execution authority
│   ├── fleet/                            # Fleet management
│   │   ├── FleetManagement.kt           # Fleet organization rules
│   │   ├── FleetFormationRules.kt       # Formation constraints
│   │   ├── CrewGradeService.kt          # Crew quality calculations
│   │   └── TransportExecutionService.kt # Transport fleet operations
│   ├── planet/                           # Planet systems
│   │   ├── PlanetProductionService.kt   # Resource production calculations
│   │   ├── PlanetFacilityService.kt     # Planet facility management
│   │   ├── PlanetFacility.kt            # Facility definitions
│   │   └── PlanetTypeRules.kt           # Planet type-specific rules
│   ├── organization/                     # Organization/rank system
│   │   └── PositionCardSystem.kt        # Position card management + CommandGating
│   ├── modifier/                         # Stat modifier system
│   │   ├── ModifierService.kt           # Applies modifiers to stats
│   │   ├── OfficerLevelModifier.kt      # Level-based stat modifiers
│   │   ├── ItemMeta.kt                  # Item metadata definitions
│   │   └── TraitSpecRegistry.kt         # Officer trait specifications
│   ├── doctrine/                         # Battle doctrine/formation
│   │   ├── Doctrine.kt                  # Doctrine definitions
│   │   └── DoctrineTransition.kt        # Doctrine change rules
│   ├── trigger/                          # Event trigger system
│   │   ├── TriggerCaller.kt             # Trigger dispatch
│   │   ├── Triggers.kt                  # Trigger implementations
│   │   └── TriggerTypes.kt              # Trigger type definitions
│   └── strategic/                        # Strategic-level commands
│       ├── StrategicCommands.kt          # Strategic command definitions with CP type/cost
│       └── PartialImplementationFixes.kt # Fixes for partially implemented features
├── service/                               # 40+ business services
│   ├── GameEventService.kt              # WebSocket broadcast: turn, event, battle, diplomacy, command
│   ├── WorldService.kt                  # World CRUD (game-app side)
│   ├── ScenarioService.kt               # Load/apply game scenarios
│   ├── AuthService.kt                   # Game-app auth (JWT verification)
│   ├── AdminService.kt                  # Admin operations
│   ├── AdminAuthorizationService.kt     # Admin auth checks
│   ├── CharacterCreationService.kt      # New character creation
│   ├── CharacterDeletionService.kt      # Character deletion
│   ├── CharacterLifecycleService.kt     # Character lifecycle management
│   ├── FactionService.kt                # Faction operations
│   ├── FactionJoinService.kt            # Joining factions
│   ├── OfficerService.kt                # Officer queries and operations
│   ├── OfficerRankService.kt            # Rank promotion/demotion
│   ├── PlanetService.kt                 # Planet queries
│   ├── PositionCardService.kt           # Position card assignment/query
│   ├── PermissionService.kt             # Permission checks
│   ├── CommandLogDispatcher.kt          # Command execution logging
│   ├── GameConstService.kt              # Game constants provider
│   ├── MapService.kt                    # Star map data
│   ├── MessageService.kt                # In-game messaging
│   ├── FrontInfoService.kt              # Front page info aggregation
│   ├── HistoryService.kt                # Game history/yearbook
│   ├── InheritanceService.kt            # Character inheritance system
│   ├── AuctionService.kt                # Item auction system
│   ├── TournamentService.kt             # Tournament management
│   ├── RankingService.kt                # Player rankings
│   ├── RankLadderService.kt             # Rank ladder calculations
│   ├── PrivateFundsService.kt           # Personal funds management
│   ├── ProposalService.kt               # Proposal/order system
│   ├── VictoryService.kt                # Victory condition checking
│   ├── RecordService.kt                 # Record keeping
│   ├── SelectPoolService.kt             # NPC selection pool
│   ├── SessionRestartService.kt         # Session restart logic
│   ├── ReregistrationService.kt         # Re-registration handling
│   ├── StatGrowthService.kt             # Stat growth calculations
│   ├── PublicCachedMapService.kt        # Public cached map data
│   └── AccountService.kt                # Account management
├── dto/                                   # Game-specific DTOs
├── model/                                 # Constants (ShipClass, PlanetConst, etc.)
├── config/
│   ├── SecurityConfig.kt                # Spring Security + JWT filter chain
│   ├── WebSocketConfig.kt               # STOMP endpoints: /ws (SockJS), /ws-stomp (raw)
│   ├── JwtAuthenticationFilter.kt       # JWT token extraction and validation
│   ├── JwtUtil.kt                       # JWT utility functions
│   └── GlobalExceptionHandler.kt        # @ControllerAdvice exception mapping
├── bootstrap/
│   └── BootstrapExitRunner.kt           # Run migrations + exit (Docker bootstrap mode)
└── util/                                  # Utilities
```

**Configuration:** `backend/game-app/src/main/resources/application.yml`
- Port: 9001 (configurable via `SERVER_PORT` env)
- DB: PostgreSQL (same as gateway, shared database)
- Redis: localhost:6379
- Flyway: enabled, migrations in `classpath:db/migration`
- Turn tick rate: 1000ms (configurable via `game.tick-rate`)
- CQRS: disabled by default (`openlogh.cqrs.enabled=false`)
- Commit SHA and version injected via args: `--game.commit-sha`, `--game.version`

**Database Migrations:** `backend/game-app/src/main/resources/db/migration/`
- V1 through V40 (40 migration files)
- Key migrations: `V1__core_tables.sql`, `V27__rename_tables_to_logh.sql` (OpenSamguk -> LOGH rename), `V32__add_position_card_table.sql`, `V38__add_officer_version_column.sql`

**Game Data:** `backend/game-app/src/main/resources/data/`
- `scenarios/` — Game scenario JSON files (initial world state templates)
- `maps/` — Star map definitions (planet positions, routes)

## Frontend Module

**Location:** `frontend/src/`

**Purpose:** Browser-based game client with lobby, game management, and real-time game display.

### App Router Structure

**Location:** `frontend/src/app/`

```
app/
├── layout.tsx                             # Root layout (auth provider, theme, metadata)
├── globals.css                            # Tailwind CSS entry point
├── (auth)/                                # Auth route group (unauthenticated)
│   ├── login/page.tsx                    # Login page
│   ├── register/page.tsx                 # Registration page
│   ├── callback/page.tsx                 # OAuth callback
│   └── terms/page.tsx                    # Terms of service
├── (lobby)/                               # Lobby route group (authenticated, pre-game)
│   └── lobby/
│       ├── page.tsx                       # World list, join world
│       ├── join/page.tsx                 # Join existing world
│       ├── create-character/page.tsx     # New character creation
│       ├── select-npc/page.tsx           # NPC selection
│       └── select-pool/page.tsx          # Selection pool
├── (game)/                                # Game route group (authenticated, in-world)
│   ├── layout.tsx                        # Game layout: sidebar, top bar, WebSocket, hotkeys
│   ├── commands/page.tsx                 # Strategic command interface
│   ├── officer/page.tsx                  # Current officer info
│   ├── officers/page.tsx                 # Officer list
│   ├── officers/[id]/page.tsx            # Officer detail
│   ├── planet/page.tsx                   # Current planet
│   ├── faction/page.tsx                  # Faction info
│   ├── faction-officers/page.tsx         # Faction officers list
│   ├── faction-planets/page.tsx          # Faction planets list
│   ├── faction-finance/page.tsx          # Faction finances
│   ├── factions/page.tsx                 # All factions list
│   ├── fleet/page.tsx                    # Fleet management
│   ├── map/page.tsx                      # Star map view
│   ├── board/page.tsx                    # Faction bulletin board
│   ├── messages/page.tsx                 # Player messaging
│   ├── messenger/page.tsx                # Message compose
│   ├── diplomacy/page.tsx                # Diplomatic relations
│   ├── global-diplomacy/page.tsx         # Galaxy-wide diplomacy view
│   ├── proposals/page.tsx                # Proposals and orders
│   ├── personnel/page.tsx                # Personnel management
│   ├── internal-affairs/page.tsx         # Internal affairs
│   ├── chief/page.tsx                    # Command HQ
│   ├── org-chart/page.tsx                # Organization chart
│   ├── position-cards/page.tsx           # Position card management
│   ├── spy/page.tsx                      # Intelligence operations
│   ├── npc-control/page.tsx              # NPC policy management
│   ├── npc-list/page.tsx                 # NPC list (빙의)
│   ├── sovereign/page.tsx                # Sovereign list
│   ├── sovereign/detail/page.tsx         # Sovereign detail
│   ├── battle/page.tsx                   # Battle monitoring
│   ├── battle-center/page.tsx            # Battle center
│   ├── battle-simulator/page.tsx         # Combat simulator
│   ├── models/page.tsx                   # 3D model viewer (React Three Fiber)
│   ├── history/page.tsx                  # Historical yearbook
│   ├── best-officers/page.tsx            # Top officers
│   ├── hall-of-fame/page.tsx             # Hall of fame
│   ├── tournament/page.tsx               # Tournament
│   ├── auction/page.tsx                  # Item auction
│   ├── betting/page.tsx                  # Betting
│   ├── faction-betting/page.tsx          # Faction unification betting
│   ├── vote/page.tsx                     # Polls
│   ├── vote/[id]/page.tsx               # Poll detail
│   ├── my-page/page.tsx                  # Player settings
│   ├── influence/page.tsx                # Influence system
│   ├── inherit/page.tsx                  # Inheritance management
│   ├── lineage/page.tsx                  # Character lineage
│   ├── private-funds/page.tsx            # Personal finances
│   ├── processing/page.tsx               # Turn processing status
│   ├── transport/page.tsx                # Transport management
│   ├── traffic/page.tsx                  # Server traffic info
│   └── superior/page.tsx                 # Superior officer view
└── (admin)/                               # Admin route group
```

### Stores

**Location:** `frontend/src/stores/`

| Store | File | Purpose | Persistence |
|-------|------|---------|-------------|
| Auth | `authStore.ts` | User auth, JWT token, login/logout | `sessionStorage` (`openlogh:auth`) |
| World | `worldStore.ts` | World list, current world selection, CRUD | `sessionStorage` (`openlogh:world`) |
| Game | `gameStore.ts` | Star systems, factions, officers, diplomacy | `sessionStorage` (`openlogh:game`) |
| Officer | `officerStore.ts` | Current player's officer (myOfficer) | Ephemeral |
| General | `generalStore.ts` | Selected officer for viewing | Ephemeral |
| Battle | `battleStore.ts` | Real-time battle state | Ephemeral |

All stores use `zustand/middleware/persist` with `createJSONStorage(() => sessionStorage)` for session persistence. Stores expose `isHydrated` flag for SSR/CSR hydration synchronization.

### API Client

**Location:** `frontend/src/lib/`

- `api.ts`: Base Axios instance, configured with `baseURL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api'`, JWT token injection, 401 redirect interceptor
- `gameApi.ts`: 30+ API modules (worldApi, factionApi, planetApi, officerApi, commandApi, realtimeApi, diplomacyApi, messageApi, fleetApi, boardApi, accountApi, adminApi, turnApi, etc.)
- `websocket.ts`: STOMP client (`@stomp/stompjs`), connects to `/ws-stomp`, subscribes to `/topic/world/{worldId}/turn|battle|diplomacy|message`
- `game-utils.ts`: Game calculation helpers (income, stats, formations)
- `josa.ts`: Korean grammar particle utility (client-side)
- `formatLog.ts`: Game event log formatting
- `image.ts`: Image CDN utilities

### Hooks

**Location:** `frontend/src/hooks/`

- `useWebSocket.ts`: Manages STOMP connection lifecycle per world, handles turn/battle/diplomacy/message events
- `useTacticalWebSocket.ts`: WebSocket for tactical battle mode
- `useHotkeys.ts`: Global keyboard shortcuts (Alt+M map, Alt+G general, Alt+C city, Alt+K commands, etc.)
- `useSoundEffects.ts`: Sound effect playback for game events
- `use-mobile.ts`: Mobile viewport detection

### Components

**Location:** `frontend/src/components/`

- `game/` — Game-specific components (70+ files)
  - `battle/` — Battle UI components (canvas-based via Konva)
  - `game-bottom-bar.tsx` — Mobile bottom navigation
- `auth/` — Auth flow components
- `ui/` — shadcn/ui primitives (button, input, dialog, sidebar, etc.)
- `app-sidebar.tsx` — Main navigation sidebar
- `top-bar.tsx` — Top navigation bar with officer info
- `mobile-menu-sheet.tsx` — Mobile menu overlay
- `responsive-sheet.tsx` — Responsive sheet/modal

### Types

**Location:** `frontend/src/types/`

- `index.ts`: All game type definitions (WorldState, Faction, StarSystem, Officer, Fleet, Diplomacy, etc.) with deprecated aliases for backward compatibility
- `tactical.ts`: Tactical battle type definitions
- `three-jsx.d.ts`: Three.js JSX type augmentations

## Infrastructure

**Docker Compose:** `docker-compose.yml`

| Service | Image | Purpose | Port |
|---------|-------|---------|------|
| postgres | postgres:16-alpine | Primary database | 5432 |
| redis | redis:7-alpine | Cache/session store | 6379 |
| bootstrap | ghcr.io/.../openlogh-game | Run Flyway migrations + exit | - |
| gateway | ghcr.io/.../openlogh-gateway | API gateway | 8080 |
| frontend | ghcr.io/.../openlogh-frontend | Next.js SSR | 3000 (internal) |
| nginx | nginx:alpine | Reverse proxy | 80 |

Startup order: postgres + redis (health checks) -> bootstrap (exits on completion) -> gateway -> frontend -> nginx

**Nginx:** `nginx/nginx.conf` — Routes `/api/` to gateway, `/ws*` to game (with WebSocket upgrade), `/` to frontend

## Key File Locations

**Entry Points:**

- `backend/gateway-app/src/main/kotlin/com/openlogh/gateway/GatewayApplication.kt`: Gateway main class (port 8080)
- `backend/game-app/src/main/kotlin/com/openlogh/OpenloghApplication.kt`: Game-app main class (port 9001+)
- `frontend/src/app/layout.tsx`: Frontend root layout
- `frontend/src/app/(game)/layout.tsx`: Game layout (WebSocket, sidebar, auth guard)

**Configuration:**

- `backend/gateway-app/src/main/resources/application.yml`: Gateway Spring config
- `backend/game-app/src/main/resources/application.yml`: Game-app Spring config
- `backend/game-app/src/main/resources/application-docker.yml`: Docker profile overrides
- `backend/settings.gradle.kts`: Gradle multi-module config
- `frontend/tsconfig.json`: TypeScript config (path alias `@/*` -> `./src/*`)
- `frontend/next.config.ts`: Next.js config
- `docker-compose.yml`: Full stack orchestration
- `nginx/nginx.conf`: Reverse proxy routing

**Core Logic:**

- `backend/gateway-app/src/main/kotlin/com/openlogh/gateway/orchestrator/GameProcessOrchestrator.kt`: JVM process management
- `backend/gateway-app/src/main/kotlin/com/openlogh/gateway/controller/GameProxyController.kt`: Reverse proxy to game-app
- `backend/game-app/src/main/kotlin/com/openlogh/command/CommandRegistry.kt`: All 112 command registrations
- `backend/game-app/src/main/kotlin/com/openlogh/command/CommandExecutor.kt`: Command validation + execution pipeline
- `backend/game-app/src/main/kotlin/com/openlogh/engine/TurnDaemon.kt`: Turn processing scheduler
- `backend/game-app/src/main/kotlin/com/openlogh/engine/turn/cqrs/TurnCoordinator.kt`: CQRS turn pipeline
- `backend/game-app/src/main/kotlin/com/openlogh/engine/RealtimeService.kt`: Real-time command execution
- `backend/game-app/src/main/kotlin/com/openlogh/service/GameEventService.kt`: WebSocket event broadcasting
- `frontend/src/stores/gameStore.ts`: Game state management
- `frontend/src/lib/gameApi.ts`: All API endpoint definitions
- `frontend/src/lib/websocket.ts`: STOMP client

## Naming Conventions

**Files:**

- Kotlin entities: PascalCase — `Officer.kt`, `Planet.kt`, `SessionState.kt`
- Kotlin services: `XyzService.kt` — `EconomyService.kt`, `GameEventService.kt`
- Kotlin controllers: `XyzController.kt` — `CommandController.kt`, `OfficerController.kt`
- Kotlin repositories: `XyzRepository.kt` — `OfficerRepository.kt`
- Kotlin WebSocket controllers: `XyzWebSocketController.kt` — `BattleWebSocketController.kt`
- Kotlin commands: Korean names — `휴식.kt`, `워프항행.kt`, `승진.kt` (prefixed with `che_` for legacy compatibility)
- React pages: `page.tsx` in route directory
- React components: PascalCase — `AppSidebar.tsx`, `TopBar.tsx`
- Zustand stores: camelCase — `authStore.ts`, `gameStore.ts`
- Hooks: `useXyz.ts` — `useWebSocket.ts`, `useHotkeys.ts`
- Test files: `*.test.ts` / `*.test.tsx` — co-located with source

**Directories:**

- Kotlin packages: `com.openlogh.[app].[layer]` — `com.openlogh.gateway.controller`, `com.openlogh.engine.tactical`
- Frontend routes: `(routeGroup)/feature/page.tsx` — `(game)/commands/page.tsx`
- Frontend components: `components/category/` — `components/game/battle/`

## Where to Add New Code

**New Game Command:**

1. Create command class in `backend/game-app/src/main/kotlin/com/openlogh/command/general/MyCommand.kt` (or `nation/`)
2. Extend `BaseCommand` (general) or `NationCommand` (faction-level)
3. Register in `backend/game-app/src/main/kotlin/com/openlogh/command/CommandRegistry.kt` — add entry to `generalCommands` or `nationCommands` map
4. If command needs position card gating, add mapping in `CommandExecutor.resolveCommandGroup()`
5. If command needs CP cost, add to `StrategicCommandRegistry` or `CommandExecutor.resolveCpCost()`
6. Tests: `backend/game-app/src/test/kotlin/com/openlogh/command/`

**New Game Page (Frontend):**

1. Create page: `frontend/src/app/(game)/feature-name/page.tsx`
2. Add to nav: Update `navSections` in `frontend/src/app/(game)/layout.tsx` if it needs sidebar entry
3. Add API endpoints: `frontend/src/lib/gameApi.ts` — add new `export const featureApi = { ... }`
4. Add types: `frontend/src/types/index.ts`
5. Add store state (if needed): New store in `frontend/src/stores/` or extend existing store
6. Components: `frontend/src/components/game/feature-name/` for complex UIs

**New Backend Engine System:**

1. Engine service: `backend/game-app/src/main/kotlin/com/openlogh/engine/subsystem/XyzService.kt`
2. Entity (if needed): `backend/game-app/src/main/kotlin/com/openlogh/entity/Xyz.kt`
3. Repository: `backend/game-app/src/main/kotlin/com/openlogh/repository/XyzRepository.kt`
4. Flyway migration: `backend/game-app/src/main/resources/db/migration/V{next}__description.sql`
5. Wire to commands: Inject service into command classes or `CommandExecutor`
6. Event broadcasting: Call `GameEventService.broadcastEvent(worldId, type, payload)` for WebSocket updates

**New REST Endpoint (game-app):**

1. Controller: `backend/game-app/src/main/kotlin/com/openlogh/controller/XyzController.kt` with `@RestController @RequestMapping("/api")`
2. Service: `backend/game-app/src/main/kotlin/com/openlogh/service/XyzService.kt` for business logic
3. DTO: `backend/game-app/src/main/kotlin/com/openlogh/dto/` for request/response types
4. Frontend API: Add functions to `frontend/src/lib/gameApi.ts`
5. Note: Game-app endpoints are accessed via gateway proxy at `/api/game/{worldId}/api/...`

**New Gateway Endpoint:**

1. Controller: `backend/gateway-app/src/main/kotlin/com/openlogh/gateway/controller/XyzController.kt`
2. Service: `backend/gateway-app/src/main/kotlin/com/openlogh/gateway/service/XyzService.kt`
3. DTO: `backend/gateway-app/src/main/kotlin/com/openlogh/gateway/dto/`
4. Note: Gateway endpoints are directly accessible at `/api/...` (no proxy needed)

**New Shared Type:**

1. Location: `backend/shared/src/main/kotlin/com/openlogh/shared/dto/` for DTOs
2. Both gateway-app and game-app can use it immediately (Gradle dependency)
3. Avoid: Putting app-specific logic in shared module

## Special Directories

**`backend/game-app/src/main/resources/db/migration/`:**

- Purpose: Flyway SQL migrations (V1-V40)
- Generated: Manual creation
- Committed: Yes
- Note: Run automatically by game-app on startup (or by `bootstrap` service in Docker)

**`backend/game-app/src/main/resources/data/`:**

- Purpose: Game scenario JSON files, star map definitions
- Generated: Manual/tool-assisted
- Committed: Yes

**`logh-game/`:**

- Purpose: Original gin7 (2004 BOTHTEC) game data files for reference and model extraction
- Generated: Extracted from original game
- Committed: Yes (reference data)

**`logs/`:**

- Purpose: Game-app process logs (`game-{commitSha}.log`)
- Generated: At runtime by `GameProcessOrchestrator`
- Committed: No (`.gitignore`)

**`build/` and `.next/`:**

- Purpose: Build outputs
- Generated: `./gradlew build` (backend), `pnpm build` (frontend)
- Committed: No

**`.planning/`:**

- Purpose: GSD planning artifacts (codebase analysis, phase plans)
- Generated: By GSD workflow commands
- Committed: Yes

---

*Structure analysis: 2026-03-31*
