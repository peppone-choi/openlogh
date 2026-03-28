# Open LOGH (오픈 은하영웅전설)

Web-based Legend of the Galactic Heroes multiplayer strategy game.
Forked from OpenSamguk, re-themed to the LOGH universe.

## Tech Stack

- Backend: Spring Boot 3 (Kotlin)
- Frontend: Next.js 15
- Database: PostgreSQL 16
- Cache: Redis 7

## Project Structure

- `backend/` - Spring Boot backend (gateway-app + game-app)
- `frontend/` - Next.js frontend
- `docs/` - Game design docs and reference materials
- `docs/reference/` - Original gin7 manual and Korean reference

## Commands

### Backend

```bash
cd backend && ./gradlew :gateway-app:bootRun
cd backend && ./gradlew :game-app:bootRun
```

### Frontend

```bash
cd frontend && pnpm dev
```

### Docker (DB services)

```bash
docker-compose up -d
```

## Domain Mapping (삼국지 → 은하영웅전설)

This project transforms a Three Kingdoms game into a LOGH space opera game.

### Core Entity Mapping

| OpenSamguk (삼국지) | OpenLOGH (은하영웅전설) | DB Table        | Notes                         |
| ------------------- | ----------------------- | --------------- | ----------------------------- |
| General (장수)      | Officer (제독/장교)     | `officer`       | Character the player controls |
| City (도시/성)      | Planet (행성)           | `planet`        | Territory unit                |
| Nation (국가)       | Faction (진영)          | `faction`       | Empire/Alliance/Fezzan        |
| Troop (부대)        | Fleet (함대)            | `fleet`         | Military unit                 |
| WorldState          | SessionState            | `session_state` | Game session                  |
| Emperor (황제)      | Sovereign (원수/의장)   | `sovereign`     | Faction leader                |

### Stat Mapping

| OpenSamguk 5-stat | OpenLOGH 8-stat       | Field Name       | Description                |
| ----------------- | --------------------- | ---------------- | -------------------------- |
| leadership (통솔) | leadership (통솔)     | `leadership`     | 인재 활용, 함대 최대 사기  |
| strength (무력)   | command (지휘)        | `command`        | 부대 지휘 능력             |
| intel (지력)      | intelligence (정보)   | `intelligence`   | 정보 수집/분석, 첩보, 색적 |
| politics (정치)   | politics (정치)       | `politics`       | 시민 지지 획득             |
| charm (매력)      | administration (운영) | `administration` | 행성 통치, 사무 관리       |
| - (new)           | mobility (기동)       | `mobility`       | 함대 이동/기동 지휘        |
| - (new)           | attack (공격)         | `attack`         | 공격 지휘 능력             |
| - (new)           | defense (방어)        | `defense`        | 방어 지휘 능력             |

### Resource Mapping

| OpenSamguk      | OpenLOGH         | Field        | Description           |
| --------------- | ---------------- | ------------ | --------------------- |
| gold (금)       | funds (자금)     | `funds`      | 국가/개인 자금        |
| rice (식량)     | supplies (물자)  | `supplies`   | 군수 물자             |
| crew (병력)     | ships (함선)     | `ships`      | 함선 수               |
| crewType (병종) | shipClass (함종) | `ship_class` | 전함/순양함/구축함 등 |
| train (훈련)    | training (훈련)  | `training`   | 부대 훈련도           |
| atmos (사기)    | morale (사기)    | `morale`     | 부대 사기             |

### City → Planet Field Mapping

| OpenSamguk     | OpenLOGH                   | Field             | Description        |
| -------------- | -------------------------- | ----------------- | ------------------ |
| pop (인구)     | population (인구)          | `population`      | 행성 인구          |
| agri (농업)    | production (생산)          | `production`      | 생산력 (함선/물자) |
| comm (상업)    | commerce (교역)            | `commerce`        | 교역/경제          |
| secu (치안)    | security (치안)            | `security`        | 행성 치안          |
| trust (민심)   | approval (지지도)          | `approval`        | 주민 지지도        |
| def (수비)     | orbital_defense (궤도방어) | `orbital_defense` | 궤도 방어력        |
| wall (성벽)    | fortress (요새)            | `fortress`        | 요새 방어력        |
| trade (교역로) | trade_route (항로)         | `trade_route`     | 교역 항로          |

### Nation → Faction Field Mapping

| OpenSamguk       | OpenLOGH                   | Field               |
| ---------------- | -------------------------- | ------------------- |
| gold             | funds                      | `funds`             |
| rice             | supplies                   | `supplies`          |
| bill (세율)      | tax_rate (세율)            | `tax_rate`          |
| rate (징병률)    | conscription_rate (징병률) | `conscription_rate` |
| tech (기술)      | tech_level (기술력)        | `tech_level`        |
| power (국력)     | military_power (군사력)    | `military_power`    |
| level (국가레벨) | faction_rank (진영 등급)   | `faction_rank`      |
| typeCode         | faction_type (진영 타입)   | `faction_type`      |

### Item Mapping

| OpenSamguk    | OpenLOGH                     | Field            |
| ------------- | ---------------------------- | ---------------- |
| weapon (무기) | flagship (기함)              | `flagship_code`  |
| book (서적)   | special_equipment (특수장비) | `equip_code`     |
| horse (말)    | engine (기관)                | `engine_code`    |
| item (아이템) | accessory (부속품)           | `accessory_code` |

### Faction Types (진영)

| Code       | Name (한국어) | Name (English)        | Description           |
| ---------- | ------------- | --------------------- | --------------------- |
| `empire`   | 은하제국      | Galactic Empire       | 전제군주제, 귀족 체계 |
| `alliance` | 자유행성동맹  | Free Planets Alliance | 민주공화제            |
| `fezzan`   | 페잔 자치령   | Fezzan Dominion       | 중립 교역 국가        |
| `rebel`    | 반란군        | Rebel Forces          | 쿠데타/반란 세력      |

### Ship Classes (함종)

| Code         | Name     | Ships/Unit | Description                         |
| ------------ | -------- | ---------- | ----------------------------------- |
| `battleship` | 전함     | 300        | 주력 전투함                         |
| `cruiser`    | 순양함   | 300        | 범용 전투함                         |
| `destroyer`  | 구축함   | 300        | 고속 전투함                         |
| `carrier`    | 항공모함 | 300        | 스파르타니안 운용                   |
| `transport`  | 수송함   | 300        | 물자/병력 수송                      |
| `hospital`   | 병원선   | 300        | 부상자 치료                         |
| `fortress`   | 요새     | 1          | 이동 요새 (이제르론/가이에스부르크) |

### Rank System (계급)

#### Empire (제국군)

| Level | Rank                 | Korean   |
| ----- | -------------------- | -------- |
| 10    | Reichsmarschall      | 원수     |
| 9     | Fleet Admiral        | 상급대장 |
| 8     | Admiral              | 대장     |
| 7     | Vice Admiral         | 중장     |
| 6     | Rear Admiral         | 소장     |
| 5     | Commodore            | 준장     |
| 4     | Captain              | 대령     |
| 3     | Commander            | 중령     |
| 2     | Lieutenant Commander | 소령     |
| 1     | Lieutenant           | 대위     |
| 0     | Sub-Lieutenant       | 소위     |

#### Alliance (동맹군)

Same structure, different titles where applicable.

### Organization (조직 구조)

Based on gin7 manual:

- 함대: 최대 60유닛(18,000척), 사령관+부사령관+참모장+참모6+부관 = 10명
- 순찰대: 3유닛(900척), 사령관+부사령관+부관 = 3명
- 수송함대: 수송함20유닛+전투함3유닛, 사령관+부사령관+부관 = 3명
- 지상부대: 양륙함3유닛+육전대3유닛, 사령관 1명
- 행성수비대: 육전대10유닛, 지휘관 1명

### Command Points (커맨드 포인트)

Two types:

- PCP (Political Command Points) - 정략 커맨드 포인트
- MCP (Military Command Points) - 군사 커맨드 포인트
- Recovery: every 5 real-time minutes
- Cross-use: can substitute at 2x cost

### Combat System

**Real-time fleet battles (RTS)**:

- Strategic game: turn-based territory management
- Tactical game: real-time space fleet combat
- Energy allocation: BEAM/GUN/SHIELD/ENGINE/WARP/SENSOR
- Formations: 紡錘(wedge), 艦種(by-class), 混成(mixed), 三列(three-column)

### Victory Conditions

- Capture enemy capital star system
- Enemy controls 3 or fewer star systems (including capital)
- Time limit reached → population comparison

### Package Structure

- Backend: `com.openlogh` (renamed from `com.opensam`)
- Gateway: `com.openlogh.gateway`
- Game: `com.openlogh` (game-app root)

## Architecture Decisions

- **Multi-Process**: Split into `gateway-app` + versioned `game-app` JVMs
- **Session = World**: `SessionState` entity for per-game-session state
- **Logical Isolation**: Game entities use `session_id` FK
- **Turn Engine**: Runs inside `game-app` for strategic (turn-based) processing
- **Combat Engine**: Real-time fleet battle system (WebSocket-based)
- **Two game modes**: Strategic (turn-based management) + Tactical (real-time combat)

<!-- GSD:project-start source:PROJECT.md -->
## Project

**Open LOGH (오픈 은하영웅전설)**

은하영웅전설 VII(gin7, 2004 BOTHTEC)을 웹 기반으로 재구현하는 다인원 온라인 전략 시뮬레이션 게임. OpenSamguk(삼국지 웹게임)을 포크하여 LOGH 세계관으로 변환 중이며, 플레이어는 은하제국 또는 자유행성동맹의 장교로 참가하여 조직 내에서 협력하며 진영의 승리를 목표로 한다.

**Core Value:** gin7의 핵심인 "조직 시뮬레이션" — 직무권한카드 기반 커맨드 시스템으로 다수 플레이어가 계급 구조 안에서 명령/제안/인사/정치를 수행하며, 원작의 라인하르트나 양웬리의 입장을 체험할 수 있는 것.

### Constraints

- **Tech Stack**: Spring Boot 3 (Kotlin) + Next.js 15 + PostgreSQL 16 + Redis 7 — 기존 코드베이스 유지
- **Architecture**: gateway-app + versioned game-app JVM 분리 구조 유지
- **Reference Fidelity**: gin7 매뉴얼의 게임 메카닉스를 최대한 충실히 재현
- **Real-time**: 전술전은 WebSocket 기반 실시간 처리 필요
- **Scale**: 세션당 최대 2,000명 동시 접속 고려
<!-- GSD:project-end -->

<!-- GSD:stack-start source:codebase/STACK.md -->
## Technology Stack

## Languages
- Kotlin 2.1.0 - Backend services (gateway-app, game-app)
- TypeScript 5.x - Frontend (Next.js)
- SQL - Database migrations via Flyway
- JavaScript - Build scripts and tooling
## Runtime
- Java 17 (JVM)
- Node.js 20 (Alpine-based Docker image)
- Backend: Gradle 8.x (with Kotlin DSL)
- Frontend: pnpm (with lockfile: `pnpm-lock.yaml`)
## Frameworks
- Spring Boot 3.4.2 - Web framework, REST APIs
- Spring Data JPA - ORM and entity management
- Spring Security - Authentication/authorization
- Spring WebSocket - Real-time communication (STOMP)
- Spring Data Redis - Caching and session management
- Spring WebFlux - Reactive HTTP client for inter-service communication
- Next.js 16.1.6 - React metaframework, SSR/SSG
- React 19.2.3 - UI component framework
- Tailwind CSS 4 - Utility-first CSS framework
- Three.js 0.170.0 - 3D graphics library
- React Three Fiber 9.5.0 - React renderer for Three.js
- React Three Drei 10.7.7 - Three.js helpers and utilities
- Konva 10.2.0 - Canvas/2D drawing library
- React Konva 19.2.2 - React wrapper for Konva
- TipTap 3.20.0 - WYSIWYG editor framework
- TipTap extensions (color, image, link, text-align, underline)
- JUnit 5 (Jupiter) - Backend unit testing
- Vitest 3.2.4 - Frontend unit testing
- Playwright 1.58.2 - E2E testing
- Flyway 1.0 - Database migration management
- Spring Boot Gradle Plugin 3.4.2 - JAR/WAR building
## Key Dependencies
- Spring Boot WebFlux Starter - Reactive HTTP client for game-app routing
- JJWT (JSON Web Token) 0.12.6 - JWT validation and parsing
- Jackson Module Kotlin - JSON serialization/deserialization
- PostgreSQL Driver 16 - JDBC driver for PostgreSQL
- Spring Data Redis - Redis client for caching
- Flyway Core + PostgreSQL Plugin - Database schema versioning
- Spring Boot Test - Integration testing framework
- Spring Security Test - Security context mocking
- H2 Database - In-memory database for tests
- Kotlin Coroutines Test - Async testing utilities
- Zustand 5.0.11 - Lightweight state management
- React Hook Form 7.71.1 - Efficient form handling
- Zod 4.3.6 - TypeScript-first schema validation
- @hookform/resolvers 5.2.2 - Form resolver adapters
- Axios 1.13.5 - HTTP client for REST API calls
- @stomp/stompjs 7.3.0 - STOMP protocol client for WebSocket
- SockJS Client 1.6.1 - WebSocket fallback library
- Radix UI 1.4.3 - Headless component library
- @radix-ui/react-accessible-icon 1.1.8 - Icon accessibility wrapper
- @radix-ui/react-switch 1.2.6 - Toggle switch component
- Lucide React 0.564.0 - Icon library
- Sonner 2.0.7 - Toast notification system
- React Resizable Panels 4.7.3 - Resizable layout panels
- Class Variance Authority 0.7.1 - CSS-in-JS variant library
- Tailwind Merge 3.4.0 - Tailwind CSS class merger
- js-sha512 0.9.0 - SHA-512 hashing
- Clsx 2.1.1 - Conditional className builder
- Next Themes 0.4.6 - Dark/light mode management
## Configuration
- `.env` file - Local development environment variables
- `.env.example` - Template for required environment variables
- `docker-compose.yml` - Local development services (PostgreSQL, Redis)
- `backend/build.gradle.kts` - Root Gradle configuration
- `backend/shared/build.gradle.kts` - Shared module (DTOs, JWT utilities)
- `backend/gateway-app/build.gradle.kts` - Gateway service configuration
- `backend/game-app/build.gradle.kts` - Game service configuration
- `backend/gateway-app/src/main/resources/application.yml` - Gateway Spring config
- `backend/game-app/src/main/resources/application.yml` - Game Spring config
- `backend/game-app/src/main/resources/application-docker.yml` - Docker environment config
- `backend/game-app/src/main/resources/db/migration/` - Flyway SQL migrations
- `frontend/package.json` - Node.js dependencies and scripts
- `frontend/pnpm-lock.yaml` - Locked dependency versions
- `frontend/next.config.ts` - Next.js configuration
- `frontend/tsconfig.json` - TypeScript compiler options
- `frontend/tailwind.config.ts` - Tailwind CSS configuration
- `frontend/Dockerfile` - Multi-stage Docker build (deps, builder, runner)
## Platform Requirements
- Java 17+ (for backend compilation)
- Gradle 8.x (bundled via wrapper)
- Node.js 20+ (for frontend)
- pnpm 9.x+ (package manager)
- Docker + Docker Compose (for PostgreSQL 16, Redis 7)
- PostgreSQL 16 client tools (optional, for direct DB access)
- Docker + Docker Compose (for containerized deployment)
- Kubernetes (optional, for orchestration)
- GHCR (GitHub Container Registry) - Pre-built images stored here
- Nginx (reverse proxy for routing frontend/backend)
## Key Configuration Values
- PostgreSQL 16
- Connection pool managed by Hikari (Spring default)
- Flyway migrations auto-run on startup
- Redis 7
- Spring Data Redis configured (optional repositories disabled)
- Gateway API: `8080`
- Game API: `9001` (dynamic when spawned by gateway)
- Frontend: `3000` (Next.js dev) or served via Nginx
- Nginx: `80` (HTTP)
<!-- GSD:stack-end -->

<!-- GSD:conventions-start source:CONVENTIONS.md -->
## Conventions

## Naming Patterns
- TypeScript/React: camelCase with `.ts`, `.tsx`, `.test.ts` extensions
- Kotlin: PascalCase for classes/entities, camelCase for functions/files
- CSS: kebab-case for class names, applied via Tailwind or direct className
- Frontend (TypeScript): camelCase for regular functions, PascalCase for React components
- Backend (Kotlin): camelCase for methods, PascalCase for classes
- Frontend: camelCase, nullable types indicated with `?`
- Backend (Kotlin): camelCase for properties, UPPER_SNAKE_CASE for constants
- Frontend: Interface prefix with `Interface` in JSDoc or explicit names
- Backend (Kotlin): Data classes for DTOs, regular classes for entities
- Comments indicate deprecated aliases (backward compat with OpenSamguk)
- Korean characters used in game-specific code (commands, UI text)
## Code Style
- Frontend: ESLint + Next.js core-web-vitals rules enforce style
- Backend: Kotlin style conventions via Spring Boot
- Frontend: `eslint` with next/core-web-vitals
- Backend: No explicit linter config found (Spring Boot conventions)
## Import Organization
- Frontend: `@/*` → `./src/*` defined in `tsconfig.json`
## Error Handling
- Frontend: try/catch in async functions, console.error for logging
- Backend (Kotlin): Custom exceptions, validation exceptions, service-level error handling
- Backend: `IllegalArgumentException`, `IllegalStateException` for business logic violations
- Frontend: Inline validation in component logic
- Backend: Service-level validation, throws on failure
## Logging
- Frontend: `console.error()` for error logging in catch blocks
- Backend: Spring Boot default logger via SLF4J (implicit, not shown in code)
## Comments
- Complex Korean particle logic (JosaUtil): Detailed explanation of algorithm
- Legacy parity checks: "Legacy parity: ..." prefix indicates OpenSamguk compatibility code
- Non-obvious game logic: Comments explain game rules and state transitions
- Data flow for complex operations: Describe why not just what
- Frontend: Minimal use, type signatures sufficient due to TypeScript
- Backend (Kotlin): KDoc style (Kotlin documentation)
## Function Design
- Frontend: Utility functions < 50 lines (e.g., `getServerPhase()`, `getPlayerInfo()`)
- Component rendering: Extract helper functions when logic exceeds 20 lines
- Backend: Service methods < 100 lines, business logic extracted to utilities
- Frontend: Use object destructuring for > 2 parameters
- Backend: Constructor injection for dependencies, method params < 4
- Frontend: Explicit return types on functions
- Backend: Non-nullable returns preferred, Optional<T> for nullable
- Frontend: async/await in stores and components
- Backend: Kotlin coroutines with `runBlocking` in tests
## Module Design
- Frontend: Named exports for utilities and types
- Backend: Public constructors and methods, package-private for internals
- Frontend: `types/index.ts` aggregates all type exports
- Backend: Not applicable (package structure used)
- Frontend co-located: Tests alongside source
- Backend: Standard Spring structure
<!-- GSD:conventions-end -->

<!-- GSD:architecture-start source:ARCHITECTURE.md -->
## Architecture

## Pattern Overview
- Multi-JVM process isolation: Gateway (port 8080) + versioned Game App instances (9001-9999)
- Session-centric world management: Each game world maps to a `SessionState` entity
- Command-centric action system: All player actions flow through CQRS-style command dispatch
- Turn-based strategic gameplay + Real-time tactical combat via WebSocket
- Event-driven architecture with CQRS event sourcing in turn engine
## Layers
- Purpose: HTTP API gateway, world lifecycle management, authentication, process orchestration
- Location: `backend/gateway-app/src/main/kotlin/com/openlogh/gateway/`
- Contains: Controllers for auth, world management, admin, proxying to game-app instances
- Depends on: PostgreSQL (entities), Redis (sessions), game-app (HTTP proxy)
- Used by: Frontend, external clients
- Purpose: Core strategic turn engine, command execution, event processing, real-time battle coordination
- Location: `backend/game-app/src/main/kotlin/com/openlogh/`
- Contains: Commands, services, entities, repositories, turn engine, WebSocket controllers
- Depends on: PostgreSQL (game state), Redis (cache), shared library (models, DTOs)
- Used by: Gateway (reverse proxy), Frontend (WebSocket)
- Purpose: Cross-process models, DTOs, error definitions, security utilities
- Location: `backend/shared/src/main/kotlin/com/openlogh/`
- Contains: Entity definitions, shared DTOs, security models, error handling
- Depends on: Nothing
- Used by: gateway-app, game-app
- Purpose: User interface for lobby, game management, real-time game display
- Location: `frontend/src/`
- Contains: React components, Zustand stores, API client, WebSocket integration
- Depends on: Backend APIs (gateway-app), WebSocket (game-app)
- Used by: Browser clients
## Data Flow
- **Persistent State:** PostgreSQL entities (Officer, Planet, Faction, Fleet, SessionState, etc.)
- **Session State:** SessionStorage (frontend) via Zustand for currentWorld, authenticated user
- **Real-time State:** Redis for connected client tracking (optional), game-app memory for active turn processing
- **Cache Invalidation:** Event broadcast triggers frontend store updates via WebSocket
## Key Abstractions
- Purpose: Container for an entire game instance with independent state
- Examples: `backend/game-app/src/main/kotlin/com/openlogh/entity/SessionState.kt`
- Pattern: One SessionState per game world; isolation via `session_id` foreign keys on all entities
- Purpose: Controllable character unit (player or NPC)
- Examples: `backend/game-app/src/main/kotlin/com/openlogh/entity/Officer.kt` (10KB, 8-stat system with abilities)
- Pattern: Links to Faction, Planet, Fleet; carries metadata for actions, position cards, stats
- Purpose: Military unit grouping ships for combat
- Examples: `backend/game-app/src/main/kotlin/com/openlogh/entity/Fleet.kt`
- Pattern: Belongs to Officer, contains ship count by class (battleship, cruiser, destroyer, etc.)
- Purpose: Territorial unit with resources and defenses
- Examples: `backend/game-app/src/main/kotlin/com/openlogh/entity/Planet.kt`
- Pattern: Owned by Faction, generates funds/supplies, can be fortified with orbital/ground defense
- Purpose: Encapsulates single player action with validation rules
- Examples: `backend/game-app/src/main/kotlin/com/openlogh/command/general/` (move, attack, recruit)
- Pattern: Registered in `CommandRegistry`, executed by `CommandExecutor`, validated against constraints
- Purpose: Stateless calculators for specific game systems
- Examples:
- Pattern: Injected into command handlers; pure functions operating on entities
- Purpose: Immutable record of game outcome (for logging and replay)
- Examples: `backend/game-app/src/main/kotlin/com/openlogh/entity/Event.kt`
- Pattern: Broadcast via WebSocket to all connected clients; persisted to database
## Entry Points
- Location: `backend/gateway-app/src/main/kotlin/com/openlogh/gateway/GatewayApplication.kt`
- Triggers: Spring Boot startup (port 8080)
- Responsibilities: HTTP API, authentication, world lifecycle orchestration, process management
- Location: `backend/game-app/src/main/kotlin/com/openlogh/OpenloghApplication.kt`
- Triggers: Spawned by `GameProcessOrchestrator` as separate JVM process (port 9001+)
- Responsibilities: Game logic execution, WebSocket event distribution, turn engine
- Location: `frontend/src/app/layout.tsx` (root), `frontend/src/app/(game)/layout.tsx` (game)
- Triggers: Browser navigation to `/`
- Responsibilities: Auth routing, game initialization, store hydration
- Location: `backend/gateway-app/src/main/kotlin/com/openlogh/gateway/orchestrator/WorldActivationBootstrap.kt`
- Triggers: On gateway startup
- Responsibilities: Restore previously active worlds by reattaching to running game instances or spawning new ones
## Error Handling
- **Business Logic Errors:** `CommandResult` (success flag + log messages) for command validation failures
- **API Errors:** Custom `ApiException` (code + message) caught by `@ControllerAdvice` for HTTP response mapping
- **WebSocket Errors:** Broadcast via `GameEventService` as client-side events
- **Validation Errors:** Position card gating in `CommandGating`, CP cost validation, cooldown checks
- **Process Errors:** `GameProcessOrchestrator` retries failed game-app startups with exponential backoff
- **Session Errors:** Missing `SessionState` returns 404; invalid session_id FK constraints prevent orphaned data
## Cross-Cutting Concerns
- Backend: SLF4J via Kotlin `LoggerFactory.getLogger()` pattern
- Game-app logs written to `logs/game-{commitSha}.log` by `GameProcessOrchestrator`
- Frontend: Console logging in stores/components, no centralized aggregation
- **Command Validation:** Constraints checked in `CommandExecutor.executeGeneralCommand()` before CP consumption
- **Entity Validation:** JPA `@NotNull`, `@Column(nullable=false)` at database level
- **Position Card Gating:** `CommandGating.canExecuteCommand()` checks command group against held cards
- **Resource Constraints:** CP cost, cooldown, CP pool size enforced at execution time
- **Gateway:** JWT token validation in `AuthService`, stored in `Authorization: Bearer` header
- **Game-app:** Session-scoped via `sessionId` path variable (WebSocket) or request parameter (HTTP)
- **Frontend:** Token stored in SessionStorage, refreshed via `AccountOAuthService` (Kakao OAuth)
- **Admin Routes:** Role-based access via `GatewayAdminAuthorizationService.isAdmin()` check
- **Command Channel:** `/app/command/{sessionId}/execute` → immediate ACK + async processing
- **Event Channel:** `/topic/world/{sessionId}/events` → broadcast to all connected clients
- **Battle Channel:** `/topic/world/{sessionId}/battle` → real-time tactical combat updates
- **Protocol:** STOMP over SockJS, managed by Spring WebSocket auto-configuration
<!-- GSD:architecture-end -->

<!-- GSD:workflow-start source:GSD defaults -->
## GSD Workflow Enforcement

Before using Edit, Write, or other file-changing tools, start work through a GSD command so planning artifacts and execution context stay in sync.

Use these entry points:
- `/gsd:quick` for small fixes, doc updates, and ad-hoc tasks
- `/gsd:debug` for investigation and bug fixing
- `/gsd:execute-phase` for planned phase work

Do not make direct repo edits outside a GSD workflow unless the user explicitly asks to bypass it.
<!-- GSD:workflow-end -->

<!-- GSD:profile-start -->
## Developer Profile

> Profile not yet configured. Run `/gsd:profile-user` to generate your developer profile.
> This section is managed by `generate-claude-profile` -- do not edit manually.
<!-- GSD:profile-end -->
