# External Integrations

**Analysis Date:** 2026-03-31

## APIs & External Services

**Kakao OAuth 2.0:**
- Purpose: Account linking (not primary login)
- Status: **Disabled by default** (`auth.oauth.kakao-enabled: false` in `backend/gateway-app/src/main/resources/application.yml`)
- Implementation: `backend/gateway-app/src/main/kotlin/com/openlogh/gateway/service/AccountOAuthService.kt`
- Controller: `backend/gateway-app/src/main/kotlin/com/openlogh/gateway/controller/AccountOAuthController.kt`
- Frontend callback: `frontend/src/app/(auth)/auth/kakao/callback/page.tsx`
- Endpoints called:
  - `https://kauth.kakao.com/oauth/authorize` - OAuth authorization redirect
  - `https://kauth.kakao.com/oauth/token` - Token exchange (authorization code -> access/refresh tokens)
  - `https://kapi.kakao.com/v2/user/me` - User profile retrieval
- Auth: `KAKAO_REST_API_KEY` env var (REST API key, not SDK)
- Callback URI: `OAUTH_ACCOUNT_LINK_CALLBACK_URI` env var, or auto-derived from request origin
- HTTP client: `java.net.http.HttpClient` (not Spring WebClient)
- Token storage: OAuth tokens stored in `AppUser.meta["oauthProviders"]` JSONB field
- Provider data stored per entry: `provider`, `externalId`, `linkedAt`, `accessToken`, `accessTokenValidUntil`, `refreshToken`, `refreshTokenValidUntil`

**jsdelivr CDN (Static Assets):**
- Purpose: Game image assets (portraits, map tiles, icons, sprites)
- Two CDN sources in codebase:
  - Game images: `https://cdn.jsdelivr.net/gh/peppone-choi/openlogh-image@master/` (`frontend/src/lib/image.ts`)
  - Legacy/Docker build default: `https://cdn.jsdelivr.net/gh/peppone-choi/opensamguk-image@master/` (`frontend/next.config.ts`, `frontend/Dockerfile`)
  - **Discrepancy:** `image.ts` defaults to `openlogh-image` repo, but `next.config.ts` and Dockerfile still reference `opensamguk-image`
- Fonts:
  - Pretendard: `https://cdn.jsdelivr.net/gh/orioncactus/pretendard/dist/web/static/pretendard.css` (`frontend/src/app/layout.tsx`)
  - DungGeunMo: `https://cdn.jsdelivr.net/gh/projectnoonnu/noonfonts_six@1.2/DungGeunMo.woff2` (`frontend/src/app/globals.css`)
- Configuration: `NEXT_PUBLIC_IMAGE_CDN_BASE` env var (build-time)
- Image utility functions: `frontend/src/lib/image.ts`
  - `getPortraitUrl(picture)` - Officer portrait images
  - `getMapAssetUrl(asset)` - Map assets
  - `getPlanetLevelIcon(level)` - Planet level indicators
  - `getShipClassIconUrl(crewType)` - Ship class icons
  - `getMapBgUrl(mapFolder, season)` - Seasonal map backgrounds
  - `getMapRoadUrl(mapCode)` - Map road overlays

## Data Storage

**PostgreSQL 16:**
- Docker image: `postgres:16-alpine`
- Connection:
  - Dev: `jdbc:postgresql://localhost:5432/openlogh` (both apps)
  - Docker: `jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}`
  - Test: `jdbc:h2:mem:openlogh;MODE=PostgreSQL` (H2 in PostgreSQL compatibility mode)
- Env vars: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`
- ORM: Spring Data JPA with Hibernate
  - Dialect: `org.hibernate.dialect.PostgreSQLDialect`
  - DDL: `hibernate.ddl-auto: update` (both apps in dev)
  - Open-in-view: disabled (`spring.jpa.open-in-view: false`)
- Connection pool: Hikari (Spring Boot default, no explicit tuning)
- Migrations: Flyway
  - Location: `backend/game-app/src/main/resources/db/migration/`
  - 40 migrations (V1 through V40)
  - Enabled: game-app only (`spring.flyway.enabled: true`)
  - Disabled in: gateway-app, test profile
  - Key domain migrations:
    - `V27__rename_tables_to_logh.sql` - Table rename from OpenSamguk to OpenLOGH
    - `V28__rename_columns_officer.sql` - Officer column renames
    - `V29__rename_columns_planet.sql` - Planet column renames
    - `V30__rename_columns_faction.sql` - Faction column renames
    - `V31__add_logh_new_columns.sql` - New LOGH-specific columns (8-stat system)
    - `V32__add_position_card_table.sql` - Position card system
    - `V36__add_battle_record_and_supplies.sql` - Battle records
    - `V40__add_home_planet_and_origin_columns.sql` - Latest migration
- Docker healthcheck: `pg_isready -U ${POSTGRES_USER}`
- Volume: `postgres-data` (named Docker volume)
- Init args: `--encoding=UTF8 --locale=C.UTF-8`

**Redis 7:**
- Docker image: `redis:7-alpine`
- Connection:
  - Dev: `localhost:6379`
  - Docker: `${REDIS_HOST}:${REDIS_PORT}`
- Env vars: `REDIS_HOST`, `REDIS_PORT`
- Client: Spring Data Redis (`spring-boot-starter-data-redis`)
- Repository mode: **Disabled** (`spring.data.redis.repositories.enabled: false` in both apps)
- Actual usage: No `RedisTemplate`, `StringRedisTemplate`, `@Cacheable`, or Redis repository patterns detected in codebase
  - Redis dependency is configured but appears to be minimally used or reserved for future use
- Docker healthcheck: `redis-cli ping`
- Volume: `redis-data` (named Docker volume)

**H2 Database (Test only):**
- Used in: test profile (`application-test.yml`)
- Mode: In-memory, PostgreSQL compatibility (`MODE=PostgreSQL`)
- Purpose: Fast unit/integration tests without external DB
- Config: `backend/game-app/src/main/resources/application-test.yml`

## Authentication & Identity

**Custom JWT Authentication:**
- Library: JJWT 0.12.6 (`io.jsonwebtoken:jjwt-api`)
- Shared across: gateway-app and game-app via `shared` module (`backend/shared/build.gradle.kts`)
- Algorithm: HS256
- Secret: `app.jwt.secret` property, env var `JWT_SECRET`
  - Must be at least 256 bits (32+ characters)
  - Default dev value in application.yml (must be changed in production)
- Token expiration: 86400000ms (24 hours)
- Gateway auth: `backend/gateway-app/src/main/kotlin/com/openlogh/gateway/service/AuthService.kt`
- Game-app auth: `backend/game-app/src/main/kotlin/com/openlogh/service/AuthService.kt`

**Admin Bootstrap:**
- Location: Gateway app configuration (`backend/gateway-app/src/main/resources/application.yml`)
- Creates admin user on first startup when `ADMIN_BOOTSTRAP_ENABLED=true`
- Env vars: `ADMIN_LOGIN_ID`, `ADMIN_PASSWORD`, `ADMIN_DISPLAY_NAME`, `ADMIN_GRADE`
- Admin grade levels: numeric (default `5` is highest)

**Frontend Token Management:**
- Storage: `localStorage` key `token` (`frontend/src/lib/api.ts`)
- Attachment: Axios request interceptor adds `Authorization: Bearer {token}` header
- 401 handling: Auto-clear token, redirect to `/login` (skipped for `/auth/` endpoints)

## WebSocket Real-time Communication

**Protocol Stack:**
- STOMP over WebSocket (Spring `@EnableWebSocketMessageBroker`)
- Config: `backend/game-app/src/main/kotlin/com/openlogh/config/WebSocketConfig.kt`
- Simple in-memory broker (not external message broker)
- Application prefix: `/app`
- Broker prefixes: `/topic`, `/queue`

**Endpoints (game-app):**
- `/ws` - SockJS-wrapped STOMP endpoint (legacy fallback, `setAllowedOriginPatterns("*")`)
- `/ws-stomp` - Raw STOMP WebSocket endpoint (preferred, no SockJS overhead)

**Subscription Topics:**
- `/topic/world/{worldId}/turn` - Turn advancement events (year/month changes)
- `/topic/world/{worldId}/battle` - Battle result broadcasts
- `/topic/world/{worldId}/diplomacy` - Diplomacy change events
- `/topic/world/{worldId}/message` - In-game message notifications

**WebSocket Controllers (game-app):**
- `backend/game-app/src/main/kotlin/com/openlogh/websocket/CommandWebSocketController.kt` - Command dispatch
- `backend/game-app/src/main/kotlin/com/openlogh/websocket/TacticalWebSocketController.kt` - Tactical combat
- `backend/game-app/src/main/kotlin/com/openlogh/websocket/BattleWebSocketController.kt` - Battle events

**Frontend WebSocket Client:**
- Library: `@stomp/stompjs` (native STOMP, no SockJS wrapper in client)
- Location: `frontend/src/lib/websocket.ts`
- Broker URL construction: Prefers `NEXT_PUBLIC_WS_URL` env var, falls back to `window.location`-derived URL
- Reconnect delay: 5000ms
- Hook: `frontend/src/hooks/useWebSocket.ts` - React hook for WebSocket lifecycle
- Tactical hook: `frontend/src/hooks/useTacticalWebSocket.ts` - Battle-specific WebSocket
- Store: `frontend/src/stores/battleStore.ts` - Battle state management via WebSocket

**Nginx WebSocket Proxying:**
- Config: `nginx/nginx.conf`
- `/ws/` -> `http://game:9001/ws/` (SockJS, `Connection: upgrade`)
- `/ws-stomp` -> `http://game:9001/ws-stomp` (raw STOMP, `Connection: upgrade`)
- Read timeout: 3600s for WebSocket connections

## Inter-Service Communication

**Gateway -> Game-app (HTTP Proxy):**
- Client: Spring WebFlux (`spring-boot-starter-webflux`) reactive HTTP client
- Location: `backend/gateway-app/build.gradle.kts`
- Pattern: Gateway proxies API requests to dynamically discovered game-app instances
- Route registry: `WorldRouteRegistry` maps `worldId` -> `baseUrl` (e.g., `http://127.0.0.1:9001`)
- Health check: `GET /internal/health` with 2-second timeout, polled every 500ms during startup

**Game Process Orchestration:**

Two orchestrator implementations, selected by `gateway.docker.enabled` property:

1. **`GameProcessOrchestrator`** (`backend/gateway-app/src/main/kotlin/com/openlogh/gateway/orchestrator/GameProcessOrchestrator.kt`)
   - Active when: `gateway.docker.enabled=false` (default, for local dev)
   - Spawns game-app as child JVM processes via `ProcessBuilder`
   - Command: `java -jar artifacts/game-app-{commitSha}.jar --server.port={port}`
   - Port allocation: 9001-9999 range, sequential allocation avoiding conflicts
   - Logs: `logs/game-{commitSha}.log`
   - Lifecycle: graceful `destroy()` with 5s timeout, then `destroyForcibly()`
   - Health timeout: configurable via `gateway.orchestrator.health-timeout-ms` (default 120s)
   - Version keyed by: `commitSha`

2. **`GameContainerOrchestrator`** (`backend/gateway-app/src/main/kotlin/com/openlogh/gateway/orchestrator/GameContainerOrchestrator.kt`)
   - Active when: `gateway.docker.enabled=true` (production Docker deployment)
   - Spawns game-app as Docker containers via `docker run` CLI
   - Docker socket: mounted at `/var/run/docker.sock` in gateway container
   - Network: `${DOCKER_NETWORK}` (default: `openlogh-net`), alias `game`
   - Container naming: `game-{sanitized-version}`
   - Label: `opensam.role=game` (legacy label, not yet renamed to openlogh)
   - Image: `${DOCKER_IMAGE_PREFIX}:{gameVersion}` (default: `ghcr.io/peppone-choi/openlogh-game`)
   - Passes through: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`
   - Orphan cleanup: removes unlabeled game containers on health check cycles
   - Version keyed by: `gameVersion`

**World Activation Bootstrap:**
- Location: `backend/gateway-app/src/main/kotlin/com/openlogh/gateway/orchestrator/WorldActivationBootstrap.kt`
- Triggers on gateway startup
- Restores previously active worlds by reattaching to running game instances or spawning new ones
- Configurable: `gateway.orchestrator.restore-active-worlds` (default: true)
- Retry: max 3 retries with 30s delay between attempts

**Frontend -> Gateway (REST):**
- Client: Axios (`frontend/src/lib/api.ts`)
- Base URL: `NEXT_PUBLIC_API_URL` (default: `/api` via Nginx proxy, dev: `http://localhost:8080/api`)
- Auth: Bearer token in `Authorization` header (from localStorage)

## Turn Engine

**Location:** `backend/game-app/src/main/kotlin/com/openlogh/engine/TurnDaemon.kt`
- Scheduled tick: `@Scheduled(fixedRateString = "${game.tick-rate:1000}")` (default 1 second)
- Task pool: 2 threads (`spring.task.scheduling.pool.size: 2`)
- Three processing modes per world:
  1. **Realtime mode** (`world.realtimeMode`): processes commands + regenerates command points
  2. **CQRS mode** (`openlogh.cqrs.enabled: true`): delegates to `TurnCoordinator`
  3. **Legacy mode** (default): delegates to `TurnService`
- CQRS: currently **disabled** (`openlogh.cqrs.enabled: false` in `application.yml`)
- Skip conditions: world locked, before open time, gateway marked inactive

## CI/CD & Deployment

**GitHub Container Registry (GHCR):**
- Images: `ghcr.io/peppone-choi/openlogh-{gateway,game,frontend}`
- Tags: git SHA (short) + `latest`
- Auth: `GITHUB_TOKEN` (automatic in Actions)

**CI Pipeline** (`.github/workflows/verify.yml`):
- Trigger: push to main, pull requests
- Concurrency: cancel in-progress for same ref
- Backend job: Java 17 Temurin, Gradle compile + test (`SPRING_PROFILES_ACTIVE=test`)
- Frontend job: Node 20, pnpm 10, lint + typecheck + vitest

**Docker Build Pipeline** (`.github/workflows/docker-build.yml`):
- Trigger: push to main (scoped to backend/frontend/nginx/docker-compose changes) + manual dispatch
- Three parallel jobs: build-gateway, build-game, build-frontend
- Uses: `docker/build-push-action@v6` with buildx, GitHub Actions cache
- Frontend build args baked in: `NEXT_PUBLIC_API_URL=/api`, `NEXT_PUBLIC_IMAGE_CDN_BASE=...opensamguk-image...`

**Local Development:**
- `docker-compose.yml` - Pull pre-built images from GHCR
- `docker-compose.local.yml` - Override with local builds (`docker-compose -f docker-compose.yml -f docker-compose.local.yml up --build`)

**Production Stack (docker-compose.yml):**
```
postgres (16-alpine) -> bootstrap (game-app, exit-on-ready) -> gateway (8080) + frontend (3000) -> nginx (80)
                                                                    |
                                                              game containers (9001, spawned dynamically)
```

## Monitoring & Observability

**Error Tracking:** None (no Sentry, Rollbar, Datadog, or similar)

**Logging:**
- Backend: SLF4J via `LoggerFactory.getLogger()` (Spring Boot default)
- Game-app child processes: stdout redirected to `logs/game-{commitSha}.log`
- Frontend: `console.error()` in catch blocks, Sonner toasts for user-facing errors
- No centralized log aggregation

**Health Checks:**
- Game-app: `/internal/health` endpoint (used by orchestrator)
- PostgreSQL: `pg_isready` in Docker healthcheck
- Redis: `redis-cli ping` in Docker healthcheck
- Spring Boot Actuator: not explicitly enabled (no `spring-boot-starter-actuator` dependency)

## Environment Configuration

**Required env vars (production):**

| Variable | Default | Purpose |
|----------|---------|---------|
| `DB_HOST` | `postgres` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `openlogh` | Database name |
| `DB_USER` | `openlogh` | Database user |
| `DB_PASSWORD` | `openlogh123` | Database password |
| `REDIS_HOST` | `redis` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |
| `JWT_SECRET` | (dev default) | JWT signing key (MUST change in prod) |
| `ADMIN_BOOTSTRAP_ENABLED` | `true` | Create admin on first start |
| `ADMIN_LOGIN_ID` | `admin` | Bootstrap admin username |
| `ADMIN_PASSWORD` | `CHANGE_ME_ADMIN_PASSWORD` | Bootstrap admin password |
| `TAG` | `latest` | Docker image tag |
| `DOCKER_NETWORK` | `openlogh-net` | Docker network name |
| `DOCKER_IMAGE_PREFIX` | `ghcr.io/peppone-choi/openlogh-game` | Game container image |

**Optional env vars:**

| Variable | Default | Purpose |
|----------|---------|---------|
| `KAKAO_REST_API_KEY` | (empty) | Kakao OAuth REST API key |
| `OAUTH_ACCOUNT_LINK_CALLBACK_URI` | (empty) | OAuth callback URI override |
| `GATEWAY_RESTORE_ACTIVE_WORLDS` | `true` | Restore worlds on gateway restart |
| `GATEWAY_HEALTH_TIMEOUT_MS` | `180000` | Game-app health check timeout |
| `GATEWAY_RESTORE_MAX_RETRIES` | `3` | World restore retry count |
| `GATEWAY_RESTORE_RETRY_DELAY_MS` | `30000` | World restore retry delay |
| `HTTP_PORT` | `80` | Nginx HTTP port |

**Frontend build-time env vars:**

| Variable | Default | Purpose |
|----------|---------|---------|
| `NEXT_PUBLIC_API_URL` | `/api` | Backend API base URL |
| `NEXT_PUBLIC_WS_URL` | (auto-derived) | WebSocket base URL |
| `NEXT_PUBLIC_IMAGE_CDN_BASE` | jsdelivr CDN | Image asset CDN base |

**Secrets location:**
- `.env` file for local development (`.env.example` provided as template)
- Docker Compose environment blocks for production
- GitHub Actions secrets for CI (`GITHUB_TOKEN` for GHCR)

## Webhooks & Callbacks

**Incoming:**
- Kakao OAuth callback: `/auth/kakao/callback` (frontend route, currently disabled)

**Outgoing:**
- None detected

---

*Integration audit: 2026-03-31*
