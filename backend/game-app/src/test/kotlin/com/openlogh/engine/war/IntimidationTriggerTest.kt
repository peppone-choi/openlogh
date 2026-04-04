package com.openlogh.engine.war

import com.openlogh.engine.war.trigger.IntimidationTrigger
import com.openlogh.entity.City
import com.openlogh.entity.General
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import kotlin.random.Random

class IntimidationTriggerTest {

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

    private val activateSeed by lazy { findSeed(0.4, wantBelow = true) }
    private val noActivateSeed by lazy { findSeed(0.4, wantBelow = false) }

    // ========== Test 1: Activation sets intimidated flags ==========

    @Test
    fun `onEngagementStart with activation sets intimidated and disables dodge critical magic`() {
        val ctx = makeCtx(rng = Random(activateSeed))
        val result = IntimidationTrigger.onEngagementStart(ctx)

        assertTrue(result.intimidated)
        assertTrue(result.dodgeDisabled)
        assertTrue(result.criticalDisabled)
        assertTrue(result.magicDisabled)
    }

    // ========== Test 2: Non-activation does NOT set intimidated ==========

    @Test
    fun `onEngagementStart without activation does not set intimidated`() {
        val ctx = makeCtx(rng = Random(noActivateSeed))
        val result = IntimidationTrigger.onEngagementStart(ctx)

        assertFalse(result.intimidated)
        assertFalse(result.dodgeDisabled)
        assertFalse(result.criticalDisabled)
        assertFalse(result.magicDisabled)
    }

    // ========== Test 3: Defender atmos reduced by 5 on activation ==========

    @Test
    fun `onEngagementStart reduces defender atmos by 5 when activated`() {
        val defender = WarUnitGeneral(createGeneral(id = 2))
        defender.atmos = 50
        val ctx = makeCtx(defender = defender, rng = Random(activateSeed))

        IntimidationTrigger.onEngagementStart(ctx)

        assertEquals(45, ctx.defender.atmos)
    }

    // ========== Test 4: intimidatePhasesRemaining set to 1 ==========

    @Test
    fun `onEngagementStart sets intimidatePhasesRemaining to 1 when activated`() {
        val ctx = makeCtx(rng = Random(activateSeed))
        IntimidationTrigger.onEngagementStart(ctx)

        assertEquals(1, ctx.intimidatePhasesRemaining)
    }

    // ========== Test 5: Battle log contains 위압 when activated ==========

    @Test
    fun `onEngagementStart adds battle log with 위압 when activated`() {
        val ctx = makeCtx(rng = Random(activateSeed))
        IntimidationTrigger.onEngagementStart(ctx)

        assertTrue(ctx.battleLogs.any { "위압" in it })
    }

    // ========== Test 6: Does not fire against WarUnitCity ==========

    @Test
    fun `onEngagementStart does not fire against WarUnitCity defender`() {
        val city = City(
            id = 1, worldId = 1, name = "도시", nationId = 2,
            def = 100, defMax = 1000, wall = 100, wallMax = 1000,
            pop = 1000, popMax = 50000,
        )
        val defender = WarUnitCity(city)
        val ctx = makeCtx(defender = defender, rng = Random(activateSeed))

        IntimidationTrigger.onEngagementStart(ctx)

        assertFalse(ctx.intimidated)
    }

    // ========== Test 7: Trigger code and priority ==========

    @Test
    fun `IntimidationTrigger has correct code and priority`() {
        assertEquals("che_위압", IntimidationTrigger.code)
        assertEquals(20, IntimidationTrigger.priority)
    }

    // ========== Test 8: Atmos does not go below 0 ==========

    @Test
    fun `onEngagementStart does not reduce atmos below 0`() {
        val defender = WarUnitGeneral(createGeneral(id = 2))
        defender.atmos = 3
        val ctx = makeCtx(defender = defender, rng = Random(activateSeed))

        IntimidationTrigger.onEngagementStart(ctx)

        assertEquals(0, ctx.defender.atmos)
    }

    // ========== Test 9: Registered in WarUnitTriggerRegistry ==========

    @Test
    fun `IntimidationTrigger is registered in WarUnitTriggerRegistry`() {
        // Force class loading
        IntimidationTrigger
        assertNotNull(WarUnitTriggerRegistry.get("che_위압"))
    }
}
