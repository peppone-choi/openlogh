# Technology Stack

**Analysis Date:** 2026-04-05

## Languages

**Primary:**
- Kotlin 2.1.0 - Backend services (gateway-app, game-app, shared)
- TypeScript 5.x - Frontend (Next.js application)

**Secondary:**
- SQL - Database migrations via Flyway (`backend/game-app/src/main/resources/db/migration/`)
- Kotlin DSL - Gradle build scripts (`backend/build.gradle.kts`, `backend/*/build.gradle.kts`)

## Runtime

**Environment:**
- JVM 17 (Eclipse Temurin) - Backend compilation and runtime
- Node.js 20+ (Alpine-based Docker image) - Frontend build and runtime
- Node.js 24 used in CI verify workflow (`.github/workflows/verify.yml`)

**Package Manager:**
- Gradle 8.x with Kotlin DSL - Backend dependency management (wrapper bundled via `backend/gradlew`)
- pnpm 10.x - Frontend dependency management
- Lockfile: `frontend/pnpm-lock.yaml` (present, used with `--frozen-lockfile`)

## Frameworks

**Core:**
- Spring Boot 3.4.2 - Backend web framework, REST APIs, DI container (`backend/build.gradle.kts` line 4)
- Spring Data JPA - ORM and entity management (game-app, gateway-app)
- Spring Security - Authentication and authorization (both apps)
- Spring WebSocket + STOMP - Real-time communication (`backend/game-app/build.gradle.kts` line 16)
- Spring WebFlux - Reactive HTTP client for gateway-to-game proxying (`backend/gateway-app/build.gradle.kts` line 29)
- Spring Data Redis - Cache and session management (both apps)
- Next.js 16.1.6 - React metaframework with SSR/SSG, standalone output mode (`frontend/next.config.ts`)
- React 19.2.3 - UI component framework (`frontend/package.json`)

**Testing:**
- JUnit 5 (Jupiter) - Backend unit testing (`backend/build.gradle.kts` line 41)
- Spring Boot Test + Spring Security Test - Backend integration testing
- H2 Database - In-memory DB for backend tests (`testRuntimeOnly`)
- Kotlin Coroutines Test - Async testing utilities for turn engine
- Vitest 3.2.4 - Frontend unit testing (`frontend/package.json`)
- Testing Library (React 16.3.2 + jest-dom 6.9.1) - Component testing
- Playwright 1.58.2 - E2E testing (`frontend/package.json`)

**Build/Dev:**
- Spring Boot Gradle Plugin 3.4.2 - Fat JAR building (`bootJar` task)
- Spring Dependency Management Plugin 1.1.7 - BOM-based version alignment
- Kotlin Spring Plugin 2.1.0 - Open classes for Spring proxying
- Kotlin JPA Plugin 2.1.0 - No-arg constructors for JPA entities
- ESLint 9 + eslint-config-next 16.1.6 - Frontend linting
- Tailwind CSS 4 + PostCSS - Utility CSS framework
- Docker + Docker Compose - Containerized deployment

## Key Dependencies

**Critical (Backend):**
- `spring-boot-starter-web` - REST API endpoints for gateway and game-app
- `spring-boot-starter-data-jpa` - Hibernate ORM with PostgreSQL dialect
- `spring-boot-starter-security` - JWT-based auth, role-based access control
- `spring-boot-starter-websocket` - STOMP over SockJS for real-time game events
- `spring-boot-starter-webflux` - `WebClient` for gateway proxying HTTP to game-app instances
- `spring-boot-starter-data-redis` - Redis client (repositories disabled, used for caching)
- `io.jsonwebtoken:jjwt-api:0.12.6` - JWT creation and validation (shared between gateway and game)
- `org.flywaydb:flyway-core` + `flyway-database-postgresql` - Database schema versioning (27+ migrations)
- `org.postgresql:postgresql` - JDBC driver for PostgreSQL 16
- `org.jetbrains.kotlinx:kotlinx-coroutines-core` - Async processing in turn engine
- `com.fasterxml.jackson.module:jackson-module-kotlin` - JSON serialization (all subprojects)

**Critical (Frontend):**
- `next` 16.1.6 - App router, standalone output for Docker
- `react` 19.2.3 / `react-dom` 19.2.3 - UI rendering
- `zustand` 5.0.11 - Lightweight state management (stores)
- `axios` 1.13.5 - HTTP client for REST API calls
- `@stomp/stompjs` 7.3.0 - STOMP protocol client for WebSocket
- `sockjs-client` 1.6.1 - WebSocket fallback transport
- `zod` 4.3.6 - Schema validation
- `react-hook-form` 7.71.1 + `@hookform/resolvers` 5.2.2 - Form handling

**UI Libraries:**
- `three` 0.183.2 + `@react-three/fiber` 9.5.0 + `@react-three/drei` 10.7.7 - 3D graphics (space combat visualization)
- `konva` 10.2.0 + `react-konva` 19.2.2 - 2D canvas drawing (maps, tactical view)
- `@tiptap/react` 3.20.0 + extensions (color, image, link, text-align, underline) - WYSIWYG editor (in-game messaging/boards)
- `radix-ui` 1.4.3 + individual Radix components (alert-dialog, avatar, collapsible, dialog, progress, scroll-area, separator, switch) - Headless accessible UI primitives
- `shadcn` 3.8.4 (devDependency) - Component generation CLI
- `lucide-react` 0.564.0 - Icon library
- `sonner` 2.0.7 - Toast notifications
- `react-resizable-panels` 4.7.3 - Resizable layout panels
- `class-variance-authority` 0.7.1 + `clsx` 2.1.1 + `tailwind-merge` 3.4.0 - CSS utility composition
- `next-themes` 0.4.6 - Dark/light mode
- `js-sha512` 0.9.0 - Client-side password hashing
- `tw-animate-css` 1.4.0 (devDependency) - Tailwind animation utilities

**Infrastructure:**
- `com.h2database:h2` (testRuntime) - In-memory DB for backend unit tests
- `jakarta.validation:jakarta.validation-api` - Bean validation annotations in shared DTOs

## Configuration

**Environment:**
- `.env.example` exists at project root - template for required env vars
- `.env` file used for local development (not committed)
- Environment variables override Spring properties via `${VAR:default}` syntax
- Key env vars: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`
- Frontend build-time vars: `NEXT_PUBLIC_API_URL`, `NEXT_PUBLIC_WS_URL`, `NEXT_PUBLIC_IMAGE_CDN_BASE`
- OAuth: `KAKAO_REST_API_KEY`, `OAUTH_ACCOUNT_LINK_CALLBACK_URI`
- Admin bootstrap: `ADMIN_BOOTSTRAP_ENABLED`, `ADMIN_LOGIN_ID`, `ADMIN_PASSWORD`

**Build:**
- `backend/build.gradle.kts` - Root Gradle config (plugins, JVM 17 target, common deps)
- `backend/shared/build.gradle.kts` - Plain Kotlin library (no Spring Boot app plugin)
- `backend/gateway-app/build.gradle.kts` - Gateway service with WebFlux for proxying
- `backend/game-app/build.gradle.kts` - Game service with WebSocket, Redis, Flyway, coroutines
- `frontend/package.json` - Scripts: `dev`, `build`, `start`, `lint`, `typecheck`, `test`, `e2e`
- `frontend/next.config.ts` - Standalone output, image CDN remote patterns (jsdelivr)
- `frontend/tsconfig.json` - ES2017 target, strict mode, `@/*` path alias to `./src/*`

**Spring Application Config:**
- `backend/gateway-app/src/main/resources/application.yml` - Port 8080, lazy init, Flyway disabled (gateway only reads)
- `backend/game-app/src/main/resources/application.yml` - Port 9001 (dynamic via `SERVER_PORT`), Flyway enabled, turn interval 5s
- `backend/game-app/src/main/resources/application-docker.yml` - Docker profile overrides for DB/Redis hosts

## Platform Requirements

**Development:**
- Java 17+ (JDK, not JRE - needed for compilation)
- Gradle 8.x (bundled via wrapper `./gradlew`)
- Node.js 20+
- pnpm 10.x+ (corepack-managed in Docker)
- Docker + Docker Compose (for PostgreSQL 16 + Redis 7)

**Production:**
- Docker + Docker Compose (primary deployment method)
- Eclipse Temurin 17 JDK Alpine (backend containers)
- Node.js 20 Alpine (frontend container)
- Nginx Alpine (reverse proxy)
- Docker socket access required for gateway container (spawns game-app containers)
- GHCR (GitHub Container Registry) - `ghcr.io/peppone-choi/openlogh-*` images

**CI:**
- GitHub Actions (ubuntu-latest runners)
- Amazon Corretto 17 JDK (CI Java distribution)
- pnpm 10.26.2 (pinned in verify workflow)
- Docker Buildx with GHA cache (layer caching per scope: gateway, game, frontend)
- EC2 deployment via SSH (`appleboy/ssh-action@v1`)

---

*Stack analysis: 2026-04-05*
