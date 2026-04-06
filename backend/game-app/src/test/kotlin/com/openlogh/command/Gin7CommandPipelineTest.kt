package com.openlogh.command

import com.openlogh.entity.Officer
import com.openlogh.model.CommandGroup
import com.openlogh.model.PositionCard
import com.openlogh.model.PositionCardRegistry
import com.openlogh.model.StatCategory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

/**
 * Tests PositionCard gating and MCP/PCP pool type assignment.
 *
 * These tests exercise the pipeline logic without a Spring context:
 * - PositionCardRegistry.canExecute() for authority checks
 * - Gin7CommandRegistry.createOfficerCommand() + getCommandPoolType() for pool type
 */
class Gin7CommandPipelineTest {

    private val registry = Gin7CommandRegistry()

    private fun makeEnv(): CommandEnv = CommandEnv(
        sessionId = 1L,
        year = 796,
        month = 1,
        startYear = 796,
        realtimeMode = true,
        gameStor = mutableMapOf(),
    )

    private fun makeOfficer(cards: List<String>): Officer = Officer(
        id = 1L,
        sessionId = 1L,
        name = "테스트제독",
        factionId = 1L,
        planetId = 10L,
        positionCards = cards.toMutableList(),
        turnTime = OffsetDateTime.now(),
    )

    // Test 4: Officer without PositionCard cannot execute "워프항행"
    @Test
    fun `officer without position cards cannot execute 워프항행`() {
        val officer = makeOfficer(emptyList())
        val cards = officer.getPositionCardEnums()
        val canExecute = PositionCardRegistry.canExecute(cards, "워프항행")
        assertFalse(canExecute, "Officer with no cards should not be able to execute 워프항행")
    }

    // Test 5: Officer with OPERATIONS-granting card can execute "워프항행"
    @Test
    fun `officer with OPERATIONS card can execute 워프항행`() {
        // CAPTAIN card grants CommandGroup.OPERATIONS
        val operationsCard = PositionCard.entries.first { CommandGroup.OPERATIONS in it.commandGroups }
        val officer = makeOfficer(listOf(operationsCard.name))
        val cards = officer.getPositionCardEnums()
        assertTrue(
            PositionCardRegistry.canExecute(cards, "워프항행"),
            "Officer with ${operationsCard.name} card should be able to execute 워프항행"
        )
    }

    // Test 6: "완전수리" (logistics) returns MCP pool type
    @Test
    fun `완전수리 command pool type is MCP`() {
        val officer = makeOfficer(listOf("PERSONAL", "CAPTAIN"))
        val env = makeEnv()
        val command = registry.createOfficerCommand("완전수리", officer, env, null)
        assertEquals(
            StatCategory.MCP,
            command.getCommandPoolType(),
            "완전수리 should use MCP pool"
        )
    }

    // Test 7: "승진" (personnel) returns PCP pool type (base class default)
    @Test
    fun `승진 command pool type is PCP`() {
        val officer = makeOfficer(listOf("PERSONAL", "CAPTAIN"))
        val env = makeEnv()
        val command = registry.createOfficerCommand("승진", officer, env, null)
        assertEquals(
            StatCategory.PCP,
            command.getCommandPoolType(),
            "승진 should use PCP pool"
        )
    }
}
