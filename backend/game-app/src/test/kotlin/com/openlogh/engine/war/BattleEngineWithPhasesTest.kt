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
        nationId: Long = 1,
        leadership: Short = 50,
        strength: Short = 50,
        intel: Short = 50,
        crew: Int = 1000,
        train: Short = 80,
        atmos: Short = 80,
        gold: Int = 1000,
        rice: Int = 5000,
    ): Officer = Officer(
        id = id,
        sessionId = 1,
        name = "장수$id",
        factionId = nationId,
        planetId = 1,
        leadership = leadership,
        command = strength,
        intelligence = intel,
        ships = crew,
        shipClass = 0,
        training = train,
        morale = atmos,
        funds = gold,
        supplies = rice,
        experience = 1000,
        dedication = 1000,
        specialCode = "None",
        special2Code = "None",
        turnTime = OffsetDateTime.now(),
    )

    private fun createCity(
        nationId: Long = 2,
        def: Int = 500,
        wall: Int = 500,
    ): Planet = Planet(
        id = 1,
        sessionId = 1,
        name = "테스트도시",
        factionId = nationId,
        orbitalDefense = def,
        orbitalDefenseMax = 1000,
        fortress = wall,
        fortressMax = 1000,
        population = 10000,
        populationMax = 50000,
    )

    @Test
    fun `resolveBattleWithPhases returns phase details`() {
        val rng = Random(42)
        val attackerGeneral = createGeneral(id = 1, nationId = 1, strength = 70, leadership = 70, crew = 3000, rice = 30000)
        val defenderGeneral = createGeneral(id = 2, nationId = 2, strength = 50, leadership = 50, crew = 2000, rice = 20000)
        val city = createCity(nationId = 2)

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
        val city2 = createCity(nationId = 2)
        val plainResult = engine.resolveBattle(attacker2, listOf(defender2), city2, Random(42))

        assertEquals(plainResult.attackerWon, withPhases.battleResult.attackerWon, "winner should match resolveBattle")
    }
}
