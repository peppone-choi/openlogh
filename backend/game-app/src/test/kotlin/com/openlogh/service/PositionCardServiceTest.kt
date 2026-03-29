package com.openlogh.service

import com.openlogh.engine.organization.CommandGating
import com.openlogh.engine.organization.PositionCardType
import com.openlogh.entity.PositionCard
import com.openlogh.repository.PositionCardRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

/**
 * PositionCardService unit tests.
 *
 * Verifies the facade correctly delegates CRUD operations to the
 * relational position_card table instead of officer.meta JSONB.
 */
class PositionCardServiceTest {

    private lateinit var positionCardRepository: PositionCardRepository
    private lateinit var service: PositionCardService

    @BeforeEach
    fun setup() {
        positionCardRepository = mock(PositionCardRepository::class.java)
        service = PositionCardService(positionCardRepository)
    }

    private fun makeCard(
        id: Long = 0L,
        officerId: Long = 1L,
        sessionId: Long = 100L,
        positionType: String = "personal",
        positionNameKo: String = "개인",
    ): PositionCard = PositionCard(
        id = id,
        officerId = officerId,
        sessionId = sessionId,
        positionType = positionType,
        positionNameKo = positionNameKo,
    )

    // Test 1: getHeldCardCodes returns default cards when no cards exist
    @Test
    fun `getHeldCardCodes returns default cards when no cards exist for officer`() {
        `when`(positionCardRepository.findBySessionIdAndOfficerId(100L, 1L))
            .thenReturn(emptyList())

        val result = service.getHeldCardCodes(100L, 1L)

        assertEquals(listOf("personal", "captain"), result)
    }

    // Test 2: getHeldCardCodes returns actual position types when cards exist
    @Test
    fun `getHeldCardCodes returns actual position types when cards exist`() {
        val cards = listOf(
            makeCard(positionType = "personal"),
            makeCard(positionType = "captain"),
            makeCard(positionType = "fleet_commander"),
        )
        `when`(positionCardRepository.findBySessionIdAndOfficerId(100L, 1L))
            .thenReturn(cards)

        val result = service.getHeldCardCodes(100L, 1L)

        assertEquals(listOf("personal", "captain", "fleet_commander"), result)
    }

    // Test 3: appointPosition creates new card row; calling again with same type is idempotent
    @Test
    fun `appointPosition creates new card row and is idempotent on duplicate`() {
        // First call: no existing cards
        `when`(positionCardRepository.findBySessionIdAndOfficerId(100L, 1L))
            .thenReturn(emptyList())

        service.appointPosition(100L, 1L, PositionCardType.FLEET_COMMANDER)

        verify(positionCardRepository).save(argThat<PositionCard> { card ->
            card.officerId == 1L &&
            card.sessionId == 100L &&
            card.positionType == "fleet_commander"
        })

        // Second call: card already exists
        reset(positionCardRepository)
        val existingCards = listOf(
            makeCard(positionType = "fleet_commander", positionNameKo = "함대사령관"),
        )
        `when`(positionCardRepository.findBySessionIdAndOfficerId(100L, 1L))
            .thenReturn(existingCards)

        service.appointPosition(100L, 1L, PositionCardType.FLEET_COMMANDER)

        verify(positionCardRepository, never()).save(any())
    }

    // Test 4: dismissPosition deletes the matching card row; no-op if card not held
    @Test
    fun `dismissPosition deletes matching card and is no-op if not held`() {
        val fleetCard = makeCard(id = 10L, positionType = "fleet_commander")
        `when`(positionCardRepository.findBySessionIdAndOfficerId(100L, 1L))
            .thenReturn(listOf(fleetCard))

        service.dismissPosition(100L, 1L, "fleet_commander")

        verify(positionCardRepository).delete(fleetCard)

        // No-op case: card not held
        reset(positionCardRepository)
        `when`(positionCardRepository.findBySessionIdAndOfficerId(100L, 1L))
            .thenReturn(emptyList())

        service.dismissPosition(100L, 1L, "fleet_commander")

        verify(positionCardRepository, never()).delete(any())
    }

    // Test 5: revokeOnRankChange deletes all cards except personal, captain, and fief_ prefixed
    @Test
    fun `revokeOnRankChange deletes all except personal captain and fief cards`() {
        val cards = listOf(
            makeCard(id = 1L, positionType = "personal"),
            makeCard(id = 2L, positionType = "captain"),
            makeCard(id = 3L, positionType = "fleet_commander"),
            makeCard(id = 4L, positionType = "personnel_chief"),
            makeCard(id = 5L, positionType = "fief_lord"),
        )
        `when`(positionCardRepository.findBySessionIdAndOfficerId(100L, 1L))
            .thenReturn(cards)

        service.revokeOnRankChange(100L, 1L)

        // Should delete fleet_commander and personnel_chief, keep personal, captain, fief_lord
        verify(positionCardRepository).deleteAll(argThat<List<PositionCard>> { toDelete ->
            toDelete.size == 2 &&
            toDelete.any { it.positionType == "fleet_commander" } &&
            toDelete.any { it.positionType == "personnel_chief" }
        })
    }

    // Test 6: canAddCard returns false when officer already holds 16 cards
    @Test
    fun `getCardCount returns correct count and appointPosition respects MAX_CARDS limit`() {
        val fullCards = (1..16).map { i ->
            makeCard(id = i.toLong(), positionType = "card_$i")
        }
        `when`(positionCardRepository.findBySessionIdAndOfficerId(100L, 1L))
            .thenReturn(fullCards)

        val count = service.getCardCount(100L, 1L)
        assertEquals(16, count)

        // Attempt to appoint when at max should be no-op
        service.appointPosition(100L, 1L, PositionCardType.FLEET_COMMANDER)
        verify(positionCardRepository, never()).save(any())
    }
}
