package com.openlogh.engine

import com.openlogh.entity.Planet
import com.openlogh.entity.Officer
import com.openlogh.entity.Faction
import com.openlogh.entity.SessionState
import com.openlogh.test.InMemoryTurnHarness
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

class GameplayIntegrationTest {

    @Test
    fun `multi-turn domestic cycle advances city stats cumulatively`() {
        val harness = InMemoryTurnHarness()
        val world = baseWorld()
        val nation = baseNation()
        val city = baseCity()
        val general = baseGeneral(
            intelligence = 80,
            politics = 80,
            funds = 5000,
            supplies = 5000,
            officerLevel = 0,
        )

        harness.putWorld(world)
        harness.putFaction(nation)
        harness.putPlanet(city)
        harness.putOfficer(general)

        harness.queueGeneralTurn(officerId = general.id, actionCode = "농지개간", turnIdx = 0)
        harness.queueGeneralTurn(officerId = general.id, actionCode = "농지개간", turnIdx = 1)
        harness.queueGeneralTurn(officerId = general.id, actionCode = "상업투자", turnIdx = 2)
        harness.queueGeneralTurn(officerId = general.id, actionCode = "치안강화", turnIdx = 3)
        harness.queueGeneralTurn(officerId = general.id, actionCode = "모병", turnIdx = 4)

        repeat(5) {
            markTickReady(world)
            harness.turnService.processWorld(world)
        }

        assertTrue(harness.generalTurnsFor(general.id).isEmpty())
        assertEquals(6, world.currentMonth.toInt())
    }

    @Test
    fun `multi-general in same city each execute independently`() {
        val harness = InMemoryTurnHarness()
        val world = baseWorld()
        val nation = baseNation()
        val city = baseCity()
        val chojo = baseGeneral(id = 1, name = "조조", intelligence = 90)
        val yubi = baseGeneral(id = 2, name = "유비", command = 90, ships = 300)

        harness.putWorld(world)
        harness.putFaction(nation)
        harness.putPlanet(city)
        harness.putOfficer(chojo)
        harness.putOfficer(yubi)

        val initialAgri = city.production
        val initialTrain = yubi.training.toInt()

        harness.queueGeneralTurn(officerId = chojo.id, actionCode = "농지개간", turnIdx = 0)
        harness.queueGeneralTurn(officerId = yubi.id, actionCode = "훈련", turnIdx = 0)

        markTickReady(world)
        harness.turnService.processWorld(world)

        assertTrue(city.production >= initialAgri)
        assertTrue(yubi.training.toInt() >= initialTrain)
        assertTrue(harness.generalTurnsFor(chojo.id).isEmpty())
        assertTrue(harness.generalTurnsFor(yubi.id).isEmpty())
    }

    @Test
    fun `nation command pipeline processes officer-level turns`() {
        val harness = InMemoryTurnHarness()
        val world = baseWorld()
        val nation = baseNation(strategicCmdLimit = 5)
        val city = baseCity()
        val chief = baseGeneral(officerLevel = 5)

        harness.putWorld(world)
        harness.putFaction(nation)
        harness.putPlanet(city)
        harness.putOfficer(chief)

        harness.queueNationTurn(factionId = nation.id, officerLevel = 5, actionCode = "Nation휴식", turnIdx = 0)
        harness.queueGeneralTurn(officerId = chief.id, actionCode = "휴식", turnIdx = 0)

        markTickReady(world, ticksBehind = 3)
        harness.turnService.processWorld(world)

        assertTrue(harness.nationTurnsFor(nation.id, 5).isEmpty())
        assertTrue(nation.strategicCmdLimit.toInt() <= 5)
    }

    @Test
    fun `twelve-month cycle rolls year correctly`() {
        val harness = InMemoryTurnHarness()
        val world = baseWorld(year = 200, month = 1)
        val nation = baseNation()
        val city = baseCity()
        val general = baseGeneral()

        harness.putWorld(world)
        harness.putFaction(nation)
        harness.putPlanet(city)
        harness.putOfficer(general)

        repeat(12) { idx ->
            harness.queueGeneralTurn(officerId = general.id, actionCode = "휴식", turnIdx = idx.toShort())
        }

        repeat(12) {
            markTickReady(world)
            harness.turnService.processWorld(world)
        }

        assertEquals(201, world.currentYear.toInt())
        assertEquals(1, world.currentMonth.toInt())
    }

    @Test
    fun `recruit then train sequence produces trained soldiers`() {
        val harness = InMemoryTurnHarness()
        val world = baseWorld()
        val nation = baseNation()
        val city = baseCity(population = 10000)
        val general = baseGeneral(
            leadership = 80,
            command = 60,
            funds = 5000,
            supplies = 5000,
        )

        harness.putWorld(world)
        harness.putFaction(nation)
        harness.putPlanet(city)
        harness.putOfficer(general)

        harness.queueGeneralTurn(officerId = general.id, actionCode = "모병", turnIdx = 0)
        harness.queueGeneralTurn(officerId = general.id, actionCode = "훈련", turnIdx = 1)
        harness.queueGeneralTurn(officerId = general.id, actionCode = "훈련", turnIdx = 2)
        harness.queueGeneralTurn(officerId = general.id, actionCode = "사기진작", turnIdx = 3)

        repeat(4) {
            markTickReady(world)
            harness.turnService.processWorld(world)
        }

        assertTrue(harness.generalTurnsFor(general.id).isEmpty())
        assertNotNull(general.lastTurn)
    }

    private fun baseWorld(year: Int = 200, month: Int = 1): SessionState {
        return SessionState(
            id = 1,
            name = "test-world",
            scenarioCode = "test",
            currentYear = year.toShort(),
            currentMonth = month.toShort(),
            tickSeconds = 60,
            updatedAt = OffsetDateTime.now().minusSeconds(90),
        )
    }

    private fun baseNation(strategicCmdLimit: Int = 10): Faction {
        return Faction(
            id = 1,
            sessionId = 1,
            name = "위",
            color = "#ffffff",
            level = 3,
            strategicCmdLimit = strategicCmdLimit.toShort(),
        )
    }

    private fun baseCity(population: Int = 10000): Planet {
        return Planet(
            id = 1,
            sessionId = 1,
            name = "낙양",
            level = 5,
            factionId = 1,
            supplyState = 1,
            frontState = 0,
            population = population,
            populationMax = 50000,
            production = 100,
            productionMax = 1000,
            commerce = 100,
            commerceMax = 1000,
            security = 100,
            securityMax = 1000,
            approval = 100,
            orbitalDefense = 100,
            orbitalDefenseMax = 1000,
            fortress = 100,
            fortressMax = 1000,
        )
    }

    private fun baseGeneral(
        id: Long = 1,
        name: String = "조조",
        factionId: Long = 1,
        planetId: Long = 1,
        officerLevel: Int = 0,
        leadership: Int = 50,
        command: Int = 50,
        intelligence: Int = 50,
        politics: Int = 50,
        funds: Int = 1000,
        supplies: Int = 1000,
        ships: Int = 0,
    ): Officer {
        return Officer(
            id = id,
            sessionId = 1,
            name = name,
            factionId = factionId,
            planetId = planetId,
            officerLevel = officerLevel.toShort(),
            leadership = leadership.toShort(),
            command = command.toShort(),
            intelligence = intelligence.toShort(),
            politics = politics.toShort(),
            funds = funds,
            supplies = supplies,
            ships = ships,
            npcState = 0,
            turnTime = OffsetDateTime.now().minusSeconds(1200),
        )
    }

    private fun markTickReady(world: SessionState, ticksBehind: Int = 1) {
        val seconds = world.tickSeconds.toLong() * ticksBehind + 10
        world.updatedAt = OffsetDateTime.now().minusSeconds(seconds)
    }
}
