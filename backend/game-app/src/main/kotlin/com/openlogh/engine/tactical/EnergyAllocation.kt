package com.openlogh.engine.tactical

data class EnergyAllocation(
    val beam: Int,
    val gun: Int,
    val shield: Int,
    val engine: Int,
    val sensor: Int,
) {
    init {
        require(beam + gun + shield + engine + sensor == 100) {
            "Energy allocation must total 100, got ${beam + gun + shield + engine + sensor}"
        }
        require(beam >= 0 && gun >= 0 && shield >= 0 && engine >= 0 && sensor >= 0) {
            "Energy values must be non-negative"
        }
    }

    /** BEAM: 1% -> +2% beam damage */
    fun beamDamageMultiplier(): Double = 1.0 + beam * 0.02

    /** GUN: 1% -> +2% gun damage */
    fun gunDamageMultiplier(): Double = 1.0 + gun * 0.02

    /** SHIELD: 1% -> +1.5% damage reduction */
    fun shieldReduction(): Double = shield * 0.015

    /** ENGINE: 1% -> +1% movement distance, +0.5% evasion */
    fun movementMultiplier(): Double = 1.0 + engine * 0.01

    /** ENGINE evasion bonus */
    fun evasionBonus(): Double = engine * 0.005

    /** SENSOR: 1% -> +3 distance range bonus */
    fun sensorRangeBonus(): Double = sensor * 3.0

    /** SENSOR: 1% -> +1% accuracy */
    fun sensorAccuracyBonus(): Double = sensor * 0.01

    companion object {
        val AGGRESSIVE = EnergyAllocation(beam = 40, gun = 30, shield = 10, engine = 10, sensor = 10)
        val DEFENSIVE = EnergyAllocation(beam = 10, gun = 10, shield = 40, engine = 20, sensor = 20)
        val MOBILE = EnergyAllocation(beam = 10, gun = 10, shield = 15, engine = 45, sensor = 20)
        val BALANCED = EnergyAllocation(beam = 20, gun = 20, shield = 20, engine = 20, sensor = 20)
        val RECON = EnergyAllocation(beam = 10, gun = 10, shield = 10, engine = 20, sensor = 50)
    }
}
