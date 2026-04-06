package com.openlogh.engine

import com.openlogh.entity.Planet
import com.openlogh.entity.Officer
import com.openlogh.entity.Faction
import com.openlogh.model.CrewType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CrewTypeAvailabilityTest {

    private val service = ShipClassAvailability()

    @Test
    fun `parses unit set data and normalizes coefficients`() {
        val parsed = service.parseUnitSetDefinition(
            mapOf(
                "id" to "test",
                "name" to "test",
                "defaultCrewTypeId" to 1100,
                "crewTypes" to listOf(
                    mapOf(
                        "id" to 1100,
                        "armType" to 1,
                        "name" to "보병",
                        "attack" to 100,
                        "defence" to 100,
                        "speed" to 7,
                        "avoid" to 10,
                        "magicCoef" to 0,
                        "cost" to 9,
                        "rice" to 9,
                        "requirements" to emptyList<Map<String, Any?>>(),
                        "attackCoef" to emptyList<Any>(),
                        "defenceCoef" to mapOf("1" to 1.2),
                        "info" to listOf("테스트"),
                        "initSkillTrigger" to null,
                        "phaseSkillTrigger" to null,
                        "iActionList" to null,
                    ),
                ),
            ),
        )

        assertEquals(1100, parsed.defaultCrewTypeId)
        assertTrue(parsed.crewTypes.first().attackCoef.isEmpty())
        assertEquals(1.2, parsed.crewTypes.first().defenceCoef["1"])
    }

    @Test
    fun `checks tech region city requirements`() {
        val unitSet = service.parseUnitSetDefinition(
            mapOf(
                "id" to "test",
                "name" to "test",
                "crewTypes" to listOf(
                    mapOf(
                        "id" to 1100,
                        "armType" to 1,
                        "name" to "기본",
                        "attack" to 100,
                        "defence" to 100,
                        "speed" to 7,
                        "avoid" to 10,
                        "magicCoef" to 0,
                        "cost" to 9,
                        "rice" to 9,
                        "requirements" to emptyList<Map<String, Any?>>(),
                        "attackCoef" to emptyMap<String, Double>(),
                        "defenceCoef" to emptyMap<String, Double>(),
                        "info" to emptyList<String>(),
                        "initSkillTrigger" to null,
                        "phaseSkillTrigger" to null,
                        "iActionList" to null,
                    ),
                    mapOf(
                        "id" to 1200,
                        "armType" to 1,
                        "name" to "기술병",
                        "attack" to 100,
                        "defence" to 100,
                        "speed" to 7,
                        "avoid" to 10,
                        "magicCoef" to 0,
                        "cost" to 9,
                        "rice" to 9,
                        "requirements" to listOf(
                            mapOf("type" to "ReqTech", "tech" to 2000),
                            mapOf("type" to "ReqRegions", "regions" to listOf("A")),
                        ),
                        "attackCoef" to emptyMap<String, Double>(),
                        "defenceCoef" to emptyMap<String, Double>(),
                        "info" to emptyList<String>(),
                        "initSkillTrigger" to null,
                        "phaseSkillTrigger" to null,
                        "iActionList" to null,
                    ),
                    mapOf(
                        "id" to 1300,
                        "armType" to 1,
                        "name" to "도시병",
                        "attack" to 100,
                        "defence" to 100,
                        "speed" to 7,
                        "avoid" to 10,
                        "magicCoef" to 0,
                        "cost" to 9,
                        "rice" to 9,
                        "requirements" to listOf(
                            mapOf("type" to "ReqCitiesWithCityLevel", "level" to 7, "cities" to listOf("A")),
                        ),
                        "attackCoef" to emptyMap<String, Double>(),
                        "defenceCoef" to emptyMap<String, Double>(),
                        "info" to emptyList<String>(),
                        "initSkillTrigger" to null,
                        "phaseSkillTrigger" to null,
                        "iActionList" to null,
                    ),
                ),
            ),
        )

        val context = CrewTypeAvailabilityContext(
            general = Officer(factionId = 1, officerLevel = 3),
            nation = Faction(id = 1, techLevel = 3000f),
            mapCities = listOf(
                MapCityDefinition(id = 1, name = "A", level = 6, region = 1),
            ),
            ownedCities = listOf(
                Planet(id = 1, name = "A", factionId = 1, level = 6),
            ),
            currentYear = 10,
            startYear = 1,
        )

        assertTrue(service.isCrewTypeAvailable(unitSet, 1100, context))
        assertTrue(service.isCrewTypeAvailable(unitSet, 1200, context))
        assertFalse(service.isCrewTypeAvailable(unitSet, 1300, context))
    }

    @Test
    fun `ships type region requirements follow map specific region ids`() {
        val habukCrewType = CrewType.entries.first { it.reqRegionNames == setOf("하북") }

        assertFalse(habukCrewType.isValidForNation(emptySet(), setOf(4), relYear = 10, techLevel = 5000))
        assertTrue(
            habukCrewType.isValidForNation(
                ownCityNames = emptySet(),
                ownRegionIds = setOf(4),
                relYear = 10,
                techLevel = 5000,
                regionNameToId = mapOf("하북" to 4),
            ),
        )
    }
}
