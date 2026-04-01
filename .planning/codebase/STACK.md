# Technology Stack

**Analysis Date:** 2026-03-31

## Languages

**Primary:**
- Kotlin 2.1.0 - Backend services (gateway-app, game-app, shared module)
- TypeScript 5.x - Frontend (Next.js), build scripts, config files

**Secondary:**
- SQL - Flyway database migrations (`backend/game-app/src/main/resources/db/migration/`)
- JavaScript - Verification scripts (`scripts/verify/`, `frontend/scripts/`)

## Runtime

**Backend:**
- Java 17 (JVM) - Eclipse Temurin distribution (Docker: `eclipse-temurin:17-jdk-alpine`)
- JVM args: `-Xmx1g -Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8` (`backend/gradle.properties`)

**Frontend:**
- Node.js 20 (Docker: `node:20-alpine`)
- Output mode: `standalone` (Next.js standalone build for production)

**Package Managers:**
- Backend: Gradle 8.x with Kotlin DSL, wrapper bundled (`backend/gradlew`)
  - Parallel builds disabled (`org.gradle.parallel=false` in `backend/gradle.properties`)
  - Daemon enabled, in-process Kotlin compilation
- Frontend: pnpm (CI uses version 10 per `.github/workflows/verify.yml`)
  - Lockfile: `frontend/pnpm-lock.yaml` (frozen-lockfile in CI/Docker)

## Frameworks

**Core Backend:**
- Spring Boot 3.4.2 (`backend/build.gradle.kts`) - Web framework, REST APIs
- Spring Dependency Management 1.1.7 - BOM version management
- Spring Data JPA - ORM via Hibernate with PostgreSQL dialect
- Spring Security - Authentication/authorization (both gateway-app and game-app)
- Spring Validation - Jakarta Bean Validation (`spring-boot-starter-validation`)
- Spring WebSocket + STOMP - Real-time communication (`game-app` only)
- Spring Data Redis - Cache/session management (both apps, repositories disabled)
- Spring WebFlux - Reactive HTTP client for gateway-to-game proxying (`gateway-app` only)
- Spring Scheduling - Turn engine tick loop (`game-app` only)
- Kotlin Coroutines Core - Async turn engine processing (`game-app` only)

**Core Frontend:**
- Next.js 16.1.6 (`frontend/package.json`) - React metaframework, SSR/SSG
  - **Note:** CLAUDE.md says "Next.js 15" but actual version is 16.1.6
- React 19.2.3 - UI component framework
- Tailwind CSS 4 (`@tailwindcss/postcss` v4) - Utility-first CSS

**3D & Canvas Rendering:**
- Three.js ^0.170.0 - 3D graphics (starmap, space battle visualization)
- React Three Fiber ^8.17.0 - React renderer for Three.js
  - **Note:** `package.json` says `^8.17.0` but CLAUDE.md says `9.5.0` - check actual resolved version
- React Three Drei ^9.121.0 - Three.js helpers/utilities
  - **Note:** `package.json` says `^9.121.0` but CLAUDE.md says `10.7.7` - check actual resolved version
- Konva ^10.2.0 - 2D canvas drawing (2D map rendering)
- React Konva ^19.2.2 - React wrapper for Konva

**Rich Text Editing:**
- TipTap ^3.20.0 - WYSIWYG editor framework (in-game board/messages)
  - Extensions: color, image, link, text-align, text-style, underline
  - pnpm overrides pin `@tiptap/core` and `@tiptap/pm` to 3.20.0

**UI Component Library:**
- Radix UI ^1.4.3 - Headless component primitives
  - `@radix-ui/react-accessible-icon` ^1.1.8
  - `@radix-ui/react-switch` ^1.2.6
- shadcn ^3.8.4 (devDependency) - Component generator using Radix primitives
- Lucide React ^0.564.0 - Icon library
- Sonner ^2.0.7 - Toast notifications
- React Resizable Panels ^4.7.3 - Resizable layout panels
- Class Variance Authority ^0.7.1 - Component variant management
- Tailwind Merge ^3.4.0 - Tailwind class deduplication
- Clsx ^2.1.1 - Conditional className builder
- tw-animate-css ^1.4.0 (devDependency) - Tailwind animation utilities

**State & Forms:**
- Zustand ^5.0.11 - Lightweight state management (stores in `frontend/src/stores/`)
- React Hook Form ^7.71.1 - Form handling
- Zod ^4.3.6 - Schema validation
- @hookform/resolvers ^5.2.2 - Zod-to-RHF bridge

**HTTP & Real-time:**
- Axios ^1.13.5 - REST API client (`frontend/src/lib/api.ts`)
- @stomp/stompjs ^7.3.0 - STOMP protocol WebSocket client
- SockJS Client ^1.6.1 - WebSocket fallback (SockJS endpoint at `/ws`)

**Testing:**
- JUnit 5 (Jupiter) - Backend unit testing (all subprojects)
- Spring Boot Test - Backend integration testing (gateway-app, game-app)
- Spring Security Test - Security context mocking
- H2 Database - In-memory PostgreSQL-mode database for tests
- Kotlin Coroutines Test - Async testing utilities (game-app)
- Vitest ^3.2.4 - Frontend unit testing
- Playwright ^1.58.2 - E2E testing (chromium-only setup)

**Build/Dev:**
- Spring Boot Gradle Plugin 3.4.2 - Fat JAR building (`bootJar` task)
- Flyway Core + Flyway PostgreSQL Plugin - Database schema versioning
- Kotlin JPA Plugin - Entity class `allOpen` annotation processing
- Kotlin Spring Plugin - Spring class `open` annotation processing
- ESLint 9 + eslint-config-next 16.1.6 - Frontend linting

## Key Dependencies

**Backend - Security & Auth:**
- JJWT 0.12.6 (`io.jsonwebtoken:jjwt-api/impl/jackson`) - JWT creation and validation
  - Used in: shared module (shared between gateway and game-app)
  - Token format: HS256 with configurable secret (min 256 bits)

**Backend - Serialization:**
- Jackson Module Kotlin (`com.fasterxml.jackson.module:jackson-module-kotlin`) - JSON (de)serialization
- Jackson Annotations - DTO annotation support (shared module)

**Backend - Database:**
- PostgreSQL JDBC Driver - Runtime dependency for both apps
- Hikari Connection Pool - Spring Boot default (no explicit config)

**Frontend - Utility:**
- js-sha512 ^0.9.0 - Client-side SHA-512 hashing (password hashing)
- Next Themes ^0.4.6 - Dark/light mode management

## Gradle Multi-Module Structure

```
backend/
  build.gradle.kts          # Root: plugin versions, common config
  settings.gradle.kts       # Modules: shared, gateway-app, game-app
  gradle.properties          # JVM args, daemon config
  shared/build.gradle.kts   # Plain Kotlin lib (no Spring Boot plugin)
  gateway-app/build.gradle.kts  # Spring Boot app
  game-app/build.gradle.kts     # Spring Boot app
```

**Module dependency graph:**
- `gateway-app` depends on `shared`
- `game-app` depends on `shared`
- `shared` is a plain Kotlin library (no `spring-boot` plugin, uses BOM for version management)

**Excluded tests in game-app** (pre-existing broken tests):
- `com/openlogh/command/ArgSchemaValidationTest.kt`
- `com/openlogh/command/GeneralMilitaryCommandTest.kt`
- `com/openlogh/command/GeneralPoliticalCommandTest.kt`
- `com/openlogh/command/NationCommandTest.kt`
- `com/openlogh/command/NationDiplomacyStrategicCommandTest.kt`
- `com/openlogh/command/NationResearchSpecialCommandTest.kt`

## Configuration Files

**Backend Spring Profiles:**
- `application.yml` - Default (local dev): localhost DB/Redis, Flyway enabled (game-app) / disabled (gateway-app)
- `application-docker.yml` - Docker: env-var-based DB/Redis hosts, gateway docker orchestration enabled
- `application-test.yml` - Test: H2 in-memory DB (PostgreSQL mode), Flyway disabled

**Frontend Build-time Environment:**
- `NEXT_PUBLIC_API_URL` - Backend API base URL (default: `/api` relative, dev: `http://localhost:8080/api`)
- `NEXT_PUBLIC_WS_URL` - WebSocket base URL (default: auto-derived from `window.location`)
- `NEXT_PUBLIC_IMAGE_CDN_BASE` - Image CDN base URL

**TypeScript Config (`frontend/tsconfig.json`):**
- Target: ES2017
- Module: ESNext with bundler resolution
- Strict mode enabled
- Path alias: `@/*` maps to `./src/*`
- Types: `@react-three/fiber` (global Three.js types)

## Server Ports

| Service      | Port  | Notes                                |
|-------------|-------|--------------------------------------|
| Gateway API | 8080  | Fixed                                |
| Game API    | 9001  | Default; dynamic range 9001-9999     |
| Frontend    | 3000  | Next.js dev server / standalone      |
| Nginx       | 80    | Reverse proxy (configurable via `HTTP_PORT`) |
| PostgreSQL  | 5432  | Configurable via `DB_PORT`           |
| Redis       | 6379  | Configurable via `REDIS_PORT`        |

## Docker Build Pipeline

**Backend Dockerfile** (`backend/Dockerfile`):
- Build stage: `eclipse-temurin:17-jdk-alpine`
- Runtime stage: `eclipse-temurin:17-jdk-alpine` (JDK, not JRE, for Java tooling)
- Parameterized: `ARG MODULE=gateway-app` (builds either gateway-app or game-app)
- Gateway image includes `docker-cli` (for container orchestration via Docker socket)

**Frontend Dockerfile** (`frontend/Dockerfile`):
- Three-stage: deps (pnpm install) -> builder (next build) -> runner (standalone)
- Runtime: `node:20-alpine` with non-root user (`nextjs:nodejs`, UID 1001)
- Build args: `NEXT_PUBLIC_API_URL`, `NEXT_PUBLIC_WS_URL`, `NEXT_PUBLIC_IMAGE_CDN_BASE`

## CI/CD Workflows

**Verify** (`.github/workflows/verify.yml`):
- Triggers: push to main, pull requests
- Backend: Java 17 Temurin, Gradle compile + test with `SPRING_PROFILES_ACTIVE=test`
- Frontend: Node 20, pnpm 10, lint + typecheck + vitest (run mode)

**Docker Build** (`.github/workflows/docker-build.yml`):
- Triggers: push to main (paths: backend/**, frontend/**, nginx/**, docker-compose.yml)
- Builds: gateway, game, frontend images in parallel
- Registry: GHCR (`ghcr.io/{owner}/openlogh-{gateway,game,frontend}`)
- Tags: git SHA + `latest`
- Cache: GitHub Actions cache (`type=gha`)

## Version Discrepancies

| Item | CLAUDE.md States | Actual (package.json/build.gradle.kts) |
|------|-----------------|----------------------------------------|
| Next.js | 15 (header), 16.1.6 (stack section) | 16.1.6 |
| React Three Fiber | 9.5.0 | ^8.17.0 (check pnpm-lock.yaml for resolved) |
| React Three Drei | 10.7.7 | ^9.121.0 (check pnpm-lock.yaml for resolved) |
| pnpm version | 9.x+ | 10 (in CI workflow) |

---

*Stack analysis: 2026-03-31*
