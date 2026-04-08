# Phase 12 Research: 작전 연동 (Operation Integration)

**Researched:** 2026-04-08
**Domain:** Strategic-Tactical Integration (Spring Boot 3 Kotlin / JPA / Flyway)
**Confidence:** HIGH

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions (D-01 .. D-19)

**D-01** — 새 `OperationPlan` 엔티티 + `operation_plan` 테이블 + V45 마이그레이션. nation.meta JSONB 확장이 아닌 정식 엔티티. **⚠️ RESEARCH FINDING: V45 already exists in codebase (`V45__create_ship_unit_table.sql`). Next free version is V47. See Section 1.**

**D-02** — 한 진영이 동시에 여러 작전을 가질 수 있다 (멀티 지역 동시 수행).

**D-03** — 한 작전 = 단일 성계 목표 (`targetStarSystemId`).

**D-04** — 1 부대 = 1 작전 (배타적). 새 작전 지정 시 기존 작전 `participantFleetIds`에서 자동 제거.

**D-05** — `OperationPlan` 엔티티 필드:
- `id` (PK), `sessionId`, `factionId`, `name`
- `objective: MissionObjective` (CONQUEST/DEFENSE/SWEEP)
- `targetStarSystemId: Long`
- `status: OperationStatus` (PENDING/ACTIVE/COMPLETED/CANCELLED)
- `participantFleetIds: List<Long>` (JSONB array column)
- `scale: Int` (1~7)
- `issuedByOfficerId: Long`
- `issuedAtTick: Long`
- `expectedCompletionTick: Long?` (컬럼만 추가, 사용은 추후)

**D-06** — `TacticalBattleState.missionObjectiveByFleetId: MutableMap<Long, MissionObjective>` 추가. AI는 이 맵에서 읽고 DB 접근 없음.

**D-07** — `BattleTriggerService`가 전투 생성 시 참가 Fleet의 OperationPlan 조회하여 맵을 한 번에 채운다.

**D-08** — OperationPlan CRUD 시 `TacticalBattleService.syncOperationToActiveBattles(operationPlan)` 호출하여 활성 전투 맵 업데이트. **Spring 이벤트 vs 직접 호출 — Claude's discretion. Research recommendation in Section 4.**

**D-09** — `TacticalUnit.missionObjective`는 stub 유지, read-through 캐시. `missionObjectiveByFleetId` 맵이 SoT. 매 tick 시작 시점에 맵에서 갱신.

**D-10** — OperationPlan에 속하지 않는 Fleet의 missionObjective 기본값: 성격 기반.
- AGGRESSIVE → SWEEP
- DEFENSIVE → DEFENSE
- CAUTIOUS → DEFENSE
- BALANCED → DEFENSE
- POLITICAL → DEFENSE
- `MissionObjective.defaultForPersonality(personality)` 헬퍼로 정의.

**D-11** — 공적 보너스 배율 고정 `× 1.5` (50% 추가).

**D-12** — 지급 시점: 전투 종료 직후. `BattleResultService` (또는 equivalent — **이 페이즈에서 신규 생성해야 함**) 가 처리. **RESEARCH FINDING: `BattleResultService` does NOT exist yet — merit crediting must be added to `TacticalBattleService.endBattle()` or a new service must be created. See Section 7.**

**D-13** — 작전 CANCELLED 전이 시 동기화 채널이 `missionObjectiveByFleetId`에서 해당 fleet 제거 → 보너스 대상 아님.

**D-14** — 공적 보너스는 `Officer.meritPoints`에 직접 누적. `PlanetConquestService` 패턴 재사용. **RESEARCH FINDING: `PlanetConquestService.ConquestCommand.meritPoints` is a *constant value per conquest command* (500/200/50/300/800/300), NOT a mutation site on Officer. The codebase currently has NO merit-accumulation path from battles — RankLadderService only RESETS merit to constants on promotion/demotion. Phase 12 is creating the first merit-from-battle credit path. See Section 7.**

**D-15** — 별도 `OperationActivationService`. 매 tick `findByStatus(PENDING)` + fleet.planetId == targetStarSystemId 1개라도 있으면 ACTIVE 전이. `TickDaemon` 스케줄 내 `BattleTriggerService` 직전 권장.

**D-16** — Status 생명주기: `PENDING → ACTIVE → COMPLETED | CANCELLED`. DRAFT 없음.

**D-17** — ACTIVE 전이: 참가자 중 1부대라도 도달.

**D-18** — COMPLETED 조건:
- CONQUEST → targetStarSystemId의 owningFactionId == operation.factionId
- DEFENSE → targetStarSystemId에 적 함대 없음 + N틱 안정
- SWEEP → targetStarSystemId의 적 함대 모두 격침 또는 퇴각
- 평가 위치 — planner 결정 (별도 `OperationCompletionService` vs 통합)

**D-19** — `OperationPlanCommand` arg 확장: `objective`, `targetStarSystemId`, `participantFleetIds`. PositionCard 권한 유지. 검증: factionId 일치, fleet 소유 확인, 1부대=1작전 보장.

### Claude's Discretion
- TacticalBattleService 동기화 채널 구현 패턴 (Spring ApplicationEvent vs 직접 호출)
- OperationCompletionService 분리 여부 (별도 vs OperationActivationService 통합)
- DEFENSE 작전 안정 N틱 상수 값 (60틱 권장)
- expectedCompletionTick 컬럼의 실제 사용 시점 (이번 페이즈에서는 컬럼만)
- ConvertCommand-style 상태 전이 이벤트 발행 여부 (감사 로그용)
- OperationPlan repository 패턴 (JpaRepository + 커스텀 쿼리)

### Deferred Ideas (OUT OF SCOPE)
- `expectedCompletionTick` 자동 실패 반등 (컬럼만)
- DRAFT 상태
- 진영 내 다중 작전 우선순위/자원 경쟁
- scale별 차등 보상 배율
- 작전 실패 페널티
- 장교별 동시 작전 한도
- 연쇄 작전 (CASCADE)
- 작전 변경 플레이어 알림 메시지 (Phase 14)
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| **OPS-01** | 전략 게임의 작전계획(점령/방어/소탕) 목적이 전술전 AI 기본 행동을 결정한다 | Sections 1 (entity + migration), 3 (TacticalBattleEngine integration), 4 (sync channel). TacticalAI already reads `ctx.mission` (= `unit.missionObjective`) and switches behavior — all Phase 11 plumbing is in place. Phase 12 must plug fleet-based OperationPlan→map→unit lookup into this. |
| **OPS-02** | 작전에 참가한 부대만 공적 보너스를 받는다 | Section 7. Requires (a) new merit-credit path in `endBattle()`, (b) checking membership via `missionObjectiveByFleetId`, (c) `×1.5` on base merit. No base merit from battles exists today — Phase 12 bootstraps it. |
| **OPS-03** | 발령된 부대가 목표 성계 도달 시 작전이 시작된다 | Sections 5 (TickDaemon ordering) + 6 (fleet reach detection). `Fleet.planetId` is the "current star system" and is updated by `IntraSystemNavigationCommand`/equivalent. `OperationActivationService.activatePending()` compares `fleet.planetId == operation.targetStarSystemId`. Insertion point: `TickEngine.processTick()` before `tacticalBattleService.processSessionBattles()`. |
</phase_requirements>

## Summary

1. **Phase 12 is a pure backend integration phase** — no frontend scope, no new tactical engine logic. Every downstream AI consumer (Phase 11's `TacticalAI`/`TacticalAIRunner`) already reads `missionObjective` correctly; Phase 12 just feeds the right value in.
2. **The Flyway version in CONTEXT.md is stale.** V45 is already `V45__create_ship_unit_table.sql` and V46 is the current tip. The new migration must be **`V47__create_operation_plan.sql`**. This is a CONTEXT.md drift correction — planner must use V47.
3. **No merit-from-battle path exists in the codebase yet.** `RankLadderService` only RESETS `Officer.meritPoints` to constants after promotion/demotion; there is no `meritPoints += X` anywhere. Phase 12 is creating the *first* merit accumulation path. `PlanetConquestService.ConquestCommand.meritPoints` is an ENUM CONSTANT (500 for surrender, 800 for infiltration, etc.), not a mutation site — the credit never flows to `Officer.meritPoints` today.
4. **No `BattleResultService` exists** — merit credit must either (a) live directly in `TacticalBattleService.endBattle()` at line 308, or (b) be extracted to a new small `BattleResultService` class. Recommendation: inline in `endBattle()` (small enough, keeps transaction simple).
5. **`Fleet.planetId` is nullable (`Long?`)** — `OperationActivationService` must null-check. Also: `WarpNavigationCommand` and `IntraSystemNavigationCommand` both update `officer.planetId` but only `IntraSystemNavigationCommand` also sets `troop?.planetId = destPlanetId`. This is a **pre-existing gap** — warp navigation does not currently move the Fleet. **Planner must decide whether to (a) fix warp navigation as part of Phase 12 or (b) document the caveat that only intra-system moves activate PENDING operations.**
6. **The sync channel (D-08) should be direct method calls**, not Spring ApplicationEvent. Only one place in the entire codebase uses `ApplicationEventPublisher` (`GameEventService`), and OperationPlan CRUD already lives in command classes that run inside the `@Transactional` command executor — direct injection is the established pattern.
7. **TacticalBattleState is a `data class`** with mutable fields; adding `missionObjectiveByFleetId: MutableMap<Long, MissionObjective> = mutableMapOf()` is a trivial additive change. The read-through cache refresh happens at the top of `processTick` (new step 0.1 between `drainCommandBuffer` and `processOutOfCrcUnits`).
8. **The position card group for `작전계획`/`작전철회` is `CommandGroup.COMMAND`**, NOT `"commander"` as the CONTEXT.md phrased it. The phrase "commander 그룹" in D-19 is an informal reference to the Korean "지휘커맨드" (지휘 = command). Phase 11 has no gating work to do — `PositionCardRegistry.canExecute()` already handles it. Planner: retain the existing registration in `Gin7CommandRegistry.kt` lines 145–146.
9. **OperationActivationService insertion point is inside `TickEngine.processTick()` step 6**, immediately before `tacticalBattleService.processSessionBattles(world.id.toLong())`. The call must be wrapped in `try/catch` matching the surrounding pattern (see lines 79–94 of `TickEngine.kt`).
10. **Completion evaluation (D-18) is lightweight** — CONQUEST reads `StarSystem.factionId`, DEFENSE/SWEEP read in-memory Fleet list by `planetId == targetStarSystemId`. Recommendation: merge into `OperationActivationService.processTick()` as a second pass after activation. Naming: rename to `OperationLifecycleService`.

**Primary recommendation:** Decompose into 4 wave-aligned plans (see Section "Recommended Plan Decomposition"), starting with the entity + migration + enum helpers (Wave 0/1), then the command rewrite, then the engine/tick wiring, then the merit credit + sync channel.

## 1. Entity + Migration Mechanics

### JSONB column pattern for `List<Long>`

**Established JSONB pattern (from `Officer.kt` lines 267–281, `Faction.kt` lines 78–84):**

```kotlin
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "participant_fleet_ids", columnDefinition = "jsonb", nullable = false)
var participantFleetIds: MutableList<Long> = mutableListOf(),
```

**Hibernate JSON type is native** — Spring Boot 3.4.2 + Hibernate 6 ships `@JdbcTypeCode(SqlTypes.JSON)` built-in. NO `io.hypersistence.utils` or custom `UserType` is registered anywhere. Verified by grep: only imports seen are `org.hibernate.annotations.JdbcTypeCode` and `org.hibernate.type.SqlTypes`.

**No existing entity uses `List<Long>` stored as JSONB.** The closest analogues are:
- `Officer.positionCards: MutableList<String>` (jsonb) — line 267–269
- `StarSystem.starRgb: List<Int>` (jsonb) — line 38–39
- `Sovereign.*: MutableMap<String, Any>` (jsonb) — line 136

`MutableList<Long>` should deserialize without a custom converter because Jackson (Spring Boot default) maps JSON numbers to Long via Kotlin reflection on the declared type. **Gap to verify:** Jackson `Long` round-trip through `JdbcTypeCode.JSON` — write a unit/integration test to confirm (see Validation Architecture section).

### Primary key type alignment

| Entity | PK Type | File |
|--------|---------|------|
| Officer | `Long` (`@GeneratedValue(strategy = IDENTITY)`) | `entity/Officer.kt:14` |
| Fleet | `Long` (`@GeneratedValue(strategy = IDENTITY)`) | `entity/Fleet.kt:14` |
| Faction | `Long` (`@GeneratedValue(strategy = IDENTITY)`) | `entity/Faction.kt:12` |
| StarSystem | `Long` (confirmed via `StarSystemRepository.findBySessionIdAndFactionId(sessionId: Long, factionId: Long)`) | `entity/StarSystem.kt` |

**`targetStarSystemId: Long` aligns with existing PK type.** No type coercion needed.

### Flyway migration versioning — CRITICAL CORRECTION

**CONTEXT.md assumption:** V45 is next.
**Actual state:**

```
V43__add_victory_result_and_session_ranking.sql
V44__drop_turn_tables.sql
V45__create_ship_unit_table.sql     ← already exists
V46__add_command_proposal.sql        ← current tip
```

**Next free Flyway version: `V47__create_operation_plan.sql`**. Note there is a **gap at V39 (missing)** in the history — this is not a mistake to repeat; keep the sequence contiguous from V47 forward.

**Naming convention observed:** `V{N}__{snake_case_description}.sql`.

### Recommended migration SQL (V47)

Model on `V37__tactical_battle.sql` (which uses `participants JSONB` and `battle_state JSONB`) and `V45__create_ship_unit_table.sql` (which uses `meta JSONB NOT NULL DEFAULT '{}'`).

```sql
-- V47: OperationPlan 엔티티 테이블 (Phase 12 작전 연동)
-- gin7 작전계획: CONQUEST/DEFENSE/SWEEP × 단일 성계 목표 × 참가 함대 집합
-- 진영당 동시 다중 작전 허용 (D-02); 부대당 1작전 enforced at application layer (D-04)

CREATE TABLE operation_plan (
    id                        BIGSERIAL    PRIMARY KEY,
    session_id                BIGINT       NOT NULL,
    faction_id                BIGINT       NOT NULL,
    name                      VARCHAR(128) NOT NULL,

    -- MissionObjective enum: CONQUEST / DEFENSE / SWEEP
    objective                 VARCHAR(16)  NOT NULL,

    target_star_system_id     BIGINT       NOT NULL,

    -- OperationStatus enum: PENDING / ACTIVE / COMPLETED / CANCELLED
    status                    VARCHAR(16)  NOT NULL DEFAULT 'PENDING',

    -- JSONB array of Fleet IDs (List<Long>)
    participant_fleet_ids     JSONB        NOT NULL DEFAULT '[]',

    -- gin7 scale 1..7 (MCP cost / merit leverage)
    scale                     SMALLINT     NOT NULL DEFAULT 1,

    -- Audit / history / succession narrative
    issued_by_officer_id      BIGINT       NOT NULL,
    issued_at_tick            BIGINT       NOT NULL,

    -- Column added but consumption deferred (CONTEXT.md D-05)
    expected_completion_tick  BIGINT,

    -- DEFENSE stability counter (Claude's discretion, D-18)
    stability_tick_counter    INT          NOT NULL DEFAULT 0,

    created_at                TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_operation_plan_session FOREIGN KEY (session_id)
        REFERENCES session_state(id) ON DELETE CASCADE
);

-- Activation query: find PENDING operations per session
CREATE INDEX idx_operation_plan_session_status
    ON operation_plan(session_id, status);

-- BattleTrigger lookup: given a set of fleets, find their active operations
-- JSONB GIN index enables `participant_fleet_ids @> '[42]'` membership queries
CREATE INDEX idx_operation_plan_participants
    ON operation_plan USING GIN (participant_fleet_ids jsonb_path_ops);

-- Faction ownership (for Phase 13 strategic AI enumeration)
CREATE INDEX idx_operation_plan_faction
    ON operation_plan(session_id, faction_id, status);
```

### Entity file location and package

Place in `backend/game-app/src/main/kotlin/com/openlogh/entity/OperationPlan.kt` (matches existing entity layout).

`OperationStatus` enum: place in same file OR in `model/` package alongside other shared enums (`model/StatCategory.kt`, `model/Formation.kt`). Recommendation: `model/OperationStatus.kt` since `MissionObjective` already lives in `engine/tactical/ai/`.

## 2. Command System Wiring

### OperationPlanCommand current state

**File:** `backend/game-app/src/main/kotlin/com/openlogh/command/gin7/commander/OperationPlanCommand.kt`

Current arg shape (to REPLACE):
```kotlin
// Old (stub):
val scale = (arg?.get("scale") as? Number)?.toInt() ?: 1
val planName = arg?.get("planName") as? String ?: "작전계획-${env.year}-${env.month}"
nation.meta["operationPlan"] = mapOf(...)
```

**Class skeleton to preserve:**
- Extends `OfficerCommand(general, env, arg)` which extends `BaseCommand`
- `actionName: String = "작전계획"`
- `getCost()` returns `CommandCost()` (empty)
- `getCommandPoolType() = StatCategory.MCP`
- `getPreReqTurn() = 0`, `getPostReqTurn() = 0`
- `run(rng: Random): CommandResult` — suspend function

**Access to repositories from commands:** `command.services: CommandServices?` is injected by CommandExecutor at line 110 (in `CommandExecutor.kt`):

```kotlin
command.services = CommandServices(
    officerRepository, planetRepository, factionRepository,
    diplomacyService, messageService = messageService,
    modifierService = modifierService,
    fleetRepository = fleetRepository
)
```

**Gap:** `CommandServices` does NOT currently include `OperationPlanRepository`. The planner must add it — this requires (a) adding `operationPlanRepository: OperationPlanRepository` to `CommandServices`, (b) updating `CommandExecutor.executeOfficerCommand` construction sites to pass it, (c) wiring the repository bean via constructor injection in `CommandExecutor`.

### CommandExecutor flow

**File:** `backend/game-app/src/main/kotlin/com/openlogh/command/CommandExecutor.kt`

Entry point for general (장수) commands — `executeOfficerCommand(actionCode, general, env, arg, ...)` at line 75:

1. **Line 85–93:** Position card gate — `PositionCardRegistry.canExecute(officerCards, actionCode)`. `작전계획` is in `CommandGroup.COMMAND` (지휘커맨드), NOT the discussed "commander" string. No change needed here.
2. **Line 97–105:** Arg schema validation via `commandRegistry.getOfficerSchema(actionCode)`. If schema is non-NONE, `schema.parse(arg)` runs. **Gap:** the planner must either (a) add an `ArgSchema` for the new OperationPlan args or (b) do inline validation in `run()`. Inline is simpler and matches the current `OperationPlanCommand` style.
3. **Line 107–111:** `commandRegistry.createOfficerCommand(...)` instantiates the command from the lambda registered in `Gin7CommandRegistry.kt:145`.
4. **Line 113–116:** Cooldown check.
5. **Line 118–126:** Constraint check via `command.checkFullCondition()`.
6. **Line 142:** `command.run(rng)` — where OperationPlan actually persists.

**PositionCardGating decision logic:** `PositionCardRegistry.canExecute()` at `model/PositionCardRegistry.kt:96`:

```kotlin
fun canExecute(cards: List<PositionCard>, actionCode: String): Boolean {
    val requiredGroup = getCommandGroup(actionCode)  // looks up commandGroupMap
    return cards.any { requiredGroup in it.commandGroups }
}
```

`commandGroupMap` (line 15) registers `"작전계획"` → `CommandGroup.COMMAND` and `"작전철회"` → `CommandGroup.COMMAND` at lines 30–33. **No change needed.**

### CommandResult shape

```kotlin
data class CommandResult(
    val success: Boolean,
    val logs: List<String>,
    // other fields...
) {
    companion object {
        fun success(logs: List<String>): CommandResult = ...
        fun fail(reason: String): CommandResult = ...
    }
}
```

**pushLog helpers in BaseCommand.kt:**
- `pushLog(message)` — regular log, line 89
- `pushHistoryLog(message)` — prefix `_history:`, line 115
- `pushNationalHistoryLog(message)` — prefix `_nationalHistory:`, line 143
- `pushGlobalHistoryLog(message)` — prefix `_globalHistory:`, line 136

### OperationCancelCommand current implementation

**File:** `backend/game-app/src/main/kotlin/com/openlogh/command/gin7/commander/OperationCancelCommand.kt`

Current stub (lines 31–43):
```kotlin
val planName = (nation.meta["operationPlan"] as? Map<*, *>)?.get("name") ?: "진행 중인 작전"
nation.meta.remove("operationPlan")
pushLog("${general.name}이(가) '${planName}' 작전을 철회했다.")
return CommandResult.success(logs)
```

**Replacement pattern:**
1. Parse `operationId: Long` from arg
2. Lookup `OperationPlan` via `operationPlanRepository.findById(operationId)`
3. Validate: `operation.factionId == general.factionId` and status is `PENDING` or `ACTIVE`
4. Set `operation.status = OperationStatus.CANCELLED`, `operation.updatedAt = now()`
5. Save via repository
6. Invoke sync channel: `tacticalBattleService.syncOperationToActiveBattles(operation)`
7. Push logs + return success

### Command registration (no change needed)

`Gin7CommandRegistry.kt` already registers both commands at lines 145–146:
```kotlin
registerOfficerCommand("작전계획") { g, e, a -> OperationPlanCommand(g, e, a) }
registerOfficerCommand("작전철회") { g, e, a -> OperationCancelCommand(g, e, a) }
```

**However**, these lambdas take only `(general, env, arg)`. To inject the new `operationPlanRepository`, either:
- Option A: Access via `command.services!!.operationPlanRepository` after CommandExecutor wires it in.
- Option B: Change the registry signature to pass a repository/service handle.

**Recommendation: Option A.** It matches the existing `FleetRepository` injection at `CommandExecutor.kt:110` with zero lambda-signature churn.

### commands.json reference

**File:** `backend/shared/src/main/resources/data/commands.json` lines 306–325 — `operation_plan` and `operation_cancel` are already registered with:
- `cpCost: 10` (base), note `"10~1280 (variable by scale)"`
- `cpCost: 5` for cancel, note `"5~320 (variable by scale)"`
- `waitTime: 0`, `duration: 0`

**No change needed to commands.json** unless the planner decides to formalize the arg schema there (out of scope for Phase 12).

## 3. TacticalBattleEngine Integration

### TacticalUnit construction site (BattleTriggerService.buildInitialState)

**File:** `backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/BattleTriggerService.kt`
**Lines 117–143** (attacker), **146–174** (defender).

Each `TacticalUnit(...)` constructor call already accepts `personality`, `missionObjective` (via default value), `anchorX`, `anchorY` etc. from Phase 11. **The construction call does NOT currently pass `missionObjective` explicitly** — it defaults to `MissionObjective.DEFENSE` per the field default at `TacticalBattleEngine.kt:107`:

```kotlin
/** Current mission objective (stub for Phase 12 connection) */
var missionObjective: MissionObjective = MissionObjective.DEFENSE,
```

**Phase 12 changes needed at `BattleTriggerService`:**
1. Inject `operationPlanRepository: OperationPlanRepository` into the `@Service` constructor at line 20.
2. In `buildInitialState(battle: TacticalBattle)`, after building all TacticalUnits (line 176), populate `TacticalBattleState.missionObjectiveByFleetId`:
   ```kotlin
   val fleetIds = units.map { it.fleetId }
   val operations = operationPlanRepository
       .findActiveOrPendingBySessionAndFleetIds(battle.sessionId, fleetIds)
   // Map each fleet to its operation's objective; fleets without an operation
   // fall back to MissionObjective.defaultForPersonality(unit.personality)
   val missionMap = mutableMapOf<Long, MissionObjective>()
   for (unit in units) {
       val operation = operations.firstOrNull { unit.fleetId in it.participantFleetIds }
       missionMap[unit.fleetId] = operation?.objective
           ?: MissionObjective.defaultForPersonality(unit.personality)
   }
   ```
3. Pass `missionObjectiveByFleetId = missionMap` to `TacticalBattleState(...)` constructor at line 199.

### processTick read-through cache point

**File:** `backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/TacticalBattleEngine.kt`
**Line 244:** `fun processTick(state: TacticalBattleState, rng: Random = Random): TacticalBattleState`

Current step order (lines 244–256):
```kotlin
state.tickEvents.clear()
state.tickCount++
state.currentTick = state.tickCount

// Step 0: Drain command buffer (per D-03)
drainCommandBuffer(state)

// Step 0.5: Process out-of-CRC units (Phase 9 Plan 03)
processOutOfCrcUnits(state)

// Step 0.7: Process tactical AI for NPC/offline units (Phase 11)
TacticalAIRunner.processAITick(state)
```

**Phase 12 insertion point: new Step 0.6, between `processOutOfCrcUnits` and `TacticalAIRunner.processAITick`.**

```kotlin
// Step 0.6: Refresh mission objective read-through cache (Phase 12)
for (unit in state.units) {
    if (!unit.isAlive) continue
    state.missionObjectiveByFleetId[unit.fleetId]?.let { unit.missionObjective = it }
    // If not in map (fleet joined battle mid-operation), keep existing value
}
```

Rationale: this must run BEFORE `TacticalAIRunner.processAITick` because the runner reads `unit.missionObjective` (verified at `TacticalAIRunner.kt:77`).

### TacticalBattleState declaration

**File:** `TacticalBattleEngine.kt:141` — `data class TacticalBattleState(...)` (mutable data class with `var` fields + mutable collections).

**Phase 12 additive change — add field:**
```kotlin
/** Phase 12: missionObjective lookup by fleetId. SoT for TacticalUnit.missionObjective. */
val missionObjectiveByFleetId: MutableMap<Long, MissionObjective> = mutableMapOf(),
```

Place after `connectedPlayerOfficerIds` at line 193 to keep Phase 9/11 additions contiguous. The default empty map preserves backward compatibility for all existing tests.

### BattleTriggerService dependencies

Current constructor (lines 20–25):
```kotlin
@Service
class BattleTriggerService(
    private val fleetRepository: FleetRepository,
    private val officerRepository: OfficerRepository,
    private val starSystemRepository: StarSystemRepository,
    private val tacticalBattleRepository: TacticalBattleRepository,
)
```

**Phase 12 adds:** `private val operationPlanRepository: OperationPlanRepository`. Non-disruptive.

### BattleOutcome and merit credit location

**`checkBattleEnd`** is at `TacticalBattleEngine.kt:404` — returns `BattleOutcome?` but does NOT touch Officer state.

**`endBattle`** is the merit credit site. Located at `TacticalBattleService.kt:308`:

```kotlin
@Transactional
fun endBattle(battle: TacticalBattle, state: TacticalBattleState, outcome: BattleOutcome) {
    battle.phase = BattlePhase.ENDED.name
    battle.endedAt = OffsetDateTime.now()
    // ... updates fleet ships, calls gameEventService.fireBattle
}
```

**Current merit path:** Lines 326–336 loop over `state.units`, update `officer.ships` and `officer.morale`, then `officerRepository.save(officer)`. **No `meritPoints` mutation exists today.**

**Phase 12 addition:** Between line 335 (`officer.morale = unit.morale.toShort()`) and `officerRepository.save(officer)`, compute base merit per unit and apply ×1.5 if `unit.fleetId in state.missionObjectiveByFleetId.keys`. This preserves the existing transaction.

### TacticalBattleService structure

**@Service** with constructor-injected repositories:
```kotlin
@Service
class TacticalBattleService(
    private val tacticalBattleRepository: TacticalBattleRepository,
    private val fleetRepository: FleetRepository,
    private val officerRepository: OfficerRepository,
    private val battleTriggerService: BattleTriggerService,
    private val gameEventService: GameEventService,
    private val messagingTemplate: SimpMessagingTemplate,
    private val shipStatRegistry: ShipStatRegistry,
)
```

**Phase 12 additions:**
- Add `operationPlanRepository: OperationPlanRepository` to constructor
- Add `fun syncOperationToActiveBattles(operationPlan: OperationPlan)` method — iterates `activeBattles.values` (already a `ConcurrentHashMap<Long, TacticalBattleState>` at line 41) and updates each state's `missionObjectiveByFleetId` map per CANCELLED/ACTIVE/updated rules:
  - CANCELLED → remove all `participantFleetIds` entries from the map (so they fall back to personality default on next tick)
  - Other status changes → update `missionObjectiveByFleetId[fleetId] = operation.objective` for each participant

**Thread safety note:** `activeBattles` is `ConcurrentHashMap`, but `TacticalBattleState.missionObjectiveByFleetId` will be accessed concurrently by the tick loop (reader) and sync channel (writer). **Recommendation:** use `ConcurrentHashMap<Long, MissionObjective>` for this specific map field instead of `MutableMap`/`HashMap`.

## 4. Sync Channel Tradeoff — Direct Call vs ApplicationEvent

### Evidence from existing codebase

**ApplicationEventPublisher usage:** ONE file only — `backend/game-app/src/main/kotlin/com/openlogh/service/GameEventService.kt`. And even there it's used for domain events, not for cross-service orchestration.

**Direct service-to-service calls:** the dominant pattern. Examples:
- `OperationPlanCommand.run()` directly mutates `nation.meta` (current stub); equivalent replacement will directly call `operationPlanRepository.save()` and `tacticalBattleService.syncOperationToActiveBattles()`.
- `TacticalBattleService.processSessionBattles()` at line 88 directly calls `battleTriggerService.checkForBattles(sessionId)`.
- `TickEngine.processTick()` directly chains 8 services in sequence (lines 52–103).

**Other coordination patterns:**
- Command buffer (`ConcurrentLinkedQueue<TacticalCommand>`) for async WebSocket→tick handoff — this IS an event-like pattern but it's in-process and tick-aligned.
- No `@EventListener` is used anywhere in the game-app code.

### Recommendation: Direct call

**Rationale:**
1. The established pattern is direct @Service injection. Introducing ApplicationEvents for ONE integration would stand out and create inconsistency.
2. The sync channel must execute synchronously within the OperationPlanCommand's `@Transactional` boundary — otherwise a race between `commit` and map update could corrupt merit bonuses. Spring ApplicationEvents can be synchronous, but you need `@TransactionalEventListener(phase = AFTER_COMMIT)` to get the guarantee, which adds mental load.
3. Direct call is trivially testable: mock `tacticalBattleService` and assert `syncOperationToActiveBattles(plan)` is called with the expected argument.
4. OperationPlanCommand and OperationCancelCommand live in the same JVM as TacticalBattleService — there's no cross-process concern.

**Signature:**
```kotlin
// In TacticalBattleService:
fun syncOperationToActiveBattles(operation: OperationPlan) {
    for ((_, state) in activeBattles) {
        when (operation.status) {
            OperationStatus.CANCELLED -> {
                operation.participantFleetIds.forEach { fleetId ->
                    state.missionObjectiveByFleetId.remove(fleetId)
                }
            }
            OperationStatus.PENDING, OperationStatus.ACTIVE -> {
                operation.participantFleetIds.forEach { fleetId ->
                    state.missionObjectiveByFleetId[fleetId] = operation.objective
                }
            }
            OperationStatus.COMPLETED -> {
                // Leave the mission in place until the battle ends naturally;
                // COMPLETED only affects future battles.
            }
        }
    }
}
```

## 5. TickDaemon / TickEngine Ordering

### Scheduler location

**TickDaemon** — `backend/game-app/src/main/kotlin/com/openlogh/engine/TickDaemon.kt`
- `@Scheduled(fixedRateString = "\${app.turn.tick-ms:1000}")` at line 45 (1 tick/sec)
- `tick()` method iterates worlds via `sessionStateRepository.findByCommitSha(processCommitSha)` and calls `tickEngine.processTick(world)` at line 59

**TickEngine.processTick** — `backend/game-app/src/main/kotlin/com/openlogh/engine/TickEngine.kt:52`. Current order:

```kotlin
fun processTick(world: SessionState) {
    world.tickCount++                                                  // line 54
    world.gameTimeSec += GameTimeConstants.GAME_SECONDS_PER_TICK       // line 55
    realtimeService.processCompletedCommands(world)                    // line 58  — step 2
    if (world.tickCount % ... == 0L) regenerateCommandPoints(world)    // line 61  — step 3
    if (world.gameTimeSec >= ...) { advanceMonth; runMonthlyPipeline } // line 66  — step 4
    processPolitics(world)                                             // line 73  — step 5
    tacticalBattleService.processSessionBattles(world.id.toLong())     // line 76  — step 6 ★
    if (tickCount % SHIPYARD_INTERVAL == 0L) shipyardProductionService.runProduction // line 80 — step 7
    if (tickCount % SORTIE_COST_INTERVAL == 0L) fleetSortieCostService.processSortieCost // line 88 — step 8
    sessionStateRepository.save(world)                                 // line 97  — step 9
    if (tickCount % TICK_BROADCAST_INTERVAL == 0L) gameEventService.broadcastTickState // line 100
}
```

### Exact insertion point for OperationActivationService

**Insert as new step 5.5, immediately before line 76 (`tacticalBattleService.processSessionBattles`):**

```kotlin
// 5.5. Process operation lifecycle: PENDING→ACTIVE transitions + COMPLETED evaluation (Phase 12)
try {
    operationLifecycleService.processTick(world.id.toLong(), world.tickCount)
} catch (e: Exception) {
    logger.warn("Operation lifecycle error for world {}: {}", world.id, e.message)
}

// 6. Process active tactical battles
tacticalBattleService.processSessionBattles(world.id.toLong())
```

**Why BEFORE battle processing:** OperationActivationService evaluates "has any participant fleet reached target?" by reading `fleet.planetId`. If BattleTriggerService runs first, it could generate a battle at the target and mutate things the activation check depends on. The CONTEXT.md D-15 explicitly requires this order.

**Error handling pattern:** matches lines 79–94 (try/catch with `logger.warn`). Do NOT let a single broken operation crash the tick.

### Transactional boundary

**TickEngine.processTick is NOT itself `@Transactional`** — each called service owns its own transaction:
- `tacticalBattleService.processSessionBattles(sessionId)` has `@Transactional` on internal `endBattle(...)` method (line 307).
- `shipyardProductionService.runProduction(sessionId)` opens its own transaction.
- `sessionStateRepository.save(world)` runs auto-transactionally via Spring Data.

**Implication for `OperationLifecycleService.processTick()`:** annotate with `@Transactional` at the method level. Each tick's activation + completion evaluation is one logical unit.

## 6. Fleet Reach Detection

### Fleet.planetId semantics

**File:** `backend/game-app/src/main/kotlin/com/openlogh/entity/Fleet.kt:40`

```kotlin
@Column(name = "planet_id")
var planetId: Long? = null,
```

**It is nullable.** `OperationActivationService.activatePending()` must null-check:
```kotlin
val reached = operation.participantFleetIds.any { fleetId ->
    val fleet = fleetRepository.findById(fleetId).orElse(null) ?: return@any false
    fleet.planetId == operation.targetStarSystemId
}
```

### Fleet.planetId mutation sites

Grep across `com.openlogh` for `fleet.planetId =` / `troop?.planetId =`:

| Site | Mutation | Effect |
|------|----------|--------|
| `command/gin7/operations/IntraSystemNavigationCommand.kt:44` | `troop?.planetId = destPlanetId` | ✓ Updates fleet after intra-system navigation |
| `service/WarehouseService.kt:91` | `if (fleet.planetId != planetId)` (read only) | — |
| `service/TacticalBattleService.kt:459` | `officer.planetId = returnPlanetId` | Only Officer, NOT fleet |
| `command/gin7/operations/WarpNavigationCommand.kt:41` | `general.planetId = destPlanetId` | **BUG / GAP:** Officer moves but Fleet does NOT |
| `command/gin7/personal/MovementCommands.kt:35, 65` | `general.planetId = destPlanetId` | Officer only |

**⚠️ Critical gap surfaced:** `WarpNavigationCommand` updates `Officer.planetId` but NOT `Fleet.planetId`. This means a fleet that warps between star systems will have a STALE `Fleet.planetId` — and OperationActivationService will never detect arrival for CONQUEST operations that require cross-system movement.

**Two planner options:**
- **(A) Fix WarpNavigationCommand** to also set `troop?.planetId = destPlanetId` as a Phase 12 side-task. Minimal change: add one line. **Recommended — otherwise OPS-03 is demonstrably broken for 90% of the operations people will issue.**
- **(B) Document the caveat** and leave the fix for a future phase. Means OPS-03 only works for intra-system operations.

**Recommendation: (A)** — add a one-line fix to `WarpNavigationCommand.kt` as part of the Phase 12 plan that rewrites OperationPlanCommand (same commander/operations area).

### MovementService arrival detection

**File:** `backend/game-app/src/main/kotlin/com/openlogh/engine/map/MovementService.kt`

`calculateNextPosition()` returns `Triple<Float, Float, Boolean>` where the third element is `arrived`. **Note:** this operates on `Officer.posX/posY/destX/destY` floats, NOT on `Fleet.planetId`. Fleet position isn't stored on `Fleet` entity at all — it's derived from the leader Officer's position.

**Phase 12 does NOT need to hook MovementService.** The arrival detection is via `Fleet.planetId == operation.targetStarSystemId` comparison, which happens every tick inside `OperationLifecycleService.processTick()`. MovementService updates Officer position continuously, but only command classes (`IntraSystemNavigationCommand`, `WarpNavigationCommand`) update `Fleet.planetId` on completion.

### Race conditions

**Concern:** `OperationLifecycleService.processTick()` reads `Fleet.planetId` while some other process could be writing it.

**Analysis:**
- `Fleet.planetId` is only written from command handlers inside `CommandExecutor.executeOfficerCommand()`, which runs sequentially inside `realtimeService.processCompletedCommands(world)` at TickEngine.processTick line 58 — **before** the proposed OperationLifecycleService step 5.5.
- Within a single TickEngine.processTick invocation, the order is: commands → politics → operations → battles. No reader/writer race.
- Across ticks, JPA/Hibernate's default isolation (READ_COMMITTED on PostgreSQL) is sufficient.

**Verdict:** No race conditions at the current granularity. The planner should annotate `OperationLifecycleService.processTick()` as `@Transactional(readOnly = false)` and let Spring manage the isolation.

## 7. Merit Bonus Application

### Current Officer.meritPoints mutation sites

**ONLY two sites in the entire codebase** (grep `officer.meritPoints =` + `meritPoints \+=`):

| File:Line | Mutation | Purpose |
|-----------|----------|---------|
| `service/RankLadderService.kt:124` | `officer.meritPoints = RankHeadcount.MERIT_AFTER_PROMOTION` | **Reset** after promotion |
| `service/RankLadderService.kt:143` | `officer.meritPoints = RankHeadcount.MERIT_AFTER_DEMOTION` | **Reset** after demotion |

**There is NO `+=` or accumulator path.** `PlanetConquestService.ConquestCommand.meritPoints` is an enum CONSTANT (e.g., `SURRENDER_DEMAND.meritPoints = 500`), but the `ConquestResult` never actually credits this to any Officer. **Phase 12 is creating the first merit accumulation path in the codebase.**

### RankLadderService usage of meritPoints

`buildLadder()` at line 37–42:
```kotlin
return officers.sortedWith(
    compareByDescending<Officer> { it.officerLevel.toInt() }
        .thenByDescending { it.meritPoints }
        .thenByDescending { it.famePoints }
        .thenByDescending { totalStats(it) }
)
```

**Merit is only used as a sort key for promotion ordering.** Applying `×1.5` has no unexpected rank-threshold effect — higher merit just means earlier promotion consideration. The promotion path resets merit to a constant anyway (lines 124/143).

### Base merit computation flow (to be created)

**Current state:** Nothing. No base merit from tactical battles.

**Proposed flow for Phase 12:**

```kotlin
// In TacticalBattleService.endBattle(), after line 335 (officer.morale update)
// and before officerRepository.save(officer):

val baseMerit = computeBaseMerit(unit, outcome)
val isOperationParticipant = state.missionObjectiveByFleetId.containsKey(unit.fleetId) &&
                              operationMemberships[unit.fleetId] == true
val multiplier = if (isOperationParticipant) 1.5 else 1.0
val awarded = (baseMerit * multiplier).toInt()
officer.meritPoints += awarded

// computeBaseMerit: simple heuristic for this phase
private fun computeBaseMerit(unit: TacticalUnit, outcome: BattleOutcome): Int {
    val wonSide = outcome.winner ?: return 0
    if (unit.side != wonSide) return 0        // no merit for losers/draws
    val survivalRatio = unit.ships.toDouble() / unit.maxShips.coerceAtLeast(1)
    return (100 * survivalRatio).toInt().coerceAtLeast(10)  // 10..100 merit
}
```

**Key distinction:** `state.missionObjectiveByFleetId` contains BOTH operation participants AND personality-defaulted fleets. To distinguish, either:
- Track a second `operationParticipantFleetIds: Set<Long>` field on TacticalBattleState (populated from OperationPlan, NOT from the defaultForPersonality fallback), OR
- Re-query `operationPlanRepository.findActiveOperationForFleet(fleetId)` during endBattle (one extra query per participating officer — acceptable).

**Recommendation:** add `operationParticipantFleetIds: MutableSet<Long>` to TacticalBattleState. BattleTriggerService populates it alongside `missionObjectiveByFleetId`. Sync channel removes entries on CANCELLED (this enforces D-13 cleanly).

### Merit bonus computation location

**Option A (recommended): Inline in `TacticalBattleService.endBattle()`** — line 308.
- Pros: matches the "small surface area" of the phase, keeps transaction coherent, no new class.
- Cons: `endBattle` gains ~15 lines.

**Option B: New `BattleResultService` class** with `fun awardMerit(battle, state, outcome)`.
- Pros: cleaner if future phases add more "result processing" (e.g., rank promotions, fame).
- Cons: adds a file + @Service bean + test scaffold for 15 lines of logic.

**Recommendation: Option A for Phase 12.** If Phase 13's SAI needs more complex result processing, extract at that point.

## 8. Completion Conditions

### CONQUEST completion

**Data source:** `StarSystem.factionId` (owning faction of the target star system).
- Grep confirms: `planet.factionId` is the current owner field. `StarSystemService.kt:117` is where it's mutated: `starSystem.factionId = newFactionId`.
- Phase 12 check: `starSystemRepository.findById(operation.targetStarSystemId).map { it.factionId == operation.factionId }.orElse(false)`.

**Timing:** ownership change fires inside `PlanetCaptureProcessor` (called from PlanetConquestService — which is invoked via `TacticalBattleService.executeConquest()`). Once ownership flips, the next tick's `OperationLifecycleService.processCompletion()` will detect it and transition the operation to `COMPLETED`.

### DEFENSE completion

**Data source:** count of enemy fleets (`Fleet.factionId != operation.factionId`) at `Fleet.planetId == operation.targetStarSystemId`.

**Stability counter pattern (new field, D-18 planner discretion):** Add `stability_tick_counter INT NOT NULL DEFAULT 0` to the `operation_plan` table (already included in the V47 SQL above). Logic:
```kotlin
val enemyCount = fleetRepository
    .findByPlanetId(operation.targetStarSystemId)
    .count { it.factionId != operation.factionId }

if (enemyCount == 0) {
    operation.stabilityTickCounter++
    if (operation.stabilityTickCounter >= DEFENSE_STABILITY_TICKS) {  // 60 recommended
        operation.status = OperationStatus.COMPLETED
    }
} else {
    operation.stabilityTickCounter = 0  // reset on enemy presence
}
```

**No existing counter pattern in the codebase** for this — `OperationLifecycleService` will introduce it. The `DEFENSE_STABILITY_TICKS = 60` constant should live at the top of OperationLifecycleService as a `companion object const`.

### SWEEP completion

**Same data source as DEFENSE** — enemy fleet count at target.
```kotlin
val enemyCount = fleetRepository
    .findByPlanetId(operation.targetStarSystemId)
    .count { it.factionId != operation.factionId && /* not retreating */ true }
```

**"Not retreating" nuance:** `Fleet` entity does NOT have a retreat status field. Retreat happens inside `TacticalBattleState.units[i].isRetreating` which is transient in-memory state. For SWEEP completion, the safer check is just `enemyCount == 0` — if enemies retreated they'd either be destroyed (removed from Fleet table) OR warped elsewhere (Fleet.planetId changed). Both cases result in the count dropping to 0 at the persistence layer.

**Immediate completion (no stability window for SWEEP)** — as soon as enemyCount hits 0, transition to COMPLETED.

### OperationCompletionService vs OperationActivationService

**Recommendation: merge into single `OperationLifecycleService`** with two methods:
- `activatePending(sessionId, tickCount)` — PENDING → ACTIVE
- `evaluateCompletion(sessionId, tickCount)` — ACTIVE → COMPLETED

Both called from `processTick(sessionId, tickCount)` in sequence. Rationale:
- Shared DB queries (both need `findBySessionIdAndStatus`)
- Shared `fleetRepository.findByPlanetId` lookups
- One transaction per tick
- One test fixture
- Fewer classes = less Phase 12 surface area

## 9. Validation Architecture (Nyquist Dimension 8 — MANDATORY)

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 (Jupiter) + Spring Boot Test 3.4.2 + H2 in-memory DB |
| Config file | `backend/game-app/build.gradle.kts` (gradle-managed), `backend/game-app/src/test/resources/application-test.yml` if present |
| Quick run command | `cd backend && ./gradlew :game-app:test --tests "com.openlogh.engine.tactical.ai.*" -x ktlintCheck` |
| Full suite command | `cd backend && ./gradlew :game-app:test` |

### Test file location convention

Observed from existing structure:
- `backend/game-app/src/test/kotlin/com/openlogh/engine/tactical/ai/TacticalAIRunnerTest.kt` (unit, no Spring)
- `backend/game-app/src/test/kotlin/com/openlogh/integration/ScenarioPlayableIntegrationTest.kt` (@SpringBootTest)

Phase 12 tests should mirror this pattern:
- `backend/game-app/src/test/kotlin/com/openlogh/engine/tactical/ai/MissionObjectiveDefaultTest.kt` (unit)
- `backend/game-app/src/test/kotlin/com/openlogh/service/OperationLifecycleServiceTest.kt` (unit with mocks)
- `backend/game-app/src/test/kotlin/com/openlogh/service/OperationPlanIntegrationTest.kt` (@SpringBootTest with H2)
- `backend/game-app/src/test/kotlin/com/openlogh/engine/tactical/BattleTriggerOperationInjectionTest.kt` (unit)
- `backend/game-app/src/test/kotlin/com/openlogh/service/OperationMeritBonusTest.kt` (integration)

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| OPS-01 | `MissionObjective.defaultForPersonality(AGGRESSIVE) == SWEEP` and 4 others | unit | `./gradlew :game-app:test --tests "com.openlogh.engine.tactical.ai.MissionObjectiveDefaultTest"` | ❌ Wave 0 |
| OPS-01 | `BattleTriggerService.buildInitialState()` populates `missionObjectiveByFleetId` from OperationPlan | unit (mocked repo) | `./gradlew :game-app:test --tests "com.openlogh.engine.tactical.BattleTriggerOperationInjectionTest"` | ❌ Wave 0 |
| OPS-01 | TacticalBattleEngine.processTick read-through: `missionObjectiveByFleetId[fleetId]` propagates to `unit.missionObjective` at tick start | unit | `./gradlew :game-app:test --tests "com.openlogh.engine.tactical.TacticalBattleEngineTest.mission_read_through*"` | ❌ Wave 0 (extend existing TacticalBattleEngineTest) |
| OPS-01 | TacticalAI reads new `unit.missionObjective` after sync channel fires | integration | `./gradlew :game-app:test --tests "com.openlogh.service.OperationLifecycleServiceTest.sync_updates_active_battles"` | ❌ Wave 0 |
| OPS-02 | Merit bonus × 1.5 applied for fleets present in `missionObjectiveByFleetId` when battle ends as victory | integration (@SpringBootTest) | `./gradlew :game-app:test --tests "com.openlogh.service.OperationMeritBonusTest"` | ❌ Wave 0 |
| OPS-02 | Merit bonus NOT applied for non-participant fleets | integration | `./gradlew :game-app:test --tests "com.openlogh.service.OperationMeritBonusTest.non_participant_no_bonus"` | ❌ Wave 0 |
| OPS-02 | CANCELLED mid-battle → no bonus awarded | integration | `./gradlew :game-app:test --tests "com.openlogh.service.OperationMeritBonusTest.cancelled_removes_bonus"` | ❌ Wave 0 |
| OPS-03 | `OperationLifecycleService.activatePending()` transitions PENDING → ACTIVE when any participant fleet reaches targetStarSystemId | unit (mocked repo) | `./gradlew :game-app:test --tests "com.openlogh.service.OperationLifecycleServiceTest.activates_on_first_arrival"` | ❌ Wave 0 |
| OPS-03 | PENDING remains PENDING when no participant has reached target | unit | `./gradlew :game-app:test --tests "com.openlogh.service.OperationLifecycleServiceTest.remains_pending_if_none_arrived"` | ❌ Wave 0 |
| OPS-03 | TickEngine.processTick runs OperationLifecycleService BEFORE tacticalBattleService.processSessionBattles | integration | `./gradlew :game-app:test --tests "com.openlogh.engine.TickEngineOrderingTest.operation_activation_before_battle_trigger"` | ❌ Wave 0 |
| OPS-03 | CONQUEST completion: StarSystem.factionId == operation.factionId → COMPLETED | unit | `./gradlew :game-app:test --tests "com.openlogh.service.OperationLifecycleServiceTest.conquest_completion"` | ❌ Wave 0 |
| OPS-03 | DEFENSE completion: 60 ticks of no enemy → COMPLETED | unit | `./gradlew :game-app:test --tests "com.openlogh.service.OperationLifecycleServiceTest.defense_stability_window"` | ❌ Wave 0 |
| OPS-03 | SWEEP completion: enemy count at target drops to 0 → COMPLETED | unit | `./gradlew :game-app:test --tests "com.openlogh.service.OperationLifecycleServiceTest.sweep_completion"` | ❌ Wave 0 |
| — | Flyway V47 applies cleanly; `operation_plan` table schema matches `OperationPlan` entity | contract | `./gradlew :game-app:flywayMigrate` (integration) | ❌ Wave 0 |
| — | JSONB round-trip of `participantFleetIds: List<Long>` via Hibernate 6 JSON type | unit/integration | `./gradlew :game-app:test --tests "com.openlogh.repository.OperationPlanRepositoryTest"` | ❌ Wave 0 |

### Sampling Strategy

**Per task commit:** `./gradlew :game-app:test --tests "com.openlogh.service.Operation*"` — ~10 seconds, covers the new service.

**Per wave merge:** `./gradlew :game-app:test --tests "com.openlogh.service.*" --tests "com.openlogh.engine.tactical.*"` — covers service + engine touchpoints.

**Phase gate:** `./gradlew :game-app:test` (full suite) — must be green before `/gsd:verify-work`.

**Boundary sampling (per test):**
- Empty `participantFleetIds` list → command should fail validation (business rule)
- Fleet at wrong planet → `activatePending` leaves status PENDING
- Fleet at right planet but different faction (ghost fleet) → should still activate (we check fleetId, not factionId, for activation)
- Multiple operations on same faction targeting same star system → both activate independently (D-02)
- Cancelled operation mid-battle → sync channel removes from map, merit bonus not awarded
- Fleet reassigned between tick N and N+1 → new operation's missionObjective takes precedence; old operation's participantFleetIds must exclude reassigned fleets (D-04)
- Stale `missionObjectiveByFleetId` after CANCELLED → read-through cache must fall back to `defaultForPersonality` at next tick

### Failure Modes (what can silently break)

1. **Fleet reassigned between tick N and N+1** — if OperationPlanCommand's validation fails to atomically remove the fleet from the previous operation's participantFleetIds, a fleet could be "in two operations" briefly. Mitigation: the command must read ALL active operations for the faction, remove any prior membership, THEN save the new operation — inside one @Transactional block.
2. **Battle created before OperationLifecycleService runs** — if the tick ordering gets reversed (regression), activation would happen AFTER battle creation, so BattleTriggerService would see PENDING operations and fill the map with the PENDING mission. Activation status wouldn't flow into the battle map on the same tick. Test with `TickEngineOrderingTest` that asserts call ordering via Mockito's `InOrder`.
3. **Stale `missionObjectiveByFleetId` cache after CANCELLED** — if sync channel fires AFTER `endBattle` begins, a fleet could get the bonus. Mitigation: endBattle runs inside one @Transactional block; sync channel runs outside it. Test: cancel during tick N, assert no merit bonus applied in tick N+1's endBattle.
4. **JSONB List<Long> serialization** — Jackson might deserialize JSON numbers as `Integer` on systems where `Long` fits in int range, breaking `fleetId in participantFleetIds` lookups for small IDs. Mitigation: add an integration test with fleetId = 1L and fleetId = 10_000_000_000L (> Int.MAX_VALUE) round-trip.
5. **WarpNavigationCommand still doesn't update Fleet.planetId** — pre-existing gap (Section 6). If not fixed in this phase, 90% of cross-system operations will never activate. Document and fix.
6. **TacticalBattleState.missionObjectiveByFleetId concurrency** — tick loop reads, sync channel writes. Use `ConcurrentHashMap` instead of `HashMap`/`mutableMapOf()`. Tests: run 1000-tick stress test with concurrent sync invocations.

### Rollback / Repair

**Flyway V47 rollback:**
- Flyway Community Edition does NOT support automatic down-migrations. If V47 fails mid-apply:
  - If the `CREATE TABLE` succeeded but the index failed → drop the index manually then retry, OR wrap the whole migration in a `BEGIN / COMMIT` transaction (PostgreSQL supports transactional DDL, so a failed index would roll back the CREATE TABLE automatically).
  - If the migration applied but the entity doesn't match → use `./gradlew flywayRepair` to fix the `flyway_schema_history` checksum after editing the SQL file.

**Recommendation:** wrap V47 in explicit `BEGIN; ... COMMIT;` to leverage PostgreSQL transactional DDL. Example:
```sql
BEGIN;
CREATE TABLE operation_plan (...);
CREATE INDEX idx_operation_plan_session_status ON operation_plan(session_id, status);
CREATE INDEX idx_operation_plan_participants ON operation_plan USING GIN (participant_fleet_ids jsonb_path_ops);
CREATE INDEX idx_operation_plan_faction ON operation_plan(session_id, faction_id, status);
COMMIT;
```

### Wave 0 Gaps

- [ ] `backend/game-app/src/test/kotlin/com/openlogh/engine/tactical/ai/MissionObjectiveDefaultTest.kt` — covers `MissionObjective.defaultForPersonality()` (unit)
- [ ] `backend/game-app/src/test/kotlin/com/openlogh/service/OperationLifecycleServiceTest.kt` — activation + completion per objective type (unit, mocked repos)
- [ ] `backend/game-app/src/test/kotlin/com/openlogh/service/OperationPlanCommandTest.kt` — replaces the stub-era test if any; validates arg parsing, 1-fleet-1-op enforcement, position card gating (integration)
- [ ] `backend/game-app/src/test/kotlin/com/openlogh/service/OperationMeritBonusTest.kt` — ×1.5 bonus applied to participants, not others (integration)
- [ ] `backend/game-app/src/test/kotlin/com/openlogh/repository/OperationPlanRepositoryTest.kt` — JSONB round-trip for `List<Long>` (@DataJpaTest or @SpringBootTest)
- [ ] `backend/game-app/src/test/kotlin/com/openlogh/engine/tactical/BattleTriggerOperationInjectionTest.kt` — verifies `missionObjectiveByFleetId` is populated at battle init from OperationPlan (unit)
- [ ] `backend/game-app/src/test/kotlin/com/openlogh/engine/TickEngineOrderingTest.kt` — asserts OperationLifecycleService runs before TacticalBattleService in a tick
- [ ] Extend `backend/game-app/src/test/kotlin/com/openlogh/engine/tactical/TacticalBattleEngineTest.kt` with `mission_read_through_refreshes_unit_objective_at_tick_start()` test

No framework install needed — JUnit 5, Spring Boot Test, H2 are already on the classpath (verified via existing Phase 11 tests).

## 10. Project Skills Compliance

Skills located at `.claude/skills/` are still written against the legacy `com.opensam` package. Most direct `grep` commands in the SKILL.md files will miss `com.openlogh` code. Interpret the rules conceptually.

| Skill | Applicable to Phase 12? | Rules to Honor |
|-------|------------------------|---------------|
| `verify-entity-parity` | ✓ Partially | Rule 3: every game entity has `session_id` FK — OperationPlan **MUST** include `session_id BIGINT NOT NULL` + FK to `session_state(id) ON DELETE CASCADE` (already in proposed V47 SQL). Rule 4: Entity ↔ DB schema alignment — table name `operation_plan`, column names snake_case, JPA field camelCase with explicit `@Column(name=...)` where it diverges. Rule 5: TypeScript type parity is OUT OF SCOPE (Phase 14 handles frontend). |
| `verify-architecture` | ✓ Fully | Rule 1 (package structure): `OperationPlan` in `entity/`, `OperationPlanRepository` in `repository/`, `OperationLifecycleService` in `service/` (or `engine/` if following BattleTriggerService precedent — BattleTriggerService is under `engine/tactical/` despite being an @Service). **Recommendation: place OperationLifecycleService in `service/` since it's not tactical-engine-internal.** Rule 2 (dependency direction): OperationPlanCommand → OperationPlanRepository OK; OperationLifecycleService → FleetRepository + StarSystemRepository OK. Rule 5 (Repository pattern): no direct `EntityManager` in Service/Command. Rule 6 (domain model independence): OperationPlan entity imports only `jakarta.persistence.*` + `org.hibernate.annotations.*`. Rule 7 (DTO separation): OperationPlanCommand args via `Map<String, Any>` (current command pattern) — no entity exposure. Rule 8 (tests present): 7 test files listed in Wave 0 Gaps cover every layer. |
| `verify-command-parity` | ✗ Legacy only | This skill targets the legacy PHP 93-command catalog. OperationPlan is a gin7 command (already in `Gin7CommandRegistry`), not a legacy PHP command. Skip — Phase 12 is not a parity task. |
| `verify-logic-parity` | ✗ Legacy only | Same reason — no legacy PHP counterpart for OperationPlan as a first-class entity. The gin7 manual has an "Operation Plan" command described, but there is no legacy PHP source to diff against. Skip. |
| `verify-game-tests` | ✓ Fully | Rule 2: every new command has a test (`OperationPlanCommandTest`). Rule 3: engine + service layers have tests (covered in Wave 0). Rule 4 edge cases: included in Sampling Strategy above. Rule 5: `./gradlew :game-app:test` green before merge. |

**Architecture parity: OperationPlan naming consistency.** Field names follow `camelCase` in Kotlin and `snake_case` in SQL. `participantFleetIds` ↔ `participant_fleet_ids`, `targetStarSystemId` ↔ `target_star_system_id`, `issuedByOfficerId` ↔ `issued_by_officer_id`. This matches every other entity in the codebase.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK 17 | Backend compilation | ✓ | (per STACK.md) | — |
| Gradle 8.x | Backend build | ✓ | wrapper bundled | — |
| PostgreSQL 16 | Flyway + integration tests | ✓ (docker-compose.yml) | 16 | H2 in-memory for unit tests |
| Hibernate 6 JSON type | `@JdbcTypeCode(SqlTypes.JSON)` | ✓ | Spring Boot 3.4.2 | — |
| Spring Data JPA | Repository interfaces | ✓ | 3.4.2 | — |
| JUnit 5 | Test framework | ✓ | Jupiter | — |
| H2 | In-memory test DB | ✓ | — | — |

**All dependencies available. No external install required for Phase 12.**

## Open Questions / Planner Discretion

1. **Sync channel pattern** — *Research recommendation:* direct call. `TacticalBattleService.syncOperationToActiveBattles(operation)` invoked from `OperationPlanCommand.run()` and `OperationCancelCommand.run()` after repository save. Rationale: codebase uses direct calls exclusively except for `GameEventService`. (See Section 4.)

2. **OperationCompletionService separation** — *Research recommendation:* merge into single `OperationLifecycleService` with `activatePending()` + `evaluateCompletion()` methods called sequentially from `processTick()`. Rationale: shared queries, one transaction, less surface area. (See Section 8.)

3. **DEFENSE stability N-ticks** — *Research recommendation:* 60 ticks (1 game-minute at 1 tick/sec). Add as `companion object const val DEFENSE_STABILITY_TICKS = 60` in `OperationLifecycleService`. CONTEXT.md already recommends this. (See Section 8.)

4. **`expectedCompletionTick` usage** — Deferred per CONTEXT.md D-05. Add column to V47, add `var expectedCompletionTick: Long?` to entity, leave unused in all Phase 12 logic. Phase 13 or later will consume.

5. **State transition audit log** — Discretion item. *Research recommendation:* emit a simple `pushNationalHistoryLog("작전 X 시작됨", ...)` from `OperationLifecycleService.activate(operation)` to the `gameEventService` broadcast channel. This matches the existing gin7-style logging without introducing a new event type. Deferred to Phase 14 if preferred.

6. **OperationPlan repository pattern** — JpaRepository + custom queries. *Research recommendation:*
   ```kotlin
   interface OperationPlanRepository : JpaRepository<OperationPlan, Long> {
       fun findBySessionIdAndStatus(sessionId: Long, status: OperationStatus): List<OperationPlan>
       fun findBySessionIdAndFactionIdAndStatusIn(
           sessionId: Long, factionId: Long, statuses: List<OperationStatus>
       ): List<OperationPlan>

       // JSONB membership query — PostgreSQL-specific
       @Query(
           value = "SELECT * FROM operation_plan " +
                   "WHERE session_id = :sessionId " +
                   "AND status IN ('PENDING', 'ACTIVE') " +
                   "AND participant_fleet_ids @> CAST(:fleetIdJson AS jsonb)",
           nativeQuery = true,
       )
       fun findActiveOrPendingByFleetId(
           @Param("sessionId") sessionId: Long,
           @Param("fleetIdJson") fleetIdJson: String,
       ): List<OperationPlan>
   }
   ```
   **Caveat:** the `@>` JSONB operator works on PostgreSQL but NOT on H2. For H2 integration tests, use `findBySessionIdAndStatus` then filter by fleetId in Kotlin.

7. **WarpNavigationCommand Fleet sync gap** — Pre-existing bug surfaced during research. *Research recommendation:* add one-line fix to `WarpNavigationCommand.kt` as part of the same plan that rewrites OperationPlanCommand. Without this fix, OPS-03 is demonstrably broken for cross-system operations.

8. **CONTEXT.md Flyway version drift** — V45 is already taken by `V45__create_ship_unit_table.sql`. **Planner must use V47** for the OperationPlan migration.

## Recommended Plan Decomposition

Based on dependency order and wave groupings. Each plan should be completable in ≤ 2 atomic tasks by the executor.

### Plan 12-01: Entity foundation + migration (Wave 0 — no other dependencies)
**Scope:**
- Create `V47__create_operation_plan.sql` (table + 3 indexes, wrapped in BEGIN/COMMIT)
- Create `com.openlogh.entity.OperationPlan` JPA entity
- Create `com.openlogh.model.OperationStatus` enum
- Extend `com.openlogh.engine.tactical.ai.MissionObjective` with `companion object fun defaultForPersonality(p: PersonalityTrait): MissionObjective`
- Create `com.openlogh.repository.OperationPlanRepository` interface
- Tests: `MissionObjectiveDefaultTest` (unit), `OperationPlanRepositoryTest` (integration, JSONB round-trip)

**Files touched:** ~6 new files, 1 migration, 0 modified.

### Plan 12-02: Command rewrite + registry wiring
**Scope:**
- Add `operationPlanRepository: OperationPlanRepository` to `CommandServices` and `CommandExecutor` constructor
- Rewrite `OperationPlanCommand.run()` — accept new args (`objective`, `targetStarSystemId`, `participantFleetIds`, optional `planName`), validate, enforce 1-fleet-1-operation (remove from prior operations), persist, trigger sync channel (will be a no-op until Plan 12-03 adds it — guard with a nullable service or sentinel)
- Rewrite `OperationCancelCommand.run()` — accept `operationId`, validate ownership + status, set CANCELLED, persist, trigger sync channel
- Fix `WarpNavigationCommand` to also set `troop?.planetId = destPlanetId` (one-line bug fix)
- Tests: `OperationPlanCommandTest`, `OperationCancelCommandTest` (integration, @SpringBootTest)

**Files touched:** 4 modified (CommandExecutor, CommandServices, OperationPlanCommand, OperationCancelCommand, WarpNavigationCommand), ~2 new test files.

### Plan 12-03: Tactical battle state wiring + sync channel
**Scope:**
- Add `missionObjectiveByFleetId: MutableMap<Long, MissionObjective>` (ConcurrentHashMap) to `TacticalBattleState`
- Add `operationParticipantFleetIds: MutableSet<Long>` to `TacticalBattleState` (for merit bonus filtering)
- Inject `operationPlanRepository` into `BattleTriggerService`; populate both maps in `buildInitialState()`
- Add `fun syncOperationToActiveBattles(operation: OperationPlan)` to `TacticalBattleService`
- Extend `TacticalBattleEngine.processTick()` — insert Step 0.6 read-through cache refresh
- Tests: `BattleTriggerOperationInjectionTest` (unit), `TacticalBattleEngineTest.mission_read_through_*` (extend existing)

**Files touched:** 3 modified (TacticalBattleEngine state class, BattleTriggerService, TacticalBattleService), 1 engine method, ~2 new test files.

### Plan 12-04: OperationLifecycleService + TickEngine wiring + merit bonus
**Scope:**
- Create `com.openlogh.service.OperationLifecycleService` with `processTick(sessionId, tickCount)`, `activatePending()`, `evaluateCompletion()` (CONQUEST/DEFENSE/SWEEP) methods
- Inject into `TickEngine`; call at new step 5.5 in `processTick()`
- Inject into `OperationPlanCommand`/`OperationCancelCommand` (via CommandServices) — wire real sync channel
- Add merit bonus logic to `TacticalBattleService.endBattle()` — check `operationParticipantFleetIds`, apply `×1.5` to a base merit computation, update `officer.meritPoints`
- Tests: `OperationLifecycleServiceTest` (unit, mocked), `OperationMeritBonusTest` (integration), `TickEngineOrderingTest`

**Files touched:** 1 new service, 2 modified (TickEngine, TacticalBattleService.endBattle), ~3 new test files.

### Plan dependency graph
```
12-01 (entity/migration)
   ↓
12-02 (command rewrite) ←── independent of 12-03 until sync channel is wired
   ↓
12-03 (battle state + sync channel)
   ↓
12-04 (lifecycle service + tick wiring + merit bonus)
```

**Plans 12-01 and 12-02 can run in parallel.** Plans 12-03 and 12-04 must run sequentially after both.

**Suggested wave execution:**
- Wave 0: 12-01 (prerequisite entity)
- Wave 1: 12-02 + 12-03 in parallel (different file sets, both depend on 12-01 only)
- Wave 2: 12-04 (depends on 12-02 and 12-03)

## Sources

### Primary (HIGH confidence)
- `backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/ai/MissionObjective.kt` (Phase 11 enum definition)
- `backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/ai/TacticalAI.kt` (consumer logic — reads `ctx.mission`)
- `backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/ai/TacticalAIRunner.kt` (line 77: `mission = unit.missionObjective`)
- `backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/TacticalBattleEngine.kt` (TacticalUnit + TacticalBattleState data classes, processTick at line 244, checkBattleEnd at 404)
- `backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/BattleTriggerService.kt` (buildInitialState at 104, TacticalUnit construction at 117–174)
- `backend/game-app/src/main/kotlin/com/openlogh/service/TacticalBattleService.kt` (endBattle at 308, processSessionBattles at 88, activeBattles at 41)
- `backend/game-app/src/main/kotlin/com/openlogh/engine/TickEngine.kt` (processTick at 52, step 6 at line 76)
- `backend/game-app/src/main/kotlin/com/openlogh/engine/TickDaemon.kt` (@Scheduled at 45)
- `backend/game-app/src/main/kotlin/com/openlogh/command/gin7/commander/OperationPlanCommand.kt` (current stub)
- `backend/game-app/src/main/kotlin/com/openlogh/command/gin7/commander/OperationCancelCommand.kt` (current stub)
- `backend/game-app/src/main/kotlin/com/openlogh/command/CommandExecutor.kt` (executeOfficerCommand at 75, PositionCardRegistry.canExecute at 87)
- `backend/game-app/src/main/kotlin/com/openlogh/command/Gin7CommandRegistry.kt:145-146` (lambda registration)
- `backend/game-app/src/main/kotlin/com/openlogh/command/BaseCommand.kt` (pushLog/pushNationalHistoryLog helpers)
- `backend/game-app/src/main/kotlin/com/openlogh/entity/Officer.kt` (Long PK, meritPoints field, JSONB pattern lines 267–281)
- `backend/game-app/src/main/kotlin/com/openlogh/entity/Fleet.kt` (Long PK, planetId: Long? at line 40)
- `backend/game-app/src/main/kotlin/com/openlogh/entity/Faction.kt` (JSONB pattern, Long PK)
- `backend/game-app/src/main/kotlin/com/openlogh/model/PositionCardRegistry.kt` (commandGroupMap at 15, 작전계획→COMMAND at 30–33, canExecute at 96)
- `backend/game-app/src/main/kotlin/com/openlogh/service/RankLadderService.kt` (ONLY merit mutation sites, lines 124, 143)
- `backend/game-app/src/main/kotlin/com/openlogh/command/gin7/operations/IntraSystemNavigationCommand.kt` (troop?.planetId = destPlanetId at line 44)
- `backend/game-app/src/main/kotlin/com/openlogh/command/gin7/operations/WarpNavigationCommand.kt` (Officer only — gap at line 41)
- `backend/game-app/src/main/resources/db/migration/V37__tactical_battle.sql` (Flyway pattern)
- `backend/game-app/src/main/resources/db/migration/V45__create_ship_unit_table.sql` (confirms V45 is taken)
- `backend/game-app/src/main/resources/db/migration/V46__add_command_proposal.sql` (confirms V46 is current tip)
- `backend/shared/src/main/resources/data/commands.json` lines 306–325 (operation_plan/operation_cancel entries)
- `.claude/skills/verify-entity-parity/SKILL.md`, `.claude/skills/verify-architecture/SKILL.md`, `.claude/skills/verify-game-tests/SKILL.md`
- `.planning/phases/12-operation-integration/12-CONTEXT.md` (locked decisions)
- `.planning/phases/12-operation-integration/12-DISCUSSION-LOG.md` (alternative tradeoffs)

### Secondary (MEDIUM confidence)
- `backend/game-app/src/main/kotlin/com/openlogh/repository/FleetRepository.kt` (available query methods: findByPlanetId, findBySessionId, findByFactionId)
- `backend/game-app/src/main/kotlin/com/openlogh/engine/ai/PersonalityTrait.kt` (5 traits: AGGRESSIVE/DEFENSIVE/BALANCED/POLITICAL/CAUTIOUS)
- `backend/game-app/src/test/kotlin/com/openlogh/engine/tactical/ai/TacticalAIRunnerTest.kt` (test harness pattern, TacticalBattleState manual construction)
- PostgreSQL docs on `jsonb @>` operator and `jsonb_path_ops` GIN index (HIGH confidence on PostgreSQL side, MEDIUM on Hibernate side re: native query handling)

### Tertiary (LOW confidence)
- Jackson+Kotlin `Long` vs `Integer` deserialization behavior for JSONB via Hibernate 6 — *needs integration test to confirm*
- H2 compatibility with `jsonb @>` — *confirmed NOT supported*; integration tests using this operator must use PostgreSQL testcontainers or fallback filter in Kotlin

## Metadata

**Confidence breakdown:**
- Entity + migration: HIGH — established JSONB pattern verified across 6+ entities, Flyway versioning confirmed, PK types aligned
- Architecture / wiring: HIGH — direct file reads of every integration point (TickEngine, TacticalBattleEngine, TacticalBattleService, BattleTriggerService)
- Merit bonus logic: HIGH — confirmed NO existing merit-from-battle path, Phase 12 bootstraps it
- Sync channel recommendation: HIGH — evidence-based (direct calls dominate, ApplicationEvent used only in GameEventService)
- Completion conditions: MEDIUM — CONQUEST and SWEEP are clear; DEFENSE stability counter is new pattern I'm proposing, no precedent in codebase
- JSONB List<Long> round-trip: LOW — no existing entity uses this exact type; needs confirmation test
- WarpNavigationCommand bug: HIGH — grep result is definitive, fix is one line

**Research date:** 2026-04-08
**Valid until:** 2026-05-08 (30 days; Spring Boot, Hibernate, PostgreSQL are stable)

## RESEARCH COMPLETE
