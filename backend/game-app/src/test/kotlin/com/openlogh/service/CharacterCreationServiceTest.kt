package com.openlogh.service

import com.openlogh.entity.Officer
import com.openlogh.repository.OfficerRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.OffsetDateTime
import java.util.Optional

class CharacterCreationServiceTest {

    private lateinit var officerRepository: OfficerRepository
    private lateinit var service: CharacterCreationService

    private val sessionId = 1L
    private val userId = 100L
    private val factionId = 10L
    private val planetId = 50L
    private val now = OffsetDateTime.now()

    @BeforeEach
    fun setUp() {
        officerRepository = mock(OfficerRepository::class.java)
        service = CharacterCreationService(officerRepository)

        // Default stub: save returns the officer as-is (with an assigned ID)
        `when`(officerRepository.save(any(Officer::class.java))).thenAnswer { invocation ->
            val officer = invocation.getArgument<Officer>(0)
            officer.also { it.id = 1L }
        }
    }

    private fun validStats() = CharacterCreationService.StatAllocation(
        leadership = 50, command = 50, intelligence = 50, politics = 50,
        administration = 50, mobility = 50, attack = 50, defense = 50,
    )

    // Test 1: validateStatAllocation accepts stats totaling 400 with each in [10,100]
    @Test
    fun `validateStatAllocation accepts valid stats totaling 400`() {
        val stats = validStats() // 50*8 = 400
        assertTrue(service.validateStatAllocation(stats))
    }

    // Test 2: validateStatAllocation rejects stats totaling != 400
    @Test
    fun `validateStatAllocation rejects stats not totaling 400`() {
        val stats = CharacterCreationService.StatAllocation(
            leadership = 50, command = 50, intelligence = 50, politics = 50,
            administration = 50, mobility = 50, attack = 50, defense = 49, // sum = 399
        )
        assertFalse(service.validateStatAllocation(stats))

        val stats2 = CharacterCreationService.StatAllocation(
            leadership = 50, command = 50, intelligence = 50, politics = 50,
            administration = 50, mobility = 50, attack = 50, defense = 51, // sum = 401
        )
        assertFalse(service.validateStatAllocation(stats2))
    }

    // Test 3: validateStatAllocation rejects any stat < 10 or > 100
    @Test
    fun `validateStatAllocation rejects stats outside 10-100 range`() {
        // stat below minimum (9)
        val statsBelowMin = CharacterCreationService.StatAllocation(
            leadership = 9, command = 56, intelligence = 50, politics = 50,
            administration = 50, mobility = 50, attack = 50, defense = 85, // sum = 400
        )
        assertFalse(service.validateStatAllocation(statsBelowMin))

        // stat above maximum (101)
        val statsAboveMax = CharacterCreationService.StatAllocation(
            leadership = 101, command = 44, intelligence = 50, politics = 50,
            administration = 50, mobility = 50, attack = 50, defense = 5, // sum may not be 400 but max check should fail first
        )
        assertFalse(service.validateStatAllocation(statsAboveMax))
    }

    // Test 4: createGeneratedOfficer creates Officer with correct 8 stats, origin, career
    @Test
    fun `createGeneratedOfficer creates officer with correct attributes`() {
        val stats = validStats()
        val officer = service.createGeneratedOfficer(
            sessionId = sessionId,
            userId = userId,
            factionId = factionId,
            name = "Reinhard",
            stats = stats,
            originType = "noble",
            factionType = "empire",
            planetId = planetId,
        )

        assertEquals(sessionId, officer.sessionId)
        assertEquals(userId, officer.userId)
        assertEquals(factionId, officer.factionId)
        assertEquals("Reinhard", officer.name)
        assertEquals(50.toShort(), officer.leadership)
        assertEquals(50.toShort(), officer.command)
        assertEquals(50.toShort(), officer.intelligence)
        assertEquals(50.toShort(), officer.politics)
        assertEquals(50.toShort(), officer.administration)
        assertEquals(50.toShort(), officer.mobility)
        assertEquals(50.toShort(), officer.attack)
        assertEquals(50.toShort(), officer.defense)
        assertEquals("noble", officer.originType)
        assertEquals("military", officer.careerType)
        assertEquals(0.toShort(), officer.rank)
        assertEquals(planetId, officer.planetId)
        assertEquals(planetId, officer.homePlanetId)
        assertEquals("planet", officer.locationState)
        assertEquals(0.toShort(), officer.npcState)
    }

    // Test 5: Empire origin must be one of [noble, knight, commoner]; Alliance must be citizen
    @Test
    fun `validateOrigin enforces faction-specific origins`() {
        // Empire valid origins
        assertTrue(service.validateOrigin("empire", "noble"))
        assertTrue(service.validateOrigin("empire", "knight"))
        assertTrue(service.validateOrigin("empire", "commoner"))
        // Empire invalid origin
        assertFalse(service.validateOrigin("empire", "citizen"))
        assertFalse(service.validateOrigin("empire", "exile"))

        // Alliance valid origin
        assertTrue(service.validateOrigin("alliance", "citizen"))
        // Alliance invalid origins
        assertFalse(service.validateOrigin("alliance", "noble"))
        assertFalse(service.validateOrigin("alliance", "commoner"))

        // Unknown faction type
        assertFalse(service.validateOrigin("fezzan", "citizen"))
    }

    // Test 6: selectOriginalOfficer fails if officer already selected by another user
    @Test
    fun `selectOriginalOfficer rejects already-selected officer`() {
        val existingOfficer = Officer(
            id = 5L,
            sessionId = sessionId,
            userId = 999L, // already claimed by user 999
            factionId = factionId,
            name = "Yang Wen-li",
            npcState = 2,
            turnTime = now,
        )
        `when`(officerRepository.findById(5L)).thenReturn(Optional.of(existingOfficer))

        val exception = assertThrows(IllegalArgumentException::class.java) {
            service.selectOriginalOfficer(sessionId, userId, 5L)
        }
        assertTrue(exception.message!!.contains("이미 다른 플레이어가 선택한"))
    }

    @Test
    fun `selectOriginalOfficer succeeds for unclaimed officer`() {
        val npcOfficer = Officer(
            id = 6L,
            sessionId = sessionId,
            userId = null, // unclaimed
            factionId = factionId,
            name = "Julian Mintz",
            npcState = 2,
            turnTime = now,
        )
        `when`(officerRepository.findById(6L)).thenReturn(Optional.of(npcOfficer))

        val result = service.selectOriginalOfficer(sessionId, userId, 6L)
        assertEquals(userId, result.userId)
        assertEquals(0.toShort(), result.npcState)
    }

    @Test
    fun `createGeneratedOfficer rejects invalid stat total`() {
        val badStats = CharacterCreationService.StatAllocation(
            leadership = 50, command = 50, intelligence = 50, politics = 50,
            administration = 50, mobility = 50, attack = 50, defense = 49,
        )
        assertThrows(IllegalArgumentException::class.java) {
            service.createGeneratedOfficer(
                sessionId = sessionId, userId = userId, factionId = factionId,
                name = "Test", stats = badStats, originType = "noble",
                factionType = "empire", planetId = planetId,
            )
        }
    }

    @Test
    fun `createGeneratedOfficer rejects invalid origin for faction`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.createGeneratedOfficer(
                sessionId = sessionId, userId = userId, factionId = factionId,
                name = "Test", stats = validStats(), originType = "citizen",
                factionType = "empire", planetId = planetId,
            )
        }
    }

    @Test
    fun `createGeneratedOfficer rejects name too short`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.createGeneratedOfficer(
                sessionId = sessionId, userId = userId, factionId = factionId,
                name = "A", stats = validStats(), originType = "noble",
                factionType = "empire", planetId = planetId,
            )
        }
    }
}
