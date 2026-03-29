package com.openlogh.service

import com.openlogh.entity.Officer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.Random

/**
 * StatGrowthService unit tests.
 *
 * Tests age-based stat modifiers (CHAR-04) and exp-to-stat growth (CHAR-05).
 * No Spring context needed: StatGrowthService has no injected dependencies.
 */
class StatGrowthServiceTest {

    private val service = StatGrowthService()

    private fun makeOfficer(
        age: Int = 30,
        leadership: Int = 50, leadershipExp: Int = 0,
        command: Int = 50, commandExp: Int = 0,
        intelligence: Int = 50, intelligenceExp: Int = 0,
        politics: Int = 50, politicsExp: Int = 0,
        administration: Int = 50, administrationExp: Int = 0,
        mobility: Int = 50, mobilityExp: Int = 0,
        attack: Int = 50, attackExp: Int = 0,
        defense: Int = 50, defenseExp: Int = 0,
    ): Officer = Officer(
        id = 1L,
        sessionId = 100L,
        age = age.toShort(),
        leadership = leadership.toShort(), leadershipExp = leadershipExp.toShort(),
        command = command.toShort(), commandExp = commandExp.toShort(),
        intelligence = intelligence.toShort(), intelligenceExp = intelligenceExp.toShort(),
        politics = politics.toShort(), politicsExp = politicsExp.toShort(),
        administration = administration.toShort(), administrationExp = administrationExp.toShort(),
        mobility = mobility.toShort(), mobilityExp = mobilityExp.toShort(),
        attack = attack.toShort(), attackExp = attackExp.toShort(),
        defense = defense.toShort(), defenseExp = defenseExp.toShort(),
    )

    // Test 1: Youth bonus (age < 30): growth multiplier 1.2
    @Test
    fun `getAgeMultiplier returns 1_2 for youth under 30`() {
        assertEquals(1.2, service.getAgeMultiplier(20))
        assertEquals(1.2, service.getAgeMultiplier(29))
    }

    // Test 2: Prime (30-50): growth multiplier 1.0
    @Test
    fun `getAgeMultiplier returns 1_0 for prime age 30 to 50`() {
        assertEquals(1.0, service.getAgeMultiplier(30))
        assertEquals(1.0, service.getAgeMultiplier(40))
        assertEquals(1.0, service.getAgeMultiplier(50))
    }

    // Test 3: Elder penalty (age > 50): growth multiplier 0.8
    @Test
    fun `getAgeMultiplier returns 0_8 for elder over 50`() {
        assertEquals(0.8, service.getAgeMultiplier(51))
        assertEquals(0.8, service.getAgeMultiplier(70))
    }

    // Test 4: processExpGrowth with leadershipExp=100 increases leadership by 1, resets exp to 0
    @Test
    fun `processExpGrowth increases stat by 1 when exp reaches 100`() {
        val officer = makeOfficer(leadershipExp = 100)

        val grew = service.processExpGrowth(officer)

        assertEquals(51, officer.leadership.toInt())
        assertEquals(0, officer.leadershipExp.toInt())
        assertTrue(grew.contains("leadership"))
    }

    // Test 5: processExpGrowth with exp=120 increases stat by 1, carries over excess (20 remaining)
    @Test
    fun `processExpGrowth carries over excess exp beyond 100`() {
        val officer = makeOfficer(leadershipExp = 120)

        val grew = service.processExpGrowth(officer)

        assertEquals(51, officer.leadership.toInt())
        assertEquals(20, officer.leadershipExp.toInt())
        assertTrue(grew.contains("leadership"))
    }

    // Test 6: processExpGrowth does NOT increase stat beyond cap of 100
    @Test
    fun `processExpGrowth does not exceed stat cap of 100`() {
        val officer = makeOfficer(leadership = 100, leadershipExp = 120)

        val grew = service.processExpGrowth(officer)

        assertEquals(100, officer.leadership.toInt())
        // Exp should remain unchanged since stat is at cap
        assertEquals(120, officer.leadershipExp.toInt())
        assertFalse(grew.contains("leadership"))
    }

    // Test 7: processExpGrowth with multiple stats at threshold processes all
    @Test
    fun `processExpGrowth processes multiple stats in single call`() {
        val officer = makeOfficer(
            leadershipExp = 100,
            commandExp = 150,
            intelligenceExp = 99,  // below threshold
            politicsExp = 200,
        )

        val grew = service.processExpGrowth(officer)

        assertEquals(51, officer.leadership.toInt())
        assertEquals(0, officer.leadershipExp.toInt())
        assertEquals(51, officer.command.toInt())
        assertEquals(50, officer.commandExp.toInt())
        assertEquals(50, officer.intelligence.toInt()) // unchanged
        assertEquals(99, officer.intelligenceExp.toInt()) // unchanged
        assertEquals(51, officer.politics.toInt())
        assertEquals(100, officer.politicsExp.toInt())

        assertTrue(grew.contains("leadership"))
        assertTrue(grew.contains("command"))
        assertFalse(grew.contains("intelligence"))
        assertTrue(grew.contains("politics"))
        assertEquals(3, grew.size)
    }

    // Test 8: Elder officer (age > 55) stat decay with deterministic random
    @Test
    fun `processElderDecay decays stats for officers over 55`() {
        val officer = makeOfficer(age = 60, leadership = 80, command = 70)

        // Use a deterministic random that always returns 0.005 (below 0.01 threshold)
        val alwaysDecay = object : Random() {
            override fun nextDouble(): Double = 0.005
            override fun next(bits: Int): Int = 0
        }

        val decayed = service.processElderDecay(officer, alwaysDecay)

        // All 8 stats should decay
        assertEquals(8, decayed.size)
        assertEquals(79, officer.leadership.toInt())
        assertEquals(69, officer.command.toInt())
    }

    // Test 9: processElderDecay does nothing for young officers
    @Test
    fun `processElderDecay does nothing for officers age 55 or younger`() {
        val officer = makeOfficer(age = 55, leadership = 80)

        val decayed = service.processElderDecay(officer)

        assertTrue(decayed.isEmpty())
        assertEquals(80, officer.leadership.toInt())
    }

    // Test 10: addExp applies age multiplier
    @Test
    fun `addExp applies age multiplier to raw exp`() {
        val youngOfficer = makeOfficer(age = 25, leadershipExp = 0)
        service.addExp(youngOfficer, "leadership", 10)
        assertEquals(12, youngOfficer.leadershipExp.toInt()) // 10 * 1.2 = 12

        val primeOfficer = makeOfficer(age = 40, commandExp = 0)
        service.addExp(primeOfficer, "command", 10)
        assertEquals(10, primeOfficer.commandExp.toInt()) // 10 * 1.0 = 10

        val elderOfficer = makeOfficer(age = 55, intelligenceExp = 0)
        service.addExp(elderOfficer, "intelligence", 10)
        assertEquals(8, elderOfficer.intelligenceExp.toInt()) // 10 * 0.8 = 8
    }
}
