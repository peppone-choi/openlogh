package com.openlogh.command

import com.openlogh.command.gin7.intelligence.ArrestAuthorizationCommand
import com.openlogh.command.gin7.intelligence.ExecutionOrderCommand
import com.openlogh.command.gin7.personnel.AppointCommand
import com.openlogh.command.gin7.politics.GovernanceGoalCommand
import com.openlogh.entity.Officer
import com.openlogh.model.CommandGroup
import com.openlogh.model.PositionCard
import com.openlogh.model.PositionCardRegistry
import com.openlogh.model.StatCategory
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import kotlin.random.Random

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

    // ========================================================
    // Gap Analysis Critical Gaps — B1/B2/B3/D2 regression tests
    // ========================================================

    // B1: 통치목표 CP = 80 (gin7 manual p72)
    @Test
    fun `B1 - 통치목표 cost is 80 CP`() {
        val officer = makeOfficer(listOf("PERSONAL", "CAPTAIN"))
        val env = makeEnv()
        val command = GovernanceGoalCommand(officer, env, null)
        assertEquals(
            80,
            command.getCommandPointCost(),
            "통치목표 should cost 80 CP per gin7 manual p72 (gap B1)"
        )
    }

    // B2: 체포허가 CP = 800 (gin7 manual p76)
    @Test
    fun `B2 - 체포허가 cost is 800 CP`() {
        val officer = makeOfficer(listOf("PERSONAL", "CAPTAIN"))
        val env = makeEnv()
        val command = ArrestAuthorizationCommand(officer, env, null)
        assertEquals(
            800,
            command.getCommandPointCost(),
            "체포허가 should cost 800 CP per gin7 manual p76 (gap B2)"
        )
        assertEquals(
            StatCategory.MCP,
            command.getCommandPoolType(),
            "체포허가 should use MCP pool"
        )
    }

    // B3: 집행명령 CP = 800 (gin7 manual p76)
    @Test
    fun `B3 - 집행명령 cost is 800 CP`() {
        val officer = makeOfficer(listOf("PERSONAL", "CAPTAIN"))
        val env = makeEnv()
        val command = ExecutionOrderCommand(officer, env, null)
        assertEquals(
            800,
            command.getCommandPointCost(),
            "집행명령 should cost 800 CP per gin7 manual p76 (gap B3)"
        )
        assertEquals(
            StatCategory.MCP,
            command.getCommandPoolType(),
            "집행명령 should use MCP pool"
        )
    }

    // D2/E54: 직무권한카드 16매 상한 (gin7 manual p26)
    @Test
    fun `D2 - Officer MAX_POSITION_CARDS is 16 per manual p26`() {
        assertEquals(
            16,
            Officer.MAX_POSITION_CARDS,
            "gin7 manual p26 — 最大保有 16매"
        )
    }

    @Test
    fun `D2 - canAcceptAdditionalPositionCard returns true when under limit`() {
        val officer = makeOfficer(listOf("PERSONAL", "CAPTAIN"))
        assertTrue(
            officer.canAcceptAdditionalPositionCard(),
            "Officer with 2 cards should accept more (limit 16)"
        )
    }

    @Test
    fun `D2 - canAcceptAdditionalPositionCard returns false at limit`() {
        val sixteenCards = (1..16).map { "CARD_$it" }
        val officer = makeOfficer(sixteenCards)
        assertFalse(
            officer.canAcceptAdditionalPositionCard(),
            "Officer with 16 cards should reject additional cards"
        )
    }

    @Test
    fun `D2 - AppointCommand refuses 17th position card`() {
        val appointer = makeOfficer(listOf("PERSONAL", "CAPTAIN"))
        val target = Officer(
            id = 2L,
            sessionId = 1L,
            name = "대상제독",
            factionId = 1L,
            planetId = 10L,
            positionCards = (1..16).map { "CARD_$it" }.toMutableList(),
            turnTime = OffsetDateTime.now(),
        )
        val env = makeEnv()
        val command = AppointCommand(appointer, env, mapOf("positionCard" to "NEW_CARD"))
        command.destOfficer = target

        val result = runBlocking { command.run(Random.Default) }

        assertFalse(result.success, "임명 should fail when target already has 16 cards")
        assertEquals(16, target.positionCards.size, "Card count should remain 16")
        assertFalse(target.positionCards.contains("NEW_CARD"))
    }

    @Test
    fun `D2 - AppointCommand allows 16th position card at boundary`() {
        val appointer = makeOfficer(listOf("PERSONAL", "CAPTAIN"))
        val target = Officer(
            id = 2L,
            sessionId = 1L,
            name = "대상제독",
            factionId = 1L,
            planetId = 10L,
            positionCards = (1..15).map { "CARD_$it" }.toMutableList(),
            turnTime = OffsetDateTime.now(),
        )
        val env = makeEnv()
        val command = AppointCommand(appointer, env, mapOf("positionCard" to "CARD_16"))
        command.destOfficer = target

        val result = runBlocking { command.run(Random.Default) }

        assertTrue(result.success, "임명 should succeed when target has exactly 15 cards")
        assertEquals(16, target.positionCards.size, "Card count should reach exactly 16")
        assertTrue(target.positionCards.contains("CARD_16"))
    }

    @Test
    fun `D2 - AppointCommand is idempotent for duplicate card`() {
        val appointer = makeOfficer(listOf("PERSONAL", "CAPTAIN"))
        val target = Officer(
            id = 2L,
            sessionId = 1L,
            name = "대상제독",
            factionId = 1L,
            planetId = 10L,
            positionCards = mutableListOf("PERSONAL", "CAPTAIN"),
            turnTime = OffsetDateTime.now(),
        )
        val env = makeEnv()
        val command = AppointCommand(appointer, env, mapOf("positionCard" to "CAPTAIN"))
        command.destOfficer = target

        val result = runBlocking { command.run(Random.Default) }

        assertTrue(result.success, "임명 should succeed (no-op) for duplicate card")
        assertEquals(2, target.positionCards.size, "Card count should remain 2")
    }
}
