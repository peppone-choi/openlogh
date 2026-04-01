# Codebase Concerns

**Analysis Date:** 2026-03-31

## Tech Debt

**Incomplete OpenSamguk-to-LOGH Migration (High Priority):**
- Issue: The fork from OpenSamguk is partially renamed. Legacy "삼국지/오픈삼국" references remain in user-facing pages, Docker labels, default configs, CDN URLs, and frontend utility code.
- Files:
    - `frontend/src/app/(auth)/terms/page.tsx` — Title says "오픈삼국 이용 약관", body references "삼국지 모의전투" throughout (lines 16, 24, 35, 208, 210)
    - `frontend/src/app/(auth)/register/page.tsx` — Title "오픈삼국 회원가입" (line 299), terms text references "삼국지 전략 시뮬레이션" (line 68)
    - `frontend/src/app/(auth)/privacy/page.tsx` — "오픈삼국 서비스" (line 27)
    - `frontend/src/app/(auth)/terms/terms.test.ts` — Asserts "오픈삼국" (lines 5-6)
    - `frontend/e2e/game-flow.spec.ts` — Test suite named "OpenSamguk game full flow" (line 29), expects "오픈삼국 로그인" text (line 62)
    - `frontend/next.config.ts` — Default image CDN points to `peppone-choi/opensamguk-image@master/` (line 4)
    - `frontend/src/app/(game)/map/page.tsx` — Fallback server name is "삼국지" (line 432)
    - `backend/gateway-app/src/main/kotlin/com/openlogh/gateway/orchestrator/GameContainerOrchestrator.kt` — Docker defaults: `opensam-net` network (line 29), `opensam/game-app` image prefix (line 31), `opensam` DB name/user/password (lines 37-41), Docker label `opensam.role` (lines 253, 377, 517)
- Impact: User-facing branding is still "삼국지/오픈삼국" instead of "은하영웅전설/OpenLOGH"; Docker orchestrator defaults reference old project names; CDN serves Three Kingdoms character images instead of LOGH assets
- Fix approach: Batch rename all user-facing strings to LOGH equivalents; update Docker default values to `openlogh-net`/`openlogh/game-app`/`openlogh`; replace CDN default with LOGH image repository

**Deprecated Field Aliases in Frontend Types (Medium Priority):**
- Issue: `frontend/src/types/index.ts` maintains 8+ sets of deprecated backward-compat aliases mapping old OpenSamguk names (General, City, Nation, Troop, gold, rice, crew, etc.) to new LOGH names (Officer, StarSystem, Faction, Fleet, funds, supplies, ships, etc.)
- Files: `frontend/src/types/index.ts` — deprecated alias blocks at lines 99, 111, 153, 171, 287, 307, 366, 405, 444, 486, 492, 539, 556, 1048, 1360
- Impact: Type definitions are twice as large as needed; consumers may use deprecated names creating inconsistency; TypeScript autocompletion shows both old and new fields
- Fix approach: Audit all consumers of deprecated aliases, migrate to new names, remove deprecated aliases and type aliases (`Nation = Faction`, `General = Officer`, `City = StarSystem`, `Troop = Fleet`, etc.)

**Legacy Three Kingdoms Game Logic in Frontend Utilities:**
- Issue: `frontend/src/lib/game-utils.ts` contains Three Kingdoms rank/level systems (황건, 유가, 법가, 병가, 도적 faction types; 황제/왕/공 rank names) that should be LOGH faction types (Empire/Alliance/Fezzan) and LOGH rank names (Reichsmarschall/Fleet Admiral/etc.)
- Files:
    - `frontend/src/lib/game-utils.ts` — `OfficerLevelMapByNationLevel` (line 161), `SpecialNationOfficerMap` (line 284), `getSpecialNationKey` (line 306), `getNationTypeLabel`/`getNationLevelLabel` functions
    - `frontend/src/lib/game-utils.test.ts` — Tests assert Three Kingdoms values (lines 43-165)
- Impact: In-game UI shows Three Kingdoms faction/rank names instead of LOGH universe equivalents
- Fix approach: Replace rank maps with LOGH rank system from CLAUDE.md; update test assertions; keep function signatures stable

**Legacy PostgreSQL ENUM Values (faction_aux_key):**
- Issue: The `faction_aux_key` ENUM (originally `nation_aux_key`) contains Three Kingdoms unit types (대검병, 극병, 화시병, 원융노병, 산저병, 상병, 음귀병, 무희, 화륜차) that have no LOGH equivalents
- Files:
    - `backend/game-app/src/main/resources/db/migration/V1__core_tables.sql` — ENUM definition (lines 246-251)
    - `backend/game-app/src/main/kotlin/com/openlogh/entity/FactionFlag.kt` — Kotlin enum `FactionAuxKey` (lines 9-12)
- Impact: Game mechanic flags reference Three Kingdoms special unit types instead of LOGH ship classes/technologies; adding new LOGH-specific flags requires ENUM migration
- Fix approach: Create V41+ migration to add LOGH-appropriate ENUM values, deprecate Three Kingdoms values, update FactionAuxKey enum

**18+ Unimplemented Stub Endpoints:**
- Issue: Controllers expose endpoints that return dummy/success data without business logic
- Files:
    - `backend/game-app/src/main/kotlin/com/openlogh/controller/AuctionController.kt` — market pricing (line 131), market buy (line 140), market sell (line 149), item auction creation (line 158)
    - `backend/game-app/src/main/kotlin/com/openlogh/controller/InheritanceController.kt` — inheritance info (line 17), set city (line 48), stat reset (line 86), owner check (line 97), buff purchase (line 108), log retrieval (line 117), unique item auction (line 128), general purchase (line 139)
    - `backend/game-app/src/main/kotlin/com/openlogh/controller/DiplomacyController.kt` — diplomacy response (line 34)
    - `backend/game-app/src/main/kotlin/com/openlogh/controller/SovereignController.kt` — sovereign action (line 25)
    - `backend/game-app/src/main/kotlin/com/openlogh/controller/TournamentController.kt` — register (line 57), advance (line 63), broadcast message (line 72)
- Impact: Players see functional UI for these features but actions have no effect; false "success" responses mask missing implementation
- Fix approach: Implement business logic per endpoint, or return 501 Not Implemented to signal incomplete features

**Tactical Battle Engine Hardcoded Planet Defense:**
- Issue: `orbitalDefense = 100.0` and `garrisonStrength = 100.0` are hardcoded instead of reading from Planet entity
- Files: `backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/TacticalBattleEngine.kt` (lines 159-160)
- Impact: All planet battles use identical defense values regardless of actual planet fortification level
- Fix approach: Wire Planet.orbitalDefense and Planet garrison data into tactical battle creation

**Pending Command Implementation (Test TODOs):**
- Issue: Test files reference unimplemented command classes
- Files: `backend/game-app/src/test/kotlin/com/openlogh/command/NationResourceCommandTest.kt` — 몰수 class (line 116), 증축/발령/천도 classes (line 146), 물자원조/국기변경/국호변경 classes (line 186)
- Impact: Nation resource management commands are partially implemented
- Fix approach: Implement missing command classes following existing command pattern in `backend/game-app/src/main/kotlin/com/openlogh/command/`

## Security Considerations

**CORS Wildcard on Both Gateway and Game-App (High Priority):**
- Risk: Both SecurityConfig files set `allowedOriginPatterns = listOf("*")` with `allowCredentials = true`
- Files:
    - `backend/game-app/src/main/kotlin/com/openlogh/config/SecurityConfig.kt` (lines 47-51)
    - `backend/gateway-app/src/main/kotlin/com/openlogh/gateway/config/SecurityConfig.kt` (lines 49-53)
- Current mitigation: None — wildcard origin with credentials is a security anti-pattern
- Recommendations: Restrict to actual frontend domain(s); use environment variable for allowed origins (e.g., `CORS_ALLOWED_ORIGINS=https://openlogh.example.com`)

**Gateway Permits All /api/worlds/** Requests Without Auth (High Priority):**
- Risk: Gateway SecurityConfig has `.requestMatchers("/api/worlds/**").permitAll()` which bypasses JWT auth for ALL world-related API calls
- Files: `backend/gateway-app/src/main/kotlin/com/openlogh/gateway/config/SecurityConfig.kt` (line 36)
- Current mitigation: Game-app has its own SecurityConfig requiring auth for non-public endpoints, but gateway proxies requests without auth context
- Recommendations: Remove the wildcard permitAll for `/api/worlds/**`; require authentication at gateway level; only permit specific public endpoints (e.g., world listing)

**Docker Socket Mounted into Gateway Container (High Priority):**
- Risk: Gateway container has `/var/run/docker.sock:/var/run/docker.sock` volume mount, giving it root-equivalent access to the host
- Files: `docker-compose.yml` (line 89)
- Current mitigation: Gateway uses it for GameContainerOrchestrator to spawn/stop game-app containers
- Recommendations: Use a Docker socket proxy (e.g., Tecnativa/docker-socket-proxy) that restricts allowed API calls; or move orchestration to a separate sidecar container with minimal permissions

**Hardcoded Default Credentials in Container Orchestrator:**
- Risk: `GameContainerOrchestrator.kt` has hardcoded fallback values: `DB_NAME:opensam`, `DB_USER:opensam`, `DB_PASSWORD:opensam123`
- Files: `backend/gateway-app/src/main/kotlin/com/openlogh/gateway/orchestrator/GameContainerOrchestrator.kt` (lines 37-41)
- Current mitigation: These are `@Value` defaults overrideable via environment variables
- Recommendations: Remove hardcoded password defaults; fail fast if DB_PASSWORD not set; at minimum update defaults to match docker-compose.yml values (`openlogh`/`openlogh123`)

**Default JWT Secret in Docker Compose:**
- Risk: `docker-compose.yml` includes a long default JWT secret (`openlogh-jwt-secret-key-must-be-at-least-256-bits-long-for-hs256`)
- Files: `docker-compose.yml` (lines 51, 78)
- Current mitigation: Environment variable override supported
- Recommendations: Remove default; require JWT_SECRET to be set explicitly; add startup validation

**Default Admin Credentials in Docker Compose:**
- Risk: `docker-compose.yml` sets `ADMIN_PASSWORD: ${ADMIN_PASSWORD:-CHANGE_ME_ADMIN_PASSWORD}` with a weak default
- Files: `docker-compose.yml` (line 83)
- Current mitigation: Default value is obviously placeholder
- Recommendations: Require ADMIN_PASSWORD to be set; fail startup if default is detected

**No Rate Limiting on Any Endpoint:**
- Risk: No rate limiting detected anywhere in the codebase — no Spring rate limiter, no gateway throttling, no Redis-based rate limit
- Files: Entire backend (confirmed via grep for `rate.?limit|throttl|@RateLimit`)
- Current mitigation: None
- Recommendations: Add rate limiting at minimum on auth endpoints (`/api/auth/**`), auction bids, command execution, and WebSocket message handlers

**WebSocket Endpoints Permit All Without Auth:**
- Risk: Both SecurityConfigs set `.requestMatchers("/ws/**").permitAll()`, and `/internal/**` is also fully open
- Files:
    - `backend/game-app/src/main/kotlin/com/openlogh/config/SecurityConfig.kt` (lines 33-34)
    - `backend/gateway-app/src/main/kotlin/com/openlogh/gateway/config/SecurityConfig.kt` (lines 30, 35)
- Current mitigation: WebSocket STOMP handlers may validate session context internally
- Recommendations: Add WebSocket authentication via STOMP CONNECT frame; restrict `/internal/**` to internal network only (not exposed via nginx)

**Internal Endpoints Exposed via Nginx:**
- Risk: Nginx config proxies `/internal/` to gateway without access restriction
- Files: `nginx/nginx.conf` (lines 53-60)
- Current mitigation: None — any external client can hit internal endpoints
- Recommendations: Add `allow` directive to restrict `/internal/` to private IPs only; or remove the nginx location block entirely

**Auction System Missing Ownership Verification:**
- Risk: Auction creation accepts any sellerId without verifying the requesting user owns that officer; bid endpoint has no auth check on bidder identity
- Files: `backend/game-app/src/main/kotlin/com/openlogh/controller/AuctionController.kt` (create at line 27, bid at line 54)
- Impact: Any authenticated user can create auctions for any officer or bid on behalf of any officer
- Recommendations: Verify requesting user's officer ID matches sellerId/bidderId via JWT claims

## Performance Bottlenecks

**SovereignController.findAll() Table Scan:**
- Problem: Loads ALL sovereign records then filters in memory by sessionId string comparison
- Files: `backend/game-app/src/main/kotlin/com/openlogh/controller/SovereignController.kt` (line 14)
- Cause: Uses `sovereignRepository.findAll()` instead of a query-by-sessionId repository method; additionally compares Long to String (`it.sessionId == sessionId.toString()`)
- Improvement path: Add `SovereignRepository.findBySessionId(sessionId: Long)` method; fix sessionId type to Long in Sovereign entity

**AuthService.findAll() Iterations:**
- Problem: Three methods in AuthService iterate over ALL users via `userRepository.findAll()` for token management operations
- Files: `backend/game-app/src/main/kotlin/com/openlogh/service/AuthService.kt` (lines 470, 487, 503)
- Cause: Token cleanup/migration operations scan entire user table
- Improvement path: Use targeted queries instead of full table scans; add batch processing with pagination

**AdminService.findAll() for User Listing:**
- Problem: `appUserRepository.findAll()` loads all users into memory for admin user list
- Files: `backend/game-app/src/main/kotlin/com/openlogh/service/AdminService.kt` (line 373)
- Cause: No pagination support
- Improvement path: Add paginated query with `Pageable` parameter

**Missing Database Indexes on Renamed Tables:**
- Problem: Original indexes (V1) were created on old table names (general, city, nation). Table renames in V27 preserved indexes, but new LOGH-specific columns added in V31/V37/V40 lack composite indexes
- Files:
    - `backend/game-app/src/main/resources/db/migration/V37__sync_entity_schema.sql` — Adds career_type, origin_type, pcp, mcp columns with no indexes
    - `backend/game-app/src/main/resources/db/migration/V40__add_home_planet_and_origin_columns.sql` — Adds home_planet, origin columns with no indexes
- Cause: Migration focus was on adding columns, not query optimization
- Improvement path: Add indexes on frequently queried new columns (e.g., `officer.career_type`, `officer.pcp`); profile actual query patterns before adding

**Large Frontend Page Components:**
- Problem: Multiple page components exceed 1000 lines with business logic, API calls, and rendering interleaved
- Files:
    - `frontend/src/app/(admin)/admin/page.tsx` (2365 lines)
    - `frontend/src/types/index.ts` (1787 lines)
    - `frontend/src/app/(game)/auction/page.tsx` (1409 lines)
    - `frontend/src/app/(game)/my-page/page.tsx` (1380 lines)
    - `frontend/src/app/(game)/betting/page.tsx` (1270 lines)
    - `frontend/src/app/(game)/battle-simulator/page.tsx` (1257 lines)
    - `frontend/src/app/(game)/faction/page.tsx` (1238 lines)
- Cause: Monolithic page components without extraction of sub-components or custom hooks
- Improvement path: Extract reusable sub-components; move API logic to custom hooks or store actions; split types/index.ts by domain

**ScenarioService First-Load Blocking:**
- Problem: `referencePictureByName` lazy map loads all scenarios on first access; blocking operation
- Files: `backend/game-app/src/main/kotlin/com/openlogh/service/ScenarioService.kt`
- Cause: Lazy initialization on first request
- Improvement path: Preload at startup via `@PostConstruct` or `ApplicationReadyEvent`; cache in Redis

## Fragile Areas

**Mutable JSONB Meta Fields Without Schema:**
- Files: `backend/game-app/src/main/kotlin/com/openlogh/entity/Officer.kt`, `backend/game-app/src/main/kotlin/com/openlogh/entity/Planet.kt`, `backend/game-app/src/main/kotlin/com/openlogh/entity/SessionState.kt`
- Why fragile: `meta`, `conflict`, `lastTurn`, `penalty` are `MutableMap<String, Any>` JSONB columns with no documented schema. Multiple services read/write different keys (e.g., `world.meta["tournamentState"]` in TournamentController). Adding or removing keys has no compile-time safety.
- Safe modification: Create typed data classes for known meta shapes; add JSON schema documentation; use sealed classes for meta key types
- Test coverage: No schema validation tests exist

**40-Migration Chain with Incremental Renames:**
- Files: `backend/game-app/src/main/resources/db/migration/V1__core_tables.sql` through `V40__add_home_planet_and_origin_columns.sql`
- Why fragile: The migration chain spans the full OpenSamguk-to-LOGH rename (V27-V35) with incremental column additions (V37, V38, V40). Fresh database setup must run all 40 migrations sequentially. Index names still reference old table names (e.g., `idx_general_world_id` for the `officer` table).
- Safe modification: Consider a squashed baseline migration for new deployments; keep incremental chain for existing databases
- Test coverage: No migration rollback tests

**Scenario Data JSON Parsing:**
- Files: `backend/game-app/src/main/kotlin/com/openlogh/service/ScenarioService.kt` (1170 lines)
- Why fragile: Parses scenario JSON files into `ArrayList<Any>` with extensive unsafe casts; 16+ `@Suppress("UNCHECKED_CAST")` in tests
- Safe modification: Create typed ScenarioDTO data classes; use Jackson deserialization to specific types
- Test coverage: Tests exist but use same unsafe casting pattern

## Known Bugs

**Sovereign Lookup Type Mismatch:**
- Symptoms: SovereignController compares `it.sessionId == sessionId.toString()` where sessionId is a Long, suggesting Sovereign entity stores sessionId as String but receives Long from path variable
- Files: `backend/game-app/src/main/kotlin/com/openlogh/controller/SovereignController.kt` (line 15)
- Trigger: Any request to `/api/worlds/{sessionId}/sovereign`
- Workaround: Works due to String.equals(Long.toString()) but wasteful (full table scan + string conversion)

**Stub Endpoints Return False Success:**
- Symptoms: 18+ endpoints return `{"success": true}` or empty default data without performing any operation
- Files: See "Unimplemented Stub Endpoints" section above
- Trigger: Any POST to sovereign/action, tournament/register, tournament/advance, diplomacy/respond, market buy/sell, etc.
- Workaround: None; frontend shows success toast but nothing changes

**OpenSamguk Branding in Production UI:**
- Symptoms: Terms of service, registration, privacy policy pages show "오픈삼국" and "삼국지" instead of "오픈 은하영웅전설" and LOGH references
- Files: See "Incomplete OpenSamguk-to-LOGH Migration" section above
- Trigger: Navigate to /terms, /register, or /privacy
- Workaround: None

## Scaling Limits

**Session-per-JVM Architecture:**
- Current capacity: One game-app JVM per game version, multiple worlds per JVM
- Limit: All worlds on same game version share one JVM; CPU-bound turn processing for one world blocks others
- Scaling path: Implement world-per-JVM isolation; add horizontal scaling for game-app instances behind load balancer

**Tactical Battle Session In-Memory:**
- Current capacity: All active tactical sessions held in `TacticalSessionManager.sessions` ConcurrentHashMap in single JVM
- Limit: 100+ concurrent battles with full fleet state + turn history could exceed 500MB heap
- Scaling path: Persist inactive battles to database; implement session eviction; consider off-heap storage

**No Connection Pooling Config for Redis:**
- Current capacity: Default Spring Data Redis connection settings
- Limit: Under high WebSocket traffic (2000 concurrent users target), default pool may exhaust connections
- Scaling path: Configure Lettuce connection pool size; add Redis Sentinel or Cluster for HA

## Dependencies at Risk

**OpenSamguk Image CDN Dependency:**
- Risk: Default image CDN (`cdn.jsdelivr.net/gh/peppone-choi/opensamguk-image@master/`) serves Three Kingdoms character portraits, not LOGH characters
- Impact: All officer portrait images are from wrong game universe
- Migration plan: Create LOGH-specific image repository; update CDN default in `frontend/next.config.ts`

**Jackson Unsafe Deserialization in ScenarioService:**
- Risk: `objectMapper.readValue<ArrayList<Any>>` can instantiate arbitrary classes if JSON includes polymorphic type info
- Impact: Remote code execution if scenario JSON files are attacker-controlled
- Migration plan: Use `objectMapper.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)`; create typed DTOs; validate scenario file checksums

## Test Coverage Gaps

**Controller-Level Tests:**
- What's not tested: Only 2 controller test files exist (`OfficerControllerTest.kt`, `GeneralControllerTest.kt`); no tests for AuctionController, InheritanceController, DiplomacyController, SovereignController, TournamentController, AuthController, AdminController, CharacterController, WorldCreationController, FrontInfoController, PrivateFundsController
- Files: `backend/game-app/src/test/kotlin/com/openlogh/controller/`
- Risk: HTTP layer bugs (serialization, status codes, auth checks) go undetected
- Priority: High — especially for endpoints with authorization concerns

**Frontend Unit Tests:**
- What's not tested: 42 frontend test files exist but mostly cover utility functions; large page components (admin 2365 LOC, auction 1409 LOC, my-page 1380 LOC) have no component-level tests
- Files: `frontend/src/` (42 test files for ~100+ source files)
- Risk: UI regressions in complex game pages undetected
- Priority: Medium — E2E tests (7 spec files) provide some coverage

**Gateway-App Tests:**
- What's not tested: Only 1 test file (`AuthServiceTest.kt`) for the entire gateway-app; no tests for GameContainerOrchestrator, GameProcessOrchestrator, WorldActivationBootstrap, AdminSystemController, proxy routing
- Files: `backend/gateway-app/src/test/kotlin/com/openlogh/gateway/`
- Risk: Container orchestration bugs (startup failures, dead container cleanup, orphan cleanup) undetected
- Priority: High — orchestrator manages production JVM processes

**Auction Edge Cases:**
- What's not tested: No tests for concurrent bids, auction expiration, bid rejection, self-bidding prevention
- Files: No test file exists for AuctionController
- Risk: Race conditions on simultaneous bids; expired auctions never close
- Priority: Medium

## Code Quality Issues

**Inconsistent Error Response Patterns:**
- Issue: Some controllers return `ResponseEntity.notFound()`, others return `ResponseEntity.ok(ErrorResponse(...))`, others throw exceptions caught by advice
- Files: Compare `AuctionController.kt` (returns OK with error field) vs `CharacterController.kt` (throws IllegalArgumentException) vs `AdminController.kt` (catches AccessDeniedException, returns 403)
- Impact: Frontend must handle errors differently per endpoint; no standardized error contract
- Fix approach: Establish consistent error response DTO; use `@ControllerAdvice` for all error mapping

**String-Based Status and Type Codes:**
- Issue: Auction status ("open"/"cancelled"/"completed"/"deleted"), tournament state (0/1/2 magic numbers), permission types ("normal"), ship classes — all use raw strings/ints instead of enums
- Files: `backend/game-app/src/main/kotlin/com/openlogh/controller/AuctionController.kt`, `TournamentController.kt`, various entities
- Impact: Typos in status transitions silently fail; no exhaustive when() matching
- Fix approach: Create Kotlin enum classes for all status/type fields; use `@Enumerated(EnumType.STRING)` in entities

**AdminController Repetitive Exception Handling:**
- Issue: Every endpoint in AdminController has identical `catch (_: AccessDeniedException) { ResponseEntity.status(403)... }` blocks (14 repetitions)
- Files: `backend/game-app/src/main/kotlin/com/openlogh/controller/AdminController.kt` (lines 38, 53, 64, 80, 91, 105, 116, 133, 143, 157, 175, 194, 209, 237)
- Impact: Boilerplate bloat; inconsistent if one catch block is accidentally modified
- Fix approach: Use `@ExceptionHandler` in controller or `@ControllerAdvice` for AccessDeniedException

---

*Concerns audit: 2026-03-31*
