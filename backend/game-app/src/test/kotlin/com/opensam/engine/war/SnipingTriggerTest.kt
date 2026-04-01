package com.opensam.engine.war

import com.opensam.engine.war.trigger.SnipingTrigger
import com.opensam.entity.City
import com.opensam.entity.General
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import kotlin.random.Random

class SnipingTriggerTest {

    private fun createGeneral(
        id: Long = 1,
        nationId: Long = 1,
        leadership: Short = 50,
        strength: Short = 50,
        intel: Short = 50,
        crew: Int = 1000,
        specialCode: String = "None",
        special2Code: String = "None",
    ): General {
        return General(
            id = id,
            worldId = 1,
            name = "장수$id",
            nationId = nationId,
            cityId = 1,
            leadership = leadership,
            strength = strength,
            intel = intel,
            crew = crew,
            specialCode = specialCode,
            special2Code = special2Code,
            turnTime = OffsetDateTime.now(),
        )
    }

    private fun makeCtx(
        attacker: WarUnit? = null,
        defender: WarUnit? = null,
        rng: Random = Random(42),
        phaseNumber: Int = 0,
        isVsCity: Boolean = false,
    ): BattleTriggerContext {
        val a = attacker ?: WarUnitGeneral(createGeneral())
        val d = defender ?: WarUnitGeneral(createGeneral(id = 2))
        return BattleTriggerContext(
            attacker = a,
            defender = d,
            rng = rng,
            phaseNumber = phaseNumber,
            isVsCity = isVsCity,
        )
    }

    private fun findSeed(threshold: Double, wantBelow: Boolean): Int {
        for (seed in 0..1000) {
            val v = Random(seed).nextDouble()
            if (wantBelow && v < threshold) return seed
            if (!wantBelow && v >= threshold) return seed
        }
        throw IllegalStateException("No seed found")
    }

    private val activateSeed by lazy { findSeed(0.5, wantBelow = true) }
    private val noActivateSeed by lazy { findSeed(0.5, wantBelow = false) }

    // ========== Test 1: Activation with newOpponent=true ==========

    @Test
    fun `onEngagementStart with newOpponent and activation sets snipeActivated and wound in 20-40`() {
        val ctx = makeCtx(rng = Random(activateSeed))
        ctx.newOpponent = true

        SnipingTrigger.onEngagementStart(ctx)

        assertTrue(ctx.snipeActivated)
        assertTrue(ctx.snipeWoundAmount in 20..40, "wound=${ ctx.snipeWoundAmount} should be in 20..40")
    }

    // ========== Test 2: Non-activation with newOpponent=true ==========

    @Test
    fun `onEngagementStart with newOpponent but no activation does not set snipeActivated`() {
        val ctx = makeCtx(rng = Random(noActivateSeed))
        ctx.newOpponent = true

        SnipingTrigger.onEngagementStart(ctx)

        assertFalse(ctx.snipeActivated)
    }

    // ========== Test 3: Does not fire without newOpponent ==========

    @Test
    fun `onEngagementStart without newOpponent does not fire regardless of RNG`() {
        val ctx = makeCtx(rng = Random(activateSeed))
        ctx.newOpponent = false

        SnipingTrigger.onEngagementStart(ctx)

        assertFalse(ctx.snipeActivated)
    }

    // ========== Test 4: snipeImmune prevents firing ==========

    @Test
    fun `onEngagementStart with snipeImmune does not fire`() {
        val ctx = makeCtx(rng = Random(activateSeed))
        ctx.newOpponent = true
        ctx.snipeImmune = true

        SnipingTrigger.onEngagementStart(ctx)

        assertFalse(ctx.snipeActivated)
    }

    // ========== Test 5: Morale boost += 20 on activation ==========

    @Test
    fun `onEngagementStart adds moraleBoost 20 when activated`() {
        val ctx = makeCtx(rng = Random(activateSeed))
        ctx.newOpponent = true

        SnipingTrigger.onEngagementStart(ctx)

        assertEquals(20, ctx.moraleBoost)
    }

    // ========== Test 6: Battle log contains 저격 ==========

    @Test
    fun `onEngagementStart adds battle log with 저격 when activated`() {
        val ctx = makeCtx(rng = Random(activateSeed))
        ctx.newOpponent = true

        SnipingTrigger.onEngagementStart(ctx)

        assertTrue(ctx.battleLogs.any { "저격" in it })
    }

    // ========== Test 7: Does not apply wound to WarUnitCity ==========

    @Test
    fun `onEngagementStart does not apply wound to WarUnitCity defender`() {
        val city = City(
            id = 1, worldId = 1, name = "도시", nationId = 2,
            def = 100, defMax = 1000, wall = 100, wallMax = 1000,
            pop = 1000, popMax = 50000,
        )
        val defender = WarUnitCity(city)
        val ctx = makeCtx(defender = defender, rng = Random(activateSeed))
        ctx.newOpponent = true

        SnipingTrigger.onEngagementStart(ctx)

        assertFalse(ctx.snipeActivated)
    }

    // ========== Test 8: Trigger code and priority ==========

    @Test
    fun `SnipingTrigger has correct code and priority`() {
        assertEquals("che_저격", SnipingTrigger.code)
        assertEquals(15, SnipingTrigger.priority)
    }

    // ========== Test 9: Registered in WarUnitTriggerRegistry ==========

    @Test
    fun `SnipingTrigger is registered in WarUnitTriggerRegistry`() {
        SnipingTrigger
        assertNotNull(WarUnitTriggerRegistry.get("che_저격"))
    }
}
