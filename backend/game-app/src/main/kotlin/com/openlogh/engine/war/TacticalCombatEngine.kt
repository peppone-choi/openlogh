package com.openlogh.engine.war

import com.openlogh.model.*
import kotlin.math.hypot

/**
 * Tactical Combat Engine — coordinates all real-time tactical battle subsystems.
 *
 * Integrates:
 * - Detection system (索敵)
 * - Command range circle (指揮範囲)
 * - Command authority transfer (指揮権)
 * - Unit stance system (太勢)
 * - Missile and fighter weapons
 * - Ground combat
 * - Death/injury processing
 *
 * One tick = one simulation step of the real-time battle.
 */
class TacticalCombatEngine {

    private val detectionEngine = DetectionEngine()
    // TODO Phase 3: groundBattleEngine 제거됨 (삼국지 GroundBattleEngine 삭제). gin7 지상전 엔진으로 대체 예정.
    private val captureProcessor = PlanetCaptureProcessor()

    /**
     * Per-unit state tracked during tactical battle ticks.
     */
    data class TacticalUnit(
        val fleetId: Long,
        val officerId: Long,
        val officerName: String,
        val factionId: Long,
        var posX: Double,
        var posY: Double,
        var velX: Double = 0.0,
        var velY: Double = 0.0,
        var hp: Int,
        var maxHp: Int,
        var ships: Int,
        var maxShips: Int,
        var supplies: Int,
        var training: Int,
        var morale: Int,
        var commandStat: Int,
        var attackStat: Int,
        var defenseStat: Int,
        var mobilityStat: Int,
        var intelligenceStat: Int,
        var energy: EnergyAllocation = EnergyAllocation.BALANCED,
        var formation: Formation = Formation.MIXED,
        var stance: UnitStance = UnitStance.NAVIGATION,
        var stanceChangeTicksRemaining: Int = 0,
        var commandRange: CommandRange = CommandRange(),
        var isAlive: Boolean = true,
        var isRetreating: Boolean = false,
        var unitType: String = "FLEET",
        var shipSubtype: ShipSubtype? = null,
        /** Weapon cooldowns: weaponType -> ticks remaining */
        var weaponCooldowns: MutableMap<TacticalWeaponType, Int> = mutableMapOf(),
        /** Active debuffs: type -> ticks remaining */
        var debuffs: MutableMap<String, Int> = mutableMapOf(),
        /** Detection capability of this unit */
        var detectionCapability: DetectionCapability = DetectionCapability(
            baseRange = 5.0, basePrecision = 0.5, evasionRating = 0.2,
        ),
    ) {
        val isStopped: Boolean get() = velX == 0.0 && velY == 0.0
    }

    /**
     * Result of a single tick processing.
     */
    data class TickResult(
        val tick: Int,
        val weaponEvents: List<TacticalWeaponEvent>,
        val detectionResults: Map<Long, List<DetectionInfo>>,
        val injuryEvents: List<InjuryEvent>,
        val stanceChanges: List<Pair<Long, UnitStance>>,
        val commandTransfers: List<Pair<Long, Long>>,
        val logs: List<String>,
    )

    /**
     * Process one tick of tactical combat.
     */
    fun processTick(
        tick: Int,
        units: List<TacticalUnit>,
        factionCommanders: Map<Long, Long>,
    ): TickResult {
        val logs = mutableListOf<String>()
        val weaponEvents = mutableListOf<TacticalWeaponEvent>()
        val injuryEvents = mutableListOf<InjuryEvent>()
        val stanceChanges = mutableListOf<Pair<Long, UnitStance>>()
        val commandTransfers = mutableListOf<Pair<Long, Long>>()

        val aliveUnits = units.filter { it.isAlive }

        // 1. Update stance change timers
        for (unit in aliveUnits) {
            if (unit.stanceChangeTicksRemaining > 0) {
                unit.stanceChangeTicksRemaining--
            }
        }

        // 2. Update command range circles
        for (unit in aliveUnits) {
            unit.commandRange = unit.commandRange.tick()
        }

        // 3. Apply stance modifiers to morale (COMBAT stance decays morale)
        for (unit in aliveUnits) {
            if (unit.stance == UnitStance.COMBAT) {
                val decay = (unit.stance.moraleDecayRate * 100).toInt()
                unit.morale = (unit.morale - decay).coerceAtLeast(0)
            }
        }

        // 4. Detection sweep per faction
        val factionIds = aliveUnits.map { it.factionId }.distinct()
        val detectionResults = mutableMapOf<Long, List<DetectionInfo>>()
        for (factionId in factionIds) {
            val friendly = aliveUnits.filter { it.factionId == factionId }
            val enemies = aliveUnits.filter { it.factionId != factionId }

            val detectors = friendly.map { unit ->
                DetectorUnit(
                    fleetId = unit.fleetId,
                    factionId = unit.factionId,
                    posX = unit.posX,
                    posY = unit.posY,
                    capability = unit.detectionCapability.copy(isStopped = unit.isStopped),
                    energy = unit.energy,
                )
            }
            val targets = enemies.map { unit ->
                DetectionTarget(
                    fleetId = unit.fleetId,
                    factionId = unit.factionId,
                    posX = unit.posX,
                    posY = unit.posY,
                    evasion = unit.detectionCapability.copy(isStopped = unit.isStopped),
                    unitType = unit.unitType,
                )
            }
            detectionResults[factionId] = detectionEngine.performFactionDetection(detectors, targets)
        }

        // 5. Process weapon cooldowns
        for (unit in aliveUnits) {
            val iterator = unit.weaponCooldowns.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                entry.setValue(entry.value - 1)
                if (entry.value <= 0) iterator.remove()
            }
        }

        // 6. Process active debuffs
        for (unit in aliveUnits) {
            val iterator = unit.debuffs.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                entry.setValue(entry.value - 1)
                if (entry.value <= 0) iterator.remove()
            }
        }

        // 7. Check for destroyed units -> injury events
        for (unit in units.filter { it.isAlive && it.hp <= 0 }) {
            unit.isAlive = false
            val severity = InjuryEvent.calculateSeverity(0, 1.0)
            injuryEvents.add(
                InjuryEvent(
                    officerId = unit.officerId,
                    officerName = unit.officerName,
                    severity = severity,
                    returnPlanetId = 0, // Resolved by caller with actual planet data
                    tick = tick,
                )
            )
            logs.add("${unit.officerName} 기함 파괴. 부상 후 귀환성으로 워프.")
        }

        return TickResult(
            tick = tick,
            weaponEvents = weaponEvents,
            detectionResults = detectionResults,
            injuryEvents = injuryEvents,
            stanceChanges = stanceChanges,
            commandTransfers = commandTransfers,
            logs = logs,
        )
    }

    /**
     * Request stance change for a unit.
     * Returns true if the change was accepted (10s delay starts).
     */
    fun requestStanceChange(unit: TacticalUnit, newStance: UnitStance): Boolean {
        if (unit.stanceChangeTicksRemaining > 0) return false
        if (unit.stance == newStance) return false

        // 10 second delay = 10000ms. Assuming 100ms per tick = 100 ticks
        val delayTicks = (UnitStance.STANCE_CHANGE_DELAY_MS / 100).toInt()
        unit.stanceChangeTicksRemaining = delayTicks
        unit.stance = newStance
        return true
    }

    /**
     * Issue a command to a unit, resetting its command range to 0.
     */
    fun issueCommand(unit: TacticalUnit) {
        unit.commandRange = unit.commandRange.resetOnCommand()
    }

    /**
     * Fire a weapon from source to target.
     */
    fun fireWeapon(
        source: TacticalUnit,
        target: TacticalUnit,
        weaponType: TacticalWeaponType,
        tick: Int,
    ): TacticalWeaponEvent? {
        // Check cooldown
        if (source.weaponCooldowns.containsKey(weaponType)) return null

        // Check range
        val distance = hypot(target.posX - source.posX, target.posY - source.posY)
        if (distance > weaponType.baseRange) return null

        // Check supply cost
        if (source.supplies < weaponType.supplyCostPerUse) return null

        // Calculate damage
        var damage = weaponType.baseDamage
        val stanceModifier = source.stance.attackModifier
        val formationModifier = source.formation.attackModifier

        damage = (damage * stanceModifier * formationModifier).toInt()

        // Apply energy multiplier for BEAM/GUN
        when (weaponType) {
            TacticalWeaponType.BEAM -> damage = (damage * source.energy.beamMultiplier()).toInt()
            TacticalWeaponType.GUN -> damage = (damage * source.energy.gunMultiplier()).toInt()
            else -> {}
        }

        // Apply shield absorption from target
        val shieldAbsorption = target.energy.shieldAbsorption()
        val targetDefenseModifier = target.formation.defenseModifier
        val absorbedDamage = (damage * shieldAbsorption * targetDefenseModifier).toInt()
        val finalDamage = (damage - absorbedDamage).coerceAtLeast(1)

        // Apply damage
        target.hp -= finalDamage

        // Consume supplies
        source.supplies -= weaponType.supplyCostPerUse

        // Set cooldown
        source.weaponCooldowns[weaponType] = weaponType.cooldownTicks

        // Fighter special: speed reduction debuff
        var speedReduction = 0.0
        if (weaponType == TacticalWeaponType.FIGHTER) {
            speedReduction = TacticalWeaponType.FIGHTER_SPEED_REDUCTION
            target.debuffs["fighter_slow"] = TacticalWeaponType.FIGHTER_DEBUFF_DURATION_TICKS
        }

        return TacticalWeaponEvent(
            weaponType = weaponType,
            sourceFleetId = source.fleetId,
            targetFleetId = target.fleetId,
            damage = finalDamage,
            supplyCost = weaponType.supplyCostPerUse,
            tick = tick,
            speedReduction = speedReduction,
        )
    }

    /**
     * Resolve command authority for a faction entering tactical battle.
     */
    fun resolveCommandAuthority(candidates: List<CommandAuthority>): CommandAuthority? =
        CommandAuthority.resolveCommander(candidates)

    /**
     * Attempt manual command authority transfer.
     */
    fun transferCommandAuthority(
        fromUnit: TacticalUnit,
        toUnit: TacticalUnit,
        allUnits: List<TacticalUnit>,
    ): Boolean {
        // Target must be fully stopped
        if (!toUnit.isStopped) return false

        // Target must not be inside another flagship's command circle
        val otherCommanders = allUnits.filter {
            it.factionId == toUnit.factionId &&
                it.fleetId != fromUnit.fleetId &&
                it.commandRange.hasCommandRange
        }
        for (commander in otherCommanders) {
            val distance = hypot(toUnit.posX - commander.posX, toUnit.posY - commander.posY)
            if (commander.commandRange.isInRange(distance)) {
                return false // Inside another commander's circle
            }
        }

        return true
    }
}
