package com.openlogh.engine

import com.openlogh.entity.Officer
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

    private fun createGeneral(
        leadership: Short = 50,
        leadershipExp: Short = 0,
        command: Short = 50,
        commandExp: Short = 0,
        intelligence: Short = 50,
        intelligenceExp: Short = 0,
        politics: Short = 50,
        politicsExp: Short = 0,
        administration: Short = 50,
        charmExp: Short = 0,
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
            administrationExp = charmExp,
            turnTime = OffsetDateTime.now(),
        )
    }

    @Test
    fun `stat increases by 1 when exp reaches upgrade limit`() {
        val general = createGeneral(leadership = 50, leadershipExp = 30)

        val result = service.checkStatChange(general)

        assertTrue(result.hasChanges)
        assertEquals(51.toShort(), general.leadership)
        assertEquals(0.toShort(), general.leadershipExp, "Exp should be consumed")
        assertEquals(1, result.changes.size)
        assertEquals(+1, result.changes[0].delta)
    }

    @Test
    fun `stat decreases by 1 when exp is negative`() {
        val general = createGeneral(command = 50, commandExp = (-5).toShort())

        val result = service.checkStatChange(general)

        assertTrue(result.hasChanges)
        assertEquals(49.toShort(), general.command)
        // Exp should be adjusted: -5 + 30 = 25
        assertEquals(25.toShort(), general.commandExp)
        assertEquals(-1, result.changes[0].delta)
    }

    @Test
    fun `no change when exp is between 0 and upgrade limit`() {
        val general = createGeneral(leadershipExp = 15, commandExp = 0, intelligenceExp = 29)

        val result = service.checkStatChange(general)

        assertFalse(result.hasChanges)
        assertEquals(50.toShort(), general.leadership)
        assertEquals(50.toShort(), general.command)
        assertEquals(50.toShort(), general.intelligence)
    }

    @Test
    fun `stat does not increase above MAX_LEVEL`() {
        val general = createGeneral(intelligence = 255, intelligenceExp = 30)

        val result = service.checkStatChange(general)

        assertFalse(result.hasChanges, "No change entry when already at max")
        assertEquals(255.toShort(), general.intelligence)
        // Exp should still be consumed
        assertEquals(0.toShort(), general.intelligenceExp)
    }

    @Test
    fun `stat does not decrease below 0`() {
        val general = createGeneral(leadership = 0, leadershipExp = (-5).toShort())

        val result = service.checkStatChange(general)

        assertFalse(result.hasChanges, "No change when stat already 0")
        assertEquals(0.toShort(), general.leadership)
        // Exp adjusted: -5 + 30 = 25
        assertEquals(25.toShort(), general.leadershipExp)
    }

    // ── Phase 24-29 (gap A10): LOGH 8-stat 자동 성장 ──

    private fun createLoghOfficer(): Officer = Officer(
        id = 1,
        sessionId = 1,
        name = "테스트",
        factionId = 1,
        planetId = 1,
        leadership = 50, command = 50, intelligence = 50,
        politics = 50, administration = 50,
        mobility = 50, attack = 50, defense = 50,
        turnTime = OffsetDateTime.now(),
    )

    @Test
    fun `A10 - politics levels up when politicsExp reaches threshold`() {
        val g = createLoghOfficer().also { it.politicsExp = 30 }
        val r = service.checkStatChange(g)
        assertTrue(r.hasChanges)
        assertEquals(51.toShort(), g.politics)
        assertEquals(0.toShort(), g.politicsExp)
    }

    @Test
    fun `A10 - administration levels up when administrationExp reaches threshold`() {
        val g = createLoghOfficer().also { it.administrationExp = 30 }
        val r = service.checkStatChange(g)
        assertTrue(r.hasChanges)
        assertEquals(51.toShort(), g.administration)
    }

    @Test
    fun `A10 - mobility levels up when mobilityExp reaches threshold`() {
        val g = createLoghOfficer().also { it.mobilityExp = 30 }
        val r = service.checkStatChange(g)
        assertTrue(r.hasChanges)
        assertEquals(51.toShort(), g.mobility)
    }

    @Test
    fun `A10 - attack levels up when attackExp reaches threshold`() {
        val g = createLoghOfficer().also { it.attackExp = 30 }
        val r = service.checkStatChange(g)
        assertTrue(r.hasChanges)
        assertEquals(51.toShort(), g.attack)
    }

    @Test
    fun `A10 - defense levels up when defenseExp reaches threshold`() {
        val g = createLoghOfficer().also { it.defenseExp = 30 }
        val r = service.checkStatChange(g)
        assertTrue(r.hasChanges)
        assertEquals(51.toShort(), g.defense)
    }

    @Test
    fun `A10 - mobility can decrease when mobilityExp is negative`() {
        val g = createLoghOfficer().also { it.mobility = 50; it.mobilityExp = (-5).toShort() }
        val r = service.checkStatChange(g)
        assertTrue(r.hasChanges)
        assertEquals(49.toShort(), g.mobility)
        assertEquals(25.toShort(), g.mobilityExp, "negative exp rolls to (-5 + 30) = 25")
    }

    @Test
    fun `A10 - all 8 stats can level up in a single check`() {
        val g = createLoghOfficer().also {
            it.leadershipExp = 30
            it.commandExp = 30
            it.intelligenceExp = 30
            it.politicsExp = 30
            it.administrationExp = 30
            it.mobilityExp = 30
            it.attackExp = 30
            it.defenseExp = 30
        }
        val r = service.checkStatChange(g)
        assertEquals(8, r.changes.size, "모든 8 개 스탯이 동시에 +1 되어야 한다")
        assertEquals(51.toShort(), g.leadership)
        assertEquals(51.toShort(), g.command)
        assertEquals(51.toShort(), g.intelligence)
        assertEquals(51.toShort(), g.politics)
        assertEquals(51.toShort(), g.administration)
        assertEquals(51.toShort(), g.mobility)
        assertEquals(51.toShort(), g.attack)
        assertEquals(51.toShort(), g.defense)
    }
}
