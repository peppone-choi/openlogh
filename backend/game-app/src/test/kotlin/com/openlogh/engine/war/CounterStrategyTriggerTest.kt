package com.openlogh.engine.war

import com.openlogh.engine.war.trigger.CounterStrategyTrigger
import com.openlogh.entity.Officer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import kotlin.random.Random

class CounterStrategyTriggerTest {

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
        val ctx = makeCtx(rng = Random(activateSeed))
        ctx.magicChanceBonus = 0.5  // Simulate opponent trying magic
        Che반계Trigger.onPreMagic(ctx)
        assertTrue(ctx.magicReflected)
    }
}
