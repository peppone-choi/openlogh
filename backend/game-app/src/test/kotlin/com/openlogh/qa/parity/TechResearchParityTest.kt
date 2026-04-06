package com.openlogh.qa.parity

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.openlogh.command.CommandEnv
import com.openlogh.command.general.che_기술연구
import com.openlogh.engine.LiteHashDRBG
import com.openlogh.entity.Planet
import com.openlogh.entity.Officer
import com.openlogh.entity.Faction
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

@DisplayName("Tech Research Legacy Parity")
class TechResearchParityTest {
    private val mapper = jacksonObjectMapper()

    @Test
    fun `TechLimit follows legacy five-year bands`() {
        val beforeFiveYears = createEnv(year = 184)
        val atFiveYears = createEnv(year = 185)

        assertFalse(beforeFiveYears.isTechLimited(999.0))
        assertTrue(beforeFiveYears.isTechLimited(1000.0))
        assertFalse(atFiveYears.isTechLimited(1000.0))
        assertTrue(atFiveYears.isTechLimited(2000.0))
    }

    @Test
    fun `che_기술연구 quarters tech gain only when legacy TechLimit applies`() {
        val limitedResult = runTechResearch(year = 184, seed = "tech_limit_before_unlock")
        val unlockedResult = runTechResearch(year = 185, seed = "tech_limit_before_unlock")

        val limitedJson = mapper.readTree(limitedResult)
        val unlockedJson = mapper.readTree(unlockedResult)

        val score = limitedJson["statChanges"]["dedication"].asInt()
        val limitedGain = limitedJson["nationChanges"]["tech"].asDouble()
        val unlockedGain = unlockedJson["nationChanges"]["tech"].asDouble()

        assertEquals(score, unlockedJson["statChanges"]["dedication"].asInt())
        assertEquals((score / 4).toDouble() / 10.0, limitedGain)
        assertEquals(score.toDouble() / 10.0, unlockedGain)
    }

    private fun runTechResearch(year: Int, seed: String): String {
        val command = che_기술연구(
            general = createGeneral(),
            env = createEnv(year = year),
        )
        command.city = createCity()
        command.nation = createNation()
        val result = runBlocking { command.run(LiteHashDRBG.build(seed)) }
        assertTrue(result.success)
        return result.message ?: error("tech research result should include a message payload")
    }

    private fun createEnv(year: Int) = CommandEnv(
        year = year,
        month = 1,
        startYear = 180,
        sessionId = 1,
        develCost = 100,
    )

    private fun createGeneral() = Officer(
        id = 1,
        sessionId = 1,
        name = "테스트장수",
        factionId = 1,
        planetId = 1,
        intelligence = 100,
        leadership = 70,
        command = 70,
        politics = 60,
        administration = 60,
        funds = 1000,
        supplies = 1000,
        turnTime = OffsetDateTime.now(),
    )

    private fun createCity() = Planet(
        id = 1,
        sessionId = 1,
        name = "테스트도시",
        factionId = 1,
        approval = 100f,
        supplyState = 1,
    )

    private fun createNation() = Faction(
        id = 1,
        sessionId = 1,
        name = "테스트국",
        techLevel = 1000f,
        meta = mutableMapOf("officerCount" to 10),
    )
}
