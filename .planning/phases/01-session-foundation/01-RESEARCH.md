# Phase 1: Session Foundation - Research

**Researched:** 2026-03-28
**Domain:** Session lifecycle management, JPA concurrency control, Spring executor lifecycle, faction-balanced joining
**Confidence:** HIGH

## Summary

Phase 1 is the first deliverable phase of Open LOGH. It builds on a substantial existing codebase that already has world CRUD (gateway WorldController), officer creation (OfficerService.createOfficer), scenario loading (ScenarioService), and a lobby frontend. The work divides into three clear domains: (1) extending session creation with scenario-based faction selection and 3:2 ratio enforcement, (2) fixing two exploit-grade concurrency bugs (HARD-01: Officer @Version for CP race condition, HARD-02: executor thread leak in TacticalWebSocketController), and (3) implementing offline officer persistence with CP recovery.

The codebase is Spring Boot 3.4.2 (Kotlin 2.1.0) with JPA/Hibernate on PostgreSQL 16. The frontend is Next.js with Zustand stores and an existing lobby page at `(lobby)/lobby/page.tsx`. Key finding: `@Version` is not used anywhere in the backend -- zero files contain it. The thread leak is precisely located at line 218 of `TacticalWebSocketController.kt` where `Executors.newSingleThreadScheduledExecutor()` creates an unmanaged executor per battle end. The `TacticalTurnScheduler` already demonstrates the correct pattern with a `@PreDestroy` shutdown method and a managed thread pool.

**Primary recommendation:** Fix HARD-01 and HARD-02 first (they are surgical, low-risk changes that eliminate data corruption), then build session creation/join with faction ratio enforcement, then add offline CP recovery to the existing `CommandPointService.recoverAllCp()` flow.

<user_constraints>

## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** 진영 인원 비율 하드캡 3:2 적용 (한 진영에 전체의 최대 60%)
- **D-02:** 캡 도달 시 메시지로 차단 ("제국군 인원이 가득 찼습니다 -- 동맹에 참가하거나 자리가 날 때까지 기다려주세요"). 플레이어는 로비에서 대기 가능
- **D-03:** 세션 생성 시 시나리오 선택만 제공. 나머지 설정(24배속, 2000명 상한, 승리조건 등)은 시나리오 JSON에 정의된 기본값 사용
- **D-04:** 로비 세션 목록에 핵심 정보만 표시: 시나리오명, 현재 인원(제국/동맹 각각), 게임 내 날짜, 상태(모집중/진행중)
- **D-05:** 오프라인 장교는 온라인과 동일하게 표시 -- 온/오프라인 구분 아이콘 없음 (gin7 원작 충실). 다른 플레이어는 누가 오프라인인지 알 수 없음
- **D-06:** 오프라인 중 CP 회복 + 기본 상태 변화 (이동 중 함대는 계속 이동 등) 구현. 체포/인사/AI대행은 해당 Phase에서 구현
- **D-07:** '퇴장'은 캐릭터 사망(전사)만 해당. 자발적 로그아웃은 오프라인 지속이지 퇴장이 아님
- **D-08:** 퇴장 후 즉시 재입장 가능 (쿨다운 없음)
- **D-09:** 재입장 제한: 같은 진영에만 복귀 가능 + 원작 캐릭터 사용 불가 (제네레이트 캐릭터만)

### Claude's Discretion

- HARD-01 (@Version 추가) 및 HARD-02 (executor 스레드 누수) 수정의 구체적 구현 방식
- DB 스키마 변경이 필요한 경우 Flyway 마이그레이션 구조
- 시나리오 JSON 스키마에 faction ratio 설정 추가 여부

### Deferred Ideas (OUT OF SCOPE)

None -- discussion stayed within phase scope
</user_constraints>

<phase_requirements>

## Phase Requirements

| ID      | Description                                                | Research Support                                                                                                                                                                               |
| ------- | ---------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| SESS-01 | 시나리오 선택하여 새 게임 세션 생성 (P0)                   | Existing WorldController.createWorld + ScenarioService provide full creation flow. D-03 simplifies: only scenario selection needed, defaults from scenario JSON                                |
| SESS-02 | 기존 세션에 캐릭터 선택하여 참가, 최대 2,000명 (P0)        | OfficerService.createOfficer already validates max officer count. Need to add faction ratio check (D-01: 3:2 cap)                                                                              |
| SESS-03 | 제국/동맹 진영 선택 (P0)                                   | Faction entity exists with factionType field. Join page needs faction picker with ratio validation                                                                                             |
| SESS-06 | 게임 시간 실시간 24배속 (P1)                               | SessionState.tickSeconds already exists (default 300 = 5min turns). Scenario JSON provides config. 24x = 1 game hour per 2.5 real minutes                                                      |
| SESS-07 | 퇴장 플레이어 재등록 제한 (P2)                             | ReregistrationService already implements core logic: ejected players must rejoin same faction, cannot use original character (D-07/D-08/D-09 align exactly)                                    |
| SMGT-01 | 오프라인 지속 (캐릭터 세계 존재, CP 계속 회복) (P0)        | CommandPointService.recoverAllCp iterates ALL officers regardless of online status. No online/offline flag exists -- this is correct per D-05. Need to verify turn daemon calls recoverAllCp   |
| HARD-01 | Officer 엔티티 @Version 추가 (CP race condition 해소) (P0) | Officer entity has NO @Version field. CommandExecutor.consume modifies pcp/mcp without optimistic lock. Fix: add @Version Long field + Flyway migration + retry-on-conflict handler            |
| HARD-02 | 전술전 executor 스레드 누수 수정 (P0)                      | TacticalWebSocketController line 218: `Executors.newSingleThreadScheduledExecutor().schedule(...)` creates orphaned executor per battle end. Fix: use TacticalTurnScheduler's managed executor |

</phase_requirements>

## Standard Stack

### Core (already in project -- no new dependencies needed)

| Library         | Version    | Purpose                                | Why Standard                                 |
| --------------- | ---------- | -------------------------------------- | -------------------------------------------- |
| Spring Boot     | 3.4.2      | Web framework, JPA, WebSocket          | Already in project                           |
| Spring Data JPA | (via Boot) | ORM with `@Version` optimistic locking | Already in project, @Version is JPA standard |
| Kotlin          | 2.1.0      | Backend language                       | Already in project                           |
| PostgreSQL      | 16         | Database with JSONB support            | Already in project                           |
| Flyway          | (via Boot) | Schema migrations                      | Already in project, next migration = V38     |
| Next.js         | 16.1.6     | Frontend framework                     | Already in project                           |
| Zustand         | 5.0.11     | State management                       | Already in project                           |
| Vitest          | 3.2.4      | Frontend unit testing                  | Already in project                           |
| JUnit 5         | (via Boot) | Backend unit testing                   | Already in project                           |

### Alternatives Considered

| Instead of                | Could Use                  | Tradeoff                                                                                                                    |
| ------------------------- | -------------------------- | --------------------------------------------------------------------------------------------------------------------------- |
| JPA @Version (optimistic) | @Lock(PESSIMISTIC_WRITE)   | Pessimistic adds DB-level lock contention. Optimistic is better for reads >> writes (CP checks). Use optimistic for HARD-01 |
| Managed ScheduledExecutor | Spring @Async + @Scheduled | @Async adds more complexity. Reuse TacticalTurnScheduler's existing managed executor for HARD-02                            |
| Flyway SQL migration      | JPA auto-DDL               | Flyway is already the migration pattern. Auto-DDL is dangerous in production                                                |

**No new packages need to be installed.** All changes use existing dependencies.

## Architecture Patterns

### Recommended Changes by Component

```
backend/game-app/
  src/main/kotlin/com/openlogh/
    entity/Officer.kt                    # Add @Version field
    websocket/TacticalWebSocketController.kt  # Fix executor leak
    service/OfficerService.kt            # Add faction ratio validation
    service/FactionJoinService.kt        # NEW: encapsulate 3:2 ratio logic
    controller/OfficerController.kt      # Wire faction ratio check
  src/main/resources/db/migration/
    V38__add_officer_version_column.sql   # NEW: version column for @Version
  src/test/kotlin/com/openlogh/
    service/FactionJoinServiceTest.kt     # NEW: ratio enforcement tests
    entity/OfficerOptimisticLockTest.kt   # NEW: @Version concurrency test
    websocket/TacticalExecutorLeakTest.kt # NEW: verify no leaked threads

frontend/src/
  app/(lobby)/lobby/join/page.tsx         # Add faction picker with ratio display
  app/(lobby)/lobby/page.tsx              # Enhance session list per D-04
  lib/gameApi.ts                          # Add faction count API call
  types/index.ts                          # Add FactionCount type
```

### Pattern 1: JPA Optimistic Locking with @Version

**What:** Add `@Version` annotation to Officer entity to prevent concurrent CP mutation
**When to use:** Any entity where concurrent writes can corrupt state (Officer.pcp/mcp)
**Implementation:**

```kotlin
// Officer.kt - add version field
@Version
@Column(name = "version", nullable = false)
var version: Long = 0
```

The JPA provider automatically increments the version on each UPDATE and adds `WHERE version = ?` to the UPDATE statement. If two transactions read the same version and both try to write, the second one gets `OptimisticLockingFailureException`.

**Retry pattern:** The command executor (or a service wrapper) should catch `OptimisticLockingFailureException` and retry the read-modify-write cycle up to 3 times. This is standard for optimistic locking.

```kotlin
// Service-level retry pattern
fun executeWithRetry(officerId: Long, action: (Officer) -> CommandResult): CommandResult {
    repeat(3) { attempt ->
        try {
            val officer = officerRepository.findById(officerId).orElseThrow()
            val result = action(officer)
            officerRepository.save(officer)
            return result
        } catch (e: OptimisticLockingFailureException) {
            if (attempt == 2) throw e
            // retry with fresh read
        }
    }
    throw IllegalStateException("Optimistic lock retry exhausted")
}
```

### Pattern 2: Managed Executor for Delayed Cleanup

**What:** Replace ad-hoc `Executors.newSingleThreadScheduledExecutor()` with the existing managed executor
**When to use:** Any scheduled/delayed task in Spring-managed components
**Implementation:**

```kotlin
// BEFORE (leaks threads):
java.util.concurrent.Executors.newSingleThreadScheduledExecutor().schedule({
    sessionManager.destroySession(sessionCode)
}, 30, java.util.concurrent.TimeUnit.SECONDS)

// AFTER (uses managed executor from TacticalTurnScheduler):
turnScheduler.scheduleDelayedCleanup(sessionCode, 30) {
    sessionManager.destroySession(sessionCode)
}
```

Or inject a Spring-managed `ScheduledExecutorService` bean:

```kotlin
@Bean
fun tacticalCleanupExecutor(): ScheduledExecutorService =
    Executors.newSingleThreadScheduledExecutor().also {
        // Register for Spring lifecycle shutdown
    }
```

The simplest fix: TacticalTurnScheduler already has a managed `executor` field with `@PreDestroy shutdown()`. Add a public method to schedule delayed tasks on it.

### Pattern 3: Faction Ratio Enforcement

**What:** 3:2 hard cap on faction join -- no faction can exceed 60% of total players
**When to use:** On every officer creation (SESS-02 + SESS-03)
**Implementation:**

```kotlin
// FactionJoinService.kt
fun canJoinFaction(sessionId: Long, targetFactionId: Long): FactionJoinResult {
    val factions = factionRepository.findBySessionId(sessionId)
    val officers = officerRepository.findBySessionId(sessionId)

    val totalPlayers = officers.count { it.userId != null }
    val targetCount = officers.count { it.factionId == targetFactionId && it.userId != null }

    // 3:2 ratio: max 60% in one faction
    val maxForFaction = ((totalPlayers + 1) * 3) / 5  // ceiling of 60%
    if (targetCount >= maxForFaction && totalPlayers > 1) {
        return FactionJoinResult(allowed = false, reason = "제국군 인원이 가득 찼습니다...")
    }
    return FactionJoinResult(allowed = true)
}
```

### Anti-Patterns to Avoid

- **Raw thread creation in Spring beans:** Never use `Executors.newXxx()` inline. Always use Spring-managed executors with `@PreDestroy` shutdown
- **Saving entities without @Version check:** After adding @Version, never bypass the version check by manually setting the version field
- **Checking faction count without transaction:** The ratio check and officer insert MUST be in the same `@Transactional` block to prevent TOCTOU race
- **Modifying Officer.pcp/mcp outside CommandExecutor:** All CP mutations must go through the retry-protected path

## Don't Hand-Roll

| Problem                     | Don't Build                            | Use Instead                                                       | Why                                                                                                |
| --------------------------- | -------------------------------------- | ----------------------------------------------------------------- | -------------------------------------------------------------------------------------------------- |
| Concurrent CP write safety  | Custom locking mechanism               | JPA `@Version` + `OptimisticLockingFailureException` retry        | JPA @Version is battle-tested, transparent to existing code, requires only 1 annotation + 1 column |
| Thread lifecycle management | Manual Thread/ExecutorService tracking | Spring-managed `ScheduledExecutorService` bean with `@PreDestroy` | Spring handles shutdown on context close; manual tracking always has edge cases                    |
| Faction ratio math          | Custom percentage calculation          | Simple integer arithmetic: `(total * 3) / 5` ceiling check        | Avoid floating-point rounding issues; gin7 uses integer-based caps                                 |
| Re-registration restriction | New service                            | Existing `ReregistrationService`                                  | Already implements D-07/D-08/D-09 exactly                                                          |
| Scenario config defaults    | Manual parameter parsing               | Existing `ScenarioService` scenario JSON loading                  | D-03 says use scenario defaults; ScenarioService already parses them                               |

**Key insight:** This phase requires surprisingly little NEW code. The existing codebase already has 80% of the session/officer/faction infrastructure. The work is mostly: (a) adding @Version, (b) fixing one line of thread leak, (c) adding faction ratio validation to the existing join flow, and (d) verifying CP recovery already covers offline officers.

## Common Pitfalls

### Pitfall 1: @Version Column Type Mismatch

**What goes wrong:** Adding `var version: Long = 0` but the Flyway migration creates `INT` instead of `BIGINT`
**Why it happens:** Kotlin `Long` maps to Java `long` which maps to `BIGINT` in PostgreSQL. If migration uses `INT`, Hibernate throws type mismatch errors.
**How to avoid:** Use `BIGINT NOT NULL DEFAULT 0` in the Flyway migration
**Warning signs:** `SchemaManagementException` on startup, `PSQLException: column "version" is of type integer`

### Pitfall 2: OptimisticLockingFailureException Not Caught

**What goes wrong:** After adding @Version, any concurrent save throws an exception that bubbles up as HTTP 500 to the client
**Why it happens:** The existing code has no exception handling for this exception type because @Version didn't exist before
**How to avoid:** Add a retry wrapper around CommandExecutor CP consumption, and add `@ControllerAdvice` handler for `ObjectOptimisticLockingFailureException`
**Warning signs:** Intermittent 500 errors on command execution under any concurrent load

### Pitfall 3: Faction Ratio TOCTOU Race

**What goes wrong:** Two players simultaneously pass the ratio check and both join, exceeding the cap
**Why it happens:** Check-then-act without transactional isolation
**How to avoid:** Use `@Transactional(isolation = Isolation.SERIALIZABLE)` or `SELECT ... FOR UPDATE` on the faction count query. Alternatively, use a DB constraint (CHECK constraint on a materialized count) but that's complex. Simplest: SERIALIZABLE transaction on the join operation.
**Warning signs:** Faction player count exceeds 60% in test with concurrent joins

### Pitfall 4: Existing Tests Use Legacy Aliases

**What goes wrong:** Tests use `General`, `City`, `Nation`, `WorldState` (legacy OpenSamguk names) instead of `Officer`, `Planet`, `Faction`, `SessionState`
**Why it happens:** The codebase has compatibility aliases in entity constructors (see Officer.kt init block with `worldId`, `nationId`, `cityId` params)
**How to avoid:** New tests should use the LOGH names (Officer, Planet, Faction). Existing tests use legacy aliases through constructor params -- this is fine, don't break them
**Warning signs:** Confusion about which class is canonical. The JPA entity name is the canonical one.

### Pitfall 5: Executor Leak Fix Breaks Battle Timing

**What goes wrong:** Replacing the inline executor with a shared one causes the 30-second delayed cleanup to interfere with ongoing battle turns
**Why it happens:** TacticalTurnScheduler's executor has 4 threads. If all 4 are busy with active battle turn timers, the cleanup task is queued and delayed
**How to avoid:** Either add a 5th thread to the pool, or use a separate single-thread executor (but managed as a Spring `@Bean` with `@PreDestroy`). The safest fix: add a `scheduleDelayedCleanup` method to `TacticalTurnScheduler` that uses its existing executor
**Warning signs:** Battle sessions not being destroyed after 30s under high concurrent battle load

## Code Examples

### HARD-01: Adding @Version to Officer Entity

```kotlin
// Officer.kt - add after the id field
@Version
@Column(name = "version", nullable = false)
var version: Long = 0,
```

```sql
-- V38__add_officer_version_column.sql
ALTER TABLE officer ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
```

### HARD-02: Fixing Executor Thread Leak

```kotlin
// TacticalWebSocketController.kt line 218 - REPLACE this block:

// BEFORE:
// java.util.concurrent.Executors.newSingleThreadScheduledExecutor().schedule({
//     sessionManager.destroySession(sessionCode)
// }, 30, java.util.concurrent.TimeUnit.SECONDS)

// AFTER - Option A: Use TacticalTurnScheduler's managed executor
turnScheduler.scheduleOnce(30, TimeUnit.SECONDS) {
    sessionManager.destroySession(sessionCode)
}

// TacticalTurnScheduler.kt - add this method:
fun scheduleOnce(delay: Long, unit: TimeUnit, task: () -> Unit): ScheduledFuture<*> {
    return executor.schedule({
        try { task() } catch (e: Exception) {
            log.error("Scheduled cleanup task failed", e)
        }
    }, delay, unit)
}
```

### Faction Ratio Enforcement

```kotlin
// FactionJoinService.kt (new file)
@Service
class FactionJoinService(
    private val officerRepository: OfficerRepository,
    private val factionRepository: FactionRepository,
) {
    companion object {
        /** 3:2 ratio = max 60% in one faction */
        const val MAX_FACTION_RATIO = 0.6
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun validateAndJoin(sessionId: Long, targetFactionId: Long): FactionJoinResult {
        val playerOfficers = officerRepository.findBySessionId(sessionId)
            .filter { it.userId != null }
        val totalPlayers = playerOfficers.size
        val targetCount = playerOfficers.count { it.factionId == targetFactionId }

        if (totalPlayers >= 2) {
            val maxAllowed = ((totalPlayers + 1) * 3 + 4) / 5  // ceil((n+1) * 0.6)
            if (targetCount >= maxAllowed) {
                val faction = factionRepository.findById(targetFactionId).orElse(null)
                val name = faction?.name ?: "해당 진영"
                return FactionJoinResult(
                    allowed = false,
                    reason = "${name} 인원이 가득 찼습니다 -- 다른 진영에 참가하거나 자리가 날 때까지 기다려주세요",
                )
            }
        }
        return FactionJoinResult(allowed = true)
    }

    data class FactionJoinResult(val allowed: Boolean, val reason: String? = null)
}
```

### SMGT-01: Offline CP Recovery Verification

```kotlin
// CommandPointService.recoverAllCp already handles this correctly:
fun recoverAllCp(sessionId: Long) {
    val officers = officerRepository.findBySessionId(sessionId)
    for (officer in officers) {
        recoverCp(officer)  // No online/offline check -- recovers for ALL officers
    }
    officerRepository.saveAll(officers)
}

// recoverCp only skips officers in "tactical" location state (in a battle)
// This is correct per D-05/D-06: offline officers recover CP normally
```

## State of the Art

| Old Approach              | Current Approach                        | When Changed                       | Impact                                         |
| ------------------------- | --------------------------------------- | ---------------------------------- | ---------------------------------------------- |
| No @Version on entities   | JPA @Version for optimistic locking     | Spring Data JPA (always supported) | Prevents concurrent write corruption           |
| Inline Executors.newXxx() | Spring-managed executor beans           | Spring 6.1+ lifecycle improvements | Prevents thread leaks on shutdown/redeployment |
| Manual faction balance    | Declarative ratio constraint in service | Phase 1 (new)                      | Enforces gin7 3:2 rule                         |

**Deprecated/outdated:**

- `WorldState` / `General` / `City` / `Nation` class names: These are OpenSamguk legacy aliases. Canonical names are `SessionState` / `Officer` / `Planet` / `Faction`. Both work via constructor compatibility aliases, but new code must use LOGH names.
- `officer.meta["positionCards"]` JSONB storage: Marked for replacement in Phase 2 (HARD-03), but Phase 1 must NOT touch it.

## Open Questions

1. **Scenario JSON faction ratio field**
    - What we know: D-01 sets 3:2 globally. D-03 says scenario JSON provides defaults.
    - What's unclear: Should the scenario JSON include a configurable `factionMaxRatio` field for per-scenario tuning?
    - Recommendation: Use hardcoded 0.6 for Phase 1 with a service constant. Add scenario JSON configurability as a small enhancement if time permits (Claude's discretion item).

2. **@Version column and existing data**
    - What we know: The migration adds `version BIGINT NOT NULL DEFAULT 0` -- all existing rows get version 0.
    - What's unclear: Are there any background processes that directly UPDATE officer rows via SQL (not JPA)?
    - Recommendation: Check for any raw SQL updates in ScenarioService or TurnDaemon. If found, they need to include `SET version = version + 1` in the SQL.

3. **Turn daemon CP recovery scheduling**
    - What we know: `CommandPointService.recoverAllCp(sessionId)` exists and recovers all officers.
    - What's unclear: Is this actually called on the 5-minute real-time interval, or is it tied to game turn ticks?
    - Recommendation: Verify the TurnDaemon or equivalent scheduler calls `recoverAllCp` on the correct interval. If it's tied to turns instead of real-time, add a separate `@Scheduled` method.

## Validation Architecture

### Test Framework

| Property           | Value                                                                             |
| ------------------ | --------------------------------------------------------------------------------- |
| Framework          | JUnit 5 (Jupiter) + Mockito (backend), Vitest 3.2.4 (frontend)                    |
| Config file        | `backend/game-app/build.gradle.kts` (JUnit), `frontend/vitest.config.ts` (Vitest) |
| Quick run command  | `cd backend && ./gradlew :game-app:test --tests "com.openlogh.*Test" -x bootJar`  |
| Full suite command | `cd backend && ./gradlew test`                                                    |

### Phase Requirements to Test Map

| Req ID  | Behavior                                   | Test Type   | Automated Command                                                                    | File Exists?    |
| ------- | ------------------------------------------ | ----------- | ------------------------------------------------------------------------------------ | --------------- |
| HARD-01 | @Version prevents concurrent CP corruption | unit        | `./gradlew :game-app:test --tests "com.openlogh.entity.OfficerOptimisticLockTest"`   | Wave 0          |
| HARD-02 | No executor thread leak after battle end   | unit        | `./gradlew :game-app:test --tests "com.openlogh.websocket.TacticalExecutorLeakTest"` | Wave 0          |
| SESS-01 | Session creation via scenario selection    | integration | `./gradlew :game-app:test --tests "com.openlogh.service.SessionCreationTest"`        | Wave 0          |
| SESS-02 | Join session with max 2000 players         | unit        | Existing `OfficerServiceTest` can be extended                                        | Partial         |
| SESS-03 | Faction selection with 3:2 ratio           | unit        | `./gradlew :game-app:test --tests "com.openlogh.service.FactionJoinServiceTest"`     | Wave 0          |
| SESS-06 | 24x game clock via tickSeconds             | unit        | Verify scenario JSON contains correct tickSeconds                                    | Existing        |
| SESS-07 | Re-registration restriction                | unit        | Existing `ReregistrationService` tests                                               | Needs extension |
| SMGT-01 | Offline CP recovery                        | unit        | `./gradlew :game-app:test --tests "com.openlogh.engine.CommandPointServiceTest"`     | Wave 0          |

### Sampling Rate

- **Per task commit:** `./gradlew :game-app:test --tests "com.openlogh.*" -x bootJar` (< 30s)
- **Per wave merge:** `cd backend && ./gradlew test` (full backend suite)
- **Phase gate:** Full backend suite green + frontend `pnpm test` green before `/gsd:verify-work`

### Wave 0 Gaps

- [ ] `backend/game-app/src/test/.../entity/OfficerOptimisticLockTest.kt` -- covers HARD-01
- [ ] `backend/game-app/src/test/.../websocket/TacticalExecutorLeakTest.kt` -- covers HARD-02
- [ ] `backend/game-app/src/test/.../service/FactionJoinServiceTest.kt` -- covers SESS-03 ratio enforcement
- [ ] `backend/game-app/src/test/.../engine/CommandPointServiceTest.kt` -- covers SMGT-01 offline recovery
- [ ] `backend/game-app/src/test/.../service/SessionCreationTest.kt` -- covers SESS-01

## Project Constraints (from CLAUDE.md)

- **Tech Stack Lock:** Spring Boot 3 (Kotlin) + Next.js 15 + PostgreSQL 16 + Redis 7 -- no substitutions
- **Architecture Lock:** gateway-app + versioned game-app JVM split must be maintained
- **Reference Fidelity:** gin7 manual mechanics take priority over convenience features
- **Multi-Account Warning:** Must maintain 1-account-per-player enforcement (existing)
- **Naming Convention:** New Kotlin code uses PascalCase classes, camelCase methods. New files use LOGH terminology (Officer, not General)
- **Commit Protocol:** Follow OMC commit protocol with trailers (Constraint, Rejected, Confidence, etc.)
- **GSD Workflow:** All changes go through GSD commands -- no direct repo edits

## Sources

### Primary (HIGH confidence)

- `/Users/apple/Desktop/openlogh/backend/game-app/src/main/kotlin/com/openlogh/entity/Officer.kt` -- confirmed no @Version field exists
- `/Users/apple/Desktop/openlogh/backend/game-app/src/main/kotlin/com/openlogh/websocket/TacticalWebSocketController.kt` -- confirmed executor leak at line 218
- `/Users/apple/Desktop/openlogh/backend/game-app/src/main/kotlin/com/openlogh/engine/CommandPointService.kt` -- confirmed recoverAllCp covers all officers
- `/Users/apple/Desktop/openlogh/backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/TacticalTurnScheduler.kt` -- confirmed managed executor pattern with @PreDestroy
- `/Users/apple/Desktop/openlogh/backend/game-app/src/main/kotlin/com/openlogh/service/ReregistrationService.kt` -- confirmed SESS-07 already implemented
- `/Users/apple/Desktop/openlogh/backend/game-app/src/main/kotlin/com/openlogh/service/OfficerService.kt` -- confirmed officer creation flow
- `/Users/apple/Desktop/openlogh/backend/game-app/src/main/kotlin/com/openlogh/entity/Faction.kt` -- confirmed faction entity with factionType and officerCount

### Secondary (MEDIUM confidence)

- [Optimistic Locking in JPA - Baeldung](https://www.baeldung.com/jpa-optimistic-locking) -- @Version annotation pattern, OptimisticLockException handling
- [Resolving Optimistic Lock Exceptions in Spring Boot JPA](https://medium.com/@AlexanderObregon/resolving-optimistic-lock-exceptions-in-spring-boot-jpa-repositories-a705e7f75af9) -- retry pattern for OptimisticLockingFailureException
- [Spring Task Execution and Scheduling](https://docs.spring.io/spring-boot/reference/features/task-execution-and-scheduling.html) -- ScheduledExecutorService lifecycle management
- [Terminating custom scheduled tasks in Spring](https://dev.karakun.com/2023/08/15/tomcat-thread-pool-executor-termination.html) -- thread leak prevention patterns
- [ScheduledExecutorFactoryBean](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/concurrent/ScheduledExecutorFactoryBean.html) -- Spring-managed executor factory

### Tertiary (LOW confidence)

- None -- all findings verified against codebase and official documentation

## Metadata

**Confidence breakdown:**

- Standard stack: HIGH -- all libraries already in project, no new dependencies
- Architecture: HIGH -- changes are surgical additions to existing patterns (1 annotation, 1 line fix, 1 new service)
- Pitfalls: HIGH -- all pitfalls verified against actual code (confirmed no @Version, confirmed leak location, confirmed test patterns)
- Validation: MEDIUM -- test infrastructure exists but specific test files for Phase 1 requirements need creation (Wave 0)

**Research date:** 2026-03-28
**Valid until:** 2026-04-28 (stable -- no fast-moving dependencies)
