package com.openlogh.command

import com.openlogh.entity.City
import com.openlogh.entity.General
import com.openlogh.entity.Nation
import com.openlogh.entity.WorldState
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
    fun `executeGeneralCommand applies 요양 stat changes to general`() = runBlocking {
        val harness = InMemoryTurnHarness()
        val world = WorldState(
            id = 1,
            scenarioCode = "test",
            currentYear = 180,
            currentMonth = 1,
            tickSeconds = 300,
        )
        val nation = Nation(
            id = 1,
            worldId = 1,
            name = "테스트국가",
            color = "#FF0000",
            level = 1,
        )
        val city = City(
            id = 1,
            worldId = 1,
            name = "테스트도시",
            nationId = 1,
            supplyState = 1,
        )
        val general = General(
            id = 1,
            worldId = 1,
            name = "테스트장수",
            nationId = 1,
            cityId = 1,
            injury = 25,
            experience = 100,
            dedication = 50,
            turnTime = OffsetDateTime.now(),
        )
        val env = CommandEnv(
            year = 180,
            month = 1,
            startYear = 180,
            worldId = 1,
        )

        harness.putWorld(world)
        harness.putNation(nation)
        harness.putCity(city)
        harness.putGeneral(general)

        val result = harness.commandExecutor.executeGeneralCommand(
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
    fun `executeGeneralCommand creates wandering nation on 거병 for unaffiliated general`() = runBlocking {
        val harness = InMemoryTurnHarness()
        val world = WorldState(
            id = 1,
            scenarioCode = "test",
            currentYear = 180,
            currentMonth = 2,
            tickSeconds = 300,
            config = mutableMapOf("startyear" to 180),
        )
        val city = City(
            id = 1,
            worldId = 1,
            name = "테스트도시",
            level = 5,
            nationId = 0,
            supplyState = 1,
        )
        val general = General(
            id = 1,
            worldId = 1,
            name = "테스트장수",
            nationId = 0,
            cityId = 1,
            officerLevel = 0,
            turnTime = OffsetDateTime.now(),
        )
        val env = CommandEnv(
            year = 180,
            month = 2,
            startYear = 180,
            worldId = 1,
        )

        harness.putWorld(world)
        harness.putCity(city)
        harness.putGeneral(general)

        val result = harness.commandExecutor.executeGeneralCommand(
            actionCode = "거병",
            general = general,
            env = env,
            city = city,
            nation = null,
        )

        assertTrue(result.success)
        assertNotEquals(0L, general.nationId)
        assertEquals(20.toShort(), general.officerLevel)

        val nations = harness.nationRepository.findByWorldId(1)
        assertEquals(1, nations.size)
        assertEquals(general.nationId, nations.first().id)
        assertEquals(0.toShort(), nations.first().level)
    }

    @Test
    fun `executeGeneralCommand founds nation directly from unaffiliated general`() = runBlocking {
        val harness = InMemoryTurnHarness()
        val world = WorldState(
            id = 1,
            scenarioCode = "test",
            currentYear = 180,
            currentMonth = 2,
            tickSeconds = 300,
            config = mutableMapOf("startyear" to 180),
        )
        val city = City(
            id = 1,
            worldId = 1,
            name = "테스트도시",
            level = 5,
            nationId = 0,
            supplyState = 1,
        )
        val general = General(
            id = 1,
            worldId = 1,
            name = "테스트장수",
            nationId = 0,
            cityId = 1,
            officerLevel = 0,
            turnTime = OffsetDateTime.now(),
        )
        val env = CommandEnv(
            year = 180,
            month = 2,
            startYear = 180,
            worldId = 1,
        )

        harness.putWorld(world)
        harness.putCity(city)
        harness.putGeneral(general)

        val result = harness.commandExecutor.executeGeneralCommand(
            actionCode = "건국",
            general = general,
            env = env,
            arg = mapOf(
                "nationName" to "신국",
                "nationType" to "군벌",
                "colorType" to 1,
            ),
            city = city,
            nation = null,
        )

        assertTrue(result.success)
        assertNotEquals(0L, general.nationId)
        assertEquals(20.toShort(), general.officerLevel)
        assertEquals(general.nationId, city.nationId)

        val foundedNation = harness.nationRepository.findById(general.nationId).orElse(null)
        assertNotNull(foundedNation)
        assertEquals("신국", foundedNation!!.name)
        assertEquals(1.toShort(), foundedNation.level)
        assertEquals("che_군벌", foundedNation.typeCode)
    }

    @Test
    fun `executeGeneralCommand random founding moves nation generals to random neutral city`() = runBlocking {
        val harness = InMemoryTurnHarness()
        val world = WorldState(
            id = 1,
            scenarioCode = "test",
            currentYear = 180,
            currentMonth = 2,
            tickSeconds = 300,
            config = mutableMapOf("startyear" to 180),
        )
        val nation = Nation(
            id = 10,
            worldId = 1,
            name = "방랑국",
            level = 0,
            gennum = 2,
            chiefGeneralId = 1,
        )
        val currentCity = City(
            id = 1,
            worldId = 1,
            name = "현재도시",
            level = 7,
            nationId = 0,
            supplyState = 1,
        )
        val candidateCityA = City(
            id = 2,
            worldId = 1,
            name = "후보A",
            level = 5,
            nationId = 0,
            supplyState = 1,
        )
        val candidateCityB = City(
            id = 3,
            worldId = 1,
            name = "후보B",
            level = 6,
            nationId = 0,
            supplyState = 1,
        )
        val general = General(
            id = 1,
            worldId = 1,
            name = "군주",
            nationId = 10,
            cityId = 1,
            officerLevel = 20,
            turnTime = OffsetDateTime.now(),
        )
        val subordinate = General(
            id = 2,
            worldId = 1,
            name = "부하",
            nationId = 10,
            cityId = 1,
            officerLevel = 2,
            turnTime = OffsetDateTime.now(),
        )
        val env = CommandEnv(
            year = 180,
            month = 2,
            startYear = 180,
            worldId = 1,
        )

        harness.putWorld(world)
        harness.putNation(nation)
        harness.putCity(currentCity)
        harness.putCity(candidateCityA)
        harness.putCity(candidateCityB)
        harness.putGeneral(general)
        harness.putGeneral(subordinate)

        val result = harness.commandExecutor.executeGeneralCommand(
            actionCode = "무작위건국",
            general = general,
            env = env,
            arg = mapOf(
                "nationName" to "신국",
                "nationType" to "군벌",
                "colorType" to 1,
            ),
            city = currentCity,
            nation = nation,
        )

        assertTrue(result.success)

        val movedCityId = general.cityId
        assertTrue(movedCityId == 2L || movedCityId == 3L)
        assertEquals(movedCityId, subordinate.cityId)

        val updatedNation = harness.nationRepository.findById(10).orElseThrow()
        assertEquals(movedCityId, updatedNation.capitalCityId)
        assertEquals(1.toShort(), updatedNation.level)

        val selectedCity = harness.cityRepository.findById(movedCityId).orElseThrow()
        assertEquals(10L, selectedCity.nationId)
    }

    @Test
    fun `cooldown failure log includes command name with color tag`() = runBlocking {
        val harness = InMemoryTurnHarness()
        val world = WorldState(
            id = 1, scenarioCode = "test", currentYear = 180, currentMonth = 1, tickSeconds = 300,
            config = mutableMapOf("startyear" to 180),
        )
        val nation = Nation(id = 1, worldId = 1, name = "테스트국가", level = 1)
        val city = City(id = 1, worldId = 1, name = "도시", nationId = 1, supplyState = 1)
        val general = General(
            id = 1, worldId = 1, name = "장수", nationId = 1, cityId = 1,
            turnTime = OffsetDateTime.now(),
        ).apply {
            meta["next_execute"] = mutableMapOf<String, Any>("che_농지개간" to (180 * 12 + 5))
        }
        val env = CommandEnv(year = 180, month = 1, startYear = 180, worldId = 1)

        harness.putWorld(world)
        harness.putNation(nation)
        harness.putCity(city)
        harness.putGeneral(general)

        val result = harness.commandExecutor.executeGeneralCommand(
            actionCode = "che_농지개간", general = general, env = env, city = city, nation = nation,
        )

        assertTrue(!result.success, "Command should fail due to cooldown")
        assertTrue(result.logs.isNotEmpty())
        val log = result.logs.first()
        assertTrue(log.contains("<R>"), "Cooldown log should contain <R> tag: $log")
        assertTrue(log.contains("쿨다운"), "Cooldown log should mention cooldown: $log")
    }
}
