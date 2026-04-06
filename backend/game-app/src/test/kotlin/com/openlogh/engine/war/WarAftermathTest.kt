package com.openlogh.engine.war

import com.openlogh.entity.Planet
import com.openlogh.entity.Officer
import com.openlogh.entity.Faction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class WarAftermathTest {

    private val service = WarAftermath()

    private fun buildConfig() = WarAftermathConfig(
        initialNationGenLimit = 1,
        techLevelIncYear = 5,
        initialAllowedTechLevel = 1,
        maxTechLevel = 12,
        defaultCityWall = 1000,
        baseGold = 0,
        baseRice = 0,
        castleCrewTypeId = 1000,
    )

    private fun buildCity(id: Long, factionId: Long): Planet {
        return Planet(
            id = id,
            sessionId = 1,
            name = "City$id",
            level = 2,
            factionId = factionId,
            population = 10_000,
            populationMax = 10_000,
            production = 1000,
            productionMax = 1000,
            commerce = 1000,
            commerceMax = 1000,
            security = 1000,
            securityMax = 1000,
            orbitalDefense = 100,
            orbitalDefenseMax = 200,
            fortress = 100,
            fortressMax = 200,
            supplyState = 1,
            frontState = 0,
            meta = mutableMapOf(),
        )
    }

    private fun buildNation(id: Long): Faction {
        return Faction(
            id = id,
            sessionId = 1,
            name = "Nation$id",
            color = "#000000",
            capitalPlanetId = id,
            chiefOfficerId = 0,
            funds = 1000,
            supplies = 1000,
            militaryPower = 0,
            factionRank = 1,
            factionType = "test",
            techLevel = 1000f,
            meta = mutableMapOf("tech" to 1000),
        )
    }

    private fun buildGeneral(id: Long, factionId: Long, planetId: Long): Officer {
        return Officer(
            id = id,
            sessionId = 1,
            name = "General$id",
            factionId = factionId,
            planetId = planetId,
            fleetId = 0,
            leadership = 70,
            command = 70,
            intelligence = 70,
            experience = 100,
            dedication = 100,
            officerLevel = 3,
            injury = 0,
            funds = 1000,
            supplies = 1000,
            ships = 1000,
            shipClass = 1000,
            training = 80,
            morale = 80,
            age = 20,
            npcState = 0,
            killTurn = 24,
            meta = mutableMapOf(),
        )
    }

    @Test
    fun `updates tech and diplomacy deltas`() {
        val attackerNation = buildNation(1)
        val defenderNation = buildNation(2)
        val attackerCity = buildCity(1, 1)
        val defenderCity = buildCity(2, 2)
        val attacker = buildGeneral(1, 1, 1)

        val outcome = service.resolveWarAftermath(
            WarAftermathInput(
                battle = WarBattleOutcome(
                    attacker = attacker,
                    defenders = emptyList(),
                    defenderCity = defenderCity,
                    logs = emptyList(),
                    conquered = false,
                    reports = listOf(
                        WarUnitReport(
                            id = attacker.id,
                            type = "general",
                            name = attacker.name,
                            isAttacker = true,
                            killed = 100,
                            dead = 50,
                        ),
                    ),
                ),
                attackerNation = attackerNation,
                defenderNation = defenderNation,
                attackerCity = attackerCity,
                defenderCity = defenderCity,
                nations = listOf(attackerNation, defenderNation),
                cities = listOf(attackerCity, defenderCity),
                generals = listOf(attacker),
                config = buildConfig(),
                time = WarTimeContext(
                    year = 200,
                    month = 1,
                    startYear = 180,
                ),
            ),
        )

        assertEquals(1001, attackerNation.meta["tech"])
        assertEquals(1001, defenderNation.meta["tech"])
        assertEquals(2, outcome.diplomacyDeltas.size)
        assertEquals(60, attackerCity.meta["dead"])
        assertEquals(90, defenderCity.meta["dead"])
    }

    @Test
    fun `applies conquest collapse rewards`() {
        val attackerNation = buildNation(1)
        val defenderNation = buildNation(2).apply {
            funds = 5000
            supplies = 6000
        }
        val attackerCity = buildCity(1, 1)
        val defenderCity = buildCity(2, 2).apply {
            meta["conflict"] = "{\"1\":100}"
        }
        val attacker = buildGeneral(1, 1, 1)
        val defender = buildGeneral(2, 2, 2)

        val outcome = service.resolveWarAftermath(
            WarAftermathInput(
                battle = WarBattleOutcome(
                    attacker = attacker,
                    defenders = listOf(defender),
                    defenderCity = defenderCity,
                    logs = emptyList(),
                    conquered = true,
                    reports = listOf(
                        WarUnitReport(
                            id = attacker.id,
                            type = "general",
                            name = attacker.name,
                            isAttacker = true,
                            killed = 10,
                            dead = 5,
                        ),
                        WarUnitReport(
                            id = defenderCity.id,
                            type = "city",
                            name = defenderCity.name,
                            isAttacker = false,
                            killed = 0,
                            dead = 0,
                        ),
                    ),
                ),
                attackerNation = attackerNation,
                defenderNation = defenderNation,
                attackerCity = attackerCity,
                defenderCity = defenderCity,
                nations = listOf(attackerNation, defenderNation),
                cities = listOf(attackerCity, defenderCity),
                generals = listOf(attacker, defender),
                config = buildConfig(),
                time = WarTimeContext(
                    year = 200,
                    month = 1,
                    startYear = 180,
                ),
                rng = Random(0),
            ),
        )

        assertEquals(true, outcome.conquest?.nationCollapsed)
        assertTrue(attackerNation.funds > 1000)
        assertTrue(attackerNation.supplies > 1000)
        assertTrue(defender.experience < 100)
        assertTrue(defender.dedication < 100)
        assertEquals(attackerNation.id, defenderCity.factionId)
        assertEquals("{}", defenderCity.meta["conflict"])
    }
}
