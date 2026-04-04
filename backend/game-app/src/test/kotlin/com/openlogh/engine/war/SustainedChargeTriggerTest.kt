package com.openlogh.engine.war

import com.openlogh.engine.war.trigger.SustainedChargeTrigger
import com.openlogh.entity.General
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import kotlin.random.Random

class SustainedChargeTriggerTest {

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
    fun `onPostDamage when attacker winning and rng triggers adds bonusPhases`() {
        val attacker = WarUnitGeneral(createGeneral(crew = 2000))
        val defender = WarUnitGeneral(createGeneral(id = 2, crew = 500))
        val ctx = makeCtx(attacker = attacker, defender = defender, rng = Random(activateSeed))

        SustainedChargeTrigger.onPostDamage(ctx)

        assertEquals(1, ctx.bonusPhases)
        assertTrue(ctx.battleLogs.any { "돌격지속" in it })
    }

    @Test
    fun `onPostDamage when attacker losing does not add bonusPhases`() {
        val attacker = WarUnitGeneral(createGeneral(crew = 500))
        val defender = WarUnitGeneral(createGeneral(id = 2, crew = 2000))
        val ctx = makeCtx(attacker = attacker, defender = defender, rng = Random(activateSeed))

        SustainedChargeTrigger.onPostDamage(ctx)

        assertEquals(0, ctx.bonusPhases)
    }

    @Test
    fun `onPostDamage does not activate when rng does not trigger`() {
        val attacker = WarUnitGeneral(createGeneral(crew = 2000))
        val defender = WarUnitGeneral(createGeneral(id = 2, crew = 500))
        val ctx = makeCtx(attacker = attacker, defender = defender, rng = Random(noActivateSeed))

        SustainedChargeTrigger.onPostDamage(ctx)

        assertEquals(0, ctx.bonusPhases)
    }

    @Test
    fun `SustainedChargeTrigger has correct code and priority`() {
        assertEquals("che_돌격지속", SustainedChargeTrigger.code)
        assertEquals(10, SustainedChargeTrigger.priority)
    }

    @Test
    fun `SustainedChargeTrigger is registered in WarUnitTriggerRegistry`() {
        SustainedChargeTrigger
        assertNotNull(WarUnitTriggerRegistry.get("che_돌격지속"))
    }
}
