package com.openlogh.engine.war

import com.openlogh.entity.Planet
import com.openlogh.entity.Officer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import kotlin.random.Random

class BattleEngineWithPhasesTest {

    private lateinit var engine: BattleEngine

    @BeforeEach
    fun setUp() {
        engine = BattleEngine()
    }

    private fun createGeneral(
        id: Long = 1,
        factionId: Long = 1,
        leadership: Short = 50,
        command: Short = 50,
        intelligence: Short = 50,
        ships: Int = 1000,
        training: Short = 80,
        morale: Short = 80,
        funds: Int = 1000,
        supplies: Int = 5000,
    ): Officer = Officer(
        id = id,
        sessionId = 1,
        name = "장수$id",
        factionId = factionId,
        planetId = 1,
        leadership = leadership,
        command = command,
        intelligence = intelligence,
        ships = ships,
        shipClass = 0,
        training = training,
        morale = morale,
        funds = funds,
        supplies = supplies,
        experience = 1000,
        dedication = 1000,
        specialCode = "None",
        special2Code = "None",
        turnTime = OffsetDateTime.now(),
    )

    private fun createCity(
        factionId: Long = 2,
        orbitalDefense: Int = 500,
        fortress: Int = 500,
    ): Planet = Planet(
        id = 1,
        sessionId = 1,
        name = "테스트도시",
        factionId = factionId,
        orbitalDefense = orbitalDefense,
        orbitalDefenseMax = 1000,
        fortress = fortress,
        fortressMax = 1000,
        population = 10000,
        populationMax = 50000,
    )

    @Test
    fun `resolveBattleWithPhases returns phase details`() {
        val rng = Random(42)
        val attackerGeneral = createGeneral(id = 1, factionId = 1, command = 70, leadership = 70, ships = 3000, supplies = 30000)
        val defenderGeneral = createGeneral(id = 2, factionId = 2, command = 50, leadership = 50, ships = 2000, supplies = 20000)
        val city = createCity(factionId = 2)

        val attacker = WarUnitOfficer(attackerGeneral)
        val defender = WarUnitOfficer(defenderGeneral)

        val withPhases = engine.resolveBattleWithPhases(attacker, listOf(defender), city, rng)

        // Phase details must be non-empty
        assertTrue(withPhases.phaseDetails.isNotEmpty(), "phases should not be empty")

        // Each phase must have valid fields
        for (detail in withPhases.phaseDetails) {
            assertTrue(detail.phase >= 0, "phase index must be >= 0")
            assertTrue(detail.attackerHp >= 0, "attackerHp must be >= 0")
            assertTrue(detail.defenderHp >= 0, "defenderHp must be >= 0")
        }

        // The embedded BattleResult must match a fresh resolveBattle call with the same seed
        val attacker2 = WarUnitOfficer(createGeneral(id = 1, factionId = 1, command = 70, leadership = 70, ships = 3000, supplies = 30000))
        val defender2 = WarUnitOfficer(createGeneral(id = 2, factionId = 2, command = 50, leadership = 50, ships = 2000, supplies = 20000))
        val city2 = createCity(factionId = 2)
        val plainResult = engine.resolveBattle(attacker2, listOf(defender2), city2, Random(42))

        assertEquals(plainResult.attackerWon, withPhases.battleResult.attackerWon, "winner should match resolveBattle")
    }
}
