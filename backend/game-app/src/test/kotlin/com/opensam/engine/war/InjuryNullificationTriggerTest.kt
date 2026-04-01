package com.opensam.engine.war

import com.opensam.engine.war.trigger.InjuryNullificationTrigger
import com.opensam.entity.City
import com.opensam.entity.General
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import kotlin.random.Random

class InjuryNullificationTriggerTest {

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
    fun `onEngagementStart sets injuryImmune to true`() {
        val ctx = makeCtx()
        InjuryNullificationTrigger.onEngagementStart(ctx)

        assertTrue(ctx.injuryImmune)
    }

    @Test
    fun `onEngagementStart fires against general defender`() {
        val ctx = makeCtx()
        InjuryNullificationTrigger.onEngagementStart(ctx)

        assertTrue(ctx.injuryImmune)
        assertTrue(ctx.battleLogs.any { "부상무효" in it })
    }

    @Test
    fun `onEngagementStart fires against city defender`() {
        val city = City(
            id = 1, worldId = 1, name = "도시", nationId = 2,
            def = 100, defMax = 1000, wall = 100, wallMax = 1000,
            pop = 1000, popMax = 50000,
        )
        val defender = WarUnitCity(city)
        val ctx = makeCtx(defender = defender, isVsCity = true)

        InjuryNullificationTrigger.onEngagementStart(ctx)

        assertTrue(ctx.injuryImmune)
    }

    @Test
    fun `InjuryNullificationTrigger has correct code and priority`() {
        assertEquals("che_부상무효", InjuryNullificationTrigger.code)
        assertEquals(10, InjuryNullificationTrigger.priority)
    }

    @Test
    fun `InjuryNullificationTrigger is registered in WarUnitTriggerRegistry`() {
        InjuryNullificationTrigger
        assertNotNull(WarUnitTriggerRegistry.get("che_부상무효"))
    }
}
