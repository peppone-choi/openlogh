package com.openlogh.command

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ArgSchemaValidationTest {

    @Test
    fun `dest city schema parses aliases and emits legacy keys`() {
        val validated = ArgSchemas.destCity.parse(mapOf("cityId" to "12"))

        assertTrue(validated.ok())
        assertEquals(12L, validated.longOrNull("destCityId"))

        val legacy = validated.toLegacyMap(ArgSchemas.destCity)
        assertEquals(12L, legacy["destCityId"])
        assertEquals(12L, legacy["destCityID"])
        assertEquals(12L, legacy["cityId"])
        assertEquals(12L, legacy["targetCityId"])
    }

    @Test
    fun `required field missing returns validation error`() {
        val validated = ArgSchemas.recruit.parse(mapOf("amount" to 500))

        assertFalse(validated.ok())
        assertTrue(validated.errors.any { it.field == "crewType" })
    }

    @Test
    fun `trade schema applies default isBuy true`() {
        val validated = ArgSchemas.trade.parse(mapOf("amount" to "300"))

        assertTrue(validated.ok())
        assertEquals(300, validated.intOrNull("amount"))
        assertEquals(true, validated.boolOrNull("isBuy"))
    }

    @Test
    fun `foundNation schema defaults nationType to legacy che_도적`() {
        val validated = ArgSchemas.foundNation.parse(mapOf("nationName" to "테스트국"))

        assertTrue(validated.ok())
        assertEquals("테스트국", validated.stringOrNull("nationName"))
        assertEquals("che_도적", validated.stringOrNull("nationType"))
        assertEquals(0, validated.intOrNull("colorType"))
    }

    @Test
    fun `all registered commands have schema entries`() {
        val registry = CommandRegistry()
        val registered = registry.getGeneralCommandNames() + registry.getNationCommandNames()

        assertEquals(101, registered.size)
        assertEquals(101, COMMAND_SCHEMAS.size)
        assertTrue(registered.all { it in COMMAND_SCHEMAS.keys })
    }
}
