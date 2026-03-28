package com.openlogh.engine

import com.openlogh.entity.Officer
import com.openlogh.repository.OfficerRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

/**
 * SMGT-01: Offline officer persistence with CP recovery.
 *
 * Verifies that recoverAllCp iterates ALL officers in a session regardless
 * of online/offline status, and that officers in tactical battle skip recovery.
 */
class CommandPointServiceTest {
    private lateinit var officerRepository: OfficerRepository
    private lateinit var service: CommandPointService

    @BeforeEach
    fun setup() {
        officerRepository = mock(OfficerRepository::class.java)
        service = CommandPointService(officerRepository)
    }

    private fun makeOfficer(
        id: Long = 1L,
        sessionId: Long = 100L,
        userId: Long? = 1L,
        locationState: String = "planet",
        pcp: Int = 5,
        mcp: Int = 5,
        politics: Short = 50,
        administration: Short = 50,
        command: Short = 50,
        leadership: Short = 50,
    ): Officer = Officer(
        id = id,
        sessionId = sessionId,
        userId = userId,
        locationState = locationState,
        pcp = pcp,
        mcp = mcp,
        politics = politics,
        administration = administration,
        command = command,
        leadership = leadership,
    )

    @Test
    fun `recoverCp increases pcp and mcp for officer on planet`() {
        // politics=50, administration=50 -> pcpRecovery = 2 + 50/20 + 50/20 = 2+2+2 = 6
        // command=50, leadership=50 -> mcpRecovery = 2 + 50/20 + 50/20 = 2+2+2 = 6
        val officer = makeOfficer(pcp = 5, mcp = 5)

        service.recoverCp(officer)

        assertEquals(11, officer.pcp, "PCP should increase by 6 (BASE_RECOVERY + politics/20 + administration/20)")
        assertEquals(11, officer.mcp, "MCP should increase by 6 (BASE_RECOVERY + command/20 + leadership/20)")
    }

    @Test
    fun `recoverCp does NOT change CP for officer in tactical battle`() {
        val officer = makeOfficer(locationState = "tactical", pcp = 5, mcp = 5)

        service.recoverCp(officer)

        assertEquals(5, officer.pcp, "PCP should not change during tactical battle")
        assertEquals(5, officer.mcp, "MCP should not change during tactical battle")
    }

    @Test
    fun `recoverAllCp recovers CP for all officers including NPCs and offline players`() {
        val sessionId = 100L
        val onlinePlayer = makeOfficer(id = 1L, sessionId = sessionId, userId = 10L, pcp = 5, mcp = 5)
        val offlinePlayer = makeOfficer(id = 2L, sessionId = sessionId, userId = 20L, pcp = 3, mcp = 3)
        val npcOfficer = makeOfficer(id = 3L, sessionId = sessionId, userId = null, pcp = 2, mcp = 2)
        val tacticalOfficer = makeOfficer(id = 4L, sessionId = sessionId, userId = 30L, locationState = "tactical", pcp = 5, mcp = 5)

        val allOfficers = listOf(onlinePlayer, offlinePlayer, npcOfficer, tacticalOfficer)
        `when`(officerRepository.findBySessionId(sessionId)).thenReturn(allOfficers)
        `when`(officerRepository.saveAll(allOfficers)).thenReturn(allOfficers)

        service.recoverAllCp(sessionId)

        // Online player recovers: 5 + 6 = 11
        assertEquals(11, onlinePlayer.pcp, "Online player PCP should recover")
        assertEquals(11, onlinePlayer.mcp, "Online player MCP should recover")

        // Offline player recovers: 3 + 6 = 9
        assertEquals(9, offlinePlayer.pcp, "Offline player PCP should recover")
        assertEquals(9, offlinePlayer.mcp, "Offline player MCP should recover")

        // NPC recovers: 2 + 6 = 8
        assertEquals(8, npcOfficer.pcp, "NPC officer PCP should recover")
        assertEquals(8, npcOfficer.mcp, "NPC officer MCP should recover")

        // Tactical officer does NOT recover
        assertEquals(5, tacticalOfficer.pcp, "Tactical officer PCP should NOT recover")
        assertEquals(5, tacticalOfficer.mcp, "Tactical officer MCP should NOT recover")

        // Verify saveAll was called with all officers
        verify(officerRepository).saveAll(allOfficers)
    }

    @Test
    fun `recoverCp caps recovery at max CP`() {
        // Max PCP = BASE_MAX_CP + politics/5 + administration/10 = 20 + 50/5 + 50/10 = 20 + 10 + 5 = 35
        // Max MCP = BASE_MAX_CP + command/5 + leadership/10 = 20 + 50/5 + 50/10 = 20 + 10 + 5 = 35
        val officer = makeOfficer(pcp = 35, mcp = 35)

        service.recoverCp(officer)

        assertEquals(35, officer.pcp, "PCP should not exceed max (35)")
        assertEquals(35, officer.mcp, "MCP should not exceed max (35)")
    }

    @Test
    fun `recoverCp recovery amount scales with politics and administration stats`() {
        // Low stats: politics=0, administration=0 -> pcpRecovery = 2 + 0 + 0 = 2
        val lowStatOfficer = makeOfficer(pcp = 5, mcp = 5, politics = 0, administration = 0, command = 0, leadership = 0)
        service.recoverCp(lowStatOfficer)
        assertEquals(7, lowStatOfficer.pcp, "Low-stat officer should recover only BASE_RECOVERY (2)")
        assertEquals(7, lowStatOfficer.mcp, "Low-stat officer should recover only BASE_RECOVERY (2)")

        // High stats: politics=100, administration=100 -> pcpRecovery = 2 + 100/20 + 100/20 = 2+5+5 = 12
        val highStatOfficer = makeOfficer(pcp = 5, mcp = 5, politics = 100, administration = 100, command = 100, leadership = 100)
        service.recoverCp(highStatOfficer)
        assertEquals(17, highStatOfficer.pcp, "High-stat officer should recover 12 (2 + 5 + 5)")
        assertEquals(17, highStatOfficer.mcp, "High-stat officer should recover 12 (2 + 5 + 5)")
    }
}
