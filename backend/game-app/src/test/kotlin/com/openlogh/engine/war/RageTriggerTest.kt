package com.openlogh.engine.war

import com.openlogh.engine.war.trigger.RageTrigger
import com.openlogh.entity.Officer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import kotlin.random.Random

class RageTriggerTest {

    private fun createGeneral(
        id: Long = 1,
        nationId: Long = 1,
        leadership: Short = 50,
        strength: Short = 50,
        intel: Short = 50,
        crew: Int = 1000,
        specialCode: String = "None",
        special2Code: String = "None",
    ): Officer {
        return Officer(
            id = id,
            sessionId = 1,
            name = "장수$id",
            factionId = nationId,
            planetId = 1,
            leadership = leadership,
            command = strength,
            intelligence = intel,
            ships = crew,
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

    private fun findSeed(threshold: Double, wantBelow: Boolean): Int {
        for (seed in 0..1000) {
            val v = Random(seed).nextDouble()
            if (wantBelow && v < threshold) return seed
            if (!wantBelow && v >= threshold) return seed
        }
        throw IllegalStateException("No seed found")
    }

    // ========== Test 1: Critical reaction activates rage ==========

    @Test
    fun `onPostDamage after critical with activation increments rageActivationCount`() {
        // Find seed where first nextDouble < 0.5 (critical reaction)
        val seed = findSeed(0.5, wantBelow = true)
        val ctx = makeCtx(rng = Random(seed))
        ctx.criticalActivated = true

        RageTrigger.onPostDamage(ctx)

        assertEquals(1, ctx.rageActivationCount)
    }

    // ========== Test 2: Dodge reaction activates rage ==========

    @Test
    fun `onPostDamage after dodge with activation increments rageActivationCount`() {
        // Find seed where first nextDouble < 0.25 (dodge reaction)
        val seed = findSeed(0.25, wantBelow = true)
        val ctx = makeCtx(rng = Random(seed))
        ctx.dodgeActivated = true

        RageTrigger.onPostDamage(ctx)

        assertEquals(1, ctx.rageActivationCount)
    }

    // ========== Test 3: Multiple activations accumulate ==========

    @Test
    fun `multiple rage activations accumulate rageDamageStack`() {
        // We need a seed where nextDouble < 0.5 (critical reaction) multiple times
        // Simulate 3 activations by calling onPostDamage 3 times with critical
        val seed = findSeed(0.5, wantBelow = true)

        // Each call consumes 2 nextDouble() calls (one for prob, one for extra phase)
        // So we need to create fresh contexts but preserve rageActivationCount
        var rageCount = 0
        var rageDamage = 0.0
        for (i in 1..3) {
            val ctx = makeCtx(rng = Random(seed))
            ctx.criticalActivated = true
            ctx.rageActivationCount = rageCount
            ctx.rageDamageStack = rageDamage

            RageTrigger.onPostDamage(ctx)

            rageCount = ctx.rageActivationCount
            rageDamage = ctx.rageDamageStack
        }

        assertEquals(3, rageCount)
        assertEquals(0.6, rageDamage, 0.001)  // 0.2 * 3 = 0.6
    }

    // ========== Test 4: onPreAttack applies warPower formula ==========

    @Test
    fun `onPreAttack applies attackMultiplier formula 1 + 0_2 times rageActivationCount`() {
        val ctx = makeCtx()
        ctx.rageActivationCount = 3

        RageTrigger.onPreAttack(ctx)

        // 1.0 * (1 + 0.2 * 3) = 1.0 * 1.6 = 1.6
        assertEquals(1.6, ctx.attackMultiplier, 0.001)
    }

    // ========== Test 5: onPreAttack with zero count does not modify ==========

    @Test
    fun `onPreAttack with zero rageActivationCount does not modify attackMultiplier`() {
        val ctx = makeCtx()
        ctx.rageActivationCount = 0

        RageTrigger.onPreAttack(ctx)

        assertEquals(1.0, ctx.attackMultiplier, 0.001)
    }

    // ========== Test 6: 50% chance of extra phase ==========

    @Test
    fun `rage activation may grant extra phase with 50 percent chance`() {
        // Find a seed where critical reaction activates (< 0.5)
        // and extra phase also activates (second nextDouble < 0.5)
        var foundExtraPhase = false
        var foundNoExtraPhase = false

        for (seed in 0..200) {
            val rng = Random(seed)
            val critRoll = rng.nextDouble()
            if (critRoll >= 0.5) continue  // skip non-activating seeds

            val extraRoll = rng.nextDouble()
            if (extraRoll < 0.5 && !foundExtraPhase) {
                // This seed gives extra phase
                val ctx = makeCtx(rng = Random(seed))
                ctx.criticalActivated = true
                RageTrigger.onPostDamage(ctx)
                assertEquals(1, ctx.rageExtraPhases, "seed=$seed should give extra phase")
                foundExtraPhase = true
            }
            if (extraRoll >= 0.5 && !foundNoExtraPhase) {
                // This seed does NOT give extra phase
                val ctx = makeCtx(rng = Random(seed))
                ctx.criticalActivated = true
                RageTrigger.onPostDamage(ctx)
                assertEquals(0, ctx.rageExtraPhases, "seed=$seed should NOT give extra phase")
                foundNoExtraPhase = true
            }
            if (foundExtraPhase && foundNoExtraPhase) break
        }

        assertTrue(foundExtraPhase, "Must find a seed that grants extra phase")
        assertTrue(foundNoExtraPhase, "Must find a seed that does not grant extra phase")
    }

    // ========== Test 7: suppressActive prevents rage ==========

    @Test
    fun `suppressActive prevents rage activation`() {
        val seed = findSeed(0.5, wantBelow = true)
        val ctx = makeCtx(rng = Random(seed))
        ctx.criticalActivated = true
        ctx.suppressActive = true

        RageTrigger.onPostDamage(ctx)

        assertEquals(0, ctx.rageActivationCount)
    }

    // ========== Test 8: Battle log contains 격노 on activation ==========

    @Test
    fun `battle log contains 격노 on activation`() {
        val seed = findSeed(0.5, wantBelow = true)
        val ctx = makeCtx(rng = Random(seed))
        ctx.criticalActivated = true

        RageTrigger.onPostDamage(ctx)

        assertTrue(ctx.battleLogs.any { "격노" in it })
    }

    // ========== Test 9: Trigger code and priority ==========

    @Test
    fun `RageTrigger has correct code and priority`() {
        assertEquals("che_격노", RageTrigger.code)
        assertEquals(10, RageTrigger.priority)
    }

    // ========== Test 10: Registered in WarUnitTriggerRegistry ==========

    @Test
    fun `RageTrigger is registered in WarUnitTriggerRegistry`() {
        RageTrigger
        assertNotNull(WarUnitTriggerRegistry.get("che_격노"))
    }

    // ========== Test 11: No activation without critical or dodge ==========

    @Test
    fun `onPostDamage without critical or dodge does not activate rage`() {
        val seed = findSeed(0.5, wantBelow = true)
        val ctx = makeCtx(rng = Random(seed))
        // Neither criticalActivated nor dodgeActivated

        RageTrigger.onPostDamage(ctx)

        assertEquals(0, ctx.rageActivationCount)
    }

    // ========== Test 12: Integration — onPreAttack fires through BattleEngine pattern ==========

    @Test
    fun `onPreAttack fires through BattleEngine pattern with accumulated rage state`() {
        // Simulate BattleEngine's phase loop: onPostDamage accumulates rage,
        // then onPreAttack in next phase applies the multiplier.

        // Phase 1: onPostDamage with critical -> rage activates
        val seed = findSeed(0.5, wantBelow = true)
        var attackerRageActivationCount = 0

        val postDamageCtx = makeCtx(rng = Random(seed))
        postDamageCtx.criticalActivated = true
        postDamageCtx.rageActivationCount = attackerRageActivationCount
        RageTrigger.onPostDamage(postDamageCtx)
        attackerRageActivationCount = postDamageCtx.rageActivationCount

        // Verify rage accumulated
        assertEquals(1, attackerRageActivationCount,
            "After onPostDamage with critical, rageActivationCount should be 1")

        // Phase 2: onPreAttack should see accumulated rage and apply multiplier
        val preAttackCtx = makeCtx()
        preAttackCtx.rageActivationCount = attackerRageActivationCount
        RageTrigger.onPreAttack(preAttackCtx)

        // 1.0 * (1 + 0.2 * 1) = 1.2
        assertEquals(1.2, preAttackCtx.attackMultiplier, 0.001,
            "onPreAttack should apply attackMultiplier 1.2 after 1 rage activation")
    }
}
