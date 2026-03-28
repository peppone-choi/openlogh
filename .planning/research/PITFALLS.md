# Domain Pitfalls

**Domain:** Multiplayer online strategy game — Spring Boot 3 (Kotlin) + WebSocket + PostgreSQL 16
**Project:** Open LOGH (오픈 은하영웅전설)
**Researched:** 2026-03-28
**Confidence:** HIGH (grounded in actual codebase + verified sources)

---

## Critical Pitfalls

Mistakes that cause rewrites, data corruption, or unrecoverable game state.

---

### Pitfall 1: CP Consumption Is Not Atomic — Race Condition Exploit

**What goes wrong:**
`CommandPointService.consume()` reads `officer.pcp`/`officer.mcp`, checks sufficiency, then mutates in-memory. If two HTTP requests arrive simultaneously for the same officer (e.g., player double-clicks or sends parallel requests), both check-then-act sequences pass the balance check before either commits, allowing the officer to execute two commands for the cost of one — or drive CP below zero.

**Why it happens:**
The current `consume()` method is a pure in-memory operation on a JPA entity. Without `@Version` optimistic locking or a `SELECT ... FOR UPDATE` on the officer row, two concurrent transactions read the same stale CP value.

**Codebase location:**
`CommandPointService.kt` — `consumePcp()` / `consumeMcp()` — no `@Version` on `Officer` entity, no pessimistic lock.

**Consequences:**

- Players farm infinite commands by rapid-firing requests
- CP-based economy (PCP/MCP substitution chain) collapses
- Exploit is deterministic and easily scripted

**Prevention:**
Add `@Version val version: Long = 0` to `Officer` entity. Catch `ObjectOptimisticLockingFailureException` in the command controller and return HTTP 409 with "retry." Alternatively, use `SELECT ... FOR UPDATE` via `@Lock(LockModeType.PESSIMISTIC_WRITE)` on the officer fetch inside `@Transactional`.

**Detection (warning sign):**
Officer CP values go negative in the database. Players executing more commands per real-time minute than CP recovery rate allows.

**Phase to address:** Early — before any command system goes to production.

---

### Pitfall 2: Turn Engine Does Not Exist as a Scheduled Service — The "Missing Heartbeat"

**What goes wrong:**
There is no `TurnEngine.kt` in the codebase. The strategic game layer (CP recovery, turn-based state progression, NPC AI tick) has no scheduled driver. `CommandPointService.recoverAllCp(sessionId)` exists but has no caller. Without an authoritative tick loop, the game clock never advances, resources never recover, and NPC officers never act.

**Why it happens:**
The tactical battle engine (WebSocket) was built first. The strategic turn engine — which is the core gameplay loop — was deferred. This is a common sequencing mistake: RTS combat is visible and testable, but the turn-based layer that drives everything else is invisible until it's missing.

**Consequences:**

- Game is unplayable: players execute commands but the world never updates
- All time-based systems (CP recovery, fleet movement ETAs, cooldowns, age/growth) are dead
- NPC factions never take actions, so the game has no AI opposition

**Prevention:**
Implement a dedicated `StrategicTurnService` (or `TurnDaemon`) early. Use Spring's `TaskScheduler` (not bare `@Scheduled`) driven by a configurable tick interval. The tick must:

1. Advance game time (24x real-time: 1 real minute = 24 game minutes)
2. Fire CP recovery for all active sessions
3. Process queued strategic commands
4. Run NPC AI decisions
5. Evaluate victory conditions

Use ShedLock (with Redis) to prevent duplicate tick execution if game-app ever runs as multiple instances.

**Detection (warning sign):**
`recoverAllCp()` has no callers. No `@Scheduled` or `TaskScheduler` bean wires to a strategic turn loop. Search: `grep -r "recoverAllCp\|TurnDaemon\|advanceTurn" backend/` returns empty.

**Phase to address:** Milestone 1 / Phase 1 — foundational; nothing else works without it.

---

### Pitfall 3: Unmanaged Executor Leak in Tactical Session Cleanup

**What goes wrong:**
`TacticalWebSocketController.processTurn()` (line 218) creates a new `Executors.newSingleThreadScheduledExecutor()` for every battle that ends, schedules a 30-second cleanup task, then discards the reference. The executor thread is never shut down.

**Why it happens:**
Convenience — a quick inline delay without wiring a managed bean. With 100+ concurrent battles this leaks 100+ daemon threads and their associated memory.

**Codebase location:**
`TacticalWebSocketController.kt` line 218: `java.util.concurrent.Executors.newSingleThreadScheduledExecutor().schedule({...}, 30, TimeUnit.SECONDS)`

**Consequences:**

- Thread pool leak: each completed battle leaves a live thread until JVM GC (which never collects live threads)
- At 2,000-player sessions with frequent battles: hundreds of leaked threads → OOM or thread exhaustion
- App restart required to recover

**Prevention:**
Inject a Spring-managed `@Bean TaskScheduler` (e.g., `ThreadPoolTaskScheduler` with pool size 4) into `TacticalWebSocketController`. Replace the inline executor with `taskScheduler.schedule({ sessionManager.destroySession(sessionCode) }, Instant.now().plusSeconds(30))`. The scheduler shuts down cleanly with the application context.

**Detection (warning sign):**
JVM thread count grows monotonically. Each battle end adds ~1 thread that never decreases. Visible in JConsole/Actuator `/actuator/metrics/jvm.threads.live`.

**Phase to address:** Before tactical combat goes to production load testing.

---

### Pitfall 4: Inconsistent Concurrency Model in TacticalGameSession

**What goes wrong:**
`TacticalGameSession` mixes `Collections.synchronizedSet()` for `joinedOfficers`/`readyOfficers` with `ConcurrentHashMap` for `previousOrders`. Compound operations (e.g., `isAllReady()` then `startCombat()` in `TacticalWebSocketController.markReady()`) are not atomic. Two officers marking ready simultaneously can both pass `isAllReady()`, triggering `startCombat()` twice.

**Codebase location:**
`TacticalGameSession.kt` lines 18–20. `TacticalWebSocketController.kt` `markReady()` — no synchronization around `isAllReady()` + `startCombat()` compound check.

**Consequences:**

- Double combat start: two `processTurn()` chains run concurrently on the same `TacticalBattleSession`
- Battle state corruption: turn counter advances twice per turn, events duplicated
- Clients receive contradictory state broadcasts

**Prevention:**
Replace `synchronizedSet` with `CopyOnWriteArraySet` for read-heavy sets. Guard compound check-then-act with `synchronized(session)` blocks or a per-session `ReentrantLock`. Better: route all session mutation through a single-threaded `SessionActor` pattern — each session owns one coroutine/thread that serializes all mutations.

**Detection (warning sign):**
`currentTurn` jumps by 2 in battle logs. Duplicate events in the same turn number. Client receives two `COMBAT` phase transitions.

**Phase to address:** Before load testing tactical combat.

---

### Pitfall 5: Command Authorization Checked In-Memory Against Stale JSON — Not Against Database State

**What goes wrong:**
`CommandExecutor.executeGeneralCommand()` reads the officer's position cards from `general.meta["positionCards"]` — a JSONB field deserialized at request time. If an admin demotes the officer or strips a position card in a concurrent transaction, the in-flight command reads the stale pre-demotion card list and executes a command the officer is no longer authorized to run.

**Why it happens:**
JSONB `meta` fields are loaded once per HTTP request transaction. There is no re-validation against current DB state before the command's effects are committed.

**Codebase location:**
`CommandExecutor.kt` lines 57–68 — `heldCards` read from `general.meta["positionCards"]`.

**Consequences:**

- Privilege escalation: demoted officer executes high-rank commands (e.g., firing faction members, declaring war)
- Invisible in logs because the check passes silently on stale data
- Coup/rebellion commands executed by officers who have already been stripped of coup-related cards

**Prevention:**
Re-fetch the officer within the same `@Transactional` command execution method rather than relying on the caller-provided `General` object. Use `officerRepository.findById(general.id).orElseThrow()` with `LockModeType.PESSIMISTIC_READ` to get the current committed state. Alternatively, enforce position card changes as synchronous DB writes that invalidate any pending command queue.

**Detection (warning sign):**
Command audit logs show an officer executing commands after a rank change event in the same second. Position card system tests use only unit-level mocks, never integration tests with concurrent transactions.

**Phase to address:** During permission/organization system implementation (Phase 2–3).

---

### Pitfall 6: WarAftermath Runs as a Single Long Transaction — Turn Blocking

**What goes wrong:**
`WarAftermath.kt` processes diplomacy deltas, tech updates, and conquest consequences inside a single `@Transactional` call. For multi-faction battles, this touches officer rows, planet rows, faction rows, and potentially all fleet rows in the session — acquiring row locks across all of them simultaneously.

**Codebase location:**
`CONCERNS.md` flags: "N-faction battles can cause transaction lock contention; long-running aftermath blocks turn processing."

**Consequences:**

- Strategic turn tick stalls while aftermath completes (can be seconds at scale)
- Other players' commands queue behind the locked rows
- Under high concurrency, partial deadlocks between aftermath and concurrent command execution

**Prevention:**
Split aftermath into async event handlers using Spring's `ApplicationEventPublisher`. The battle result commits a minimal `BattleResultEvent` record; consequences (diplomat changes, tech upgrades, conquest) process asynchronously in separate short transactions. Use an outbox pattern to guarantee at-least-once delivery without blocking the main turn loop.

**Detection (warning sign):**
`pg_stat_activity` shows long-running transactions from `WarAftermath` holding locks during busy turns. Turn processing time increases proportionally to the number of active battles.

**Phase to address:** Before multi-faction warfare is enabled.

---

### Pitfall 7: Scenario Initialization Silently Falls Back to Wrong Map

**What goes wrong:**
`ScenarioService.initializeWorld()` catches map load exceptions and silently retries with the "logh" fallback map (line 140). A corrupted or missing custom map produces no error, no log warning, and initializes a world with the wrong galaxy layout. Players in a custom scenario find themselves in the default LOGH map.

**Codebase location:**
`ScenarioService.kt` line 140: `catch (_: Exception) { mapService.getCities("logh") }`

**Consequences:**

- Silent data corruption on world creation
- All 80 star systems in the wrong positions; all strategic movement calculations wrong
- Irreversible without deleting and recreating the world

**Prevention:**
Remove the silent catch. Log the error at ERROR level. Fail fast: throw `WorldInitializationException("Map '$mapName' could not be loaded")` so world creation returns HTTP 500 rather than creating a broken world. Add a pre-validation step that checks map existence before starting initialization.

**Detection (warning sign):**
World creation succeeds but the galaxy map shows default LOGH positions regardless of scenario selection.

**Phase to address:** Immediately — fix before adding any new scenarios.

---

## Moderate Pitfalls

### Pitfall 8: Spring STOMP Default Thread Pool Exhaustion at 2,000 Players

**What goes wrong:**
Spring's `clientInboundChannel` and `clientOutboundChannel` default to `2 × CPU cores` threads. For a 4-core server this is 8 threads. At 2,000 WebSocket players each sending orders every turn, the inbound channel saturates. Messages queue in `Integer.MAX_VALUE` capacity queues — creating invisible backlog, not rejection.

Additionally, Tomcat's default `maxThreads=200` means 200 simultaneous WebSocket connections exhaust the thread pool (each WebSocket holds a thread in the traditional model). Beyond ~200 concurrent WebSocket connections, new connections are refused.

**Prevention:**
Configure STOMP channel thread pools explicitly:

```kotlin
override fun configureClientInboundChannel(registration: ChannelRegistration) {
    registration.taskExecutor().corePoolSize(16).maxPoolSize(32).queueCapacity(1000)
}
```

Enable virtual threads (`spring.threads.virtual.enabled=true` on Java 21+) to eliminate the per-thread cost (~1MB per thread → ~few KB per virtual thread). Set `sendTimeLimit` and `sendBufferSizeLimit` to drop slow clients rather than buffering indefinitely.

**Detection (warning sign):**
`/actuator/metrics/spring.integration.channel.send.errors` rising. Response time degrades linearly with player count. Thread dump shows hundreds of WAITING threads on `clientInboundChannel`.

**Phase to address:** Before player capacity testing (load test at 500, 1000, 2000 players).

---

### Pitfall 9: @Scheduled CP Recovery Runs on All Sessions Simultaneously

**What goes wrong:**
When `CommandPointService.recoverAllCp(sessionId)` is eventually wired to a `@Scheduled` trigger, it will (by default) run on Spring's single-threaded scheduler. If there are 10 active sessions each with 200 players, one tick call processes 2,000 officer rows. The single scheduler thread blocks all other `@Scheduled` tasks (health checks, cleanup jobs) until it completes.

**Prevention:**
Wire CP recovery to a dedicated `ThreadPoolTaskScheduler` with at least as many threads as expected concurrent active sessions. Use `@Async` with a named executor for the per-session recovery call so sessions process in parallel. For multi-instance deployments, use ShedLock with Redis to prevent duplicate recovery runs across JVM instances.

**Detection (warning sign):**
Other `@Scheduled` tasks start firing late. Scheduler thread shows long blocking in thread dumps. Session count × officer count × DB round-trip latency > scheduler interval.

**Phase to address:** During turn engine implementation.

---

### Pitfall 10: JSONB `meta` Fields Become an Untyped Dumping Ground

**What goes wrong:**
`Officer.meta` and `Planet.meta` (JSONB) accumulate ad-hoc keys: `"positionCards"`, `"rebellionIntent"`, `"coupLeader"`, `"next_execute"`, `"coupStep"`. Each new feature adds keys without schema documentation. After 6 months, no developer can enumerate what `meta` might contain. Queries on JSONB keys require full-table scans without GIN indexes.

**Codebase location:**
`CommandExecutor.kt` reads `general.meta["next_execute"]`, `general.meta["positionCards"]`, `general.meta["rebellionIntent"]`, `general.meta["coupLeader"]` — four different feature areas sharing one map.

**Prevention:**
Create typed `@Embeddable` or separate `officer_meta` table for each logical group:

- `OfficerCommandState` (cooldowns, CP usage)
- `OfficerOrganizationState` (position cards, rank)
- `OfficerCoupState` (rebellion intent, coup role)

If JSONB is retained, add GIN index: `CREATE INDEX idx_officer_meta ON officer USING GIN(meta)` and document every key in a companion enum/constant file.

**Detection (warning sign):**
`@Suppress("UNCHECKED_CAST")` count grows. New features add new `meta[...]` keys without a schema PR. PostgreSQL `EXPLAIN ANALYZE` shows sequential scans on officer table when filtering by meta keys.

**Phase to address:** Before the organization/permission system ships (it adds many more meta keys).

---

### Pitfall 11: Tactical Battle RNG Uses `System.currentTimeMillis()` — Determinism Broken

**What goes wrong:**
`TacticalWebSocketController.processTurn()` seeds both the AI RNG and `TacticalBattleEngine` with `Random(System.currentTimeMillis() + battleSession.currentTurn)`. Two calls within the same millisecond (possible under load) produce identical seeds. More critically, replay and debugging are impossible: re-running the same inputs produces different results.

**Codebase location:**
`TacticalWebSocketController.kt` lines 177, 188.

**Consequences:**

- Irreproducible bugs in battle resolution
- Cannot write deterministic parity tests (`BattleParityTest.kt` exists but RNG diverges)
- Potential for seed collision producing identical AI behavior across different battles

**Prevention:**
Generate one seed per battle session at creation time (store in `TacticalBattleSession.seed`). Derive per-turn seeds deterministically: `Random(session.seed * 31L + turn)`. This makes every battle reproducible from its seed + turn sequence, enabling replay and regression testing.

**Detection (warning sign):**
`BattleParityTest` passes locally but fails intermittently in CI. Battle replays diverge from live results.

**Phase to address:** During tactical battle engine stabilization.

---

### Pitfall 12: Coup/Rebellion State Machine Has No Rollback

**What goes wrong:**
The coup system is a multi-step process: `반의 → 모의 → 설득 → 반란`. Each step writes flags to `officer.meta["rebellionIntent"]` and `officer.meta["coupLeader"]`. There is no compensation logic if a mid-coup officer is arrested, killed, or the session ends. The coup flags remain in `meta` permanently, creating ghost coup state that triggers on the next world load.

**Codebase location:**
`CommandExecutor.handleCoupExecution()` writes `general.meta["rebellionIntent"]` and `general.meta["coupLeader"]` without defining a cleanup path.

**Prevention:**
Model the coup as an explicit `CoupAttempt` entity with a state machine (`PLANNING → ACTIVE → SUCCESS | FAILED | ABORTED`). Every transition is a row update. On officer death/arrest, the service resolves the coup attempt to `ABORTED` and cleans all participant meta. On session end, all in-progress coups resolve to `FAILED`.

**Detection (warning sign):**
Officers in new game sessions have `rebellionIntent` flags from previous sessions. Coup-related commands trigger unexpectedly on officers who never initiated a coup.

**Phase to address:** Before coup/rebellion commands are implemented.

---

### Pitfall 13: Organization Hierarchy Permissions Are Not Enforced at the Data Layer

**What goes wrong:**
Position card checks in `CommandGating.canExecuteCommand()` are enforced in application code. Any request that bypasses `CommandExecutor` (e.g., a future admin endpoint, a background service, or a direct repository call in a test) skips permission enforcement entirely.

**Prevention:**
Position card/rank checks must be a service-layer invariant, not a controller concern. Every command-executing code path must call `CommandGating`. Consider a `@CommandGuard` AOP annotation that intercepts all methods annotated with it and enforces gating before execution, making bypass impossible without explicitly opting out.

Do not rely on frontend validation as a security layer — all permission checks must be server-side.

**Detection (warning sign):**
Integration tests exist that call command service methods directly without setting up position cards and still pass. Admin endpoints that invoke game commands do not call `CommandGating`.

**Phase to address:** During organization/permission system implementation.

---

### Pitfall 14: Game Economy Inflation — Funds/Supplies Have No Effective Sinks at Scale

**What goes wrong:**
With 200+ players generating funds via planet taxation and commerce every turn, and limited spend opportunities early in the game, faction treasuries accumulate unbounded wealth. When warfare commands (which are the primary sink) open up, early-joining factions have insurmountable economic leads. New players entering mid-session cannot catch up.

**Why it happens:**
Economy sinks (fleet construction, espionage operations, political events) are late-phase features. Sources (taxation, commerce) are early. This is a classic browser strategy game inflation pattern documented in games like Ogame, Travian, and Ikariam.

**Prevention:**

- Define maintenance costs for fleets and planets from turn 1 (even at low rates)
- Cap planet treasury accumulation (overflow converts to supplies at a loss)
- Make CP-heavy late-game commands cost both CP and funds, scaling with faction size
- Model economy sinks in the same phase as the sources that feed them

**Detection (warning sign):**
After 10 game sessions, faction funds values are 1000x what any command costs. The economy test `CommandParityTest` only verifies that commands succeed, not that resource drains are proportionate.

**Phase to address:** During economic system design; validate with simulated turns before opening to players.

---

## Minor Pitfalls

### Pitfall 15: NPC AI Runs on `System.currentTimeMillis()` Seed Per Turn

Same as Pitfall 11 — NPC AI decisions are non-deterministic. NPC factions will behave differently in every playthrough even with identical starting conditions. This makes balance testing and bug reproduction impossible.

**Prevention:** Seed NPC AI from the session seed + faction ID + turn number.

**Phase to address:** During NPC AI implementation.

---

### Pitfall 16: Jackson Unsafe Deserialization in ScenarioService

**What goes wrong:**
`ScenarioService` uses `objectMapper.readValue<ArrayList<Any>>()` which can instantiate arbitrary classes if scenario JSON includes a `@type` field — a remote code execution vector if scenario files are ever user-uploadable or loaded from untrusted sources.

**Prevention:**
Use `objectMapper.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)` and disable polymorphic type handling. Create strongly-typed `ScenarioDto` data classes. Validate scenario files against a schema on load.

**Phase to address:** Before any scenario upload/import feature is added.

---

### Pitfall 17: N+1 Query Pattern in Sovereign and Faction Lookups

**What goes wrong:**
`SovereignController` calls `findAll()` and filters in memory. As session officer counts grow (2,000 players), this loads the entire officer table on every sovereign lookup.

**Prevention:**
Replace all `findAll().filter {}` patterns with `@Query`-annotated repository methods using `WHERE session_id = :sessionId AND ...`. Add composite indexes on `(session_id, faction_id)` for officer and planet tables.

**Phase to address:** Before performance testing with large player counts.

---

### Pitfall 18: Magic Numbers in `WarFormula` DEX Thresholds

**What goes wrong:**
Hardcoded thresholds `[350, 1375, 3500, 7125, ...]` in `WarFormula.kt` have no documentation. Balance changes to officer stat ranges require editing raw numbers with no understanding of the intended scaling curve.

**Prevention:**
Extract to a named constant array with a comment explaining the progression formula. Write a unit test that validates the thresholds are monotonically increasing and produce the expected DEX tier for boundary stat values.

**Phase to address:** Before any balance tuning pass.

---

## Phase-Specific Warnings

| Phase Topic                       | Likely Pitfall                                                        | Mitigation                                          |
| --------------------------------- | --------------------------------------------------------------------- | --------------------------------------------------- |
| Turn engine implementation        | Missing heartbeat (Pitfall 2), @Scheduled single-thread (Pitfall 9)   | Use TaskScheduler + ShedLock from day 1             |
| Command system (all 70+ commands) | CP race condition (Pitfall 1), stale auth (Pitfall 5)                 | @Version on Officer, pessimistic lock on CP consume |
| Organization/permission system    | Data-layer bypass (Pitfall 13), stale position cards (Pitfall 5)      | AOP guard + re-fetch officer in transaction         |
| Coup/rebellion commands           | No rollback (Pitfall 12)                                              | CoupAttempt entity with explicit state machine      |
| Tactical combat at scale          | Executor leak (Pitfall 3), double-start (Pitfall 4), RNG (Pitfall 11) | Managed scheduler, session lock, deterministic seed |
| Economy system                    | Inflation (Pitfall 14)                                                | Sinks defined in same phase as sources              |
| 2000-player load test             | STOMP thread exhaustion (Pitfall 8)                                   | Virtual threads + explicit pool config before test  |
| Multi-session / multi-instance    | Duplicate tick execution (Pitfall 9)                                  | ShedLock + Redis from first distributed deployment  |
| Scenario creation                 | Silent map fallback (Pitfall 7)                                       | Fail fast, no silent catch                          |
| Long-running battles              | WarAftermath blocking (Pitfall 6)                                     | Async event handlers, short transactions            |

---

## Sources

- Spring Framework STOMP Performance Docs: https://docs.spring.io/spring-framework/reference/web/websocket/stomp/configuration-performance.html
- Spring Boot WebSocket STOMP memory leak issues: https://github.com/spring-projects/spring-boot/issues/5810 (confirmed at 2,000 connections)
- Spring Framework OOM with 2,000 WebSocket clients (Tomcat): https://github.com/spring-projects/spring-framework/issues/35017
- PostgreSQL deadlock and long transaction pitfalls: https://www.cybertec-postgresql.com/en/postgresql-understanding-deadlocks/
- PostgreSQL lock contention: https://oneuptime.com/blog/post/2026-02-02-postgresql-lock-contention/view
- @Scheduled pitfalls for distributed game tasks: https://medium.com/@rakesh.mali/avoid-using-scheduled-for-background-tasks-in-spring-boot-use-this-instead-876d6a8d7c57
- Race condition exploit via concurrent HTTP requests: https://www.bugcrowd.com/blog/racing-against-time-an-introduction-to-race-conditions/
- Game economy inflation design: https://machinations.io/articles/what-is-game-economy-inflation-how-to-foresee-it-and-how-to-overcome-it-in-your-game-design
- Deterministic lockstep and tick synchronization: https://www.gamedeveloper.com/programming/hack-source-net-post-mortem-or-why-making-online-multiplayer-games-are-hard-
- Codebase analysis: `.planning/codebase/CONCERNS.md` (2026-03-28)
- Direct code review: `CommandExecutor.kt`, `CommandPointService.kt`, `TacticalWebSocketController.kt`, `TacticalGameSession.kt`, `ScenarioService.kt`
