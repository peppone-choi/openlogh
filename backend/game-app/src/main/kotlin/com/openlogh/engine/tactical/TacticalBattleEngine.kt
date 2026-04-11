package com.openlogh.engine.tactical

import com.openlogh.engine.ai.PersonalityTrait
import com.openlogh.engine.tactical.ai.MissionObjective
import com.openlogh.engine.tactical.ai.TacticalAIRunner
import com.openlogh.model.CommandRange
import com.openlogh.model.DetectionCapability
import com.openlogh.model.EnergyAllocation
import com.openlogh.model.Formation
import com.openlogh.model.InjuryEvent
import com.openlogh.model.ShipSubtype
import com.openlogh.model.TacticalWeaponType
import com.openlogh.model.UnitStance
import com.openlogh.service.ShipStatRegistry
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.*
import kotlin.random.Random

/**
 * Runtime unit state within a tactical battle tick loop.
 */
data class TacticalUnit(
    val fleetId: Long,
    val officerId: Long,
    val officerName: String,
    val factionId: Long,
    val side: BattleSide,

    // Position on 2D tactical grid (0..1000)
    var posX: Double = 0.0,
    var posY: Double = 0.0,

    // Velocity (units per tick)
    var velX: Double = 0.0,
    var velY: Double = 0.0,

    // Combat stats
    var hp: Int = 0,
    var maxHp: Int = 0,
    var ships: Int = 0,
    var maxShips: Int = 0,
    var training: Int = 50,
    var morale: Int = 50,

    // Officer stats (8-stat system)
    var leadership: Int = 50,
    var command: Int = 50,
    var intelligence: Int = 50,
    var mobility: Int = 50,
    var attack: Int = 50,
    var defense: Int = 50,

    // Energy allocation
    var energy: EnergyAllocation = EnergyAllocation.BALANCED,

    // Formation
    var formation: Formation = Formation.MIXED,

    // Command range: expands based on command stat, resets on new order
    var commandRange: CommandRange = CommandRange(),

    // Phase 14 FE-05 / D-19 fog-of-war visibility radius.
    // Derived from energy.sensor each tick via SensorRangeFormula; cached on
    // the unit so the DTO builder and downstream services can read it without
    // re-deriving the formula. Default 150.0 matches BASE_SENSOR_RANGE.
    var sensorRange: Double = 150.0,

    // Status
    var isAlive: Boolean = true,
    var isRetreating: Boolean = false,
    var retreatProgress: Double = 0.0,

    // Unit type info
    var unitType: String = "FLEET",
    var shipClass: Int = 0,

    // gin7 확장 필드 (Plan 03-01)
    /** 현재 태세 (NAVIGATION/ANCHORING/STATIONED/COMBAT) */
    var stance: UnitStance = UnitStance.NAVIGATION,
    /** 미사일 잔탄 수 */
    var missileCount: Int = 100,
    /** 함선 서브타입 (battleship/cruiser/destroyer 등 세부 구분) */
    var shipSubtype: ShipSubtype? = null,
    /** 기함 여부 */
    var isFlagship: Boolean = false,
    /** 탑재 지상부대 수 */
    var groundUnitsEmbark: Int = 0,
    /** 스파르타니안 속도 디버프 잔여 틱 */
    var fighterSpeedDebuffTicks: Int = 0,
    /** 태세 변경 쿨다운 잔여 틱 (0 이하이면 변경 가능) */
    var stanceChangeTicksRemaining: Int = 0,
    /**
     * Phase 24-25 (gap C9, gin7 매뉴얼 p52):
     * REVERSE(反転) 커맨드는 명령 수신 후 10초 대기 뒤 실제 선회가 발생한다.
     * 값이 0 보다 크면 선회 준비 중 — 매 틱 -1, 0 이 되는 순간 velocity 를 반전한다.
     */
    var reverseChargeTicksRemaining: Int = 0,
    /** 플레이어 지정 공격 대상 함대 ID (null=자동 선택) */
    var targetFleetId: Long? = null,
    /** 선회 중 여부 (ORBIT 커맨드) */
    var isOrbiting: Boolean = false,

    // ── Phase 9: Command hierarchy fields ──
    /** 이 유닛이 소속된 분함대장의 officerId (null = 사령관 직할) */
    var subFleetCommanderId: Long? = null,
    /** 마지막 명령을 수신한 tick 번호 (CRC 밖 자율행동 판단용) */
    var lastCommandTick: Int = 0,
    /** Officer rank level (populated at battle init from Officer.officerLevel, for priority ordering) */
    val officerLevel: Int = 0,
    /** Evaluation points (populated at battle init from Officer.evaluationPoints, for priority ordering) */
    val evaluationPoints: Int = 0,
    /** Merit points (populated at battle init from Officer.meritPoints, for priority ordering) */
    val meritPoints: Int = 0,

    // ── Phase 11: Tactical AI fields ──
    /** Officer personality trait for AI decisions */
    var personality: PersonalityTrait = PersonalityTrait.BALANCED,
    /** Current mission objective (stub for Phase 12 connection) */
    var missionObjective: MissionObjective = MissionObjective.DEFENSE,
    /** Anchor position X for DEFENSE mission (initial position at battle start) */
    var anchorX: Double = 0.0,
    /** Anchor position Y for DEFENSE mission */
    var anchorY: Double = 0.0,
    /** Last AI evaluation tick (for 10-tick re-evaluation cycle per D-06) */
    var lastAIEvalTick: Int = -10,

    // ── Merged from TacticalCombatEngine ──
    /** 보급 물자 */
    var supplies: Int = 0,
    /** Weapon cooldowns: weaponType -> ticks remaining */
    var weaponCooldowns: MutableMap<TacticalWeaponType, Int> = mutableMapOf(),
    /** Active debuffs: type -> ticks remaining */
    var debuffs: MutableMap<String, Int> = mutableMapOf(),
    /** Detection capability of this unit */
    var detectionCapability: DetectionCapability = DetectionCapability(
        baseRange = 5.0, basePrecision = 0.5, evasionRating = 0.2,
    ),
) {
    /** Unit is stopped (zero velocity) */
    val isStopped: Boolean get() = velX == 0.0 && velY == 0.0
}

enum class BattleSide { ATTACKER, DEFENDER }

data class BattleOutcome(
    val winner: BattleSide?,
    val reason: String,
)

/**
 * Full state of an active tactical battle.
 */
data class TacticalBattleState(
    val battleId: Long,
    val starSystemId: Long,
    val units: MutableList<TacticalUnit>,
    var tickCount: Int = 0,
    val battleBoundsX: Double = 1000.0,
    val battleBoundsY: Double = 600.0,

    // Fortress gun state
    var fortressGunLastFired: Int = -9999,
    var fortressGunPower: Int = 0,
    var fortressGunRange: Int = 0,
    var fortressGunCooldown: Int = 0,
    var fortressFactionId: Long = 0,

    // Tick events for broadcasting
    val tickEvents: MutableList<BattleTickEvent> = mutableListOf(),

    /** 현재 전투 단계 (보급/이동/수색/교전/점령) */
    var currentPhase: String = "MOVEMENT",

    /**
     * 색적 매트릭스: officerId → 탐지된 적 fleetId 집합.
     * DetectionService.updateDetectionMatrix() 호출 시 매 틱 갱신.
     */
    val detectionMatrix: MutableMap<Long, MutableSet<Long>> = mutableMapOf(),

    /**
     * 기함 격침으로 생성된 부상 이벤트 목록.
     * endBattle 이후 TacticalBattleService.processFlagshipDestructions()에서 처리.
     */
    val pendingInjuryEvents: MutableList<InjuryEvent> = mutableListOf(),

    /**
     * 지상전 박스 상태. 육전대 강하 시 초기화 (Plan 03-04).
     * null이면 지상전 미진행.
     */
    var groundBattleState: GroundBattleState? = null,

    /** Command buffer: WebSocket handlers enqueue, tick loop drains (per D-03/D-04) */
    val commandBuffer: ConcurrentLinkedQueue<TacticalCommand> = ConcurrentLinkedQueue(),

    /** Command hierarchy for attacker side (per D-05, populated at battle init) */
    var attackerHierarchy: CommandHierarchy? = null,

    /** Command hierarchy for defender side (per D-05, populated at battle init) */
    var defenderHierarchy: CommandHierarchy? = null,

    /** Pending conquest commands to be processed after command buffer drain */
    val pendingConquestCommands: MutableList<TacticalCommand.PlanetConquest> = mutableListOf(),

    /** Connected player officer IDs for online status tracking (Phase 9: priority ordering) */
    val connectedPlayerOfficerIds: MutableSet<Long> = mutableSetOf(),

    /**
     * Phase 14 D-22: NPC officer IDs (Officer.npcState != 0 at battle init).
     * Populated by BattleTriggerService.buildInitialState; read by
     * TacticalBattleService.toUnitDto to derive TacticalUnitDto.isNpc so the
     * frontend can render NPC markers (● / ○ / 🤖) per D-35.
     */
    val npcOfficerIds: MutableSet<Long> = mutableSetOf(),

    /**
     * Phase 12 D-06/D-09: SoT for mission objective per fleet.
     * Populated by BattleTriggerService at battle init and by
     * TacticalBattleService.syncOperationToActiveBattles on OperationPlan CRUD.
     * Read-through cached into TacticalUnit.missionObjective at tick start (Step 0.6).
     * ConcurrentHashMap because the tick loop reads while the sync channel writes.
     */
    val missionObjectiveByFleetId: MutableMap<Long, com.openlogh.engine.tactical.ai.MissionObjective> = ConcurrentHashMap(),

    /**
     * Phase 12 D-11/D-13: Fleet IDs that belong to a real OperationPlan
     * (NOT personality-defaulted). Used by TacticalBattleService.endBattle
     * to filter merit bonus recipients. CANCELLED sync channel removes entries here.
     */
    val operationParticipantFleetIds: MutableSet<Long> = java.util.Collections.newSetFromMap(ConcurrentHashMap()),

    /** Current tick counter (alias for tickCount, used by command hierarchy logic) */
    var currentTick: Int = 0,

    /**
     * Phase 24-20 (gap E42, gin7 manual p46-47): 地形障害 placed entities.
     * 플라즈마 폭풍(`PLASMA_STORM`) — 射線을 차단하고 진입 시 데미지.
     * 사르가소 暗礁域(`SARGASSO`) — 射線 차단 + 엔진 출력 저하.
     * 隕石帯(`ASTEROID_FIELD`) — 射線 차단 + 워프 진입 차단 가능.
     * 비어 있는 목록이면 맵에 장애물이 없는 상태와 동일.
     */
    val obstacles: MutableList<TerrainObstacle> = mutableListOf(),
)

data class BattleTickEvent(
    val type: String,  // "damage", "destroy", "retreat", "fortress_fire", "formation_change"
    val sourceUnitId: Long = 0,
    val targetUnitId: Long = 0,
    val value: Int = 0,
    val detail: String = "",
)

/**
 * Phase 24-20 (gap E42, gin7 manual p46-47):
 * 전술전 맵 위에 놓이는 地形障害 단일 노드. gin7 는 원형 장애물 모델을 쓰므로
 * 중심 좌표 + 반경으로 충분하다. type 은 렌더링/특수 효과 분기용.
 */
data class TerrainObstacle(
    val posX: Double,
    val posY: Double,
    val radius: Double,
    val type: TerrainObstacleType,
    /** Optional label for debugging / telemetry. */
    val label: String = type.displayNameKo,
)

enum class TerrainObstacleType(val displayNameKo: String, val blocksLineOfSight: Boolean, val blocksWarp: Boolean) {
    /** 플라즈마 폭풍 — 사선 차단, 내부 진입 시 데미지(후속 phase 에서 배선). */
    PLASMA_STORM("플라즈마 폭풍", blocksLineOfSight = true, blocksWarp = false),
    /** 사르가소 성운 — 사선 차단, 내부 이동 속도 감소(후속). */
    SARGASSO("사르가소", blocksLineOfSight = true, blocksWarp = false),
    /** 소행성대 — 사선 차단 + 워프 경로 차단. */
    ASTEROID_FIELD("소행성대", blocksLineOfSight = true, blocksWarp = true),
}

/**
 * Phase 14 Plan 03 (FE-05 / D-19): sensorRange formula.
 *
 * Server-authoritative fog-of-war visibility radius derived from
 * `TacticalUnit.energy.sensor`. The frontend never re-derives this — it
 * simply reads `TacticalUnitDto.sensorRange` every tick and draws a
 * visibility circle per friendly unit.
 *
 * Formula anchors (pinned by SensorRangeComputationTest):
 * - DEFAULT_SENSOR_SLIDER (17 ≈ balanced 100/6) ⇒ BASE_SENSOR_RANGE (150.0)
 * - Linear scaling: range = BASE * (slider / DEFAULT)
 * - Clamped to [MIN_SENSOR_RANGE, MAX_SENSOR_RANGE] so units are never blind
 *   and never see the whole map
 * - HP < INJURY_HP_THRESHOLD (30%) ⇒ multiplied by INJURY_MODIFIER (0.7)
 * - Dead unit ⇒ 0.0
 */
internal object SensorRangeFormula {
    const val BASE_SENSOR_RANGE: Double = 150.0
    const val MIN_SENSOR_RANGE: Double = 30.0
    const val MAX_SENSOR_RANGE: Double = 500.0
    const val DEFAULT_SENSOR_SLIDER: Int = 17  // 100 / 6 ≈ 17
    const val INJURY_MODIFIER: Double = 0.7
    const val INJURY_HP_THRESHOLD: Double = 0.30

    fun compute(unit: TacticalUnit): Double {
        if (!unit.isAlive) return 0.0
        val sensorSlider = unit.energy.sensor.coerceIn(0, 100)
        // Linear: base * (slider / default). Default slider 17 ⇒ multiplier 1.0.
        val baseMultiplier = sensorSlider.toDouble() / DEFAULT_SENSOR_SLIDER.toDouble()
        var range = BASE_SENSOR_RANGE * baseMultiplier
        val hpRatio = unit.hp.toDouble() / unit.maxHp.coerceAtLeast(1).toDouble()
        if (hpRatio < INJURY_HP_THRESHOLD) range *= INJURY_MODIFIER
        return range.coerceIn(MIN_SENSOR_RANGE, MAX_SENSOR_RANGE)
    }
}

/**
 * Top-level wrapper around [SensorRangeFormula.compute] so same-package test
 * code can pin the formula without touching the private tick loop.
 */
internal fun computeSensorRange(unit: TacticalUnit): Double = SensorRangeFormula.compute(unit)

/**
 * Core tactical battle engine. Processes one tick at a time.
 *
 * Based on gin7 manual Chapter 4:
 * - BEAM/GUN weapons with energy-based damage
 * - Shield absorption
 * - Command range expansion
 * - Formation modifiers
 * - Fortress gun line-of-fire
 */
class TacticalBattleEngine(
    private val missileSystem: MissileWeaponSystem = MissileWeaponSystem(),
    private val fortressGunSystem: FortressGunSystem = FortressGunSystem(),
    private val detectionService: DetectionService = DetectionService(),
    private val shipStatRegistry: ShipStatRegistry? = null,
) {

    companion object {
        const val BEAM_BASE_DAMAGE = 30.0
        const val GUN_BASE_DAMAGE = 40.0
        const val BEAM_RANGE = 200.0       // mid-close range
        const val GUN_RANGE = 150.0        // mid-close range
        const val BASE_SPEED = 3.0         // base movement per tick
        const val COMMAND_RANGE_GROWTH_RATE = 0.5  // per tick, scaled by command stat
        // Phase 24-13 (gap C10, gin7 manual p52): 撤退 命令 all所要時間 = 2.5分.
        // At 1 tick/sec the retreat progress must take 150 ticks from 0 → 1.0,
        // so RETREAT_SPEED = 1.0 / 150.0 ≈ 0.006667. The legacy 0.02 value
        // reached the warp threshold in 50 ticks (~50 s) — roughly 3× too fast.
        const val RETREAT_SPEED = 1.0 / 150.0
        const val MORALE_DAMAGE_THRESHOLD = 20  // below this, combat effectiveness drops
        const val MISSILE_RANGE = 800.0    // TacticalWeaponType.MISSILE.baseRange * 100
        const val FIGHTER_RANGE = 600.0    // TacticalWeaponType.FIGHTER.baseRange * 100
        const val STANCE_CHANGE_COOLDOWN = 10  // ticks between stance changes

        /**
         * Phase 24-25 (gap C9, gin7 매뉴얼 p52):
         * REVERSE(反転) 커맨드 수신 후 실제 진행 방향이 반전되기까지 걸리는 시간.
         * 매뉴얼은 10 초를 명시하고 이 엔진은 1 tick = 1 초로 동작하므로 10 ticks.
         */
        const val REVERSE_PREP_TICKS = 10
    }

    /** Expose missile system for SORTIE command in TacticalBattleService. */
    fun getMissileSystem(): MissileWeaponSystem = missileSystem

    /**
     * Process a single battle tick. Returns updated state.
     */
    fun processTick(state: TacticalBattleState, rng: Random = Random): TacticalBattleState {
        state.tickEvents.clear()
        state.tickCount++
        state.currentTick = state.tickCount  // Sync currentTick for CRC/hierarchy logic

        // Step 0: Drain command buffer (per D-03)
        drainCommandBuffer(state)

        // Step 0.5: Process out-of-CRC units (Phase 9 Plan 03)
        processOutOfCrcUnits(state)

        // Step 0.6: Phase 12 — read-through mission objective cache refresh.
        // Must run BEFORE TacticalAIRunner.processAITick because the runner reads
        // unit.missionObjective at TacticalAIRunner.kt:77. Absence in the map leaves
        // the existing value untouched (BattleTriggerService has already seeded it
        // via defaultForPersonality for non-participants at battle init).
        for (unit in state.units) {
            if (!unit.isAlive) continue
            state.missionObjectiveByFleetId[unit.fleetId]?.let { unit.missionObjective = it }
        }

        // Step 0.7: Process tactical AI for NPC/offline units (Phase 11)
        TacticalAIRunner.processAITick(state)

        val aliveUnits = state.units.filter { it.isAlive }

        // 0. Update per-tick counters (stance cooldown counts down)
        for (unit in aliveUnits) {
            if (unit.stanceChangeTicksRemaining > 0) {
                unit.stanceChangeTicksRemaining--
            }
            // Phase 24-25 (gap C9, gin7 매뉴얼 p52): 反転 charge tick-down.
            // charge 카운터가 1 → 0 으로 넘어가는 순간에만 실제로 velocity 를 반전한다.
            // mobility 스케일은 24-12 에서 도입한 공식을 그대로 사용 — mobility 50 = 1.0,
            // 0 = 0.5, 100 = 1.5 (clamp).
            if (unit.reverseChargeTicksRemaining > 0) {
                unit.reverseChargeTicksRemaining--
                if (unit.reverseChargeTicksRemaining == 0) {
                    val mobilityFactor = (0.5 + unit.mobility / 100.0).coerceIn(0.5, 1.5)
                    unit.velX = -unit.velX * mobilityFactor
                    unit.velY = -unit.velY * mobilityFactor
                    state.tickEvents.add(
                        BattleTickEvent(
                            "reverse_complete", sourceUnitId = unit.fleetId,
                            detail = "${unit.officerName} 선회 완료 (反転 10 초 대기 종료)"
                        )
                    )
                }
            }
        }

        // 1. Update command range for all units
        for (unit in aliveUnits) {
            updateCommandRange(unit)
            // Phase 14 FE-05 / D-19: recompute sensorRange from energy.sensor.
            // Cached on the unit so TacticalBattleService.toUnitDto can surface
            // it without re-deriving the formula on every DTO build.
            unit.sensorRange = SensorRangeFormula.compute(unit)
        }

        // 2. Process movement (STATIONED/ANCHORING cannot move — handled in processMovement)
        for (unit in aliveUnits) {
            if (unit.isRetreating) {
                processRetreat(unit, state)
            } else {
                processMovement(unit, state, aliveUnits)
            }
        }

        // 2.5 Detection sweep — update detectionMatrix from SENSOR allocations
        detectionService.updateDetectionMatrix(state)

        // 3. Process combat (weapons fire)
        // gin7 rule: 사기 20 미만 함대는 공격 행동 불가 (isEffective=false)
        val effectiveUnits = aliveUnits.filter { it.morale >= MORALE_DAMAGE_THRESHOLD && !it.isRetreating }
        for (unit in effectiveUnits) {
            processCombat(unit, state, aliveUnits, rng)
        }

        // 4. Process fortress gun
        if (state.fortressGunPower > 0) {
            processFortressGun(state, rng)
        }

        // 5. Remove destroyed units
        for (unit in state.units) {
            if (unit.hp <= 0 && unit.isAlive) {
                unit.isAlive = false
                unit.ships = 0
                state.tickEvents.add(BattleTickEvent("destroy", targetUnitId = unit.fleetId,
                    detail = "${unit.officerName} 함대 궤멸"))

                // 기함 격침 → 부상 이벤트 생성
                if (unit.isFlagship) {
                    // 격침된 기함은 isFlagship 해제
                    unit.isFlagship = false

                    val damageRatio = 1.0  // 기함 격침 = 최대 피해
                    val severity = InjuryEvent.calculateSeverity(0, damageRatio)
                    state.pendingInjuryEvents.add(InjuryEvent(
                        officerId = unit.officerId,
                        officerName = unit.officerName,
                        severity = severity,
                        returnPlanetId = 0L,  // 플레이스홀더 — TacticalBattleService에서 DB 조회로 교체
                        tick = state.tickCount,
                    ))
                    state.tickEvents.add(BattleTickEvent("flagship_destroyed",
                        sourceUnitId = unit.fleetId, value = severity,
                        detail = "${unit.officerName} 기함 격침 → 부상 (중증도 $severity)"))

                    // SUCC-03: Start succession vacancy countdown
                    val fsHierarchy = getHierarchyForUnit(unit, state)
                    if (fsHierarchy != null) {
                        SuccessionService.startVacancy(fsHierarchy, unit.officerId, state.tickCount)
                    }

                    // SUCC-02: Apply injury capability reduction
                    if (fsHierarchy != null) {
                        SuccessionService.applyInjuryCapabilityReduction(fsHierarchy, state.pendingInjuryEvents.last())
                    }

                    // D-07: Flagship destroyed -> immediate AI re-evaluation for that side
                    TacticalAIRunner.triggerImmediateReeval(state, unit.side)

                    // 다음 부대가 기함 대체: 같은 진영 잔존 유닛 중 ships 최대 유닛 승격
                    val replacementUnit = state.units
                        .filter { it.isAlive && it.ships > 0 && it.side == unit.side && it.fleetId != unit.fleetId }
                        .maxByOrNull { it.ships }
                    if (replacementUnit != null) {
                        replacementUnit.isFlagship = true
                        state.tickEvents.add(BattleTickEvent("flagship_transfer",
                            sourceUnitId = replacementUnit.fleetId,
                            detail = "${replacementUnit.officerName} 기함 부대 대체"))
                    }

                    // Phase 24-15 (gap C5, gin7 manual p51):
                    // "艦艇ユニットに搭載されている陸戦隊ユニットは、艦艇が撃破されても
                    //  自動的に他の艦艇に移動します。"
                    // Redistribute the destroyed unit's embarked ground troops to the
                    // largest surviving same-side unit (prefer the new flagship). If no
                    // friendly survivor, the chitai is lost with the ship.
                    if (unit.groundUnitsEmbark > 0) {
                        val receiver = replacementUnit ?: state.units
                            .filter { it.isAlive && it.side == unit.side && it.fleetId != unit.fleetId }
                            .maxByOrNull { it.ships }
                        if (receiver != null) {
                            receiver.groundUnitsEmbark += unit.groundUnitsEmbark
                            state.tickEvents.add(BattleTickEvent("ground_transfer",
                                sourceUnitId = unit.fleetId, targetUnitId = receiver.fleetId,
                                value = unit.groundUnitsEmbark,
                                detail = "${unit.officerName} 육전대 ${unit.groundUnitsEmbark}유닛 → ${receiver.officerName}"))
                            unit.groundUnitsEmbark = 0
                        } else {
                            state.tickEvents.add(BattleTickEvent("ground_annihilated",
                                sourceUnitId = unit.fleetId, value = unit.groundUnitsEmbark,
                                detail = "${unit.officerName} 육전대 ${unit.groundUnitsEmbark}유닛 전멸 (수령 부대 없음)"))
                            unit.groundUnitsEmbark = 0
                        }
                    }
                }

                // SUCC-05: subfleet commander incapacitated -> return units to direct command
                val unitHierarchy = getHierarchyForUnit(unit, state)
                if (unitHierarchy != null && unitHierarchy.subCommanders.containsKey(unit.officerId)) {
                    val returnedIds = CommandHierarchyService.returnUnitsToDirectCommand(
                        unitHierarchy, unit.officerId, state.units
                    )
                    if (returnedIds.isNotEmpty()) {
                        state.tickEvents.add(BattleTickEvent("subfleet_dissolved",
                            sourceUnitId = unit.fleetId,
                            detail = "${unit.officerName} 분함대 해체 — ${returnedIds.size}개 유닛 사령관 직할 복귀"))
                    }
                }
            }
        }

        // Step 5.3: Process command succession (SUCC-03/04/05)
        processSuccession(state)

        // Step 5.4: Check for command breakdown (SUCC-06)
        processCommandBreakdown(state)

        // 5.5 Ground battle tick (Plan 03-04)
        state.groundBattleState?.let { groundState ->
            val groundEngine = GroundBattleEngine()
            val groundLogs = groundEngine.processTick(groundState)
            if (groundLogs.isNotEmpty()) {
                state.tickEvents.add(BattleTickEvent("ground_battle",
                    detail = groundLogs.joinToString("; ")))
            }
            if (groundState.isConquestComplete) {
                state.tickEvents.add(BattleTickEvent("conquest_complete",
                    detail = "지상전 승리 — 행성 점령 완료 (행성 ${groundState.planetId})"))
            }
        }

        // 5.7 Tick jamming countdown + source-gone check (D-14)
        state.attackerHierarchy?.let { h ->
            CommunicationJamming.tickJamming(h)
            CommunicationJamming.clearJammingIfSourceGone(h, state.units)
        }
        state.defenderHierarchy?.let { h ->
            CommunicationJamming.tickJamming(h)
            CommunicationJamming.clearJammingIfSourceGone(h, state.units)
        }

        // 6. Morale effects
        for (unit in aliveUnits.filter { it.isAlive }) {
            updateMorale(unit, state)
        }

        return state
    }

    /**
     * Check if the battle has ended.
     */
    fun checkBattleEnd(state: TacticalBattleState): BattleOutcome? {
        val aliveAttackers = state.units.filter { it.isAlive && it.side == BattleSide.ATTACKER && !it.isRetreating }
        val aliveDefenders = state.units.filter { it.isAlive && it.side == BattleSide.DEFENDER && !it.isRetreating }

        if (aliveAttackers.isEmpty() && aliveDefenders.isEmpty()) {
            return BattleOutcome(null, "양군 전멸")
        }
        if (aliveAttackers.isEmpty()) {
            return BattleOutcome(BattleSide.DEFENDER, "공격 함대 전멸/퇴각")
        }
        if (aliveDefenders.isEmpty()) {
            return BattleOutcome(BattleSide.ATTACKER, "방어 함대 전멸/퇴각")
        }

        // Maximum battle duration: 600 ticks (10 minutes at 1 tick/sec)
        if (state.tickCount >= 600) {
            val attackerHpRatio = aliveAttackers.sumOf { it.hp }.toDouble() / aliveAttackers.sumOf { it.maxHp }.coerceAtLeast(1)
            val defenderHpRatio = aliveDefenders.sumOf { it.hp }.toDouble() / aliveDefenders.sumOf { it.maxHp }.coerceAtLeast(1)
            return if (attackerHpRatio > defenderHpRatio) {
                BattleOutcome(BattleSide.ATTACKER, "시간 초과 - 공격측 우세")
            } else if (defenderHpRatio > attackerHpRatio) {
                BattleOutcome(BattleSide.DEFENDER, "시간 초과 - 방어측 우세")
            } else {
                BattleOutcome(null, "시간 초과 - 무승부")
            }
        }

        return null
    }

    // ── Command Buffer ──

    /**
     * Step 0 of processTick: drain all buffered commands and apply them.
     * Called BEFORE any other tick processing (movement, detection, combat).
     */
    fun drainCommandBuffer(state: TacticalBattleState) {
        var cmd = state.commandBuffer.poll()
        while (cmd != null) {
            applyCommand(cmd, state)
            cmd = state.commandBuffer.poll()
        }
    }

    private fun applyCommand(cmd: TacticalCommand, state: TacticalBattleState) {
        // Succession commands bypass CRC and unit lookup (SUCC-01, SUCC-02)
        if (cmd is TacticalCommand.DesignateSuccessor) {
            val hierarchy = getHierarchyForSide(cmd, state)
            if (hierarchy != null) {
                val error = SuccessionService.designateSuccessor(hierarchy, cmd.officerId, cmd.successorOfficerId)
                if (error == null) {
                    state.tickEvents.add(BattleTickEvent("successor_designated",
                        sourceUnitId = cmd.officerId, targetUnitId = cmd.successorOfficerId,
                        detail = "후계자 지명 완료"))
                }
            }
            return
        }
        if (cmd is TacticalCommand.DelegateCommand) {
            val hierarchy = getHierarchyForSide(cmd, state)
            if (hierarchy != null) {
                val error = SuccessionService.delegateCommand(hierarchy, cmd.officerId)
                if (error == null) {
                    state.tickEvents.add(BattleTickEvent("command_delegated",
                        sourceUnitId = cmd.officerId,
                        detail = "지휘권 위임 완료"))
                }
            }
            return
        }
        // Sub-fleet assignment commands bypass CRC (administrative, not tactical)
        if (cmd is TacticalCommand.AssignSubFleet) {
            applyAssignSubFleet(cmd, state)
            return
        }
        // Phase 24-19 (gap C11, gin7 manual p52): group formation command dispatches
        // a single formation change to many subordinates at once. Handled before the
        // single-unit dispatch path because it operates on multiple officer ids.
        if (cmd is TacticalCommand.GroupFormationChange) {
            applyGroupFormationChange(cmd, state)
            return
        }
        if (cmd is TacticalCommand.ReassignUnit) {
            applyReassignUnit(cmd, state)
            return
        }
        // TriggerJamming targets enemy hierarchy -- no unit lookup needed
        if (cmd is TacticalCommand.TriggerJamming) {
            val targetHierarchy = when (cmd.targetSide) {
                BattleSide.ATTACKER -> state.attackerHierarchy
                BattleSide.DEFENDER -> state.defenderHierarchy
            }
            if (targetHierarchy != null) {
                CommunicationJamming.applyJamming(targetHierarchy, cmd.officerId, cmd.durationTicks)
            }
            return
        }

        val unit = state.units.find { it.officerId == cmd.officerId && it.isAlive } ?: return

        // CRC gate: check if command can reach the target unit (Phase 9 Plan 03)
        val hierarchy = getHierarchyForUnit(unit, state)
        if (hierarchy != null && !CrcValidator.isCommandReachable(cmd, unit, hierarchy, state.units)) {
            return  // Command blocked by CRC -- unit maintains last order
        }

        // Jamming gate: blocks fleet commander's fleet-wide orders when jammed (D-13)
        if (hierarchy != null && CommunicationJamming.isFleetWideCommandBlocked(cmd, unit, hierarchy)) {
            return  // Command blocked by communication jamming
        }

        // Update lastCommandTick on successful command delivery
        unit.lastCommandTick = state.currentTick

        when (cmd) {
            is TacticalCommand.SetEnergy -> {
                unit.energy = cmd.allocation
                unit.commandRange = unit.commandRange.resetOnCommand()
            }
            is TacticalCommand.SetStance -> {
                if (unit.stanceChangeTicksRemaining <= 0) {
                    unit.stance = cmd.stance
                    unit.stanceChangeTicksRemaining = STANCE_CHANGE_COOLDOWN
                    unit.commandRange = unit.commandRange.resetOnCommand()
                }
            }
            is TacticalCommand.SetFormation -> {
                unit.formation = cmd.formation
                unit.commandRange = unit.commandRange.resetOnCommand()
            }
            is TacticalCommand.Retreat -> {
                unit.isRetreating = true
                unit.commandRange = unit.commandRange.resetOnCommand()
            }
            is TacticalCommand.SetAttackTarget -> {
                unit.targetFleetId = cmd.targetFleetId
                unit.commandRange = unit.commandRange.resetOnCommand()
            }
            is TacticalCommand.UnitCommand -> {
                applyUnitCommand(cmd, unit, state)
            }
            is TacticalCommand.PlanetConquest -> {
                // PlanetConquest commands require service-level logic; store as pending
                state.pendingConquestCommands.add(cmd)
            }
            is TacticalCommand.AssignSubFleet -> {
                // Handled above; exhaustive when requires this branch
            }
            is TacticalCommand.GroupFormationChange -> {
                // Handled above; exhaustive when requires this branch
            }
            is TacticalCommand.ReassignUnit -> {
                // Handled above; exhaustive when requires this branch
            }
            is TacticalCommand.TriggerJamming -> {
                // Handled above; exhaustive when requires this branch
            }
            is TacticalCommand.DesignateSuccessor -> {
                // Handled in applyCommand before unit lookup
            }
            is TacticalCommand.DelegateCommand -> {
                // Handled in applyCommand before unit lookup
            }
        }
    }

    // ── Phase 9: Command Hierarchy helpers ──

    /**
     * Resolve the CommandHierarchy for a unit based on its side.
     */
    private fun getHierarchyForUnit(unit: TacticalUnit, state: TacticalBattleState): CommandHierarchy? {
        return when (unit.side) {
            BattleSide.ATTACKER -> state.attackerHierarchy
            BattleSide.DEFENDER -> state.defenderHierarchy
        }
    }

    /**
     * Resolve the CommandHierarchy for a command by finding the issuing officer's side.
     * Used for succession/delegation commands that don't require a live unit lookup.
     */
    private fun getHierarchyForSide(cmd: TacticalCommand, state: TacticalBattleState): CommandHierarchy? {
        val unit = state.units.find { it.officerId == cmd.officerId }
        return if (unit != null) getHierarchyForUnit(unit, state) else null
    }

    /**
     * Process command succession for both sides (SUCC-03/04).
     * Called after unit destruction (step 5) so vacancy/death state is current.
     */
    private fun processSuccession(state: TacticalBattleState) {
        listOfNotNull(state.attackerHierarchy, state.defenderHierarchy).forEach { hierarchy ->
            // Skip if no vacancy is active
            if (hierarchy.vacancyStartTick < 0) return@forEach

            // Check if 30-tick countdown has expired
            if (!SuccessionService.isVacancyExpired(hierarchy, state.currentTick)) {
                // Broadcast countdown event
                val ticksRemaining = SuccessionService.VACANCY_DURATION_TICKS -
                    (state.currentTick - hierarchy.vacancyStartTick)
                if (ticksRemaining % 10 == 0 || ticksRemaining <= 5) {  // broadcast every 10 ticks + last 5
                    state.tickEvents.add(BattleTickEvent("succession_countdown",
                        value = ticksRemaining,
                        detail = "지휘 승계 대기 중 (${ticksRemaining}틱 남음)"))
                }
                return@forEach
            }

            // Vacancy expired -> execute succession
            val aliveOfficerIds = state.units
                .filter { it.isAlive }
                .map { it.officerId }
                .toSet()

            val newCommander = SuccessionService.executeSuccession(hierarchy, aliveOfficerIds)
            if (newCommander != null) {
                val newCmdUnit = state.units.find { it.officerId == newCommander }
                state.tickEvents.add(BattleTickEvent("succession_complete",
                    sourceUnitId = newCmdUnit?.fleetId ?: 0,
                    detail = "${newCmdUnit?.officerName ?: "Unknown"} 지휘권 승계 완료"))
            } else {
                // No successor available -> command breakdown (handled in 10-07)
                state.tickEvents.add(BattleTickEvent("command_breakdown",
                    detail = "지휘 체계 붕괴 — 승계 가능한 지휘관 없음"))
            }
        }
    }

    /**
     * Process AssignSubFleet command: fleet commander assigns units to a sub-commander.
     * Validates via CommandHierarchyService before mutating hierarchy.
     */
    private fun applyAssignSubFleet(cmd: TacticalCommand.AssignSubFleet, state: TacticalBattleState) {
        val commanderUnit = state.units.find { it.officerId == cmd.officerId && it.isAlive } ?: return
        val hierarchy = getHierarchyForUnit(commanderUnit, state) ?: return

        // Only fleet commander can assign (validated in CommandHierarchyService)
        val crewOfficerIds = state.units
            .filter { it.side == commanderUnit.side && it.isAlive }
            .map { it.officerId }
            .toSet()

        val error = CommandHierarchyService.validateSubFleetAssignment(
            hierarchy, cmd.officerId, cmd.subCommanderId, cmd.unitIds, crewOfficerIds,
        )
        if (error != null) return  // silently reject invalid assignment

        val subUnit = state.units.find { it.officerId == cmd.subCommanderId && it.isAlive } ?: return
        CommandHierarchyService.assignSubFleet(
            hierarchy, cmd.subCommanderId, subUnit.officerName, subUnit.officerLevel,
            cmd.unitIds, state.units,
        )
    }

    /**
     * Phase 24-19 (gap C11, gin7 manual p52): 隊列命令 group formation command.
     *
     * A fleet commander (or sub-commander) designates a set of subordinate officer
     * ids and changes their formation in a single dispatch. Each target unit is
     * validated individually:
     *   - Must be alive.
     *   - Must be on the same side as the commander.
     *   - Must be reachable from the commander's CRC (otherwise silently skipped;
     *     it keeps its last order just like a single-unit out-of-CRC command).
     *
     * The commander's own unit is included if present in targetOfficerIds. Missing
     * ids are ignored (they may have been destroyed between enqueue and dispatch).
     */
    private fun applyGroupFormationChange(cmd: TacticalCommand.GroupFormationChange, state: TacticalBattleState) {
        val commanderUnit = state.units.find { it.officerId == cmd.officerId && it.isAlive } ?: return

        // Jamming rule mirrors the single-unit SetFormation path: if the commander
        // is jammed their fleet-wide orders are blocked. 隊列命令 is by definition
        // fleet-wide so we guard it at the commander level once.
        val hierarchy = getHierarchyForUnit(commanderUnit, state)
        if (hierarchy != null && CommunicationJamming.isFleetWideCommandBlocked(
                TacticalCommand.SetFormation(cmd.battleId, cmd.officerId, cmd.formation),
                commanderUnit,
                hierarchy,
            )
        ) {
            return
        }

        val targetIds = cmd.targetOfficerIds.toSet()
        var applied = 0
        for (target in state.units) {
            if (!target.isAlive) continue
            if (target.side != commanderUnit.side) continue
            if (target.officerId !in targetIds) continue

            // CRC gating per target — out-of-reach units are left alone. Gating is
            // only enforced when a command hierarchy exists; in hierarchy-less
            // scenarios (fallback sandbox / tests / flat command) the dispatch
            // applies directly, mirroring the single-unit SetFormation path.
            if (hierarchy != null && target.officerId != commanderUnit.officerId) {
                val reachable = CrcValidator.isCommandReachable(cmd, target, hierarchy, state.units)
                if (!reachable) continue
            }

            target.formation = cmd.formation
            target.commandRange = target.commandRange.resetOnCommand()
            target.lastCommandTick = state.currentTick
            applied++
        }

        if (applied > 0) {
            state.tickEvents.add(
                BattleTickEvent(
                    "group_formation", sourceUnitId = commanderUnit.fleetId,
                    value = applied,
                    detail = "${commanderUnit.officerName}: 대형 일괄 변경 ${cmd.formation.name} → ${applied}유닛",
                )
            )
        }
    }

    /**
     * Process ReassignUnit command: fleet commander reassigns a unit between sub-fleets.
     * CMD-05 condition: unit must be outside CRC AND stopped.
     */
    private fun applyReassignUnit(cmd: TacticalCommand.ReassignUnit, state: TacticalBattleState) {
        val commanderUnit = state.units.find { it.officerId == cmd.officerId && it.isAlive } ?: return
        val hierarchy = getHierarchyForUnit(commanderUnit, state) ?: return
        if (cmd.officerId != hierarchy.fleetCommander) return  // only fleet commander

        val targetUnit = state.units.find { it.fleetId == cmd.unitId && it.isAlive } ?: return

        // CMD-05 condition: unit must be outside CRC AND stopped
        val isOutsideCrc = !CrcValidator.isWithinCrc(commanderUnit, targetUnit)
        val isStopped = targetUnit.isStopped
        if (!isOutsideCrc || !isStopped) return  // reject if conditions not met

        // Reassign: remove from current sub-fleet, assign to new one (or direct)
        if (cmd.newSubCommanderId != null) {
            val newCommander = state.units.find { it.officerId == cmd.newSubCommanderId && it.isAlive } ?: return
            CommandHierarchyService.assignSubFleet(
                hierarchy, cmd.newSubCommanderId, newCommander.officerName, newCommander.officerLevel,
                listOf(cmd.unitId), state.units,
            )
        } else {
            // Return to fleet commander direct: remove from all sub-fleets
            targetUnit.subFleetCommanderId = null
            hierarchy.subCommanders.values.forEach { sf ->
                if (cmd.unitId in sf.unitIds) {
                    val updated = sf.copy(unitIds = sf.unitIds - cmd.unitId)
                    hierarchy.subCommanders[sf.commanderId] = updated
                }
            }
        }
    }

    private fun applyUnitCommand(cmd: TacticalCommand.UnitCommand, unit: TacticalUnit, state: TacticalBattleState) {
        unit.commandRange = unit.commandRange.resetOnCommand()

        when (cmd.command.uppercase()) {
            "MOVE" -> {
                val norm = sqrt(cmd.dirX * cmd.dirX + cmd.dirY * cmd.dirY).coerceAtLeast(0.001)
                val spd = BASE_SPEED * cmd.speed.coerceIn(0.0, 2.0)
                unit.velX = (cmd.dirX / norm) * spd
                unit.velY = (cmd.dirY / norm) * spd
            }
            "TURN" -> {
                val currentSpeed = sqrt(unit.velX * unit.velX + unit.velY * unit.velY)
                val norm = sqrt(cmd.dirX * cmd.dirX + cmd.dirY * cmd.dirY).coerceAtLeast(0.001)
                unit.velX = (cmd.dirX / norm) * currentSpeed
                unit.velY = (cmd.dirY / norm) * currentSpeed
            }
            "STRAFE" -> {
                unit.velY = cmd.dirY * BASE_SPEED
            }
            "REVERSE" -> {
                // Phase 24-12 (gap C7): mobility scaling — charge 완료 후에 곱해진다.
                // Phase 24-25 (gap C9, gin7 매뉴얼 p52): 反転 커맨드는 명령 수신 후
                // 10 초 대기를 두고 실제 선회가 일어난다. 이미 charge 중이면 재명령을 무시.
                if (unit.reverseChargeTicksRemaining <= 0) {
                    unit.reverseChargeTicksRemaining = REVERSE_PREP_TICKS
                }
            }
            "ATTACK", "FIRE" -> {
                if (cmd.targetFleetId != null) unit.targetFleetId = cmd.targetFleetId
            }
            "ORBIT" -> {
                if (cmd.targetFleetId != null) unit.targetFleetId = cmd.targetFleetId
                unit.isOrbiting = true
            }
            "FORMATION_CHANGE" -> {
                val formation = cmd.formation?.let { Formation.fromString(it) } ?: Formation.MIXED
                unit.formation = formation
            }
            "REPAIR" -> {
                if (unit.morale >= 5) {
                    unit.morale -= 5
                    val repairAmount = (unit.training / 100.0 * unit.maxHp * 0.05).toInt().coerceAtLeast(1)
                    unit.hp = (unit.hp + repairAmount).coerceAtMost(unit.maxHp)
                    state.tickEvents.add(BattleTickEvent("repair", sourceUnitId = unit.fleetId,
                        value = repairAmount, detail = "${unit.officerName} 자함 수리 (+$repairAmount HP)"))
                }
            }
            "RESUPPLY" -> {
                val nearPlanet = unit.posX < 100.0 || unit.posX > 900.0
                if (nearPlanet && unit.missileCount < 100) {
                    val resupplyAmount = 20
                    unit.missileCount = (unit.missileCount + resupplyAmount).coerceAtMost(100)
                    state.tickEvents.add(BattleTickEvent("resupply", sourceUnitId = unit.fleetId,
                        value = resupplyAmount, detail = "${unit.officerName} 미사일 보급 (+$resupplyAmount)"))
                }
            }
            // Phase 24-19 (gap C13, gin7 manual p49 空戰命令):
            // SORTIE 는 gin7 원본의 "戦闘艇 空戰命令"이며 대상이 CARRIER 인지에 따라
            // processFighterAttack 내부에서 対艦戦/迎撃戦을 자동 판정한다.
            // AIR_COMBAT 은 매뉴얼 표기와 정렬된 alias 로 동일 경로를 탄다.
            "SORTIE", "AIR_COMBAT" -> {
                val enemies = state.units.filter { it.side != unit.side && it.isAlive }
                val target = enemies.minByOrNull {
                    val dx = unit.posX - it.posX
                    val dy = unit.posY - it.posY
                    sqrt(dx * dx + dy * dy)
                }
                if (target != null) {
                    missileSystem.processFighterAttack(unit, target, state)
                }
            }
        }
    }

    /**
     * Process out-of-CRC units: maintain last order or AI retreat (D-06).
     * Called each tick AFTER command buffer drain and BEFORE movement processing.
     */
    private fun processOutOfCrcUnits(state: TacticalBattleState) {
        for (unit in state.units) {
            if (!unit.isAlive || unit.isRetreating) continue

            val hierarchy = getHierarchyForUnit(unit, state) ?: continue
            val commanderId = CommandHierarchyService.resolveCommanderForUnit(unit, hierarchy)
            val commanderUnit = state.units.find { it.officerId == commanderId && it.isAlive }

            // Skip if commander not found (edge case) or if unit IS the commander
            if (commanderUnit == null || unit.officerId == commanderId) continue

            // Check if unit is outside commander's CRC
            if (!CrcValidator.isWithinCrc(commanderUnit, unit)) {
                OutOfCrcBehavior.processOutOfCrcUnit(unit, commanderUnit, state.currentTick)
            }
        }
    }

    /**
     * Detect command breakdown and transition all units to independent AI (SUCC-06).
     * Called after processSuccession() — if succession failed (no successor found),
     * all alive units on that side fall back to OutOfCrcBehavior.
     */
    private fun processCommandBreakdown(state: TacticalBattleState) {
        val aliveOfficerIds = state.units
            .filter { it.isAlive }
            .map { it.officerId }
            .toSet()

        listOf(
            BattleSide.ATTACKER to state.attackerHierarchy,
            BattleSide.DEFENDER to state.defenderHierarchy,
        ).forEach { (side, hierarchy) ->
            if (hierarchy == null) return@forEach
            if (!SuccessionService.isCommandBroken(hierarchy, aliveOfficerIds)) return@forEach

            // Command is broken — force immediate AI re-evaluation for all units on this side
            // AI will handle retreat decisions based on personality thresholds
            TacticalAIRunner.triggerImmediateReeval(state, side)

            val affectedUnits = state.units.filter { it.isAlive && it.side == side && !it.isRetreating }

            // Broadcast once per tick only if there are affected units
            if (affectedUnits.isNotEmpty()) {
                state.tickEvents.add(BattleTickEvent("command_broken_ai",
                    value = affectedUnits.size,
                    detail = "${side.name} 지휘 체계 붕괴 — ${affectedUnits.size}개 유닛 독립 AI 행동"))
            }
        }
    }

    // ── Private helpers ──

    private fun updateCommandRange(unit: TacticalUnit) {
        // CommandRange.tick() handles expansion toward maxRange
        unit.commandRange = unit.commandRange.tick()
    }

    private fun processMovement(unit: TacticalUnit, state: TacticalBattleState, allUnits: List<TacticalUnit>) {
        // gin7 rule: STATIONED/ANCHORING 태세는 이동 불가
        if (!unit.stance.canMove) return

        // gin7 rule: 전투정 속도 디버프 (30% 감소, 60틱 지속)
        val speedDebuffFactor = if (unit.fighterSpeedDebuffTicks > 0) {
            unit.fighterSpeedDebuffTicks--
            1.0 - TacticalWeaponType.FIGHTER_SPEED_REDUCTION
        } else 1.0

        val speed = BASE_SPEED * unit.energy.speedMultiplier() * unit.formation.speedModifier * (unit.mobility / 50.0) * speedDebuffFactor

        // Find closest enemy
        val enemies = allUnits.filter { it.side != unit.side && it.isAlive && !it.isRetreating }
        val closestEnemy = enemies.minByOrNull { distance(unit, it) } ?: return

        val dist = distance(unit, closestEnemy)
        val optimalRange = BEAM_RANGE * 0.8  // try to stay at optimal weapon range

        if (dist > optimalRange) {
            // Move toward enemy
            val dx = closestEnemy.posX - unit.posX
            val dy = closestEnemy.posY - unit.posY
            val norm = sqrt(dx * dx + dy * dy).coerceAtLeast(1.0)
            unit.velX = dx / norm * speed
            unit.velY = dy / norm * speed
        } else if (dist < optimalRange * 0.5) {
            // Too close, back off slightly
            val dx = unit.posX - closestEnemy.posX
            val dy = unit.posY - closestEnemy.posY
            val norm = sqrt(dx * dx + dy * dy).coerceAtLeast(1.0)
            unit.velX = dx / norm * speed * 0.5
            unit.velY = dy / norm * speed * 0.5
        } else {
            // At good range, slow down
            unit.velX *= 0.5
            unit.velY *= 0.5
        }

        unit.posX = (unit.posX + unit.velX).coerceIn(0.0, state.battleBoundsX)
        unit.posY = (unit.posY + unit.velY).coerceIn(0.0, state.battleBoundsY)
    }

    private fun processRetreat(unit: TacticalUnit, state: TacticalBattleState) {
        val warpSpeed = RETREAT_SPEED * unit.energy.warpReadiness()
        unit.retreatProgress += warpSpeed

        // Move toward edge
        val edgeX = if (unit.side == BattleSide.ATTACKER) 0.0 else state.battleBoundsX
        val dx = edgeX - unit.posX
        val norm = abs(dx).coerceAtLeast(1.0)
        unit.posX += (dx / norm) * BASE_SPEED * 2

        if (unit.retreatProgress >= 1.0) {
            unit.isAlive = false  // successfully retreated
            state.tickEvents.add(BattleTickEvent("retreat", sourceUnitId = unit.fleetId, detail = "${unit.officerName} 퇴각 완료"))
        }
    }

    private fun processCombat(unit: TacticalUnit, state: TacticalBattleState, allUnits: List<TacticalUnit>, rng: Random) {
        val enemies = allUnits.filter { it.side != unit.side && it.isAlive && !it.isRetreating }
        if (enemies.isEmpty()) return

        // Target: player-designated target takes priority; fallback to closest in range
        val target = if (unit.targetFleetId != null) {
            enemies.find { it.fleetId == unit.targetFleetId && it.isAlive }
                ?: enemies.filter { distance(unit, it) <= BEAM_RANGE }.minByOrNull { distance(unit, it) }
        } else {
            enemies.filter { distance(unit, it) <= BEAM_RANGE }.minByOrNull { distance(unit, it) }
        } ?: return

        val dist = distance(unit, target)

        // Combat effectiveness factors
        val moraleModifier = (unit.morale / 100.0).coerceIn(0.3, 1.2)
        val trainingModifier = (unit.training / 100.0).coerceIn(0.3, 1.2)
        val attackStatModifier = unit.attack / 50.0
        val formationAttack = unit.formation.attackModifier
        // gin7 rule: 태세별 attackModifier 적용
        val stanceAttack = unit.stance.attackModifier

        // Resolve per-subtype base damage from ShipStatRegistry (fallback to hardcoded constants)
        val subtypeName = unit.shipSubtype?.name
        val subtypeStat = if (subtypeName != null) {
            shipStatRegistry?.getShipStat(subtypeName, "empire")
                ?: shipStatRegistry?.getShipStat(subtypeName, "alliance")
        } else null
        val beamBaseDamage = subtypeStat?.beamPower?.takeIf { it > 0 }?.toDouble() ?: BEAM_BASE_DAMAGE
        val gunBaseDamage  = subtypeStat?.gunPower?.takeIf { it > 0 }?.toDouble()  ?: GUN_BASE_DAMAGE

        // Phase 24-08 (gin7 manual p49): line-of-sight check. Friendly units
        // between source and target block BEAM/GUN/MISSILE fire.
        val hasLoS = hasLineOfSight(unit, target, state)

        // BEAM damage — gin7 rule: 최대사거리 70% 지점에서 최대 위력, 너무 가깝거나 멀면 선형 감소
        if (hasLoS && unit.energy.beam > 0 && dist <= BEAM_RANGE) {
            val optimalDist = BEAM_RANGE * 0.7
            val distFactor = (1.0 - abs(dist - optimalDist) / optimalDist).coerceAtLeast(0.0)
            val beamDmg = (beamBaseDamage * unit.energy.beamMultiplier() * attackStatModifier
                * formationAttack * stanceAttack * moraleModifier * trainingModifier * distFactor)
            val sensorAccuracy = unit.energy.sensorMultiplier()
            val hitChance = (0.6 + sensorAccuracy * 0.3).coerceAtMost(0.95)
            if (rng.nextDouble() < hitChance) {
                applyDamage(target, beamDmg.toInt(), state, unit, "BEAM")
            }
        }

        // GUN damage
        // Phase 24-12 (gap A9, gin7 manual p49): GUN attacks consume 軍需物資
        // per shot. If the unit has no supplies left, the gun is inoperable.
        if (hasLoS && unit.energy.gun > 0 && dist <= GUN_RANGE && unit.supplies > 0) {
            val gunDmg = (gunBaseDamage * unit.energy.gunMultiplier() * attackStatModifier
                * formationAttack * stanceAttack * moraleModifier * trainingModifier)
            val sensorAccuracy = unit.energy.sensorMultiplier()
            val hitChance = (0.5 + sensorAccuracy * 0.3).coerceAtMost(0.90)
            // Deduct a supply unit regardless of hit result — gin7 models it
            // as shell expenditure, not accuracy.
            unit.supplies = (unit.supplies - 1).coerceAtLeast(0)
            if (rng.nextDouble() < hitChance) {
                applyDamage(target, gunDmg.toInt(), state, unit, "GUN")
            }
        }

        // MISSILE attack (long range, missileCount > 0)
        // Phase 24-08: missiles also respect line-of-sight — guided, but not
        // "around corners". Only fire at a target with clear LoS.
        val missileTarget = enemies
            .filter { distance(unit, it) <= MISSILE_RANGE && hasLineOfSight(unit, it, state) }
            .minByOrNull { distance(unit, it) }
        if (missileTarget != null) {
            missileSystem.processMissileAttack(unit, missileTarget, state, rng)
        }

        // FIGHTER launch (carrier only, medium range) — fighters are small
        // mobile craft, not affected by ship-silhouette line-of-sight.
        val fighterTarget = enemies.filter { distance(unit, it) <= FIGHTER_RANGE }
            .minByOrNull { distance(unit, it) }
        if (fighterTarget != null) {
            missileSystem.processFighterAttack(unit, fighterTarget, state, rng)
        }
    }

    private fun applyDamage(target: TacticalUnit, rawDamage: Int, state: TacticalBattleState, source: TacticalUnit, weaponType: String) {
        // Apply shield absorption with stance defenseModifier
        // gin7 rule: STATIONED(1.3) 방어 보너스, COMBAT(0.9) 방어 패널티
        val shieldAbsorb = target.energy.shieldAbsorption()
        val formationDefense = target.formation.defenseModifier
        val defenseStatModifier = target.defense / 50.0
        val stanceDefense = target.stance.defenseModifier
        val absorbed = (rawDamage * shieldAbsorb * formationDefense * defenseStatModifier * stanceDefense).toInt()
        val finalDamage = (rawDamage - absorbed).coerceAtLeast(1)

        target.hp -= finalDamage
        // Calculate ship losses proportional to HP loss
        val shipLossRatio = finalDamage.toDouble() / target.maxHp.coerceAtLeast(1)
        val shipLoss = (shipLossRatio * target.maxShips).toInt().coerceAtLeast(0)
        val oldShips = target.ships
        target.ships = (target.ships - shipLoss).coerceAtLeast(0)

        // Phase 24-13 (gap C6, gin7 manual p51):
        // "搭載物: 艦艇ユニットに搭載されている資源/武器/軍需物資は、艦艇ユニットの隻数と
        //  比例して、同一の割合で減少します。"
        // When a unit loses ships, its carried supplies/missiles drop by the
        // same fraction. shipsRatio = newShips / oldShips. Use the ratio to
        // avoid discarding all cargo on a single grazing hit.
        if (oldShips > 0 && target.ships < oldShips) {
            val carryoverRatio = target.ships.toDouble() / oldShips.toDouble()
            target.supplies = (target.supplies * carryoverRatio).toInt().coerceAtLeast(0)
            target.missileCount = (target.missileCount * carryoverRatio).toInt().coerceAtLeast(0)
        }

        state.tickEvents.add(BattleTickEvent(
            "damage", sourceUnitId = source.fleetId, targetUnitId = target.fleetId,
            value = finalDamage, detail = "$weaponType ${source.officerName}→${target.officerName}"
        ))
    }

    private fun processFortressGun(state: TacticalBattleState, rng: Random) {
        // Delegate to FortressGunSystem — handles 4-type enum, full power per unit (not split), friendly fire
        fortressGunSystem.processFortressGunFire(state, rng)
    }

    /**
     * Phase 24-08 (Gap C1, gin7 manual p49): ship-to-ship line-of-sight check.
     *
     * Returns true iff there is NO friendly unit (or blocking terrain proxy)
     * between [source] and [target]. Used to gate BEAM/GUN/MISSILE fire so
     * 射線判定 matches the manual:
     *
     *   "射線上に味方の艦艇ユニット(移動要塞も含む)が存在する場合。
     *    射線上に射線障害地形が存在する場合。"
     *
     * Unlike [calculateLineOfFire] (which is designed for fortress guns and
     * returns every unit in the beam path, friendly or otherwise), this check
     * only looks for *friendly blockers* between source and target. Enemy
     * units on the path are fair game and do not block.
     *
     * Phase 24-20 (gap E42): terrain obstacles (플라즈마 폭풍 / 사르가소 / 隕石帯)
     * are now placed entities on the battle map. Any obstacle whose circular body
     * straddles the shot-line between source and target blocks the shot, per
     * manual p46-47 "射線障害地形". The check is O(units + obstacles) per shot
     * and short-circuits on the first blocker.
     */
    private fun hasLineOfSight(
        source: TacticalUnit,
        target: TacticalUnit,
        state: TacticalBattleState,
        blockingWidth: Double = 15.0,
    ): Boolean {
        val totalLen = distance(source, target)
        if (totalLen < 0.001) return true

        for (unit in state.units) {
            if (!unit.isAlive || unit.isRetreating) continue
            if (unit.fleetId == source.fleetId) continue
            if (unit.fleetId == target.fleetId) continue
            // gin7 p49: only FRIENDLY (same-side) units block the line of fire.
            if (unit.side != source.side) continue

            val perp = distanceToLine(
                unit.posX, unit.posY,
                source.posX, source.posY,
                target.posX, target.posY,
            )
            if (perp > blockingWidth) continue

            val proj = projectOnLine(
                unit.posX, unit.posY,
                source.posX, source.posY,
                target.posX, target.posY,
            )
            if (proj <= 0.0 || proj >= totalLen) continue

            return false
        }

        // Phase 24-20 (gap E42): Terrain obstacle blockers. An obstacle blocks
        // the line-of-fire iff its centre lies within `radius` perpendicular
        // distance from the shot-line AND the closest approach lies strictly
        // between source and target (so obstacles behind the shooter or past
        // the target never block).
        for (obstacle in state.obstacles) {
            if (!obstacle.type.blocksLineOfSight) continue

            val perp = distanceToLine(
                obstacle.posX, obstacle.posY,
                source.posX, source.posY,
                target.posX, target.posY,
            )
            if (perp > obstacle.radius) continue

            val proj = projectOnLine(
                obstacle.posX, obstacle.posY,
                source.posX, source.posY,
                target.posX, target.posY,
            )
            if (proj <= 0.0 || proj >= totalLen) continue

            return false
        }

        return true
    }

    /**
     * Calculate units in the line of fire from gun position to target.
     * gin7: fortress guns hit ALL units in their path, including friendlies.
     */
    private fun calculateLineOfFire(
        state: TacticalBattleState,
        gunPosX: Double, gunPosY: Double,
        targetX: Double, targetY: Double,
        range: Double,
        lineWidth: Double = 30.0,
    ): List<TacticalUnit> {
        val dx = targetX - gunPosX
        val dy = targetY - gunPosY
        val lineLength = sqrt(dx * dx + dy * dy).coerceAtLeast(1.0)

        return state.units.filter { unit ->
            if (!unit.isAlive || unit.isRetreating) return@filter false
            val dist = distanceToLine(unit.posX, unit.posY, gunPosX, gunPosY, targetX, targetY)
            val projDist = projectOnLine(unit.posX, unit.posY, gunPosX, gunPosY, targetX, targetY)
            dist <= lineWidth && projDist >= 0 && projDist <= range.coerceAtMost(lineLength)
        }
    }

    private fun updateMorale(unit: TacticalUnit, state: TacticalBattleState) {
        // Morale drops when HP is low
        val hpRatio = unit.hp.toDouble() / unit.maxHp.coerceAtLeast(1)
        if (hpRatio < 0.3) {
            unit.morale = (unit.morale - 1).coerceAtLeast(0)
        }
        // Morale boost from leadership
        if (unit.leadership > 70 && unit.morale < 80) {
            unit.morale = (unit.morale + 1).coerceAtMost(100)
        }
        // gin7 rule: COMBAT 태세이면 매 틱 사기 감소 (moraleDecayRate=0.002 → 0.2/틱 → toInt()=0, 500틱마다 1 감소)
        if (unit.stance == UnitStance.COMBAT) {
            unit.morale = (unit.morale - (unit.stance.moraleDecayRate * 100).toInt()).coerceAtLeast(0)
        }
    }

    // ── Geometry helpers ──

    private fun distance(a: TacticalUnit, b: TacticalUnit): Double =
        sqrt((a.posX - b.posX).pow(2) + (a.posY - b.posY).pow(2))

    /** Perpendicular distance from point to line segment. */
    private fun distanceToLine(px: Double, py: Double, lx1: Double, ly1: Double, lx2: Double, ly2: Double): Double {
        val dx = lx2 - lx1
        val dy = ly2 - ly1
        val lenSq = dx * dx + dy * dy
        if (lenSq == 0.0) return sqrt((px - lx1).pow(2) + (py - ly1).pow(2))
        val t = ((px - lx1) * dx + (py - ly1) * dy) / lenSq
        val clampedT = t.coerceIn(0.0, 1.0)
        val closestX = lx1 + clampedT * dx
        val closestY = ly1 + clampedT * dy
        return sqrt((px - closestX).pow(2) + (py - closestY).pow(2))
    }

    /** Project point onto line, return distance along line from start. */
    private fun projectOnLine(px: Double, py: Double, lx1: Double, ly1: Double, lx2: Double, ly2: Double): Double {
        val dx = lx2 - lx1
        val dy = ly2 - ly1
        val lenSq = dx * dx + dy * dy
        if (lenSq == 0.0) return 0.0
        return ((px - lx1) * dx + (py - ly1) * dy) / sqrt(lenSq)
    }
}
