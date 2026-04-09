package com.openlogh.engine.ai.strategic

import com.openlogh.entity.Fleet
import com.openlogh.entity.Officer
import com.openlogh.entity.Planet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StrategicPowerScorerTest {

    private fun createFleet(
        id: Long = 1,
        currentUnits: Int = 0,
        planetId: Long = 1,
    ): Fleet = Fleet(
        id = id,
        sessionId = 1,
        leaderOfficerId = 1,
        factionId = 1,
        name = "F$id",
        unitType = "FLEET",
        maxUnits = 60,
        currentUnits = currentUnits,
        planetId = planetId,
    )

    private fun createOfficer(
        id: Long = 1,
        planetId: Long = 1,
        command: Short = 0,
        leadership: Short = 0,
    ): Officer = Officer(
        id = id,
        sessionId = 1,
        name = "O$id",
        factionId = 1,
        planetId = planetId,
        command = command,
        leadership = leadership,
    )

    private fun createPlanet(
        id: Long = 1,
        orbitalDefense: Int = 0,
        fortress: Int = 0,
    ): Planet = Planet(
        id = id,
        sessionId = 1,
        name = "P$id",
        factionId = 1,
        orbitalDefense = orbitalDefense,
        fortress = fortress,
    )

    @Test
    fun `empty fleets and officers produce zero composite score`() {
        val planet = createPlanet(orbitalDefense = 0, fortress = 0)
        val power = StrategicPowerScorer.evaluatePower(emptyList(), emptyList(), planet)

        assertEquals(0, power.totalShips)
        assertEquals(0.0, power.commanderScore)
        assertEquals(0.0, power.defenseScore)
        assertEquals(0.0, power.compositeScore)
    }

    @Test
    fun `single fleet officer and defended planet produces expected composite score`() {
        // 1 fleet × 1 unit = 300 ships
        val fleet = createFleet(currentUnits = 1)
        val officer = createOfficer(command = 80, leadership = 60)
        val planet = createPlanet(orbitalDefense = 50, fortress = 30)

        val power = StrategicPowerScorer.evaluatePower(listOf(fleet), listOf(officer), planet)

        // totalShips = 1 * 300 = 300
        assertEquals(300, power.totalShips)
        // commanderScore = (80 + 60) / 2 = 70.0 (single officer, average is 70)
        assertEquals(70.0, power.commanderScore)
        // defenseScore = 50 + 30 = 80.0
        assertEquals(80.0, power.defenseScore)
        // compositeScore = 300 * 0.5 + 70 * 30 + 80 * 20 = 150 + 2100 + 1600 = 3850
        assertEquals(3850.0, power.compositeScore, 0.0001)
    }

    @Test
    fun `multiple officers commander score is averaged`() {
        // Two officers with different stats — score should be the mean of their per-officer averages
        val planet = createPlanet()
        val o1 = createOfficer(id = 1, command = 100, leadership = 80) // (100+80)/2 = 90
        val o2 = createOfficer(id = 2, command = 60, leadership = 40)  // (60+40)/2 = 50
        // Average across officers: (90 + 50) / 2 = 70
        val power = StrategicPowerScorer.evaluatePower(emptyList(), listOf(o1, o2), planet)

        assertEquals(70.0, power.commanderScore, 0.0001)
        // No fleets, no defense, only commander contribution: 70 * 30 = 2100
        assertTrue(power.compositeScore > 2099.0 && power.compositeScore < 2101.0)
    }
}
