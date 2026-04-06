package com.openlogh.service

import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandExecutor
import com.openlogh.command.CommandResult
import com.openlogh.entity.CommandProposal
import com.openlogh.entity.Faction
import com.openlogh.entity.Officer
import com.openlogh.entity.Planet
import com.openlogh.entity.SessionState
import com.openlogh.repository.CommandProposalRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.SessionStateRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import kotlin.random.Random
import java.time.OffsetDateTime
import java.util.Optional

/**
 * Fake CommandExecutor for testing — always returns success.
 * Used because Mockito cannot stub Kotlin suspend functions without mockito-kotlin.
 */
private class SuccessCommandExecutor : CommandExecutor(
    commandRegistry = org.mockito.Mockito.mock(com.openlogh.command.CommandRegistry::class.java),
    officerRepository = org.mockito.Mockito.mock(com.openlogh.repository.OfficerRepository::class.java),
    planetRepository = org.mockito.Mockito.mock(com.openlogh.repository.PlanetRepository::class.java),
    factionRepository = org.mockito.Mockito.mock(com.openlogh.repository.FactionRepository::class.java),
    diplomacyRepository = org.mockito.Mockito.mock(com.openlogh.repository.DiplomacyRepository::class.java),
    diplomacyService = org.mockito.Mockito.mock(com.openlogh.engine.DiplomacyService::class.java),
    mapService = org.mockito.Mockito.mock(com.openlogh.service.MapService::class.java),
    statChangeService = org.mockito.Mockito.mock(com.openlogh.engine.StatChangeService::class.java),
    modifierService = org.mockito.Mockito.mock(com.openlogh.engine.modifier.ModifierService::class.java),
    messageService = org.mockito.Mockito.mock(com.openlogh.service.MessageService::class.java),
) {
    var lastActionCode: String? = null

    override suspend fun executeOfficerCommand(
        actionCode: String,
        general: Officer,
        env: CommandEnv,
        arg: Map<String, Any>?,
        city: Planet?,
        nation: Faction?,
        rng: Random,
    ): CommandResult {
        lastActionCode = actionCode
        return CommandResult(success = true, logs = listOf("$actionCode 완료 (stub)"))
    }
}

@ExtendWith(MockitoExtension::class)
class CommandProposalServiceTest {

    @Mock
    lateinit var proposalRepository: CommandProposalRepository

    @Mock
    lateinit var officerRepository: OfficerRepository

    @Mock
    lateinit var gameEventService: GameEventService

    @Mock
    lateinit var sessionStateRepository: SessionStateRepository

    private val fakeCommandExecutor = SuccessCommandExecutor()

    private lateinit var proposalService: CommandProposalService

    @BeforeEach
    fun setUp() {
        proposalService = CommandProposalService(
            proposalRepository = proposalRepository,
            officerRepository = officerRepository,
            commandExecutor = fakeCommandExecutor,
            gameEventService = gameEventService,
            sessionStateRepository = sessionStateRepository,
        )
    }

    private fun makeOfficer(id: Long, level: Short): Officer = Officer(
        id = id,
        sessionId = 1L,
        name = "제독$id",
        factionId = 1L,
        planetId = 10L,
        officerLevel = level,
        positionCards = mutableListOf("PERSONAL", "CAPTAIN"),
        turnTime = OffsetDateTime.now(),
    )

    private fun makeSession(): SessionState = SessionState(
        id = 1,
        name = "테스트세션",
        currentYear = 796,
        currentMonth = 1,
        realtimeMode = true,
    )

    private fun makePendingProposal(
        id: Long = 100L,
        proposerId: Long = 1L,
        approverId: Long = 2L,
        commandCode: String = "워프항행",
    ): CommandProposal = CommandProposal(
        id = id,
        sessionId = 1L,
        proposerId = proposerId,
        approverId = approverId,
        commandCode = commandCode,
        args = mutableMapOf(),
        status = "PENDING",
    )

    // Test 1: 하급자(level=3)가 상급자(level=5)에게 제안 → PENDING
    @Test
    fun `createProposal stores PENDING when proposer rank is lower than approver`() {
        val proposer = makeOfficer(1L, 3)
        val approver = makeOfficer(2L, 5)
        val savedProposal = makePendingProposal()

        `when`(officerRepository.findById(1L)).thenReturn(Optional.of(proposer))
        `when`(officerRepository.findById(2L)).thenReturn(Optional.of(approver))
        `when`(proposalRepository.save(any(CommandProposal::class.java))).thenReturn(savedProposal)

        val result = proposalService.createProposal(1L, 1L, 2L, "워프항행", emptyMap())

        assertEquals("PENDING", result.status)
        verify(proposalRepository).save(any(CommandProposal::class.java))
    }

    // Test 2: 동급자(level=5 vs level=5) 제안 → IllegalArgumentException
    @Test
    fun `createProposal throws IllegalArgumentException when proposer and approver have same rank`() {
        val proposer = makeOfficer(1L, 5)
        val approver = makeOfficer(2L, 5)

        `when`(officerRepository.findById(1L)).thenReturn(Optional.of(proposer))
        `when`(officerRepository.findById(2L)).thenReturn(Optional.of(approver))

        assertThrows<IllegalArgumentException> {
            proposalService.createProposal(1L, 1L, 2L, "워프항행", emptyMap())
        }
    }

    // Test 3: 상급자(level=6)가 하급자(level=5)에게 제안 → IllegalArgumentException
    @Test
    fun `createProposal throws IllegalArgumentException when proposer rank is higher than approver`() {
        val proposer = makeOfficer(1L, 6)
        val approver = makeOfficer(2L, 5)

        `when`(officerRepository.findById(1L)).thenReturn(Optional.of(proposer))
        `when`(officerRepository.findById(2L)).thenReturn(Optional.of(approver))

        assertThrows<IllegalArgumentException> {
            proposalService.createProposal(1L, 1L, 2L, "워프항행", emptyMap())
        }
    }

    // Test 4: 상급자가 승인하면 APPROVED로 상태 전환되고 commandExecutor 호출됨
    @Test
    fun `approveProposal sets status to APPROVED and calls commandExecutor`() {
        val proposal = makePendingProposal()
        val proposer = makeOfficer(1L, 3)
        val session = makeSession()

        `when`(proposalRepository.findById(100L)).thenReturn(Optional.of(proposal))
        `when`(officerRepository.findById(1L)).thenReturn(Optional.of(proposer))
        `when`(sessionStateRepository.findById(1.toShort())).thenReturn(Optional.of(session))
        `when`(proposalRepository.save(any(CommandProposal::class.java))).thenAnswer { it.arguments[0] }

        val result = runBlocking { proposalService.approveProposal(1L, 100L, 2L) }

        assertEquals("APPROVED", result.status)
        assertEquals("워프항행", fakeCommandExecutor.lastActionCode)
    }

    // Test 5: 상급자가 거부하면 REJECTED로 상태 전환됨
    @Test
    fun `rejectProposal sets status to REJECTED`() {
        val proposal = makePendingProposal()

        `when`(proposalRepository.findById(100L)).thenReturn(Optional.of(proposal))
        `when`(proposalRepository.save(any(CommandProposal::class.java))).thenAnswer { it.arguments[0] }

        val result = proposalService.rejectProposal(1L, 100L, 2L)

        assertEquals("REJECTED", result.status)
    }

    // Test 6: 이미 APPROVED인 제안에 rejectProposal → IllegalArgumentException
    @Test
    fun `rejectProposal throws IllegalArgumentException when proposal is already APPROVED`() {
        val proposal = makePendingProposal().also { it.status = "APPROVED" }

        `when`(proposalRepository.findById(100L)).thenReturn(Optional.of(proposal))

        assertThrows<IllegalArgumentException> {
            proposalService.rejectProposal(1L, 100L, 2L)
        }
    }
}
