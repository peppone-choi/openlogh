package com.openlogh.engine

import com.openlogh.entity.*
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
            intel = 80,
            politics = 80,
            gold = 5000,
            rice = 5000,
            officerLevel = 0,
        )

        harness.putWorld(world)
        harness.putFaction(nation)
        harness.putPlanet(city)
        harness.putOfficer(general)

        harness.queueOfficerTurn(officerId = general.id, actionCode = "농지개간", turnIdx = 0)
        harness.queueOfficerTurn(officerId = general.id, actionCode = "농지개간", turnIdx = 1)
        harness.queueOfficerTurn(officerId = general.id, actionCode = "상업투자", turnIdx = 2)
        harness.queueOfficerTurn(officerId = general.id, actionCode = "치안강화", turnIdx = 3)
        harness.queueOfficerTurn(officerId = general.id, actionCode = "모병", turnIdx = 4)

        repeat(5) {
            markTickReady(world)
            harness.turnService.processWorld(world)
        }

        assertTrue(harness.officerTurnsFor(general.id).isEmpty())
        assertEquals(6, world.currentMonth.toInt())
    }

    @Test
    fun `multi-general in same city each execute independently`() {
        val harness = InMemoryTurnHarness()
        val world = baseWorld()
        val nation = baseNation()
        val city = baseCity()
        val chojo = baseGeneral(id = 1, name = "조조", intel = 90)
        val yubi = baseGeneral(id = 2, name = "유비", strength = 90, crew = 300)

        harness.putWorld(world)
        harness.putFaction(nation)
        harness.putPlanet(city)
        harness.putOfficer(chojo)
        harness.putOfficer(yubi)

        val initialAgri = city.agri
        val initialTrain = yubi.train.toInt()

        harness.queueOfficerTurn(officerId = chojo.id, actionCode = "농지개간", turnIdx = 0)
        harness.queueOfficerTurn(officerId = yubi.id, actionCode = "훈련", turnIdx = 0)

        markTickReady(world)
        harness.turnService.processWorld(world)

        assertTrue(city.agri >= initialAgri)
        assertTrue(yubi.train.toInt() >= initialTrain)
        assertTrue(harness.officerTurnsFor(chojo.id).isEmpty())
        assertTrue(harness.officerTurnsFor(yubi.id).isEmpty())
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

        harness.queueFactionTurn(factionId = nation.id, officerLevel = 5, actionCode = "Nation휴식", turnIdx = 0)
        harness.queueOfficerTurn(officerId = chief.id, actionCode = "휴식", turnIdx = 0)

        markTickReady(world, ticksBehind = 3)
        harness.turnService.processWorld(world)

        assertTrue(harness.factionTurnsFor(nation.id, 5).isEmpty())
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
            harness.queueOfficerTurn(officerId = general.id, actionCode = "휴식", turnIdx = idx.toShort())
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
        val city = baseCity(pop = 10000)
        val general = baseGeneral(
            leadership = 80,
            strength = 60,
            gold = 5000,
            rice = 5000,
        )

        harness.putWorld(world)
        harness.putFaction(nation)
        harness.putPlanet(city)
        harness.putOfficer(general)

        harness.queueOfficerTurn(officerId = general.id, actionCode = "모병", turnIdx = 0)
        harness.queueOfficerTurn(officerId = general.id, actionCode = "훈련", turnIdx = 1)
        harness.queueOfficerTurn(officerId = general.id, actionCode = "훈련", turnIdx = 2)
        harness.queueOfficerTurn(officerId = general.id, actionCode = "사기진작", turnIdx = 3)

        repeat(4) {
            markTickReady(world)
            harness.turnService.processWorld(world)
        }

        assertTrue(harness.officerTurnsFor(general.id).isEmpty())
        assertNotNull(general.lastTurn)
    }

    private fun baseWorld(year: Int = 200, month: Int = 1): WorldState {
        return WorldState(
            id = 1,
            name = "test-world",
            scenarioCode = "test",
            currentYear = year.toShort(),
            currentMonth = month.toShort(),
            tickSeconds = 60,
            updatedAt = OffsetDateTime.now().minusSeconds(90),
        )
    }

    private fun baseNation(strategicCmdLimit: Int = 10): Nation {
        return Nation(
            id = 1,
            worldId = 1,
            name = "위",
            color = "#ffffff",
            level = 3,
            strategicCmdLimit = strategicCmdLimit.toShort(),
        )
    }

    private fun baseCity(pop: Int = 10000): City {
        return City(
            id = 1,
            worldId = 1,
            name = "낙양",
            level = 5,
            nationId = 1,
            supplyState = 1,
            frontState = 0,
            pop = pop,
            popMax = 50000,
            agri = 100,
            agriMax = 1000,
            comm = 100,
            commMax = 1000,
            secu = 100,
            secuMax = 1000,
            trust = 100,
            def = 100,
            defMax = 1000,
            wall = 100,
            wallMax = 1000,
        )
    }

    private fun baseGeneral(
        id: Long = 1,
        name: String = "조조",
        nationId: Long = 1,
        cityId: Long = 1,
        officerLevel: Int = 0,
        leadership: Int = 50,
        strength: Int = 50,
        intel: Int = 50,
        politics: Int = 50,
        gold: Int = 1000,
        rice: Int = 1000,
        crew: Int = 0,
    ): General {
        return General(
            id = id,
            worldId = 1,
            name = name,
            nationId = nationId,
            cityId = cityId,
            officerLevel = officerLevel.toShort(),
            leadership = leadership.toShort(),
            strength = strength.toShort(),
            intel = intel.toShort(),
            politics = politics.toShort(),
            gold = gold,
            rice = rice,
            crew = crew,
            npcState = 0,
            turnTime = OffsetDateTime.now().minusSeconds(1200),
        )
    }

    private fun markTickReady(world: WorldState, ticksBehind: Int = 1) {
        val seconds = world.tickSeconds.toLong() * ticksBehind + 10
        world.updatedAt = OffsetDateTime.now().minusSeconds(seconds)
    }
}
