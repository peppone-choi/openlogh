# External Integrations

**Analysis Date:** 2026-03-28

## APIs & External Services

**Authentication (Planned, Not Active):**

- Kakao OAuth 2.0 - OAuth integration flag present but disabled
    - Status: `auth.oauth.kakao-enabled: false` in `backend/gateway-app/src/main/resources/application.yml`
    - Implementation: Callback endpoint exists at `frontend/src/app/(auth)/auth/kakao/callback/page.tsx`
    - SDK: None currently active

## Data Storage

**Databases:**

- PostgreSQL 16
    - Connection: Configured via `DB_HOST`, `DB_PORT`, `DB_USER`, `DB_PASSWORD` environment variables
    - Default: `localhost:5432`
    - Client: Spring Data JPA (Hibernate ORM)
    - Migrations: Flyway (managed in `backend/game-app/src/main/resources/db/migration/`)

**File Storage:**

- CDN (Read-only images): `https://cdn.jsdelivr.net/gh/peppone-choi/openlogh-image@master/`
    - Configured via `NEXT_PUBLIC_IMAGE_CDN_BASE` environment variable
    - Used for game assets (3D models, textures, sprites)
    - No backend file storage or S3 integration detected

**Caching:**

- Redis 7
    - Connection: Configured via `REDIS_HOST`, `REDIS_PORT` environment variables
    - Default: `localhost:6379`
    - Client: Spring Data Redis
    - Usage: Session/state caching (exact usage requires code review)

## Authentication & Identity

**Auth Provider:**

- Custom JWT-based authentication
    - Implementation: `backend/shared` module (JJWT library)
    - Location: `backend/shared/build.gradle.kts` - JWT validation (shared between gateway and game)
    - Token Secret: `JWT_SECRET` environment variable (must be 32+ characters for HS256)
    - Expiration: 86400000ms (24 hours) configured in application.yml

**Gateway Bootstrap:**

- Admin user auto-creation on first startup
    - Enabled via `ADMIN_BOOTSTRAP_ENABLED` (default: true)
    - Credentials configured via environment: `ADMIN_LOGIN_ID`, `ADMIN_PASSWORD`, `ADMIN_DISPLAY_NAME`, `ADMIN_GRADE`
    - Location: `backend/gateway-app/src/main/resources/application.yml`

**Frontend Token Storage:**

- LocalStorage (`token` key)
    - Token set by login/registration endpoints
    - Auto-attached to all axios requests via interceptor in `frontend/src/lib/api.ts`
    - Auto-cleared on 401 Unauthorized response

## Monitoring & Observability

**Error Tracking:**

- Not detected - no Sentry, Rollbar, or similar integration

**Logs:**

- Console/stdout logging
    - Spring Boot default SLF4J logging
    - Frontend: `console.log`, `sonner` toast notifications for user-facing errors
    - No centralized logging service (ELK, CloudWatch, etc.) detected

**Health Checks:**

- Spring Boot Actuator - Not explicitly enabled in config
- Docker Compose health checks: PostgreSQL and Redis use native health checks

## CI/CD & Deployment

**Hosting:**

- Docker containers (ghcr.io/peppone-choi/openlogh-\*)
    - Gateway: `ghcr.io/peppone-choi/openlogh-gateway:{TAG}`
    - Game: `ghcr.io/peppone-choi/openlogh-game:{TAG}`
    - Frontend: `ghcr.io/peppone-choi/openlogh-frontend:{TAG}`
    - Bootstrap: `ghcr.io/peppone-choi/openlogh-game:{TAG}` (special bootstrap mode)
- Nginx reverse proxy for routing

**CI Pipeline:**

- GitHub Actions (inferred from GHCR image storage)
- Dockerfile multi-stage builds present
- Tag promotion via `TAG` environment variable in docker-compose.yml

**Build Artifacts:**

- Docker images published to GitHub Container Registry (GHCR)
- Image prefix configurable via `DOCKER_IMAGE_PREFIX` environment variable

## Environment Configuration

**Required env vars:**

- `DB_NAME` - PostgreSQL database name (default: `openlogh`)
- `DB_USER` - PostgreSQL user (default: `openlogh`)
- `DB_PASSWORD` - PostgreSQL password (default: `openlogh123`)
- `DB_PORT` - PostgreSQL port (default: `5432`)
- `REDIS_PORT` - Redis port (default: `6379`)
- `JWT_SECRET` - JWT signing key (32+ chars for HS256, must change in production)
- `ADMIN_LOGIN_ID` - Bootstrap admin username
- `ADMIN_PASSWORD` - Bootstrap admin password (CHANGE_ME_ADMIN_PASSWORD in example)
- `ADMIN_DISPLAY_NAME` - Admin display name (Korean: `관리자`)
- `ADMIN_GRADE` - Admin privilege level (default: `5`)
- `TAG` - Docker image tag for pre-built containers (default: `latest`)
- `HTTP_PORT` - Nginx HTTP port (default: `80`)
- `DOCKER_IMAGE_PREFIX` - Container registry prefix (default: `ghcr.io/peppone-choi/openlogh-game`)
- `DOCKER_NETWORK` - Docker network name (default: `openlogh-net`)

**Frontend env vars (build-time):**

- `NEXT_PUBLIC_API_URL` - Backend API base URL (default: `/api` → relative to frontend)
- `NEXT_PUBLIC_WS_URL` - WebSocket URL for game events (default: auto-derive from `window.location`)
- `NEXT_PUBLIC_IMAGE_CDN_BASE` - CDN URL for static assets

**Secrets location:**

- `.env` file (local development)
- Environment variables in Docker Compose (production)
- `.env` file must NOT be committed (listed in `.gitignore`)

## Webhooks & Callbacks

**Incoming:**

- Kakao OAuth callback: `/auth/kakao/callback` (endpoint exists but OAuth disabled)
- WebSocket endpoints (STOMP protocol):
    - `/topic/world/{worldId}/turn` - Turn advancement events
    - `/topic/world/{worldId}/battle` - Battle result events
    - `/topic/world/{worldId}/diplomacy` - Diplomacy change events
    - `/topic/world/{worldId}/message` - In-game message notifications

**Outgoing:**

- None detected - no external webhook integrations

## Inter-Service Communication

**Gateway → Game Service:**

- HTTP/WebFlux client (Spring Boot WebFlux Starter)
- Location: `backend/gateway-app/src/main/kotlin/com/openlogh/gateway/config/WebClientConfig.kt`
- Used for proxying game requests to dynamically spawned game JVMs
- Error handling: `WebClientResponseException` caught in `backend/gateway-app/src/main/kotlin/com/openlogh/gateway/controller/WorldController.kt`

**Frontend → Backend:**

- REST API via Axios
    - Base URL: `process.env.NEXT_PUBLIC_API_URL` or `http://localhost:8080/api`
    - Location: `frontend/src/lib/api.ts`
    - Authentication: Bearer token in `Authorization` header
- WebSocket (STOMP) for real-time game events
    - Broker URL: `process.env.NEXT_PUBLIC_WS_URL` or auto-derived
    - Location: `frontend/src/lib/websocket.ts`
    - Client: `@stomp/stompjs` with `sockjs-client` fallback

## Database Migrations

**Tool:** Flyway 1.0

- Locations: `backend/game-app/src/main/resources/db/migration/`
- Format: SQL (V{number}\_\_{description}.sql)
- Auto-run: Enabled in game-app, disabled in gateway-app
- Migration examples:
    - `V10__board_comment_reference_message.sql`
    - `V26__create_records_table.sql`
    - `V27__rename_tables_to_logh.sql` (domain migration from OpenSamguk → OpenLOGH)
    - `V29__rename_columns_planet.sql`
    - `V31__add_logh_new_columns.sql`

---

_Integration audit: 2026-03-28_
