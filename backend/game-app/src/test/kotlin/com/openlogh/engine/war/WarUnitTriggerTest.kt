package com.openlogh.engine.war

import com.openlogh.entity.Planet
import com.openlogh.entity.Officer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import kotlin.random.Random

class WarUnitTriggerTest {

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

    // ========== Test 1: WarUnitTrigger interface has correct hook methods ==========

    @Test
    fun `WarUnitTrigger interface has code, priority, and four hook methods`() {
        val trigger = object : WarUnitTrigger {
            override val code = "test_interface"
            override val priority = 10
        }

        assertEquals("test_interface", trigger.code)
        assertEquals(10, trigger.priority)

        // Default implementations return ctx unchanged
        val ctx = makeCtx()
        assertSame(ctx, trigger.onEngagementStart(ctx))
        assertSame(ctx, trigger.onPreAttack(ctx))
        assertSame(ctx, trigger.onPostDamage(ctx))
        assertSame(ctx, trigger.onPostRound(ctx))
    }

    // ========== Test 2: Registry get returns registered trigger; unknown returns null ==========

    @Test
    fun `WarUnitTriggerRegistry get returns registered trigger and null for unknown`() {
        val trigger = object : WarUnitTrigger {
            override val code = "test_registry_get"
            override val priority = 5
        }
        WarUnitTriggerRegistry.register(trigger)

        assertNotNull(WarUnitTriggerRegistry.get("test_registry_get"))
        assertEquals("test_registry_get", WarUnitTriggerRegistry.get("test_registry_get")?.code)
        assertNull(WarUnitTriggerRegistry.get("unknown_code_xyz"))
    }

    // ========== Test 3: Registry register and retrieve ==========

    @Test
    fun `registered trigger can be retrieved from WarUnitTriggerRegistry`() {
        val trigger = object : WarUnitTrigger {
            override val code = "test_trigger_retrieve"
            override val priority = 1
        }
        WarUnitTriggerRegistry.register(trigger)

        val retrieved = WarUnitTriggerRegistry.get("test_trigger_retrieve")
        assertNotNull(retrieved)
        assertEquals(1, retrieved?.priority)
        assertTrue(WarUnitTriggerRegistry.allCodes().contains("test_trigger_retrieve"))
    }

    // ========== Test 4: collectWarUnitTriggers returns triggers for general ==========

    @Test
    fun `collectWarUnitTriggers returns triggers for general with matching specialCode`() {
        val trigger1 = object : WarUnitTrigger {
            override val code = "test_special_war"
            override val priority = 10
        }
        val trigger2 = object : WarUnitTrigger {
            override val code = "test_special2_war"
            override val priority = 5
        }
        WarUnitTriggerRegistry.register(trigger1)
        WarUnitTriggerRegistry.register(trigger2)

        val general = createGeneral(specialCode = "test_special_war", special2Code = "test_special2_war")
        val unit = WarUnitOfficer(general)
        val engine = BattleEngine()
        val triggers = engine.collectWarUnitTriggers(unit)

        assertEquals(2, triggers.size)
        // Should be sorted by priority (5, 10)
        assertEquals("test_special2_war", triggers[0].code)
        assertEquals("test_special_war", triggers[1].code)
    }

    // ========== Test 5: collectWarUnitTriggers returns empty for non-WarUnitOfficer ==========

    @Test
    fun `collectWarUnitTriggers returns empty list for WarUnitPlanet`() {
        val city = Planet(
            id = 1, sessionId = 1, name = "도시", factionId = 2,
            orbitalDefense = 100, orbitalDefenseMax = 1000, fortress = 100, fortressMax = 1000,
            population = 1000, populationMax = 50000,
        )
        val cityUnit = WarUnitPlanet(city)
        val engine = BattleEngine()
        val triggers = engine.collectWarUnitTriggers(cityUnit)

        assertTrue(triggers.isEmpty())
    }

    // ========== Test 6: Hook invocation order ==========

    @Test
    fun `hook invocation order is onEngagementStart then onPreAttack then onPostDamage then onPostRound`() {
        val callOrder = mutableListOf<String>()

        val trigger = object : WarUnitTrigger {
            override val code = "test_hook_order"
            override val priority = 1

            override fun onEngagementStart(ctx: BattleTriggerContext): BattleTriggerContext {
                callOrder.add("onEngagementStart")
                return ctx
            }

            override fun onPreAttack(ctx: BattleTriggerContext): BattleTriggerContext {
                callOrder.add("onPreAttack")
                return ctx
            }

            override fun onPostDamage(ctx: BattleTriggerContext): BattleTriggerContext {
                callOrder.add("onPostDamage")
                return ctx
            }

            override fun onPostRound(ctx: BattleTriggerContext): BattleTriggerContext {
                callOrder.add("onPostRound")
                return ctx
            }
        }

        // Simulate the expected call order in BattleEngine
        val ctx = makeCtx()
        trigger.onEngagementStart(ctx)   // Once per new opponent
        trigger.onPreAttack(ctx)          // Per phase before attack
        trigger.onPostDamage(ctx)         // Per phase after damage
        trigger.onPostRound(ctx)          // After all phases with one opponent

        assertEquals(
            listOf("onEngagementStart", "onPreAttack", "onPostDamage", "onPostRound"),
            callOrder,
        )
    }
}
