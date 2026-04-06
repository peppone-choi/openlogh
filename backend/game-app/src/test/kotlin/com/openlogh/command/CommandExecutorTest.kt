package com.openlogh.command

import com.openlogh.entity.Planet
import com.openlogh.entity.Officer
import com.openlogh.entity.Faction
import com.openlogh.entity.SessionState
import com.openlogh.test.InMemoryTurnHarness
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

class CommandExecutorTest {

    @Test
    fun `executeOfficerCommand applies 요양 stat changes to general`() = runBlocking {
        val harness = InMemoryTurnHarness()
        val world = SessionState(
            id = 1,
            scenarioCode = "test",
            currentYear = 180,
            currentMonth = 1,
            tickSeconds = 300,
        )
        val nation = Faction(
            id = 1,
            sessionId = 1,
            name = "테스트국가",
            color = "#FF0000",
            factionRank = 1,
        )
        val city = Planet(
            id = 1,
            sessionId = 1,
            name = "테스트도시",
            factionId = 1,
            supplyState = 1,
        )
        val general = Officer(
            id = 1,
            sessionId = 1,
            name = "테스트장수",
            factionId = 1,
            planetId = 1,
            injury = 25,
            experience = 100,
            dedication = 50,
            turnTime = OffsetDateTime.now(),
        )
        val env = CommandEnv(
            year = 180,
            month = 1,
            startYear = 180,
            sessionId = 1,
        )

        harness.putWorld(world)
        harness.putFaction(nation)
        harness.putPlanet(city)
        harness.putOfficer(general)

        val result = harness.commandExecutor.executeOfficerCommand(
            actionCode = "요양",
            general = general,
            env = env,
            city = city,
            nation = nation,
        )

        assertTrue(result.success)
        assertEquals(0.toShort(), general.injury)
        assertEquals(110, general.experience)
        assertEquals(57, general.dedication)
    }

    @Test
    fun `executeOfficerCommand creates wandering nation on 거병 for unaffiliated general`() = runBlocking {
        val harness = InMemoryTurnHarness()
        val world = SessionState(
            id = 1,
            scenarioCode = "test",
            currentYear = 180,
            currentMonth = 2,
            tickSeconds = 300,
            config = mutableMapOf("startyear" to 180),
        )
        val city = Planet(
            id = 1,
            sessionId = 1,
            name = "테스트도시",
            level = 5,
            factionId = 0,
            supplyState = 1,
        )
        val general = Officer(
            id = 1,
            sessionId = 1,
            name = "테스트장수",
            factionId = 0,
            planetId = 1,
            officerLevel = 0,
            turnTime = OffsetDateTime.now(),
        )
        val env = CommandEnv(
            year = 180,
            month = 2,
            startYear = 180,
            sessionId = 1,
        )

        harness.putWorld(world)
        harness.putPlanet(city)
        harness.putOfficer(general)

        val result = harness.commandExecutor.executeOfficerCommand(
            actionCode = "거병",
            general = general,
            env = env,
            city = city,
            nation = null,
        )

        assertTrue(result.success)
        assertNotEquals(0L, general.factionId)
        assertEquals(20.toShort(), general.officerLevel)

        val nations = harness.factionRepository.findBySessionId(1)
        assertEquals(1, nations.size)
        assertEquals(general.factionId, nations.first().id)
        assertEquals(0.toShort(), nations.first().factionRank)
    }

    @Test
    fun `executeOfficerCommand founds nation directly from unaffiliated general`() = runBlocking {
        val harness = InMemoryTurnHarness()
        val world = SessionState(
            id = 1,
            scenarioCode = "test",
            currentYear = 180,
            currentMonth = 2,
            tickSeconds = 300,
            config = mutableMapOf("startyear" to 180),
        )
        val city = Planet(
            id = 1,
            sessionId = 1,
            name = "테스트도시",
            level = 5,
            factionId = 0,
            supplyState = 1,
        )
        val general = Officer(
            id = 1,
            sessionId = 1,
            name = "테스트장수",
            factionId = 0,
            planetId = 1,
            officerLevel = 0,
            turnTime = OffsetDateTime.now(),
        )
        val env = CommandEnv(
            year = 180,
            month = 2,
            startYear = 180,
            sessionId = 1,
        )

        harness.putWorld(world)
        harness.putPlanet(city)
        harness.putOfficer(general)

        val result = harness.commandExecutor.executeOfficerCommand(
            actionCode = "건국",
            general = general,
            env = env,
            arg = mapOf(
                "factionName" to "신국",
                "nationType" to "군벌",
                "colorType" to 1,
            ),
            city = city,
            nation = null,
        )

        assertTrue(result.success)
        assertNotEquals(0L, general.factionId)
        assertEquals(20.toShort(), general.officerLevel)
        assertEquals(general.factionId, city.factionId)

        val foundedNation = harness.factionRepository.findById(general.factionId).orElse(null)
        assertNotNull(foundedNation)
        assertEquals("신국", foundedNation!!.name)
        assertEquals(1.toShort(), foundedNation.factionRank)
        assertEquals("che_군벌", foundedNation.factionType)
    }

    @Test
    fun `executeOfficerCommand random founding moves nation generals to random neutral city`() = runBlocking {
        val harness = InMemoryTurnHarness()
        val world = SessionState(
            id = 1,
            scenarioCode = "test",
            currentYear = 180,
            currentMonth = 2,
            tickSeconds = 300,
            config = mutableMapOf("startyear" to 180),
        )
        val nation = Faction(
            id = 10,
            sessionId = 1,
            name = "방랑국",
            factionRank = 0,
            officerCount = 2,
            chiefOfficerId = 1,
        )
        val currentCity = Planet(
            id = 1,
            sessionId = 1,
            name = "현재도시",
            level = 7,
            factionId = 0,
            supplyState = 1,
        )
        val candidateCityA = Planet(
            id = 2,
            sessionId = 1,
            name = "후보A",
            level = 5,
            factionId = 0,
            supplyState = 1,
        )
        val candidateCityB = Planet(
            id = 3,
            sessionId = 1,
            name = "후보B",
            level = 6,
            factionId = 0,
            supplyState = 1,
        )
        val general = Officer(
            id = 1,
            sessionId = 1,
            name = "군주",
            factionId = 10,
            planetId = 1,
            officerLevel = 20,
            turnTime = OffsetDateTime.now(),
        )
        val subordinate = Officer(
            id = 2,
            sessionId = 1,
            name = "부하",
            factionId = 10,
            planetId = 1,
            officerLevel = 2,
            turnTime = OffsetDateTime.now(),
        )
        val env = CommandEnv(
            year = 180,
            month = 2,
            startYear = 180,
            sessionId = 1,
        )

        harness.putWorld(world)
        harness.putFaction(nation)
        harness.putPlanet(currentCity)
        harness.putPlanet(candidateCityA)
        harness.putPlanet(candidateCityB)
        harness.putOfficer(general)
        harness.putOfficer(subordinate)

        val result = harness.commandExecutor.executeOfficerCommand(
            actionCode = "무작위건국",
            general = general,
            env = env,
            arg = mapOf(
                "factionName" to "신국",
                "nationType" to "군벌",
                "colorType" to 1,
            ),
            city = currentCity,
            nation = nation,
        )

        assertTrue(result.success)

        val movedCityId = general.planetId
        assertTrue(movedCityId == 2L || movedCityId == 3L)
        assertEquals(movedCityId, subordinate.planetId)

        val updatedNation = harness.factionRepository.findById(10).orElseThrow()
        assertEquals(movedCityId, updatedNation.capitalPlanetId)
        assertEquals(1.toShort(), updatedNation.factionRank)

        val selectedCity = harness.planetRepository.findById(movedCityId).orElseThrow()
        assertEquals(10L, selectedCity.factionId)
    }

    @Test
    fun `cooldown failure log includes command name with color tag`() = runBlocking {
        val harness = InMemoryTurnHarness()
        val world = SessionState(
            id = 1, scenarioCode = "test", currentYear = 180, currentMonth = 1, tickSeconds = 300,
            config = mutableMapOf("startyear" to 180),
        )
        val nation = Faction(id = 1, sessionId = 1, name = "테스트국가", factionRank = 1)
        val city = Planet(id = 1, sessionId = 1, name = "도시", factionId = 1, supplyState = 1)
        val general = Officer(
            id = 1, sessionId = 1, name = "장수", factionId = 1, planetId = 1,
            turnTime = OffsetDateTime.now(),
        ).apply {
            meta["next_execute"] = mutableMapOf<String, Any>("che_농지개간" to (180 * 12 + 5))
        }
        val env = CommandEnv(year = 180, month = 1, startYear = 180, sessionId = 1)

        harness.putWorld(world)
        harness.putFaction(nation)
        harness.putPlanet(city)
        harness.putOfficer(general)

        val result = harness.commandExecutor.executeOfficerCommand(
            actionCode = "che_농지개간", general = general, env = env, city = city, nation = nation,
        )

        assertTrue(!result.success, "Command should fail due to cooldown")
        assertTrue(result.logs.isNotEmpty())
        val log = result.logs.first()
        assertTrue(log.contains("<R>"), "Cooldown log should contain <R> tag: $log")
        assertTrue(log.contains("쿨다운"), "Cooldown log should mention cooldown: $log")
    }
}
