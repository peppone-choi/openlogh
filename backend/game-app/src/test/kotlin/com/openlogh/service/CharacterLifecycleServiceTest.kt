package com.openlogh.service

import com.openlogh.entity.Officer
import com.openlogh.repository.OfficerRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class CharacterLifecycleServiceTest {

    private lateinit var officerRepository: OfficerRepository
    private lateinit var service: CharacterLifecycleService

    @BeforeEach
    fun setUp() {
        officerRepository = mock(OfficerRepository::class.java)
        service = CharacterLifecycleService(officerRepository)

        `when`(officerRepository.save(any(Officer::class.java))).thenAnswer { invocation ->
            invocation.getArgument<Officer>(0)
        }
    }

    private fun createOfficer(
        rank: Short = 2,
        locationState: String = "planet",
        killTurn: Short? = null,
        leadership: Short = 80,
        command: Short = 70,
        intelligence: Short = 60,
        politics: Short = 50,
        administration: Short = 40,
        mobility: Short = 30,
        attack: Short = 75,
        defense: Short = 65,
        famePoints: Int = 100,
        age: Short = 30,
        homePlanetId: Long? = 10L,
        planetId: Long = 10L,
        factionId: Long = 1L,
        sessionId: Long = 1L,
        userId: Long? = 100L,
    ): Officer = Officer(
        id = 1L,
        sessionId = sessionId,
        userId = userId,
        factionId = factionId,
        rank = rank,
        locationState = locationState,
        killTurn = killTurn,
        leadership = leadership,
        command = command,
        intelligence = intelligence,
        politics = politics,
        administration = administration,
        mobility = mobility,
        attack = attack,
        defense = defense,
        famePoints = famePoints,
        age = age,
        homePlanetId = homePlanetId,
        planetId = planetId,
        name = "Test Officer",
        picture = "default",
        originType = "noble",
        careerType = "military",
    )

    // === Deletion Tests (CHAR-10) ===

    // Test 1: canDeleteOfficer returns true when rank <= 4 and locationState == "planet"
    @Test
    fun `canDeleteOfficer returns true for colonel rank on planet`() {
        val officer = createOfficer(rank = 4, locationState = "planet")
        assertTrue(service.canDeleteOfficer(officer))
    }

    // Test 2: canDeleteOfficer returns false when rank > 4
    @Test
    fun `canDeleteOfficer returns false for commodore and above`() {
        val officer = createOfficer(rank = 5, locationState = "planet")
        assertFalse(service.canDeleteOfficer(officer))

        val admiral = createOfficer(rank = 8, locationState = "planet")
        assertFalse(service.canDeleteOfficer(admiral))
    }

    // Test 3: canDeleteOfficer returns false when locationState != "planet"
    @Test
    fun `canDeleteOfficer returns false when not on planet`() {
        val officer = createOfficer(rank = 2, locationState = "fleet")
        assertFalse(service.canDeleteOfficer(officer))

        val spaceOfficer = createOfficer(rank = 2, locationState = "space")
        assertFalse(service.canDeleteOfficer(spaceOfficer))
    }

    // Test 4: deleteOfficer sets killTurn and nullifies userId
    @Test
    fun `deleteOfficer sets killTurn and clears userId`() {
        val officer = createOfficer(rank = 2, locationState = "planet")
        val currentTurn: Short = 42

        val result = service.deleteOfficer(officer, currentTurn)

        assertEquals(currentTurn, result.killTurn)
        assertNull(result.userId)
    }

    // === Injury Tests (CHAR-11) ===

    // Test 5: applyInjury sets injury > 0 and reduces stats by injury/10 (min 1)
    @Test
    fun `applyInjury reduces stats by injury divided by 10`() {
        val officer = createOfficer(
            leadership = 80, command = 70, intelligence = 60,
            politics = 50, administration = 40, mobility = 30,
            attack = 75, defense = 65,
        )

        val result = service.applyInjury(officer, 30)

        assertEquals(30.toShort(), result.injury)
        // penalty = 30 / 10 = 3
        assertEquals(77.toShort(), result.leadership)    // 80 - 3
        assertEquals(67.toShort(), result.command)        // 70 - 3
        assertEquals(57.toShort(), result.intelligence)   // 60 - 3
        assertEquals(47.toShort(), result.politics)       // 50 - 3
        assertEquals(37.toShort(), result.administration) // 40 - 3
        assertEquals(27.toShort(), result.mobility)       // 30 - 3
        assertEquals(72.toShort(), result.attack)         // 75 - 3
        assertEquals(62.toShort(), result.defense)        // 65 - 3
    }

    // Test 5b: applyInjury minimum stat is 1
    @Test
    fun `applyInjury does not reduce stats below 1`() {
        val officer = createOfficer(mobility = 2) // very low stat

        val result = service.applyInjury(officer, 30)

        // penalty = 3, mobility 2 - 3 would be -1, clamped to 1
        assertEquals(1.toShort(), result.mobility)
    }

    // Test 6: recoverFromInjury restores original stats from meta
    @Test
    fun `recoverFromInjury restores pre-injury stats`() {
        val officer = createOfficer(
            leadership = 80, command = 70, intelligence = 60,
            politics = 50, administration = 40, mobility = 30,
            attack = 75, defense = 65,
        )

        // Apply injury first
        service.applyInjury(officer, 30)

        // Verify stats were reduced
        assertEquals(77.toShort(), officer.leadership)

        // Now recover
        val result = service.recoverFromInjury(officer)

        assertEquals(0.toShort(), result.injury)
        assertEquals(80.toShort(), result.leadership)
        assertEquals(70.toShort(), result.command)
        assertEquals(60.toShort(), result.intelligence)
        assertEquals(50.toShort(), result.politics)
        assertEquals(40.toShort(), result.administration)
        assertEquals(30.toShort(), result.mobility)
        assertEquals(75.toShort(), result.attack)
        assertEquals(65.toShort(), result.defense)
    }

    // === Death Tests (CHAR-12) ===

    // Test 7: triggerDeath sets killTurn and moves officer to homePlanetId
    @Test
    fun `triggerDeath sets killTurn and moves to home planet`() {
        val officer = createOfficer(homePlanetId = 42L, planetId = 99L)
        val currentTurn: Short = 10
        val factionCapital = 1L

        val result = service.triggerDeath(officer, currentTurn, factionCapital)

        assertEquals(currentTurn, result.killTurn)
        assertEquals(42L, result.planetId)
        assertEquals("planet", result.locationState)
    }

    // Test 8: triggerDeath with no homePlanetId moves to faction capital
    @Test
    fun `triggerDeath with no home planet moves to faction capital`() {
        val officer = createOfficer(homePlanetId = null, planetId = 99L)
        val currentTurn: Short = 10
        val factionCapital = 777L

        val result = service.triggerDeath(officer, currentTurn, factionCapital)

        assertEquals(currentTurn, result.killTurn)
        assertEquals(777L, result.planetId)
        assertEquals("planet", result.locationState)
    }

    // === Inheritance Tests (CHAR-09) ===

    // Test 9: canInherit returns true when famePoints > 0, age <= 60, killTurn is null
    @Test
    fun `canInherit returns true for eligible officer`() {
        val officer = createOfficer(famePoints = 100, age = 30, killTurn = null)
        assertTrue(service.canInherit(officer))
    }

    // Test 10: canInherit returns false when age > 60
    @Test
    fun `canInherit returns false when age exceeds 60`() {
        val officer = createOfficer(famePoints = 100, age = 61, killTurn = null)
        assertFalse(service.canInherit(officer))

        // Boundary: exactly 60 should be allowed
        val boundary = createOfficer(famePoints = 100, age = 60, killTurn = null)
        assertTrue(service.canInherit(boundary))
    }

    // Test 11: canInherit returns false when killTurn is set (dead officer)
    @Test
    fun `canInherit returns false for dead officer`() {
        val officer = createOfficer(famePoints = 100, age = 30, killTurn = 5)
        assertFalse(service.canInherit(officer))
    }
}
