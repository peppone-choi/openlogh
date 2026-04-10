package com.openlogh.command

import com.openlogh.entity.Officer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

class CommandRegistryTest {

    private lateinit var registry: CommandRegistry

    @BeforeEach
    fun setUp() {
        registry = Gin7CommandRegistry()
    }

    private fun createTestOfficer(): Officer = Officer(
        id = 1,
        sessionId = 1,
        name = "테스트제독",
        factionId = 1,
        planetId = 1,
        positionCards = mutableListOf("PERSONAL", "CAPTAIN"),
        turnTime = OffsetDateTime.now(),
    )

    private fun createTestEnv(): CommandEnv = CommandEnv(
        year = 200,
        month = 1,
        startYear = 190,
        sessionId = 1,
        realtimeMode = true,
    )

    @Test
    fun `gin7 registry exposes 82 officer commands including standby`() {
        val names = registry.getGeneralCommandNames()

        assertEquals(82, names.size)
        assertTrue("대기" in names)
        assertTrue("워프항행" in names)
        assertTrue("작전계획" in names)
        assertTrue("완전수리" in names)
        assertTrue("승진" in names)
        assertTrue("외교" in names)
        assertTrue("일제수색" in names)
    }

    @Test
    fun `current gin7 registry does not register legacy nation command map`() {
        assertTrue(registry.getNationCommandNames().isEmpty())
        assertFalse(registry.hasNationCommand("천도"))
    }

    @Test
    fun `legacy deleted samguk command codes are absent`() {
        assertFalse(registry.hasGeneralCommand("농지개간"))
        assertFalse(registry.hasGeneralCommand("거병"))
        assertFalse(registry.hasGeneralCommand("건국"))
        assertFalse(registry.hasGeneralCommand("요양"))
    }

    @Test
    fun `createOfficerCommand returns a gin7 command for known action`() {
        val officer = createTestOfficer()
        val env = createTestEnv()

        val command = registry.createOfficerCommand("워프항행", officer, env)

        assertEquals("워프항행", command.actionName)
    }

    @Test
    fun `registered command keys do not overlap with nation command keys`() {
        val overlap = registry.getGeneralCommandNames().intersect(registry.getNationCommandNames())
        assertTrue(overlap.isEmpty())
    }
}
