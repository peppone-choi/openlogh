package com.openlogh.command

import com.openlogh.command.gin7.intelligence.ArrestAuthorizationCommand
import com.openlogh.command.gin7.intelligence.ExecutionOrderCommand
import com.openlogh.command.gin7.personal.RebellionCommand
import com.openlogh.command.gin7.personnel.AppointCommand
import com.openlogh.command.gin7.personnel.AwardDecorationCommand
import com.openlogh.command.gin7.politics.GovernanceGoalCommand
import com.openlogh.command.CommandServices
import com.openlogh.entity.Faction
import com.openlogh.repository.FactionRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.PlanetRepository
import com.openlogh.engine.DiplomacyService
import com.openlogh.engine.GridCapacityChecker
import com.openlogh.entity.Fleet
import com.openlogh.entity.Officer
import com.openlogh.model.CommandGroup
import com.openlogh.model.InjuryEvent
import com.openlogh.model.PositionCard
import com.openlogh.model.PositionCardRegistry
import com.openlogh.model.StatCategory
import com.openlogh.repository.FleetRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
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

    // ================================================================
    // Phase 24-07 (A7/C4): 戦死 → 帰還惑星 워프
    // gin7 manual p51 — 戦死는 미구현, 負傷 → 帰還惑星 워프
    // ================================================================

    @Test
    fun `A7 - Officer returnPlanetId defaults to null per manual p51`() {
        val officer = makeOfficer(listOf("PERSONAL"))
        assertNull(
            officer.returnPlanetId,
            "New officers should have no configured return planet (fall back to faction capital)"
        )
    }

    @Test
    fun `A7 - Officer returnPlanetId can be set to target planet`() {
        val officer = makeOfficer(listOf("PERSONAL"))
        officer.returnPlanetId = 42L
        assertEquals(42L, officer.returnPlanetId)
    }

    @Test
    fun `A7 - resolveReturnPlanet prefers officer configured return planet`() {
        // Officer wins: configured 10 > faction 20 > current 30
        assertEquals(
            10L,
            InjuryEvent.resolveReturnPlanet(
                configuredReturnPlanetId = 10L,
                factionCapitalPlanetId = 20L,
                currentPlanetId = 30L,
            )
        )
    }

    @Test
    fun `A7 - resolveReturnPlanet falls back to faction capital when officer return null`() {
        assertEquals(
            20L,
            InjuryEvent.resolveReturnPlanet(
                configuredReturnPlanetId = null,
                factionCapitalPlanetId = 20L,
                currentPlanetId = 30L,
            )
        )
    }

    @Test
    fun `A7 - resolveReturnPlanet falls back to current planet when both null`() {
        assertEquals(
            30L,
            InjuryEvent.resolveReturnPlanet(
                configuredReturnPlanetId = null,
                factionCapitalPlanetId = null,
                currentPlanetId = 30L,
            )
        )
    }

    // ================================================================
    // Phase 24-06 (E39): 그리드 300 유닛/진영 capacity
    // gin7 manual p30 — 1つのグリッドに進入できる艦船ユニット数は1陣営 300 이하
    // ================================================================

    private fun mockFleetRepoAt(planetId: Long, fleets: List<Fleet>): FleetRepository {
        val repo = mock(FleetRepository::class.java)
        `when`(repo.findBySessionIdAndPlanetIdAndFactionId(anyLong(), eqLong(planetId), anyLong()))
            .thenReturn(fleets)
        return repo
    }

    @Test
    fun `E39 - GridCapacityChecker constant is 300 per manual p30`() {
        assertEquals(
            300,
            GridCapacityChecker.MAX_UNITS_PER_GRID_PER_FACTION,
            "gin7 manual p30 — 300 units/faction/grid"
        )
    }

    @Test
    fun `E39 - availableCapacity returns 300 on empty grid`() {
        val repo = mockFleetRepoAt(99L, emptyList())
        val available = GridCapacityChecker.availableCapacity(
            repo, sessionId = 1L, factionId = 1L, destPlanetId = 99L, movingFleetId = null
        )
        assertEquals(300, available, "Empty grid should allow full 300 units")
    }

    @Test
    fun `E39 - availableCapacity subtracts existing fleet units`() {
        val existing = listOf(
            Fleet(id = 10L, sessionId = 1L, factionId = 1L, planetId = 99L, currentUnits = 60),
            Fleet(id = 11L, sessionId = 1L, factionId = 1L, planetId = 99L, currentUnits = 40),
        )
        val repo = mockFleetRepoAt(99L, existing)
        val available = GridCapacityChecker.availableCapacity(
            repo, sessionId = 1L, factionId = 1L, destPlanetId = 99L, movingFleetId = null
        )
        assertEquals(200, available, "Grid with 60+40=100 units used should leave 200")
    }

    @Test
    fun `E39 - availableCapacity excludes the moving fleet from the sum`() {
        val existing = listOf(
            Fleet(id = 10L, sessionId = 1L, factionId = 1L, planetId = 99L, currentUnits = 60),
            Fleet(id = 11L, sessionId = 1L, factionId = 1L, planetId = 99L, currentUnits = 40),
        )
        val repo = mockFleetRepoAt(99L, existing)
        val available = GridCapacityChecker.availableCapacity(
            repo, sessionId = 1L, factionId = 1L, destPlanetId = 99L, movingFleetId = 10L
        )
        assertEquals(260, available, "Excluding the moving fleet #10 (60) leaves 260")
    }

    @Test
    fun `E39 - canEnterGrid rejects fleet that would exceed 300 cap`() {
        val existing = listOf(
            Fleet(id = 10L, sessionId = 1L, factionId = 1L, planetId = 99L, currentUnits = 280),
        )
        val repo = mockFleetRepoAt(99L, existing)
        val movingFleet = Fleet(
            id = 20L, sessionId = 1L, factionId = 1L, planetId = 5L, currentUnits = 30
        )
        assertFalse(
            GridCapacityChecker.canEnterGrid(
                repo, sessionId = 1L, factionId = 1L, destPlanetId = 99L, movingFleet = movingFleet
            ),
            "280 + 30 > 300 should be blocked"
        )
    }

    @Test
    fun `E39 - canEnterGrid allows fleet at exact 300 boundary`() {
        val existing = listOf(
            Fleet(id = 10L, sessionId = 1L, factionId = 1L, planetId = 99L, currentUnits = 270),
        )
        val repo = mockFleetRepoAt(99L, existing)
        val movingFleet = Fleet(
            id = 20L, sessionId = 1L, factionId = 1L, planetId = 5L, currentUnits = 30
        )
        assertTrue(
            GridCapacityChecker.canEnterGrid(
                repo, sessionId = 1L, factionId = 1L, destPlanetId = 99L, movingFleet = movingFleet
            ),
            "270 + 30 = 300 should be allowed (boundary)"
        )
    }

    @Test
    fun `E39 - canEnterGrid uses 1 unit minimum for solo officer (null fleet)`() {
        val existing299 = listOf(
            Fleet(id = 10L, sessionId = 1L, factionId = 1L, planetId = 99L, currentUnits = 299),
        )
        assertTrue(
            GridCapacityChecker.canEnterGrid(
                mockFleetRepoAt(99L, existing299),
                sessionId = 1L, factionId = 1L, destPlanetId = 99L, movingFleet = null
            ),
            "299 + 1 (solo officer) = 300 should be allowed at boundary"
        )

        val full = listOf(
            Fleet(id = 10L, sessionId = 1L, factionId = 1L, planetId = 99L, currentUnits = 300),
        )
        assertFalse(
            GridCapacityChecker.canEnterGrid(
                mockFleetRepoAt(99L, full),
                sessionId = 1L, factionId = 1L, destPlanetId = 99L, movingFleet = null
            ),
            "300 + 1 (solo officer) = 301 should be rejected"
        )
    }

    // ================================================================
    // Phase 24-09 (A3): 叙勲 Medal system
    // gin7 manual p34-35 — 階級ラダー 第三法則 勲章順
    // ================================================================

    @Test
    fun `A3 - Officer medalRank defaults to 0 and medalCount to 0`() {
        val officer = makeOfficer(listOf("PERSONAL"))
        assertEquals(0.toShort(), officer.medalRank)
        assertEquals(0.toShort(), officer.medalCount)
    }

    @Test
    fun `A3 - AwardDecorationCommand costs 160 PCP per manual p76`() {
        val dummy = makeOfficer(listOf("PERSONAL"))
        val cmd = AwardDecorationCommand(dummy, makeEnv(), null)
        assertEquals(160, cmd.getCommandPointCost(), "gin7 manual p76 — 叙勲 160 PCP")
        assertEquals(StatCategory.PCP, cmd.getCommandPoolType())
    }

    @Test
    fun `A3 - AwardDecorationCommand increments medal count and rank`() {
        val appointer = makeOfficer(listOf("PERSONAL", "CAPTAIN"))
        val target = makeOfficer(listOf("PERSONAL"))
        assertEquals(0.toShort(), target.medalCount)
        assertEquals(0.toShort(), target.medalRank)

        val cmd = AwardDecorationCommand(appointer, makeEnv(), mapOf("decoration" to "은성훈장"))
        cmd.destOfficer = target
        val result = runBlocking { cmd.run(Random.Default) }

        assertTrue(result.success)
        assertEquals(1.toShort(), target.medalCount, "First award bumps count to 1")
        assertEquals(1.toShort(), target.medalRank, "First award bumps rank to 1 (default = current+1)")
    }

    @Test
    fun `A3 - AwardDecorationCommand respects explicit medalRank arg`() {
        val appointer = makeOfficer(listOf("PERSONAL", "CAPTAIN"))
        val target = makeOfficer(listOf("PERSONAL"))

        val cmd = AwardDecorationCommand(
            appointer, makeEnv(),
            mapOf("decoration" to "제국 최고훈장", "medalRank" to 7)
        )
        cmd.destOfficer = target
        val result = runBlocking { cmd.run(Random.Default) }

        assertTrue(result.success)
        assertEquals(7.toShort(), target.medalRank, "Explicit medalRank should be applied")
        assertEquals(1.toShort(), target.medalCount)
    }

    @Test
    fun `A3 - AwardDecorationCommand preserves highest rank on multiple awards`() {
        val appointer = makeOfficer(listOf("PERSONAL", "CAPTAIN"))
        val target = makeOfficer(listOf("PERSONAL"))

        // Award rank 5 first, then rank 3 — the 3 should NOT lower the medalRank.
        val cmd1 = AwardDecorationCommand(appointer, makeEnv(), mapOf("medalRank" to 5))
        cmd1.destOfficer = target
        runBlocking { cmd1.run(Random.Default) }

        val cmd2 = AwardDecorationCommand(appointer, makeEnv(), mapOf("medalRank" to 3))
        cmd2.destOfficer = target
        runBlocking { cmd2.run(Random.Default) }

        assertEquals(5.toShort(), target.medalRank, "Highest medalRank wins")
        assertEquals(2.toShort(), target.medalCount, "Count still increments on each award")
    }

    // ================================================================
    // Phase 24-10 (E51): 반란군 진영 분리 배선
    // gin7 manual p11/p27 — 반란 → 반란군 진영 신설
    // ================================================================

    @Test
    fun `E51 - RebellionCommand costs 640 PCP per manual p27`() {
        val leader = makeOfficer(listOf("PERSONAL", "CAPTAIN"))
        val cmd = RebellionCommand(leader, makeEnv(), null)
        assertEquals(640, cmd.getCommandPointCost(), "gin7 manual p27 — 반란 640 PCP")
    }

    @Test
    fun `E51 - RebellionCommand fails when services are not injected`() {
        val leader = makeOfficer(listOf("PERSONAL"))
        val cmd = RebellionCommand(leader, makeEnv(), null)
        val result = runBlocking { cmd.run(Random.Default) }
        assertFalse(result.success, "Rebellion should fail with no services")
    }

    @Test
    fun `E51 - RebellionCommand creates rebel faction and moves leader`() {
        val imperial = Faction(
            id = 100L, sessionId = 1L, name = "은하제국",
            abbreviation = "제국", color = "#000000",
            factionType = "empire", chiefOfficerId = 999L,
        )
        val leader = Officer(
            id = 50L, sessionId = 1L, name = "반란주동자",
            factionId = 100L, planetId = 77L,
            positionCards = mutableListOf("PERSONAL", "CAPTAIN"),
            turnTime = OffsetDateTime.now(),
        )

        val factionRepo = mock(FactionRepository::class.java)
        val officerRepo = mock(OfficerRepository::class.java)
        val planetRepo = mock(PlanetRepository::class.java)
        val diplomacySvc = mock(DiplomacyService::class.java)

        `when`(factionRepo.findById(100L)).thenReturn(java.util.Optional.of(imperial))
        `when`(factionRepo.save<Faction>(org.mockito.ArgumentMatchers.any(Faction::class.java)))
            .thenAnswer { invocation ->
                val f = invocation.getArgument<Faction>(0)
                f.id = 200L
                f
            }

        val cmd = RebellionCommand(leader, makeEnv(), null)
        cmd.services = CommandServices(
            officerRepository = officerRepo,
            planetRepository = planetRepo,
            factionRepository = factionRepo,
            diplomacyService = diplomacySvc,
        )

        val result = runBlocking { cmd.run(Random.Default) }

        assertTrue(result.success, "Rebellion should succeed against a valid imperial faction")
        assertEquals(200L, leader.factionId, "Leader should be moved to the new rebel faction")
        assertEquals("EXECUTED", leader.meta["rebellionStatus"])
    }

    @Test
    fun `E51 - RebellionCommand rejects repeat execution`() {
        val imperial = Faction(
            id = 100L, sessionId = 1L, name = "은하제국",
            abbreviation = "제국", color = "#000000",
            factionType = "empire", chiefOfficerId = 999L,
        )
        val leader = Officer(
            id = 50L, sessionId = 1L, name = "반란주동자",
            factionId = 100L, planetId = 77L,
            positionCards = mutableListOf("PERSONAL", "CAPTAIN"),
            turnTime = OffsetDateTime.now(),
        )
        leader.meta["rebellionStatus"] = "EXECUTED"

        val factionRepo = mock(FactionRepository::class.java)
        val officerRepo = mock(OfficerRepository::class.java)
        val planetRepo = mock(PlanetRepository::class.java)
        val diplomacySvc = mock(DiplomacyService::class.java)

        `when`(factionRepo.findById(100L)).thenReturn(java.util.Optional.of(imperial))

        val cmd = RebellionCommand(leader, makeEnv(), null)
        cmd.services = CommandServices(
            officerRepository = officerRepo,
            planetRepository = planetRepo,
            factionRepository = factionRepo,
            diplomacyService = diplomacySvc,
        )

        val result = runBlocking { cmd.run(Random.Default) }
        assertFalse(result.success, "Second rebellion attempt should fail")
    }

    @Test
    fun `E51 - RebellionCommand rejects officer already in rebel faction`() {
        val rebel = Faction(
            id = 200L, sessionId = 1L, name = "반란군",
            abbreviation = "반", color = "#FFA500",
            factionType = "rebel", chiefOfficerId = 50L,
        )
        val officer = Officer(
            id = 50L, sessionId = 1L, name = "반란주동자",
            factionId = 200L, planetId = 77L,
            positionCards = mutableListOf("PERSONAL", "CAPTAIN"),
            turnTime = OffsetDateTime.now(),
        )

        val factionRepo = mock(FactionRepository::class.java)
        val officerRepo = mock(OfficerRepository::class.java)
        val planetRepo = mock(PlanetRepository::class.java)
        val diplomacySvc = mock(DiplomacyService::class.java)

        `when`(factionRepo.findById(200L)).thenReturn(java.util.Optional.of(rebel))

        val cmd = RebellionCommand(officer, makeEnv(), null)
        cmd.services = CommandServices(
            officerRepository = officerRepo,
            planetRepository = planetRepo,
            factionRepository = factionRepo,
            diplomacyService = diplomacySvc,
        )

        val result = runBlocking { cmd.run(Random.Default) }
        assertFalse(result.success, "Already-rebel officer cannot rebel again")
    }

    private fun anyLong(): Long = org.mockito.ArgumentMatchers.anyLong()
    private fun eqLong(value: Long): Long = org.mockito.ArgumentMatchers.eq(value)
}
