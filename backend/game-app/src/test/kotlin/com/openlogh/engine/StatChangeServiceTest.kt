package com.openlogh.engine

import com.openlogh.entity.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

class StatChangeServiceTest {

    private lateinit var service: StatChangeService

    @BeforeEach
    fun setUp() {
        service = StatChangeService()
    }

    private fun createOfficer(
        leadership: Short = 50,
        leadershipExp: Short = 0,
        command: Short = 50,
        commandExp: Short = 0,
        intelligence: Short = 50,
        intelligenceExp: Short = 0,
        politics: Short = 50,
        politicsExp: Short = 0,
        administration: Short = 50,
        administrationExp: Short = 0,
    ): Officer {
        return Officer(
            id = 1,
            sessionId = 1,
            name = "테스트",
            factionId = 1,
            planetId = 1,
            leadership = leadership,
            leadershipExp = leadershipExp,
            command = command,
            commandExp = commandExp,
            intelligence = intelligence,
            intelligenceExp = intelligenceExp,
            politics = politics,
            politicsExp = politicsExp,
            administration = administration,
            administrationExp = administrationExp,
            turnTime = OffsetDateTime.now(),
        )
    }

    @Test
    fun `stat increases by 1 when exp reaches upgrade limit`() {
        val officer = createOfficer(leadership = 50, leadershipExp = 30)

        val result = service.checkStatChange(officer)

        assertTrue(result.hasChanges)
        assertEquals(51.toShort(), officer.leadership)
        assertEquals(0.toShort(), officer.leadershipExp, "Exp should be consumed")
        assertEquals(1, result.changes.size)
        assertEquals(+1, result.changes[0].delta)
    }

    @Test
    fun `stat decreases by 1 when exp is negative`() {
        val officer = createOfficer(command = 50, commandExp = (-5).toShort())

        val result = service.checkStatChange(officer)

        assertTrue(result.hasChanges)
        assertEquals(49.toShort(), officer.command)
        // Exp should be adjusted: -5 + 30 = 25
        assertEquals(25.toShort(), officer.commandExp)
        assertEquals(-1, result.changes[0].delta)
    }

    @Test
    fun `no change when exp is between 0 and upgrade limit`() {
        val officer = createOfficer(leadershipExp = 15, commandExp = 0, intelligenceExp = 29)

        val result = service.checkStatChange(officer)

        assertFalse(result.hasChanges)
        assertEquals(50.toShort(), officer.leadership)
        assertEquals(50.toShort(), officer.command)
        assertEquals(50.toShort(), officer.intelligence)
    }

    @Test
    fun `stat does not increase above MAX_LEVEL`() {
        val officer = createOfficer(intelligence = 255, intelligenceExp = 30)

        val result = service.checkStatChange(officer)

        assertFalse(result.hasChanges, "No change entry when already at max")
        assertEquals(255.toShort(), officer.intelligence)
        // Exp should still be consumed
        assertEquals(0.toShort(), officer.intelligenceExp)
    }

    @Test
    fun `stat does not decrease below 0`() {
        val officer = createOfficer(leadership = 0, leadershipExp = (-5).toShort())

        val result = service.checkStatChange(officer)

        assertFalse(result.hasChanges, "No change when stat already 0")
        assertEquals(0.toShort(), officer.leadership)
        // Exp adjusted: -5 + 30 = 25
        assertEquals(25.toShort(), officer.leadershipExp)
    }
}
