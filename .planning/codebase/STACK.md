# Technology Stack

**Analysis Date:** 2026-03-28

## Languages

**Primary:**

- Kotlin 2.1.0 - Backend services (gateway-app, game-app)
- TypeScript 5.x - Frontend (Next.js)

**Secondary:**

- SQL - Database migrations via Flyway
- JavaScript - Build scripts and tooling

## Runtime

**Backend:**

- Java 17 (JVM)

**Frontend:**

- Node.js 20 (Alpine-based Docker image)

**Package Manager:**

- Backend: Gradle 8.x (with Kotlin DSL)
- Frontend: pnpm (with lockfile: `pnpm-lock.yaml`)

## Frameworks

**Core Backend:**

- Spring Boot 3.4.2 - Web framework, REST APIs
- Spring Data JPA - ORM and entity management
- Spring Security - Authentication/authorization
- Spring WebSocket - Real-time communication (STOMP)
- Spring Data Redis - Caching and session management
- Spring WebFlux - Reactive HTTP client for inter-service communication

**Core Frontend:**

- Next.js 16.1.6 - React metaframework, SSR/SSG
- React 19.2.3 - UI component framework
- Tailwind CSS 4 - Utility-first CSS framework

**3D & Canvas Rendering:**

- Three.js 0.170.0 - 3D graphics library
- React Three Fiber 9.5.0 - React renderer for Three.js
- React Three Drei 10.7.7 - Three.js helpers and utilities
- Konva 10.2.0 - Canvas/2D drawing library
- React Konva 19.2.2 - React wrapper for Konva

**Rich Text Editing:**

- TipTap 3.20.0 - WYSIWYG editor framework
- TipTap extensions (color, image, link, text-align, underline)

**Testing:**

- JUnit 5 (Jupiter) - Backend unit testing
- Vitest 3.2.4 - Frontend unit testing
- Playwright 1.58.2 - E2E testing

**Build/Dev:**

- Flyway 1.0 - Database migration management
- Spring Boot Gradle Plugin 3.4.2 - JAR/WAR building

## Key Dependencies

**Backend - HTTP & Communication:**

- Spring Boot WebFlux Starter - Reactive HTTP client for game-app routing
- JJWT (JSON Web Token) 0.12.6 - JWT validation and parsing
- Jackson Module Kotlin - JSON serialization/deserialization

**Backend - Persistence:**

- PostgreSQL Driver 16 - JDBC driver for PostgreSQL
- Spring Data Redis - Redis client for caching
- Flyway Core + PostgreSQL Plugin - Database schema versioning

**Backend - Testing:**

- Spring Boot Test - Integration testing framework
- Spring Security Test - Security context mocking
- H2 Database - In-memory database for tests
- Kotlin Coroutines Test - Async testing utilities

**Frontend - State & Forms:**

- Zustand 5.0.11 - Lightweight state management
- React Hook Form 7.71.1 - Efficient form handling
- Zod 4.3.6 - TypeScript-first schema validation
- @hookform/resolvers 5.2.2 - Form resolver adapters

**Frontend - HTTP & WebSocket:**

- Axios 1.13.5 - HTTP client for REST API calls
- @stomp/stompjs 7.3.0 - STOMP protocol client for WebSocket
- SockJS Client 1.6.1 - WebSocket fallback library

**Frontend - UI & Components:**

- Radix UI 1.4.3 - Headless component library
- @radix-ui/react-accessible-icon 1.1.8 - Icon accessibility wrapper
- @radix-ui/react-switch 1.2.6 - Toggle switch component
- Lucide React 0.564.0 - Icon library
- Sonner 2.0.7 - Toast notification system
- React Resizable Panels 4.7.3 - Resizable layout panels
- Class Variance Authority 0.7.1 - CSS-in-JS variant library
- Tailwind Merge 3.4.0 - Tailwind CSS class merger

**Frontend - Utility:**

- js-sha512 0.9.0 - SHA-512 hashing
- Clsx 2.1.1 - Conditional className builder
- Next Themes 0.4.6 - Dark/light mode management

## Configuration

**Environment:**

- `.env` file - Local development environment variables
- `.env.example` - Template for required environment variables
- `docker-compose.yml` - Local development services (PostgreSQL, Redis)

**Backend:**

- `backend/build.gradle.kts` - Root Gradle configuration
- `backend/shared/build.gradle.kts` - Shared module (DTOs, JWT utilities)
- `backend/gateway-app/build.gradle.kts` - Gateway service configuration
- `backend/game-app/build.gradle.kts` - Game service configuration
- `backend/gateway-app/src/main/resources/application.yml` - Gateway Spring config
- `backend/game-app/src/main/resources/application.yml` - Game Spring config
- `backend/game-app/src/main/resources/application-docker.yml` - Docker environment config
- `backend/game-app/src/main/resources/db/migration/` - Flyway SQL migrations

**Frontend:**

- `frontend/package.json` - Node.js dependencies and scripts
- `frontend/pnpm-lock.yaml` - Locked dependency versions
- `frontend/next.config.ts` - Next.js configuration
- `frontend/tsconfig.json` - TypeScript compiler options
- `frontend/tailwind.config.ts` - Tailwind CSS configuration
- `frontend/Dockerfile` - Multi-stage Docker build (deps, builder, runner)

## Platform Requirements

**Development:**

- Java 17+ (for backend compilation)
- Gradle 8.x (bundled via wrapper)
- Node.js 20+ (for frontend)
- pnpm 9.x+ (package manager)
- Docker + Docker Compose (for PostgreSQL 16, Redis 7)
- PostgreSQL 16 client tools (optional, for direct DB access)

**Production:**

- Docker + Docker Compose (for containerized deployment)
- Kubernetes (optional, for orchestration)
- GHCR (GitHub Container Registry) - Pre-built images stored here
- Nginx (reverse proxy for routing frontend/backend)

## Key Configuration Values

**Database:**

- PostgreSQL 16
- Connection pool managed by Hikari (Spring default)
- Flyway migrations auto-run on startup

**Caching:**

- Redis 7
- Spring Data Redis configured (optional repositories disabled)

**Server Ports:**

- Gateway API: `8080`
- Game API: `9001` (dynamic when spawned by gateway)
- Frontend: `3000` (Next.js dev) or served via Nginx
- Nginx: `80` (HTTP)

---

_Stack analysis: 2026-03-28_
