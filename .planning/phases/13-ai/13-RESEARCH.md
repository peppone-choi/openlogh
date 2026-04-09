# Phase 13: 전략 AI - Research

**Researched:** 2026-04-09
**Domain:** Strategic AI for faction-level operation planning (Kotlin / Spring Boot)
**Confidence:** HIGH

## Summary

Phase 13 replaces the legacy `atWar` branch in `FactionAI.decideNationAction()` with an OperationPlan-based strategic AI. The existing infrastructure is well-established: Phase 12 provides `OperationPlanService.assignOperation()` as the transactional API, `OperationPlanCommand` is registered as an officer command ("작전계획"), and `FactionAIScheduler` already processes one faction per tick in round-robin. The AI must evaluate star system power, select operation targets, choose operation types (CONQUEST/DEFENSE/SWEEP), and allocate fleets.

The critical architectural insight is that `OperationPlanCommand` is an **officer command** (not a faction command), so execution must go through `commandExecutor.executeOfficerCommand()` with the sovereign officer as the acting general. FactionAI currently returns a command name string but the scheduler only logs it -- Phase 13 must wire actual command execution for operation planning, either by injecting CommandExecutor into FactionAI or by having the scheduler execute the returned command.

**Primary recommendation:** Create a `StrategicOperationPlanner` pure-logic class following the `UtilityScorer` object pattern for all scoring/evaluation, and modify `FactionAI.decideNationAction()` atWar branch to call through CommandExecutor via the existing AiCommandBridge pattern.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** 성계별 복합 스코어링 -- 함선 수(ships 합산), 사령관 능력(command/leadership), 궤도방어/요새 방어력을 다차원 점수화. 구체적 가중치는 Claude 재량.
- **D-02:** 안개 제한 적용 -- 해당 성계에 첩보원(intelligence 높은 장교)이 실제 체류 중이어야 적 전력을 정확하게 평가. 첩보원이 없으면 노이즈가 추가된 추정치 사용.
- **D-03:** 적 정보 접근은 전술전 안개(Phase 14 FE-05)와 별개 -- 전략 AI 안개는 성계 단위, 전술전 안개는 유닛 단위.
- **D-04:** 복합 기준(전선 + 전략적 가치) -- CONQUEST: 적 전선 성계 중 전력 약하고 전략적 가치(자원량/연결성) 높은 곳. DEFENSE: 아군 전선 성계 중 위협받는 곳. SWEEP: 아군 영역 내 적 함대 존재 성계.
- **D-05:** 동시 작전 수 무제한 -- Phase 12 D-02와 일관. AI도 필요한 만큼 작전 수립.
- **D-06:** CommandExecutor를 통해 OperationPlanCommand 실행 -- 기존 커맨드 파이프라인(CP 소모, 권한 검증, 로그 발행) 일관 유지.
- **D-07:** AI 발령자는 진영 원수/의장(sovereign) -- commander 그룹 권한을 자동 보유하므로 별도 권한 우회 불필요.
- **D-08:** atWar 분기의 삼국지 잔재(급습/의병모집/필사즉생, strategicCmdLimit) 완전 제거 -> 작전계획 수립 로직으로 교체.
- **D-09:** 전력 기반 최적 배정 -- 목표 성계의 적 전력보다 충분한 전력이 되도록 최소 함대 조합 계산. 나머지는 방어 예비.
- **D-10:** 성격(PersonalityTrait) 기반 작전 유형 경향 -- AGGRESSIVE 진영 원수는 CONQUEST 우선, DEFENSIVE는 DEFENSE 우선, CAUTIOUS는 보수적 투입 등.

### Claude's Discretion
- 복합 스코어링의 구체적 가중치 및 공식
- 첩보원 판정 기준 (intelligence 임계값)
- 노이즈 추정치의 오차 범위
- 전략적 가치 평가 세부 요소 (자원량/항로 수/인구 등)
- 작전 수립 주기 (매 tick? 특정 인터벌?)
- 최소 함대 조합 알고리즘 (greedy? knapsack?)
- 기존 FactionAI의 비전쟁 로직(발령/증축/포상/불가침) 유지 범위

### Deferred Ideas (OUT OF SCOPE)
- 연쇄 작전(CASCADE): Phase 12에서도 deferred -- 작전 완료 시 다음 작전 자동 발령
- 작전 우선순위: 다중 작전 시 자원/CP 우선순위
- 전략 AI 학습: ML 기반 전술 판단 (Out of Scope -- REQUIREMENTS.md)
- 외교 AI 고도화: 동맹/불가침 전략적 제안 (현재는 단순 확률 기반)
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| SAI-01 | 전술전 진입 시 AI가 작전계획을 자동 수립한다 | FactionAI.decideNationAction() atWar branch replacement; CommandExecutor.executeOfficerCommand() with sovereign officer; OperationPlanService.assignOperation() API |
| SAI-02 | 전력 평가 기반으로 작전 유형(점령/방어/소탕)을 선택한다 | StrategicPowerScorer multi-dimensional scoring; PersonalityTrait-based type bias; fog-of-war noise system for intelligence-gated accuracy |
</phase_requirements>

## Architecture Patterns

### Recommended Project Structure
```
engine/ai/
├── FactionAI.kt                    # MODIFY: atWar branch -> StrategicOperationPlanner 호출
├── FactionAIScheduler.kt           # MODIFY: action 실행 연결 (CommandExecutor 주입)
├── FactionAIPort.kt                # 유지 (인터페이스 변경 없음)
├── PersonalityTrait.kt             # 유지 (기존 5종 성격 활용)
├── UtilityScorer.kt                # 참조 패턴 (pure object scoring)
├── strategic/                      # NEW: 전략 AI 전용 패키지
│   ├── StrategicPowerScorer.kt     # 성계별 복합 전력 평가 (pure object)
│   ├── OperationTargetSelector.kt  # 작전 대상 성계 선정 (pure object)
│   ├── FleetAllocator.kt           # 전력 기반 최소 함대 배정 (pure object)
│   └── FogOfWarEstimator.kt        # 안개 제한 노이즈 적용 (pure object)
└── AiCommandBridge.kt              # 참조 패턴 (CommandExecutor 실행)
```

### Pattern 1: Pure Object Scorer (established)
**What:** Stateless scoring/evaluation logic as Kotlin `object` without Spring DI
**When to use:** All tactical/strategic AI computation classes
**Why:** Established by UtilityScorer, CommandHierarchyService, SuccessionService. Enables unit testing without Spring context.
**Example:**
```kotlin
// Source: UtilityScorer.kt (existing pattern)
object StrategicPowerScorer {
    data class StarSystemPower(
        val totalShips: Int,
        val commanderScore: Double,
        val defenseScore: Double,
        val compositeScore: Double,
    )
    
    fun evaluatePower(
        fleets: List<Fleet>,
        officers: List<Officer>,
        planet: Planet,
    ): StarSystemPower {
        val totalShips = officers.sumOf { it.ships }
        val commanderScore = officers.sumOf { 
            (it.command + it.leadership).toDouble() / 2.0 
        }
        val defenseScore = (planet.orbitalDefense + planet.fortress).toDouble()
        val compositeScore = totalShips * 0.5 + commanderScore * 0.3 + defenseScore * 0.2
        return StarSystemPower(totalShips, commanderScore, defenseScore, compositeScore)
    }
}
```

### Pattern 2: FactionAI Command Execution via CommandExecutor
**What:** FactionAI needs to execute OperationPlanCommand through CommandExecutor, not just return a string
**When to use:** When the atWar branch decides to create an operation plan
**Critical insight:** `OperationPlanCommand` is registered as officer command ("작전계획") in `Gin7CommandRegistry`. FactionAI must:
1. Find the sovereign officer (`faction.chiefOfficerId`)
2. Build `CommandEnv` from `SessionState`
3. Call `commandExecutor.executeOfficerCommand("작전계획", sovereign, env, arg)` with arg containing: objective, targetStarSystemId, participantFleetIds, scale
4. This requires injecting CommandExecutor into FactionAI (currently only has JpaWorldPortFactory)

**Example:**
```kotlin
// FactionAI must construct these args for OperationPlanCommand
val operationArg = mapOf<String, Any>(
    "objective" to "CONQUEST",
    "targetStarSystemId" to targetPlanet.id,
    "participantFleetIds" to selectedFleetIds,
    "scale" to 1,
    "planName" to "AI작전-${world.currentYear}-${world.currentMonth}",
)
// Then execute via CommandExecutor with sovereign officer
```

### Pattern 3: Fog-of-War Noise Injection
**What:** When no intelligence officer is present at a star system, add random noise to power estimates
**When to use:** Every enemy star system power evaluation
**Example:**
```kotlin
object FogOfWarEstimator {
    private const val INTELLIGENCE_THRESHOLD = 70 // Claude's discretion
    private const val NOISE_RANGE = 0.4 // +/- 40% error
    
    fun hasIntelligenceAgent(
        targetPlanetId: Long,
        friendlyOfficers: List<Officer>,
    ): Boolean {
        return friendlyOfficers.any { 
            it.planetId == targetPlanetId && it.intelligence >= INTELLIGENCE_THRESHOLD 
        }
    }
    
    fun applyFogNoise(
        truePower: Double,
        hasAgent: Boolean,
        rng: Random,
    ): Double {
        if (hasAgent) return truePower
        val noise = 1.0 + (rng.nextDouble() * 2 - 1) * NOISE_RANGE
        return (truePower * noise).coerceAtLeast(0.0)
    }
}
```

### Anti-Patterns to Avoid
- **Spring DI for scoring classes:** All previous AI classes (UtilityScorer, SuccessionService, CommandHierarchyService) are pure objects. Do NOT make scorers @Service.
- **Direct DB access in scoring:** Pass pre-loaded entity lists to scoring functions. FactionAI already loads all needed data via `worldPortFactory.create(worldId)`.
- **Bypassing CommandExecutor:** D-06 mandates going through the full command pipeline. Do NOT call `OperationPlanService.assignOperation()` directly from FactionAI.
- **mockito-kotlin:** Phase 12 decision -- NOT on classpath. Use `org.mockito.Mockito.mock(Class::class.java)` + `when(...).thenReturn(...)`.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Operation creation | Custom DB insert | `OperationPlanService.assignOperation()` via `OperationPlanCommand` | D-04 atomicity, D-06 pipeline consistency |
| Command execution | Direct service call | `CommandExecutor.executeOfficerCommand()` | CP deduction, cooldown, authority check, log dispatch |
| Personality weights | New weight system | `PersonalityWeights.forTrait()` | Already maps 5 traits to 8 stat multipliers |
| Front-line detection | Custom adjacency | `Planet.frontState > 0` | Already computed by game engine each turn |
| Fleet faction query | Custom query | `FleetRepository.findBySessionIdAndFactionId()` | Existing JPA derived query |
| Operation duplicate check | Manual scan | `OperationPlanRepository.findBySessionIdAndFactionIdAndStatusIn()` | Existing repository method |

**Key insight:** Phase 12 deliberately designed OperationPlanService.assignOperation() to be callable from FactionAI. The D-04 atomicity (1-fleet-1-operation) and fleet-reassignment logic are already handled inside the transactional service.

## Common Pitfalls

### Pitfall 1: FactionAI returns String but needs to execute commands
**What goes wrong:** FactionAI.decideNationAction() returns a command name string. FactionAIScheduler currently only logs it. For operation planning, the AI needs to actually execute `OperationPlanCommand` with complex args (fleetIds, targetId, objective).
**Why it happens:** The original design assumed faction AI would return simple command strings that the turn system would pick up. OperationPlanCommand needs structured args.
**How to avoid:** Two options: (1) Inject CommandExecutor into FactionAI and execute directly within decideNationAction(), or (2) Expand FactionAIPort return type to include args. Option 1 is simpler and follows AiCommandBridge pattern.
**Warning signs:** Tests that check `decideNationAction()` return value without verifying command execution.

### Pitfall 2: Sovereign officer might not exist or be loadable
**What goes wrong:** `faction.chiefOfficerId` might be 0 or the officer might be dead/captured.
**Why it happens:** Edge cases in game state -- sovereign can die, faction can be leaderless temporarily.
**How to avoid:** Check `chiefOfficerId > 0` and verify officer exists before attempting command execution. Fall back to highest-ranked officer, or skip operation planning for that tick.
**Warning signs:** NullPointerException or "CommandServices unavailable" from OperationPlanCommand.

### Pitfall 3: No available fleets for operation
**What goes wrong:** AI tries to create operation with empty fleetIds list, `OperationPlanService.assignOperation()` throws `IllegalArgumentException("참가 부대를 1개 이상 지정해야 합니다")`.
**Why it happens:** All faction fleets already assigned to existing operations, or faction has no fleets.
**How to avoid:** Pre-check available (unassigned) fleets before deciding to create an operation. Query existing PENDING/ACTIVE operations to find which fleets are already committed.
**Warning signs:** Repeated "작전 수립 실패" logs.

### Pitfall 4: Overcommitting all fleets to offense
**What goes wrong:** AI assigns all fleets to CONQUEST operations, leaving zero defense.
**Why it happens:** Scoring algorithm biases toward CONQUEST without a defense reserve.
**How to avoid:** D-09 mandates "나머지는 방어 예비". FleetAllocator must calculate minimum defensive coverage before offensive allocation.
**Warning signs:** Enemy captures undefended home systems immediately after AI launches attack.

### Pitfall 5: H2 test incompatibility with JSONB queries
**What goes wrong:** Tests using `OperationPlanRepository.findActiveOrPendingByFleetIdNative()` fail on H2.
**Why it happens:** H2 does not support PostgreSQL `@>` JSONB operator.
**How to avoid:** Use `findBySessionIdAndFactionIdAndStatusIn()` + Kotlin-side filtering in tests, as established in Phase 12.
**Warning signs:** SQL syntax error in H2 tests mentioning `@>` or `jsonb`.

### Pitfall 6: Concurrent operation creation race
**What goes wrong:** Two AI ticks create conflicting operations with overlapping fleets.
**Why it happens:** FactionAIScheduler processes one faction per tick (round-robin), so same-faction concurrency is prevented. But if FactionAI creates multiple operations in a single decideNationAction() call, the D-04 atomicity in assignOperation() handles fleet reassignment automatically.
**How to avoid:** Call assignOperation() sequentially for each planned operation within the same decideNationAction() call. The @Transactional boundary will handle fleet deduplication.

## Code Examples

### Existing: How FactionAI loads data (reuse this pattern)
```kotlin
// Source: FactionAI.kt:17-24
override fun decideNationAction(nation: Faction, world: SessionState, rng: Random): String {
    val worldId = world.id.toLong()
    val ports = worldPortFactory.create(worldId)
    val nationCities = ports.planetsByFaction(nation.id).map { it.toEntity() }
    val nationGenerals = ports.officersByFaction(nation.id).map { it.toEntity() }
    val diplomacies = ports.activeDiplomacies().map { it.toEntity() }
    // ... decision logic
}
```

### Existing: How AiCommandBridge executes commands (follow this pattern)
```kotlin
// Source: AiCommandBridge.kt:60-66
val result = runBlocking {
    commandExecutor.executeOfficerCommand(
        actionCode = candidate.commandName,
        general = officer,
        env = env,
    )
}
```

### Existing: OperationPlanCommand arg format (must match)
```kotlin
// Source: OperationPlanCommand.kt:48-62
val objectiveStr = arg?.get("objective") as? String           // "CONQUEST"|"DEFENSE"|"SWEEP"
val targetStarSystemId = (arg["targetStarSystemId"] as? Number)?.toLong()
val fleetIdsRaw = arg["participantFleetIds"] as? List<*>
val participantFleetIds = fleetIdsRaw.mapNotNull { (it as? Number)?.toLong() }
val scale = (arg["scale"] as? Number)?.toInt() ?: 1
val planName = arg["planName"] as? String ?: "작전계획-${env.year}-${env.month}"
```

### Existing: NationAITest mock pattern (follow for new tests)
```kotlin
// Source: NationAITest.kt:34-44
@BeforeEach
fun setUp() {
    planetRepository = mock(PlanetRepository::class.java)
    officerRepository = mock(OfficerRepository::class.java)
    factionRepository = mock(FactionRepository::class.java)
    diplomacyRepository = mock(DiplomacyRepository::class.java)
    ai = FactionAI(JpaWorldPortFactory(
        officerRepository = officerRepository,
        planetRepository = planetRepository,
        factionRepository = factionRepository,
        diplomacyRepository = diplomacyRepository,
    ))
}
```

### Existing: PersonalityTrait weights (use for operation type bias)
```kotlin
// Source: PersonalityTrait.kt:46-61
PersonalityTrait.AGGRESSIVE -> PersonalityWeights(
    attack = 1.5, command = 1.3, mobility = 1.2,
    defense = 0.7, administration = 0.8,
)
PersonalityTrait.DEFENSIVE -> PersonalityWeights(
    defense = 1.5, administration = 1.3, leadership = 1.1,
    attack = 0.7, mobility = 0.9,
)
```

## Discretion Recommendations

### Composite Scoring Weights (D-01)
**Recommendation:** Use normalized 0-100 scale per dimension, then weighted sum:
- Ships: 50% weight (primary combat power)
- Commander ability: 30% weight (average of command + leadership for all officers at system)
- Defense: 20% weight (orbitalDefense + fortress, normalized)

Formula: `compositeScore = ships_normalized * 0.5 + commander_normalized * 0.3 + defense_normalized * 0.2`
**Confidence:** MEDIUM -- weights are game-balance tunable, these are reasonable starting points.

### Intelligence Threshold (D-02)
**Recommendation:** intelligence >= 70 as "spy" threshold. The 8-stat system uses 0-100 range, with 50 as average. 70 represents a specialist-level intelligence officer.
**Confidence:** MEDIUM -- tunable parameter, 70 is a reasonable default.

### Noise Range (D-02)
**Recommendation:** +/- 40% random noise when no intelligence agent present. This is large enough to cause meaningful AI mistakes (attacking stronger positions, neglecting weak ones) but not so large as to be completely random.
**Confidence:** MEDIUM -- game-balance tunable.

### Strategic Value Factors (D-04)
**Recommendation:** For CONQUEST target scoring:
- Resource output: production + commerce (economic value)
- Trade routes: tradeRoute count (connectivity)
- Population: population (long-term value)
- Enemy weakness: inverse of enemy composite power score

For DEFENSE priority scoring:
- Enemy threat proximity: enemy composite power at adjacent systems
- Own asset value: own production + commerce at the system
- Undermanning: fewer friendly officers = higher priority

### Operation Planning Frequency
**Recommendation:** AI evaluates once per FactionAIScheduler tick when at war, which is already 1 tick per faction (round-robin). No separate interval needed -- the existing scheduler cadence is appropriate.
**Confidence:** HIGH -- leverages established scheduling pattern.

### Fleet Allocation Algorithm (D-09)
**Recommendation:** Greedy algorithm, not knapsack:
1. Calculate target enemy power (with fog noise if applicable)
2. Set required power = enemy power * 1.3 (30% superiority margin)
3. Sort available fleets by power (descending)
4. Greedily add fleets until required power threshold met
5. Remaining fleets stay as defense reserve

Greedy is simpler, faster, and sufficient -- the "optimal" knapsack solution provides marginal benefit for a game AI.
**Confidence:** HIGH -- greedy is standard for strategy game AI fleet allocation.

### Non-War Logic Preservation
**Recommendation:** Keep all existing non-war logic (발령/증축/포상/불가침/천도) completely untouched. Only the `if (atWar)` block is replaced. This minimizes risk and regression scope.
**Confidence:** HIGH -- D-08 specifies "완전 제거" only for the atWar branch contents.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 (Jupiter) |
| Config file | backend/game-app/build.gradle.kts |
| Quick run command | `cd backend && ./gradlew :game-app:test --tests "com.openlogh.engine.ai.*" -x :gateway-app:test` |
| Full suite command | `cd backend && ./gradlew :game-app:test -x :gateway-app:test` |

### Phase Requirements -> Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| SAI-01 | AI creates OperationPlan when at war | unit | `./gradlew :game-app:test --tests "com.openlogh.engine.ai.strategic.StrategicOperationPlannerTest" -x :gateway-app:test` | Wave 0 |
| SAI-01 | FactionAI atWar branch executes operation command | unit | `./gradlew :game-app:test --tests "com.openlogh.engine.ai.NationAITest" -x :gateway-app:test` | Existing (update) |
| SAI-02 | CONQUEST selected for weak enemy systems | unit | `./gradlew :game-app:test --tests "com.openlogh.engine.ai.strategic.OperationTargetSelectorTest" -x :gateway-app:test` | Wave 0 |
| SAI-02 | DEFENSE selected for threatened own systems | unit | `./gradlew :game-app:test --tests "com.openlogh.engine.ai.strategic.OperationTargetSelectorTest" -x :gateway-app:test` | Wave 0 |
| SAI-02 | SWEEP selected for enemy fleets in own territory | unit | `./gradlew :game-app:test --tests "com.openlogh.engine.ai.strategic.OperationTargetSelectorTest" -x :gateway-app:test` | Wave 0 |
| SAI-02 | Fog noise applied when no intel agent present | unit | `./gradlew :game-app:test --tests "com.openlogh.engine.ai.strategic.FogOfWarEstimatorTest" -x :gateway-app:test` | Wave 0 |
| SAI-02 | PersonalityTrait biases operation type selection | unit | `./gradlew :game-app:test --tests "com.openlogh.engine.ai.strategic.OperationTargetSelectorTest" -x :gateway-app:test` | Wave 0 |

### Sampling Rate
- **Per task commit:** `cd backend && ./gradlew :game-app:test --tests "com.openlogh.engine.ai.*" -x :gateway-app:test`
- **Per wave merge:** `cd backend && ./gradlew :game-app:test -x :gateway-app:test`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `backend/game-app/src/test/kotlin/com/openlogh/engine/ai/strategic/StrategicPowerScorerTest.kt` -- covers composite power scoring
- [ ] `backend/game-app/src/test/kotlin/com/openlogh/engine/ai/strategic/OperationTargetSelectorTest.kt` -- covers SAI-02 operation type selection
- [ ] `backend/game-app/src/test/kotlin/com/openlogh/engine/ai/strategic/FleetAllocatorTest.kt` -- covers D-09 fleet allocation
- [ ] `backend/game-app/src/test/kotlin/com/openlogh/engine/ai/strategic/FogOfWarEstimatorTest.kt` -- covers D-02 intelligence noise

## Key Integration Details

### FactionAI Constructor Change
FactionAI currently only takes `JpaWorldPortFactory`. To execute commands per D-06, it needs `CommandExecutor` injected:
```kotlin
@Service
class FactionAI(
    private val worldPortFactory: JpaWorldPortFactory,
    private val commandExecutor: CommandExecutor,  // NEW
) : FactionAIPort
```

This affects NationAITest setup -- mock CommandExecutor must be provided. Follow existing Mockito pattern: `mock(CommandExecutor::class.java)`.

### FactionAIPort Interface
Currently returns `String`. The interface can stay unchanged if FactionAI executes commands internally and still returns a descriptive action string for logging. The scheduler uses the return value only for logging (`logger.debug("Faction AI [{}] decided: {}", faction.id, action)`).

### Sovereign Officer Loading
```kotlin
val sovereignId = nation.chiefOfficerId
if (sovereignId <= 0L) return "Nation휴식" // no leader, skip
val sovereign = ports.officer(sovereignId)?.toEntity() ?: return "Nation휴식"
```

### Available Fleet Detection
```kotlin
// Load all faction fleets
val allFleets = fleetRepository.findBySessionIdAndFactionId(sessionId, nation.id)
// Load existing PENDING/ACTIVE operations to find committed fleets
val existingOps = operationPlanRepository.findBySessionIdAndFactionIdAndStatusIn(
    sessionId, nation.id, listOf(OperationStatus.PENDING, OperationStatus.ACTIVE)
)
val committedFleetIds = existingOps.flatMap { it.participantFleetIds }.toSet()
val availableFleets = allFleets.filter { it.id !in committedFleetIds }
```

Note: FactionAI currently accesses DB only through `worldPortFactory` ports. For FleetRepository and OperationPlanRepository, either: (a) inject these repositories into FactionAI, or (b) add fleet queries to WorldWritePort. Option (a) is simpler and follows the CommandExecutor pattern of direct repository injection.

### CommandEnv Construction (reuse AiCommandBridge pattern)
```kotlin
private fun buildCommandEnv(world: SessionState): CommandEnv = CommandEnv(
    year = world.currentYear.toInt(),
    month = world.currentMonth.toInt(),
    startYear = world.currentYear.toInt(),
    sessionId = world.id.toLong(),
    realtimeMode = true,
)
```

## Sources

### Primary (HIGH confidence)
- `FactionAI.kt` -- current atWar branch logic, data loading patterns, decideNationAction return contract
- `FactionAIScheduler.kt` -- round-robin scheduling, 1 tick = 1 faction
- `OperationPlanCommand.kt` -- arg format (objective, targetStarSystemId, participantFleetIds, scale, planName)
- `OperationPlanService.kt` -- assignOperation() API, D-04 atomicity, @Transactional boundary
- `CommandExecutor.kt` -- executeOfficerCommand() signature, CommandServices wiring (operationPlanService optional nullable)
- `Gin7CommandRegistry.kt:145` -- "작전계획" registered as officer command
- `AiCommandBridge.kt` -- established pattern for AI executing commands through CommandExecutor
- `PersonalityTrait.kt` -- 5 traits with stat weight multipliers
- `UtilityScorer.kt` -- pure object scoring pattern
- `NationAITest.kt` -- test mock setup pattern (Mockito without mockito-kotlin)
- `OperationPlanRepository.kt` -- available query methods

### Secondary (MEDIUM confidence)
- Discretion recommendations (scoring weights, thresholds) -- based on game design analysis and domain patterns, not official docs

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH -- all libraries already in project, no new dependencies
- Architecture: HIGH -- follows established patterns (pure object scoring, CommandExecutor pipeline, FactionAIScheduler scheduling)
- Pitfalls: HIGH -- identified from actual code analysis of integration points
- Discretion items: MEDIUM -- game-balance parameters are inherently tunable

**Research date:** 2026-04-09
**Valid until:** 2026-05-09 (stable -- no external dependency changes)
