---
phase: 12-operation-integration
plan: 01
subsystem: database
tags: [kotlin, spring-boot, jpa, flyway, postgres, jsonb, hibernate, junit5]

# Dependency graph
requires:
  - phase: 11-tactical-ai
    provides: "MissionObjective enum (CONQUEST/DEFENSE/SWEEP), PersonalityTrait enum (5 traits)"
provides:
  - "operation_plan table (V47 Flyway migration) with JSONB participantFleetIds + 3 indexes (session_status, participants GIN, faction)"
  - "OperationPlan JPA entity with 14 fields per D-05 and @JdbcTypeCode(SqlTypes.JSON) List<Long> participantFleetIds"
  - "OperationStatus enum (PENDING/ACTIVE/COMPLETED/CANCELLED) under com.openlogh.model"
  - "MissionObjective.defaultForPersonality(PersonalityTrait) helper (D-10 personality fallback)"
  - "OperationPlanRepository: JpaRepository with findBySessionIdAndStatus + findBySessionIdAndFactionIdAndStatusIn + native JSONB membership query (prod-only)"
affects: [12-02-operation-command, 12-03-engine-state, 12-04-lifecycle-service, 13-strategic-ai]

# Tech tracking
tech-stack:
  added: []  # Only reuses existing jose libraries (jackson-module-kotlin for JSONB Long serialization already present)
  patterns:
    - "JSONB List<Long> via @JdbcTypeCode(SqlTypes.JSON) mirrored from Officer.positionCards"
    - "Flyway transactional DDL via BEGIN/COMMIT wrap per 12-VALIDATION rollback section"
    - "@SpringBootTest(classes = [OpenloghApplication::class]) explicit classes attribute to avoid duplicate @SpringBootConfiguration with OpenloghApplicationTests$TestConfig"
    - "Repository with JpaRepository derived queries for H2-compatible tests + separate PostgreSQL-only native JSONB query for prod"

key-files:
  created:
    - "backend/game-app/src/main/resources/db/migration/V47__create_operation_plan.sql"
    - "backend/game-app/src/main/kotlin/com/openlogh/model/OperationStatus.kt"
    - "backend/game-app/src/main/kotlin/com/openlogh/entity/OperationPlan.kt"
    - "backend/game-app/src/main/kotlin/com/openlogh/repository/OperationPlanRepository.kt"
    - "backend/game-app/src/test/kotlin/com/openlogh/engine/tactical/ai/MissionObjectiveDefaultTest.kt"
    - "backend/game-app/src/test/kotlin/com/openlogh/repository/OperationPlanRepositoryTest.kt"
    - ".planning/phases/12-operation-integration/deferred-items.md"
  modified:
    - "backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/ai/MissionObjective.kt"

key-decisions:
  - "V47 migration number chosen (not V45 — CONTEXT.md drifted) because V46__add_command_proposal.sql is current tip"
  - "JSONB List<Long> participantFleetIds uses @JdbcTypeCode(SqlTypes.JSON) matching Officer.positionCards pattern — tests confirm 10_000_000_000L > Int.MAX_VALUE round-trips correctly"
  - "Native @> JSONB membership query exists in repository but is H2-incompatible — all H2 tests MUST use derived queries + Kotlin-side filtering (Plan 12-04 will use prod query in real deployments)"
  - "OperationPlanRepositoryTest uses explicit @SpringBootTest(classes = [OpenloghApplication::class]) to prevent Spring auto-discovering both OpenloghApplication and the pre-existing OpenloghApplicationTests$TestConfig (duplicate @SpringBootConfiguration)"
  - "MissionObjective uses trailing comma with explicit semicolon separator before companion object (Kotlin enum + companion pattern)"

patterns-established:
  - "Flyway V-number discipline: planners MUST run `ls db/migration/ | sort -V | tail -3` before assigning version numbers to avoid drift from context docs"
  - "Repository integration tests under :game-app must use `classes = [OpenloghApplication::class]` explicitly to avoid TestConfig discovery conflict"

requirements-completed: [OPS-01]

# Metrics
duration: 8min
completed: 2026-04-09
---

# Phase 12 Plan 01: Operation Plan Entity Foundation Summary

**OperationPlan JPA entity + V47 Flyway migration + OperationStatus enum + MissionObjective.defaultForPersonality helper — the persistence substrate all Phase 12 downstream plans consume**

## Performance

- **Duration:** ~8 min
- **Started:** 2026-04-09T02:52:45Z
- **Completed:** 2026-04-09T03:01:11Z
- **Tasks:** 2
- **Files created:** 7 (1 migration + 4 Kotlin source + 2 Kotlin tests + 1 deferred-items.md)
- **Files modified:** 1 (MissionObjective.kt — extended with companion helper)

## Accomplishments

- V47 Flyway migration creates `operation_plan` table with session_id FK → session_state(id) ON DELETE CASCADE, 14 columns matching D-05 field list exactly, and 3 indexes (session_status lookup, GIN on participant_fleet_ids for JSONB @> queries, faction ownership). BEGIN/COMMIT wrapper per 12-VALIDATION.md rollback guidance
- OperationPlan JPA entity maps all 14 D-05 fields. `participantFleetIds: MutableList<Long>` round-trips through JSONB via `@JdbcTypeCode(SqlTypes.JSON)`, verified to handle values above Int.MAX (10_000_000_000L)
- OperationStatus enum (PENDING/ACTIVE/COMPLETED/CANCELLED) under `com.openlogh.model`
- MissionObjective extended with `defaultForPersonality(PersonalityTrait): MissionObjective` companion helper. Maps AGGRESSIVE→SWEEP and all 4 other traits (DEFENSIVE/CAUTIOUS/BALANCED/POLITICAL)→DEFENSE per D-10
- OperationPlanRepository: JpaRepository with `findBySessionIdAndStatus`, `findBySessionIdAndFactionIdAndStatusIn`, and native PostgreSQL JSONB `@>` membership query (marked H2-incompatible in KDoc)
- 7/7 new tests pass (5 MissionObjective personality mappings + 2 OperationPlanRepository JSONB round-trip + findBySessionIdAndStatus)

## Task Commits

Each task was committed atomically (TDD RED→GREEN combined per task, not separate RED commits):

1. **Task 1: V47 migration + OperationStatus + MissionObjective.defaultForPersonality** - `58c5cc68` (feat)
2. **Task 2: OperationPlan entity + OperationPlanRepository** - `fcf7a9f2` (feat)

**Plan metadata commit:** pending (will include SUMMARY.md, STATE.md, ROADMAP.md, REQUIREMENTS.md)

## Files Created/Modified

### Created

- `backend/game-app/src/main/resources/db/migration/V47__create_operation_plan.sql` — operation_plan DDL with 14 columns, session_state FK ON DELETE CASCADE, 3 indexes, BEGIN/COMMIT wrapped
- `backend/game-app/src/main/kotlin/com/openlogh/model/OperationStatus.kt` — 4-state enum (PENDING/ACTIVE/COMPLETED/CANCELLED)
- `backend/game-app/src/main/kotlin/com/openlogh/entity/OperationPlan.kt` — JPA entity, 14 fields, 2 @Enumerated(STRING) fields (objective/status), JSONB participantFleetIds
- `backend/game-app/src/main/kotlin/com/openlogh/repository/OperationPlanRepository.kt` — JpaRepository<OperationPlan, Long> + 3 queries
- `backend/game-app/src/test/kotlin/com/openlogh/engine/tactical/ai/MissionObjectiveDefaultTest.kt` — 5 unit tests for personality fallback mapping
- `backend/game-app/src/test/kotlin/com/openlogh/repository/OperationPlanRepositoryTest.kt` — 2 @SpringBootTest integration tests (JSONB round-trip + findBySessionIdAndStatus)
- `.planning/phases/12-operation-integration/deferred-items.md` — documents 207 pre-existing test failures in PlanetServiceTest/ScenarioServiceTest that are unrelated to Plan 12-01

### Modified

- `backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/ai/MissionObjective.kt` — added companion object with `defaultForPersonality(PersonalityTrait)` helper, moved enum to trailing-comma form with explicit `;` separator

## Decisions Made

- **Migration version V47 (not V45 as CONTEXT.md said):** Detected that V46__add_command_proposal.sql is the current tip of the migration chain. Kept V47 as planned — the 12-01-PLAN.md already corrected this drift.
- **JSONB Long handling explicitly tested:** 10_000_000_000L (> Int.MAX_VALUE) round-trip test passes, confirming Jackson deserializes as Long. This was flagged in 12-VALIDATION.md failure modes #4 as a risk.
- **@SpringBootTest explicit classes:** Initial test failed with `Found multiple @SpringBootConfiguration annotated classes`. Fixed by specifying `classes = [OpenloghApplication::class]` explicitly, mirroring `ScenarioPlayableIntegrationTest`. No new classes created — just used the main app class.
- **No REFACTOR phase needed:** GREEN code was already minimal and clean. Skipping refactor to avoid touching working code.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] JAVA_HOME pointing to Java 25 caused Gradle/Kotlin DSL crash**
- **Found during:** Task 1 RED test execution
- **Issue:** `./gradlew` crashed with `java.lang.IllegalArgumentException: 25.0.2` in `JavaVersion.parse` inside the Kotlin DSL compiler. Gradle 8.12 + Kotlin 2.1.0 DSL cannot parse Java 25 version strings. The project requires Java 17 (per `backend/build.gradle.kts` `sourceCompatibility = JavaVersion.VERSION_17`).
- **Fix:** Prepended `export JAVA_HOME=$(/usr/libexec/java_home -v 17)` to all gradle invocations. Java 17.0.18 is already installed on this machine.
- **Files modified:** None (environment-level fix for this session only)
- **Verification:** Build succeeded with `./gradlew :game-app:test --tests "com.openlogh.engine.tactical.ai.MissionObjectiveDefaultTest"` returning BUILD SUCCESSFUL on Java 17

**2. [Rule 3 - Blocking] `-x ktlintCheck` exclusion flag invalid**
- **Found during:** Task 1 RED test execution
- **Issue:** Plan suggests `-x ktlintCheck` in verify command, but the task doesn't exist in this Gradle project (`Task 'ktlintCheck' not found in root project 'opensam' and its subprojects.`)
- **Fix:** Removed `-x ktlintCheck` from all gradle invocations. Ktlint isn't configured in this build.
- **Files modified:** None (only test command invocation)
- **Verification:** Test commands succeed without the flag

**3. [Rule 3 - Blocking] `@SpringBootTest` context load failed due to duplicate `@SpringBootConfiguration`**
- **Found during:** Task 2 GREEN test execution
- **Issue:** `java.lang.IllegalStateException: Found multiple @SpringBootConfiguration annotated classes [OpenloghApplicationTests$TestConfig, OpenloghApplication]`. Spring Boot auto-discovery found both the main app class AND an existing inner TestConfig class in `OpensamApplicationTests.kt` (file named confusingly but class is `OpenloghApplicationTests` with a `TestConfig`).
- **Fix:** Updated `OperationPlanRepositoryTest.kt` with explicit `@SpringBootTest(classes = [OpenloghApplication::class], webEnvironment = SpringBootTest.WebEnvironment.NONE)` and `@ActiveProfiles("test")`, mirroring the pattern in `ScenarioPlayableIntegrationTest.kt` which solves the same problem.
- **Files modified:** `backend/game-app/src/test/kotlin/com/openlogh/repository/OperationPlanRepositoryTest.kt`
- **Verification:** Test now passes; import of `OpenloghApplication` added.
- **Committed in:** `fcf7a9f2` (Task 2 commit)

---

**Total deviations:** 3 auto-fixed (all Rule 3 - blocking issues)
**Impact on plan:** No scope creep. All three fixes were necessary to get tests running at all. Test content exactly matches the plan spec.

## Issues Encountered

- **207 pre-existing test failures in full `:game-app:test` suite** — PlanetServiceTest, ScenarioServiceTest, CityServiceTest all fail on legacy Three Kingdoms city names (남피/하북 etc.). These files predate this plan (git blame shows `8ab11cfc` initial LOGH transform + `2e113181` engine replacement). **Not caused by Plan 12-01.** Documented in `deferred-items.md`. Per GSD scope boundary rules, out of scope — these belong to the ongoing OpenSamguk → OpenLOGH rewrite cleanup and should be tackled by a dedicated plan.

## Known Stubs

None. All new code is production-ready (entity/repository/enum/helper/migration). No placeholder values, no TODO comments, no hardcoded empty returns.

## Next Phase Readiness

Plan 12-01 unblocks all downstream Phase 12 plans:

- **12-02 (Command Rewrite):** Can now `import com.openlogh.entity.OperationPlan` / `OperationPlanRepository` / `com.openlogh.model.OperationStatus` / `MissionObjective.defaultForPersonality` and rewrite `OperationPlanCommand` + `OperationCancelCommand` against real entity instead of `nation.meta` stub. Note: CommandExecutor has no `@Transactional` — the 1-fleet-1-operation invariant MUST live in `OperationPlanService` (per 12-VALIDATION failure mode #1).
- **12-03 (Engine State):** Can add `missionObjectiveByFleetId: ConcurrentHashMap<Long, MissionObjective>` to TacticalBattleState and populate via `OperationPlanRepository.findBySessionIdAndStatus` in BattleTriggerService. The native `@>` JSONB query in the repository is ready for prod; H2 tests must use derived + Kotlin filter.
- **12-04 (Lifecycle Service):** Can create `OperationLifecycleService` using `OperationPlanRepository.findBySessionIdAndStatus(sessionId, PENDING)` + the native JSONB query for fleet membership lookups.

**Cross-references for downstream agents:**
- JSONB round-trip of `List<Long>` confirmed working — Jackson correctly produces Long (not Integer) for values above 2^31. Tests in `OperationPlanRepositoryTest.kt` prove this.
- The `@>` native query is deliberately isolated because H2 (test DB) does not support it. Any test that needs fleet-membership lookup must use `findBySessionIdAndStatus` + Kotlin-side `.contains()` filtering.
- All downstream services that inject `OperationPlanRepository` should be annotated `@Transactional` where mutations cross entity boundaries.

## Self-Check: PASSED

**Files verified to exist:**
- FOUND: `backend/game-app/src/main/resources/db/migration/V47__create_operation_plan.sql`
- FOUND: `backend/game-app/src/main/kotlin/com/openlogh/model/OperationStatus.kt`
- FOUND: `backend/game-app/src/main/kotlin/com/openlogh/entity/OperationPlan.kt`
- FOUND: `backend/game-app/src/main/kotlin/com/openlogh/repository/OperationPlanRepository.kt`
- FOUND: `backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/ai/MissionObjective.kt` (modified)
- FOUND: `backend/game-app/src/test/kotlin/com/openlogh/engine/tactical/ai/MissionObjectiveDefaultTest.kt`
- FOUND: `backend/game-app/src/test/kotlin/com/openlogh/repository/OperationPlanRepositoryTest.kt`

**Commits verified:**
- FOUND: `58c5cc68` — feat(12-01): add V47 operation_plan migration, OperationStatus enum, and MissionObjective.defaultForPersonality
- FOUND: `fcf7a9f2` — feat(12-01): add OperationPlan entity and OperationPlanRepository

**Test results verified:**
- FOUND: `TEST-com.openlogh.engine.tactical.ai.MissionObjectiveDefaultTest.xml` — `tests="5" skipped="0" failures="0" errors="0"`
- FOUND: `TEST-com.openlogh.repository.OperationPlanRepositoryTest.xml` — `tests="2" skipped="0" failures="0" errors="0"`

**Acceptance criteria verified:**
- `participant_fleet_ids` appears 2× in V47 SQL (column definition + GIN index) — grep confirmed
- `class OperationPlan` found in entity file
- V47 is the only migration file matching `V*create_operation_plan.sql` — no V45/V46 variants
- V46__add_command_proposal.sql confirmed as current tip (V47 is the correct next number)

---
*Phase: 12-operation-integration*
*Plan: 01*
*Completed: 2026-04-09*
