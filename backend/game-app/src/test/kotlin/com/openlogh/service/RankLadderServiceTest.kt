package com.openlogh.service

import com.openlogh.engine.organization.PositionCardType
import com.openlogh.entity.Officer
import com.openlogh.repository.OfficerRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

/**
 * RankLadderService unit tests.
 *
 * Validates rank ladder 5-law sort, auto promotion/demotion,
 * PositionCardService integration, per-rank limits, and personnel authority.
 */
class RankLadderServiceTest {

    private lateinit var officerRepository: OfficerRepository
    private lateinit var positionCardService: PositionCardService
    private lateinit var service: RankLadderService

    @BeforeEach
    fun setup() {
        officerRepository = mock(OfficerRepository::class.java)
        positionCardService = mock(PositionCardService::class.java)
        service = RankLadderService(officerRepository, positionCardService)
    }

    private fun makeOfficer(
        id: Long = 1L,
        sessionId: Long = 100L,
        factionId: Long = 1L,
        rank: Int = 4,
        experience: Int = 50,
        peerage: String = "none",
        influence: Int = 0,
        leadership: Int = 50,
        command: Int = 50,
        intelligence: Int = 50,
        politics: Int = 50,
        administration: Int = 50,
        mobility: Int = 50,
        attack: Int = 50,
        defense: Int = 50,
        name: String = "Officer$id",
    ): Officer = Officer(
        id = id,
        sessionId = sessionId,
        factionId = factionId,
        rank = rank.toShort(),
        experience = experience,
        peerage = peerage,
        influence = influence,
        leadership = leadership.toShort(),
        command = command.toShort(),
        intelligence = intelligence.toShort(),
        politics = politics.toShort(),
        administration = administration.toShort(),
        mobility = mobility.toShort(),
        attack = attack.toShort(),
        defense = defense.toShort(),
        name = name,
    )

    // Test 1: getRankLadder returns officers sorted by 5 laws
    // (merit desc, peerage desc, medal count desc, influence desc, stat total desc)
    @Test
    fun `getRankLadder returns officers sorted by 5-law priority`() {
        val o1 = makeOfficer(id = 1, experience = 80, name = "HighMerit")
        val o2 = makeOfficer(id = 2, experience = 80, peerage = "duke", name = "DukeSameMerit")
        val o3 = makeOfficer(id = 3, experience = 50, name = "LowMerit")
        val o4 = makeOfficer(id = 4, experience = 80, peerage = "duke", influence = 100, name = "DukeHighInfluence")
        // o2 and o4 have same merit, same peerage; o4 has higher influence
        // o4's meta has medalCount = 5, o2's meta has medalCount = 5 (same)
        o2.meta["medalCount"] = 5
        o4.meta["medalCount"] = 5

        `when`(officerRepository.findBySessionId(100L)).thenReturn(listOf(o1, o2, o3, o4))

        val ladder = service.getRankLadder(100L, 1L, 4)

        // Order: o2 and o4 tied on merit(80), peerage(duke/6), medalCount(5)
        // then sorted by influence: o4(100) > o2(0)
        // o1 has merit=80, peerage=none(0) -> after the dukes
        // o3 has merit=50 -> last
        assertEquals(4, ladder.size)
        assertEquals("DukeHighInfluence", ladder[0].name)
        assertEquals("DukeSameMerit", ladder[1].name)
        assertEquals("HighMerit", ladder[2].name)
        assertEquals("LowMerit", ladder[3].name)
    }

    // Test 2: processAutoPromotion promotes ladder #1 at colonel-and-below ranks,
    // resets merit to ladder average, calls revokeOnRankChange
    @Test
    fun `processAutoPromotion promotes ladder top and calls revokeOnRankChange`() {
        // Use rank 4 (max for auto-promotion) so cascading doesn't occur
        val o1 = makeOfficer(id = 1, rank = 4, experience = 100, name = "Top")
        val o2 = makeOfficer(id = 2, rank = 4, experience = 50, name = "Second")
        val o3 = makeOfficer(id = 3, rank = 4, experience = 20, name = "Third")

        `when`(officerRepository.findBySessionId(100L)).thenReturn(listOf(o1, o2, o3))

        service.processAutoPromotion(100L)

        // o1 should be promoted from rank 4 -> 5
        assertEquals(5, o1.rank.toInt())
        // Merit set to average of ladder: (100+50+20)/3 = 56
        assertEquals(56, o1.experience)
        // revokeOnRankChange should be called for the promoted officer
        verify(positionCardService).revokeOnRankChange(100L, 1L)
        verify(officerRepository).save(o1)
    }

    // Test 3: processAutoPromotion resets merit to average correctly
    @Test
    fun `processAutoPromotion resets merit correctly after promotion`() {
        // Use rank 4 (max) to avoid cascading
        val o1 = makeOfficer(id = 1, rank = 4, experience = 200, name = "Rookie")
        `when`(officerRepository.findBySessionId(100L)).thenReturn(listOf(o1))

        service.processAutoPromotion(100L)

        assertEquals(5, o1.rank.toInt())
        // Average of single-officer ladder = 200
        assertEquals(200, o1.experience)
        verify(positionCardService).revokeOnRankChange(100L, 1L)
    }

    // Test 4: Auto demotion calls revokeOnRankChange and sets experience to 100
    @Test
    fun `processAutoDemotion calls revokeOnRankChange and sets experience to 100`() {
        // Create excess officers at rank 10 (limit is 5)
        val officers = (1L..7L).map { id ->
            makeOfficer(id = id, rank = 10, experience = (100 - id * 10).toInt(), name = "Marshal$id")
        }
        `when`(officerRepository.findBySessionId(100L)).thenReturn(officers)

        service.processAutoDemotion(100L)

        // 7 at rank 10, limit 5 -> 2 excess should be demoted (last 2 in ladder)
        val demoted = officers.filter { it.rank.toInt() == 9 }
        assertEquals(2, demoted.size)
        // Demoted officers should have experience = 100
        demoted.forEach { assertEquals(100, it.experience) }
        // revokeOnRankChange should be called for each demoted officer
        verify(positionCardService, times(2)).revokeOnRankChange(eq(100L), anyLong())
    }

    // Test 5: Per-rank limit check: promotion blocked when rank count >= RANK_LIMITS[targetRank]
    @Test
    fun `canPromote returns false when target rank is full`() {
        // Rank 10 (marshal) has limit of 5
        val marshals = (1L..5L).map { makeOfficer(id = it, rank = 10) }
        `when`(officerRepository.findBySessionId(100L)).thenReturn(marshals)

        // Promoter holds emperor card -> has authority
        `when`(positionCardService.getHeldCardCodes(100L, 99L))
            .thenReturn(listOf("personal", "captain", "emperor"))

        val result = service.canPromote(100L, 99L, 1L, 10)
        assertFalse(result)
    }

    // Test 6: Personnel authority validation via PositionCard
    @Test
    fun `canPromote checks personnel authority via position cards`() {
        `when`(officerRepository.findBySessionId(100L)).thenReturn(emptyList())

        // No personnel card -> cannot promote
        `when`(positionCardService.getHeldCardCodes(100L, 1L))
            .thenReturn(listOf("personal", "captain"))
        assertFalse(service.canPromote(100L, 1L, 1L, 5))

        // Emperor card -> can promote to any rank
        `when`(positionCardService.getHeldCardCodes(100L, 2L))
            .thenReturn(listOf("personal", "captain", "emperor"))
        assertTrue(service.canPromote(100L, 2L, 1L, 10))

        // Military minister -> can promote up to rank 8 (admiral)
        `when`(positionCardService.getHeldCardCodes(100L, 3L))
            .thenReturn(listOf("personal", "captain", "military_minister"))
        assertTrue(service.canPromote(100L, 3L, 1L, 8))
        assertFalse(service.canPromote(100L, 3L, 1L, 9))

        // Personnel chief -> can promote up to rank 6 (rear admiral)
        `when`(positionCardService.getHeldCardCodes(100L, 4L))
            .thenReturn(listOf("personal", "captain", "personnel_chief"))
        assertTrue(service.canPromote(100L, 4L, 1L, 6))
        assertFalse(service.canPromote(100L, 4L, 1L, 7))

        // Chairman -> can promote to any rank (like emperor)
        `when`(positionCardService.getHeldCardCodes(100L, 5L))
            .thenReturn(listOf("personal", "captain", "chairman"))
        assertTrue(service.canPromote(100L, 5L, 1L, 10))
    }
}
