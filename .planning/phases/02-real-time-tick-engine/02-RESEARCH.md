# Phase 2: Real-time Tick Engine - Research

**Researched:** 2026-04-05
**Domain:** Game engine scheduling, real-time tick processing, game clock model
**Confidence:** HIGH

## Summary

The existing codebase already has a **dual-mode architecture**: `TurnDaemon` dispatches either to `TurnService` (turn-based monthly processing) or `RealtimeService` (CP-based command scheduling) based on `SessionState.realtimeMode`. The current turn-based mode uses `tickSeconds` (default 300s = 5min) as the interval between monthly advances. The real-time mode already supports command scheduling with `commandEndTime`, CP deduction, and WebSocket notifications, but lacks a proper game clock advancement mechanism.

Phase 2 must convert the system from "advance one game-month per tick" to "advance 24 game-seconds per tick (1-second server tick)." This requires: (1) a new game clock model with sub-month granularity, (2) converting the tick interval from 5000ms to 1000ms, (3) decoupling monthly processing (economy, diplomacy, maintenance) from the tick loop so it fires only when the game clock crosses a month boundary, (4) implementing CP regeneration on a 5-real-minute cycle, and (5) broadcasting tick state to clients via WebSocket.

**Primary recommendation:** Add `gameTimeSec` (Long) to `SessionState` to track sub-month game time in seconds. Each 1-second tick adds 24 to this counter. When `gameTimeSec` crosses a month boundary (108,000 game-seconds = 30 real-hours), trigger the existing monthly pipeline. Keep the current `TurnPipeline` steps intact; only change when they fire.

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| ENG-01 | 1-second server tick = 24 game-seconds (24x speed) | TurnDaemon @Scheduled interval change from 5000ms to 1000ms; new gameTimeSec accumulator |
| ENG-02 | 30 real-time hours = 1 game month | 108,000 game-seconds per month; month boundary detection in tick loop |
| ENG-03 | CP regenerates every 5 real-time minutes | Decouple from monthly cycle; track lastCpRegenTime per officer or use modular tick counter |
| ENG-04 | Commands execute with real-time duration waits | Already implemented in RealtimeService.scheduleCommand; needs integration with new tick engine |
</phase_requirements>

## Standard Stack

### Core (Already in Project)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot | 3.4.2 | Application framework | Project standard |
| Spring @Scheduled | 3.4.2 | Tick scheduling | Already used by TurnDaemon |
| Spring WebSocket (STOMP) | 3.4.2 | Client broadcasting | Already used by GameEventService |
| Spring Data JPA | 3.4.2 | Persistence | Already used for all entities |
| PostgreSQL | 16 | Game state storage | Project standard |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| java.util.concurrent.ScheduledExecutorService | JDK 17 | Alternative to @Scheduled for precise 1s ticks | If @Scheduled jitter is unacceptable |
| java.time.Instant / OffsetDateTime | JDK 17 | Time calculations | Already used throughout codebase |
| AtomicLong / ReentrantLock | JDK 17 | Thread-safe tick counter | For concurrent access to game clock |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| @Scheduled(fixedDelay=1000) | ScheduledExecutorService | More control but more boilerplate; @Scheduled is already proven in codebase |
| Kotlin coroutines ticker | @Scheduled | Coroutines add complexity; codebase uses runBlocking sparingly |
| Redis-based distributed clock | JVM-local clock | Only needed for multi-instance game-app; current architecture is single game-app per session |

## Architecture Patterns

### Current Architecture (What Exists)

```
TurnDaemon (@Scheduled fixedDelay=5000ms)
  |
  +-- for each world:
  |     +-- if realtimeMode:
  |     |     RealtimeService.processCompletedCommands()
  |     |     RealtimeService.regenerateCommandPoints()
  |     +-- else:
  |           TurnService.processWorld()
  |             +-- while (now >= nextTurnAt):
  |             |     executeGeneralCommandsUntil()
  |             |     advanceMonth()
  |             |     TurnPipeline.execute() (economy, diplomacy, etc.)
  |             +-- save world
```

### Target Architecture (Phase 2)

```
TickDaemon (@Scheduled fixedRate=1000ms)
  |
  +-- for each world:
        +-- advanceGameClock(+24 game-seconds)
        +-- processCompletedCommands()  // commands with elapsed duration
        +-- if (gameTimeSec crosses 5-real-min boundary):
        |     regenerateCommandPoints()
        +-- if (gameTimeSec crosses month boundary):
        |     TurnPipeline.execute()    // economy, diplomacy, maintenance
        +-- broadcastTickState()        // WebSocket: current game time, next events
```

### Key Design Decision: Game Clock Model

The game clock must track time below month granularity. Two options:

**Option A (Recommended): `gameTimeSec` accumulator on SessionState**
- Add `game_time_sec BIGINT NOT NULL DEFAULT 0` to `session_state`
- Each tick: `gameTimeSec += 24`
- Month boundary: `gameTimeSec >= GAME_SECONDS_PER_MONTH (108,000)`
- On month cross: reset `gameTimeSec -= 108_000`, call `advanceMonth()`, run pipeline
- Simple, deterministic, no floating-point drift

**Option B: Epoch-based game time**
- Store `gameEpochSec` as absolute game time since scenario start
- Derive year/month from epoch: `month = (gameEpochSec / 108_000) + startMonth`
- More flexible but requires changing all year/month access patterns

Option A is recommended because it preserves the existing `currentYear`/`currentMonth` fields and minimizes changes to downstream code that reads them.

### Game Time Constants

```kotlin
object GameTimeConstants {
    const val TICK_INTERVAL_MS = 1_000L           // 1 real second
    const val GAME_SECONDS_PER_TICK = 24          // 24x speed
    const val GAME_SECONDS_PER_MINUTE = 24 * 60   // 1,440
    const val GAME_SECONDS_PER_HOUR = 24 * 3600    // 86,400
    const val GAME_SECONDS_PER_MONTH = 108_000     // 30 real hours * 3600 / 1 * 24... 
    // Actually: 30 real hours = 30*3600 = 108,000 real seconds
    // At 24x: 108,000 * 24 = 2,592,000 game-seconds per month
    // OR: 1 month = 30 real hours = 108,000 ticks (at 1 tick/sec)
    // Each tick = 24 game-seconds, so 108,000 ticks * 24 = 2,592,000 game-seconds/month
    
    // CP regen: every 5 real minutes = 300 real seconds = 300 ticks
    const val CP_REGEN_INTERVAL_TICKS = 300       // 5 real minutes
}
```

**Critical math verification:**
- 1 real second = 1 tick = 24 game-seconds
- 1 real minute = 60 ticks = 1,440 game-seconds = 24 game-minutes
- 1 real hour = 3,600 ticks = 86,400 game-seconds = 1 game-day (24 hours)
- 30 real hours = 108,000 ticks = 2,592,000 game-seconds = 30 game-days = 1 game-month
- 360 real hours (15 days) = 12 game-months = 1 game-year
- CP regen: 300 ticks = 5 real minutes = 7,200 game-seconds

### Pattern 1: Tick Counter for Sub-Tick Events

Rather than checking wall-clock time for CP regen, use a simple modular tick counter:

```kotlin
// In the tick loop:
val tickCount = world.tickCount  // persistent counter, incremented each tick

// CP regen every 300 ticks (5 real minutes)
if (tickCount % CP_REGEN_INTERVAL_TICKS == 0L) {
    regenerateCommandPoints(world)
}

// Month boundary every 108,000 ticks
if (tickCount % TICKS_PER_MONTH == 0L && tickCount > 0) {
    advanceMonth(world)
    runMonthlyPipeline(world)
}
```

### Pattern 2: Command Duration with Game-Time Awareness

The existing `RealtimeService.scheduleCommand()` sets `commandEndTime = now + duration seconds` using wall-clock time. This is correct for real-time mode and does not need to change. The `processCompletedCommands()` method already checks `commandEndTime < now`.

### Anti-Patterns to Avoid
- **Floating-point game time:** Use integer seconds, never fractional time. Floating-point accumulation causes drift over long sessions.
- **Wall-clock month detection:** Do not use `System.currentTimeMillis()` to detect month boundaries. Use the tick counter for determinism.
- **Processing all monthly steps every tick:** The monthly pipeline (economy, diplomacy, NPC spawn, etc.) is expensive. Guard it strictly behind month-boundary detection.
- **Blocking the tick thread:** Monthly processing can take 100ms+. If it blocks, subsequent ticks queue up. Consider running monthly processing asynchronously or accepting tick jitter during month transitions.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Tick scheduling | Custom thread loop | Spring @Scheduled(fixedRate=1000) | Already battle-tested in codebase; handles thread pool, error recovery |
| WebSocket broadcasting | Custom socket management | SimpMessagingTemplate (already in GameEventService) | STOMP protocol, session management built in |
| Thread-safe state updates | Manual synchronization | @Transactional + JPA optimistic locking | Database is the source of truth; avoid in-memory race conditions |
| Game time formatting | Custom date string builder | Simple utility converting gameTimeSec to UC date string | But keep it simple -- just division/modulo |

## Common Pitfalls

### Pitfall 1: Tick Drift Under Load
**What goes wrong:** Monthly processing takes 200ms, causing the next tick to fire late. Over time, game clock drifts behind real time.
**Why it happens:** `@Scheduled(fixedDelay=1000)` waits 1000ms AFTER completion. `fixedRate=1000` tries to maintain rate but queues if processing exceeds interval.
**How to avoid:** Use `fixedRate` (not `fixedDelay`). Accept that occasional ticks may be slightly late. The tick counter approach (not wall-clock) ensures no accumulated drift in game time. If a tick is delayed, the next tick catches up.
**Warning signs:** Log messages showing tick processing time >500ms regularly.

### Pitfall 2: CP Regen Tied to Monthly Cycle
**What goes wrong:** Current `RealtimeService.regenerateCommandPoints()` is called every tick (5s interval). At 1-second ticks, this would regenerate CP every second instead of every 5 minutes.
**Why it happens:** The current code unconditionally calls regen on every daemon tick.
**How to avoid:** Gate CP regen behind a tick counter modulo check (every 300 ticks = 5 real minutes). Or track `lastCpRegenTime` on the officer/world.
**Warning signs:** Officers accumulating CP instantly to maximum.

### Pitfall 3: Month Boundary Race Condition
**What goes wrong:** Two concurrent ticks both detect the month boundary and run the monthly pipeline twice.
**Why it happens:** `TurnDaemon` is single-threaded via `@Scheduled` with state guard (`DaemonState.IDLE` check), so this is unlikely in current design. But if parallelized per-world, it becomes a risk.
**How to avoid:** Keep single-threaded tick processing per world (current pattern). The `DaemonState` guard already prevents concurrent tick runs.
**Warning signs:** Duplicate economy calculations, double CP recovery.

### Pitfall 4: Existing Turn-Based Code Assumes Monthly Granularity
**What goes wrong:** `TurnService.executeGeneralCommandsUntil()` processes ALL officers' commands per month. In real-time mode, commands are already individually scheduled. Mixing the two would double-execute commands.
**Why it happens:** The turn-based path processes commands in batch; the real-time path processes them individually.
**How to avoid:** Phase 2 should make ALL worlds use the real-time command path. The turn-based batch command processing path becomes dead code (or kept for legacy compatibility). The monthly pipeline still runs for economy/diplomacy but NOT for command execution.
**Warning signs:** Commands executing twice, unexpected state changes on month boundary.

### Pitfall 5: Frontend Polling vs Push for Game Clock
**What goes wrong:** Frontend polls `/api/world/status` every second to show game time, creating N*1 requests/second per player.
**Why it happens:** No tick broadcast channel exists yet.
**How to avoid:** Broadcast game time via WebSocket on a reduced frequency (every 5-10 ticks). Frontend interpolates between broadcasts. Include `gameTimeSec` and `serverTimestamp` so client can calculate drift.
**Warning signs:** API request volume scaling linearly with player count.

## Code Examples

### Example 1: Game Time Accumulator (New SessionState Fields)

```kotlin
// SessionState.kt additions
@Column(name = "game_time_sec", nullable = false)
var gameTimeSec: Long = 0,

@Column(name = "tick_count", nullable = false)
var tickCount: Long = 0,
```

### Example 2: Tick Processing Loop

```kotlin
// TickEngine.kt (replaces TurnDaemon tick logic for real-time worlds)
fun processTick(world: SessionState) {
    world.tickCount++
    world.gameTimeSec += GAME_SECONDS_PER_TICK  // +24

    // Process completed commands
    realtimeService.processCompletedCommands(world)

    // CP regeneration every 5 real minutes (300 ticks)
    if (world.tickCount % CP_REGEN_INTERVAL_TICKS == 0L) {
        realtimeService.regenerateCommandPoints(world)
    }

    // Month boundary
    if (world.gameTimeSec >= GAME_SECONDS_PER_MONTH) {
        world.gameTimeSec -= GAME_SECONDS_PER_MONTH
        advanceMonth(world)
        turnPipeline.execute(buildTurnContext(world))
    }

    sessionStateRepository.save(world)

    // Broadcast to clients every 10 ticks (10 seconds)
    if (world.tickCount % 10 == 0L) {
        gameEventService.broadcastTickState(world)
    }
}
```

### Example 3: Flyway Migration

```sql
-- V29__add_game_time_fields.sql
ALTER TABLE session_state ADD COLUMN game_time_sec BIGINT NOT NULL DEFAULT 0;
ALTER TABLE session_state ADD COLUMN tick_count BIGINT NOT NULL DEFAULT 0;
```

### Example 4: WebSocket Tick Broadcast

```kotlin
// GameEventService.kt addition
fun broadcastTickState(world: SessionState) {
    messagingTemplate.convertAndSend(
        "/topic/world/${world.id}/tick",
        mapOf(
            "gameTimeSec" to world.gameTimeSec,
            "tickCount" to world.tickCount,
            "year" to world.currentYear,
            "month" to world.currentMonth,
            "gameDayOfMonth" to (world.gameTimeSec / 86_400 + 1),  // approx
            "gameHour" to ((world.gameTimeSec % 86_400) / 3600),
            "serverTimestamp" to System.currentTimeMillis(),
        )
    )
}
```

## State of the Art

| Old Approach (Current) | New Approach (Phase 2) | When Changed | Impact |
|------------------------|------------------------|--------------|--------|
| 1 tick = 1 game month (tickSeconds=300) | 1 tick = 24 game seconds (1s interval) | Phase 2 | Fundamental game pacing change |
| Batch command execution per month | Individual command scheduling with duration | Partially exists (RealtimeService) | All worlds use realtime path |
| CP regen per daemon tick (every 5s) | CP regen every 300 ticks (5 real minutes) | Phase 2 | Prevents instant CP flooding |
| No sub-month time display | UC calendar with day/hour display | Phase 2 | Frontend shows real-time game clock |

## Open Questions

1. **Backward compatibility with turn-based mode**
   - What we know: The current codebase supports both `realtimeMode=true` and `realtimeMode=false`. Phase 2 makes all new worlds real-time.
   - What's unclear: Should the turn-based path be removed, or kept for legacy/testing? Do existing scenarios need migration?
   - Recommendation: Keep both paths but default new worlds to `realtimeMode=true`. Add a migration to set existing worlds. The turn-based path can serve as a "fast-forward" debug tool.

2. **Monthly pipeline performance at 1-second ticks**
   - What we know: Monthly processing includes economy, diplomacy, NPC AI, maintenance. Current processing can take 100-500ms for complex worlds.
   - What's unclear: Will monthly processing block the 1-second tick loop causing cascading delays?
   - Recommendation: Accept occasional jitter during month transitions. Monitor tick duration. If needed later, run monthly pipeline in a separate thread with a completion flag.

3. **Tick count persistence frequency**
   - What we know: Saving `SessionState` to DB every tick (every 1s) adds write load.
   - What's unclear: Is PostgreSQL write throughput sufficient for 1 write/second per world?
   - Recommendation: PostgreSQL easily handles this (single row UPDATE). For optimization later, batch save every N ticks and recover from `lastSaveTime` on restart.

4. **Command duration values**
   - What we know: Most commands have `getDuration() = 300` (300 seconds = 5 minutes). This was hardcoded for the existing realtime mode.
   - What's unclear: Should durations change with the new 24x game clock? gin7 manual specifies execution wait times per command.
   - Recommendation: Keep current duration values for Phase 2 (they represent real-time seconds). Phase 4 (Position Cards) will refine per-command durations per gin7 manual.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 (Jupiter) + Spring Boot Test |
| Config file | `backend/game-app/build.gradle.kts` (JUnit 5 dependency) |
| Quick run command | `cd /Users/apple/Desktop/개인프로젝트/openlogh/backend && ./gradlew :game-app:test --tests "com.openlogh.engine.*" -x bootJar` |
| Full suite command | `cd /Users/apple/Desktop/개인프로젝트/openlogh/backend && ./gradlew :game-app:test -x bootJar` |

### Phase Requirements to Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| ENG-01 | 1 tick advances gameTimeSec by 24 | unit | `./gradlew :game-app:test --tests "com.openlogh.engine.TickEngineTest" -x bootJar` | Wave 0 |
| ENG-02 | 108,000 ticks = 1 month advance | unit | `./gradlew :game-app:test --tests "com.openlogh.engine.TickEngineTest" -x bootJar` | Wave 0 |
| ENG-03 | CP regen every 300 ticks | unit | `./gradlew :game-app:test --tests "com.openlogh.engine.TickEngineTest" -x bootJar` | Wave 0 |
| ENG-04 | Command duration countdown | unit | `./gradlew :game-app:test --tests "com.openlogh.engine.RealtimeServiceTest" -x bootJar` | Wave 0 |

### Sampling Rate
- **Per task commit:** `./gradlew :game-app:test --tests "com.openlogh.engine.*" -x bootJar`
- **Per wave merge:** `./gradlew :game-app:test -x bootJar`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `backend/game-app/src/test/kotlin/com/openlogh/engine/TickEngineTest.kt` -- covers ENG-01, ENG-02, ENG-03
- [ ] `backend/game-app/src/test/kotlin/com/openlogh/engine/GameTimeConstantsTest.kt` -- covers time math verification
- [ ] Existing `InMemoryTurnHarness.kt` can be extended for tick testing

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| PostgreSQL | DB migrations | Required (Docker) | 16 | -- |
| Redis | Session cache | Required (Docker) | 7 | -- |
| JDK | Compilation | Required | 17+ | -- |
| Gradle | Build | Required | 8.x (wrapper) | -- |

No external dependencies beyond what the project already uses. Phase 2 is purely internal engine changes.

## Sources

### Primary (HIGH confidence)
- Project source code: `TurnDaemon.kt`, `TurnService.kt`, `RealtimeService.kt`, `CommandService.kt` -- direct reading
- Project source code: `SessionState.kt`, `Officer.kt` entity definitions -- direct reading
- Project source code: `application.yml` -- scheduling config (`app.turn.interval-ms: 5000`)
- Project source code: `V1__core_tables.sql` -- DB schema for session_state, officer tables
- `CLAUDE.md` -- gin7 time system reference (1 real sec = 24 game sec)
- `.planning/ROADMAP.md` -- Phase 2 requirements (ENG-01 through ENG-04)

### Secondary (MEDIUM confidence)
- Spring Framework @Scheduled documentation -- fixedRate vs fixedDelay semantics (well-known, stable API)
- gin7 manual time flow (p.9) -- referenced in CLAUDE.md but not directly read

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - all libraries already in project, no new dependencies
- Architecture: HIGH - existing dual-mode (turn/realtime) architecture provides clear migration path
- Pitfalls: HIGH - identified from direct code reading of current tick/turn processing
- Game time math: HIGH - verified arithmetic: 30h * 3600s/h = 108,000 ticks; 108,000 * 24 = 2,592,000 game-sec

**Research date:** 2026-04-05
**Valid until:** 2026-05-05 (stable -- internal engine, no external API changes)
