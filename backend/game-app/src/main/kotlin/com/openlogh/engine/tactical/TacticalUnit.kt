package com.openlogh.engine.tactical

import kotlin.math.max

data class TacticalUnit(
    val id: Int,
    val fleetId: Long,
    val factionId: Long,
    val officerId: Long,
    val shipClass: TacticalShipClass,
    var hp: Int,
    val maxHp: Int = hp,
    var x: Double = 0.0,
    var y: Double = 0.0,
    var z: Double = 0.0,
    var isFlagship: Boolean = false,
    /** 마지막 이동 방향 (방향 방어 계산용) — 초기값은 전방 */
    var lastMoveX: Double = 0.0,
    var lastMoveY: Double = 1.0,
) {
    fun isAlive(): Boolean = hp > 0

    fun position(): Position = Position(x, y, z)

    fun takeDamage(amount: Int): Int {
        val actual = amount.coerceAtMost(hp)
        hp = max(0, hp - actual)
        return actual
    }

    /** Max movement distance per turn (distance units) */
    fun getMovementRange(energy: EnergyAllocation, formation: Formation): Double {
        val base = shipClass.baseSpeed
        return max(1.0, base * energy.movementMultiplier() * formation.mobilityBonus)
    }

    /** BEAM attack range (distance units) */
    fun getBeamRange(energy: EnergyAllocation): Double =
        shipClass.beamRange + energy.sensorRangeBonus()

    /** GUN attack range (distance units) */
    fun getGunRange(energy: EnergyAllocation): Double =
        shipClass.gunRange + energy.sensorRangeBonus()

    fun getBeamAttackPower(energy: EnergyAllocation, formation: Formation): Double {
        val base = shipClass.baseAttack.toDouble()
        return base * energy.beamDamageMultiplier() * formation.attackBonus
    }

    fun getGunAttackPower(energy: EnergyAllocation, formation: Formation): Double {
        val base = shipClass.baseAttack * 0.8
        return base * energy.gunDamageMultiplier() * formation.attackBonus
    }

    fun getDefensePower(energy: EnergyAllocation, formation: Formation): Double {
        val base = shipClass.baseDefense.toDouble()
        val shieldMod = 1.0 + energy.shieldReduction()
        return base * shieldMod * formation.defenseBonus
    }

    fun getEvasionChance(energy: EnergyAllocation, formation: Formation): Double {
        val base = 0.05
        return (base + energy.evasionBonus()) * formation.mobilityBonus
    }

    /** Max attack range (distance units) — 탑재 무기 중 최대 사거리 (토르 해머 제외) */
    fun getAttackRange(energy: EnergyAllocation): Double {
        val sensorBonus = energy.sensorRangeBonus()
        return shipClass.defaultWeapons()
            .filter { it.weaponType != WeaponType.THOR_HAMMER }
            .maxOfOrNull { it.weaponType.baseRange + sensorBonus }
            ?: maxOf(getBeamRange(energy), getGunRange(energy))
    }
}
