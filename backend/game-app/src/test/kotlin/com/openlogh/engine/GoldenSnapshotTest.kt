package com.openlogh.engine

import com.openlogh.entity.Planet
import com.openlogh.entity.Officer
import com.openlogh.entity.Faction
import com.openlogh.entity.SessionState
import com.openlogh.test.InMemoryTurnHarness
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

class GoldenSnapshotTest {

    @Test
    fun `multi-turn snapshot is deterministic and matches golden values`() {
        val left = runScenario()
        val right = runScenario()

        assertEquals(left, right)
        assertEquals(expectedSnapshot(), left)
    }

    private fun runScenario(): Snapshot {
        val harness = InMemoryTurnHarness()
        val world = SessionState(
            id = 1,
            name = "golden-world",
            scenarioCode = "test",
            currentYear = 200,
            currentMonth = 1,
            tickSeconds = 60,
            updatedAt = OffsetDateTime.now().minusSeconds(90),
        )

        val nationWei = Faction(
            id = 1,
            sessionId = 1,
            name = "위",
            color = "#111111",
            level = 3,
            funds = 10000,
            supplies = 10000,
            strategicCmdLimit = 0,
        )
        val nationShu = Faction(
            id = 2,
            sessionId = 1,
            name = "촉",
            color = "#228833",
            level = 3,
            funds = 9000,
            supplies = 8000,
            strategicCmdLimit = 0,
        )

        val cityWei = createCity(id = 1, factionId = 1, name = "허창")
        val cityShu = createCity(id = 2, factionId = 2, name = "성도")
        val cityNeutral = createCity(id = 3, factionId = 0, name = "중립")

        val g1 = createGeneral(id = 1, factionId = 1, planetId = 1, name = "조조", leadership = 80, ships = 200, training = 60, morale = 70)
        val g2 = createGeneral(id = 2, factionId = 1, planetId = 1, name = "하후돈", leadership = 60, ships = 300, training = 40, morale = 65)
        val g3 = createGeneral(id = 3, factionId = 2, planetId = 2, name = "유비", leadership = 75, ships = 250, training = 55, morale = 75)
        val g4 = createGeneral(id = 4, factionId = 2, planetId = 2, name = "관우", leadership = 50, ships = 400, training = 30, morale = 60)

        harness.putWorld(world)
        harness.putFaction(nationWei)
        harness.putFaction(nationShu)
        harness.putPlanet(cityWei)
        harness.putPlanet(cityShu)
        harness.putPlanet(cityNeutral)
        harness.putOfficer(g1)
        harness.putOfficer(g2)
        harness.putOfficer(g3)
        harness.putOfficer(g4)

        harness.queueGeneralTurn(officerId = 1, actionCode = "훈련")
        harness.queueGeneralTurn(officerId = 2, actionCode = "훈련")
        harness.queueGeneralTurn(officerId = 3, actionCode = "훈련")
        harness.queueGeneralTurn(officerId = 4, actionCode = "훈련")

        harness.turnService.processWorld(world)

        assertTrue(harness.generalTurnsFor(1).isEmpty())
        assertTrue(harness.generalTurnsFor(2).isEmpty())
        assertTrue(harness.generalTurnsFor(3).isEmpty())
        assertTrue(harness.generalTurnsFor(4).isEmpty())

        val generals = harness.officerRepository.findBySessionId(1).sortedBy { it.id }
        val nations = harness.factionRepository.findBySessionId(1).sortedBy { it.id }
        val cities = harness.planetRepository.findBySessionId(1).sortedBy { it.id }

        return Snapshot(
            year = world.currentYear.toInt(),
            month = world.currentMonth.toInt(),
            generals = generals.map {
                GeneralState(
                    id = it.id,
                    ships = it.ships,
                    training = it.training.toInt(),
                    morale = it.morale.toInt(),
                    experience = it.experience,
                    dedication = it.dedication,
                )
            },
            nations = nations.map {
                NationState(
                    id = it.id,
                    funds = it.funds,
                    supplies = it.supplies,
                    strategicCmdLimit = it.strategicCmdLimit.toInt(),
                )
            },
            cities = cities.map {
                CityState(
                    id = it.id,
                    factionId = it.factionId,
                    production = it.production,
                    commerce = it.commerce,
                    security = it.security,
                    population = it.population,
                )
            },
        )
    }

    private fun expectedSnapshot(): Snapshot {
        return Snapshot(
            year = 200,
            month = 2,
            generals = listOf(
                GeneralState(id = 1, ships = 199, training = 100, morale = 70, experience = 100, dedication = 70),
                GeneralState(id = 2, ships = 299, training = 100, morale = 65, experience = 100, dedication = 70),
                GeneralState(id = 3, ships = 249, training = 100, morale = 75, experience = 100, dedication = 70),
                GeneralState(id = 4, ships = 399, training = 100, morale = 60, experience = 100, dedication = 70),
            ),
            nations = listOf(
                NationState(id = 1, funds = 10000, supplies = 10000, strategicCmdLimit = 0),
                NationState(id = 2, funds = 9000, supplies = 8000, strategicCmdLimit = 0),
            ),
            cities = listOf(
                CityState(id = 1, factionId = 1, production = 500, commerce = 500, security = 500, population = 10000),
                CityState(id = 2, factionId = 2, production = 500, commerce = 500, security = 500, population = 10000),
                CityState(id = 3, factionId = 0, production = 500, commerce = 500, security = 500, population = 10000),
            ),
        )
    }

    private fun createCity(id: Long, factionId: Long, name: String): Planet {
        return Planet(
            id = id,
            sessionId = 1,
            name = name,
            factionId = factionId,
            supplyState = 1,
            frontState = 0,
            population = 10000,
            populationMax = 50000,
            production = 500,
            productionMax = 1000,
            commerce = 500,
            commerceMax = 1000,
            security = 500,
            securityMax = 1000,
            approval = 80,
            orbitalDefense = 500,
            orbitalDefenseMax = 1000,
            fortress = 500,
            fortressMax = 1000,
        )
    }

    private fun createGeneral(
        id: Long,
        factionId: Long,
        planetId: Long,
        name: String,
        leadership: Short,
        ships: Int,
        training: Short,
        morale: Short,
    ): Officer {
        return Officer(
            id = id,
            sessionId = 1,
            name = name,
            factionId = factionId,
            planetId = planetId,
            leadership = leadership,
            command = 70,
            intelligence = 70,
            politics = 60,
            administration = 60,
            ships = ships,
            shipClass = 0,
            training = training,
            morale = morale,
            funds = 500,
            supplies = 500,
            npcState = 0,
            turnTime = OffsetDateTime.now().minusSeconds(1200),
        )
    }

    private data class Snapshot(
        val year: Int,
        val month: Int,
        val generals: List<GeneralState>,
        val nations: List<NationState>,
        val cities: List<CityState>,
    )

    private data class GeneralState(
        val id: Long,
        val ships: Int,
        val training: Int,
        val morale: Int,
        val experience: Int,
        val dedication: Int,
    )

    private data class NationState(
        val id: Long,
        val funds: Int,
        val supplies: Int,
        val strategicCmdLimit: Int,
    )

    private data class CityState(
        val id: Long,
        val factionId: Long,
        val production: Int,
        val commerce: Int,
        val security: Int,
        val population: Int,
    )
}
