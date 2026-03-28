# Architecture Patterns: OpenLOGH Backend Systems

**Domain:** Multiplayer online strategy game with real-time tactical combat
**Researched:** 2026-03-28
**Confidence:** HIGH (based on direct codebase analysis + verified external patterns)

---

## Current State Assessment

The codebase is substantially more complete than typical early-stage projects. Most major
subsystems are scaffolded. The research question is not "how to build these from scratch" but
"how to complete, wire together, and harden the existing components."

### What Already Exists (Do Not Rebuild)

| System                   | Files                                                                           | Status                                                 |
| ------------------------ | ------------------------------------------------------------------------------- | ------------------------------------------------------ |
| Game tick engine         | `TurnDaemon`, `InMemoryTurnProcessor`, `TurnCoordinator`                        | WORKING — `@Scheduled(fixedRate)` loop, CQRS path      |
| Tactical battle engine   | `TacticalBattleEngine`, `TacticalSessionManager`, `TacticalWebSocketController` | WORKING — full turn-based RTS with WS                  |
| WebSocket infrastructure | `TacticalWebSocketController`, `CommandWebSocketController`, STOMP config       | WORKING — session-isolated topic routing               |
| Command point system     | `CommandPointService`                                                           | WORKING — PCP/MCP dual pool, recovery, exp gain        |
| Position card system     | `PositionCardSystem`, `CommandGating`                                           | WORKING — 22 card types, command gating                |
| Command execution        | `CommandExecutor`, `CommandRegistry`                                            | WORKING — constraint chain, cooldown, CP cost          |
| Organization permissions | `PositionCardType` enum                                                         | WORKING — 22 position types, rank gating               |
| Fleet entities           | `Fleet`, `TacticalFleet`, `TacticalSessionManager`                              | WORKING — 11 ship classes, crew grades                 |
| Logistics commands       | `LogisticsCommands.kt`                                                          | PARTIAL — 완전수리/보급/재편성/반출입/보충 implemented |
| Strategic commands       | `StrategicCommandRegistry`                                                      | PARTIAL — metadata defined, execution stubs needed     |
| Proposal/obedience       | `ProposalSystem`                                                                | WORKING — acceptance/obedience probability calculated  |

### What Is Missing (Research Target)

1. Game time acceleration (24x) — `tickSeconds` field exists but the 24x ratio is not enforced
2. CP recovery timing tied to real game time — currently called per monthly tick, not per 5 real minutes
3. Operation lifecycle state machine — `StrategicCommandDef` has `waitTime`/`executionTime` fields but no execution runner
4. Logistics chain (planet warehouse → fleet warehouse) — transfer semantics incomplete
5. Organization hierarchy persistence — `PositionCard` entity exists but assignment/query is incomplete
6. Battle trigger from strategic layer — `BattleTrigger` exists but bridge to `TacticalSessionManager` is not wired
7. Session join / faction selection flow — no entity tracks player-to-officer assignment per session
8. Victory condition evaluation — `UnificationService` exists but is not called from turn loop

---

## Recommended Architecture

### Component Map

```
┌─────────────────────────────────────────────────────────────────┐
│                        GATEWAY-APP (8080)                        │
│  WorldController  ProcessOrchestrator  AuthService               │
│  WorldRouteRegistry  WorldActivationBootstrap                    │
└───────────────────────────────┬─────────────────────────────────┘
                                │ HTTP proxy / JWT forward
┌───────────────────────────────▼─────────────────────────────────┐
│                      GAME-APP (9001+)                            │
│                                                                  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐   │
│  │ Strategic    │  │ Tactical     │  │ Communication        │   │
│  │ Layer        │  │ Layer        │  │ Layer                │   │
│  │              │  │              │  │                      │   │
│  │ TurnDaemon   │  │ TacticalSM   │  │ MessageController    │   │
│  │ TurnCoord    │  │ TacticalBE   │  │ BattleRecordCtrl     │   │
│  │ RealtimeSvc  │  │ TacticalWS   │  │ (chat, mail,         │   │
│  │ CommandExec  │  │ TacticalAI   │  │  messenger)          │   │
│  │ EconomySvc   │  │              │  │                      │   │
│  │ DiplomacySvc │  └──────┬───────┘  └──────────────────────┘   │
│  │ EspionageSvc │         │                                      │
│  │              │  ┌──────▼───────┐                             │
│  └──────┬───────┘  │ BattleTrigger│ ← bridges strategic→tactical│
│         │          └──────────────┘                             │
│  ┌──────▼───────────────────────────────────────────────────┐   │
│  │                  DOMAIN ENTITIES (JPA/PostgreSQL)         │   │
│  │  SessionState  Officer  Fleet  Planet  Faction           │   │
│  │  PositionCard  OfficerTurn  FactionTurn  Event           │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │                  WEBSOCKET CHANNELS (STOMP)              │    │
│  │  /app/command/{sessionId}/execute   → CommandWS         │    │
│  │  /topic/world/{sessionId}/events    ← GameEventService  │    │
│  │  /app/tactical/{code}/join|order    → TacticalWS        │    │
│  │  /topic/tactical/{code}/state       ← TacticalWS        │    │
│  │  /topic/tactical/{code}/turn-result ← TacticalWS        │    │
│  │  /topic/tactical/{code}/victory     ← TacticalWS        │    │
│  └─────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
                                │
        ┌───────────────────────┤
        │                       │
┌───────▼───────┐    ┌──────────▼──────┐
│  PostgreSQL 16│    │    Redis 7       │
│  (persistent) │    │  (sessions,      │
│               │    │   pub/sub,       │
│               │    │   CP timers)     │
└───────────────┘    └─────────────────┘
```

---

## Component Boundaries

### 1. Strategic Layer (Turn Engine)

**What it does:** Advances game time, executes player commands, runs economy/diplomacy/espionage,
triggers battles, evaluates victory conditions.

**Communicates with:** PostgreSQL (read/write all entities), Redis (CP timer tracking),
Tactical Layer (battle trigger), WebSocket (event broadcast to clients).

**Key invariant:** Only one turn cycle runs per world at a time. `TurnDaemon` is the single
scheduler; `TurnCoordinator` serializes CQRS processing. Never call `TurnService`/`TurnCoordinator`
from an HTTP request thread — only from the scheduled tick.

**Tick cycle (existing, confirmed):**

```
TurnDaemon.tick() [every game.tick-rate ms, default 1000ms]
  └─ for each SessionState (matching commitSha):
       ├─ realtimeMode=true  → RealtimeService.processCompletedCommands() + regenerateCommandPoints()
       └─ cqrsEnabled=true   → TurnCoordinator.processWorld()
            └─ InMemoryTurnProcessor.process()
                 ├─ while (world.updatedAt + tickSeconds <= now): advance month
                 │    ├─ EconomyService.preUpdateMonthly/postUpdateMonthly
                 │    ├─ CommandPointService.recoverAllCp   ← per game-month, not per 5 real-min
                 │    ├─ AgeGrowthService.processMonthlyGrowth
                 │    └─ OfficerMaintenanceService
                 └─ DirtyTracker → WorldStatePersister → JPA batch save
```

**24x time acceleration — how to complete:**

`tickSeconds` on `SessionState` is the key lever. For 24x real-time acceleration:

- 1 real minute = 24 game minutes = 2 game months (at 12 months/year cadence)
- Set `tickSeconds = 150` (2.5 real minutes per game month) for approximately 24x
- More precisely: `tickSeconds = (real_seconds_per_game_year) / 12`
- For 24x: 1 real hour = 24 game hours. If a game year = 12 turns, 1 real year ≈ 0.5 real hours.
  Use `tickSeconds = 150` (each game month takes 2.5 real minutes).
- CP recovery trigger: currently fires per game-month. Per gin7 spec it should fire every
  2 game-time hours (= every 5 real minutes). Decouple CP recovery from turn advancement
  using a separate `@Scheduled(fixedRate = 300_000)` bean that calls `CommandPointService.recoverAllCp`
  independently of month boundaries.

**Operation execution lifecycle — what to build:**

`StrategicCommandDef` already carries `waitTime` and `executionTime` in game-time minutes.
The missing piece is a state machine for in-progress operations. Pattern:

```
SUBMITTED → [waitTime elapses] → EXECUTING → [executionTime elapses] → COMPLETED → effect applied
```

Store state in `OfficerTurn` (already exists). Add fields: `operationState` (enum),
`operationStartsAt` (game timestamp), `operationCompletesAt` (game timestamp).
`InMemoryTurnProcessor` checks each tick whether any `OfficerTurn` has crossed its completion
timestamp and applies the effect. This is the "operation planning/execution lifecycle" — it
maps directly onto the existing turn loop.

---

### 2. Tactical Layer (Real-Time Combat)

**What it does:** Manages isolated battle rooms, processes player orders each turn, runs the
combat simulation, broadcasts results via WebSocket, writes outcomes back to strategic state.

**Communicates with:** WebSocket clients (STOMP), PostgreSQL (writeback only — via
`TacticalResultWriteback`), Strategic Layer (receives trigger, returns result).

**Key invariant:** Tactical sessions are entirely in-memory (`ConcurrentHashMap` in
`TacticalSessionManager`). They are never persisted mid-battle. Only the final result
(`TacticalResultWriteback.applyResult`) touches the database.

**Battle room lifecycle (existing, confirmed):**

```
POST /api/tactical/create
  └─ TacticalSessionManager.createSession()
       ├─ builds TacticalFleet objects from Officer+Fleet entities
       ├─ assigns command priority (gin7 §10.20)
       ├─ places units on 1000x1000 grid with obstacles
       └─ stores TacticalGameSession in activeSessions ConcurrentHashMap

WS /app/tactical/{code}/join → all join → phase=SETUP → /app/tactical/{code}/ready
WS /app/tactical/{code}/setup → formation + energy submitted per officer
WS /app/tactical/{code}/order → TacticalOrder queued per officer
WS /app/tactical/{code}/ready → all ready:
  ├─ SETUP→COMBAT: startTurnTimer → TacticalTurnScheduler fires after N seconds
  └─ COMBAT: turnScheduler.onAllReady() → early turn resolution

TacticalBattleEngine.resolveTurn():
  1. Config orders (formation/energy)
  2. Retreat orders
  3. Special orders
  4. Movement (by mobility desc)
  5. Attack (simultaneous)
  6. Morale updates
  7. Forced retreats
  8. Death judgments (flagship destruction)
  9. Ground assault
  10. Victory check → if done: resultWriteback + destroySession (after 30s)

Broadcast per turn:
  /topic/tactical/{code}/state       → full fleet summaries + phase
  /topic/tactical/{code}/turn-result → events + fleet HP snapshots
  /topic/tactical/{code}/victory     → winner, type, casualties
```

**Missing bridge — strategic to tactical:** `BattleTrigger.kt` exists but is not wired.
The `OperationsCommands` attack command should call `BattleTrigger`, which calls
`TacticalSessionManager.createSession()` and returns the `sessionCode` to the strategic event
broadcast so clients can navigate to the battle. This is the critical missing integration.

**Session isolation:** Already correct. Each `TacticalGameSession` has a unique `sessionCode`
(UUID-based). STOMP topics are namespaced by `sessionCode`, so concurrent battles are fully
isolated. No changes needed here.

---

### 3. Command Point Recovery System

**What it does:** Regenerates PCP and MCP for each officer on a timer, independent of turn advancement.

**Current state:** `CommandPointService.recoverCp()` is implemented and correct. It is currently
called from `InMemoryTurnProcessor` once per game month. This is wrong — gin7 spec says every
2 game-time hours (5 real minutes).

**Correct architecture:**

```kotlin
// Separate scheduled bean (NOT inside TurnDaemon)
@Scheduled(fixedRate = 300_000) // every 5 real minutes
fun cpRecoveryTick() {
    val worlds = sessionStateRepository.findAllActive()
    for (world in worlds) {
        commandPointService.recoverAllCp(world.id.toLong())
    }
}
```

Remove the CP recovery call from `InMemoryTurnProcessor`. This decouples CP rhythm from
turn advancement rhythm, which is the correct gin7 behavior.

The `officer.locationState == "tactical"` check in `recoverCp()` already correctly suppresses
recovery during tactical combat.

---

### 4. Organization Hierarchy

**What it does:** Tracks which officer holds which position card, enforces rank minimums,
limits concurrent cards (max 16), and routes command permissions.

**Current state:** `PositionCardType` enum (22 types) + `CommandGating` are complete.
`PositionCard` entity exists. `CommandExecutor` reads `positionCards` from `officer.meta` JSON.

**Problem:** Position card assignment is stored in `officer.meta["positionCards"]` as a JSON
list, not as a proper relational link. This makes it hard to query "who holds position X" or
enforce the per-position uniqueness required for the 100+ position hierarchy.

**Recommended architecture:**

Use the existing `PositionCard` entity as a proper join table:

```
PositionCard {
    sessionId   FK → session_state
    officerId   FK → officer
    cardCode    String (matches PositionCardType.code)
    factionId   FK → faction
    assignedAt  Timestamp
    assignedBy  FK → officer (for audit)
}
```

Unique constraint: `(sessionId, cardCode)` — enforces one holder per position per world.
Exception: cards in `CardCategory.BASIC` are not stored (every officer has them implicitly).

`CommandGating.canExecuteCommand()` already works from a `List<String>` of card codes.
The query path becomes:

```
PositionCardRepository.findByOfficerId(officerId)
    .map { it.cardCode }
    → CommandGating.canExecuteCommand(codes, commandGroup)
```

Keep the `officer.meta["positionCards"]` cache for performance (single officer lookup),
but treat `PositionCard` table as the authoritative source during turn processing.

---

### 5. Logistics Chain

**What it does:** Moves supplies and ships between planet warehouses and fleet (officer) warehouses.
Enables production on planets, consumption by fleets, and transport operations.

**Current state:** `LogisticsCommands.kt` implements 완전수리/보급/재편성/반출입/보충 as
officer-level commands. They modify `officer.supplies`, `officer.ships`, `nation.supplies`
directly. This is mostly correct for the officer-level view.

**Missing:** Planet-level warehouse separation. Currently planets (`City`/`Planet` entity) do not
have an independent warehouse; all supplies belong to the faction (`nation.supplies`). The gin7
logistics model has:

```
Faction treasury (funds, supplies)
  └─ Planet warehouse (production output accumulates here)
       └─ Fleet warehouse (officer.supplies / officer.ships)
            └─ Individual officer allocation
```

**Recommended architecture:**

Add `planetSupplies: Int` and `planetShips: Int` fields to `Planet` entity (or store in `Planet.meta`
if avoiding schema changes). The `EconomyService.postUpdateMonthly` should transfer production
output into planet warehouses first, then faction treasury gets the remainder after planet
needs are met.

Transport logistics flow:

```
Transport command (수송 커맨드):
  1. OfficerTurn created: state=SUBMITTED, completesAt=now+travelTime
  2. Turn loop: when completesAt reached, state→COMPLETED
  3. Effect: planet.planetSupplies -= amount; officer.supplies += amount
  4. Event broadcast: "수송 완료"

Production flow (per turn):
  Planet.production * efficiency → planet.planetSupplies += delta
  If planet has excess: faction.supplies += overflow (tax collection)
```

The existing `TransportExecutionService` in `engine/fleet/` provides the travel time calculation.
Wire it into `InMemoryTurnProcessor`'s operation completion check.

---

### 6. Battle Trigger Integration (Strategic → Tactical)

**What it does:** Converts a strategic attack command into a tactical battle session and
notifies relevant players.

**Current state:** `BattleTrigger.kt` exists. `OperationsCommands.kt` has attack-type commands.
They are not connected.

**Recommended data flow:**

```
Player submits attack command (e.g., "warp + engage"):
  1. CommandExecutor validates: CP, position card (operations group), target reachable
  2. OfficerTurn.operationState = EXECUTING, type = ATTACK
  3. InMemoryTurnProcessor (on turn where attack arrives at target):
       a. BattleTrigger.shouldStartBattle(attacker, defender, planet) → true
       b. TacticalSessionManager.createSession(attackerIds, defenderIds, planetId)
       c. sessionCode stored in OfficerTurn.meta["tacticalSessionCode"]
       d. GameEventService.broadcastEvent("battle_started", {sessionCode, planetId})
  4. Frontend receives event → navigates to /battle/{sessionCode}
  5. Players join via WS /app/tactical/{sessionCode}/join
  6. Strategic turn loop pauses battle-participant officers (locationState = "tactical")
  7. On TacticalResultWriteback.applyResult():
       a. Officer ships/morale updated from battle outcome
       b. Planet ownership transferred if attacker wins
       c. Officers' locationState restored to "planet" or "space"
       d. Strategic event broadcast: "battle_ended"
```

---

## Data Flow Summary

### Strategic Turn (per game month)

```
Wall clock tick (every tickSeconds real seconds)
  → world.currentMonth++
  → EconomyService: planet production, tax collection, trade
  → OfficerMaintenance: salary, morale decay
  → CommandPointService: CP recovery (should be decoupled — see above)
  → AgeGrowth: stat growth, aging death check
  → OfficerTurn sweep: check EXECUTING operations, apply completions
  → BattleTrigger sweep: check pending attacks, spawn tactical sessions
  → UnificationService: check victory conditions
  → DirtyTracker → batch JPA save (only dirty entities)
  → GameEventService.broadcastTurnAdvance → all WS clients
```

### Real-Time Command (player input)

```
HTTP POST /api/command/{sessionId}/execute
  → RealtimeService.submitCommand()
  → CommandExecutor.executeGeneralCommand()
       ├─ cooldown check (officer.meta["next_execute"])
       ├─ position card check (CommandGating)
       ├─ CP check + consume (CommandPointService)
       └─ command.run() → CommandResult
  → if success: persist officer/planet/faction changes
  → GameEventService.broadcastEvent → WS clients
```

### Tactical Battle (per battle turn)

```
TacticalTurnScheduler fires (or all-ready trigger)
  → TacticalBattleEngine.resolveTurn(session)
       ├─ resolve 10-step sequence (config→movement→attack→morale→death→ground→victory)
       └─ TurnResult {events, fleetSummaries, victory?}
  → messagingTemplate.convertAndSend /topic/tactical/{code}/turn-result
  → messagingTemplate.convertAndSend /topic/tactical/{code}/state
  → if victory: TacticalResultWriteback.applyResult() + destroySession(after 30s)
```

### CP Recovery (independent timer)

```
@Scheduled(fixedRate = 300_000) [every 5 real minutes]
  → for each active world:
       for each officer (locationState != "tactical"):
           pcp += BASE_RECOVERY + politics/20 + administration/20
           mcp += BASE_RECOVERY + command/20 + leadership/20
  → batch save
```

---

## Suggested Build Order

Dependencies flow bottom-up. Build in this order:

### Phase 1 — Session Foundation

**Why first:** Everything else is scoped to a session. Players can't act without joining one.

- Session creation/join/faction-selection API
- Officer-to-user binding (player gets an officer per session)
- World activation bootstrap (already partially exists)

### Phase 2 — Character and Organization

**Why second:** Commands need an officer with stats, rank, and position cards.

- Officer generation (original + canonical characters)
- 8-stat system persistence (already exists in entity)
- Position card assignment to PositionCard table (replace meta-JSON approach)
- Rank ladder (11 levels, per-faction counts)

### Phase 3 — Strategic Tick Hardening

**Why third:** The tick engine works but needs the 24x calibration and operation lifecycle.

- `tickSeconds` configuration per world
- CP recovery decoupled from turn advancement (separate scheduler)
- Operation state machine in `OfficerTurn` (SUBMITTED → EXECUTING → COMPLETED)
- `StrategicCommandDef.waitTime`/`executionTime` → turn loop enforcement

### Phase 4 — Galaxy Map and Planet Management

**Why fourth:** Operations and logistics need map coordinates and planet resources.

- 80-star-system galaxy map (grid coordinates, already have `Planet.gridX/gridY`)
- Planet resource model (production → planet warehouse → faction treasury)
- Warp/navigation distance calculation (`DistanceService` already exists)
- Planet facilities (`PlanetFacility`, `PlanetFacilityService` already exist)

### Phase 5 — Fleet and Logistics

**Why fifth:** Depends on planet warehouse model from Phase 4.

- Fleet formation and composition rules (`FleetFormationRules` already exists)
- Planet warehouse → fleet warehouse transfer (extend `반출입` command)
- Ship production pipeline (planet production → available ships)
- Conscription and manpower limits

### Phase 6 — Tactical Combat Integration

**Why sixth:** Depends on fleet data model being complete.

- Wire `BattleTrigger` → `TacticalSessionManager`
- Frontend battle entry flow (event → navigate to battle room)
- `TacticalResultWriteback` → strategic state (already exists, needs wiring)
- AI orders for NPC-controlled fleets (`TacticalAI` already exists)

### Phase 7 — Personnel and Politics

**Why seventh:** Depends on organization hierarchy from Phase 2 being persisted correctly.

- Promotion/demotion commands with position card grant/revoke
- Appointment/dismissal with PositionCard table writes
- Proposal/obedience system (`ProposalSystem` already implemented — needs HTTP/WS surface)
- Coup/rebellion lifecycle (partial implementation exists in `CommandExecutor`)

### Phase 8 — Communication Systems

**Why eighth:** Standalone, low dependency, but needs officers from Phase 2.

- In-game mail (120 message cap, address book)
- 1:1 messenger (WebSocket, same infrastructure)
- Positional chat (same grid/spot)

### Phase 9 — Victory Conditions and Session Lifecycle

**Why ninth:** Needs all game systems in place to evaluate conditions correctly.

- `UnificationService` wired into turn loop
- Victory conditions: capital capture, 3-star threshold, time limit
- Session end: freeze world, compute rankings, Hall of Fame
- World history / yearbook

---

## Anti-Patterns to Avoid

### Anti-Pattern 1: Calling TurnService from HTTP Request Threads

**What goes wrong:** Concurrent modification of officer/planet/faction state between the
scheduled turn and an HTTP request handler.
**Prevention:** HTTP endpoints for strategic commands write to `OfficerTurn` queue only.
The turn loop is the only writer to officer/planet state. `RealtimeService.submitCommand()`
is the exception — it executes immediately in real-time mode, which is fine because
`realtimeMode=true` worlds do not run the monthly turn loop.

### Anti-Pattern 2: Persisting Tactical Session State Mid-Battle

**What goes wrong:** Tactical sessions are in-memory for performance. Trying to checkpoint
them mid-battle adds complexity without benefit for a 10-40 turn battle.
**Prevention:** Keep tactical state in `TacticalSessionManager.activeSessions` (ConcurrentHashMap).
Only write to DB on battle completion via `TacticalResultWriteback`. Accept that a game-app
crash during a battle loses that battle — reconnection restores from strategic state.

### Anti-Pattern 3: Querying All Officers Per CP Recovery Tick

**What goes wrong:** `commandPointService.recoverAllCp(sessionId)` loads all officers every
5 minutes. At 2,000 players this is 2,000 DB reads + 2,000 writes per tick.
**Prevention:** Use batch queries (`findBySessionId` already used — keep it). Add dirty-check:
only save officers whose CP actually changed. At max CP, skip the save entirely.

### Anti-Pattern 4: Storing Position Card Authority in officer.meta JSON

**What goes wrong:** JSON fields cannot be queried for "who holds position X?", cannot
enforce uniqueness constraints, and make audit/history impossible.
**Prevention:** Migrate to `PositionCard` table (entity already exists). Keep `officer.meta`
as a denormalized read cache only, refreshed on card grant/revoke.

### Anti-Pattern 5: Blocking the Turn Loop on Tactical Battle Resolution

**What goes wrong:** If tactical battle turn resolution is called synchronously inside
`InMemoryTurnProcessor`, the strategic turn loop stalls for the duration of the battle.
**Prevention:** Strategic layer only creates the `TacticalGameSession` and records its
`sessionCode`. The tactical layer runs on its own timer (`TacticalTurnScheduler`), entirely
asynchronous. Strategic turn loop marks battle-participant officers as `locationState="tactical"`
and skips their turn processing until `TacticalResultWriteback` clears the flag.

### Anti-Pattern 6: Hardcoding tickSeconds in Application Logic

**What goes wrong:** Time acceleration is a game design tuning parameter. Baking `300` seconds
into code makes server-speed games impossible to configure.
**Prevention:** `SessionState.tickSeconds` is already the correct design. All time calculations
use this field. Never use hardcoded durations; derive from `world.tickSeconds`.

---

## Scalability Considerations

| Concern                 | At 200 players (MVP)            | At 2,000 players (target)           | Notes                                                                 |
| ----------------------- | ------------------------------- | ----------------------------------- | --------------------------------------------------------------------- |
| Turn loop               | Single-threaded per world, fine | Still fine — one game-app per world | Multi-world handled by process isolation                              |
| Tactical sessions       | 3-5 concurrent, in-memory       | 20-50 concurrent                    | `ConcurrentHashMap` already thread-safe                               |
| CP recovery batch       | 200 DB writes/5min              | 2,000 DB writes/5min                | Add dirty-check to skip no-change saves                               |
| WebSocket connections   | 200 STOMP connections           | 2,000 connections                   | Spring WebSocket handles this; add Redis pub/sub for horizontal scale |
| Strategic WS broadcasts | 1 broadcast/turn per world      | Same                                | Topic-based; no fan-out problem                                       |
| DB write throughput     | DirtyTracker batch writes       | Same, larger batches                | PostgreSQL 16 handles this comfortably                                |

For the 2,000-player scale target, the current single game-app process per world is the
correct architecture. Do not introduce microservice decomposition within a single game world —
the in-memory world state model (MMO source-of-truth pattern) requires colocation.

---

## Sources

- Direct codebase analysis: `/Users/apple/Desktop/openlogh/backend/game-app/src/main/kotlin/`
  (TurnDaemon, TacticalSessionManager, TacticalBattleEngine, CommandPointService,
  PositionCardSystem, CommandExecutor, InMemoryTurnProcessor, TacticalWebSocketController,
  RealtimeService, StrategicCommands, SessionState)
- [Authoritative Multiplayer — Heroic Labs / Nakama](https://heroiclabs.com/docs/nakama/concepts/multiplayer/authoritative/)
  — confirms server-authoritative tick model, match loop patterns
- [MMO Architecture: Source of truth, Dataflows, I/O bottlenecks](https://prdeving.wordpress.com/2023/09/29/mmo-architecture-source-of-truth-dataflows-i-o-bottlenecks-and-how-to-solve-them/)
  — confirms in-memory world state as source of truth (not DB), selective persistence pattern
- [Lance.gg Architecture](https://lance-gg.github.io/docs_out/tutorial-overview_architecture.html)
  — server authoritative game loop, client reconciliation pattern
- [Making Games Tick Part 2 — Mathieu Ropert](https://mropert.github.io/2025/04/30/making_games_tick_part2/)
  — top-down explicit task model preferred over per-object tick() (matches current InMemoryTurnProcessor design)
- [Spring Boot WebSocket STOMP — Toptal](https://www.toptal.com/java/stomp-spring-boot-websocket)
  — session isolation via topic namespacing (matches current /topic/tactical/{code} design)
- gin7 manual (54p) — referenced via CLAUDE.md and PROJECT.md for game mechanics authority

---

_Architecture analysis: 2026-03-28_
