# External Integrations

**Analysis Date:** 2026-03-31

## APIs & External Services

**Kakao OAuth:**
- Purpose: Social login / account linking
- Implementation: `backend/gateway-app/src/main/kotlin/com/opensam/gateway/service/AccountOAuthService.kt`
- Controller: `backend/gateway-app/src/main/kotlin/com/opensam/gateway/controller/AccountOAuthController.kt`
- Frontend callback: `frontend/src/app/(auth)/auth/kakao/callback/page.tsx`
- Feature flag: `auth.oauth.kakao-enabled` (default: `false` in `application.yml`)
- Frontend flag: `NEXT_PUBLIC_KAKAO_ENABLED` env var (`frontend/src/lib/auth-features.ts`)
- Auth flow: OAuth2 authorization code grant
  - Token endpoint: `https://kauth.kakao.com/oauth/authorize`
  - Token exchange: `https://kauth.kakao.com/oauth/token`
  - User info: `https://kapi.kakao.com/v2/user/me`
- Env vars required: `KAKAO_REST_API_KEY`, `OAUTH_ACCOUNT_LINK_CALLBACK_URI`

**Image CDN (jsDelivr):**
- Purpose: Serve game images (officer portraits, map assets)
- Default URL: `https://cdn.jsdelivr.net/gh/peppone-choi/opensamguk-image@master/`
- Config: `NEXT_PUBLIC_IMAGE_CDN_BASE` env var
- Usage: `frontend/src/lib/image.ts` (exports `CDN_BASE`)
- Next.js remote pattern: `frontend/next.config.ts` (configured for image optimization)
- Source repo: `https://github.com/peppone-choi/opensamguk-image`

## Data Storage

**PostgreSQL 16:**
- Purpose: Primary relational database for all game/user data
- Docker image: `postgres:16-alpine`
- Connection (dev): `jdbc:postgresql://localhost:5432/opensam`
- Connection (docker): `jdbc:postgresql://postgres:5432/opensam`
- Connection (test): `jdbc:h2:mem:opensam;MODE=PostgreSQL` (H2 in-memory)
- Driver: `org.postgresql:postgresql`
- ORM: Hibernate via Spring Data JPA
- DDL strategy: `validate` (Flyway handles schema)
- Migrations: Flyway - 27 migrations in `backend/game-app/src/main/resources/db/migration/`
  - Range: `V1__core_tables.sql` through `V27__add_general_position.sql`
  - Flyway enabled only in `game-app`, disabled in `gateway-app`
- Volume: `postgres-data` (Docker named volume)
- Env vars: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`

**Redis 7:**
- Purpose: NPC token management, session/cache data
- Docker image: `redis:7-alpine`
- Connection: `localhost:6379` (dev), `redis:6379` (docker)
- Client: Spring Data Redis (`spring-boot-starter-data-redis`)
- Repository mode: disabled (`spring.data.redis.repositories.enabled: false`)
- Key usage: NPC token service (`backend/game-app/src/main/kotlin/com/opensam/service/SelectNpcTokenService.kt`)
- Volume: `redis-data` (Docker named volume)
- Env vars: `REDIS_HOST`, `REDIS_PORT`

**File Storage:**
- No external file storage service detected
- Game data stored as JSON resources in classpath

## Authentication & Identity

**JWT (Custom Implementation):**
- Library: JJWT 0.12.6 (`io.jsonwebtoken:jjwt-api`)
- Secret: Configured via `app.jwt.secret` (256-bit HS256 key)
- Expiration: 86,400,000 ms (24 hours)
- Token storage: `localStorage` on frontend (`frontend/src/lib/api.ts`)
- Flow: Login -> JWT issued by gateway -> token sent as `Authorization: Bearer` header
- Shared between: `gateway-app` (issues tokens) and `game-app` (validates tokens)
- Shared JWT code lives in: `backend/shared/` module

**Spring Security:**
- Gateway: Full auth (login, register, JWT filter)
- Game: JWT validation for incoming requests from gateway
- Auth endpoints: `backend/gateway-app/src/main/kotlin/com/opensam/gateway/controller/AuthController.kt`
- OAuth: Kakao link/unlink (optional, feature-flagged)

**Admin Bootstrap:**
- On first startup, gateway can bootstrap an admin user
- Config: `ADMIN_BOOTSTRAP_ENABLED`, `ADMIN_LOGIN_ID`, `ADMIN_PASSWORD`, `ADMIN_DISPLAY_NAME`, `ADMIN_GRADE`

## Real-Time Communication

**WebSocket (STOMP over SockJS):**
- Backend config: `backend/game-app/src/main/kotlin/com/opensam/config/WebSocketConfig.kt`
- Endpoint: `/ws` (SockJS, all origins allowed)
- Message broker: Simple in-memory broker (prefixes: `/topic`, `/queue`)
- Application destination prefix: `/app`
- Event service: `backend/game-app/src/main/kotlin/com/opensam/service/GameEventService.kt`
- Frontend client: `frontend/src/lib/websocket.ts`
- Frontend hook: `frontend/src/hooks/useWebSocket.ts`
- Topics subscribed:
  - `/topic/world/{worldId}/turn` - Turn advance events
  - `/topic/world/{worldId}/battle` - Battle events
  - `/topic/world/{worldId}/diplomacy` - Diplomacy events
  - `/topic/world/{worldId}/message` - General messages
- Transport: SockJS with reconnect delay of 5000ms
- Nginx routing: `/ws` proxied directly to `game-latest:9001` (bypasses gateway)

## Inter-Service Communication

**Gateway -> Game JVM Proxy:**
- Gateway proxies game API requests to game-app JVMs via Spring WebFlux HTTP client
- Gateway dependency: `spring-boot-starter-webflux`

**Gateway Docker Orchestration:**
- Gateway manages game-app containers via Docker socket
- Files:
  - `backend/gateway-app/src/main/kotlin/com/opensam/gateway/orchestrator/GameContainerOrchestrator.kt`
  - `backend/gateway-app/src/main/kotlin/com/opensam/gateway/orchestrator/GameProcessOrchestrator.kt`
  - `backend/gateway-app/src/main/kotlin/com/opensam/gateway/dto/OrchestratorDtos.kt`
- Docker socket mount: `/var/run/docker.sock` (in `docker-compose.yml`)
- Docker CLI installed in gateway container for container management
- Config: `gateway.docker.enabled`, `gateway.docker.network`, `gateway.docker.image-prefix`
- World restore: Configurable retries and timeouts via `gateway.orchestrator.*` properties

## Monitoring & Observability

**Error Tracking:**
- Not detected - no Sentry, Datadog, or similar SDK

**Logs:**
- Standard Spring Boot logging (default Logback)
- No structured logging framework detected

**Health Checks:**
- PostgreSQL: `pg_isready` (Docker healthcheck)
- Redis: `redis-cli ping` (Docker healthcheck)
- Game JVM health: Gateway polls with configurable timeout (`gateway.orchestrator.health-timeout-ms: 180000`)

## CI/CD & Deployment

**CI Pipeline (GitHub Actions):**
- Verify workflow: `.github/workflows/verify.yml`
  - Trigger: Push to `main` + pull requests
  - Services: PostgreSQL 16 + Redis 7 (GitHub Actions services)
  - Java: Amazon Corretto 17 (`actions/setup-java@v4`)
  - Node: 24 (`actions/setup-node@v4`)
  - pnpm: 10.26.2 (`pnpm/action-setup@v4`)
  - Runs: `./verify ci` (backend tests + parity, frontend unit tests + typecheck + build + parity)

- Docker Build workflow: `.github/workflows/docker-build.yml`
  - Trigger: Push to `main` (backend/frontend/docker-compose changes) + manual dispatch
  - Registry: `ghcr.io` (GitHub Container Registry)
  - Images built:
    - `ghcr.io/{owner}/opensam-gateway` - Gateway app
    - `ghcr.io/{owner}/opensam-game` - Game app
    - `ghcr.io/{owner}/opensam-frontend` - Frontend
  - Tags: Git SHA + `latest`
  - Cache: GitHub Actions cache (`type=gha`)
  - Deploy: SSH to EC2 (`appleboy/ssh-action@v1`), `docker compose pull && up -d`

**Local Verification (`./verify`):**
- Entry: `verify` (shell script at project root)
- Implementation: `scripts/verify/run.sh`
- Profiles:
  - `pre-commit` - Runs only for staged changes, includes TDD gate (`scripts/verify/tdd-gate.sh`)
  - `ci` / `nightly` - Full suite (backend tests + parity, frontend tests + typecheck + build + parity)
- Git hooks: `scripts/verify/install-hooks.sh`

**Deployment Target:**
- AWS EC2 instance
- Deploy repo: `https://github.com/peppone-choi/opensamguk-deploy`
- Architecture: nginx (port 80) -> frontend (port 3000) + gateway (port 8080)
- WebSocket: nginx routes `/ws` directly to game-app (port 9001)
- API: nginx routes `/api/` to gateway
- Internal endpoints blocked: `/internal/` returns 403

**Container Images:**
- Backend: `eclipse-temurin:17-jdk-alpine` (multi-stage build)
- Frontend: `node:20-alpine` (multi-stage build, standalone output)
- All pushed to GHCR: `ghcr.io/peppone-choi/opensam-{gateway,game,frontend}`

## MCP Servers (Development Tools)

Configured in `.mcp.json`:
- **context7** - Library documentation lookup (`@upstash/context7-mcp`)
- **sequential-thinking** - Structured reasoning (`@modelcontextprotocol/server-sequential-thinking`)
- **playwright** - Browser automation for testing (`@playwright/mcp`)
- **postgres** - Direct PostgreSQL access for development (`@modelcontextprotocol/server-postgres`)

## Environment Configuration

**Required env vars (production):**
- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` - Database
- `REDIS_HOST`, `REDIS_PORT` - Cache
- `ADMIN_LOGIN_ID`, `ADMIN_PASSWORD` - Initial admin account
- `DOCKER_NETWORK`, `DOCKER_IMAGE_PREFIX` - Container orchestration
- `NEXT_PUBLIC_IMAGE_CDN_BASE` - Image CDN

**Optional env vars:**
- `KAKAO_REST_API_KEY`, `OAUTH_ACCOUNT_LINK_CALLBACK_URI` - Kakao OAuth
- `NEXT_PUBLIC_KAKAO_ENABLED` - Frontend OAuth toggle
- `NEXT_PUBLIC_WS_URL` - WebSocket URL override
- `GATEWAY_RESTORE_ACTIVE_WORLDS` - Auto-restore worlds on startup
- `GATEWAY_HEALTH_TIMEOUT_MS` - Game JVM health check timeout
- `SERVER_PORT` - Game-app port override (default: 9001)
- `GAME_COMMIT_SHA`, `GAME_VERSION` - Version pinning

**Secrets location:**
- `.env.example` at project root (template, no real secrets)
- `.env` files not committed (gitignored)
- GitHub Actions secrets: `EC2_HOST`, `EC2_SSH_KEY`, `GITHUB_TOKEN`

## Webhooks & Callbacks

**Incoming:**
- Kakao OAuth callback: `/auth/kakao/callback` (frontend page)
- Backend OAuth complete: gateway API endpoint for code exchange

**Outgoing:**
- None detected

## Legacy Reference

**PHP Legacy Codebase:**
- Location: `legacy-core/` directory (cloned from `https://storage.hided.net/gitea/devsam/core`)
- Purpose: Parity target - the Kotlin/TypeScript implementation replicates this PHP game
- Not a runtime dependency - used for reference/comparison only
- Parity tests: `com.opensam.qa.parity.*`, `com.opensam.command.CommandParityTest`, etc.

---

*Integration audit: 2026-03-31*
