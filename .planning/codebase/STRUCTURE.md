# Codebase Structure

**Analysis Date:** 2026-03-28

## Directory Layout

```
openlogh/
├── backend/                           # Spring Boot microservices
│   ├── shared/                        # Cross-app shared library
│   │   ├── src/main/kotlin/com/openlogh/shared/
│   │   │   ├── dto/                   # Shared DTOs
│   │   │   ├── model/                 # Shared models
│   │   │   ├── security/              # Auth/JWT utilities
│   │   │   └── error/                 # Error definitions
│   │   └── src/main/resources/data/   # Game scenarios, maps
│   ├── gateway-app/                   # HTTP API gateway (port 8080)
│   │   ├── src/main/kotlin/com/openlogh/gateway/
│   │   │   ├── controller/            # REST endpoints (auth, worlds, admin, proxy)
│   │   │   ├── service/               # Auth, world, OAuth services
│   │   │   ├── repository/            # Gateway-local entities (User, Account)
│   │   │   ├── entity/                # Account, User entities
│   │   │   ├── orchestrator/          # GameProcessOrchestrator, WorldRouteRegistry
│   │   │   ├── config/                # JWT, security, database config
│   │   │   ├── dto/                   # Gateway-specific DTOs
│   │   │   └── bootstrap/             # Admin bootstrap
│   │   └── src/main/resources/        # application.yml (DB, Redis, JWT config)
│   ├── game-app/                      # Game engine (port 9001+, spawned by gateway)
│   │   ├── src/main/kotlin/com/openlogh/
│   │   │   ├── entity/                # Game entities (Officer, Planet, Fleet, Faction, etc.)
│   │   │   ├── repository/            # JPA repositories for game entities
│   │   │   ├── service/               # Game services (28+ services: Auth, Admin, Economy, etc.)
│   │   │   ├── controller/            # REST endpoints (command, records, messages, etc.)
│   │   │   ├── websocket/             # WebSocket controllers (command, tactical, battle)
│   │   │   ├── command/               # Command CQRS system
│   │   │   │   ├── general/           # General (officer) commands
│   │   │   │   ├── nation/            # Nation (faction) commands
│   │   │   │   ├── constraint/        # Command validation constraints
│   │   │   │   ├── CommandExecutor.kt # Command dispatch & execution
│   │   │   │   └── CommandRegistry.kt # Command registration
│   │   │   ├── engine/                # Game logic engines
│   │   │   │   ├── strategic/         # Turn-based strategic processing
│   │   │   │   ├── tactical/          # Real-time battle system
│   │   │   │   ├── turn/              # Turn progression (CQRS event sourcing)
│   │   │   │   ├── organization/      # Officer positions, card system
│   │   │   │   ├── planet/            # Planet resource & defense systems
│   │   │   │   ├── espionage/         # Spy operations
│   │   │   │   ├── diplomacy/         # Faction relations
│   │   │   │   ├── war/               # Combat resolution
│   │   │   │   ├── ai/                # NPC AI
│   │   │   │   ├── doctrine/          # Battle formations, tactics
│   │   │   │   ├── fleet/             # Fleet movement, organization
│   │   │   │   ├── trigger/           # Event triggers
│   │   │   │   ├── modifier/          # Stat modifiers
│   │   │   │   └── ...services        # 20+ specialized engine services
│   │   │   ├── model/                 # Constants (ShipClass, PlanetConst, etc.)
│   │   │   ├── dto/                   # Game-specific DTOs
│   │   │   ├── config/                # Database, WebSocket, scheduling config
│   │   │   ├── bootstrap/             # World/scenario initialization
│   │   │   └── util/                  # Utilities (RNG, distance, validation)
│   │   └── src/main/resources/
│   │       ├── application.yml        # Game-app config (inherits from gateway DB)
│   │       └── data/                  # Game assets, maps, scenarios
│   ├── build.gradle.kts               # Root gradle config (Kotlin 2.1, Spring Boot 3.4)
│   ├── settings.gradle.kts            # Module configuration (shared, gateway-app, game-app)
│   └── gradle/                        # Gradle wrapper
├── frontend/                          # Next.js 15 React application
│   ├── src/
│   │   ├── app/
│   │   │   ├── (auth)/                # Auth routes (login, register, Kakao callback)
│   │   │   ├── (lobby)/               # Lobby routes (world join, character creation)
│   │   │   ├── (game)/                # Protected game routes
│   │   │   │   ├── layout.tsx         # Game layout (sidebar, top bar, WebSocket provider)
│   │   │   │   ├── models/            # 3D model viewer (React Three Fiber)
│   │   │   │   ├── battle/            # Real-time tactical battle UI (Konva canvas)
│   │   │   │   ├── faction-*/         # Faction management pages (officers, planets, finance)
│   │   │   │   ├── commands/          # Strategic command interface
│   │   │   │   ├── diplomacy/         # Diplomatic relations
│   │   │   │   ├── vote/              # Voting systems
│   │   │   │   └── ...45+ game pages
│   │   │   ├── (admin)/               # Admin routes (users, logs, game versions)
│   │   │   ├── layout.tsx             # Root layout (auth provider, theme)
│   │   │   └── globals.css            # Tailwind CSS entry point
│   │   ├── components/
│   │   │   ├── game/                  # Game-specific components (70+ components)
│   │   │   ├── auth/                  # Auth flow components
│   │   │   ├── ui/                    # shadcn/ui primitives (button, input, etc.)
│   │   │   ├── app-sidebar.tsx        # Navigation sidebar
│   │   │   ├── top-bar.tsx            # Top navigation bar
│   │   │   └── ...
│   │   ├── stores/                    # Zustand state management
│   │   │   ├── authStore.ts           # User auth, JWT token
│   │   │   ├── gameStore.ts           # Current game session state
│   │   │   ├── worldStore.ts          # World list, active world selection
│   │   │   ├── generalStore.ts        # Selected officer, stats display
│   │   │   ├── battleStore.ts         # Real-time battle state
│   │   │   ├── officerStore.ts        # Officer list, details
│   │   │   └── ...
│   │   ├── lib/
│   │   │   ├── gameApi.ts             # Axios client for game-app endpoints
│   │   │   ├── game-utils.ts          # Game calculations (income, stats, etc.)
│   │   │   ├── formatLog.ts           # Game event log formatting
│   │   │   ├── websocket.ts           # WebSocket connection management
│   │   │   ├── image.ts               # Image CDN utilities
│   │   │   ├── josa.ts                # Korean grammar (particles)
│   │   │   ├── api.ts                 # Base API client setup
│   │   │   └── ...utilities
│   │   ├── hooks/                     # Custom React hooks
│   │   │   ├── useGameLoop.ts         # Turn timer, auto-refresh
│   │   │   ├── useWebSocket.ts        # WebSocket connection
│   │   │   └── ...
│   │   ├── types/                     # TypeScript type definitions
│   │   │   ├── index.ts               # WorldState, GameState, Officer, etc.
│   │   │   └── ...
│   │   └── contexts/                  # React context providers
│   ├── public/                        # Static assets
│   │   └── 3d-models/                 # 3D model files (glTF, FBX)
│   ├── e2e/                           # Playwright E2E tests
│   ├── package.json                   # Dependencies (Next.js 15, React 19, Zustand, Axios)
│   ├── next.config.ts                 # Image CDN, build config
│   ├── tsconfig.json                  # TypeScript config
│   ├── tailwind.config.ts             # Tailwind CSS config
│   └── eslint.config.* / vitest.config.* # Linting, testing config
├── docs/                              # Design & reference docs
│   ├── 01-plan/                       # Game design documents
│   ├── 03-analysis/                   # Technical analysis
│   └── reference/                     # Original gin7 manual, LOGH reference
├── .planning/codebase/                # GSD codebase analysis documents
│   ├── ARCHITECTURE.md                # This file's sibling
│   ├── STRUCTURE.md                   # You are here
│   ├── CONVENTIONS.md                 # Code style (if quality focus)
│   ├── TESTING.md                     # Test patterns (if quality focus)
│   ├── STACK.md                       # Dependencies (if tech focus)
│   └── INTEGRATIONS.md                # External services (if tech focus)
├── docker-compose.yml                 # PostgreSQL 16, Redis 7 services
├── CLAUDE.md                          # Project instructions
└── README.md                          # Project overview
```

## Directory Purposes

**backend/shared/:**

- Purpose: Shared contracts between gateway-app and game-app
- Contains: Entity base classes, common DTOs, error types, security models
- Key files: `com/openlogh/shared/model/`, `com/openlogh/shared/dto/`, `com/openlogh/shared/error/`

**backend/gateway-app/:**

- Purpose: Public HTTP API entry point, world orchestration, authentication
- Contains: User account management, world lifecycle, OAuth integration, game-app process management
- Key services: `AuthService.kt` (JWT), `WorldService.kt` (CRUD), `ProcessOrchestratorController.kt`, `GameProcessOrchestrator.kt`

**backend/game-app/:**

- Purpose: Core game simulation engine (one instance per world on port 9001+)
- Contains: All game logic, command execution, event broadcasting, turn processing
- Key components:
    - `controller/` — REST + WebSocket endpoints for commands, records, messages
    - `service/` — 28+ business logic services (Economy, Diplomacy, Admin, etc.)
    - `command/` — CQRS command dispatch (`CommandExecutor`, `CommandRegistry`)
    - `engine/` — 20+ specialized game systems (strategic, tactical, diplomacy, etc.)
    - `entity/` — JPA-managed game state (Officer, Planet, Fleet, Faction, SessionState, etc.)

**frontend/src/app/:**

- Purpose: Next.js App Router file-based routing
- Structure: Route groups `(auth)`, `(lobby)`, `(game)`, `(admin)` organize pages by functional area
- Key layouts: `(game)/layout.tsx` provides game context (WebSocket, sidebar, theme)

**frontend/src/stores/:**

- Purpose: Zustand state management for client-side state
- Pattern: Each store is a single Zustand instance with actions and getters
- Hydration: `authStore`, `worldStore` persist to SessionStorage; `gameStore`, `battleStore` are ephemeral

**frontend/src/lib/:**

- Purpose: Reusable utilities and API clients
- Key modules:
    - `gameApi.ts` — Axios instance + all game-app endpoint functions
    - `game-utils.ts` — Calculations (income, stats, formations)
    - `websocket.ts` — STOMP client connection manager

**frontend/src/components/:**

- Purpose: Reusable React components
- Organization: `game/` contains 70+ game-specific components; `ui/` contains shadcn primitives

## Key File Locations

**Entry Points:**

- Backend Gateway: `backend/gateway-app/src/main/kotlin/com/openlogh/gateway/GatewayApplication.kt`
- Backend Game: `backend/game-app/src/main/kotlin/com/openlogh/OpenloghApplication.kt`
- Frontend Root: `frontend/src/app/layout.tsx`
- Game Layout: `frontend/src/app/(game)/layout.tsx`

**Configuration:**

- Gateway Config: `backend/gateway-app/src/main/resources/application.yml` (DB, Redis, JWT, OAuth)
- Game Config: `backend/game-app/src/main/resources/application.yml` (inherits DB from gateway)
- Frontend Config: `frontend/next.config.ts` (image CDN), `frontend/tsconfig.json` (TypeScript)
- Build Config: `backend/build.gradle.kts` (Gradle, Spring Boot plugins), `frontend/package.json` (npm)

**Core Logic:**

- World Management: `backend/gateway-app/src/main/kotlin/com/openlogh/gateway/orchestrator/GameProcessOrchestrator.kt`
- Command Execution: `backend/game-app/src/main/kotlin/com/openlogh/command/CommandExecutor.kt`
- Strategic Engine: `backend/game-app/src/main/kotlin/com/openlogh/engine/strategic/`
- Event Broadcasting: `backend/game-app/src/main/kotlin/com/openlogh/service/GameEventService.kt`
- Frontend State: `frontend/src/stores/` (Zustand stores), `frontend/src/lib/gameApi.ts` (API client)

**Testing:**

- Backend Tests: `backend/gateway-app/src/test/kotlin/`, `backend/game-app/src/test/kotlin/`
- Frontend Tests: `frontend/**/*.test.ts`, `frontend/**/*.test.tsx`
- E2E Tests: `frontend/e2e/`
- Test Config: `vitest.config.ts` (unit), `playwright.config.ts` (E2E)

**Data Assets:**

- Game Scenarios: `backend/shared/src/main/resources/data/scenarios/` (JSON)
- Map Data: `backend/shared/src/main/resources/data/maps/` (JSON)
- 3D Models: `frontend/public/3d-models/` (glTF, FBX)
- Frontend Assets: `frontend/public/` (images, fonts)

## Naming Conventions

**Files:**

- Entities: `Entity.kt` (e.g., `Officer.kt`, `Planet.kt`) — JPA-managed classes
- Services: `XyzService.kt` (e.g., `EconomyService.kt`) — @Service beans with business logic
- Controllers: `XyzController.kt` (e.g., `CommandController.kt`) — @Controller beans with endpoints
- Repositories: `XyzRepository.kt` (e.g., `OfficerRepository.kt`) — JPA repositories
- WebSocket Controllers: `XyzWebSocketController.kt` (e.g., `CommandWebSocketController.kt`)
- Commands: `XyzCommand.kt` or in `command/general/`, `command/nation/` subdirectories
- Engine Components: `XyzService.kt` in `engine/` subpackages (e.g., `engine/DiplomacyService.kt`)
- React Components: `XyzComponent.tsx` or `xyz.tsx` (kebab-case)
- Hooks: `useXyz.ts` (e.g., `useGameLoop.ts`)
- Stores: `xyzStore.ts` (e.g., `authStore.ts`)

**Directories:**

- Kotlin Packages: `com.openlogh.[app].[layer].xyz` (e.g., `com.openlogh.gateway.service`)
- Frontend Routes: `(routeGroup)/path/page.tsx` or `path/page.tsx`
- Frontend Components: `components/category/ComponentName.tsx`

## Where to Add New Code

**New Game Command:**

- Implementation: `backend/game-app/src/main/kotlin/com/openlogh/command/general/` or `.../nation/` (depends on scope)
- Registration: Add to `CommandRegistry.kt` entry in appropriate group
- Tests: `backend/game-app/src/test/kotlin/com/openlogh/command/`
- Service dependencies: Inject in command class via constructor

**New Game Page (Frontend):**

- Component file: `frontend/src/app/(game)/feature-name/page.tsx`
- Components: `frontend/src/components/game/FeatureName/` (if complex)
- Store state: Add methods to relevant store in `frontend/src/stores/` or create new store
- API calls: Add endpoint functions to `frontend/src/lib/gameApi.ts`
- Types: Add types to `frontend/src/types/index.ts`

**New Game System (Backend):**

- Engine service: `backend/game-app/src/main/kotlin/com/openlogh/engine/subsystem/XyzService.kt`
- Entities: `backend/game-app/src/main/kotlin/com/openlogh/entity/Xyz.kt` if needed
- Repositories: `backend/game-app/src/main/kotlin/com/openlogh/repository/XyzRepository.kt`
- Command handlers: Invoke service from command classes in `backend/game-app/src/main/kotlin/com/openlogh/command/`
- Event creation: Emit `Event` entities via `GameEventService.broadcastEvent()`
- WebSocket broadcast: Service calls `messagingTemplate.convertAndSend()` if real-time update needed

**New Gateway Endpoint:**

- Controller: `backend/gateway-app/src/main/kotlin/com/openlogh/gateway/controller/XyzController.kt`
- Service: `backend/gateway-app/src/main/kotlin/com/openlogh/gateway/service/XyzService.kt` if business logic needed
- DTO: `backend/gateway-app/src/main/kotlin/com/openlogh/gateway/dto/` for request/response types
- Auth: Add `@PreAuthorize` annotation if admin-only or role-based
- Tests: `backend/gateway-app/src/test/kotlin/com/openlogh/gateway/`

**Shared Utilities:**

- Location: `backend/shared/src/main/kotlin/com/openlogh/shared/` (or appropriate subdirectory)
- Use when: Code needed by both gateway-app and game-app
- Avoid putting: Gateway-specific or game-specific logic

## Special Directories

**backend/game-app/src/main/kotlin/com/openlogh/engine/turn/cqrs/:**

- Purpose: CQRS event sourcing for turn history
- Generated: Yes (by turn engine during gameplay)
- Committed: No (runtime ephemeral state)

**backend/shared/src/main/resources/data/scenarios/:**

- Purpose: Game scenario JSON files (initial state templates)
- Generated: Manual creation by designers
- Committed: Yes

**frontend/public/3d-models/:**

- Purpose: 3D model assets for React Three Fiber viewer
- Generated: External tools (Blender, asset creation)
- Committed: Yes (but large files tracked separately)

**logs/:**

- Purpose: Game-app process logs written by `GameProcessOrchestrator`
- Generated: At runtime (one log per game-app process)
- Committed: No (`.gitignore` entry)

**build/ and .next/:**

- Purpose: Build outputs (compiled JARs, Next.js build artifacts)
- Generated: At build time (`gradle build`, `npm run build`)
- Committed: No (in `.gitignore`)

---

_Structure analysis: 2026-03-28_
