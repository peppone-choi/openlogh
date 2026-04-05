package com.openlogh.engine.war

import com.openlogh.model.ArmType
import kotlin.math.pow

data class WarContinuation(
    val canContinue: Boolean,
    val isRiceShortage: Boolean,
)

abstract class WarUnit(
    val name: String,
    val nationId: Long,
) {
    var hp: Int = 0
    var maxHp: Int = 0
    var ships: Int = 0
    var training: Int = 0
    var morale: Int = 0
    var shipClass: Int = 0
    var leadership: Int = 0
    var command: Int = 0
    var intelligence: Int = 0
    var experience: Int = 0
    var dedication: Int = 0
    var techLevel: Float = 0f
    var injury: Int = 0

    var attackMultiplier: Double = 1.0
    var defenceMultiplier: Double = 1.0
    var criticalChance: Double = 0.05
    var dodgeChance: Double = 0.05
    var magicChance: Double = 0.0
    var magicDamageMultiplier: Double = 1.0

    var supplies: Int = 0
    var killRiceMultiplier: Double = 1.0

    var activatedSkills: MutableList<String> = mutableListOf()
    var isAlive: Boolean = true

    abstract fun getBaseAttack(): Double
    abstract fun getBaseDefence(): Double

    /**
     * Get dex (경험치) for the given arm type.
     * Legacy: GeneralBase::getDex() returns dex{armType}, castle maps to siege.
     */
    open fun getDexForArmType(armType: ArmType): Int = 0

    fun calcBattleOrder(): Double {
        // Legacy: (realStat + fullStat) / 2; Kotlin has single stat set ≈ effective stats
        val totalStat = (leadership + command + intelligence).toDouble()
        val totalCrew = ships / 1000000.0 * (training * morale).toDouble().pow(1.5)
        return totalStat + totalCrew / 100.0
    }

    open fun takeDamage(damage: Int) {
        hp -= damage
        if (hp <= 0) {
            hp = 0
            isAlive = false
        }
    }

    open fun continueWar(): WarContinuation = WarContinuation(
        canContinue = hp > 0,
        isRiceShortage = false,
    )

    fun beginPhase() {
        activatedSkills.clear()
    }
}
