package com.openlogh.engine.war

import com.openlogh.engine.modifier.ItemModifiers
import com.openlogh.entity.Officer
import com.openlogh.model.ArmType
import com.openlogh.model.CrewType
import kotlin.math.min

class WarUnitOfficer(
    val general: Officer,
    nationTech: Float = 0f,
    isAttacker: Boolean = true,
    cityLevel: Int = 0,
    capitalCityId: Long = 0,
) : WarUnit(general.name, general.factionId) {

    /** Train bonus from city-level defender bonuses (legacy: WarUnitOfficer constructor). */
    var trainBonus: Int = 0
    /** Atmos bonus from city-level attacker bonuses (legacy: WarUnitOfficer constructor). */
    var atmosBonus: Int = 0

    // C7: battle experience accumulators — written to general in applyResults()
    /** Level experience accumulated during battle (PHP: addLevelExp). */
    var pendingLevelExp: Int = 0
    /** Stat experience amount accumulated during battle (PHP: addStatExp). */
    var pendingStatExp: Int = 0

    init {
        ships = general.ships
        training = general.training.toInt()
        morale = general.morale.toInt()
        shipClass = general.shipClass.toInt()
        leadership = general.leadership.toInt()
        command = general.command.toInt()
        intelligence = general.intelligence.toInt()
        experience = general.experience
        dedication = general.dedication
        techLevel = nationTech
        injury = general.injury.toInt()
        supplies = general.supplies
        hp = ships
        maxHp = ships

        // Legacy WarUnitOfficer constructor: city-level bonuses (GAP-8)
        if (isAttacker) {
            if (cityLevel == 2) {
                atmosBonus += 5
            }
            if (capitalCityId == general.planetId) {
                atmosBonus += 5
            }
        } else {
            if (cityLevel == 1 || cityLevel == 3) {
                trainBonus += 5
            }
        }

        // Apply city-level bonuses to effective training/morale
        training = (training + trainBonus).coerceAtMost(100)
        morale = (morale + atmosBonus).coerceAtMost(100)

        val unitCrewType = getCrewType()
        criticalChance = computeCriticalChance(unitCrewType)
        dodgeChance = unitCrewType.avoid / 100.0 * (training / 100.0)

        // Apply item killRice modifier (e.g., 백우선/백상: 소모 군량 +10%)
        val itemKillRice = ItemModifiers.getKillRice(general.accessoryCode)
        if (itemKillRice != 1.0) {
            killRiceMultiplier = itemKillRice
        }
    }

    /**
     * Get dex for a given arm type from the general's dex fields.
     * Legacy: GeneralBase::getDex() — castle arm type maps to siege.
     */
    override fun getDexForArmType(armType: ArmType): Int {
        val effectiveType = if (armType == ArmType.CASTLE) ArmType.SIEGE else armType
        return when (effectiveType) {
            ArmType.FOOTMAN -> general.dex1
            ArmType.ARCHER -> general.dex2
            ArmType.CAVALRY -> general.dex3
            ArmType.WIZARD -> general.dex4
            ArmType.SIEGE -> general.dex5
            ArmType.MISC -> 0  // MISC has no dedicated dex field
            else -> 0
        }
    }

    /** Computed attack: stat + tech component. Train/atmos applied separately in war power. */
    override fun getBaseAttack(): Double {
        val unitCrewType = getCrewType()
        val ratioByArmType = when (unitCrewType.armType) {
            ArmType.WIZARD -> intelligence * 2.0 - 40.0
            ArmType.SIEGE -> leadership * 2.0 - 40.0
            ArmType.MISC -> (intelligence + leadership + command) * 2.0 / 3.0 - 40.0
            else -> command * 2.0 - 40.0
        }
        val ratio = when {
            ratioByArmType < 10.0 -> 10.0
            ratioByArmType > 100.0 -> 50.0 + ratioByArmType / 2.0
            else -> ratioByArmType
        }
        val attack = unitCrewType.attack + getTechAbil(techLevel)
        return attack * ratio / 100.0 * attackMultiplier
    }

    /** Computed defence: stat + tech component. Train applied separately in war power. */
    override fun getBaseDefence(): Double {
        val unitCrewType = getCrewType()
        val defence = unitCrewType.defence + getTechAbil(techLevel)
        val crewFactor = ships / 233.33 + 70.0
        return defence * crewFactor / 100.0 * defenceMultiplier
    }

    /** Legacy: HP > 0 AND rice > crew/100. */
    override fun continueWar(): WarContinuation {
        if (supplies <= hp / 100) return WarContinuation(false, true)
        if (hp <= 0) return WarContinuation(false, false)
        return WarContinuation(true, false)
    }

    fun consumeRice(damageDealt: Int, isAttacker: Boolean = true, vsCity: Boolean = false) {
        val unitCrewType = getCrewType()
        var consumption = damageDealt / 100.0
        if (!isAttacker) consumption *= 0.8
        if (vsCity) consumption *= 0.8
        consumption *= unitCrewType.riceCost
        consumption *= getTechCost(techLevel)
        consumption *= killRiceMultiplier
        supplies = (supplies - consumption.toInt()).coerceAtLeast(0)
    }

    private fun getCrewType(): CrewType = CrewType.fromCode(shipClass) ?: CrewType.FOOTMAN

    private fun computeCriticalChance(unitCrewType: CrewType): Double {
        val (mainStat, coef) = when (unitCrewType.armType) {
            ArmType.WIZARD -> intelligence to 0.4
            ArmType.SIEGE -> leadership to 0.4
            ArmType.MISC -> (intelligence + leadership + command) / 3 to 0.4
            else -> command to 0.5
        }
        val ratio = (mainStat - 65).coerceAtLeast(0) * coef
        return min(50.0, ratio) / 100.0
    }

    fun applyResults() {
        general.ships = hp.coerceAtLeast(0)
        general.supplies = supplies.coerceAtLeast(0)
        general.training = training.coerceIn(0, 110).toShort()
        general.morale = morale.coerceIn(0, 150).toShort()
        general.injury = injury.coerceIn(0, 80).toShort()

        // C7: apply accumulated battle experience
        if (pendingLevelExp > 0) {
            general.experience += pendingLevelExp
        }
        if (pendingStatExp > 0) {
            // PHP addStatExp: armType FOOTMAN/CAVALRY → strength, WIZARD → intel, SIEGE → leadership, MISC → all three
            val unitCrewType = CrewType.fromCode(general.shipClass.toInt())
            when (unitCrewType?.armType) {
                ArmType.WIZARD -> general.intelligenceExp = (general.intelligenceExp + pendingStatExp).coerceIn(0, 1000).toShort()
                ArmType.SIEGE -> general.leadershipExp = (general.leadershipExp + pendingStatExp).coerceIn(0, 1000).toShort()
                ArmType.MISC -> {
                    general.leadershipExp = (general.leadershipExp + pendingStatExp).coerceIn(0, 1000).toShort()
                    general.commandExp = (general.commandExp + pendingStatExp).coerceIn(0, 1000).toShort()
                    general.intelligenceExp = (general.intelligenceExp + pendingStatExp).coerceIn(0, 1000).toShort()
                }
                else -> general.commandExp = (general.commandExp + pendingStatExp).coerceIn(0, 1000).toShort()  // FOOTMAN, CAVALRY, ARCHER
            }
        }
    }
}
