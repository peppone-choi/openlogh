# Codebase Structure

**Analysis Date:** 2026-03-31

## Directory Layout

```
opensamguk/
├── backend/                    # Spring Boot backend (Kotlin, multi-module Gradle)
│   ├── build.gradle.kts        # Root build config (plugins, allprojects, subprojects)
│   ├── settings.gradle.kts     # Module declarations: shared, gateway-app, game-app
│   ├── gradle.properties       # Gradle settings
│   ├── Dockerfile              # Backend Docker build
│   ├── shared/                 # Shared Kotlin library (DTOs, JWT, game constants)
│   ├── gateway-app/            # API gateway (auth, proxy, orchestration)
│   ├── game-app/               # Game logic (commands, engine, entities, turn daemon)
│   ├── scripts/                # Backend utility scripts
│   └── gradle/wrapper/         # Gradle wrapper
├── frontend/                   # Next.js 15 frontend (TypeScript, App Router)
│   ├── package.json            # Dependencies (pnpm)
│   ├── next.config.ts          # Next.js configuration
│   ├── tsconfig.json           # TypeScript config
│   ├── vitest.config.ts        # Test runner config
│   ├── playwright.config.ts    # E2E test config
│   ├── eslint.config.mjs       # Linting config
│   ├── components.json         # shadcn/ui component config
│   ├── Dockerfile              # Frontend Docker build
│   ├── src/                    # Application source
│   ├── public/                 # Static assets (fonts, icons)
│   ├── e2e/                    # Playwright E2E tests
│   └── scripts/                # Frontend utility scripts
├── legacy-core/                # PHP legacy codebase (parity reference, NOT runtime)
├── docs/                       # Project documentation
│   ├── 01-plan/                # Planning documents
│   ├── 02-design/              # Design documents
│   ├── 03-analysis/            # Analysis documents
│   └── 04-report/              # Report documents
├── scripts/                    # Project-wide scripts
│   └── verify/                 # Pre-commit hooks and CI verification
├── nginx/                      # Nginx reverse proxy config (production)
├── legacy-docker/              # Legacy Docker configs
├── parity-screenshots/         # Visual parity comparison screenshots
├── docker-compose.yml          # Development/production Docker services
├── CLAUDE.md                   # AI assistant project instructions
├── verify                      # Verification entry point script
└── .env.example                # Environment variable template
```

## Directory Purposes

**`backend/shared/`:**
- Purpose: Cross-module Kotlin library shared between gateway-app and game-app
- Contains: DTOs, JWT verification, game constants, scenario data model
- Key files:
  - `src/main/kotlin/com/opensam/shared/dto/AuthDtos.kt`: Auth request/response DTOs
  - `src/main/kotlin/com/opensam/shared/dto/AccountDtos.kt`: Account-related DTOs
  - `src/main/kotlin/com/opensam/shared/dto/AdminDtos.kt`: Admin DTOs
  - `src/main/kotlin/com/opensam/shared/dto/WorldDtos.kt`: World state DTOs
  - `src/main/kotlin/com/opensam/shared/dto/JwtUserPrincipal.kt`: JWT user principal
  - `src/main/kotlin/com/opensam/shared/security/JwtTokenVerifier.kt`: JWT token validation
  - `src/main/kotlin/com/opensam/shared/error/ErrorResponse.kt`: Standardized error response
  - `src/main/kotlin/com/opensam/shared/GameConstants.kt`: Game-wide constants
  - `src/main/kotlin/com/opensam/shared/model/ScenarioData.kt`: Scenario JSON model
  - `src/main/kotlin/com/opensam/util/JosaUtil.kt`: Korean postposition utility
  - `src/main/resources/data/`: All game data JSON files

**`backend/gateway-app/`:**
- Purpose: API gateway -- authentication, world management, request proxying to game instances
- Contains: Controllers, services, entities, orchestrators, config
- Key files:
  - `src/main/kotlin/com/opensam/gateway/GatewayApplication.kt`: Spring Boot entry point
  - `src/main/kotlin/com/opensam/gateway/controller/AuthController.kt`: Login, register, OAuth
  - `src/main/kotlin/com/opensam/gateway/controller/WorldController.kt`: World CRUD, activate/deactivate
  - `src/main/kotlin/com/opensam/gateway/controller/GameProxyController.kt`: Per-world request proxy (`/api/game/{worldId}/**`)
  - `src/main/kotlin/com/opensam/gateway/config/GameProxyFilter.kt`: Catch-all proxy for `/api/**`
  - `src/main/kotlin/com/opensam/gateway/config/SecurityConfig.kt`: Spring Security, CORS, JWT filter chain
  - `src/main/kotlin/com/opensam/gateway/config/JwtUtil.kt`: JWT generation
  - `src/main/kotlin/com/opensam/gateway/config/JwtAuthenticationFilter.kt`: JWT validation filter
  - `src/main/kotlin/com/opensam/gateway/orchestrator/GameProcessOrchestrator.kt`: Spawn/manage local game JVM processes
  - `src/main/kotlin/com/opensam/gateway/orchestrator/GameContainerOrchestrator.kt`: Docker container orchestration (production)
  - `src/main/kotlin/com/opensam/gateway/orchestrator/WorldActivationBootstrap.kt`: Auto-activate worlds on startup
  - `src/main/kotlin/com/opensam/gateway/service/WorldRouteRegistry.kt`: In-memory worldId -> baseUrl routing map
  - `src/main/kotlin/com/opensam/gateway/service/AuthService.kt`: Auth business logic
  - `src/main/kotlin/com/opensam/gateway/service/WorldService.kt`: World lifecycle management
  - `src/main/kotlin/com/opensam/gateway/entity/AppUser.kt`: User entity (gateway DB)
  - `src/main/kotlin/com/opensam/gateway/entity/WorldState.kt`: World state entity (gateway DB)

**`backend/game-app/`:**
- Purpose: Core game logic -- all domain entities, commands, turn processing, war, economy, NPC AI
- Contains: Full Spring Boot application with controllers, services, repositories, entities, engine
- Subpackages:
  - `bootstrap/`: `BootstrapExitRunner.kt` -- exit-on-ready mode for DB migration runs
  - `command/`: Command system (BaseCommand, CommandRegistry, CommandExecutor, ArgSchema)
  - `command/constraint/`: Command precondition validators
  - `command/general/`: 55 general command implementations (Korean-named classes)
  - `command/nation/`: 38 nation command implementations
  - `command/util/`: Command utility helpers
  - `config/`: Spring config (Security, WebSocket, JWT, GlobalExceptionHandler)
  - `controller/`: 35 REST controllers (see Controllers section below)
  - `dto/`: Game-specific DTOs
  - `engine/`: Core game engine services (TurnDaemon, TurnService, EconomyService, DiplomacyService, WarEngine, NPC AI, etc.)
  - `engine/ai/`: NPC artificial intelligence
  - `engine/event/`: Game event system with action subcategories (betting, control, economy, game, misc, npc)
  - `engine/map/`: Map-related engine logic
  - `engine/modifier/`: Action modifier system (nation type, personality, specials, items)
  - `engine/trigger/`: Triggered game effects
  - `engine/turn/`: Turn pipeline and steps
  - `engine/turn/cqrs/`: CQRS turn processing (coordinator, lifecycle, in-memory state, persistence)
  - `engine/turn/cqrs/memory/`: In-memory world state, dirty tracking, snapshots
  - `engine/turn/cqrs/persist/`: JPA persistence adapters, bulk writer, snapshot mapper
  - `engine/turn/cqrs/port/`: Port interfaces (WorldReadPort, WorldWritePort, IdAllocator)
  - `engine/turn/steps/`: 17 individual turn pipeline steps
  - `engine/war/`: War/battle processing
  - `entity/`: 34 JPA entities (General, City, Nation, Diplomacy, Troop, etc.)
  - `model/`: Domain model classes
  - `repository/`: 34 Spring Data JPA repositories
  - `service/`: 41 service classes
  - `util/`: Utility classes

**`frontend/src/app/`:**
- Purpose: Next.js App Router pages organized by route groups
- Contains: Page components, layouts, co-located tests
- Route groups:
  - `(auth)/`: Authentication pages -- login, register, account, OAuth callback, terms, privacy
  - `(lobby)/`: Pre-game lobby -- world selection, NPC selection, join game, select pool
  - `(game)/`: Main game pages (30+ pages) -- dashboard, commands, map, generals, nations, diplomacy, battle, etc.
  - `(admin)/`: Admin panel -- dashboard, users, members, game versions, logs, diplomacy, statistics, select pool
  - `(tutorial)/`: Interactive tutorial -- create, main, city, command, battle, diplomacy, nation, complete

**`frontend/src/components/`:**
- Purpose: Reusable React components
- Contains: Three categories
  - `ui/`: shadcn/ui primitives (27 components) -- button, card, dialog, input, sidebar, table, tabs, etc.
  - `game/`: Game-specific components (39 components) -- dashboard, command panel, map viewer, general cards, nation badges, stat bars, etc.
  - `auth/`: Auth-specific components (server-status-card)
  - `tutorial/`: Tutorial components
  - Top-level: `app-sidebar.tsx`, `top-bar.tsx`, `mobile-menu-sheet.tsx`, `responsive-sheet.tsx`

**`frontend/src/stores/`:**
- Purpose: Zustand state management stores
- Contains: 4 stores with co-located tests
  - `authStore.ts`: Authentication state (JWT, user, login/logout)
  - `worldStore.ts`: World selection and management (persisted in sessionStorage)
  - `generalStore.ts`: Current player's general data (persisted in sessionStorage)
  - `gameStore.ts`: Transient game state (cities, nations, not persisted)
  - `tutorialStore.ts`: Tutorial progress state

**`frontend/src/lib/`:**
- Purpose: Shared utilities, API clients, helpers
- Contains:
  - `api.ts`: Axios instance with JWT interceptor (base API client)
  - `gameApi.ts`: Game-specific API functions organized by domain (worldApi, generalApi, cityApi, nationApi, commandApi, etc.)
  - `websocket.ts`: STOMP/SockJS WebSocket client
  - `api-error.ts`: API error message extraction
  - `auth-error.ts`: Auth error handling
  - `auth-features.ts`: Feature flags for auth
  - `formatLog.ts`: Game log message formatting
  - `image.ts`: Image CDN URL builder
  - `josa.ts`: Korean postposition helper
  - `game-utils.ts`: Game calculation utilities
  - `income-calc.ts`: Income calculation
  - `interception-utils.ts`: Interception/movement utilities
  - `map-constants.ts`: Map rendering constants
  - `utils.ts`: General utilities (cn() classname merger)

**`frontend/src/hooks/`:**
- Purpose: Custom React hooks
- Contains:
  - `useWebSocket.ts`: WebSocket connection management
  - `useHotkeys.ts`: Keyboard shortcut handler
  - `useSoundEffects.ts`: Sound effect toggle
  - `useDebouncedCallback.ts`: Debounced callback
  - `use-mobile.ts`: Mobile viewport detection

**`frontend/src/types/`:**
- Purpose: TypeScript type definitions
- Contains: `index.ts` -- single barrel file exporting all game types (WorldState, General, Nation, City, Troop, Diplomacy, CommandResult, etc.)

**`frontend/src/contexts/`:**
- Purpose: React context providers
- Contains: Context definitions (minimal usage -- stores preferred over contexts)

**`frontend/src/data/tutorial/`:**
- Purpose: Tutorial mock data
- Contains: Mock cities, generals, nations, commands, world data, tutorial step definitions

## Key File Locations

**Entry Points:**
- `backend/gateway-app/src/main/kotlin/com/opensam/gateway/GatewayApplication.kt`: Gateway main class
- `backend/game-app/src/main/kotlin/com/opensam/` (Spring Boot auto-detected main class)
- `frontend/src/app/layout.tsx`: Root layout (HTML, theme, global CSS)
- `frontend/src/app/(game)/layout.tsx`: Game layout (sidebar, auth guard, WebSocket, hotkeys)
- `frontend/src/app/(game)/page.tsx`: Game dashboard entry (delegates to `game-dashboard.tsx`)

**Configuration:**
- `backend/build.gradle.kts`: Root Gradle config (Kotlin 2.1.0, Spring Boot 3.4.2, JVM 17)
- `backend/game-app/build.gradle.kts`: Game-app dependencies
- `backend/gateway-app/build.gradle.kts`: Gateway-app dependencies
- `backend/shared/build.gradle.kts`: Shared library dependencies
- `backend/game-app/src/main/resources/application.yml`: Game-app Spring config (DB, Redis, JWT, turn interval)
- `backend/game-app/src/main/resources/application-docker.yml`: Docker profile overrides
- `backend/game-app/src/main/resources/application-test.yml`: Test profile
- `frontend/next.config.ts`: Next.js config (standalone output, image CDN)
- `frontend/tsconfig.json`: TypeScript config
- `frontend/vitest.config.ts`: Vitest test runner config
- `frontend/playwright.config.ts`: Playwright E2E config
- `frontend/eslint.config.mjs`: ESLint config
- `frontend/components.json`: shadcn/ui config
- `docker-compose.yml`: Full stack Docker services (postgres, redis, bootstrap, gateway, frontend, nginx)
- `.env.example`: Environment variable template (existence noted, contents not read)

**Core Logic:**
- `backend/game-app/src/main/kotlin/com/opensam/command/CommandExecutor.kt`: Command orchestration (constraint check, execution, persistence)
- `backend/game-app/src/main/kotlin/com/opensam/command/CommandRegistry.kt`: All 93 commands registered with factory lambdas
- `backend/game-app/src/main/kotlin/com/opensam/command/BaseCommand.kt`: Abstract command base class
- `backend/game-app/src/main/kotlin/com/opensam/engine/TurnDaemon.kt`: Turn scheduler
- `backend/game-app/src/main/kotlin/com/opensam/engine/TurnService.kt`: Turn processing logic
- `backend/game-app/src/main/kotlin/com/opensam/engine/turn/TurnPipeline.kt`: Ordered step pipeline
- `backend/game-app/src/main/kotlin/com/opensam/engine/turn/cqrs/TurnCoordinator.kt`: CQRS turn lifecycle
- `backend/game-app/src/main/kotlin/com/opensam/engine/war/`: War/battle processing
- `backend/game-app/src/main/kotlin/com/opensam/engine/ai/`: NPC AI logic
- `backend/gateway-app/src/main/kotlin/com/opensam/gateway/orchestrator/GameProcessOrchestrator.kt`: Game JVM lifecycle

**Game Data:**
- `backend/shared/src/main/resources/data/game_const.json`: City levels, region mappings, initial values
- `backend/shared/src/main/resources/data/officer_ranks.json`: Officer rank system by nation level
- `backend/shared/src/main/resources/data/items.json`: Equipment/item definitions
- `backend/shared/src/main/resources/data/general_pool.json`: NPC general pool
- `backend/shared/src/main/resources/data/maps/`: 9 map definitions (che, miniche, cr, chess, duel, etc.)
- `backend/shared/src/main/resources/data/scenarios/`: 80+ scenario definitions

**Database Migrations:**
- `backend/game-app/src/main/resources/db/migration/V1__core_tables.sql` through `V27__add_general_position.sql`: 27 Flyway migrations

**Testing:**
- `backend/game-app/src/test/kotlin/com/opensam/`: Backend tests (command, controller, engine, entity, service, qa/parity)
- `frontend/src/app/**/*.test.ts`: Co-located page tests
- `frontend/src/components/**/*.test.ts(x)`: Co-located component tests
- `frontend/src/stores/**/*.test.ts`: Store tests
- `frontend/src/lib/**/*.test.ts`: Lib utility tests
- `frontend/src/hooks/**/*.test.ts`: Hook tests
- `frontend/e2e/parity/`: Playwright E2E parity tests

**Verification Scripts:**
- `verify`: Root verification entry point
- `scripts/verify/install-hooks.sh`: Git hook installer
- `scripts/verify/run.sh`: Verification runner
- `scripts/verify/tdd-gate.sh`: TDD gate check

## Naming Conventions

**Files (Backend):**
- Kotlin classes: PascalCase (`CommandExecutor.kt`, `GeneralRepository.kt`)
- Korean-named command classes: `che_농지개간.kt`, `출병.kt`, `Nation휴식.kt` (legacy parity)
- Config classes: PascalCase with `Config` suffix (`SecurityConfig.kt`, `WebSocketConfig.kt`)
- Test files: `{ClassName}Test.kt`

**Files (Frontend):**
- Components: kebab-case (`game-dashboard.tsx`, `command-panel.tsx`, `app-sidebar.tsx`)
- Pages: `page.tsx` (Next.js convention)
- Layouts: `layout.tsx` (Next.js convention)
- Stores: camelCase (`authStore.ts`, `worldStore.ts`)
- Hooks: camelCase with `use` prefix (`useWebSocket.ts`, `useHotkeys.ts`)
- Tests: `{name}.test.ts` or `{name}.test.tsx` (co-located)
- Types: `index.ts` barrel in `types/`

**Directories:**
- Backend packages: lowercase dot-separated (`com.opensam.command.general`)
- Frontend route groups: parenthesized `(game)`, `(auth)`, `(lobby)`, `(admin)`, `(tutorial)`
- Frontend feature dirs: kebab-case (`battle-simulator`, `nation-cities`, `global-diplomacy`)

## Where to Add New Code

**New General Command:**
- Implementation: `backend/game-app/src/main/kotlin/com/opensam/command/general/{CommandName}.kt`
- Register in: `backend/game-app/src/main/kotlin/com/opensam/command/CommandRegistry.kt` (add `registerGeneralCommand()` call in `init {}`)
- Add arg schema if needed: `backend/game-app/src/main/kotlin/com/opensam/command/ArgSchemas.kt`
- Tests: `backend/game-app/src/test/kotlin/com/opensam/command/general/{CommandName}Test.kt`

**New Nation Command:**
- Implementation: `backend/game-app/src/main/kotlin/com/opensam/command/nation/{CommandName}.kt`
- Register in: `backend/game-app/src/main/kotlin/com/opensam/command/CommandRegistry.kt`
- Tests: `backend/game-app/src/test/kotlin/com/opensam/command/nation/`

**New Backend API Endpoint:**
- Controller: `backend/game-app/src/main/kotlin/com/opensam/controller/{Feature}Controller.kt`
- Service: `backend/game-app/src/main/kotlin/com/opensam/service/{Feature}Service.kt`
- Repository (if new entity): `backend/game-app/src/main/kotlin/com/opensam/repository/{Entity}Repository.kt`
- Entity (if new table): `backend/game-app/src/main/kotlin/com/opensam/entity/{Entity}.kt`
- Migration (if new table): `backend/game-app/src/main/resources/db/migration/V{N}__{description}.sql`
- Frontend API: Add function to appropriate section in `frontend/src/lib/gameApi.ts`
- Frontend types: Add types to `frontend/src/types/index.ts`

**New Gateway Endpoint (auth/world/admin):**
- Controller: `backend/gateway-app/src/main/kotlin/com/opensam/gateway/controller/{Feature}Controller.kt`
- Service: `backend/gateway-app/src/main/kotlin/com/opensam/gateway/service/{Feature}Service.kt`
- Update security rules if needed: `backend/gateway-app/src/main/kotlin/com/opensam/gateway/config/SecurityConfig.kt`

**New Frontend Page:**
- Page: `frontend/src/app/(game)/{feature}/page.tsx` (or appropriate route group)
- Test: `frontend/src/app/(game)/{feature}/{feature}.test.ts`
- Add nav entry: `frontend/src/app/(game)/layout.tsx` in `navSections` array
- If new component needed: `frontend/src/components/game/{component-name}.tsx`

**New UI Component (shadcn/ui):**
- Component: `frontend/src/components/ui/{component-name}.tsx`
- Follow shadcn/ui conventions (use `components.json` for configuration)

**New Game Component:**
- Component: `frontend/src/components/game/{component-name}.tsx`
- Test: `frontend/src/components/game/{component-name}.test.ts`

**New Frontend Store:**
- Store: `frontend/src/stores/{feature}Store.ts`
- Test: `frontend/src/stores/{feature}Store.test.ts`
- Use Zustand `create()`, optionally with `persist()` middleware

**New Custom Hook:**
- Hook: `frontend/src/hooks/use{Feature}.ts`
- Test: `frontend/src/hooks/use{Feature}.test.ts`

**New Turn Pipeline Step:**
- Step: `backend/game-app/src/main/kotlin/com/opensam/engine/turn/steps/{StepName}Step.kt`
- Implement `TurnStep` interface with `name`, `order`, `execute(context)`, optionally `shouldSkip(context)`
- Auto-registered via Spring `@Component` -- TurnPipeline collects all TurnStep beans

**New Entity / Database Table:**
- Entity: `backend/game-app/src/main/kotlin/com/opensam/entity/{Entity}.kt`
- Repository: `backend/game-app/src/main/kotlin/com/opensam/repository/{Entity}Repository.kt`
- Migration: `backend/game-app/src/main/resources/db/migration/V{next}__{description}.sql` (next is V28)
- If used in CQRS turn engine: Add snapshot type in `engine/turn/cqrs/memory/`, add port methods in `WorldReadPort`/`WorldWritePort`

**New Scenario:**
- JSON file: `backend/shared/src/main/resources/data/scenarios/scenario_{code}.json`
- Follow existing scenario JSON schema (see `ScenarioData.kt` model)

**Shared DTOs (cross-module):**
- DTO: `backend/shared/src/main/kotlin/com/opensam/shared/dto/{Feature}Dtos.kt`

## Special Directories

**`legacy-core/`:**
- Purpose: Cloned PHP source from `devsam/core` -- the parity reference target
- Generated: No (externally maintained)
- Committed: Yes
- Note: NOT runtime code. Used only for comparing behavior during development.

**`backend/game-app/src/main/resources/db/migration/`:**
- Purpose: Flyway SQL migration scripts
- Generated: No (hand-written)
- Committed: Yes
- Note: Sequential versioning (V1 through V27). Next migration is V28.

**`backend/shared/src/main/resources/data/`:**
- Purpose: Static game JSON data (scenarios, maps, items, ranks, general pool)
- Generated: Partially (scenarios may be exported from tools)
- Committed: Yes
- Note: 80+ scenario files, 9 map files, items, officer ranks, general pool

**`frontend/public/`:**
- Purpose: Static web assets served directly
- Contains: `fonts/` (game fonts), `icons/` (favicon variants)
- Generated: Some icons generated from source
- Committed: Yes

**`frontend/e2e/`:**
- Purpose: Playwright end-to-end tests for visual parity checking
- Contains: `parity/` subdirectory with parity test specs
- Generated: No
- Committed: Yes

**`parity-screenshots/`:**
- Purpose: Reference screenshots for visual parity comparison
- Generated: Captured during testing
- Committed: Yes

**`.planning/`:**
- Purpose: GSD planning and codebase analysis documents
- Generated: Yes (by AI tools)
- Committed: Varies

**`nginx/`:**
- Purpose: Nginx reverse proxy configuration for production deployment
- Contains: `nginx.conf` routing frontend and API traffic
- Committed: Yes

**`scripts/verify/`:**
- Purpose: Pre-commit hooks and CI verification pipeline
- Contains: `install-hooks.sh`, `run.sh`, `tdd-gate.sh`
- Committed: Yes
- Usage: `./verify pre-commit` or `./verify ci`

---

*Structure analysis: 2026-03-31*
