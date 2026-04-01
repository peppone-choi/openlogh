package com.opensam.engine.war

import com.opensam.engine.war.trigger.UnavoidableCriticalTrigger
import com.opensam.entity.General
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import kotlin.random.Random

class UnavoidableCriticalTriggerTest {

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

    @Test
    fun `onPreAttack sets dodgeDisabled to true`() {
        val ctx = makeCtx()
        UnavoidableCriticalTrigger.onPreAttack(ctx)

        assertTrue(ctx.dodgeDisabled)
    }

    @Test
    fun `onPreAttack fires every phase without probability gate`() {
        // Test multiple phases -- always activates
        for (phase in 0..5) {
            val ctx = makeCtx(phaseNumber = phase, rng = Random(phase * 100))
            UnavoidableCriticalTrigger.onPreAttack(ctx)
            assertTrue(ctx.dodgeDisabled, "Should be active on phase $phase")
        }
    }

    @Test
    fun `onPreAttack logs activation message`() {
        val ctx = makeCtx()
        UnavoidableCriticalTrigger.onPreAttack(ctx)

        assertTrue(ctx.battleLogs.any { "필살강화" in it || "회피 불가" in it || "회피불가" in it })
    }

    @Test
    fun `UnavoidableCriticalTrigger has correct code and priority`() {
        assertEquals("che_필살강화_회피불가", UnavoidableCriticalTrigger.code)
        assertEquals(10, UnavoidableCriticalTrigger.priority)
    }

    @Test
    fun `UnavoidableCriticalTrigger is registered in WarUnitTriggerRegistry`() {
        UnavoidableCriticalTrigger
        assertNotNull(WarUnitTriggerRegistry.get("che_필살강화_회피불가"))
    }
}
