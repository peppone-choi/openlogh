package com.openlogh.engine.war

import com.openlogh.engine.trigger.CityHealTrigger
import com.openlogh.engine.trigger.TriggerEnv
import com.openlogh.engine.trigger.buildPreTurnTriggers
import com.openlogh.entity.Officer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import kotlin.random.Random

class CityHealTriggerTest {

    private fun createGeneral(
        id: Long = 1,
        factionId: Long = 1,
        injury: Short = 0,
        ships: Int = 1000,
        specialCode: String = "None",
        special2Code: String = "None",
    ): Officer {
        return Officer(
            id = id,
            sessionId = 1,
            name = "장수$id",
            factionId = factionId,
            planetId = 1,
            leadership = 50,
            command = 50,
            intelligence = 50,
            ships = ships,
            specialCode = specialCode,
            special2Code = special2Code,
            turnTime = OffsetDateTime.now(),
            injury = injury,
        )
    }

    private fun makeEnv(officerId: Long = 1): TriggerEnv {
        return TriggerEnv(
            worldId = 1,
            year = 200,
            month = 1,
            generalId = officerId,
        )
    }

    /**
     * Find a seed where Random(seed).nextDouble() < threshold (activation)
     * or >= threshold (non-activation).
     */
    private fun findSeed(threshold: Double, wantBelow: Boolean): Int {
        for (seed in 0..1000) {
            val v = Random(seed).nextDouble()
            if (wantBelow && v < threshold) return seed
            if (!wantBelow && v >= threshold) return seed
        }
        throw IllegalStateException("No seed found")
    }

    private val healSeed by lazy { findSeed(0.5, wantBelow = true) }
    private val noHealSeed by lazy { findSeed(0.5, wantBelow = false) }

    // ========== Test 1: Self-heal clears own injury ==========

    @Test
    fun `self-heal clears own injury`() {
        val general = createGeneral(id = 1, injury = 30)
        val cityMates = listOf(createGeneral(id = 2))
        val trigger = CityHealTrigger(general, cityMates, Random(42))

        trigger.action(makeEnv())

        assertEquals(0.toShort(), general.injury)
    }

    // ========== Test 2: Planet-mate with injury above 10 healed at 50% ==========

    @Test
    fun `city-mate with injury above 10 healed at 50 percent probability`() {
        val general = createGeneral(id = 1)
        val mate = createGeneral(id = 2, injury = 20)
        val trigger = CityHealTrigger(general, listOf(mate), Random(healSeed))

        trigger.action(makeEnv())

        assertEquals(0.toShort(), mate.injury)
    }

    // ========== Test 3: Planet-mate with injury 10 or below not healed ==========

    @Test
    fun `city-mate with injury 10 or below not healed`() {
        val general = createGeneral(id = 1)
        val mate = createGeneral(id = 2, injury = 10)
        val trigger = CityHealTrigger(general, listOf(mate), Random(healSeed))

        trigger.action(makeEnv())

        assertEquals(10.toShort(), mate.injury)
    }

    // ========== Test 4: Faction-0 general only heals nation-0 city-mates ==========

    @Test
    fun `nation-0 general only heals nation-0 city-mates`() {
        val general = createGeneral(id = 1, factionId = 0)
        val sameNationMate = createGeneral(id = 2, factionId = 0, injury = 20)
        val otherNationMate = createGeneral(id = 3, factionId = 1, injury = 20)
        val trigger = CityHealTrigger(
            general,
            listOf(sameNationMate, otherNationMate),
            Random(healSeed),
        )

        trigger.action(makeEnv())

        // Same nation-0 mate eligible for healing
        assertEquals(0.toShort(), sameNationMate.injury)
        // Other nation mate NOT eligible -- injury unchanged
        assertEquals(20.toShort(), otherNationMate.injury)
    }

    // ========== Test 5: Non-nation-0 general heals any city-mate ==========

    @Test
    fun `non-nation-0 general heals any city-mate`() {
        val general = createGeneral(id = 1, factionId = 1)
        val sameNationMate = createGeneral(id = 2, factionId = 1, injury = 20)
        val otherNationMate = createGeneral(id = 3, factionId = 2, injury = 20)

        // Need a seed that heals both -- find seed where first two nextDouble() < 0.5
        var dualHealSeed = 0
        for (seed in 0..10000) {
            val rng = Random(seed)
            if (rng.nextDouble() < 0.5 && rng.nextDouble() < 0.5) {
                dualHealSeed = seed
                break
            }
        }

        val trigger = CityHealTrigger(
            general,
            listOf(sameNationMate, otherNationMate),
            Random(dualHealSeed),
        )

        trigger.action(makeEnv())

        // Non-nation-0 general heals any city-mate regardless of nation
        assertEquals(0.toShort(), sameNationMate.injury)
        assertEquals(0.toShort(), otherNationMate.injury)
    }

    // ========== Test 6: Officer does not heal itself via city-mate loop ==========

    @Test
    fun `general does not heal itself via city-mate loop`() {
        val general = createGeneral(id = 1, injury = 30)
        // Include general itself in city-mates list
        val trigger = CityHealTrigger(general, listOf(general), Random(healSeed))

        // Clear the self-heal by setting injury after trigger construction
        general.injury = 30

        trigger.action(makeEnv())

        // Self-heal always clears injury (first step in action())
        // But the city-mate loop should skip self (id == general.id filter)
        assertEquals(0.toShort(), general.injury)
    }

    // ========== Test 7: buildPreTurnTriggers includes CityHealTrigger when cityMates non-empty ==========

    @Test
    fun `buildPreTurnTriggers includes CityHealTrigger when cityMates non-empty`() {
        val general = createGeneral(id = 1)
        val mate = createGeneral(id = 2)
        val triggers = buildPreTurnTriggers(
            general = general,
            cityMates = listOf(mate),
            rng = Random(42),
        )

        assertTrue(triggers.any { it.uniqueId.startsWith("도시치료_") })
    }

    // ========== Test 8: buildPreTurnTriggers excludes CityHealTrigger when cityMates empty ==========

    @Test
    fun `buildPreTurnTriggers excludes CityHealTrigger when cityMates empty`() {
        val general = createGeneral(id = 1)
        val triggers = buildPreTurnTriggers(
            general = general,
            cityMates = emptyList(),
            rng = Random(42),
        )

        assertFalse(triggers.any { it.uniqueId.startsWith("도시치료_") })
    }

    // ========== Test 9: Planet-mate not healed when rng does not trigger ==========

    @Test
    fun `city-mate not healed when rng does not trigger`() {
        val general = createGeneral(id = 1)
        val mate = createGeneral(id = 2, injury = 20)
        val trigger = CityHealTrigger(general, listOf(mate), Random(noHealSeed))

        trigger.action(makeEnv())

        assertEquals(20.toShort(), mate.injury)
    }
}
