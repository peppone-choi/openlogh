package com.openlogh.command

import com.openlogh.entity.Faction
import com.openlogh.entity.Officer
import com.openlogh.entity.Planet
import com.openlogh.entity.SessionState
import com.openlogh.test.InMemoryTurnHarness
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

class CommandExecutorTest {

    @Test
    fun `executeOfficerCommand returns permission failure when officer lacks required position card`() = runBlocking {
        val harness = InMemoryTurnHarness()
        val world = SessionState(id = 1, scenarioCode = "test", currentYear = 180, currentMonth = 1, tickSeconds = 300)
        val faction = Faction(id = 1, sessionId = 1, name = "테스트국가", factionRank = 1)
        val planet = Planet(id = 1, sessionId = 1, name = "테스트도시", factionId = 1, supplyState = 1)
        val officer = Officer(
            id = 1,
            sessionId = 1,
            name = "테스트제독",
            factionId = 1,
            planetId = 1,
            positionCards = mutableListOf(),
            turnTime = OffsetDateTime.now(),
        )

        harness.putWorld(world)
        harness.putFaction(faction)
        harness.putPlanet(planet)
        harness.putOfficer(officer)

        val result = harness.commandExecutor.executeOfficerCommand(
            actionCode = "워프항행",
            general = officer,
            env = CommandEnv(year = 180, month = 1, startYear = 180, sessionId = 1),
            city = planet,
            nation = faction,
        )

        assertFalse(result.success)
        assertTrue(result.logs.any { it.contains("직무권한카드") })
    }

    @Test
    fun `cooldown failure log includes command name with color tag for current gin7 command`() = runBlocking {
        val harness = InMemoryTurnHarness()
        val world = SessionState(
            id = 1,
            scenarioCode = "test",
            currentYear = 180,
            currentMonth = 1,
            tickSeconds = 300,
        )
        val faction = Faction(id = 1, sessionId = 1, name = "테스트국가", factionRank = 1)
        val planet = Planet(id = 1, sessionId = 1, name = "도시", factionId = 1, supplyState = 1)
        val officer = Officer(
            id = 1,
            sessionId = 1,
            name = "장수",
            factionId = 1,
            planetId = 1,
            positionCards = mutableListOf("CAPTAIN"),
            turnTime = OffsetDateTime.now(),
        ).apply {
            meta["next_execute"] = mutableMapOf<String, Any>(
                "워프항행" to OffsetDateTime.now().plusMinutes(5).toString(),
            )
        }

        harness.putWorld(world)
        harness.putFaction(faction)
        harness.putPlanet(planet)
        harness.putOfficer(officer)

        val result = harness.commandExecutor.executeOfficerCommand(
            actionCode = "워프항행",
            general = officer,
            env = CommandEnv(year = 180, month = 1, startYear = 180, sessionId = 1),
            city = planet,
            nation = faction,
        )

        assertFalse(result.success)
        assertTrue(result.logs.isNotEmpty())
        val log = result.logs.first()
        assertTrue(log.contains("<R>"), "Cooldown log should contain <R> tag: $log")
        assertTrue(log.contains("쿨다운"), "Cooldown log should mention cooldown: $log")
    }
}
