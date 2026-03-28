# Project Research Summary

**Project:** Open LOGH (오픈 은하영웅전설)
**Domain:** Persistent multiplayer organizational-hierarchy space strategy game (browser-based MMO)
**Researched:** 2026-03-28
**Confidence:** HIGH

## Executive Summary

Open LOGH is a browser-based persistent MMO where players act as military officers inside a faction hierarchy — earning rank, holding authority cards, issuing strategic commands, and commanding real-time fleet battles. The game is not a generic 4X or base-builder; its core loop is organizational simulation: rank determines access, duty cards gate commands, and CP scarcity forces role specialization. The reference implementation is gin7 (銀河英雄伝説VII), and research across all four domains confirms that the design spec is sound, ambitious, and substantially more complete in code than most early-stage projects.

The recommended build approach is inside-out: complete and harden the existing backend subsystems before adding features. The codebase already contains working implementations of the turn engine (TurnDaemon), tactical battle engine (TacticalBattleEngine), command point system, position card gating, WebSocket infrastructure, and command executor. What is missing is the wiring: the strategic-to-tactical bridge (BattleTrigger is unconnected), CP recovery is fired on the wrong schedule, the operation lifecycle state machine has no runner, and the organization hierarchy is persisted in unqueryable JSONB rather than a proper join table. The gap is integration, not invention.

The principal risks are technical correctness issues that compound: CP race conditions enable infinite-command exploits, stale position card reads allow privilege escalation, and the coup system has no rollback path. All three are pre-existing code issues that must be resolved before any new feature layer is built on top of them. The right mitigation is to treat Phase 1-3 as a hardening sprint — fixing concurrency bugs, decoupling schedulers, and normalizing data — before expanding the feature surface.

---

## Key Findings

### Recommended Stack

The existing Spring Boot 3 (Kotlin) + Next.js 15 + PostgreSQL 16 + Redis 7 stack is correct and requires minimal additions. Three new backend dependencies cover all unimplemented features: RabbitMQ as an external STOMP broker (required for multi-pod WebSocket fan-out at 2,000 players), `kotlinx-coroutines-core` for per-session tick loops, and `kstatemachine-coroutines` for NPC officer AI. The frontend requires zero new packages — React Three Fiber, Zustand 5.x, and `@stomp/stompjs` already cover the RTS rendering loop, state management, and WebSocket client.

**Core new technologies:**

- **RabbitMQ 3 (STOMP plugin):** External STOMP broker relay — required to replace the in-memory Spring broker for multi-instance WebSocket fan-out; Spring's `enableStompBrokerRelay()` connects via `reactor-netty`
- **kotlinx-coroutines-core 1.8.x:** Per-session tick loop engine — one `CoroutineScope` per active game session; avoids global `@Scheduled` scheduler contention
- **kstatemachine-coroutines 0.36.0:** NPC officer FSM — Kotlin-native, coroutine-aware, hierarchical state support; replaces ad-hoc `when`/`sealed class` chains at 10+ NPC states
- **PostgreSQL advisory locks:** Per-session turn serialization — zero new infra, `pg_try_advisory_xact_lock` via existing `JdbcTemplate`; prevents concurrent turn processing on multi-instance deployments
- **R3F `useFrame` + Zustand slices:** Already present — correct architecture for the RTS rendering loop and high-frequency state updates; no new packages needed

**What not to add:** Socket.IO, Spring State Machine, separate NPC AI service, Redis SETNX distributed locks, GraphQL subscriptions, or XState. Each would add complexity that the existing stack already handles better.

### Expected Features

The feature landscape is anchored to the gin7 manual. Research classified 180+ features across 14 categories. The core loop depends on 7 P0 feature groups being implemented in strict dependency order — no group can be skipped.

**Must have (table stakes):**

- Session creation + player/officer binding — the entry point; nothing else exists without it
- Rank system (11 levels, merit points, per-tier caps) — skeleton of all authority and progression
- Duty card system (22+ card types, command gating, appointment chains) — gates every command
- CP system, dual PCP/MCP with offline recovery — pacing mechanism for all 70+ commands
- Galaxy map (80 star systems) + planet management + fleet movement — the strategic arena
- Fleet combat with formations and retreat — the tactical RTS layer this game promises
- In-game mail + location-scoped chat — coordination infrastructure

**Should have (differentiators):**

- Dual CP split (PCP/MCP) with cross-substitution — forces role specialization; no comparable browser MMO has this
- 5-law rank ladder (merit → title → decoration → influence → stat tiebreaker) — creates political competition within factions
- NPC AI officers (canonical LOGH characters acting as AI when not chosen by players) — the core "play alongside Reinhard" fantasy
- Energy allocation across 6 channels in tactical combat (BEAM/GUN/SHIELD/ENGINE/WARP/SENSOR) — high skill ceiling
- Faction asymmetry (Empire fief system vs. Alliance democratic elections) — structural differentiation, not cosmetic

**Defer (Phase 4+):**

- Coup d'état system (6-stage chain) — requires full organizational simulation to be meaningful
- Full espionage suite (12+ commands) — requires physical infiltration state
- Democratic elections (Alliance) — requires active player base
- 3D tactical visualization — 2D Konva top-down view is specified and sufficient
- Auction/tournament systems

**Critical anti-features to avoid:** pay-to-win mechanics, planet management as the primary loop, combat auto-resolve as default, global faction chat, unlimited faction switching, and complex UI before backend systems are working (the 3D model viewer's existence before core game systems is flagged as the current risk).

### Architecture Approach

The codebase follows a correct server-authoritative in-memory model: one game-app process per world holds all session state in memory, writes only dirty entities to PostgreSQL via DirtyTracker batch saves, and broadcasts changes via STOMP WebSocket. The architecture is sound — the problem is that critical bridges between subsystems are unconnected. The turn engine, tactical engine, CP recovery, operation lifecycle, organization hierarchy, and logistics chain each work independently but are not wired into a coherent game loop.

**Major components:**

1. **Gateway-App (8080)** — HTTP proxy, JWT auth, world routing, session activation; already complete
2. **Strategic Layer (TurnDaemon + InMemoryTurnProcessor)** — game clock (24x), economy, command execution, operation lifecycle; WORKING but needs 24x calibration, CP recovery decoupling, and operation state machine runner
3. **Tactical Layer (TacticalSessionManager + TacticalBattleEngine)** — in-memory battle rooms, 10-step turn resolution, WebSocket broadcast; WORKING but not wired to strategic layer via BattleTrigger
4. **Command System (CommandExecutor + CommandGating)** — constraint chain, CP cost, position card gating; WORKING but vulnerable to CP race condition and stale auth reads
5. **Organization Hierarchy (PositionCard + CommandGating)** — 22 card types, rank gating; WORKING but authority stored in unqueryable officer.meta JSON instead of PositionCard table
6. **WebSocket Channels (STOMP)** — session-isolated topic routing; WORKING with in-memory broker that must be replaced with RabbitMQ relay before multi-pod deployment
7. **PostgreSQL + Redis** — persistent state + CP timer tracking; in place, needs GIN indexes on JSONB fields and advisory lock pattern for turn serialization

### Critical Pitfalls

1. **CP race condition (exploit-grade)** — two concurrent HTTP requests pass the CP balance check before either commits; fix with `@Version` on Officer entity or `PESSIMISTIC_WRITE` lock on officer fetch inside the command transaction. Address before any command system goes live.

2. **Stale position card authorization** — `CommandExecutor` reads position cards from `officer.meta` JSON loaded at request time; a concurrent demotion transaction is invisible to in-flight commands. Fix by re-fetching the officer with `PESSIMISTIC_READ` inside the command `@Transactional` boundary. Address during Phase 2-3.

3. **Executor leak in tactical session cleanup** — `TacticalWebSocketController` creates an unmanaged `Executors.newSingleThreadScheduledExecutor()` per battle end and never shuts it down; at scale this exhausts JVM threads. Fix with a Spring-managed `ThreadPoolTaskScheduler` bean. Address before load testing.

4. **Inconsistent concurrency in TacticalGameSession** — `synchronizedSet` for `joinedOfficers`/`readyOfficers` mixed with `ConcurrentHashMap`; compound check-then-act on `isAllReady() + startCombat()` is not atomic, causing double combat starts. Fix with per-session `ReentrantLock` or `synchronized(session)` guard.

5. **Coup state machine has no rollback** — mid-coup flags in `officer.meta` are never cleaned up on arrest, death, or session end; ghost coup state persists across sessions. Fix by modeling coup as a `CoupAttempt` entity with explicit `PLANNING → ACTIVE → SUCCESS | FAILED | ABORTED` transitions before implementing coup commands.

---

## Implications for Roadmap

Based on combined research, the dependency chain is strict: session → character/rank → command authority → game loop → map/fleet → combat → politics/espionage. No phase can be reordered. The architecture research confirms 9 build phases from direct codebase analysis; the feature research confirms the same ordering from a design dependencies perspective. These converge.

### Phase 1: Session Foundation and Concurrency Hardening

**Rationale:** Every other system is scoped to a session. The CP race condition and stale auth vulnerabilities exist in code right now and will corrupt all future work if not fixed first. Foundation before features.
**Delivers:** Working session creation/join/faction-selection flow; officer-to-user binding per session; CP atomic consumption (no exploit); BattleTrigger executor leak fixed; scenario initialization fail-fast (no silent map fallback).
**Addresses:** Table stakes — session creation, character location state, persistent offline presence.
**Avoids:** Pitfall 1 (CP race), Pitfall 3 (executor leak), Pitfall 7 (silent map fallback).

### Phase 2: Character, Rank, and Organization Authority

**Rationale:** Commands need an officer with stats, rank, and position cards. The position card authority stored in JSONB is the root cause of stale auth reads — must be migrated to the PositionCard relational table before any more commands are implemented.
**Delivers:** Officer generation (original + canonical characters); 8-stat persistence; PositionCard table migration (replace officer.meta JSON); 11-rank ladder with merit points and per-tier caps; appointment/dismissal chain.
**Addresses:** Table stakes — rank system, duty card authority, human-controlled promotion/demotion.
**Avoids:** Pitfall 5 (stale auth via JSONB), Pitfall 13 (permissions bypassed at data layer), Pitfall 10 (JSONB dumping ground).
**Uses:** PostgreSQL PositionCard unique constraint `(sessionId, cardCode)`.

### Phase 3: Strategic Tick Hardening and CP System

**Rationale:** The tick engine exists but is miscalibrated. CP recovery fires on the wrong schedule (per game month instead of every 5 real minutes). The operation lifecycle has no state machine runner. These gaps mean the game clock is broken even though the code exists.
**Delivers:** `tickSeconds` configured for 24x acceleration; CP recovery decoupled to independent `@Scheduled(fixedRate=300_000)` bean; operation state machine (`SUBMITTED → EXECUTING → COMPLETED`) in OfficerTurn; `StrategicCommandDef.waitTime/executionTime` enforced in turn loop; dual PCP/MCP with offline recovery correct from this phase.
**Addresses:** Table stakes — CP system, CP recovery continues offline, CP cost differentiates action weight.
**Avoids:** Pitfall 2 (missing heartbeat), Pitfall 9 (@Scheduled single-thread contention).
**Uses:** `kotlinx-coroutines-core` per-session scope pattern; dedicated `ThreadPoolTaskScheduler`.

### Phase 4: Galaxy Map and Planet Management

**Rationale:** Operations and logistics need map coordinates and planet resources. The planet warehouse separation (planet → fleet warehouse chain) is required before fleet logistics commands can work correctly.
**Delivers:** 80-star-system galaxy map with grid coordinates; planet resource model (production → planet warehouse → faction treasury); warp/navigation distance calculation; planet facilities; fog of war foundation.
**Addresses:** Table stakes — persistent star map, planet management, territory capture, victory conditions visible.
**Avoids:** Pitfall 14 (economy inflation — sinks defined in same phase as sources).

### Phase 5: Fleet System and Logistics

**Rationale:** Depends on planet warehouse model from Phase 4. Fleet composition, morale, supply constraints, and the transport command lifecycle complete the strategic layer's resource model.
**Delivers:** Fleet formation and composition rules; planet warehouse to fleet warehouse transfer (`반출입` command extension); ship production pipeline; conscription and manpower limits; morale and fuel constraints on movement.
**Addresses:** Table stakes — multiple fleet types, fleet movement, morale threshold, fuel/supply constraint.

### Phase 6: Tactical Combat Integration

**Rationale:** The tactical engine is complete but isolated. This phase is purely integration: wire BattleTrigger to TacticalSessionManager, connect TacticalResultWriteback to strategic state, and ship the frontend battle entry flow. NPC AI for offline commanders is also addressed here since TacticalAI already exists.
**Delivers:** BattleTrigger wired to TacticalSessionManager; frontend battle entry flow (strategic event → navigate to /battle/{code}); TacticalResultWriteback applied to strategic state (planet ownership, officer ships/morale); AI orders for NPC-controlled fleets; deterministic RNG seeding for battle sessions.
**Addresses:** Table stakes — combat triggers when fleets meet, formation system, retreat mechanic, AI takes over for offline commanders.
**Avoids:** Pitfall 4 (TacticalGameSession double-start concurrency), Pitfall 11 (non-deterministic RNG).
**Uses:** `ThreadPoolTaskScheduler` for session cleanup (replaces inline executor).

### Phase 7: Personnel Commands and Political Systems

**Rationale:** Depends on the organization hierarchy from Phase 2 being persisted correctly in the PositionCard table. The ProposalSystem is already implemented — it needs an HTTP/WS surface. Coup/rebellion requires a CoupAttempt entity before commands are exposed.
**Delivers:** Promotion/demotion commands with position card grant/revoke; appointment/dismissal with PositionCard table writes; ProposalSystem HTTP/WS surface; 5-law rank ladder tiebreakers (influence, friendship/affinity); CoupAttempt entity with state machine (no-rollback hazard resolved before commands ship).
**Addresses:** Differentiators — 5-law rank ladder, proposal/order chain, influence system.
**Avoids:** Pitfall 12 (coup state machine no rollback).
**Uses:** `kstatemachine-coroutines` for CoupAttempt state machine.

### Phase 8: Communication Systems

**Rationale:** Low dependency on other systems (needs character from Phase 2), standalone, but required before players can coordinate at scale. In-game mail gating by duty card addresses means it cannot ship before Phase 7.
**Delivers:** In-game mail (120-message cap, address book, duty card addresses); 1:1 messenger (WebSocket, user destination); positional chat (same grid/spot scope only — no global faction chat).
**Addresses:** Table stakes — in-game mail, location-scoped chat, faction-internal messaging.
**Avoids:** Anti-feature — single global chat that destroys location immersion.
**Uses:** Spring `SimpMessagingTemplate` user destinations; `/topic/chat.grid.{gridId}` for spot chat; no new dependencies.

### Phase 9: Victory Conditions, Session Lifecycle, and Scale Hardening

**Rationale:** Needs all game systems in place to evaluate conditions correctly. Scale hardening (STOMP thread pools, virtual threads, RabbitMQ relay) must precede any public load test.
**Delivers:** `UnificationService` wired into turn loop; victory conditions (capital capture, 3-star threshold, time limit); session end (freeze world, rankings, Hall of Fame); STOMP channel thread pool configuration; virtual threads enabled; RabbitMQ STOMP relay replacing in-memory broker; N+1 query fixes; GIN indexes on JSONB fields.
**Addresses:** Table stakes — victory conditions visible, battle log/replay visibility.
**Avoids:** Pitfall 8 (STOMP thread exhaustion at 2,000 players), Pitfall 17 (N+1 queries in sovereign lookups).
**Uses:** RabbitMQ `reactor-netty`/`netty-all`; `spring.threads.virtual.enabled=true` (Java 21).

### Phase Ordering Rationale

- **Hardening before features:** Pitfalls 1, 3, 5, and 7 are pre-existing bugs in deployed code. Building new features on top of a CP race condition or stale auth vulnerability makes all subsequent phases insecure. The research is unanimous: fix the foundation first.
- **Data model before authority logic:** Migrating position cards from JSONB to the relational PositionCard table (Phase 2) must precede all command gating work. Every feature in Phases 3-8 depends on correct authority enforcement.
- **Strategic tick before map:** The turn engine calibration (Phase 3) must come before map/fleet work (Phases 4-5) because all fleet movement ETAs, resource ticks, and operation lifecycles depend on a correctly running game clock.
- **Integration before new features:** The tactical combat integration (Phase 6) completes already-built subsystems before any new combat features are added. The BattleTrigger bridge is a one-time wiring task that unlocks the entire tactical layer.
- **Political systems after organization normalization:** The coup, proposal, and influence systems (Phase 7) depend on the PositionCard table established in Phase 2. Attempting to build these on the JSONB meta approach would guarantee Pitfall 12.

### Research Flags

Phases likely needing deeper research during planning:

- **Phase 3 (Strategic Tick Hardening):** The 24x time acceleration formula and CP recovery interval have specific gin7 specs that need verification against the `tickSeconds` field and real-time scheduling constraints. The @Scheduled coroutine bug (Spring issue #32165) needs version verification against the project's exact Spring Boot 3.4.2.
- **Phase 6 (Tactical Combat Integration):** The BattleTrigger data flow requires careful design of the strategic pause model (officers in `locationState="tactical"` being skipped by the turn loop). The reconnection/crash-recovery behavior for mid-battle game-app restarts needs a decision.
- **Phase 9 (Scale Hardening):** RabbitMQ STOMP relay configuration for the specific Spring Boot 3.4.2 + `reactor-netty` combination should be tested in isolation before integrating into the production turn loop.

Phases with standard patterns (skip research-phase):

- **Phase 1 (Session Foundation):** Session creation, player-officer binding, and concurrency fixes are standard Spring Boot patterns. The CP atomic fix is a well-documented JPA optimistic locking pattern.
- **Phase 2 (Character/Rank/Org):** Schema migration from JSONB to relational table is standard Flyway work. The PositionCard entity already exists.
- **Phase 8 (Communication):** In-game mail and location chat map directly to existing STOMP infrastructure. Spring user destination support is well-documented with no unknowns.

---

## Confidence Assessment

| Area         | Confidence | Notes                                                                                                                                                                               |
| ------------ | ---------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Stack        | HIGH       | All major recommendations verified against official Spring, Kotlin, R3F, and PostgreSQL docs; only KStateMachine (MEDIUM) has community-inferred suitability                        |
| Features     | HIGH       | Primary source is the gin7 manual accessed directly via project docs; cross-referenced against EVE, Foxhole, OGame, Travian, Hades Star design analysis                             |
| Architecture | HIGH       | Based on direct codebase analysis of actual source files, not inference; confirmed against server-authoritative MMO architecture patterns                                           |
| Pitfalls     | HIGH       | All critical pitfalls grounded in specific codebase locations with line numbers; confirmed against verified external sources (Spring WebSocket OOM issues, PG lock contention docs) |

**Overall confidence:** HIGH

### Gaps to Address

- **SockJS client status:** `sockjs-client` 1.6.1 is in maintenance mode (low npm activity). Not officially deprecated, but should be monitored. If connectivity issues arise on modern browsers, evaluate `@stomp/stompjs` native WebSocket fallback (it supports running without SockJS). Flag for Phase 9 scale testing.

- **KStateMachine production suitability:** Version 0.36.0 verified from GitHub releases but production game-at-scale usage data is sparse. Verify with a small NPC AI prototype before committing the full NPC officer system to this library. The fallback is hand-rolled coroutine FSM with sealed classes, which is viable for fewer than 10 states per NPC type.

- **`tickSeconds` formula validation:** The 24x time acceleration requires `tickSeconds = 150` (2.5 real minutes per game month). This needs empirical validation — game sessions that last too long lose players; sessions that end too fast create churn. The correct value is a design tuning parameter that may require iteration after the first player test.

- **WarAftermath transaction scope:** PITFALLS.md flags that `WarAftermath.kt` runs multi-entity updates in a single long transaction, causing turn blocking at scale. The async event handler refactor (outbox pattern) is architecturally non-trivial and is not modeled in any phase above. It should be a sub-task within Phase 6 (tactical combat integration) when battle outcome processing is first exercised under load.

- **RabbitMQ operational burden:** Adding RabbitMQ to the infrastructure increases operational complexity (clustering, message persistence configuration, dead-letter queues). For the single-world MVP (200 players), the in-memory STOMP broker is sufficient and RabbitMQ can be deferred to Phase 9. The codebase should be structured so the broker relay is a configuration switch, not a code change.

---

## Sources

### Primary (HIGH confidence)

- Direct codebase analysis: `/Users/apple/Desktop/openlogh/backend/game-app/src/` — TurnDaemon, TacticalSessionManager, TacticalBattleEngine, CommandPointService, PositionCardSystem, CommandExecutor, InMemoryTurnProcessor, TacticalWebSocketController, RealtimeService
- gin7 manual (54p) — via `/Users/apple/Desktop/openlogh/docs/feature-checklist.md` and `feature-audit.md` — primary game design authority
- [Spring Framework — External STOMP Broker Relay](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/handle-broker-relay.html)
- [Spring Framework — Token-Based WebSocket Auth](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/authentication-token-based.html)
- [Spring Framework — STOMP Performance Configuration](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/configuration-performance.html)
- [React Three Fiber — useFrame Hook](https://r3f.docs.pmnd.rs/api/hooks#useframe)
- [PostgreSQL — Explicit Locking (Advisory Locks)](https://www.postgresql.org/docs/current/explicit-locking.html)
- [Spring Issue #32165 — @Scheduled + Kotlin suspend bug](https://github.com/spring-projects/spring-framework/issues/32165)

### Secondary (MEDIUM confidence)

- [EVE Online Corporation Roles](https://support.eveonline.com/hc/en-us/articles/203217712-Roles-Listing) — org hierarchy design patterns
- [Heroic Labs / Nakama — Authoritative Multiplayer](https://heroiclabs.com/docs/nakama/concepts/multiplayer/authoritative/) — server-authoritative tick model confirmation
- [MMO Architecture: Source of truth, Dataflows](https://prdeving.wordpress.com/2023/09/29/mmo-architecture-source-of-truth-dataflows-i-o-bottlenecks-and-how-to-solve-them/) — in-memory world state pattern
- [KStateMachine GitHub Releases](https://github.com/KStateMachine/kstatemachine/releases) — version 0.36.0 verified
- [Hades Star FAQ](https://hadesstar.com/faq.html) — anti-feature rationale (auto-resolve as default)
- Race condition exploit pattern: [Bugcrowd — Race Conditions](https://www.bugcrowd.com/blog/racing-against-time-an-introduction-to-race-conditions/)
- Game economy inflation: [Machinations — Game Economy Inflation](https://machinations.io/articles/what-is-game-economy-inflation-how-to-foresee-it-and-how-to-overcome-it-in-your-game-design)

### Tertiary (LOW confidence)

- [OGame Fleet Management 2025](https://ogame.life/ogame/blog/ogame-fleet-management-for-2025-top-strategies/) — genre context only, not design authority

---

_Research completed: 2026-03-28_
_Ready for roadmap: yes_
