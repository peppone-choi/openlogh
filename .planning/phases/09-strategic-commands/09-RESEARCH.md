# Phase 9: 지휘권 분배 + 커맨드레인지서클 - Research

**Researched:** 2026-04-07
**Domain:** Tactical battle command hierarchy, CRC mechanics, communication jamming
**Confidence:** HIGH

## Summary

Phase 9 implements the core gin7 organizational simulation mechanics within tactical battles: sub-fleet assignment, Command Range Circle (CRC) order propagation, priority-based command delegation, and communication jamming. The existing codebase from Phase 8 provides solid data model foundations -- `CommandHierarchy`, `SubFleet`, `CommandRange`, and `TacticalCommand` sealed class are all in place. The primary work is adding **behavioral logic** to these data structures.

The key technical challenges are: (1) changing `SubFleet.unitFleetIds` from fleet-level IDs to individual ShipUnit IDs per decision D-01, (2) integrating CRC distance checks into the tick loop's command propagation path, (3) implementing priority-based commander selection with online/rank/evaluation/merit ordering, and (4) adding communication jamming as a state modifier that blocks fleet-wide order propagation.

**Primary recommendation:** Build incrementally on the existing Phase 8 data models. Add a `CommandHierarchyService` (pure logic, no Spring DI) that handles sub-fleet assignment, CRC validation, priority calculation, and jamming checks. Integrate into `TacticalBattleEngine.processTick()` as a new step between command buffer drain and movement processing.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** 개별 ShipUnit 단위로 분함대에 배정한다. 현재 SubFleet.unitFleetIds(List<Long>)를 ShipUnit ID 기반으로 변경해야 한다.
- **D-02:** 분함대 크기는 무제한이다. 60유닛 범위 내에서 사령관이 자유롭게 분배한다 (최소 1유닛).
- **D-03:** 분함대 배정은 전투 전 + 전투 중 모두 가능하다. 전투 중 재배정은 CMD-05 조건(서클 밖 + 정지 유닛)을 충족해야 한다.
- **D-04:** 분함대장은 gin7 10명 슬롯 규칙(CrewSlotRole)에 따라 함대 슬롯에 배정된 장교만 가능하다. UnitCrew 엔티티 기반.
- **D-05:** CRC 반경은 지휘관의 command(지휘) 스탯에 비례한다. command가 높을수록 maxRange와 expansionRate가 증가한다. 현재 CommandRange 구조(currentRange/maxRange/expansionRate) 유지.
- **D-06:** CRC 밖 유닛은 복합 행동한다: 기본은 마지막 명령 유지, HP<30% 위험 상황에서는 AI 자율 퇴각.
- **D-07:** 명령 발령 시 CRC가 0으로 리셋되고 tick마다 확장된다. 명령 빈도와 CRC 크기 간 트레이드오프.
- **D-08:** CRC 내/외는 이진 판정(단순 거리 비교)으로 처리한다. 버퍼 존 없음.
- **D-09:** 우선순위: 온라인 → 계급 → 평가 → 공적. 모든 기준이 동점이면 officerId 오름차순(더 오래된 장교 우선).
- **D-10:** 우선순위 재계산은 이벤트 기반이다. 장교 온라인/오프라인 변경, 부상, 사망 시에만 재계산한다.
- **D-11:** 온라인 플레이어가 NPC/오프라인 플레이어보다 계급 무관하게 우선 배정된다 -- gin7 조직 시뮬레이션 철학.
- **D-12:** 통신 방해는 적 장교의 특수 능력 또는 특수장비로 트리거된다 (gin7 기준).
- **D-13:** 통신 방해 시 총사령관의 전군 명령만 차단된다. 분함대장->자기 유닛 명령은 정상 동작한다 (CMD-06 충족).
- **D-14:** 통신 방해 해제 조건: 일정 tick 후 자동 해제, 또는 방해 발동자 격침/퇴각 시 즉시 해제.

### Claude's Discretion
- SubFleet.unitFleetIds -> ShipUnit ID 기반으로의 구체적 리팩토링 방식
- CRC 반경의 정확한 수식 (command 스탯 -> maxRange/expansionRate 변환 공식)
- HP<30% AI 자율 퇴각의 구체적 퇴각 방향/속도 결정
- 통신 방해 지속 tick 수, 특수 능력/장비 연동 구체적 메카닉
- 테스트 전략 및 구현 순서

### Deferred Ideas (OUT OF SCOPE)
None -- discussion stayed within phase scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| CMD-01 | 사령관이 함대 내 60유닛을 부사령관/참모에게 분함대로 배정할 수 있다 | SubFleet refactor (D-01/D-02), UnitCrew validation (D-04), WebSocket endpoint for assignment |
| CMD-02 | 지휘권 우선순위(온라인->계급->평가->공적)가 자동 적용된다 | Priority comparator (D-09/D-10/D-11), Officer entity fields (officerLevel, evaluationPoints, meritPoints) |
| CMD-03 | 커맨드레인지서클 내 유닛에만 명령이 전달된다 | CRC check in tick loop (D-05/D-07/D-08), CommandRange.isInRange() already exists |
| CMD-04 | 서클 밖 유닛은 마지막 명령을 유지하거나 AI 자율 행동한다 | Out-of-CRC behavior (D-06), lastCommand field on TacticalUnit, HP<30% retreat AI |
| CMD-05 | 사령관이 실시간으로 유닛을 재배정할 수 있다 (서클 밖 + 정지 조건) | Mid-battle reassignment (D-03), isStopped check on TacticalUnit, WebSocket command |
| CMD-06 | 통신 방해 시 총사령관의 전군 명령이 불가능하다 | commJammed flag (D-12/D-13/D-14), jamming trigger/clear logic |
</phase_requirements>

## Architecture Patterns

### Recommended Project Structure
```
backend/game-app/src/main/kotlin/com/openlogh/
├── engine/tactical/
│   ├── CommandHierarchy.kt          # (existing) data model - MODIFY SubFleet
│   ├── CommandHierarchyService.kt   # NEW: pure logic for hierarchy operations
│   ├── CrcValidator.kt             # NEW: CRC distance checks
│   ├── CommandPriorityComparator.kt # NEW: online>rank>eval>merit comparator
│   ├── TacticalBattleEngine.kt     # MODIFY: integrate CRC gate in tick loop
│   └── TacticalCommand.kt          # MODIFY: add SubFleetAssign command
├── controller/
│   └── BattleWebSocketController.kt # MODIFY: add sub-fleet assignment endpoint
├── service/
│   └── TacticalBattleService.kt     # MODIFY: buildCommandHierarchy with priority
└── model/
    └── CommandRange.kt              # (existing) - no changes needed
```

### Pattern 1: CRC Gate in Tick Loop
**What:** Before applying a command to a unit, check if the issuing commander's CRC covers that unit's position.
**When to use:** Every command propagation during `drainCommandBuffer()`.
**Example:**
```kotlin
// In TacticalBattleEngine.drainCommandBuffer() or applyCommand()
private fun applyCommand(cmd: TacticalCommand, state: TacticalBattleState) {
    val unit = state.units.find { it.officerId == cmd.officerId && it.isAlive } ?: return
    
    // CRC gate: check if command issuer has authority over this unit
    val hierarchy = getHierarchyForUnit(unit, state)
    if (hierarchy != null && !isCommandReachable(cmd, unit, hierarchy, state)) {
        // Command blocked by CRC - unit maintains last order
        return
    }
    
    when (cmd) { /* existing exhaustive when */ }
}
```

### Pattern 2: Commander-to-SubFleet Command Propagation
**What:** Fleet commander issues an order -> only units within their CRC and assigned sub-fleet receive it. Sub-fleet commander issues an order -> only their assigned units within their own CRC receive it.
**When to use:** For all fleet-wide commands (formation change, energy, attack target).
**Key distinction:** Direct unit commands (officer controls own unit) skip CRC check. Fleet-wide commands require CRC validation.

### Pattern 3: Event-Driven Priority Recalculation
**What:** Priority comparator recalculates only on specific events (officer online/offline, injury, death), not every tick.
**When to use:** When any officer status changes during battle.
**Example:**
```kotlin
data class CommandPriority(
    val isOnline: Boolean,
    val rank: Int,          // Officer.officerLevel
    val evaluation: Int,    // Officer.evaluationPoints
    val merit: Int,         // Officer.meritPoints
    val officerId: Long,    // tiebreaker: lower = older = higher priority
) : Comparable<CommandPriority> {
    override fun compareTo(other: CommandPriority): Int {
        // Online first (true > false)
        if (isOnline != other.isOnline) return if (isOnline) -1 else 1
        // Higher rank first
        if (rank != other.rank) return other.rank - rank
        // Higher evaluation first
        if (evaluation != other.evaluation) return other.evaluation - evaluation
        // Higher merit first
        if (merit != other.merit) return other.merit - merit
        // Lower officerId first (older officer)
        return officerId.compareTo(other.officerId)
    }
}
```

### Pattern 4: Communication Jamming as State Modifier
**What:** `commJammed` flag on CommandHierarchy blocks fleet commander's fleet-wide orders, but sub-fleet commanders can still command their own units.
**When to use:** When enemy special ability/equipment triggers jamming.
**Implementation:** Add `jammingTicksRemaining: Int` and `jammingSourceOfficerId: Long?` fields to CommandHierarchy.

### Anti-Patterns to Avoid
- **CRC check on every unit every tick:** Only check CRC when a command is being propagated, not as part of the general tick loop. CRC expansion happens every tick (already implemented), but validation is only needed at command time.
- **DB access in CRC validation:** CRC checks must be pure in-memory operations on TacticalBattleState. No Spring DI, no repository calls.
- **Recalculating priority every tick:** Priority only changes on discrete events. Caching the sorted priority list and invalidating on events is the correct approach.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Distance calculation | Custom distance formula | Reuse existing `distance()` in TacticalBattleEngine | Already tested, consistent with weapon range checks |
| Command range expansion | New expansion system | Existing CommandRange.tick() / resetOnCommand() | Phase 8 already built this correctly |
| Concurrent command access | Custom locking | ConcurrentLinkedQueue command buffer pattern | Phase 8 established this pattern, proven thread-safe |
| Officer rank lookup | DB query per check | Cache officerLevel in TacticalUnit or hierarchy | Avoid DB access during tick processing |

**Key insight:** Phase 8 established the data model and concurrency patterns. Phase 9 adds behavioral logic on top without changing the concurrency model.

## Existing Code Analysis

### SubFleet.unitFleetIds Refactor (D-01)
**Current state:** `SubFleet.unitFleetIds: List<Long>` -- semantically "fleet IDs" but needs to become ShipUnit IDs.
**Impact:** The field name `unitFleetIds` is already somewhat ambiguous. Rename to `unitIds: List<Long>` where each Long is a TacticalUnit's identifier (which maps to fleet IDs in the current system, since each fleet = 1 unit in tactical battle).
**Key insight:** In the current TacticalBattleEngine, `TacticalUnit.fleetId` IS the unit identifier. So `SubFleet.unitFleetIds` already contains the correct IDs -- the rename is for clarity, and the real change is adding **assignment logic** (who can assign, when, validation).

### Commander CRC Initialization (D-05)
**Current state:** `CommandRange.create(commandStat, flagshipMaxRange, isSolo)` exists. `expansionRate = commandStat / 100.0`.
**Recommended formula for CRC:**
- `maxRange = 50.0 + (command * 3.0)` -- command=50 gives 200 range, command=100 gives 350 range (map is 1000x600)
- `expansionRate = 0.5 + (command / 100.0)` -- command=50 gives 1.0/tick, command=100 gives 1.5/tick
- At command=50: takes ~200 ticks to reach max range after reset. At command=100: takes ~233 ticks.
- Trade-off: high command stat = larger circle but slightly slower proportional fill. Frequent commands = smaller effective circle.

### Online Status Tracking
**Current state:** `Officer` entity has `userId: Long?` (nullable -- null = NPC). No explicit `isOnline` field.
**Required:** Need to determine online status at runtime. Options:
1. Check if officer's userId has an active WebSocket session (from Spring WebSocket session registry)
2. Add a `connectedOfficerIds: MutableSet<Long>` to TacticalBattleState
**Recommendation:** Option 2 -- maintain `connectedPlayerOfficerIds` in TacticalBattleState, updated when WebSocket connects/disconnects. Pure in-memory, no DB access. Officers with `userId == null` are always NPC (offline).

### TacticalUnit Missing Fields
Fields needed for Phase 9 that don't exist yet on TacticalUnit:
- `subFleetCommanderId: Long?` -- which sub-fleet this unit belongs to (null = fleet commander's direct units)
- `lastCommandTick: Int` -- tick when last command was received (for "maintain last order" behavior)

### TacticalCommand Extensions Needed
New command subtypes for Phase 9:
```kotlin
/** Sub-fleet assignment: assign units to a sub-fleet commander */
data class AssignSubFleet(
    override val battleId: Long,
    override val officerId: Long,  // the fleet commander issuing the assignment
    val subCommanderId: Long,      // officer to command the sub-fleet
    val unitIds: List<Long>,       // TacticalUnit.fleetId values to assign
) : TacticalCommand()

/** Reassign units mid-battle (CMD-05: CRC-outside + stopped condition) */
data class ReassignUnit(
    override val battleId: Long,
    override val officerId: Long,
    val unitId: Long,
    val newSubCommanderId: Long?,  // null = return to fleet commander direct
) : TacticalCommand()
```

## Common Pitfalls

### Pitfall 1: CRC Check on Self-Commands
**What goes wrong:** Player commanding their own unit gets blocked by CRC because their own CRC hasn't expanded yet.
**Why it happens:** After issuing a command, CRC resets to 0. If the next command comes before CRC expands, it would be blocked.
**How to avoid:** Direct self-commands (officer commanding their own TacticalUnit where `cmd.officerId == unit.officerId`) ALWAYS bypass CRC. CRC only gates commands from a commander to subordinate units.
**Warning signs:** Player unable to control their own unit after issuing a command.

### Pitfall 2: Stale Priority After Disconnection
**What goes wrong:** Player disconnects but priority list still shows them as online, giving them command slots over NPC officers with higher rank.
**Why it happens:** WebSocket disconnect events not properly propagated to TacticalBattleState.
**How to avoid:** Use Spring WebSocket `SessionDisconnectEvent` listener to remove officerId from `connectedPlayerOfficerIds` and trigger priority recalculation.
**Warning signs:** Disconnected player's units not responding to NPC AI behavior.

### Pitfall 3: Mid-Battle Reassignment Race Condition
**What goes wrong:** Two fleet commanders try to assign the same unit to different sub-fleets simultaneously.
**Why it happens:** Command buffer processes both assignments in the same tick drain.
**How to avoid:** Process AssignSubFleet commands sequentially within the drain. Each assignment validates current state -- if unit is already assigned to a different sub-fleet, reject the second assignment.
**Warning signs:** Unit appearing in multiple sub-fleets simultaneously.

### Pitfall 4: Jamming Persists After Source Destroyed
**What goes wrong:** Communication jamming continues even after the jammer unit is destroyed/retreated.
**Why it happens:** No link between jamming state and jammer unit lifecycle.
**How to avoid:** Store `jammingSourceOfficerId` on the hierarchy. In the tick loop's destroy/retreat processing, check if any destroyed/retreated unit was a jamming source and clear the flag.
**Warning signs:** Jamming lasting forever in battles where the jammer was destroyed early.

### Pitfall 5: CRC Outside Units Stuck Forever
**What goes wrong:** Units outside CRC maintain last order indefinitely, even when it no longer makes sense (e.g., moving toward a target that was destroyed).
**Why it happens:** "Maintain last order" has no expiration or validity check.
**How to avoid:** Implement a fallback: if the last order's target is destroyed/retreated, or if the unit has been outside CRC for more than N ticks, switch to AI autonomous behavior (move toward commander position).
**Warning signs:** Units flying off the map edge or orbiting destroyed positions.

## Code Examples

### CRC Distance Check (pure function)
```kotlin
// In CrcValidator.kt
object CrcValidator {
    /**
     * Check if a target unit is within the commander's current CRC.
     * D-08: binary in/out check, no buffer zone.
     */
    fun isWithinCrc(
        commander: TacticalUnit,
        target: TacticalUnit,
    ): Boolean {
        if (!commander.commandRange.hasCommandRange) return false
        val dx = commander.posX - target.posX
        val dy = commander.posY - target.posY
        val distance = sqrt(dx * dx + dy * dy)
        return commander.commandRange.isInRange(distance)
    }
}
```

### Sub-Fleet Assignment Validation
```kotlin
// In CommandHierarchyService.kt
fun validateSubFleetAssignment(
    hierarchy: CommandHierarchy,
    commanderId: Long,       // must be fleet commander
    subCommanderId: Long,    // must be in crew slots
    unitIds: List<Long>,
    allUnits: List<TacticalUnit>,
    crewOfficerIds: Set<Long>,  // officers in UnitCrew for this fleet
): String? {
    if (commanderId != hierarchy.fleetCommander) return "분함대 배정은 사령관만 가능합니다"
    if (subCommanderId !in crewOfficerIds) return "분함대장은 함대 슬롯 장교만 가능합니다"
    if (unitIds.isEmpty()) return "최소 1유닛을 배정해야 합니다"
    val totalAssigned = hierarchy.subCommanders.values.sumOf { it.unitFleetIds.size }
    if (totalAssigned + unitIds.size > 60) return "함대 최대 60유닛 초과"
    return null  // valid
}
```

### Out-of-CRC AI Retreat Behavior (D-06)
```kotlin
// Applied during tick processing for units outside CRC
fun processOutOfCrcBehavior(unit: TacticalUnit, state: TacticalBattleState) {
    val hpRatio = unit.hp.toDouble() / unit.maxHp.coerceAtLeast(1)
    if (hpRatio < 0.3 && !unit.isRetreating) {
        // AI autonomous retreat: move toward own side's edge
        val retreatX = if (unit.side == BattleSide.ATTACKER) 0.0 else state.battleBoundsX
        val dx = retreatX - unit.posX
        val dy = 0.0
        val norm = abs(dx).coerceAtLeast(1.0)
        unit.velX = (dx / norm) * TacticalBattleEngine.BASE_SPEED * 0.8
        unit.velY = 0.0
    }
    // else: maintain last order (velocity unchanged)
}
```

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 (Jupiter) |
| Config file | `backend/game-app/build.gradle.kts` (test task config) |
| Quick run command | `cd /Users/apple/Desktop/개인프로젝트/openlogh/backend && ./gradlew :game-app:test --tests "com.openlogh.engine.tactical.*" -x bootJar` |
| Full suite command | `cd /Users/apple/Desktop/개인프로젝트/openlogh/backend && ./gradlew :game-app:test -x bootJar` |

### Phase Requirements -> Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| CMD-01 | Sub-fleet assignment with unit IDs | unit | `./gradlew :game-app:test --tests "*.CommandHierarchyServiceTest.assignSubFleet*" -x bootJar` | Wave 0 |
| CMD-02 | Priority ordering (online>rank>eval>merit) | unit | `./gradlew :game-app:test --tests "*.CommandPriorityTest.*" -x bootJar` | Wave 0 |
| CMD-03 | CRC blocks commands to out-of-range units | unit | `./gradlew :game-app:test --tests "*.CrcValidatorTest.*" -x bootJar` | Wave 0 |
| CMD-04 | Out-of-CRC units maintain last order / AI retreat | unit | `./gradlew :game-app:test --tests "*.OutOfCrcBehaviorTest.*" -x bootJar` | Wave 0 |
| CMD-05 | Mid-battle reassignment (CRC-outside + stopped) | unit | `./gradlew :game-app:test --tests "*.CommandHierarchyServiceTest.reassign*" -x bootJar` | Wave 0 |
| CMD-06 | Jamming blocks fleet-wide orders only | unit | `./gradlew :game-app:test --tests "*.CommunicationJammingTest.*" -x bootJar` | Wave 0 |

### Sampling Rate
- **Per task commit:** `./gradlew :game-app:test --tests "com.openlogh.engine.tactical.*" -x bootJar`
- **Per wave merge:** `./gradlew :game-app:test -x bootJar`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `CommandHierarchyServiceTest.kt` -- covers CMD-01, CMD-05
- [ ] `CommandPriorityTest.kt` -- covers CMD-02
- [ ] `CrcValidatorTest.kt` -- covers CMD-03
- [ ] `OutOfCrcBehaviorTest.kt` -- covers CMD-04
- [ ] `CommunicationJammingTest.kt` -- covers CMD-06

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| SubFleet with fleet-level IDs | SubFleet with ShipUnit IDs | Phase 9 (D-01) | Field rename + assignment logic |
| No CRC enforcement | Binary CRC gate on command propagation | Phase 9 | Commands only reach units in circle |
| No priority ordering | Event-driven priority with 4 criteria | Phase 9 | Commander slots auto-assigned |
| No jamming system | commJammed flag + source tracking | Phase 9 | Fleet-wide orders blockable |

## Open Questions

1. **Online status detection mechanism**
   - What we know: Officer.userId != null means player-controlled. Need runtime online check.
   - What's unclear: Whether Spring WebSocket session registry is reliable enough, or if a dedicated heartbeat is needed.
   - Recommendation: Start with `connectedPlayerOfficerIds: MutableSet<Long>` on TacticalBattleState, populated by WebSocket connect/disconnect events. Simple and testable.

2. **CRC formula tuning**
   - What we know: command stat ranges 1-100, map is 1000x600, existing BEAM_RANGE is 200.
   - What's unclear: Exact feel of different command stat values in gameplay.
   - Recommendation: Use `maxRange = 50.0 + (command * 3.0)`, `expansionRate = 0.5 + (command / 100.0)`. Expose as constants for easy tuning.

3. **Jamming duration**
   - What we know: D-14 says "일정 tick 후 자동 해제" but no specific number.
   - What's unclear: How many ticks is balanced.
   - Recommendation: Default to 60 ticks (1 minute at 1 tick/sec). Define as constant `JAMMING_DEFAULT_DURATION_TICKS`.

## Sources

### Primary (HIGH confidence)
- `CommandHierarchy.kt` -- Phase 8 data model, verified current state
- `TacticalBattleEngine.kt` -- tick loop, command buffer drain, CRC expansion logic
- `CommandRange.kt` -- CRC model with tick/reset/isInRange methods
- `TacticalCommand.kt` -- sealed class with 7 command subtypes
- `BattleWebSocketController.kt` -- existing WebSocket command endpoints
- `UnitCrew.kt` + `CrewSlotRole.kt` -- crew slot system for sub-fleet leader validation
- `Officer.kt` -- officerLevel (rank), evaluationPoints, meritPoints, userId fields confirmed

### Secondary (MEDIUM confidence)
- `docs/REWRITE_PROMPT.md` -- gin7 CRC rules, tactical mode details
- `docs/reference/unit_composition.md` -- fleet organization (10 crew slots, 60 units max)
- `09-CONTEXT.md` -- all 14 locked decisions verified against codebase

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH -- all code is Kotlin/Spring Boot, no new dependencies needed
- Architecture: HIGH -- extending existing proven patterns from Phase 8
- Pitfalls: HIGH -- identified from codebase analysis (race conditions, lifecycle gaps)

**Research date:** 2026-04-07
**Valid until:** 2026-05-07 (stable domain, internal game logic)
