package com.openlogh.engine.war

import com.openlogh.engine.war.trigger.InjuryNullificationTrigger
import com.openlogh.entity.Planet
import com.openlogh.entity.Officer
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
        val city = Planet(
            id = 1, sessionId = 1, name = "도시", factionId = 2,
            orbitalDefense = 100, orbitalDefenseMax = 1000, fortress = 100, fortressMax = 1000,
            population = 1000, populationMax = 50000,
        )
        val defender = WarUnitPlanet(city)
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
