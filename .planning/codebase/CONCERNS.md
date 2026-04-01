# Codebase Concerns

**Analysis Date:** 2026-03-31

## Tech Debt

**Incomplete Turn Engine Steps (TurnService):**
- Issue: Multiple turn-processing methods are stubbed out with TODO comments and empty bodies
- Files: `backend/game-app/src/main/kotlin/com/opensam/engine/TurnService.kt` (lines 992-1086)
- Specific stubs:
  - `updateOnline()` -- per-tick online count snapshot (line 994)
  - `checkOverhead()` -- runaway process guard (line 1003)
  - `checkWander()` -- wander nation dissolution after 2 years (line 1012)
  - `updateGeneralNumber()` -- refresh nation static info (line 1084)
- Impact: Game logic deviates from legacy parity. Wander nations never dissolve; online counts are never tracked per-tick; no overhead protection exists.
- Fix approach: Implement each stub following the legacy PHP parity references in `legacy-core/hwe/func.php` and `legacy-core/src/daemon.ts`.

**Incomplete Battle Special Modifiers (SpecialModifiers):**
- Issue: 14+ war special abilities have TODO comments indicating missing WarUnitTrigger implementations. Only stat modifiers (`onCalcStat`/`onCalcOpposeStat`) are implemented; runtime battle-phase triggers are not.
- Files: `backend/game-app/src/main/kotlin/com/opensam/engine/modifier/SpecialModifiers.kt` (lines 301-431)
- Missing triggers include: `che_반계시도/발동`, `che_돌격지속`, `che_부상무효`, `che_위압시도/발동`, `che_저격시도/발동`, `che_필살강화_회피불가`, `che_도시치료`, `che_전투치료시도/발동`, `che_격노시도/발동`
- Impact: Battle outcomes differ from legacy. Special abilities like 위압, 저격, 의술, 격노 are effectively no-ops during actual combat phases.
- Fix approach: Implement a `WarUnitTrigger` system that hooks into battle phases in `BattleEngine` and `BattleTrigger`.

**Hardcoded Values in SpecialModifiers:**
- Issue: `che_무쌍` modifier hardcodes `killnum = 0.0` (line 380) instead of reading from runtime rank data. This makes the 무쌍 warPower multiplier always baseline.
- Files: `backend/game-app/src/main/kotlin/com/opensam/engine/modifier/SpecialModifiers.kt` (line 378-380)
- Impact: 무쌍 special ability scaling is broken -- it never benefits from actual kill counts.
- Fix approach: Pass `RankColumn.killnum` through `StatContext` so modifiers can access runtime rank data.

**Command Parity Gap:**
- Issue: Legacy has 55 general commands + 38 nation commands = 93 total. Current implementation has 60 general + 43 nation command files, but some may be partial implementations.
- Files: `backend/game-app/src/main/kotlin/com/opensam/command/general/` (60 files), `backend/game-app/src/main/kotlin/com/opensam/command/nation/` (43 files)
- Impact: Some commands may not match legacy behavior exactly. Each difference is a potential gameplay inconsistency.
- Fix approach: Run `verify-command-parity` skill systematically. Cross-reference each command against `legacy-core/hwe/sammo/Command/General/` and `legacy-core/hwe/sammo/Command/Nation/`.

**Duplicate AuthService Implementations:**
- Issue: Two separate `AuthService` classes exist -- one in gateway-app, one in game-app -- with overlapping responsibilities (login, registration, JWT generation, OTP handling).
- Files: `backend/gateway-app/src/main/kotlin/com/opensam/gateway/service/AuthService.kt` (829 lines), `backend/game-app/src/main/kotlin/com/opensam/service/AuthService.kt` (605 lines)
- Impact: Bug fixes or security patches must be applied in two places. Role determination logic (`grade >= 5 -> "ADMIN"`) is duplicated at line 563 (gateway) and line 348 (game-app).
- Fix approach: Extract shared auth logic into the `shared` module. The game-app should validate JWT tokens forwarded by the gateway, not re-implement auth flows.

**Non-deterministic RNG in Game Logic:**
- Issue: `java.util.Random()` (unseeded) is used in `TurnService.registerAuction()` and `GeneralTrigger`, making auction registration and trigger outcomes non-reproducible across server restarts or replays.
- Files: `backend/game-app/src/main/kotlin/com/opensam/engine/TurnService.kt` (line 1050), `backend/game-app/src/main/kotlin/com/opensam/engine/trigger/GeneralTrigger.kt` (line 200)
- Impact: Auction amounts and trigger outcomes are non-deterministic. This breaks replay-ability and makes debugging turn outcomes difficult. The codebase already has `DeterministicRng` / `LiteHashDRBG` used by GeneralAI.
- Fix approach: Replace `java.util.Random()` with `DeterministicRng.create(...)` seeded from world state, consistent with the pattern in `GeneralAI.kt`.

## Security Considerations

**Internal Endpoints Exposed Without Authentication:**
- Risk: Both gateway-app and game-app mark `/internal/**` as `permitAll()`. Internal endpoints include route management (`/internal/worlds`), process orchestration (`/internal/process`), and health checks (`/internal/health`).
- Files: `backend/game-app/src/main/kotlin/com/opensam/config/SecurityConfig.kt` (line 32), `backend/gateway-app/src/main/kotlin/com/opensam/gateway/config/SecurityConfig.kt` (line 30)
- Controllers: `backend/gateway-app/src/main/kotlin/com/opensam/gateway/controller/RouteRegistryController.kt`, `backend/gateway-app/src/main/kotlin/com/opensam/gateway/controller/ProcessOrchestratorController.kt`
- Current mitigation: These are intended for inter-service communication. If both services run on the same host or private network, external access may be blocked at the infrastructure level.
- Recommendations: Add shared-secret or IP-whitelist authentication for internal endpoints. A malicious actor with network access could attach/detach worlds or manipulate process orchestration.

**CORS Wildcard Configuration:**
- Risk: Both gateway-app and game-app set `allowedOriginPatterns = listOf("*")` with `allowCredentials = true`. This allows any origin to make authenticated requests.
- Files: `backend/game-app/src/main/kotlin/com/opensam/config/SecurityConfig.kt` (line 48), `backend/gateway-app/src/main/kotlin/com/opensam/gateway/config/SecurityConfig.kt` (line 50)
- Current mitigation: JWT tokens are required for authenticated endpoints, so CSRF is less of a concern with stateless auth. However, wildcard CORS + credentials is flagged by security scanners.
- Recommendations: Restrict `allowedOriginPatterns` to the actual frontend domain(s) in production.

**WebSocket Endpoint Has No Authentication:**
- Risk: The STOMP WebSocket endpoint at `/ws` is marked `permitAll()` and has `setAllowedOriginPatterns("*")`. There is no JWT validation on WebSocket connections. Any client can subscribe to `/topic/*` or `/queue/*` channels.
- Files: `backend/game-app/src/main/kotlin/com/opensam/config/WebSocketConfig.kt` (line 19), `backend/game-app/src/main/kotlin/com/opensam/config/SecurityConfig.kt` (line 33)
- Current mitigation: None. Game events are broadcast to all subscribers without authentication.
- Recommendations: Add a `ChannelInterceptor` that validates JWT from STOMP CONNECT headers. Filter subscription topics by user's world/nation membership.

**JWT Token Stored in localStorage:**
- Risk: JWT tokens are stored in `localStorage` on the frontend, making them vulnerable to XSS attacks.
- Files: `frontend/src/stores/authStore.ts` (line 82), `frontend/src/lib/api.ts` (line 11)
- Current mitigation: None specific.
- Recommendations: Consider `httpOnly` cookies for JWT storage. If localStorage is kept, ensure robust XSS prevention (see below).

**XSS via dangerouslySetInnerHTML:**
- Risk: Multiple frontend components render server-provided HTML without sanitization using `dangerouslySetInnerHTML`. If any of these values contain user-generated content, XSS is possible.
- Files:
  - `frontend/src/app/(lobby)/lobby/page.tsx` (lines 218, 369-370) -- server notices
  - `frontend/src/components/game/game-dashboard.tsx` (line 308) -- nation notice
  - `frontend/src/app/(lobby)/lobby/join/page.tsx` (line 643) -- inheritance special info
  - `frontend/src/app/(game)/internal-affairs/page.tsx` (lines 48, 57, 65) -- innerHTML direct assignment
  - `frontend/src/app/(game)/my-page/page.tsx` (lines 869, 1226, 1316-1317) -- custom CSS injection, log messages, record payload content
  - `frontend/src/app/(game)/nation-betting/page.tsx` (line 254) -- candidate info
- Current mitigation: No sanitization library (DOMPurify or similar) is present in `package.json`.
- Recommendations: Add DOMPurify and sanitize all `dangerouslySetInnerHTML` inputs. The custom CSS injection on my-page line 869 is especially dangerous -- a `<style>` tag with user content allows CSS-based data exfiltration.

**Admin Endpoints Use `authenticated()` Not `hasRole('ADMIN')`:**
- Risk: The game-app SecurityConfig (line 35) marks `/api/admin/**` as `.authenticated()`, meaning any logged-in user can reach admin controller endpoints. Authorization is checked manually via `AdminAuthorizationService` in each controller method.
- Files: `backend/game-app/src/main/kotlin/com/opensam/config/SecurityConfig.kt` (line 35), `backend/game-app/src/main/kotlin/com/opensam/controller/AdminController.kt`
- Current mitigation: `AdminAuthorizationService` performs grade/permission checks. This is functional but error-prone -- a new admin endpoint that forgets to call `adminAuthorizationService` would be accessible to all users.
- Recommendations: Add `.requestMatchers("/api/admin/**").hasRole("ADMIN")` in SecurityConfig as a defense-in-depth layer.

**No Rate Limiting:**
- Risk: No rate limiting exists on any endpoint -- login, registration, API calls, or WebSocket connections. Brute-force attacks on login, resource-exhaustion on game APIs, and WebSocket flooding are all possible.
- Files: No rate limiting configuration found anywhere in the backend.
- Recommendations: Add Spring Boot rate limiting (e.g., `bucket4j-spring-boot-starter` or a custom filter) at minimum for `/api/auth/**` endpoints.

## Performance Bottlenecks

**GeneralAI Loading All Entities Per Decision:**
- Problem: `GeneralAI.decideAndExecute()` loads ALL cities, ALL generals, ALL nations, and ALL diplomacies for the entire world on every single NPC decision call.
- Files: `backend/game-app/src/main/kotlin/com/opensam/engine/ai/GeneralAI.kt` (lines 74-77)
- Cause: `ports.allCities()`, `ports.allGenerals()`, `ports.allNations()`, `ports.allDiplomacies()` are called per-general, per-turn. With hundreds of NPC generals, this means hundreds of full-table loads per turn.
- Improvement path: Cache world state snapshots per-turn so all NPC decisions in the same turn share the same loaded data. The `WorldPortFactory`/CQRS layer already provides a snapshot abstraction -- ensure it caches within a turn cycle.

**No Pagination on Repository Queries:**
- Problem: Most repository methods return `List<T>` without pagination. As game history grows, queries like `findByWorldId`, message queries, and record queries will return unbounded result sets.
- Files: `backend/game-app/src/main/kotlin/com/opensam/repository/MessageRepository.kt`, `backend/game-app/src/main/kotlin/com/opensam/repository/GeneralRepository.kt`, `backend/game-app/src/main/kotlin/com/opensam/repository/AuctionRepository.kt`, and others
- Cause: Spring Data JPA `findBy*` methods without `Pageable` parameter.
- Improvement path: Add pagination to message, auction history, record, and log queries. Frontend already shows paginated views; backend should limit result sets.

**Large File Complexity:**
- Problem: Several files exceed 800 lines, concentrating too much logic.
- Files:
  - `backend/game-app/src/main/kotlin/com/opensam/engine/ai/GeneralAI.kt` (3,741 lines)
  - `backend/game-app/src/main/kotlin/com/opensam/command/constraint/ConstraintHelper.kt` (1,362 lines)
  - `backend/game-app/src/main/kotlin/com/opensam/engine/TurnService.kt` (1,244 lines)
  - `backend/game-app/src/main/kotlin/com/opensam/service/ScenarioService.kt` (1,130 lines)
  - `backend/game-app/src/main/kotlin/com/opensam/engine/war/BattleTrigger.kt` (1,005 lines)
  - `frontend/src/app/(admin)/admin/page.tsx` (2,354 lines)
  - `frontend/src/app/(game)/auction/page.tsx` (1,409 lines)
  - `frontend/src/types/index.ts` (1,322 lines)
- Impact: Hard to review, test, and maintain. High merge conflict risk.
- Improvement path: Extract sub-concerns. For example, GeneralAI could be split into strategy modules (domestic, military, diplomatic). Admin page could be split into sub-components.

## Fragile Areas

**Broad Exception Swallowing:**
- Files: Multiple locations across the backend
- Why fragile: ~30 instances of `catch (_: Exception)` or `catch (e: Exception)` that silently swallow errors. Notable cases:
  - `backend/game-app/src/main/kotlin/com/opensam/command/CommandResultApplicator.kt` (line 50)
  - `backend/game-app/src/main/kotlin/com/opensam/command/CommandServices.kt` (line 32)
  - `backend/game-app/src/main/kotlin/com/opensam/command/CommandExecutor.kt` (line 422)
  - `backend/game-app/src/main/kotlin/com/opensam/service/ScenarioService.kt` (lines 140, 347, 546, 1113)
  - `backend/game-app/src/main/kotlin/com/opensam/controller/DiplomacyController.kt` (line 39)
  - `backend/game-app/src/main/kotlin/com/opensam/service/MapRecentService.kt` (line 84)
  - `backend/gateway-app/src/main/kotlin/com/opensam/gateway/orchestrator/GameContainerOrchestrator.kt` (5 catch blocks)
- Safe modification: Add structured logging (`logger.warn/error`) to all catch blocks before suppressing. Replace blanket `Exception` catches with specific exception types where possible.
- Test coverage: Most catch blocks are not tested because tests don't simulate failure paths.

**ScenarioService Silent Map Fallback:**
- Files: `backend/game-app/src/main/kotlin/com/opensam/service/ScenarioService.kt` (lines 140, 347)
- Why fragile: If a scenario's map fails to load, it silently falls back to the "che" map: `try { mapService.getCities(mapName) } catch (_: Exception) { mapService.getCities("che") }`. A misconfigured scenario would silently use the wrong map with no error indication.
- Safe modification: Log a warning when fallback occurs. Consider failing loudly during scenario setup.

**GlobalExceptionHandler Only in game-app:**
- Files: `backend/game-app/src/main/kotlin/com/opensam/config/GlobalExceptionHandler.kt`
- Why fragile: The gateway-app has no `@RestControllerAdvice`. Unhandled exceptions in gateway controllers produce Spring Boot's default error response (HTML or generic JSON), which may leak stack traces.
- Fix: Add a matching `GlobalExceptionHandler` to gateway-app.

**No Frontend Error Boundaries:**
- Files: No `error.tsx` files exist in `frontend/src/app/`
- Why fragile: Any unhandled React error crashes the entire page. Next.js App Router supports `error.tsx` at route group levels to provide graceful error recovery.
- Fix: Add `error.tsx` files at minimum to `frontend/src/app/(game)/`, `frontend/src/app/(lobby)/`, and `frontend/src/app/(admin)/`.

## Test Coverage Gaps

**Backend: 99 test files vs 472 source files (21% file-level ratio):**
- What's not tested: Many service classes, controllers, and individual commands lack dedicated test files. Entity/repository layer has no tests.
- Files: `backend/game-app/src/test/kotlin/com/opensam/` (test root)
- Risk: Regressions in game logic, especially in the complex battle engine and economy service, may go unnoticed.
- Priority: High -- particularly for `BattleEngine.kt` (881 lines), `BattleTrigger.kt` (1,005 lines), `EconomyService.kt` (877 lines), and `DiplomacyService.kt` (590 lines).

**Frontend: 56 test files vs 211 source files (27% file-level ratio):**
- What's not tested: Most game page components (auction, battle, betting, chief, diplomacy, tournament, etc.) have no tests. The `gameApi.ts` (893 lines) has no tests.
- Files: `frontend/src/app/(game)/` -- many pages without corresponding `.test.ts`
- Risk: UI regressions and broken API integrations may go unnoticed.
- Priority: Medium -- focus on critical paths: command execution flow, game dashboard, and auth flows.

**No Integration Tests:**
- What's not tested: No tests verify the full request cycle (HTTP request -> controller -> service -> repository -> database). Tests use H2 in-memory DB but are unit-level with mocks.
- Risk: SQL/JPA mapping issues, transaction boundaries, and query performance problems surface only in production.
- Priority: Medium

## Missing Critical Features

**No Frontend Error Recovery:**
- Problem: No `error.tsx` files, no error boundaries. The 401 interceptor in `api.ts` redirects to `/login` but other errors (500, network failures) show raw error states or blank screens.
- Blocks: Production readiness. Users see broken states on transient errors.

**No Audit Logging:**
- Problem: Admin actions (settings changes, general blocking, world reset, user management) are not audit-logged.
- Files: `backend/game-app/src/main/kotlin/com/opensam/controller/AdminController.kt`, `backend/gateway-app/src/main/kotlin/com/opensam/gateway/controller/AdminUsersController.kt`
- Blocks: Accountability for admin actions. Investigation of admin abuse requires guessing from application logs.

**Gateway Has No Input Validation on Proxy Requests:**
- Problem: `GameProxyController` and `AdminGameProxyController` forward raw request bodies as `ByteArray` without any validation or size limits.
- Files: `backend/gateway-app/src/main/kotlin/com/opensam/gateway/controller/GameProxyController.kt`, `backend/gateway-app/src/main/kotlin/com/opensam/gateway/controller/AdminGameProxyController.kt`
- Blocks: The gateway is vulnerable to large-payload attacks. A malicious user could send multi-GB request bodies that get forwarded to game-app.

## Dependencies at Risk

**Spring Boot 3.4.2 (January 2025):**
- Risk: Not the latest patch release. Spring Boot 3.4.x is maintained but should be kept current for security fixes.
- Files: `backend/build.gradle.kts` (line 4)
- Impact: Potential unpatched vulnerabilities.
- Migration plan: Update to latest 3.4.x patch release regularly.

**Next.js 16.1.6:**
- Risk: Next.js 16 is very recent (2026). Ensure compatibility with all dependencies, especially React 19.
- Files: `frontend/package.json` (line 44)
- Impact: Ecosystem libraries may not yet fully support Next.js 16.
- Migration plan: Monitor for breaking changes and dependency compatibility.

**sockjs-client 1.6.1:**
- Risk: SockJS is in maintenance mode. The WebSocket ecosystem is moving toward native WebSocket or alternatives.
- Files: `frontend/package.json` (line 53)
- Impact: May not receive security updates.
- Migration plan: Consider migrating to native WebSocket with a reconnection library, or use the STOMP client's native WebSocket support.

---

*Concerns audit: 2026-03-31*
