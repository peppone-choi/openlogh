package com.openlogh.service

import com.openlogh.entity.Faction
import com.openlogh.entity.Officer
import com.openlogh.repository.FactionRepository
import com.openlogh.repository.OfficerRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.Optional

/**
 * SESS-07 + D-07/D-08/D-09: Re-entry restriction tests.
 *
 * D-07: "퇴장" means character death only, not voluntary logout.
 * D-08: No cooldown on re-entry (immediate).
 * D-09: Ejected player must rejoin same faction, with generated character only (no original reuse).
 */
class ReregistrationServiceTest {
    private lateinit var officerRepository: OfficerRepository
    private lateinit var factionRepository: FactionRepository
    private lateinit var service: ReregistrationService

    @BeforeEach
    fun setup() {
        officerRepository = mock(OfficerRepository::class.java)
        factionRepository = mock(FactionRepository::class.java)
        service = ReregistrationService(officerRepository, factionRepository)
    }

    private fun makeOfficer(
        id: Long = 1L,
        sessionId: Long = 100L,
        userId: Long? = 1L,
        factionId: Long = 10L,
        personalCode: String = "None",
        meta: MutableMap<String, Any> = mutableMapOf(),
    ): Officer = Officer(
        id = id,
        sessionId = sessionId,
        userId = userId,
        factionId = factionId,
        personalCode = personalCode,
        meta = meta,
    )

    private fun makeFaction(
        id: Long = 10L,
        name: String = "은하제국",
    ): Faction = Faction(
        id = id,
        name = name,
    )

    @Test
    fun `non-ejected player can register in any faction`() {
        val sessionId = 100L
        val userId = 1L
        val targetFactionId = 20L

        // No existing officers or only non-ejected ones
        val normalOfficer = makeOfficer(meta = mutableMapOf())
        `when`(officerRepository.findBySessionIdAndUserId(sessionId, userId)).thenReturn(listOf(normalOfficer))

        val result = service.canReregister(sessionId, userId, targetFactionId, "newCharacter")

        assertTrue(result.allowed, "Non-ejected player should be allowed to register in any faction")
    }

    @Test
    fun `ejected player can re-enter same faction with generated character (D-08, D-09)`() {
        val sessionId = 100L
        val userId = 1L
        val originalFactionId = 10L

        // Ejected officer: original character that was killed
        val ejectedOfficer = makeOfficer(
            userId = userId,
            factionId = originalFactionId,
            personalCode = "None", // generated character for re-entry attempt
            meta = mutableMapOf("ejectedFrom" to originalFactionId, "wasOriginal" to true),
        )
        `when`(officerRepository.findBySessionIdAndUserId(sessionId, userId)).thenReturn(listOf(ejectedOfficer))

        // Re-enter same faction with generated character
        val result = service.canReregister(sessionId, userId, originalFactionId, "newGenerated")

        // D-08: No cooldown -- immediate re-entry allowed
        // D-09: Same faction + generated character = allowed
        assertTrue(result.allowed, "Ejected player should be allowed to re-enter same faction with generated character")
    }

    @Test
    fun `ejected player CANNOT join a different faction (D-09)`() {
        val sessionId = 100L
        val userId = 1L
        val originalFactionId = 10L
        val differentFactionId = 20L

        val ejectedOfficer = makeOfficer(
            userId = userId,
            factionId = originalFactionId,
            personalCode = "None",
            meta = mutableMapOf("ejectedFrom" to originalFactionId, "wasOriginal" to true),
        )
        `when`(officerRepository.findBySessionIdAndUserId(sessionId, userId)).thenReturn(listOf(ejectedOfficer))

        val faction = makeFaction(id = originalFactionId, name = "은하제국")
        `when`(factionRepository.findById(originalFactionId)).thenReturn(Optional.of(faction))

        val result = service.canReregister(sessionId, userId, differentFactionId, "newCharacter")

        assertFalse(result.allowed, "Ejected player should NOT be allowed to join a different faction")
        assertNotNull(result.reason, "Should provide a reason for rejection")
        assertTrue(result.reason!!.contains("은하제국"), "Reason should mention the original faction name")
    }

    @Test
    fun `ejected player CANNOT reuse original character (D-09)`() {
        val sessionId = 100L
        val userId = 1L
        val originalFactionId = 10L

        // Ejected officer with an original character code (non-blank, non-None)
        val ejectedOfficer = makeOfficer(
            userId = userId,
            factionId = originalFactionId,
            personalCode = "reinhard", // original character
            meta = mutableMapOf("ejectedFrom" to originalFactionId, "wasOriginal" to true),
        )
        `when`(officerRepository.findBySessionIdAndUserId(sessionId, userId)).thenReturn(listOf(ejectedOfficer))

        val result = service.canReregister(sessionId, userId, originalFactionId, "reinhard")

        assertFalse(result.allowed, "Ejected player should NOT be allowed to reuse original character")
        assertNotNull(result.reason, "Should provide a reason for rejection")
        assertTrue(result.reason!!.contains("원래 캐릭터"), "Reason should mention original character restriction")
    }

    @Test
    fun `D-07 ejection is triggered by character death only, tracked via meta ejectedFrom`() {
        val sessionId = 100L
        val userId = 1L
        val factionId = 10L

        // An officer who simply logged out (no meta["ejectedFrom"]) should not be restricted
        val loggedOutOfficer = makeOfficer(
            userId = userId,
            factionId = factionId,
            personalCode = "reinhard",
            meta = mutableMapOf(), // No ejectedFrom = just logged out, not dead
        )
        `when`(officerRepository.findBySessionIdAndUserId(sessionId, userId)).thenReturn(listOf(loggedOutOfficer))

        val result = service.canReregister(sessionId, userId, 20L, "newCharacter")

        assertTrue(result.allowed, "Logged-out player (not ejected) should have no restrictions")
        // Confirm: only meta["ejectedFrom"] triggers the restriction path
    }
}
