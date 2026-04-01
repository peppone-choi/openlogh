# Technology Stack

**Analysis Date:** 2026-03-31

## Languages

**Primary:**
- Kotlin 2.1.0 - Backend (Spring Boot, all three modules: `shared`, `gateway-app`, `game-app`)
- TypeScript 5.x - Frontend (Next.js, React)

**Secondary:**
- SQL - Flyway migrations (`backend/game-app/src/main/resources/db/migration/`)
- Bash - Verification scripts (`scripts/verify/run.sh`, `verify`)

## Runtime

**Backend:**
- JVM 17 (target: `JvmTarget.JVM_17`)
- Docker base image: `eclipse-temurin:17-jdk-alpine`
- JVM args: `-Xmx2g -XX:+HeapDumpOnOutOfMemoryError`

**Frontend:**
- Node.js 20 (Docker) / Node.js 24 (CI)
- Docker base image: `node:20-alpine`
- Next.js standalone output mode

**Package Managers:**
- Gradle 8.12 (wrapper, `backend/gradle/wrapper/gradle-wrapper.properties`)
- pnpm 10.26.2 (`frontend/pnpm-lock.yaml` present)
- Lockfiles: `frontend/pnpm-lock.yaml` (present), `frontend/package-lock.json` (also present - legacy)

## Frameworks

**Core:**
- Spring Boot 3.4.2 - Backend framework (`backend/build.gradle.kts`)
- Next.js 16.1.6 - Frontend framework (`frontend/package.json`)
- React 19.2.3 - UI library (`frontend/package.json`)

**Testing:**
- JUnit Jupiter - Backend unit/integration tests (`backend/build.gradle.kts`)
- Spring Boot Test + Spring Security Test - Backend test support
- H2 Database - Backend test DB (`application-test.yml`, mode=PostgreSQL)
- Vitest 3.2.4 - Frontend unit tests (`frontend/vitest.config.ts`)
- Playwright 1.58.2 - Frontend E2E tests (`frontend/playwright.config.ts`)

**Build/Dev:**
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

**Backend Configuration Files:**
- `backend/gateway-app/src/main/resources/application.yml` - Gateway defaults (port 8080)
- `backend/gateway-app/src/main/resources/application-docker.yml` - Docker profile overrides
- `backend/game-app/src/main/resources/application.yml` - Game defaults (port 9001)
- `backend/game-app/src/main/resources/application-docker.yml` - Docker profile overrides
- `backend/game-app/src/main/resources/application-test.yml` - Test profile (H2 in-memory)
- `backend/gradle.properties` - JVM/daemon settings

**Frontend Configuration Files:**
- `frontend/next.config.ts` - Next.js config (standalone output, image CDN patterns)
- `frontend/tsconfig.json` - TypeScript config (ES2017 target, `@/*` path alias)
- `frontend/vitest.config.ts` - Unit test config (node environment, `@/` alias)
- `frontend/playwright.config.ts` - E2E test config (chromium only)
- `frontend/eslint.config.mjs` - ESLint flat config (next core-web-vitals + typescript)
- `frontend/postcss.config.mjs` - PostCSS with Tailwind CSS v4 plugin
- `frontend/components.json` - shadcn/ui config (new-york style, RSC enabled)

**Environment Variables (from `.env.example`):**
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

**Build Configuration:**
- `backend/build.gradle.kts` - Root build config with plugin versions
- `backend/settings.gradle.kts` - Multi-module: `shared`, `gateway-app`, `game-app`
- JPA `allOpen` annotations for Entity/MappedSuperclass/Embeddable in both app modules

## Platform Requirements

**Development:**
- Java 17 (JDK)
- Node.js 20+ with pnpm
- Docker + Docker Compose (for PostgreSQL 16 + Redis 7)
- Ports: 5432 (PostgreSQL), 6379 (Redis), 8080 (gateway), 9001 (game), 3000 (frontend)

**Production:**
- Docker containers on AWS EC2
- nginx reverse proxy (port 80)
- Docker socket mount for gateway container orchestration
- GHCR (GitHub Container Registry) for image storage

## Multi-Module Backend Structure

```
backend/
├── shared/            # Plain Kotlin library (DTOs, JWT, Jackson)
├── gateway-app/       # Spring Boot app (auth, user mgmt, proxy to game JVMs)
└── game-app/          # Spring Boot app (game logic, turn engine, WebSocket)
```

- `shared` has no Spring Boot plugin - produces a plain JAR
- Both `gateway-app` and `game-app` depend on `shared`
- `gateway-app` runs on port 8080, proxies game requests to game JVMs via WebFlux
- `game-app` runs on port 9001, handles game logic and WebSocket connections
- Flyway migrations run in `game-app` only (disabled in `gateway-app`)

---

*Stack analysis: 2026-03-31*
