---
phase: 12
slug: operation-integration
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-08
---

# Phase 12 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Derived from `12-RESEARCH.md` Section 9 (Validation Architecture).

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (Jupiter) + Spring Boot Test 3.4.2 + H2 in-memory DB |
| **Config file** | `backend/game-app/build.gradle.kts` (gradle-managed) |
| **Quick run command** | `cd backend && ./gradlew :game-app:test --tests "com.openlogh.service.Operation*" -x ktlintCheck` |
| **Full suite command** | `cd backend && ./gradlew :game-app:test` |
| **Estimated runtime** | ~10s quick / ~3min full |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :game-app:test --tests "com.openlogh.service.Operation*"`
- **After every plan wave:** Run `./gradlew :game-app:test --tests "com.openlogh.service.*" --tests "com.openlogh.engine.tactical.*"`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds for quick, 180 seconds for wave

---

## Per-Task Verification Map

> Task IDs will be finalized by the planner. The rows below map phase requirements to automated commands that MUST exist when the task is complete.

| Req | Behavior | Test Type | Automated Command | File Exists |
|-----|----------|-----------|-------------------|-------------|
| OPS-01 | `MissionObjective.defaultForPersonality(AGGRESSIVE) == SWEEP` (+ 4 other personality mappings) | unit | `./gradlew :game-app:test --tests "com.openlogh.engine.tactical.ai.MissionObjectiveDefaultTest"` | ❌ W0 |
| OPS-01 | `BattleTriggerService.buildInitialState()` populates `missionObjectiveByFleetId` from `OperationPlanRepository` | unit (mocked repo) | `./gradlew :game-app:test --tests "com.openlogh.engine.tactical.BattleTriggerOperationInjectionTest"` | ❌ W0 |
| OPS-01 | `TacticalBattleEngine.processTick` read-through: `missionObjectiveByFleetId[fleetId]` propagates into `unit.missionObjective` at tick start (Step 0.6, before `TacticalAIRunner.processAITick`) | unit | `./gradlew :game-app:test --tests "com.openlogh.engine.tactical.TacticalBattleEngineTest.mission_read_through*"` | ❌ W0 (extend existing) |
| OPS-01 | `TacticalBattleService.syncOperationToActiveBattles(plan)` updates the map for every active `TacticalBattle` that includes a participant fleet | integration | `./gradlew :game-app:test --tests "com.openlogh.service.OperationLifecycleServiceTest.sync_updates_active_battles"` | ❌ W0 |
| OPS-01 | Non-participant fleets receive `MissionObjective.defaultForPersonality(unit.personality)` | unit | `./gradlew :game-app:test --tests "com.openlogh.engine.tactical.TacticalBattleEngineTest.non_participant_uses_default"` | ❌ W0 |
| OPS-02 | Merit bonus ×1.5 applied for fleets in `missionObjectiveByFleetId` when battle ends as victory for their side | integration | `./gradlew :game-app:test --tests "com.openlogh.service.OperationMeritBonusTest.participant_receives_bonus"` | ❌ W0 |
| OPS-02 | Merit bonus NOT applied for non-participant fleets in the same battle | integration | `./gradlew :game-app:test --tests "com.openlogh.service.OperationMeritBonusTest.non_participant_no_bonus"` | ❌ W0 |
| OPS-02 | CANCELLED operation mid-battle → sync channel removes fleet → no bonus on battle end | integration | `./gradlew :game-app:test --tests "com.openlogh.service.OperationMeritBonusTest.cancelled_removes_bonus"` | ❌ W0 |
| OPS-02 | Merit bonus persists on `Officer.meritPoints` (first real accumulation path in codebase) | integration | `./gradlew :game-app:test --tests "com.openlogh.service.OperationMeritBonusTest.merit_persisted_on_officer"` | ❌ W0 |
| OPS-03 | `OperationLifecycleService.activatePending()` PENDING → ACTIVE when any participant's `Fleet.planetId == targetStarSystemId` | unit (mocked repo) | `./gradlew :game-app:test --tests "com.openlogh.service.OperationLifecycleServiceTest.activates_on_first_arrival"` | ❌ W0 |
| OPS-03 | `OperationLifecycleService.activatePending()` stays PENDING when no participant has arrived | unit | `./gradlew :game-app:test --tests "com.openlogh.service.OperationLifecycleServiceTest.remains_pending_if_none_arrived"` | ❌ W0 |
| OPS-03 | `TickEngine.processTick` runs `OperationLifecycleService.processTick()` BEFORE `TacticalBattleService.processSessionBattles()` (order asserted via Mockito `InOrder`) | integration | `./gradlew :game-app:test --tests "com.openlogh.engine.TickEngineOrderingTest.operation_activation_before_battle_trigger"` | ❌ W0 |
| OPS-03 | CONQUEST completion: `StarSystem.owningFactionId == operation.factionId` → COMPLETED | unit | `./gradlew :game-app:test --tests "com.openlogh.service.OperationLifecycleServiceTest.conquest_completion"` | ❌ W0 |
| OPS-03 | DEFENSE completion: 60 ticks of no enemy fleets at target → COMPLETED (stability counter) | unit | `./gradlew :game-app:test --tests "com.openlogh.service.OperationLifecycleServiceTest.defense_stability_window"` | ❌ W0 |
| OPS-03 | SWEEP completion: enemy fleet count at target drops to 0 → COMPLETED | unit | `./gradlew :game-app:test --tests "com.openlogh.service.OperationLifecycleServiceTest.sweep_completion"` | ❌ W0 |
| OPS-03 | `OperationPlanCommand` enforces 1-fleet-1-operation (atomically removes prior membership inside the same `@Transactional`) | integration | `./gradlew :game-app:test --tests "com.openlogh.service.OperationPlanCommandTest.one_fleet_one_operation"` | ❌ W0 |
| OPS-03 | `WarpNavigationCommand` updates `Fleet.planetId` on arrival (pre-existing bug fix required for cross-system OPS-03) | integration | `./gradlew :game-app:test --tests "com.openlogh.command.gin7.commander.WarpNavigationCommandTest.updates_fleet_planet_id"` | ❌ W0 |
| — | Flyway `V47__create_operation_plan.sql` applies cleanly; entity schema matches table | contract | `./gradlew :game-app:test --tests "com.openlogh.repository.OperationPlanRepositoryTest.schema_matches_entity"` | ❌ W0 |
| — | JSONB round-trip of `participantFleetIds: List<Long>` via `@JdbcTypeCode(SqlTypes.JSON)` — tests `1L` and `10_000_000_000L` | integration | `./gradlew :game-app:test --tests "com.openlogh.repository.OperationPlanRepositoryTest.jsonb_round_trip_long"` | ❌ W0 |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

New test files to create (no framework install needed — JUnit 5 + Spring Boot Test + H2 already on classpath):

- [ ] `backend/game-app/src/test/kotlin/com/openlogh/engine/tactical/ai/MissionObjectiveDefaultTest.kt`
- [ ] `backend/game-app/src/test/kotlin/com/openlogh/service/OperationLifecycleServiceTest.kt`
- [ ] `backend/game-app/src/test/kotlin/com/openlogh/service/OperationPlanCommandTest.kt`
- [ ] `backend/game-app/src/test/kotlin/com/openlogh/service/OperationMeritBonusTest.kt`
- [ ] `backend/game-app/src/test/kotlin/com/openlogh/repository/OperationPlanRepositoryTest.kt`
- [ ] `backend/game-app/src/test/kotlin/com/openlogh/engine/tactical/BattleTriggerOperationInjectionTest.kt`
- [ ] `backend/game-app/src/test/kotlin/com/openlogh/engine/TickEngineOrderingTest.kt`
- [ ] `backend/game-app/src/test/kotlin/com/openlogh/command/gin7/commander/WarpNavigationCommandTest.kt` (extend if exists)
- [ ] Extend `backend/game-app/src/test/kotlin/com/openlogh/engine/tactical/TacticalBattleEngineTest.kt` with `mission_read_through_refreshes_unit_objective_at_tick_start()` + `non_participant_uses_default()`

---

## Sampling Strategy — Boundary Inputs

Every OperationLifecycleService/OperationPlanCommand test MUST cover these boundaries (Nyquist failure-mode sampling):

- Empty `participantFleetIds` list → command fails arg validation
- Fleet at wrong planet → `activatePending` leaves status PENDING
- Fleet at target but different faction (ghost fleet) → still activates (activation keys on fleetId, not factionId — see D-17)
- Multiple operations on same faction targeting same star system → both activate independently (D-02)
- Cancelled operation mid-battle → sync channel removes from map; merit bonus not awarded at endBattle
- Fleet reassigned from Op-A to Op-B between tick N and N+1 → Op-B's missionObjective takes precedence; Op-A's participantFleetIds excludes the fleet (D-04, atomic inside one @Transactional)
- Stale `missionObjectiveByFleetId` after CANCELLED → read-through cache falls back to `defaultForPersonality` on next tick
- `participantFleetIds` containing `1L` AND `10_000_000_000L` (> Int.MAX_VALUE) → JSONB round-trip preserves Long type

---

## Failure Modes

1. **Fleet reassignment race** — `OperationPlanCommand.run()` MUST read all active operations for the faction, remove prior memberships, THEN save the new operation, inside one `@Transactional` block.
2. **Tick ordering regression** — `TickEngineOrderingTest` asserts `OperationLifecycleService` runs before `TacticalBattleService` via Mockito `InOrder`.
3. **Cancel-during-battle race** — `TacticalBattleService.endBattle()` runs inside one `@Transactional` block; sync channel executes outside it. Test cancels during tick N, asserts no merit bonus applied in tick N+1's endBattle.
4. **JSONB Long/Integer deserialization** — Jackson may deserialize numbers as `Integer` for small IDs. Integration test round-trips both `1L` and `10_000_000_000L`.
5. **WarpNavigationCommand Fleet.planetId gap** — pre-existing bug at `WarpNavigationCommand.kt:41` updates only `Officer.planetId`, not `Fleet.planetId`. Without the fix, ~90% of cross-system operations never activate. Fix is a one-line addition mirroring `IntraSystemNavigationCommand.kt:44`.
6. **`missionObjectiveByFleetId` concurrency** — tick loop reads while sync channel writes. Use `ConcurrentHashMap`, not `HashMap`/`mutableMapOf()`.

---

## Rollback / Repair

**Flyway V47 rollback:**
- Flyway Community Edition has no automatic down-migration. Wrap the V47 SQL in explicit `BEGIN; ... COMMIT;` to leverage PostgreSQL transactional DDL — a failed index creation will roll back the CREATE TABLE automatically.
- If migration applied but entity doesn't match → `./gradlew flywayRepair` to fix the `flyway_schema_history` checksum after editing the SQL.
- Example V47 skeleton:
  ```sql
  BEGIN;
  CREATE TABLE operation_plan (...);
  CREATE INDEX idx_operation_plan_session_status ON operation_plan(session_id, status);
  CREATE INDEX idx_operation_plan_participants ON operation_plan USING GIN (participant_fleet_ids jsonb_path_ops);
  CREATE INDEX idx_operation_plan_faction ON operation_plan(session_id, faction_id, status);
  COMMIT;
  ```

**H2 test caveat:** the `@>` JSONB operator does not work on H2. Repository integration tests using JSONB membership queries must either (a) run against PostgreSQL testcontainers, or (b) use `findBySessionIdAndStatus` + Kotlin-side filtering (recommended — simpler setup).

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| End-to-end: player issues OperationPlan in UI stub → fleets warp → operation activates → battle runs → merit appears on Officer | OPS-01, OPS-02, OPS-03 | Requires full gateway + game-app stack + seed scenario; no Phase 12 UI yet | Run `docker-compose up`, start a test session, issue OperationPlanCommand via REST API, tick forward, observe `/officers/{id}` endpoint for meritPoints increase |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify command or Wave 0 dependency
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all ❌ W0 references above
- [ ] No watch-mode flags in automated commands
- [ ] Feedback latency < 30s (quick) / 180s (wave)
- [ ] `nyquist_compliant: true` set in frontmatter after planner approval

**Approval:** pending
