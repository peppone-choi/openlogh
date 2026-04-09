package com.openlogh.engine.tactical

import com.openlogh.model.EnergyAllocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Phase 14 Plan 03 (FE-05): Contract test pinning the SensorRangeFormula.
 *
 * sensorRange is derived per tick from `TacticalUnit.energy.sensor` so the
 * fog-of-war frontend (14-11) can compute enemy visibility without replicating
 * the formula. Per D-19 the server is the single source of truth; these tests
 * pin that contract.
 *
 * Formula anchors:
 * - Default slider (≈ balanced 100/6) ⇒ BASE_SENSOR_RANGE (150.0)
 * - High sensor allocation scales linearly, capped at MAX_SENSOR_RANGE (500.0)
 * - Zero sensor allocation clamps at MIN_SENSOR_RANGE (30.0) — never blind
 * - HP < 30% ⇒ multiplied by INJURY_MODIFIER (0.7)
 * - Dead unit ⇒ 0.0
 */
class SensorRangeComputationTest {

    private fun makeUnit(
        sensor: Int = 17,
        hp: Int = 1000,
        maxHp: Int = 1000,
        alive: Boolean = true,
    ): TacticalUnit {
        // EnergyAllocation requires total == 100. Pad the other channels to
        // absorb whatever sensor value the caller gave us.
        val remaining = 100 - sensor
        val beam = remaining / 5
        val gun = remaining / 5
        val shield = remaining / 5
        val engine = remaining / 5
        val warp = remaining - beam - gun - shield - engine
        val u = TacticalUnit(
            fleetId = 1L,
            officerId = 1L,
            officerName = "Test",
            factionId = 1L,
            side = BattleSide.ATTACKER,
            hp = hp,
            maxHp = maxHp,
            ships = 300,
            maxShips = 300,
            training = 80,
            morale = 80,
            energy = EnergyAllocation(
                beam = beam,
                gun = gun,
                shield = shield,
                engine = engine,
                warp = warp,
                sensor = sensor,
            ),
        )
        u.isAlive = alive
        return u
    }

    @Test
    fun `default sensor slider produces base sensor range`() {
        val u = makeUnit(sensor = 17)
        val range = computeSensorRange(u)
        // slider 17 == DEFAULT_SENSOR_SLIDER ⇒ BASE_SENSOR_RANGE 150.0
        assertEquals(150.0, range, 1.0)
    }

    @Test
    fun `high sensor allocation scales range up`() {
        val u = makeUnit(sensor = 50)
        val range = computeSensorRange(u)
        // 150.0 * (50/17) ≈ 441, capped at MAX_SENSOR_RANGE (500.0)
        assertTrue(range > 400.0, "Expected >400, got $range")
        assertTrue(range <= 500.0, "Expected <=500, got $range")
    }

    @Test
    fun `zero sensor allocation clamps to minimum`() {
        val u = makeUnit(sensor = 0)
        val range = computeSensorRange(u)
        assertEquals(30.0, range, 0.01)
    }

    @Test
    fun `injury modifier reduces range below 30 percent HP`() {
        val u = makeUnit(sensor = 17, hp = 200, maxHp = 1000)  // 20% HP
        val range = computeSensorRange(u)
        // 150 * 0.7 = 105
        assertEquals(105.0, range, 1.0)
    }

    @Test
    fun `dead unit has zero sensor range`() {
        val u = makeUnit(alive = false)
        val range = computeSensorRange(u)
        assertEquals(0.0, range)
    }
}
