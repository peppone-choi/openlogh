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
            factionRank = 3,
            funds = 10000,
            supplies = 10000,
            strategicCmdLimit = 0,
        )
        val nationShu = Faction(
            id = 2,
            sessionId = 1,
            name = "촉",
            color = "#228833",
            factionRank = 3,
            funds = 9000,
            supplies = 8000,
            strategicCmdLimit = 0,
        )

        val cityWei = createCity(id = 1, nationId = 1, name = "허창")
        val cityShu = createCity(id = 2, nationId = 2, name = "성도")
        val cityNeutral = createCity(id = 3, nationId = 0, name = "중립")

        val g1 = createGeneral(id = 1, nationId = 1, cityId = 1, name = "조조", leadership = 80, crew = 200, train = 60, atmos = 70)
        val g2 = createGeneral(id = 2, nationId = 1, cityId = 1, name = "하후돈", leadership = 60, crew = 300, train = 40, atmos = 65)
        val g3 = createGeneral(id = 3, nationId = 2, cityId = 2, name = "유비", leadership = 75, crew = 250, train = 55, atmos = 75)
        val g4 = createGeneral(id = 4, nationId = 2, cityId = 2, name = "관우", leadership = 50, crew = 400, train = 30, atmos = 60)

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

        harness.queueGeneralTurn(generalId = 1, actionCode = "훈련")
        harness.queueGeneralTurn(generalId = 2, actionCode = "훈련")
        harness.queueGeneralTurn(generalId = 3, actionCode = "훈련")
        harness.queueGeneralTurn(generalId = 4, actionCode = "훈련")

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
                    crew = it.ships,
                    train = it.training.toInt(),
                    atmos = it.morale.toInt(),
                    experience = it.experience,
                    dedication = it.dedication,
                )
            },
            nations = nations.map {
                NationState(
                    id = it.id,
                    gold = it.funds,
                    rice = it.supplies,
                    strategicCmdLimit = it.strategicCmdLimit.toInt(),
                )
            },
            cities = cities.map {
                CityState(
                    id = it.id,
                    nationId = it.factionId,
                    agri = it.production,
                    comm = it.commerce,
                    secu = it.security,
                    pop = it.population,
                )
            },
        )
    }

    private fun expectedSnapshot(): Snapshot {
        return Snapshot(
            year = 200,
            month = 2,
            generals = listOf(
                GeneralState(id = 1, crew = 199, train = 100, atmos = 70, experience = 100, dedication = 70),
                GeneralState(id = 2, crew = 299, train = 100, atmos = 65, experience = 100, dedication = 70),
                GeneralState(id = 3, crew = 249, train = 100, atmos = 75, experience = 100, dedication = 70),
                GeneralState(id = 4, crew = 399, train = 100, atmos = 60, experience = 100, dedication = 70),
            ),
            nations = listOf(
                NationState(id = 1, gold = 10000, rice = 10000, strategicCmdLimit = 0),
                NationState(id = 2, gold = 9000, rice = 8000, strategicCmdLimit = 0),
            ),
            cities = listOf(
                CityState(id = 1, nationId = 1, agri = 500, comm = 500, secu = 500, pop = 10000),
                CityState(id = 2, nationId = 2, agri = 500, comm = 500, secu = 500, pop = 10000),
                CityState(id = 3, nationId = 0, agri = 500, comm = 500, secu = 500, pop = 10000),
            ),
        )
    }

    private fun createCity(id: Long, nationId: Long, name: String): Planet {
        return Planet(
            id = id,
            sessionId = 1,
            name = name,
            factionId = nationId,
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
        nationId: Long,
        cityId: Long,
        name: String,
        leadership: Short,
        crew: Int,
        train: Short,
        atmos: Short,
    ): Officer {
        return Officer(
            id = id,
            sessionId = 1,
            name = name,
            factionId = nationId,
            planetId = cityId,
            leadership = leadership,
            command = 70,
            intelligence = 70,
            politics = 60,
            administration = 60,
            ships = crew,
            shipClass = 0,
            training = train,
            morale = atmos,
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
        val crew: Int,
        val train: Int,
        val atmos: Int,
        val experience: Int,
        val dedication: Int,
    )

    private data class NationState(
        val id: Long,
        val gold: Int,
        val rice: Int,
        val strategicCmdLimit: Int,
    )

    private data class CityState(
        val id: Long,
        val nationId: Long,
        val agri: Int,
        val comm: Int,
        val secu: Int,
        val pop: Int,
    )
}
