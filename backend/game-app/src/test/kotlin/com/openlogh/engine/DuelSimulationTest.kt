package com.openlogh.engine

import com.openlogh.entity.Planet
import com.openlogh.entity.Officer
import com.openlogh.entity.Faction
import com.openlogh.entity.SessionState
import com.openlogh.model.ScenarioData
import com.openlogh.service.ScenarioService
import com.openlogh.test.InMemoryTurnHarness
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import java.time.OffsetDateTime

class DuelSimulationTest {

    @Test
    fun `duel world processes a tick and consumes queued player turn`() {
        val fx = setupWorld(userKing = true)

        fx.harness.turnService.processWorld(fx.world)

        assertTrue(fx.harness.generalTurnsFor(fx.dongKing.id).isEmpty())
        assertEquals(2, fx.world.currentMonth.toInt())
    }

    @Test
    fun `duel world keeps opposing city ownership without battle stub`() {
        val fx = setupWorld(userKing = true)

        fx.harness.turnService.processWorld(fx.world)

        assertEquals(2L, fx.city2.factionId)
    }

    @Test
    fun `multi turn duel invokes unification checks each tick`() {
        val fx = setupWorld(userKing = false)

        fx.harness.turnService.processWorld(fx.world)
        fx.world.updatedAt = OffsetDateTime.now().minusSeconds(90)
        fx.harness.turnService.processWorld(fx.world)

        verify(fx.harness.unificationService, times(2)).checkAndSettleUnification(fx.world)
    }

    @Test
    fun `mixed user and npc generals process in same tick without crashes`() {
        val fx = setupWorld(userKing = true, addWanderer = true)

        fx.harness.turnService.processWorld(fx.world)

        assertTrue(fx.harness.generalTurnsFor(fx.dongKing.id).isEmpty())
        assertEquals(2, fx.world.currentMonth.toInt())
    }

    private fun setupWorld(userKing: Boolean, addWanderer: Boolean = false): Fixture {
        val harness = InMemoryTurnHarness()
        val world = SessionState(
            id = 1,
            name = "duel-world",
            scenarioCode = "test",
            currentYear = 200,
            currentMonth = 1,
            tickSeconds = 60,
            updatedAt = OffsetDateTime.now().minusSeconds(90),
        )
        world.config = mutableMapOf("mapName" to "duel", "mapCode" to "duel", "hiddenSeed" to "test")

        val dong = Faction(id = 1, sessionId = 1, name = "동국", color = "#ff3300", factionRank = 3, strategicCmdLimit = 10)
        val seo = Faction(id = 2, sessionId = 1, name = "서국", color = "#0033ff", factionRank = 3, strategicCmdLimit = 10)
        val city1 = Planet(
            id = 1, sessionId = 1, name = "동성", level = 5, factionId = 1,
            supplyState = 1, frontState = 1, population = 10000, populationMax = 50000,
            production = 500, productionMax = 1000, commerce = 500, commerceMax = 1000,
            security = 500, securityMax = 1000, approval = 100, orbitalDefense = 500, orbitalDefenseMax = 1000, fortress = 500, fortressMax = 1000,
        )
        val city2 = Planet(
            id = 2, sessionId = 1, name = "서성", level = 5, factionId = 2,
            supplyState = 1, frontState = 1, population = 10000, populationMax = 50000,
            production = 500, productionMax = 1000, commerce = 500, commerceMax = 1000,
            security = 500, securityMax = 1000, approval = 100, orbitalDefense = 500, orbitalDefenseMax = 1000, fortress = 500, fortressMax = 1000,
        )

        val baseTurnTime = OffsetDateTime.now().minusSeconds(120)
        val dongKing = Officer(
            id = 11, sessionId = 1, name = "동왕", factionId = 1, planetId = 1,
            officerLevel = 20, npcState = if (userKing) 0 else 2, ships = 2000, training = 100, morale = 100,
            leadership = 95, command = 90, intelligence = 80, turnTime = baseTurnTime,
        )
        val dongOfficer = Officer(
            id = 12, sessionId = 1, name = "동장", factionId = 1, planetId = 1,
            officerLevel = 8, npcState = 2, ships = 1500, training = 100, morale = 100,
            leadership = 82, command = 78, intelligence = 75, turnTime = baseTurnTime.plusSeconds(1),
        )
        val seoKing = Officer(
            id = 21, sessionId = 1, name = "서왕", factionId = 2, planetId = 2,
            officerLevel = 20, npcState = 2, ships = 1800, training = 100, morale = 100,
            leadership = 92, command = 88, intelligence = 84, turnTime = baseTurnTime.plusSeconds(2),
        )

        harness.putWorld(world)
        harness.putFaction(dong)
        harness.putFaction(seo)
        harness.putPlanet(city1)
        harness.putPlanet(city2)
        harness.putOfficer(dongKing)
        harness.putOfficer(dongOfficer)
        harness.putOfficer(seoKing)

        if (addWanderer) {
            harness.putOfficer(
                Officer(
                    id = 31,
                    sessionId = 1,
                    name = "재야장수",
                    factionId = 0,
                    planetId = 1,
                    officerLevel = 0,
                    npcState = 1,
                    turnTime = baseTurnTime.plusSeconds(3),
                )
            )
        }

        val scenarioService = privateField<ScenarioService>(harness, "scenarioService")
        `when`(scenarioService.getScenario("test")).thenReturn(ScenarioData(startYear = 190))

        harness.queueGeneralTurn(officerId = 11, actionCode = "출병", arg = mutableMapOf("destCityId" to 2L))
        harness.queueNationTurn(factionId = 1, officerLevel = 20, actionCode = "Nation휴식")
        harness.queueNationTurn(factionId = 1, officerLevel = 8, actionCode = "Nation휴식")
        harness.queueNationTurn(factionId = 2, officerLevel = 20, actionCode = "Nation휴식")

        return Fixture(harness, world, city2, dongKing)
    }

    private fun <T> privateField(target: Any, fieldName: String): T {
        val field = target::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return field.get(target) as T
    }

    private data class Fixture(
        val harness: InMemoryTurnHarness,
        val world: SessionState,
        val city2: Planet,
        val dongKing: Officer,
    )
}
