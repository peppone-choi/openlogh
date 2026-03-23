package com.opensam.engine.war

import com.opensam.engine.modifier.ItemModifiers
import com.opensam.entity.General
import com.opensam.model.ArmType
import com.opensam.model.CrewType
import kotlin.math.min

class WarUnitGeneral(
    val general: General,
    nationTech: Float = 0f,
    isAttacker: Boolean = true,
    cityLevel: Int = 0,
    capitalCityId: Long = 0,
) : WarUnit(general.name, general.nationId) {

    /** Train bonus from city-level defender bonuses (legacy: WarUnitGeneral constructor). */
    var trainBonus: Int = 0
    /** Atmos bonus from city-level attacker bonuses (legacy: WarUnitGeneral constructor). */
    var atmosBonus: Int = 0

    // C7: battle experience accumulators — written to general in applyResults()
    /** Level experience accumulated during battle (PHP: addLevelExp). */
    var pendingLevelExp: Int = 0
    /** Stat experience amount accumulated during battle (PHP: addStatExp). */
    var pendingStatExp: Int = 0

    init {
        crew = general.crew
        train = general.train.toInt()
        atmos = general.atmos.toInt()
        crewType = general.crewType.toInt()
        leadership = general.leadership.toInt()
        strength = general.strength.toInt()
        intel = general.intel.toInt()
        experience = general.experience
        dedication = general.dedication
        tech = nationTech
        injury = general.injury.toInt()
        rice = general.rice
        hp = crew
        maxHp = crew

        // Legacy WarUnitGeneral constructor: city-level bonuses (GAP-8)
        if (isAttacker) {
            if (cityLevel == 2) {
                atmosBonus += 5
            }
            if (capitalCityId == general.cityId) {
                atmosBonus += 5
            }
        } else {
            if (cityLevel == 1 || cityLevel == 3) {
                trainBonus += 5
            }
        }

        // Apply city-level bonuses to effective train/atmos
        train = (train + trainBonus).coerceAtMost(100)
        atmos = (atmos + atmosBonus).coerceAtMost(100)

        val unitCrewType = getCrewType()
        criticalChance = computeCriticalChance(unitCrewType)
        dodgeChance = unitCrewType.avoid / 100.0 * (train / 100.0)

        // Apply item killRice modifier (e.g., 백우선/백상: 소모 군량 +10%)
        val itemKillRice = ItemModifiers.getKillRice(general.itemCode)
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
            ArmType.WIZARD -> intel * 2.0 - 40.0
            ArmType.SIEGE -> leadership * 2.0 - 40.0
            ArmType.MISC -> (intel + leadership + strength) * 2.0 / 3.0 - 40.0
            else -> strength * 2.0 - 40.0
        }
        val ratio = when {
            ratioByArmType < 10.0 -> 10.0
            ratioByArmType > 100.0 -> 50.0 + ratioByArmType / 2.0
            else -> ratioByArmType
        }
        val attack = unitCrewType.attack + getTechAbil(tech)
        return attack * ratio / 100.0 * attackMultiplier
    }

    /** Computed defence: stat + tech component. Train applied separately in war power. */
    override fun getBaseDefence(): Double {
        val unitCrewType = getCrewType()
        val defence = unitCrewType.defence + getTechAbil(tech)
        val crewFactor = crew / 233.33 + 70.0
        return defence * crewFactor / 100.0 * defenceMultiplier
    }

    /** Legacy: HP > 0 AND rice > crew/100. */
    override fun continueWar(): WarContinuation {
        if (rice <= hp / 100) return WarContinuation(false, true)
        if (hp <= 0) return WarContinuation(false, false)
        return WarContinuation(true, false)
    }

    fun consumeRice(damageDealt: Int, isAttacker: Boolean = true, vsCity: Boolean = false) {
        val unitCrewType = getCrewType()
        var consumption = damageDealt / 100.0
        if (!isAttacker) consumption *= 0.8
        if (vsCity) consumption *= 0.8
        consumption *= unitCrewType.riceCost
        consumption *= getTechCost(tech)
        consumption *= killRiceMultiplier
        rice = (rice - consumption.toInt()).coerceAtLeast(0)
    }

    private fun getCrewType(): CrewType = CrewType.fromCode(crewType) ?: CrewType.FOOTMAN

    private fun computeCriticalChance(unitCrewType: CrewType): Double {
        val (mainStat, coef) = when (unitCrewType.armType) {
            ArmType.WIZARD -> intel to 0.4
            ArmType.SIEGE -> leadership to 0.4
            ArmType.MISC -> (intel + leadership + strength) / 3 to 0.4
            else -> strength to 0.5
        }
        val ratio = (mainStat - 65).coerceAtLeast(0) * coef
        return min(50.0, ratio) / 100.0
    }

    fun applyResults() {
        general.crew = hp.coerceAtLeast(0)
        general.rice = rice.coerceAtLeast(0)
        general.train = train.toShort()
        general.atmos = atmos.toShort()
        general.injury = injury.toShort()

        // C7: apply accumulated battle experience
        if (pendingLevelExp > 0) {
            general.experience += pendingLevelExp
        }
        if (pendingStatExp > 0) {
            // PHP addStatExp: armType FOOTMAN/CAVALRY → strength, WIZARD → intel, SIEGE → leadership, MISC → all three
            val unitCrewType = CrewType.fromCode(crewType)
            when (unitCrewType?.armType) {
                ArmType.WIZARD -> general.intelExp = (general.intelExp + pendingStatExp).toShort()
                ArmType.SIEGE -> general.leadershipExp = (general.leadershipExp + pendingStatExp).toShort()
                ArmType.MISC -> {
                    general.leadershipExp = (general.leadershipExp + pendingStatExp).toShort()
                    general.strengthExp = (general.strengthExp + pendingStatExp).toShort()
                    general.intelExp = (general.intelExp + pendingStatExp).toShort()
                }
                else -> general.strengthExp = (general.strengthExp + pendingStatExp).toShort()  // FOOTMAN, CAVALRY, ARCHER
            }
        }
    }
}
