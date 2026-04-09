package com.openlogh.engine.ai.strategic

import com.openlogh.engine.ai.PersonalityTrait
import com.openlogh.engine.tactical.ai.MissionObjective
import com.openlogh.entity.Fleet
import com.openlogh.entity.Officer
import com.openlogh.entity.Planet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class OperationTargetSelectorTest {

    private fun createFleet(
        id: Long,
        planetId: Long,
        currentUnits: Int,
        factionId: Long = 1,
    ): Fleet = Fleet(
        id = id,
        sessionId = 1,
        leaderOfficerId = 1,
        factionId = factionId,
        name = "F$id",
        unitType = "FLEET",
        maxUnits = 60,
        currentUnits = currentUnits,
        planetId = planetId,
    )

    private fun createOfficer(
        id: Long,
        planetId: Long,
        command: Short = 50,
        leadership: Short = 50,
        intelligence: Short = 50,
        factionId: Long = 1,
    ): Officer = Officer(
        id = id,
        sessionId = 1,
        name = "O$id",
        factionId = factionId,
        planetId = planetId,
        command = command,
        leadership = leadership,
        intelligence = intelligence,
    )

    private fun createPlanet(
        id: Long,
        factionId: Long,
        frontState: Short = 1,
        production: Int = 0,
        commerce: Int = 0,
        tradeRoute: Int = 0,
        population: Int = 0,
        orbitalDefense: Int = 0,
        fortress: Int = 0,
    ): Planet = Planet(
        id = id,
        sessionId = 1,
        name = "P$id",
        factionId = factionId,
        frontState = frontState,
        production = production,
        commerce = commerce,
        tradeRoute = tradeRoute,
        population = population,
        orbitalDefense = orbitalDefense,
        fortress = fortress,
    )

    @Test
    fun `weak enemy front-line planet produces a CONQUEST candidate`() {
        // Own planet — strong, defensive
        val ownPlanet = createPlanet(id = 1, factionId = 1, frontState = 1, orbitalDefense = 100, fortress = 100)
        val ownOfficer = createOfficer(id = 10, planetId = 1, command = 80, leadership = 80, intelligence = 80)
        val ownFleet = createFleet(id = 100, planetId = 1, currentUnits = 50)

        // Enemy planet — high strategic value, no defenders
        val enemyPlanet = createPlanet(
            id = 2,
            factionId = 2,
            frontState = 1,
            production = 1000,
            commerce = 1000,
            tradeRoute = 50,
            population = 100000,
        )

        val candidates = OperationTargetSelector.selectTargets(
            ownPlanets = listOf(ownPlanet),
            enemyPlanets = listOf(enemyPlanet),
            ownFleetsByPlanet = mapOf(1L to listOf(ownFleet)),
            enemyFleetsByPlanet = emptyMap(),
            ownOfficersByPlanet = mapOf(1L to listOf(ownOfficer)),
            enemyOfficersByPlanet = emptyMap(),
            sovereignPersonality = PersonalityTrait.BALANCED,
            friendlyOfficers = listOf(ownOfficer),
            rng = Random(42),
        )

        val conquest = candidates.firstOrNull { it.objective == MissionObjective.CONQUEST }
        assertNotNull(conquest, "Expected at least one CONQUEST candidate")
        assertEquals(2L, conquest!!.targetPlanetId)
    }

    @Test
    fun `threatened own front-line planet produces a DEFENSE candidate`() {
        // Own planet — weak, on the front line
        val ownPlanet = createPlanet(id = 1, factionId = 1, frontState = 1, orbitalDefense = 0, fortress = 0)
        val ownOfficer = createOfficer(id = 10, planetId = 1, command = 30, leadership = 30, intelligence = 80)

        // Enemy front-line system with overwhelming power
        val enemyPlanet = createPlanet(id = 2, factionId = 2, frontState = 1)
        val enemyOfficer = createOfficer(id = 20, planetId = 2, command = 100, leadership = 100, factionId = 2)
        val enemyFleet = createFleet(id = 200, planetId = 2, currentUnits = 60, factionId = 2)

        val candidates = OperationTargetSelector.selectTargets(
            ownPlanets = listOf(ownPlanet),
            enemyPlanets = listOf(enemyPlanet),
            ownFleetsByPlanet = emptyMap(),
            enemyFleetsByPlanet = mapOf(2L to listOf(enemyFleet)),
            ownOfficersByPlanet = mapOf(1L to listOf(ownOfficer)),
            enemyOfficersByPlanet = mapOf(2L to listOf(enemyOfficer)),
            sovereignPersonality = PersonalityTrait.BALANCED,
            friendlyOfficers = listOf(ownOfficer), // intel agent at 1, not at 2 — fog applies to enemy
            rng = Random(42),
        )

        val defense = candidates.firstOrNull { it.objective == MissionObjective.DEFENSE }
        assertNotNull(defense, "Expected at least one DEFENSE candidate")
        assertEquals(1L, defense!!.targetPlanetId)
    }

    @Test
    fun `enemy fleet inside own territory produces a SWEEP candidate`() {
        val ownPlanet = createPlanet(id = 1, factionId = 1, frontState = 0)
        val intruder = createFleet(id = 999, planetId = 1, currentUnits = 10, factionId = 2)

        val candidates = OperationTargetSelector.selectTargets(
            ownPlanets = listOf(ownPlanet),
            enemyPlanets = emptyList(),
            ownFleetsByPlanet = emptyMap(),
            enemyFleetsByPlanet = mapOf(1L to listOf(intruder)),
            ownOfficersByPlanet = emptyMap(),
            enemyOfficersByPlanet = emptyMap(),
            sovereignPersonality = PersonalityTrait.BALANCED,
            friendlyOfficers = emptyList(),
            rng = Random(42),
        )

        val sweep = candidates.firstOrNull { it.objective == MissionObjective.SWEEP }
        assertNotNull(sweep, "Expected at least one SWEEP candidate")
        assertEquals(1L, sweep!!.targetPlanetId)
        // estimatedEnemyPower = 10 units * 300 ships = 3000
        assertEquals(3000.0, sweep.estimatedEnemyPower)
    }

    @Test
    fun `AGGRESSIVE sovereign boosts CONQUEST score by 1_5x`() {
        val ownPlanet = createPlanet(id = 1, factionId = 1, frontState = 1, orbitalDefense = 100)
        val ownOfficer = createOfficer(id = 10, planetId = 1, intelligence = 90)
        val enemyPlanet = createPlanet(
            id = 2,
            factionId = 2,
            frontState = 1,
            production = 500,
            commerce = 500,
        )

        val baseline = OperationTargetSelector.selectTargets(
            ownPlanets = listOf(ownPlanet),
            enemyPlanets = listOf(enemyPlanet),
            ownFleetsByPlanet = emptyMap(),
            enemyFleetsByPlanet = emptyMap(),
            ownOfficersByPlanet = mapOf(1L to listOf(ownOfficer)),
            enemyOfficersByPlanet = emptyMap(),
            sovereignPersonality = PersonalityTrait.BALANCED,
            // Agent at the enemy planet so fog noise is disabled and the comparison is deterministic
            friendlyOfficers = listOf(createOfficer(id = 11, planetId = 2, intelligence = 90)),
            rng = Random(42),
        ).first { it.objective == MissionObjective.CONQUEST }

        val boosted = OperationTargetSelector.selectTargets(
            ownPlanets = listOf(ownPlanet),
            enemyPlanets = listOf(enemyPlanet),
            ownFleetsByPlanet = emptyMap(),
            enemyFleetsByPlanet = emptyMap(),
            ownOfficersByPlanet = mapOf(1L to listOf(ownOfficer)),
            enemyOfficersByPlanet = emptyMap(),
            sovereignPersonality = PersonalityTrait.AGGRESSIVE,
            friendlyOfficers = listOf(createOfficer(id = 11, planetId = 2, intelligence = 90)),
            rng = Random(42),
        ).first { it.objective == MissionObjective.CONQUEST }

        assertEquals(baseline.score * OperationTargetSelector.AGGRESSIVE_CONQUEST_BIAS, boosted.score, 0.0001)
        assertTrue(boosted.score > baseline.score)
    }

    @Test
    fun `DEFENSIVE sovereign boosts DEFENSE score by 1_5x`() {
        // Reuse the threatened-own setup
        val ownPlanet = createPlanet(id = 1, factionId = 1, frontState = 1)
        val ownOfficer = createOfficer(id = 10, planetId = 1, command = 30, leadership = 30, intelligence = 80)
        val enemyPlanet = createPlanet(id = 2, factionId = 2, frontState = 1)
        val enemyOfficer = createOfficer(id = 20, planetId = 2, command = 100, leadership = 100, factionId = 2)
        val enemyFleet = createFleet(id = 200, planetId = 2, currentUnits = 60, factionId = 2)
        // Place an intel agent at the enemy planet so fog is disabled and runs are deterministic
        val agent = createOfficer(id = 11, planetId = 2, intelligence = 90)

        val baseline = OperationTargetSelector.selectTargets(
            ownPlanets = listOf(ownPlanet),
            enemyPlanets = listOf(enemyPlanet),
            ownFleetsByPlanet = emptyMap(),
            enemyFleetsByPlanet = mapOf(2L to listOf(enemyFleet)),
            ownOfficersByPlanet = mapOf(1L to listOf(ownOfficer)),
            enemyOfficersByPlanet = mapOf(2L to listOf(enemyOfficer)),
            sovereignPersonality = PersonalityTrait.BALANCED,
            friendlyOfficers = listOf(ownOfficer, agent),
            rng = Random(42),
        ).first { it.objective == MissionObjective.DEFENSE }

        val boosted = OperationTargetSelector.selectTargets(
            ownPlanets = listOf(ownPlanet),
            enemyPlanets = listOf(enemyPlanet),
            ownFleetsByPlanet = emptyMap(),
            enemyFleetsByPlanet = mapOf(2L to listOf(enemyFleet)),
            ownOfficersByPlanet = mapOf(1L to listOf(ownOfficer)),
            enemyOfficersByPlanet = mapOf(2L to listOf(enemyOfficer)),
            sovereignPersonality = PersonalityTrait.DEFENSIVE,
            friendlyOfficers = listOf(ownOfficer, agent),
            rng = Random(42),
        ).first { it.objective == MissionObjective.DEFENSE }

        assertEquals(baseline.score * OperationTargetSelector.DEFENSIVE_DEFENSE_BIAS, boosted.score, 0.0001)
        assertTrue(boosted.score > baseline.score)
    }

    @Test
    fun `non-front-line enemy planets do not produce CONQUEST candidates`() {
        val ownPlanet = createPlanet(id = 1, factionId = 1, frontState = 0)
        val rearEnemy = createPlanet(id = 2, factionId = 2, frontState = 0, production = 9999)

        val candidates = OperationTargetSelector.selectTargets(
            ownPlanets = listOf(ownPlanet),
            enemyPlanets = listOf(rearEnemy),
            ownFleetsByPlanet = emptyMap(),
            enemyFleetsByPlanet = emptyMap(),
            ownOfficersByPlanet = emptyMap(),
            enemyOfficersByPlanet = emptyMap(),
            sovereignPersonality = PersonalityTrait.AGGRESSIVE,
            friendlyOfficers = emptyList(),
            rng = Random(42),
        )

        assertTrue(candidates.none { it.objective == MissionObjective.CONQUEST })
    }
}
