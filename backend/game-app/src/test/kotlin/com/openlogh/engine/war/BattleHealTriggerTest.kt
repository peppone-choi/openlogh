package com.openlogh.engine.war

import com.openlogh.engine.war.trigger.BattleHealTrigger
import com.openlogh.entity.General
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import kotlin.random.Random

class BattleHealTriggerTest {

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

    private val activateSeed by lazy { findSeed(0.4, wantBelow = true) }
    private val noActivateSeed by lazy { findSeed(0.4, wantBelow = false) }

    // ========== Test 1: Activation reduces defenderDamage by 30% ==========

    @Test
    fun `onPostDamage with activation reduces defenderDamage by 30 percent`() {
        val ctx = makeCtx(rng = Random(activateSeed))
        ctx.defenderDamage = 100

        BattleHealTrigger.onPostDamage(ctx)

        assertEquals(70, ctx.defenderDamage)
    }

    // ========== Test 2: Non-activation leaves defenderDamage unchanged ==========

    @Test
    fun `onPostDamage without activation leaves defenderDamage unchanged`() {
        val ctx = makeCtx(rng = Random(noActivateSeed))
        ctx.defenderDamage = 100

        BattleHealTrigger.onPostDamage(ctx)

        assertEquals(100, ctx.defenderDamage)
    }

    // ========== Test 3: When activated, attacker injury set to 0 ==========

    @Test
    fun `onPostDamage clears attacker injury when activated and attacker is general`() {
        val attacker = WarUnitGeneral(createGeneral())
        attacker.injury = 30
        val ctx = makeCtx(attacker = attacker, rng = Random(activateSeed))
        ctx.defenderDamage = 50

        BattleHealTrigger.onPostDamage(ctx)

        assertEquals(0, ctx.attacker.injury)
    }

    // ========== Test 4: Battle log contains 의술 when activated ==========

    @Test
    fun `onPostDamage adds battle log with 의술 when activated`() {
        val ctx = makeCtx(rng = Random(activateSeed))
        ctx.defenderDamage = 100

        BattleHealTrigger.onPostDamage(ctx)

        assertTrue(ctx.battleLogs.any { "의술" in it })
    }

    // ========== Test 5: defenderDamage=100 becomes 70 ==========

    @Test
    fun `defenderDamage 100 becomes 70 when activated`() {
        val ctx = makeCtx(rng = Random(activateSeed))
        ctx.defenderDamage = 100

        BattleHealTrigger.onPostDamage(ctx)

        assertEquals(70, ctx.defenderDamage)
    }

    // ========== Test 6: defenderDamage=33 becomes 23 ==========

    @Test
    fun `defenderDamage 33 becomes 23 when activated`() {
        val ctx = makeCtx(rng = Random(activateSeed))
        ctx.defenderDamage = 33

        BattleHealTrigger.onPostDamage(ctx)

        // floor(33 * 0.7) = floor(23.1) = 23
        assertEquals(23, ctx.defenderDamage)
    }

    // ========== Test 7: Trigger code and priority ==========

    @Test
    fun `BattleHealTrigger has correct code and priority`() {
        assertEquals("che_의술", BattleHealTrigger.code)
        assertEquals(10, BattleHealTrigger.priority)
    }

    // ========== Test 8: Registered in WarUnitTriggerRegistry ==========

    @Test
    fun `BattleHealTrigger is registered in WarUnitTriggerRegistry`() {
        BattleHealTrigger
        assertNotNull(WarUnitTriggerRegistry.get("che_의술"))
    }
}
