package com.openlogh.engine

import com.openlogh.entity.Planet
import com.openlogh.entity.Officer
import com.openlogh.entity.Faction
import com.openlogh.entity.SessionState
import com.openlogh.test.InMemoryTurnHarness
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import java.time.OffsetDateTime

class InMemoryTurnHarnessIntegrationTest {

    @Test
    fun `tier2 reserved turn is consumed and world advances`() {
        val harness = InMemoryTurnHarness()
        val world = SessionState(
            id = 1,
            name = "test-world",
            scenarioCode = "test",
            currentYear = 200,
            currentMonth = 1,
            tickSeconds = 60,
            updatedAt = OffsetDateTime.now().minusSeconds(90),
        )
        val nation = Faction(
            id = 1,
            sessionId = 1,
            name = "위",
            color = "#ffffff",
            factionRank = 3,
            strategicCmdLimit = 10,
        )
        val city = Planet(
            id = 1,
            sessionId = 1,
            name = "낙양",
            level = 5,
            factionId = 1,
            supplyState = 1,
            frontState = 0,
            population = 10000,
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
        val general = Officer(
            id = 1,
            sessionId = 1,
            name = "조조",
            factionId = 1,
            planetId = 1,
            funds = 1000,
            supplies = 1000,
            npcState = 0,
            turnTime = world.updatedAt.minusSeconds(600),
        )

        harness.putWorld(world)
        harness.putFaction(nation)
        harness.putPlanet(city)
        harness.putOfficer(general)
        harness.queueGeneralTurn(generalId = 1, actionCode = "휴식")

        harness.turnService.processWorld(world)

        assertTrue(harness.generalTurnsFor(1).isEmpty())
        assertEquals(2, world.currentMonth.toInt())
    }

    @Test
    fun `tier3 monthly integration consumes nation turn and decrements strategic limit`() {
        val harness = InMemoryTurnHarness()
        val world = SessionState(
            id = 1,
            name = "test-world",
            scenarioCode = "test",
            currentYear = 200,
            currentMonth = 10,
            tickSeconds = 60,
            updatedAt = OffsetDateTime.now().minusSeconds(190),
        )
        val nation = Faction(
            id = 1,
            sessionId = 1,
            name = "촉",
            color = "#00ff00",
            factionRank = 4,
            strategicCmdLimit = 5,
        )
        val city = Planet(
            id = 1,
            sessionId = 1,
            name = "성도",
            level = 5,
            factionId = 1,
            supplyState = 1,
            frontState = 0,
            population = 12000,
            populationMax = 50000,
            production = 500,
            productionMax = 1000,
            commerce = 500,
            commerceMax = 1000,
            security = 500,
            securityMax = 1000,
            approval = 90,
            orbitalDefense = 500,
            orbitalDefenseMax = 1000,
            fortress = 500,
            fortressMax = 1000,
        )
        val officer = Officer(
            id = 1,
            sessionId = 1,
            name = "유비",
            factionId = 1,
            planetId = 1,
            officerLevel = 5,
            npcState = 0,
            turnTime = world.updatedAt.minusSeconds(600),
        )

        harness.putWorld(world)
        harness.putFaction(nation)
        harness.putPlanet(city)
        harness.putOfficer(officer)
        harness.queueNationTurn(nationId = 1, officerLevel = 5, actionCode = "Nation휴식")
        harness.queueGeneralTurn(generalId = 1, actionCode = "휴식")

        harness.turnService.processWorld(world)

        assertTrue(harness.nationTurnsFor(1, 5).isEmpty())
        assertEquals(1, world.currentMonth.toInt())
        assertTrue(nation.strategicCmdLimit.toInt() <= 5)
        verify(harness.unificationService, times(3)).checkAndSettleUnification(world)
    }
}
