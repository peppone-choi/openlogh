# 오픈삼국 (OpenSamguk)

Web-based Three Kingdoms strategy game.

## Tech Stack

- Backend: Spring Boot 3 (Kotlin)
- Frontend: Next.js 15
- Database: PostgreSQL 16
- Cache: Redis 7

## Project Structure

- `backend/` - Spring Boot backend
- `frontend/` - Next.js frontend

## Image CDN

Base URL (default): https://cdn.jsdelivr.net/gh/peppone-choi/opensamguk-image@master/
Configurable via: `NEXT_PUBLIC_IMAGE_CDN_BASE`

## Commands

### Backend

```bash
cd backend && ./gradlew :gateway-app:bootRun
# game instance
cd backend && ./gradlew :game-app:bootRun
```

### Frontend

```bash
cd frontend && pnpm dev
```

### Verification

```bash
./scripts/verify/install-hooks.sh
./verify pre-commit
./verify ci
```

### Docker (DB services)

```bash
docker-compose up -d
```

## Parity Target

- **패러티 대상: devsam/core PHP (`legacy-core/` 폴더, https://storage.hided.net/gitea/devsam/core)**
- **패러티 문서는 신뢰하지 않음. 코드로만 판단.**
- 장수 스탯: **3-stat 시스템** (통솔/무력/지력) + 정치/매력은 opensamguk에서 추가
    - `leadership`, `strength`, `intel` (core 원본), `politics`, `charm` (opensamguk 확장)

## Officer Rank System

관직은 국가 레벨에 따라 결정됨 (`officer_ranks.json` 참조):

| 국가 레벨  | 칭호        | 문관 예시    | 무관 예시         |
| ---------- | ----------- | ------------ | ----------------- |
| 7 (황제)   | 승상/태위   | 사도, 사공   | 대도독, 표기장군  |
| 6 (왕)     | 태상/광록훈 | 위위, 태복   | 정동/남/서/북장군 |
| 5 (공)     | 정위/대홍려 | 종저, 대사농 | 진동/남/서/북장군 |
| 0 (주자사) | 기본        | 종사좨주     | 호위장군, 비장군  |

특수 국가: 황건(천공장군 체계)

## Architecture Decisions

- **Multi-Process**: Split into `gateway-app` + versioned `game-app` JVMs
- **World = Profile**: `World` entity replaces core's per-server model
- **Logical Isolation**: Game entities use `world_id` FK (no schema-per-profile)
- **Version Pinning**: `world_state.commit_sha` + `world_state.game_version` pin each world to a game build
- **Turn Engine**: Runs inside `game-app` per-version JVM and processes attached worlds
- **Field Naming**: Follow core conventions (`intel` not `intelligence`, `crew`/`crewType`/`train`/`atmos`)
- **DB**: PostgreSQL (core uses MariaDB)
- **NPC Token**: Redis-based (core uses `select_npc_token` DB table)

## Reference

- **Core PHP source**: `legacy-core/` (parity target, cloned from devsam/core)
- **Deploy Docker**: `https://github.com/peppone-choi/opensamguk-deploy` (배포용 docker-compose)
- Image CDN: `https://cdn.jsdelivr.net/gh/peppone-choi/opensamguk-image@master/`

### Core PHP Source (`legacy-core/`)

| Path                         | Content                                |
| ---------------------------- | -------------------------------------- |
| `hwe/sammo/Command/General/` | 55개 장수 커맨드                       |
| `hwe/sammo/Command/Nation/`  | 38개 국가 커맨드                       |
| `hwe/sammo/API/`             | 78개 API 엔드포인트 (12 카테고리)      |
| `hwe/sql/schema.sql`         | DB 스키마 (45+ 테이블)                 |
| `hwe/func.php`               | 메인 게임 함수 (80KB)                  |
| `hwe/GeneralAI.php`          | NPC AI 구현 (153KB)                    |
| `hwe/process_war.php`        | 전투 처리 (33KB)                       |
| `hwe/scenario/`              | 시나리오 83종 + 맵 8종 + 병종 7종      |
| `hwe/data/`                  | 게임 상수, 도시, 병종 데이터           |
| `src/daemon.ts`              | 턴 데몬 (TypeScript, 441줄)            |
| `src/sammo/`                 | 유틸리티 클래스 (API, Session, RNG 등) |

## Game Data

- `backend/game-app/src/main/resources/data/game_const.json` - 도시 레벨/지역 매핑, 초기값
- `backend/game-app/src/main/resources/data/officer_ranks.json` - 관직 체계
- `backend/game-app/src/main/resources/data/maps/` - 맵 데이터 (che, miniche, cr 등 8종)

## Skills

- `verify-implementation` - 구현 검증 + 패러티 체크 (docs 대비)
- `build-and-test` - 백엔드/프론트엔드 빌드 실행
- `add-backend-endpoint` - 백엔드 엔드포인트 추가 가이드 (docs 참조 필수)
- `add-frontend-page` - 프론트엔드 페이지 추가 가이드 (SPA plan 참조 필수)
- `legacy-compare` - 레거시 vs 현재 구현 비교 (docs + legacy PHP 전체 참조)
- `reference-docs` - 기능별 docs/legacy 참조 파일 조회
- `manage-skills` - 스킬 관리 및 업데이트
- `verify-entity-parity` - Entity/Type/Schema 정합성, 5-stat, 필드 네이밍 검증
- `verify-command-parity` - 레거시 PHP 93개 커맨드(55 장수 + 38 국가) 구현 상태 추적
- `verify-resource-parity` - 레거시 게임 리소스(시나리오, 맵, 도시, 관직, 병종) 존재 확인
- `verify-logic-parity` - core2026/레거시 대비 게임 로직 동일 결과 확인
- `verify-game-tests` - 백엔드/프론트엔드 테스트 러너와 커밋 전 검증 파이프라인 확인
- `verify-frontend-parity` - 레거시/SPA plan 대비 프론트엔드 페이지, 출력 정보, UI 확인
- `verify-docs-parity` - docs/architecture 문서 의도 대비 구현 반영 확인
- `verify-daemon-parity` - NPC AI + 턴 데몬 레거시/docs 대비 동작 확인
- `verify-npc-data` - 시나리오 NPC 장수 삼국지14 기준 5-stat(통무지정매) 최신화 확인
- `verify-architecture` - 백엔드 TDD/DDD/클린-레이어드 아키텍처, 레포지토리 패턴 준수 검증
- `verify-api-parity` - 풀스택 1:1 메서드-레벨 패러티 (Controller-Service-Repository 체인, FE API-BE 엔드포인트 매칭, 데드 코드, 타입 호환성)
- `verify-type-parity` - FE TypeScript 타입 ↔ BE Kotlin DTO/Entity strict 타입 매칭 (loose 타입, 인라인 DTO, 필드 불일치 탐지)
- `find-skills` - 스킬 검색/설치 도우미
- `frontend-design` - 프로덕션급 프론트엔드 UI 생성
- `vercel-react-best-practices` - React/Next.js 성능 최적화 가이드라인
- `vercel-composition-patterns` - React 컴포지션 패턴 (compound components, render props)
- `vercel-react-native-skills` - React Native/Expo 모범 사례
- `web-design-guidelines` - Web UI 접근성/디자인 감사

### Skill Usage Guidelines

프론트엔드 작업 시 다음 스킬을 적극 활용:

- **React/Next.js 코드 작성/리뷰 시** → `vercel-react-best-practices` 참조
- **컴포넌트 설계/리팩터링 시** → `vercel-composition-patterns` 참조
- **UI 페이지/컴포넌트 생성 시** → `frontend-design` 사용
- **UI 접근성/디자인 리뷰 시** → `web-design-guidelines` 사용

---

## Development Guidelines (Karpathy Method)

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use judgment.

### 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:

- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

### 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

### 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:

- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.

When your changes create orphans:

- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.

### 4. Goal-Driven Execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:

- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"

For multi-step tasks, state a brief plan:

```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
```

Strong success criteria let you loop independently. Weak criteria require constant clarification.

<!-- GSD:project-start source:PROJECT.md -->
## Project

**OpenSamguk Legacy Parity**

A web-based Three Kingdoms (samguk) strategy game being ported from a legacy PHP codebase (devsam/core) to a modern Spring Boot (Kotlin) + Next.js stack. The project already has substantial infrastructure and game logic implemented, but needs systematic verification and completion of parity gaps against the legacy PHP source.

**Core Value:** Every game mechanic must produce identical outcomes to the legacy PHP implementation given the same inputs -- commands, turn processing, battle resolution, NPC AI, and economy must match legacy behavior exactly.

### Constraints

- **Parity target**: `legacy-core/` PHP source is the single source of truth -- not docs, not assumptions
- **Field naming**: Must follow core conventions (intel not intelligence, crew/crewType/train/atmos)
- **Database**: PostgreSQL (legacy uses MariaDB) -- SQL differences must be handled
- **Architecture**: Must maintain gateway-app + game-app split
- **NPC Token**: Redis-based (legacy uses select_npc_token DB table)
<!-- GSD:project-end -->

<!-- GSD:stack-start source:codebase/STACK.md -->
## Technology Stack

## Languages
- Kotlin 2.1.0 - Backend (Spring Boot, all three modules: `shared`, `gateway-app`, `game-app`)
- TypeScript 5.x - Frontend (Next.js, React)
- SQL - Flyway migrations (`backend/game-app/src/main/resources/db/migration/`)
- Bash - Verification scripts (`scripts/verify/run.sh`, `verify`)
## Runtime
- JVM 17 (target: `JvmTarget.JVM_17`)
- Docker base image: `eclipse-temurin:17-jdk-alpine`
- JVM args: `-Xmx2g -XX:+HeapDumpOnOutOfMemoryError`
- Node.js 20 (Docker) / Node.js 24 (CI)
- Docker base image: `node:20-alpine`
- Next.js standalone output mode
- Gradle 8.12 (wrapper, `backend/gradle/wrapper/gradle-wrapper.properties`)
- pnpm 10.26.2 (`frontend/pnpm-lock.yaml` present)
- Lockfiles: `frontend/pnpm-lock.yaml` (present), `frontend/package-lock.json` (also present - legacy)
## Frameworks
- Spring Boot 3.4.2 - Backend framework (`backend/build.gradle.kts`)
- Next.js 16.1.6 - Frontend framework (`frontend/package.json`)
- React 19.2.3 - UI library (`frontend/package.json`)
- JUnit Jupiter - Backend unit/integration tests (`backend/build.gradle.kts`)
- Spring Boot Test + Spring Security Test - Backend test support
- H2 Database - Backend test DB (`application-test.yml`, mode=PostgreSQL)
- Vitest 3.2.4 - Frontend unit tests (`frontend/vitest.config.ts`)
- Playwright 1.58.2 - Frontend E2E tests (`frontend/playwright.config.ts`)
- Gradle 8.12 - Backend build (`backend/gradle/wrapper/gradle-wrapper.properties`)
- Spring Dependency Management 1.1.7 - BOM management
- Kotlin Spring plugin 2.1.0 - `open` class generation for Spring
- Kotlin JPA plugin 2.1.0 - No-arg constructors for JPA entities
- Docker multi-stage builds - Production images (`backend/Dockerfile`, `frontend/Dockerfile`)
## Key Dependencies
### Backend Critical
- `spring-boot-starter-web` - REST API (gateway-app + game-app)
- `spring-boot-starter-data-jpa` - ORM / database access (Hibernate)
- `spring-boot-starter-security` - Authentication/authorization
- `spring-boot-starter-validation` - Request validation (Jakarta)
- `spring-boot-starter-data-redis` - Redis client (gateway-app + game-app)
- `spring-boot-starter-websocket` - WebSocket/STOMP (game-app only)
- `spring-boot-starter-webflux` - HTTP client for proxying to game JVMs (gateway-app only)
- `org.postgresql:postgresql` - PostgreSQL JDBC driver
- `org.flywaydb:flyway-core` + `flyway-database-postgresql` - Schema migrations
- `io.jsonwebtoken:jjwt-api:0.12.6` - JWT creation/validation (all modules)
- `org.jetbrains.kotlinx:kotlinx-coroutines-core` - Turn engine async (game-app only)
- `com.fasterxml.jackson.module:jackson-module-kotlin` - JSON serialization (all modules)
### Frontend Critical
- `axios` ^1.13.5 - HTTP client (`frontend/src/lib/api.ts`)
- `@stomp/stompjs` ^7.3.0 - WebSocket STOMP client (`frontend/src/lib/websocket.ts`)
- `sockjs-client` ^1.6.1 - WebSocket fallback transport
- `zustand` ^5.0.11 - State management
- `zod` ^4.3.6 - Schema validation
- `react-hook-form` ^7.71.1 + `@hookform/resolvers` ^5.2.2 - Form management
- `konva` ^10.2.0 + `react-konva` ^19.2.2 - Canvas rendering (game map)
- `next-themes` ^0.4.6 - Theme switching
### Frontend UI
- `radix-ui` ^1.4.3 - Headless UI primitives (dialog, avatar, switch, scroll-area, etc.)
- `shadcn` ^3.8.4 - Component generator (new-york style, `frontend/components.json`)
- `lucide-react` ^0.564.0 - Icon library
- `tailwindcss` ^4 + `@tailwindcss/postcss` ^4 - CSS framework
- `tw-animate-css` ^1.4.0 - Animation utilities
- `class-variance-authority` ^0.7.1 + `clsx` ^2.1.1 + `tailwind-merge` ^3.4.0 - Class management
- `sonner` ^2.0.7 - Toast notifications
- `react-resizable-panels` ^4.7.3 - Resizable panel layouts
### Frontend Rich Text
- `@tiptap/react` ^3.20.0 + `@tiptap/starter-kit` - Rich text editor
- Extensions: color, image, link, text-align, text-style, underline
### Frontend Utilities
- `js-sha512` ^0.9.0 - Client-side hashing
## Configuration
- `backend/gateway-app/src/main/resources/application.yml` - Gateway defaults (port 8080)
- `backend/gateway-app/src/main/resources/application-docker.yml` - Docker profile overrides
- `backend/game-app/src/main/resources/application.yml` - Game defaults (port 9001)
- `backend/game-app/src/main/resources/application-docker.yml` - Docker profile overrides
- `backend/game-app/src/main/resources/application-test.yml` - Test profile (H2 in-memory)
- `backend/gradle.properties` - JVM/daemon settings
- `frontend/next.config.ts` - Next.js config (standalone output, image CDN patterns)
- `frontend/tsconfig.json` - TypeScript config (ES2017 target, `@/*` path alias)
- `frontend/vitest.config.ts` - Unit test config (node environment, `@/` alias)
- `frontend/playwright.config.ts` - E2E test config (chromium only)
- `frontend/eslint.config.mjs` - ESLint flat config (next core-web-vitals + typescript)
- `frontend/postcss.config.mjs` - PostCSS with Tailwind CSS v4 plugin
- `frontend/components.json` - shadcn/ui config (new-york style, RSC enabled)
- `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `DB_PORT` - PostgreSQL connection
- `REDIS_PORT` - Redis connection
- `HTTP_PORT` - Nginx listen port
- `TAG` - Docker image tag
- `ADMIN_BOOTSTRAP_ENABLED`, `ADMIN_LOGIN_ID`, `ADMIN_PASSWORD`, `ADMIN_DISPLAY_NAME`, `ADMIN_GRADE` - Bootstrap admin
- `NEXT_PUBLIC_IMAGE_CDN_BASE` - Image CDN URL
- `NEXT_PUBLIC_API_URL` - Backend API URL (default: `/api`)
- `NEXT_PUBLIC_WS_URL` - WebSocket URL (default: `http://localhost:8080`)
- `NEXT_PUBLIC_KAKAO_ENABLED` - Kakao OAuth feature flag
- `KAKAO_REST_API_KEY` - Kakao OAuth API key (backend)
- `OAUTH_ACCOUNT_LINK_CALLBACK_URI` - OAuth callback URI (backend)
- `backend/build.gradle.kts` - Root build config with plugin versions
- `backend/settings.gradle.kts` - Multi-module: `shared`, `gateway-app`, `game-app`
- JPA `allOpen` annotations for Entity/MappedSuperclass/Embeddable in both app modules
## Platform Requirements
- Java 17 (JDK)
- Node.js 20+ with pnpm
- Docker + Docker Compose (for PostgreSQL 16 + Redis 7)
- Ports: 5432 (PostgreSQL), 6379 (Redis), 8080 (gateway), 9001 (game), 3000 (frontend)
- Docker containers on AWS EC2
- nginx reverse proxy (port 80)
- Docker socket mount for gateway container orchestration
- GHCR (GitHub Container Registry) for image storage
## Multi-Module Backend Structure
- `shared` has no Spring Boot plugin - produces a plain JAR
- Both `gateway-app` and `game-app` depend on `shared`
- `gateway-app` runs on port 8080, proxies game requests to game JVMs via WebFlux
- `game-app` runs on port 9001, handles game logic and WebSocket connections
- Flyway migrations run in `game-app` only (disabled in `gateway-app`)
<!-- GSD:stack-end -->

<!-- GSD:conventions-start source:CONVENTIONS.md -->
## Conventions

## Naming Patterns
- Entity classes: PascalCase singular nouns - `General.kt`, `City.kt`, `Nation.kt`, `WorldState.kt`
- Repositories: `{Entity}Repository.kt` - `GeneralRepository.kt`, `CityRepository.kt`
- Services: `{Domain}Service.kt` - `GeneralService.kt`, `EconomyService.kt`, `MapService.kt`
- Controllers: `{Domain}Controller.kt` - `GeneralController.kt`, `AdminController.kt`
- DTOs: grouped by domain in `{Domain}Dtos.kt` files - `GeneralDtos.kt`, `AuthDtos.kt`, `CommandDtos.kt`
- Tests: `{ClassUnderTest}Test.kt` - `GeneralServiceTest.kt`, `CommandTest.kt`
- Game commands (Korean): `che_{한글명}.kt` - follows legacy PHP naming from `hwe/sammo/Command/General/`
- Components: kebab-case `.tsx` - `city-basic-card.tsx`, `command-panel.tsx`, `game-dashboard.tsx`
- Hooks: camelCase with `use` prefix - `useWebSocket.ts`, `useDebouncedCallback.ts`, `use-mobile.ts` (mixed convention)
- Stores: camelCase with `Store` suffix - `gameStore.ts`, `authStore.ts`, `worldStore.ts`
- Lib utilities: kebab-case - `api-error.ts`, `game-utils.ts`, `map-constants.ts`
- Tests: co-located with source, `.test.ts` / `.test.tsx` suffix - `gameStore.test.ts`, `city-basic-card.test.ts`
- Pages: Next.js App Router convention - `page.tsx` in route directories
- Use camelCase: `listByWorld()`, `getMyGeneral()`, `createGeneral()`
- Repository methods follow Spring Data naming: `findByWorldId()`, `findByWorldIdAndUserId()`
- Service methods use domain verbs: `possessNpc()`, `buildPoolGeneral()`, `selectFromPool()`
- React components: PascalCase exported functions - `export function CityBasicCard()`
- Hooks: camelCase with `use` prefix - `useWebSocket()`, `useGameStore()`
- Utility functions: camelCase - `applyApiErrorMessage()`, `extractAuthErrorMessage()`
- Store actions: camelCase verbs - `loadAll()`, `loadMap()`, `clear()`
- Backend: camelCase for local variables, UPPER_SNAKE_CASE for constants
- Frontend: camelCase for variables, UPPER_SNAKE_CASE for module-level constants (`LOGIN_TOKEN_KEY`, `OTP_TICKET_STORAGE_KEY`)
- Interfaces: PascalCase with descriptive suffixes - `FrontInfoResponse`, `GeneralFrontInfo`, `CityBasicCardProps`
- Type aliases: PascalCase - `MailboxType`, `CommandArg`, `InheritBuffType`
- All types centralized in `frontend/src/types/index.ts`
- Data classes for DTOs: PascalCase with `Request`/`Response` suffixes - `CreateGeneralRequest`, `GeneralResponse`
- Entities: PascalCase singular nouns matching table names - `General`, `City`, `Nation`
## Field Naming (Legacy Parity Critical)
- `intel` (NOT `intelligence`)
- `crew` / `crewType` / `train` / `atmos` (military stats)
- `agri` / `comm` / `secu` / `def` / `wall` (city stats)
- `pop` / `popMax` / `agriMax` / `commMax` / `secuMax` / `defMax` / `wallMax`
- `dex1` through `dex5` (dexterity stats)
- `npcState` / `npcOrg` / `killTurn` / `turnTime`
- `officerLevel` / `officerCity`
- `belong` / `betray` / `personalCode` / `specialCode` / `specAge`
- `bill` / `rate` / `rateTmp` (nation economy)
## Code Style
- 4-space indentation
- Single quotes for strings
- Trailing commas in multi-line structures
- No semicolons (auto-inserted by transpiler, but code consistently omits trailing semicolons in some files and includes them in others -- mixed but generally included)
- No Prettier config file detected -- style enforced by ESLint
- 4-space indentation (Kotlin default)
- Trailing commas in multi-line parameter lists and when-expressions
- String templates used for interpolation: `"${variable}"`
- ESLint 9 with flat config: `frontend/eslint.config.mjs`
- Extends `eslint-config-next/core-web-vitals` and `eslint-config-next/typescript`
- TypeScript strict mode enabled in `frontend/tsconfig.json`
- No dedicated Kotlin linter (no detekt/ktlint config detected)
- Kotlin compiler with `-Xjsr305=strict` for null-safety interop
- JPA `allOpen` annotations for entities: `@Entity`, `@MappedSuperclass`, `@Embeddable`
## Import Organization
## Error Handling
- Axios interceptor catches 401 responses globally, clears token, redirects to `/login` (except for auth endpoints): `frontend/src/lib/api.ts`
- 400 responses: `applyApiErrorMessage()` extracts backend validation error messages: `frontend/src/lib/api-error.ts`
- Auth errors: `extractAuthErrorMessage()` provides fallback message extraction: `frontend/src/lib/auth-error.ts`
- Component-level: try-catch with `toast.error()` for user-facing errors (Sonner toast library)
- Store-level: `Promise.allSettled()` for parallel requests with partial failure tolerance: `frontend/src/stores/gameStore.ts`
- Controllers return `ResponseEntity` with explicit HTTP status codes
- Null checks return `ResponseEntity.notFound().build()` or `ResponseEntity.badRequest().build()`
- Auth checks via `SecurityContextHolder.getContext().authentication?.name`
- Service-level: `IllegalArgumentException` with Korean error messages for validation
- No global `@ControllerAdvice` exception handler detected -- errors handled per-controller
## Logging
- No structured logging framework
- `console` methods for development debugging
- User-facing notifications via `sonner` toast: `toast.info()`, `toast.warning()`, `toast.error()`
- Sound effects on events via `playSoundEffect()`: `frontend/src/hooks/useSoundEffects.ts`
- Spring Boot default logging (SLF4J/Logback)
- No custom logging configuration detected
- Game records stored in database via `RecordService` for audit trail
## Comments
- Legacy parity references: `// Legacy: score = clamp(...)` with PHP line references
- JSDoc `/** */` on interfaces in `frontend/src/types/index.ts` for complex fields
- `@deprecated` annotations for superseded types
- Korean-language comments throughout (matches development team language)
## Function Design
- Constructor injection via Kotlin primary constructor: `class GeneralService(private val generalRepository: GeneralRepository, ...)`
- `@Service` annotation on all service classes
- `@Transactional` on write operations
- Methods return domain entities or null (not Optional)
- Companion objects for constants: `companion object { private const val JOIN_STAT_TOTAL = 350 }`
- Functional components only (no class components)
- `'use client'` directive at top of client-side components
- Props defined as inline interfaces: `interface CityBasicCardProps { ... }`
- Early return for null/empty states: `if (!city) return null;`
- `data-tutorial` attributes on key elements for tutorial system
- Created with `create<StoreType>((set) => ({...}))` pattern
- Exported as `use{Name}Store` hooks
- State and actions co-located in single store definition
- In-flight deduplication for network requests (see `_inflightLoadAll` in `frontend/src/stores/gameStore.ts`)
## Module Design
- Multi-module Gradle project: `shared`, `gateway-app`, `game-app`
- `shared` module: DTOs, JWT validation, Jackson configuration (no Spring Boot app)
- `gateway-app`: auth, user management, proxy to game JVMs
- `game-app`: game logic, turn engine, commands, entities
- Package structure: `com.opensam.{layer}` - controller, service, repository, entity, dto, engine, command
- No barrel files except `frontend/src/types/index.ts` (single barrel for all types)
- API clients grouped by domain in `frontend/src/lib/gameApi.ts` as object literals: `worldApi`, `nationApi`, `cityApi`, `generalApi`
- Stores per domain: `authStore.ts`, `gameStore.ts`, `worldStore.ts`, `generalStore.ts`
- Backend: standard Spring component scanning (no explicit exports)
- Frontend: named exports for components and utilities; default export only for `api` instance (`frontend/src/lib/api.ts`)
## API Layer Pattern
## Git Commit Message Style
- `feat:` - new features
- `fix:` - bug fixes
- `chore:` - maintenance tasks
- `revert:` - reverts
## TDD Gate (Pre-Commit Hook)
- Backend source changes (`backend/.*/src/main/.*\.kt`) MUST be accompanied by backend test changes
- Frontend source changes (`frontend/src/(app|components|lib|stores|hooks)/.*\.(ts|tsx)`) MUST be accompanied by frontend test changes
- Deleting committed tests is blocked and requires manual review
- Installed via `scripts/verify/install-hooks.sh`
<!-- GSD:conventions-end -->

<!-- GSD:architecture-start source:ARCHITECTURE.md -->
## Architecture

## Pattern Overview
- Two separate Spring Boot JVM processes: `gateway-app` (auth, routing, orchestration) and `game-app` (game logic, turn engine, domain)
- Gateway proxies authenticated requests to game-app instances via HTTP (WebClient)
- Game-app instances are version-pinned and can run multiple worlds per JVM
- Frontend is a standalone Next.js 15 SPA communicating via REST + WebSocket (STOMP/SockJS)
- Shared Kotlin library (`shared` module) provides DTOs, JWT verification, and game constants across both apps
- Legacy PHP codebase (`legacy-core/`) serves as parity target -- not runtime code
## Layers
- Purpose: Authentication, authorization, world management, request routing to game instances
- Location: `backend/gateway-app/src/main/kotlin/com/opensam/gateway/`
- Contains: Auth controllers, JWT filter, game proxy, world orchestration, admin endpoints
- Depends on: `shared` module, PostgreSQL, Redis
- Used by: Frontend (all `/api/**` requests hit gateway first)
- Purpose: All game domain logic -- commands, turn engine, war, economy, NPC AI, scenarios
- Location: `backend/game-app/src/main/kotlin/com/opensam/`
- Contains: Controllers, services, entities, repositories, engine, commands
- Depends on: `shared` module, PostgreSQL, Redis, Flyway migrations
- Used by: Gateway (proxied requests), Turn daemon (internal scheduler)
- Purpose: Cross-cutting DTOs, JWT token verifier, game constants, scenario data model
- Location: `backend/shared/src/main/kotlin/com/opensam/shared/`
- Contains: `dto/` (AuthDtos, AccountDtos, AdminDtos, WorldDtos, JwtUserPrincipal), `error/ErrorResponse`, `model/ScenarioData`, `security/JwtTokenVerifier`, `GameConstants`
- Depends on: Jackson, JJWT, Jakarta Validation
- Used by: Both gateway-app and game-app
- Purpose: Player-facing SPA -- game UI, lobby, auth, admin panel, tutorial
- Location: `frontend/src/`
- Contains: Next.js App Router pages, React components, Zustand stores, API clients, types
- Depends on: Next.js 15, React, Zustand, Axios, shadcn/ui, STOMP.js, SockJS
- Used by: Players via browser
- Purpose: Static game reference data (scenarios, maps, items, officer ranks, general pool)
- Location: `backend/shared/src/main/resources/data/`
- Contains: JSON files for scenarios (80+), maps (9), items, officer ranks, general pool
- Depends on: Nothing (pure data)
- Used by: `ScenarioService`, `MapService`, `OfficerRankService`, `ItemService`, `GameConstService`
## Data Flow
- `useAuthStore` (Zustand): JWT token, user info, auth state -- persisted in `localStorage`
- `useWorldStore` (Zustand + persist): Current world, world list -- persisted in `sessionStorage`
- `useGeneralStore` (Zustand + persist): Current player's general data -- persisted in `sessionStorage`
- `useGameStore` (Zustand): Transient game state (cities, nations, etc.) -- not persisted
- WebSocket (`useWebSocket` hook): STOMP/SockJS connection for real-time turn/battle/diplomacy events
## Key Abstractions
- Purpose: Encapsulates all player actions (55 general commands + 38 nation commands)
- Examples: `backend/game-app/src/main/kotlin/com/opensam/command/general/che_농지개간.kt`, `backend/game-app/src/main/kotlin/com/opensam/command/nation/che_선전포고.kt`
- Pattern: Strategy pattern -- `BaseCommand` abstract class with `run()`, `getCost()`, `getPreReqTurn()`, `getPostReqTurn()`. All commands registered in `CommandRegistry` via factory lambdas. `CommandExecutor` orchestrates constraint checking, cooldown, multi-turn stacking, and entity persistence.
- Korean names used for command classes and action codes (legacy parity with PHP)
- Purpose: Validates command preconditions (resources, permissions, war state, distance, etc.)
- Examples: `backend/game-app/src/main/kotlin/com/opensam/command/constraint/Constraint.kt`, `ConstraintChain.kt`, `ConstraintHelper.kt`
- Pattern: Chain of responsibility -- each `Constraint` returns `Pass` or `Fail(reason)`. Commands declare `fullConditionConstraints` and `minConditionConstraints` lists.
- Purpose: Process monthly turns with in-memory state for performance, then persist dirty entities
- Examples: `backend/game-app/src/main/kotlin/com/opensam/engine/turn/cqrs/TurnCoordinator.kt`, `InMemoryWorldPorts.kt`, `DirtyTracker.kt`
- Pattern: Load all world state into memory (`InMemoryWorldState`), process through `TurnPipeline` steps, track mutations via `DirtyTracker`, bulk-persist only changed entities via `WorldStatePersister`
- Lifecycle states: IDLE -> LOADING -> PROCESSING -> PERSISTING -> PUBLISHING -> IDLE (or FAILED)
- Purpose: Abstract data access for both real-time commands (JPA) and turn processing (in-memory)
- Examples: `backend/game-app/src/main/kotlin/com/opensam/engine/turn/cqrs/port/WorldReadPort.kt`, `WorldWritePort.kt`
- Pattern: Port/Adapter -- `WorldReadPort` and `WorldWritePort` interfaces with two implementations: `JpaWorldPorts` (for live command execution) and `InMemoryWorldPorts` (for turn processing)
- Purpose: Apply stat/action bonuses from nation type, personality, specials, items
- Examples: `backend/game-app/src/main/kotlin/com/opensam/engine/modifier/ModifierService.kt`, `ActionModifier`
- Pattern: Decorator/Pipeline -- `ModifierService.getModifiers()` collects applicable modifiers, injected into commands via `CommandExecutor`
- Purpose: Manage game-app JVM process lifecycle from gateway
- Examples: `backend/gateway-app/src/main/kotlin/com/opensam/gateway/orchestrator/GameProcessOrchestrator.kt`, `GameContainerOrchestrator.kt`
- Pattern: Strategy -- `GameOrchestrator` interface with `GameProcessOrchestrator` (dev: spawn local JVM) and `GameContainerOrchestrator` (prod: Docker containers). Selected via `@ConditionalOnProperty("gateway.docker.enabled")`
## Entry Points
- Location: `backend/gateway-app/src/main/kotlin/com/opensam/gateway/GatewayApplication.kt`
- Triggers: `./gradlew :gateway-app:bootRun` or Docker
- Responsibilities: Boot Spring context, run admin bootstrap, start proxy routing
- Port: 8080 (default)
- Location: `backend/game-app/src/main/kotlin/com/opensam/` (main class not shown but follows Spring Boot convention)
- Triggers: Spawned by gateway orchestrator on port 9001+, or `./gradlew :game-app:bootRun`
- Responsibilities: Boot Spring context, run Flyway migrations, start turn daemon scheduler
- Port: 9001 (default, configurable)
- Location: `frontend/src/app/layout.tsx` (root layout)
- Triggers: `pnpm dev` or Docker (standalone output)
- Responsibilities: Next.js App Router entry, theme provider, Toaster
- Location: `frontend/src/app/(game)/page.tsx` -> delegates to `frontend/src/components/game/game-dashboard.tsx`
- Triggers: Authenticated player navigates to `/`
- Responsibilities: Main game view after login and world selection
- Location: `backend/game-app/src/main/kotlin/com/opensam/engine/TurnDaemon.kt`
- Triggers: Spring `@Scheduled` every 5 seconds (`app.turn.interval-ms: 5000`)
- Responsibilities: Check all active worlds, trigger `TurnCoordinator.processWorld()` for overdue turns
## Error Handling
- Backend global: `backend/game-app/src/main/kotlin/com/opensam/config/GlobalExceptionHandler.kt` -- catches exceptions and returns structured `ErrorResponse`
- Command failures: `CommandResult(success = false, logs = [...])` -- commands never throw; they return failure results with Korean-language log messages
- Turn pipeline fault tolerance: Each `TurnStep` is wrapped in try-catch; failures are logged but pipeline continues to next step (legacy daemon.ts parity)
- Frontend API errors: Axios interceptor in `frontend/src/lib/api.ts` -- 401 triggers logout/redirect; `api-error.ts` extracts structured error messages
- Frontend auth errors: `frontend/src/lib/auth-error.ts` handles auth-specific error formatting
## Cross-Cutting Concerns
- Backend: SLF4J via `LoggerFactory.getLogger()` (standard Spring Boot)
- Key loggers: `TurnPipeline`, `TurnCoordinator`, `GameProcessOrchestrator`
- Command results carry structured log messages with color tags (e.g., `<R>...</>` for red)
- Backend: Spring Validation (`@Valid`, Jakarta annotations) for DTOs
- Command args: `ArgSchema` system -- each command declares its argument schema; `ArgSchema.parse()` validates and coerces types before execution
- Frontend: Form-level validation in page components
- JWT (HS256) with shared secret between gateway and game-app (via `shared` module `JwtTokenVerifier`)
- Gateway: `JwtAuthenticationFilter` -> `SecurityConfig` (Spring Security, stateless sessions)
- Game-app: Same `JwtAuthenticationFilter` pattern for direct access
- OAuth support: Kakao provider via `AccountOAuthController` / `AccountOAuthService`
- OTP support: Two-factor authentication flow
- Role-based: `USER`, `ADMIN` roles in JWT claims
- Game-level: `officerLevel` determines nation command access (0=wanderer, 1+=member, 2+=secret access, 20=chief)
- Admin: `AdminAuthorizationService` / `GatewayAdminAuthorizationService` check admin grade
- STOMP over SockJS WebSocket
- Game-app config: `backend/game-app/src/main/kotlin/com/opensam/config/WebSocketConfig.kt`
- Topics: `/topic/world/{id}/turn`, `/topic/world/{id}/battle`, `/topic/world/{id}/diplomacy`, `/topic/world/{id}/message`
- Frontend: `frontend/src/lib/websocket.ts` (STOMP client), `frontend/src/hooks/useWebSocket.ts` (React hook)
- Flyway with SQL migrations: `backend/game-app/src/main/resources/db/migration/V1__core_tables.sql` through `V27__add_general_position.sql`
- DDL validation mode (`ddl-auto: validate`) -- schema managed entirely by Flyway
- Redis used for NPC token management (`SelectNpcTokenService`)
- Zustand stores use `sessionStorage` persistence for world/general state across page navigations
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
