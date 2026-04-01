package com.opensam.engine.war

import com.opensam.engine.war.trigger.CounterStrategyTrigger
import com.opensam.entity.General
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import kotlin.random.Random

class CounterStrategyTriggerTest {

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

    @Test
    fun `onPreAttack with activation logs counter attempt`() {
        val ctx = makeCtx(rng = Random(activateSeed))
        CounterStrategyTrigger.onPreAttack(ctx)

        assertTrue(ctx.battleLogs.any { "반계" in it })
    }

    @Test
    fun `onPreAttack without activation does not log`() {
        val ctx = makeCtx(rng = Random(noActivateSeed))
        CounterStrategyTrigger.onPreAttack(ctx)

        assertTrue(ctx.battleLogs.isEmpty())
    }

    @Test
    fun `CounterStrategyTrigger has correct code and priority`() {
        assertEquals("che_반계", CounterStrategyTrigger.code)
        assertEquals(10, CounterStrategyTrigger.priority)
    }

    @Test
    fun `CounterStrategyTrigger is registered in WarUnitTriggerRegistry`() {
        // Force class loading
        CounterStrategyTrigger
        assertNotNull(WarUnitTriggerRegistry.get("che_반계"))
    }

    @Test
    fun `existing Che반계Trigger BattleTrigger still handles magic reflection`() {
        // Che반계Trigger (BattleTrigger) continues to handle magic reflection via onPreMagic
        // This WarUnitTrigger only handles phase-level logging via onPreAttack
        val ctx = makeCtx()
        ctx.magicActivated = true
        Che반계Trigger.onPostMagic(ctx)
        assertTrue(ctx.magicReflected)
    }
}
