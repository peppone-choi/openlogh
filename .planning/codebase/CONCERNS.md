# Codebase Concerns

**Analysis Date:** 2026-03-28

## Tech Debt

**Unimplemented Feature Endpoints:**

- Issue: 18 TODO markers indicate stub endpoints with no implementation
- Files:
    - `backend/game-app/src/main/kotlin/com/openlogh/controller/SovereignController.kt` (sovereignAction endpoint)
    - `backend/game-app/src/main/kotlin/com/openlogh/controller/TournamentController.kt` (validateOfficer, advancePhase, broadcastMessage)
    - `backend/game-app/src/main/kotlin/com/openlogh/controller/DiplomacyController.kt` (diplomacy response)
    - `backend/game-app/src/main/kotlin/com/openlogh/controller/AuctionController.kt` (dynamic pricing, buy/sell logic, item auction)
    - `backend/game-app/src/main/kotlin/com/openlogh/controller/InheritanceController.kt` (inheritance info, city set, stat reset, buff purchase, unique item auction)
- Impact: Game features (tournament, sovereign actions, auctions, inheritance) respond with dummy data; players can't interact with these systems
- Fix approach: Implement business logic for each endpoint, add validation and transaction handling

**Tactical Battle Engine Hardcoded Values:**

- Issue: TODO markers in `TacticalBattleEngine.kt` indicate placeholder values for planet defense stats
- Files: `backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/TacticalBattleEngine.kt` (lines with orbitalDefense = 100.0, garrisonStrength = 100.0)
- Impact: Tactical battles don't use actual planet defense data; all planets treated equally regardless of fortification
- Fix approach: Wire up Planet entity fields to tactical battle calculations

**Type Safety in ScenarioService:**

- Issue: Heavy use of `@Suppress("UNCHECKED_CAST")` in tests and `ArrayList<Any>` in scenario data parsing
- Files: `backend/game-app/src/test/kotlin/com/openlogh/service/ScenarioServiceTest.kt` (16 suppressions), `backend/game-app/src/main/kotlin/com/openlogh/service/ScenarioService.kt`
- Impact: Scenario data deserialization lacks type safety; runtime errors possible if JSON schema changes
- Fix approach: Create strongly-typed ScenarioDTO classes, use Jackson deserialization to Kotlin data classes instead of ArrayList<Any>

## Threading & Concurrency Issues

**Unmanaged Thread Executor in WebSocket Controller:**

- Issue: Raw thread pool creation without lifecycle management
- Files: `backend/game-app/src/main/kotlin/com/openlogh/websocket/TacticalWebSocketController.kt`
- Code pattern: `java.util.concurrent.Executors.newSingleThreadScheduledExecutor().schedule({ }, 30, TimeUnit.SECONDS)`
- Impact: Executor threads never shut down; memory leak on app restart or active connection cycling
- Fix approach: Use Spring `@Bean` to create managed `ScheduledExecutorService`, inject via dependency injection, or use Spring's `@Async`

**Synchronized Collections in TacticalGameSession:**

- Issue: Manual synchronization with `Collections.synchronizedSet()` instead of modern concurrent collections
- Files: `backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/TacticalGameSession.kt`
- Code: `val joinedOfficers: MutableSet<Long> = Collections.synchronizedSet(mutableSetOf())`
- Impact: Thread-safe but verbose; mixing `synchronizedSet` with `ConcurrentHashMap` in same class indicates inconsistent concurrency model
- Fix approach: Standardize on `ConcurrentHashMap`/`CopyOnWriteArraySet` across tactical session management

## Missing Authorization Checks

**Auction System Missing Seller Verification:**

- Issue: Auction creation doesn't validate that bid originator is the seller or that amounts exceed previous bids
- Files: `backend/game-app/src/main/kotlin/com/openlogh/controller/AuctionController.kt` (create, bid endpoints)
- Impact: Players can create auctions for other officers; players can be outbid but don't know bid history
- Fix approach: Add `@Transactional` with isolation level, verify bid > previous bid, add auditing, return bid history

**Inheritance System Authorization Gap:**

- Issue: InheritanceController has TODO for "implement owner check" on inheritance purchases
- Files: `backend/game-app/src/main/kotlin/com/openlogh/controller/InheritanceController.kt`
- Impact: Unverified inheritance could allow players to inherit stats/items from other accounts
- Fix approach: Implement permission checks, validate session-level ownership, add audit trail

## Performance Bottlenecks

**ScenarioService Lazy Reference Data:**

- Issue: `referencePictureByName` lazy map loads all scenarios on first access; blocking operation
- Files: `backend/game-app/src/main/kotlin/com/openlogh/service/ScenarioService.kt` (lines 41-56)
- Impact: First officer portrait lookup triggers full scenario load; delays initial UI painting
- Fix approach: Preload scenario index at service startup, use indexed lookup map, cache reference data separately

**Battle Aftermath Complexity:**

- Issue: WarAftermath handling performs diplomacy deltas + tech updates + conquest consequences in single transaction
- Files: `backend/game-app/src/main/kotlin/com/openlogh/engine/war/WarAftermath.kt`
- Impact: N-faction battles can cause transaction lock contention; long-running aftermath blocks turn processing
- Fix approach: Split into async event handlers, use event sourcing for consequence chain

**Large File Sizes:**

- 7 Kotlin files exceed 700 lines, largest being:
    - `ScenarioService.kt` (1127 lines) - scenario data initialization
    - `TacticalBattleEngine.kt` (774 lines) - real-time battle resolution
    - `OperationsCommands.kt` (735 lines) - command implementations
    - `Constraints.kt` (640 lines) - command validation rules
- Impact: High cognitive load; difficult to test individual features; increased merge conflict risk
- Fix approach: Extract specialized handlers (e.g., TacticalBattleResolver, OperationsCommandBuilder) into separate classes

## Test Coverage Gaps

**Scenario JSON Deserialization Not Type-Safe:**

- Issue: Scenario loading parses generic ArrayList<Any>; no unit tests validate schema compliance
- Files: `backend/game-app/src/main/kotlin/com/openlogh/service/ScenarioService.kt` (scenario loading logic)
- Impact: Corrupted scenario JSON silently produces incorrect game state; hard to debug
- Fix approach: Add unit tests with malformed JSON input, create validator for required fields

**Auction Edge Cases:**

- Issue: No tests for auction expiration, concurrent bids, bid rejection scenarios
- Files: `backend/game-app/src/main/kotlin/com/openlogh/controller/AuctionController.kt`
- Impact: Race condition possible if two bids arrive simultaneously; expired auctions may not close
- Fix approach: Add integration tests with multiple clients, test auction state machine transitions

**Tactical WebSocket Synchronization:**

- Issue: TacticalGameSession uses both `synchronizedSet` and `ConcurrentHashMap`; order semantics unclear
- Files: `backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/TacticalGameSession.kt`
- Impact: Race conditions between turn preparation and order submission not covered by tests
- Fix approach: Add tests for concurrent order submission during prepareNextTurn()

## Security Considerations

**No Rate Limiting on Auctions:**

- Risk: Auction endpoint allows unlimited bid submissions per client; spam attack vector
- Files: `backend/game-app/src/main/kotlin/com/openlogh/controller/AuctionController.kt` (bid endpoint)
- Current mitigation: None detected
- Recommendations: Add `@RateLimiter` or Spring Cloud Gateway rate limit filter; validate bid timestamp >= last bid + min_interval

**Session ID as String in Sovereign Lookup:**

- Risk: Line 15 in SovereignController converts Long sessionId to String for comparison; potential type confusion
- Files: `backend/game-app/src/main/kotlin/com/openlogh/controller/SovereignController.kt`
- Current mitigation: findAll() + filter (inefficient but safe)
- Recommendations: Use repository query method `findBySessionId(sessionId: Long)` instead of string conversion

**Sensitive Data in Scenario Reference Cache:**

- Risk: referencePictureByName loads all officer names into memory; admin info exposure if cache dumped
- Files: `backend/game-app/src/main/kotlin/com/openlogh/service/ScenarioService.kt`
- Current mitigation: Lazy load, memory-only
- Recommendations: Consider paginated access, audit access logs

## Architecture Fragility

**Scenario Data Initialization Brittleness:**

- Issue: initializeWorld() loads city data from MapService; falls back to "logh" map silently if load fails
- Files: `backend/game-app/src/main/kotlin/com/openlogh/service/ScenarioService.kt` (lines 140)
- Pattern: `val mapCities = try { mapService.getCities(mapName) } catch (_: Exception) { mapService.getCities("logh") }`
- Impact: Wrong map loaded without error logging; duplicate world initialization possible if try/catch swallows real errors
- Safe modification: Log failed map load, fail fast if primary map missing, add map validation
- Test coverage: Missing test for map load failure scenario

**Type Coercion in Officer Rankings:**

- Issue: Officer dex values use 26-threshold array lookup; thresholds are magic numbers with no documentation
- Files: `backend/game-app/src/main/kotlin/com/openlogh/engine/war/WarFormula.kt`
- Impact: Changing thresholds breaks all officer skill calculations; no validation that thresholds are monotonic
- Safe modification: Extract DEX_THRESHOLDS to named constant with comment explaining 350→1375→3500 scaling logic

**Mutable JSONB in Entities:**

- Issue: Officer.meta, Planet.meta, Planet.conflict use MutableMap<String, Any> without schema documentation
- Files: `backend/game-app/src/main/kotlin/com/openlogh/entity/Officer.kt`, Planet.kt
- Impact: Hard to trace what's stored in meta/conflict; unsafe concurrent mutations; no migration path if schema changes
- Safe modification: Create @Embeddable data classes for each meta field, document expected JSON shape
- Test coverage: No schema validation tests

## Known Bugs

**Sovereign Action Endpoint Accepts All Requests:**

- Symptoms: POST to /sovereign/action always returns {"success": true}
- Files: `backend/game-app/src/main/kotlin/com/openlogh/controller/SovereignController.kt`
- Trigger: Any POST to sovereign/action with any body
- Workaround: None; feature not implemented

**Tournament Endpoints Return Dummy Data:**

- Symptoms: Tournament bracket doesn't populate; no phase transitions occur
- Files: `backend/game-app/src/main/kotlin/com/openlogh/controller/TournamentController.kt`
- Trigger: Post-tournament scenario start
- Workaround: Admin directly updates tournament state via database

**Inheritance System Allows All Purchases:**

- Symptoms: Players can purchase inheritance stats for other players if ID known
- Files: `backend/game-app/src/main/kotlin/com/openlogh/controller/InheritanceController.kt`
- Trigger: POST /worlds/{id}/inheritance/buff-purchase with another user's officer ID
- Workaround: None; requires front-end to prevent invalid UI states

## Scaling Limits

**Scenario Load on Startup:**

- Current: Single synchronous load of all scenarios + reference picture map on first request
- Limit: Blocks turn daemon for >1s if scenarios >100MB
- Scaling path: Move scenario loading to async job queue, cache in Redis, precompile scenario index

**Tactical Battle Session Memory:**

- Current: All active tactical sessions held in TacticalSessionManager.sessions ConcurrentHashMap
- Limit: 100+ concurrent battles = >500MB heap if each battle stores full fleet state + turn history
- Scaling path: Persist inactive battles to database, implement session eviction policy, use off-heap storage

**Auction Bid History:**

- Current: All bids loaded in AuctionBidRepository.findByAuctionId(); no pagination
- Limit: Auctions with >10k bids cause OOM
- Scaling path: Implement bid summary (count, min/max) instead of full history fetch; paginate bid details

## Dependencies at Risk

**Jackson Unsafe Deserialization:**

- Risk: `objectMapper.readValue<ArrayList<Any>>` in ScenarioService can instantiate arbitrary classes if JSON includes @type
- Impact: Remote code execution if scenario JSON is attacker-controlled
- Migration plan: Use `objectMapper.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)`, restrict to approved scenario directory with file validation

**Spring Data JPA N+1 Query Pattern:**

- Risk: findAll() in SovereignController loads all sovereign records, then filters in memory
- Impact: Query time O(n) instead of O(1); performance degrades with faction count
- Migration plan: Replace with repository query method, use `@Query` with WHERE clause

**Kotlin Suppress Annotations Accumulation:**

- Risk: 16+ `@Suppress("UNCHECKED_CAST")` annotations hide real type safety issues
- Impact: Next refactoring may introduce silent bugs
- Migration plan: Create typed ScenarioDTO classes, remove all suppressions with actual type information

## Missing Critical Features

**Sovereign Action System Incomplete:**

- Problem: No implementation for sovereign faction-wide actions
- Blocks: Faction leaders can't issue commands; diplomacy responses don't persist
- Test coverage: No tests for sovereign action flow

**Auction Market Pricing Logic Missing:**

- Problem: Dynamic pricing algorithm marked TODO; auctions use fixed min price
- Blocks: Market can't respond to supply/demand
- Test coverage: No economic simulation tests

**Inheritance Purchase Validation Missing:**

- Problem: No owner checks on stat/item purchases
- Blocks: Cross-player inheritance abuse possible
- Test coverage: No authorization tests

## Database & Schema Concerns

**JSONB Columns Lack Schema Definition:**

- Issue: Officer.meta, Planet.meta, Planet.conflict are JSONB without documented structure
- Files: `entity/Officer.kt`, `entity/Planet.kt`
- Risk: Queries on JSON fields are expensive; schema changes require data migration
- Recommendation: Create separate tables for meta fields OR document JSONB schema with examples

**Foreign Key Cascade Behavior Unclear:**

- Issue: Officer→Faction FK, Fleet→Officer FK cascade behavior not documented
- Files: Entity definitions
- Risk: Faction deletion may orphan fleets; batch operations may fail
- Recommendation: Add `@OnDelete` annotations, add cascade tests

## Code Quality Issues

**Suppress Annotations Hide Type Safety:**

- Issue: ScenarioServiceTest has 16 `@Suppress("UNCHECKED_CAST")` for ArrayList<Any> parsing
- Files: `backend/game-app/src/test/kotlin/com/openlogh/service/ScenarioServiceTest.kt`
- Impact: Refactoring unsafe; compiler warnings disabled without addressing root cause
- Fix approach: Create ScenarioDTO classes with Jackson deserialization instead of raw ArrayList

**Magic Numbers in Battle Calculations:**

- Issue: WarFormula.kt uses hardcoded thresholds [350, 1375, 3500, 7125, ...] for DEX calculation with no explanation
- Files: `backend/game-app/src/main/kotlin/com/openlogh/engine/war/WarFormula.kt`
- Impact: Impossible to understand or modify skill scaling; no constants defined
- Fix approach: Extract to named enum or configuration, add docstring explaining progression

**Inconsistent Error Handling:**

- Issue: AuctionController checks auction.status != "open" but doesn't validate status values are only "open"/"closed"/"deleted"
- Files: `backend/game-app/src/main/kotlin/com/openlogh/controller/AuctionController.kt`
- Impact: Typos in status transitions silently fail
- Fix approach: Create Auction.Status enum, use for all status checks

---

_Concerns audit: 2026-03-28_
