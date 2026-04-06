package com.openlogh.engine.war

import com.openlogh.engine.war.trigger.IntimidationTrigger
import com.openlogh.entity.Planet
import com.openlogh.entity.Officer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import kotlin.random.Random

class IntimidationTriggerTest {

    private fun createGeneral(
        id: Long = 1,
        factionId: Long = 1,
        leadership: Short = 50,
        command: Short = 50,
        intelligence: Short = 50,
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
            leadership = leadership,
            command = command,
            intelligence = intelligence,
            ships = ships,
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
        val a = attacker ?: WarUnitOfficer(createGeneral())
        val d = defender ?: WarUnitOfficer(createGeneral(id = 2))
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

    // ========== Test 3: Defender morale reduced by 5 on activation ==========

    @Test
    fun `onEngagementStart reduces defender morale by 5 when activated`() {
        val defender = WarUnitOfficer(createGeneral(id = 2))
        defender.morale = 50
        val ctx = makeCtx(defender = defender, rng = Random(activateSeed))

        IntimidationTrigger.onEngagementStart(ctx)

        assertEquals(45, ctx.defender.morale)
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

    // ========== Test 6: Does not fire against WarUnitPlanet ==========

    @Test
    fun `onEngagementStart does not fire against WarUnitPlanet defender`() {
        val city = Planet(
            id = 1, sessionId = 1, name = "도시", factionId = 2,
            orbitalDefense = 100, orbitalDefenseMax = 1000, fortress = 100, fortressMax = 1000,
            population = 1000, populationMax = 50000,
        )
        val defender = WarUnitPlanet(city)
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
    fun `onEngagementStart does not reduce morale below 0`() {
        val defender = WarUnitOfficer(createGeneral(id = 2))
        defender.morale = 3
        val ctx = makeCtx(defender = defender, rng = Random(activateSeed))

        IntimidationTrigger.onEngagementStart(ctx)

        assertEquals(0, ctx.defender.morale)
    }

    // ========== Test 9: Registered in WarUnitTriggerRegistry ==========

    @Test
    fun `IntimidationTrigger is registered in WarUnitTriggerRegistry`() {
        // Force class loading
        IntimidationTrigger
        assertNotNull(WarUnitTriggerRegistry.get("che_위압"))
    }
}
