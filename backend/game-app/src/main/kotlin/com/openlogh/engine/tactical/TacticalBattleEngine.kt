package com.openlogh.engine.tactical

import com.openlogh.model.CommandRange
import com.openlogh.model.DetectionCapability
import com.openlogh.model.EnergyAllocation
import com.openlogh.model.Formation
import com.openlogh.model.InjuryEvent
import com.openlogh.model.ShipSubtype
import com.openlogh.model.TacticalWeaponType
import com.openlogh.model.UnitStance
import com.openlogh.service.ShipStatRegistry
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
    /** 플레이어 지정 공격 대상 함대 ID (null=자동 선택) */
    var targetFleetId: Long? = null,
    /** 선회 중 여부 (ORBIT 커맨드) */
    var isOrbiting: Boolean = false,

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
)

data class BattleTickEvent(
    val type: String,  // "damage", "destroy", "retreat", "fortress_fire", "formation_change"
    val sourceUnitId: Long = 0,
    val targetUnitId: Long = 0,
    val value: Int = 0,
    val detail: String = "",
)

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
        const val RETREAT_SPEED = 0.02     // progress per tick when retreating
        const val MORALE_DAMAGE_THRESHOLD = 20  // below this, combat effectiveness drops
        const val MISSILE_RANGE = 800.0    // TacticalWeaponType.MISSILE.baseRange * 100
        const val FIGHTER_RANGE = 600.0    // TacticalWeaponType.FIGHTER.baseRange * 100
        const val STANCE_CHANGE_COOLDOWN = 10  // ticks between stance changes
    }

    /** Expose missile system for SORTIE command in TacticalBattleService. */
    fun getMissileSystem(): MissileWeaponSystem = missileSystem

    /**
     * Process a single battle tick. Returns updated state.
     */
    fun processTick(state: TacticalBattleState, rng: Random = Random): TacticalBattleState {
        state.tickEvents.clear()
        state.tickCount++

        // Step 0: Drain command buffer (per D-03)
        drainCommandBuffer(state)

        val aliveUnits = state.units.filter { it.isAlive }

        // 0. Update per-tick counters (stance cooldown counts down)
        for (unit in aliveUnits) {
            if (unit.stanceChangeTicksRemaining > 0) {
                unit.stanceChangeTicksRemaining--
            }
        }

        // 1. Update command range for all units
        for (unit in aliveUnits) {
            updateCommandRange(unit)
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
                }
            }
        }

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
        val unit = state.units.find { it.officerId == cmd.officerId && it.isAlive } ?: return
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
                unit.velX = -unit.velX
                unit.velY = -unit.velY
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
            "SORTIE" -> {
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

        // BEAM damage — gin7 rule: 최대사거리 70% 지점에서 최대 위력, 너무 가깝거나 멀면 선형 감소
        if (unit.energy.beam > 0 && dist <= BEAM_RANGE) {
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
        if (unit.energy.gun > 0 && dist <= GUN_RANGE) {
            val gunDmg = (gunBaseDamage * unit.energy.gunMultiplier() * attackStatModifier
                * formationAttack * stanceAttack * moraleModifier * trainingModifier)
            val sensorAccuracy = unit.energy.sensorMultiplier()
            val hitChance = (0.5 + sensorAccuracy * 0.3).coerceAtMost(0.90)
            if (rng.nextDouble() < hitChance) {
                applyDamage(target, gunDmg.toInt(), state, unit, "GUN")
            }
        }

        // MISSILE attack (long range, missileCount > 0)
        val missileTarget = enemies.filter { distance(unit, it) <= MISSILE_RANGE }
            .minByOrNull { distance(unit, it) }
        if (missileTarget != null) {
            missileSystem.processMissileAttack(unit, missileTarget, state, rng)
        }

        // FIGHTER launch (carrier only, medium range)
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
        val shipLoss = (finalDamage.toDouble() / target.maxHp.coerceAtLeast(1) * target.maxShips).toInt().coerceAtLeast(0)
        target.ships = (target.ships - shipLoss).coerceAtLeast(0)

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
