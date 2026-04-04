package com.openlogh.engine.trigger

import com.openlogh.engine.LiteHashDRBG
import com.openlogh.entity.General
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

class GeneralTriggerTest {

    private fun createGeneral(
        injury: Short = 0,
        crew: Int = 0,
        rice: Int = 1000,
        atmos: Short = 100,
        itemCode: String = "None",
    ): General {
        return General(
            id = 1,
            worldId = 1,
            name = "테스트",
            nationId = 1,
            cityId = 1,
            injury = injury,
            crew = crew,
            rice = rice,
            atmos = atmos,
            itemCode = itemCode,
            turnTime = OffsetDateTime.now(),
        )
    }

    // ========== InjuryReductionTrigger ==========

    @Test
    fun `InjuryReductionTrigger reduces injury by 1`() {
        val general = createGeneral(injury = 5)
        val trigger = InjuryReductionTrigger(general)
        val env = TriggerEnv(worldId = 1, year = 200, month = 6, generalId = 1)

        trigger.action(env)

        assertEquals(4.toShort(), general.injury)
        assertTrue(env.vars["injuryReduced"] as Boolean)
    }

    @Test
    fun `InjuryReductionTrigger does nothing when injury is 0`() {
        val general = createGeneral(injury = 0)
        val trigger = InjuryReductionTrigger(general)
        val env = TriggerEnv(worldId = 1, year = 200, month = 6, generalId = 1)

        trigger.action(env)

        assertEquals(0.toShort(), general.injury)
        assertNull(env.vars["injuryReduced"])
    }

    // ========== TroopConsumptionTrigger ==========

    @Test
    fun `TroopConsumptionTrigger consumes rice for crew`() {
        val general = createGeneral(crew = 500, rice = 100)
        val trigger = TroopConsumptionTrigger(general)
        val env = TriggerEnv(worldId = 1, year = 200, month = 6, generalId = 1)

        trigger.action(env)

        // 500 / 100 = 5 rice consumed
        assertEquals(95, general.rice)
        assertNull(env.vars["troopStarving"])
    }

    @Test
    fun `TroopConsumptionTrigger drops morale when no rice`() {
        val general = createGeneral(crew = 500, rice = 0, atmos = 80)
        val trigger = TroopConsumptionTrigger(general)
        val env = TriggerEnv(worldId = 1, year = 200, month = 6, generalId = 1)

        trigger.action(env)

        assertEquals(0, general.rice)
        assertEquals(75.toShort(), general.atmos, "Atmos should drop by 5")
        assertTrue(env.vars["troopStarving"] as Boolean)
    }

    @Test
    fun `TroopConsumptionTrigger does nothing when crew is 0`() {
        val general = createGeneral(crew = 0, rice = 100)
        val trigger = TroopConsumptionTrigger(general)
        val env = TriggerEnv(worldId = 1, year = 200, month = 6, generalId = 1)

        trigger.action(env)

        assertEquals(100, general.rice, "Rice should not change with zero crew")
    }

    // ========== MedicineHealTrigger ==========

    @Test
    fun `MedicineHealTrigger heals injury at or above threshold`() {
        val general = createGeneral(injury = 15, itemCode = "medicine_pill")
        val trigger = MedicineHealTrigger(general, injuryTarget = 10)
        val env = TriggerEnv(worldId = 1, year = 200, month = 6, generalId = 1)

        trigger.action(env)

        assertEquals(0.toShort(), general.injury)
        assertTrue(env.vars["medicineHealed"] as Boolean)
    }

    @Test
    fun `MedicineHealTrigger does not heal when injury below threshold`() {
        val general = createGeneral(injury = 5, itemCode = "medicine_pill")
        val trigger = MedicineHealTrigger(general, injuryTarget = 10)
        val env = TriggerEnv(worldId = 1, year = 200, month = 6, generalId = 1)

        trigger.action(env)

        assertEquals(5.toShort(), general.injury, "Injury should remain unchanged")
        assertNull(env.vars["medicineHealed"])
    }

    // ========== CityHealTrigger ==========

    @Test
    fun `CityHealTrigger accepts injected kotlin random`() {
        val general = createGeneral(injury = 5)
        val rng = LiteHashDRBG.build("test-city-heal")
        val trigger = CityHealTrigger(general, emptyList(), rng)
        val env = TriggerEnv(worldId = 1, year = 200, month = 6, generalId = 1)

        trigger.action(env)

        // Self-heal always happens regardless of RNG
        assertEquals(0.toShort(), general.injury)
    }

    @Test
    fun `CityHealTrigger heals city-mates deterministically with injected rng`() {
        val healer = createGeneral(injury = 0)
        val patient1 = createGeneral(injury = 15).apply { id = 2 }
        val patient2 = createGeneral(injury = 20).apply { id = 3 }
        val cityMates = listOf(healer, patient1, patient2)

        // Run twice with same seed -- must produce identical results
        val rng1 = LiteHashDRBG.build("deterministic-heal-seed")
        val trigger1 = CityHealTrigger(healer, cityMates, rng1)
        val env1 = TriggerEnv(worldId = 1, year = 200, month = 6, generalId = 1)
        trigger1.action(env1)
        val injury1a = patient1.injury
        val injury1b = patient2.injury

        // Reset patients
        patient1.injury = 15
        patient2.injury = 20

        val rng2 = LiteHashDRBG.build("deterministic-heal-seed")
        val trigger2 = CityHealTrigger(healer, cityMates, rng2)
        val env2 = TriggerEnv(worldId = 1, year = 200, month = 6, generalId = 1)
        trigger2.action(env2)

        assertEquals(injury1a, patient1.injury, "Same seed must produce same heal outcome for patient1")
        assertEquals(injury1b, patient2.injury, "Same seed must produce same heal outcome for patient2")
    }
}
