# External Integrations

**Analysis Date:** 2026-04-05

## APIs & External Services

**Image CDN:**
- jsDelivr CDN - Game asset images (officer portraits, maps, etc.)
  - URL: `https://cdn.jsdelivr.net/gh/peppone-choi/openlogh-image@master/`
  - Config: `NEXT_PUBLIC_IMAGE_CDN_BASE` env var
  - Used in: `frontend/next.config.ts` (remote image patterns)

**OAuth Provider:**
- Kakao OAuth 2.0 - Account linking (not primary login)
  - Authorization: `https://kauth.kakao.com/oauth/authorize`
  - Token exchange: `https://kauth.kakao.com/oauth/token`
  - User info: `https://kapi.kakao.com/v2/user/me`
  - SDK/Client: Java `HttpClient` (no SDK, raw HTTP)
  - Auth env vars: `KAKAO_REST_API_KEY`, `OAUTH_ACCOUNT_LINK_CALLBACK_URI`
  - Feature flag: `auth.oauth.kakao-enabled` (default `false` in gateway config)
  - Implementation: `backend/gateway-app/src/main/kotlin/com/openlogh/gateway/service/AccountOAuthService.kt`
  - Controller: `backend/gateway-app/src/main/kotlin/com/openlogh/gateway/controller/AccountOAuthController.kt`

## Data Storage

**Databases:**
- PostgreSQL 16 (Alpine)
  - Docker image: `postgres:16-alpine`
  - Connection: `jdbc:postgresql://localhost:5432/openlogh` (dev), `jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}` (docker)
  - Env vars: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`
  - Client: Spring Data JPA + Hibernate with PostgreSQL dialect
  - DDL: `validate` mode (schema managed exclusively by Flyway)
  - Connection pool: HikariCP (Spring Boot default)
  - Migrations: Flyway, 27 migration files at `backend/game-app/src/main/resources/db/migration/` (V1 through V27)
  - Flyway enabled in game-app only; gateway-app has `flyway.enabled: false`
  - Open-in-view: disabled (`spring.jpa.open-in-view: false`)

**Caching:**
- Redis 7 (Alpine)
  - Docker image: `redis:7-alpine`
  - Connection: `localhost:6379` (dev), `${REDIS_HOST}:${REDIS_PORT}` (docker)
  - Client: Spring Data Redis
  - Repositories: explicitly disabled (`spring.data.redis.repositories.enabled: false`)
  - Used for: session/cache (not as primary data store)
  - Both gateway-app and game-app connect to the same Redis instance

**File Storage:**
- Local filesystem only (no cloud storage)
- Game process logs: `logs/game-{commitSha}.log` (written by `GameProcessOrchestrator`)
- JAR artifacts: `artifacts/game-app-{commitSha}.jar` (resolved by orchestrator)

## Authentication & Identity

**Primary Auth:**
- Custom JWT-based authentication
  - Library: JJWT 0.12.6 (`io.jsonwebtoken:jjwt-api`)
  - Secret: `app.jwt.secret` (HS256, min 256 bits)
  - Expiration: 86400000ms (24 hours)
  - Shared between gateway-app and game-app via `:shared` module
  - Token transport: `Authorization: Bearer` header
  - Frontend storage: SessionStorage (via Zustand stores)
  - Implementation: `backend/gateway-app/src/main/kotlin/com/openlogh/gateway/service/AuthService.kt`
  - Game-app validation: `backend/game-app/src/main/kotlin/com/openlogh/service/AuthService.kt`
  - Shared JWT utilities: `backend/shared/src/main/kotlin/com/openlogh/`

**Admin Bootstrap:**
- Auto-creates admin user on gateway startup (optional)
  - Controlled by: `ADMIN_BOOTSTRAP_ENABLED` (default `false` in dev, `true` in docker-compose)
  - Config: `ADMIN_LOGIN_ID`, `ADMIN_PASSWORD`, `ADMIN_DISPLAY_NAME`, `ADMIN_GRADE`
  - Config location: `backend/gateway-app/src/main/resources/application.yml` lines 35-40

**OAuth (Secondary):**
- Kakao OAuth for account linking (see APIs section above)
- Not used as primary login mechanism
- Can be toggled via `auth.oauth.kakao-enabled`

## WebSocket / Real-time Communication

**Protocol:** STOMP over SockJS
- Config: `backend/game-app/src/main/kotlin/com/openlogh/config/WebSocketConfig.kt`
- Endpoint: `/ws` (SockJS-enabled, all origins allowed)
- Application prefix: `/app` (client-to-server messages)
- Broker prefixes: `/topic` (broadcast), `/queue` (point-to-point)
- Client library: `@stomp/stompjs` 7.3.0 + `sockjs-client` 1.6.1

**Channels (from CLAUDE.md architecture):**
- Command channel: `/app/command/{sessionId}/execute` - Player action dispatch
- Event channel: `/topic/world/{sessionId}/events` - Broadcast game events to all clients
- Battle channel: `/topic/world/{sessionId}/battle` - Real-time tactical combat updates

**Event Service:**
- `backend/game-app/src/main/kotlin/com/openlogh/service/GameEventService.kt` - Broadcasts events via WebSocket

## Inter-Service Communication

**Gateway to Game-App (HTTP Proxy):**
- Gateway uses Spring WebFlux `WebClient` to proxy requests to game-app instances
- Route registry: `WorldRouteRegistry` maps `worldId` to `http://127.0.0.1:{port}` base URLs
- Orchestrator: `backend/gateway-app/src/main/kotlin/com/openlogh/gateway/orchestrator/GameProcessOrchestrator.kt`
- Controller: `backend/gateway-app/src/main/kotlin/com/openlogh/gateway/controller/ProcessOrchestratorController.kt`

**Process Orchestration (Local JVM Mode):**
- Gateway spawns game-app as separate JVM processes via `ProcessBuilder`
- JAR resolution: `artifacts/game-app-{commitSha}.jar`
- Port allocation: dynamic range 9001-9999
- Health check: polls `GET /internal/health` (2s timeout, configurable total timeout via `gateway.orchestrator.health-timeout-ms`, default 120s/180s)
- Process lifecycle: graceful shutdown (5s SIGTERM, then SIGKILL)
- World activation: `WorldActivationBootstrap` restores active worlds on gateway startup
- Config: `gateway.orchestrator.restore-active-worlds`, `restore-max-retries`, `restore-retry-delay-ms`

**Docker Mode (Production):**
- Conditional on `gateway.docker.enabled` property
- Gateway container mounts Docker socket (`/var/run/docker.sock`)
- Game images pulled from GHCR: `ghcr.io/peppone-choi/openlogh-game:{tag}`
- Docker network: `openlogh-net` (bridge)

**Shared Module:**
- `backend/shared/` - Cross-process library (DTOs, JWT utilities, validation annotations, error models)
- Compiled as plain Kotlin library (no Spring Boot plugin, no fat JAR)
- Depended on by both `:gateway-app` and `:game-app` via `implementation(project(":shared"))`

## Monitoring & Observability

**Error Tracking:**
- None (no Sentry, Datadog, or similar)

**Logs:**
- Backend: SLF4J via `LoggerFactory.getLogger()` (Spring Boot default Logback)
- Game process logs redirected to files: `logs/game-{commitSha}.log`
- Frontend: `console.error()` / `console.log()` (no centralized logging)

**Health Checks:**
- Game-app: `/internal/health` endpoint (used by orchestrator)
- Docker Compose: `pg_isready` for PostgreSQL, `redis-cli ping` for Redis
- Nginx blocks `/internal/` from external access (`return 403`)

## CI/CD & Deployment

**CI Pipeline:**
- GitHub Actions
- Verify workflow (`.github/workflows/verify.yml`): runs on PRs and pushes to main
  - Spins up PostgreSQL 16 + Redis 7 as service containers
  - Runs `./verify ci` script (shared verify pipeline)
- Docker build workflow (`.github/workflows/docker-build.yml`): on push to main (backend/frontend paths)
  - Builds 3 images in parallel: gateway, game, frontend
  - Pushes to GHCR with `sha` and `latest` tags
  - Uses Docker Buildx with GHA cache (per-scope: gateway, game, frontend)

**Container Registry:**
- GHCR (GitHub Container Registry)
- Images: `ghcr.io/peppone-choi/openlogh-gateway`, `ghcr.io/peppone-choi/openlogh-game`, `ghcr.io/peppone-choi/openlogh-frontend`
- Tags: git SHA (short) + `latest`

**Deployment:**
- Target: AWS EC2 instance
- Method: SSH deploy via `appleboy/ssh-action@v1`
- Deploy script: `cd ~/openlogh-deploy && docker compose pull && docker compose up -d`
- Triggered automatically after successful Docker builds

**Docker Compose Architecture (`docker-compose.yml`):**
1. `postgres` (postgres:16-alpine) - Primary database, port 5432
2. `redis` (redis:7-alpine) - Cache, port 6379
3. `bootstrap` (game image) - Runs Flyway migrations then exits (`--spring.main.web-application-type=none`)
4. `gateway` (gateway image) - API gateway, port 8080, mounts Docker socket
5. `frontend` (frontend image) - Next.js standalone, port 3000
6. `nginx` (nginx:alpine) - Reverse proxy, port 80
   - `/` -> frontend:3000
   - `/api/` -> gateway:8080
   - `/ws` -> game-latest:9001 (dynamic DNS resolution)
   - `/internal/` -> 403 (blocked)

**Dockerfiles:**
- Backend: `backend/Dockerfile` - Multi-stage (Temurin 17 JDK Alpine), parameterized `MODULE` arg for gateway-app or game-app, installs docker-cli for gateway
- Frontend: `frontend/Dockerfile` - Multi-stage (Node 20 Alpine), pnpm build, standalone output, runs as non-root `nextjs` user

## Environment Configuration

**Required env vars (production):**
- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` - PostgreSQL connection
- `REDIS_HOST`, `REDIS_PORT` - Redis connection
- `ADMIN_BOOTSTRAP_ENABLED`, `ADMIN_LOGIN_ID`, `ADMIN_PASSWORD` - Initial admin user
- `NEXT_PUBLIC_API_URL` - Frontend API base path (default `/api`)
- `NEXT_PUBLIC_WS_URL` - Frontend WebSocket URL (empty = relative)
- `NEXT_PUBLIC_IMAGE_CDN_BASE` - Image CDN base URL

**Optional env vars:**
- `KAKAO_REST_API_KEY` - Kakao OAuth (blank = disabled)
- `OAUTH_ACCOUNT_LINK_CALLBACK_URI` - OAuth callback URL
- `GATEWAY_RESTORE_ACTIVE_WORLDS` - Auto-restore worlds on startup (default `true`)
- `GATEWAY_HEALTH_TIMEOUT_MS` - Game process health check timeout (default 180s)
- `SERVER_PORT` - Game-app port override (default 9001)
- `GAME_COMMIT_SHA`, `GAME_VERSION` - Game instance metadata
- `TAG` - Docker image tag for compose (default `latest`)

**Secrets location:**
- `.env` file at project root (local dev, not committed)
- `.env.example` at project root (template)
- GitHub Actions secrets: `EC2_HOST`, `EC2_SSH_KEY`, `GITHUB_TOKEN`
- GitHub Actions vars: `NEXT_PUBLIC_IMAGE_CDN_BASE`

## Webhooks & Callbacks

**Incoming:**
- Kakao OAuth callback: `/auth/kakao/callback` (frontend route, exchanges code for token via gateway API)

**Outgoing:**
- None

---

*Integration audit: 2026-04-05*
