package com.openlogh.engine.war

import com.openlogh.entity.Officer
import com.openlogh.entity.Planet
import com.openlogh.model.ArmType
import com.openlogh.model.ShipClass
import kotlin.math.max
import kotlin.math.min

data class ContinueWarResult(val canContinue: Boolean, val reason: String = "")

abstract class WarUnit {
    abstract var hp: Int
    abstract val maxHp: Int
    val isAlive: Boolean get() = hp > 0

    abstract var supplies: Int
    abstract var training: Int
    abstract var morale: Int
    abstract var injury: Int
    var injuryImmune: Boolean = false

    fun takeDamage(amount: Int) {
        hp = max(0, hp - amount)
    }

    abstract fun getBaseAttack(): Double
    abstract fun getBaseDefence(): Double
    abstract fun continueWar(): ContinueWarResult
    abstract fun calcBattleOrder(): Double
    abstract fun applyResults()
}

fun resolveShipClass(code: Short): ShipClass {
    ShipClass.fromCode(code.toInt())?.let { return it }
    return when (code.toInt()) {
        0 -> ShipClass.FOOTMAN
        1 -> ShipClass.FOOTMAN
        2 -> ShipClass.ARCHER
        3 -> ShipClass.CAVALRY
        4 -> ShipClass.WIZARD
        5 -> ShipClass.JEONGRAN
        else -> ShipClass.FOOTMAN
    }
}

class WarUnitGeneral(
    val officer: Officer,
    private val nationTech: Float = 0f,
) : WarUnit() {

    private val shipClass: ShipClass = resolveShipClass(officer.shipClass)
    private val techAbil: Int = getTechAbil(nationTech)
    private val techCost: Double = getTechCost(nationTech)

    override var hp: Int = officer.ships
    override val maxHp: Int = officer.ships
    override var supplies: Int = officer.supplies.toInt()
    override var training: Int = officer.training.toInt()
    override var morale: Int = officer.morale.toInt()
    override var injury: Int = officer.injury.toInt()

    override fun getBaseAttack(): Double {
        val mainStat = when (shipClass.armType) {
            ArmType.WIZARD, ArmType.SIEGE -> officer.intelligence.toInt()
            else -> officer.command.toInt()
        }
        val rawRatio = mainStat * 2 - 40
        val ratio = when {
            rawRatio < 10 -> 10
            rawRatio > 100 -> 50 + rawRatio / 2
            else -> rawRatio
        }
        return (shipClass.attack + techAbil).toDouble() * ratio / 100.0
    }

    override fun getBaseDefence(): Double {
        val crewFactor = hp.toDouble() / 233.33 + 70.0
        return shipClass.defence.toDouble() * crewFactor / 100.0
    }

    override fun continueWar(): ContinueWarResult {
        if (hp <= 0) return ContinueWarResult(false, "HP depleted")
        if (morale <= 20) return ContinueWarResult(false, "Low morale")
        if (supplies <= hp / 100) return ContinueWarResult(false, "Insufficient supplies")
        return ContinueWarResult(true)
    }

    fun consumeRice(damageDealt: Int, isAttacker: Boolean = true, vsCity: Boolean = false) {
        var base = damageDealt / 100.0
        if (!isAttacker) base *= 0.8
        if (vsCity) base *= 0.8
        val cost = (base * shipClass.riceCost * techCost).toInt()
        supplies -= cost
    }

    override fun calcBattleOrder(): Double {
        val statSum = officer.leadership + officer.command + officer.intelligence
        return statSum.toDouble() * hp * training * morale / 1_000_000.0
    }

    fun getDexForArmType(armType: ArmType): Int {
        return when (armType) {
            ArmType.FOOTMAN -> officer.dex1
            ArmType.ARCHER -> officer.dex2
            ArmType.CAVALRY -> officer.dex3
            ArmType.WIZARD -> officer.dex4
            ArmType.SIEGE -> officer.dex5
            else -> 0
        }
    }

    var rice: Int
        get() = supplies
        set(value) { supplies = value }

    var atmos: Int
        get() = morale
        set(value) { morale = value }

    var train: Int
        get() = training
        set(value) { training = value }

    val criticalChance: Double
        get() {
            val mainStat = when (shipClass.armType) {
                ArmType.WIZARD, ArmType.SIEGE -> officer.intelligence.toDouble()
                else -> officer.command.toDouble()
            }
            val raw = (mainStat - 65) * 0.5
            return maxOf(0.0, minOf(50.0, raw)) / 100.0
        }

    val dodgeChance: Double
        get() = shipClass.avoid / 100.0 * (training / 100.0)

    override fun applyResults() {
        officer.ships = hp
        officer.supplies = supplies
        officer.training = training.toShort()
        officer.morale = morale.toShort()
        officer.injury = injury.toShort()
    }
}

class WarUnitCity(
    val planet: Planet,
    private val year: Int = 0,
    private val startYear: Int = 0,
) : WarUnit() {

    private val cityTrainAtmos: Int = (year - startYear + 59).coerceIn(60, 110)
    private val trainBonus: Int = if (planet.level.toInt() == 1 || planet.level.toInt() == 3) 5 else 0

    override var hp: Int = planet.orbitalDefense * 10
    override val maxHp: Int = planet.orbitalDefense * 10
    override var supplies: Int = 0
    override var training: Int = cityTrainAtmos + trainBonus
    override var morale: Int = cityTrainAtmos
    override var injury: Int = 0

    override fun getBaseAttack(): Double {
        return (planet.orbitalDefense + planet.fortress * 9).toDouble() / 500.0 + 200.0
    }

    override fun getBaseDefence(): Double = getBaseAttack()

    override fun continueWar(): ContinueWarResult = ContinueWarResult(hp > 0)

    override fun calcBattleOrder(): Double = 0.0

    fun getDexForArmType(armType: ArmType): Int = (cityTrainAtmos - 60) * 7200

    override fun applyResults() {
        planet.orbitalDefense = hp / 10
    }
}
