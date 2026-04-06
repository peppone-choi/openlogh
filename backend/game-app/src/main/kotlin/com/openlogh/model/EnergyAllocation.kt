package com.openlogh.model

/**
 * Energy allocation across 6 ship systems for tactical combat.
 * Total must equal 100 (percentage-based allocation).
 *
 * Based on gin7 manual Chapter 4:
 * - BEAM: mid-close range continuous fire weapon
 * - GUN: mid-close range burst weapon
 * - SHIELD: damage absorption
 * - ENGINE: movement speed
 * - WARP: emergency retreat capability
 * - SENSOR: detection range and accuracy
 */
data class EnergyAllocation(
    val beam: Int = 20,
    val gun: Int = 20,
    val shield: Int = 20,
    val engine: Int = 20,
    val warp: Int = 10,
    val sensor: Int = 10,
) {
    init {
        require(beam in 0..100) { "BEAM allocation must be 0-100, got $beam" }
        require(gun in 0..100) { "GUN allocation must be 0-100, got $gun" }
        require(shield in 0..100) { "SHIELD allocation must be 0-100, got $shield" }
        require(engine in 0..100) { "ENGINE allocation must be 0-100, got $engine" }
        require(warp in 0..100) { "WARP allocation must be 0-100, got $warp" }
        require(sensor in 0..100) { "SENSOR allocation must be 0-100, got $sensor" }
        require(total() == 100) { "Energy allocation must sum to 100, got ${total()}" }
    }

    fun total(): Int = beam + gun + shield + engine + warp + sensor

    /** Beam damage multiplier (0.0 to 1.0) */
    fun beamMultiplier(): Double = beam / 100.0

    /** Gun damage multiplier (0.0 to 1.0) */
    fun gunMultiplier(): Double = gun / 100.0

    /** Shield absorption ratio (0.0 to 1.0) */
    fun shieldAbsorption(): Double = shield / 100.0 * 0.8  // max 80% absorption at full shield

    /** Speed multiplier from engine allocation */
    fun speedMultiplier(): Double = 0.3 + (engine / 100.0 * 0.7)  // 30% base + up to 70% from engine

    /** Warp readiness (0.0 to 1.0), retreat possible when >= 0.5 */
    fun warpReadiness(): Double = warp / 100.0

    /** Sensor range multiplier */
    fun sensorMultiplier(): Double = 0.5 + (sensor / 100.0 * 0.5)  // 50% base + up to 50% from sensor

    companion object {
        /** Default balanced allocation */
        val BALANCED = EnergyAllocation()

        /** Aggressive: max weapons */
        val AGGRESSIVE = EnergyAllocation(beam = 30, gun = 30, shield = 15, engine = 15, warp = 5, sensor = 5)

        /** Defensive: max shield */
        val DEFENSIVE = EnergyAllocation(beam = 10, gun = 10, shield = 40, engine = 15, warp = 15, sensor = 10)

        /** Evasive: max engine + warp for retreat */
        val EVASIVE = EnergyAllocation(beam = 5, gun = 5, shield = 15, engine = 35, warp = 30, sensor = 10)

        fun fromMap(map: Map<String, Any>): EnergyAllocation = EnergyAllocation(
            beam = (map["beam"] as? Number)?.toInt() ?: 20,
            gun = (map["gun"] as? Number)?.toInt() ?: 20,
            shield = (map["shield"] as? Number)?.toInt() ?: 20,
            engine = (map["engine"] as? Number)?.toInt() ?: 20,
            warp = (map["warp"] as? Number)?.toInt() ?: 10,
            sensor = (map["sensor"] as? Number)?.toInt() ?: 10,
        )

        fun toMap(allocation: EnergyAllocation): Map<String, Int> = mapOf(
            "beam" to allocation.beam,
            "gun" to allocation.gun,
            "shield" to allocation.shield,
            "engine" to allocation.engine,
            "warp" to allocation.warp,
            "sensor" to allocation.sensor,
        )
    }
}
