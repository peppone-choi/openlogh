package com.openlogh.service

import com.openlogh.entity.Faction
import com.openlogh.entity.Officer
import com.openlogh.repository.FactionRepository
import com.openlogh.repository.OfficerRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.OffsetDateTime
import java.util.Optional

class FactionJoinServiceTest {
    private lateinit var officerRepository: OfficerRepository
    private lateinit var factionRepository: FactionRepository
    private lateinit var service: FactionJoinService

    private val sessionId = 1L
    private val empireFactionId = 10L
    private val allianceFactionId = 20L
    private val now = OffsetDateTime.now()

    @BeforeEach
    fun setUp() {
        officerRepository = mock(OfficerRepository::class.java)
        factionRepository = mock(FactionRepository::class.java)
        service = FactionJoinService(officerRepository, factionRepository)

        val empireFaction = Faction(id = empireFactionId, sessionId = sessionId, name = "은하제국")
        val allianceFaction = Faction(id = allianceFactionId, sessionId = sessionId, name = "자유행성동맹")
        `when`(factionRepository.findById(empireFactionId)).thenReturn(Optional.of(empireFaction))
        `when`(factionRepository.findById(allianceFactionId)).thenReturn(Optional.of(allianceFaction))
    }

    private fun playerOfficer(id: Long, factionId: Long, userId: Long): Officer {
        return Officer(
            id = id,
            sessionId = sessionId,
            userId = userId,
            factionId = factionId,
            name = "officer-$id",
            turnTime = now,
        )
    }

    private fun npcOfficer(id: Long, factionId: Long): Officer {
        return Officer(
            id = id,
            sessionId = sessionId,
            userId = null,
            factionId = factionId,
            name = "npc-$id",
            turnTime = now,
        )
    }

    @Test
    fun `canJoinFaction returns allowed when faction has less than 60 percent of total players`() {
        // 2 Empire + 2 Alliance = 4 total. Adding Empire -> 3/5 = 60% = exactly at limit, allowed
        val officers = listOf(
            playerOfficer(1, empireFactionId, 101),
            playerOfficer(2, empireFactionId, 102),
            playerOfficer(3, allianceFactionId, 103),
            playerOfficer(4, allianceFactionId, 104),
        )
        `when`(officerRepository.findBySessionId(sessionId)).thenReturn(officers)

        val result = service.canJoinFaction(sessionId, empireFactionId)

        assertTrue(result.allowed)
        assertNull(result.reason)
    }

    @Test
    fun `canJoinFaction returns blocked with Korean message when faction reaches 60 percent cap`() {
        // 3 Empire + 1 Alliance = 4 total. Adding Empire -> 4/5 = 80% > 60%, blocked
        val officers = listOf(
            playerOfficer(1, empireFactionId, 101),
            playerOfficer(2, empireFactionId, 102),
            playerOfficer(3, empireFactionId, 103),
            playerOfficer(4, allianceFactionId, 104),
        )
        `when`(officerRepository.findBySessionId(sessionId)).thenReturn(officers)

        val result = service.canJoinFaction(sessionId, empireFactionId)

        assertFalse(result.allowed)
        assertNotNull(result.reason)
        assertTrue(result.reason!!.contains("인원이 가득 찼습니다"))
        assertTrue(result.reason!!.contains("은하제국"))
    }

    @Test
    fun `canJoinFaction allows any faction when total players is 0`() {
        `when`(officerRepository.findBySessionId(sessionId)).thenReturn(emptyList())

        val result = service.canJoinFaction(sessionId, empireFactionId)

        assertTrue(result.allowed)
    }

    @Test
    fun `canJoinFaction allows any faction when total players is 1`() {
        val officers = listOf(playerOfficer(1, empireFactionId, 101))
        `when`(officerRepository.findBySessionId(sessionId)).thenReturn(officers)

        val result = service.canJoinFaction(sessionId, empireFactionId)

        assertTrue(result.allowed)
    }

    @Test
    fun `with 4 Empire and 0 Alliance a 5th Empire join is blocked`() {
        // 4 Empire + 0 Alliance = 4 total. Adding Empire -> 5/5 = 100% > 60%, blocked
        val officers = listOf(
            playerOfficer(1, empireFactionId, 101),
            playerOfficer(2, empireFactionId, 102),
            playerOfficer(3, empireFactionId, 103),
            playerOfficer(4, empireFactionId, 104),
        )
        `when`(officerRepository.findBySessionId(sessionId)).thenReturn(officers)

        val result = service.canJoinFaction(sessionId, empireFactionId)

        assertFalse(result.allowed)
        assertTrue(result.reason!!.contains("인원이 가득 찼습니다"))
    }

    @Test
    fun `with 3 Empire and 2 Alliance a 6th Empire join is blocked`() {
        // 3 Empire + 2 Alliance = 5. Adding Empire -> 4/6 = 67% > 60%, blocked
        val officers = listOf(
            playerOfficer(1, empireFactionId, 101),
            playerOfficer(2, empireFactionId, 102),
            playerOfficer(3, empireFactionId, 103),
            playerOfficer(4, allianceFactionId, 104),
            playerOfficer(5, allianceFactionId, 105),
        )
        `when`(officerRepository.findBySessionId(sessionId)).thenReturn(officers)

        val result = service.canJoinFaction(sessionId, empireFactionId)

        assertFalse(result.allowed)
    }

    @Test
    fun `with 3 Empire and 2 Alliance a 6th Alliance join is allowed`() {
        // 3 Empire + 2 Alliance = 5. Adding Alliance -> 3/6 = 50% < 60%, allowed
        val officers = listOf(
            playerOfficer(1, empireFactionId, 101),
            playerOfficer(2, empireFactionId, 102),
            playerOfficer(3, empireFactionId, 103),
            playerOfficer(4, allianceFactionId, 104),
            playerOfficer(5, allianceFactionId, 105),
        )
        `when`(officerRepository.findBySessionId(sessionId)).thenReturn(officers)

        val result = service.canJoinFaction(sessionId, allianceFactionId)

        assertTrue(result.allowed)
    }

    @Test
    fun `only officers with non-null userId count as player officers`() {
        // 3 Empire players + 5 Empire NPCs + 2 Alliance players = 5 player total
        // Adding Empire -> 4/6 player = 67% > 60%, blocked
        // NPCs should be excluded from the ratio calculation
        val officers = listOf(
            playerOfficer(1, empireFactionId, 101),
            playerOfficer(2, empireFactionId, 102),
            playerOfficer(3, empireFactionId, 103),
            npcOfficer(4, empireFactionId),
            npcOfficer(5, empireFactionId),
            npcOfficer(6, empireFactionId),
            npcOfficer(7, empireFactionId),
            npcOfficer(8, empireFactionId),
            playerOfficer(9, allianceFactionId, 109),
            playerOfficer(10, allianceFactionId, 110),
        )
        `when`(officerRepository.findBySessionId(sessionId)).thenReturn(officers)

        val result = service.canJoinFaction(sessionId, empireFactionId)

        assertFalse(result.allowed)
    }

    @Test
    fun `getFactionCounts returns per-faction player counts excluding NPCs`() {
        val officers = listOf(
            playerOfficer(1, empireFactionId, 101),
            playerOfficer(2, empireFactionId, 102),
            playerOfficer(3, allianceFactionId, 103),
            npcOfficer(4, empireFactionId),
            npcOfficer(5, allianceFactionId),
        )
        `when`(officerRepository.findBySessionId(sessionId)).thenReturn(officers)

        val counts = service.getFactionCounts(sessionId)

        assertEquals(2, counts[empireFactionId])
        assertEquals(1, counts[allianceFactionId])
        // NPCs should not be counted
        assertEquals(2, counts.size)
    }
}
