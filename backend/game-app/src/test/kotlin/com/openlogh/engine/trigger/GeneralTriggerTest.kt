package com.openlogh.engine.trigger

import com.openlogh.entity.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

class GeneralTriggerTest {

    private fun createOfficer(
        injury: Short = 0,
        ships: Int = 0,
        supplies: Int = 1000,
        morale: Short = 100,
        accessoryCode: String = "None",
    ): Officer {
        return Officer(
            id = 1,
            sessionId = 1,
            name = "테스트",
            factionId = 1,
            planetId = 1,
            injury = injury,
            ships = ships,
            supplies = supplies,
            morale = morale,
            accessoryCode = accessoryCode,
            turnTime = OffsetDateTime.now(),
        )
    }

    // ========== InjuryReductionTrigger ==========

    @Test
    fun `InjuryReductionTrigger reduces injury by 1`() {
        val officer = createOfficer(injury = 5)
        val trigger = InjuryReductionTrigger(officer)
        val env = TriggerEnv(worldId = 1, year = 200, month = 6, generalId = 1)

        trigger.action(env)

        assertEquals(4.toShort(), officer.injury)
        assertTrue(env.vars["injuryReduced"] as Boolean)
    }

    @Test
    fun `InjuryReductionTrigger does nothing when injury is 0`() {
        val officer = createOfficer(injury = 0)
        val trigger = InjuryReductionTrigger(officer)
        val env = TriggerEnv(worldId = 1, year = 200, month = 6, generalId = 1)

        trigger.action(env)

        assertEquals(0.toShort(), officer.injury)
        assertNull(env.vars["injuryReduced"])
    }

    // ========== TroopConsumptionTrigger ==========

    @Test
    fun `TroopConsumptionTrigger consumes rice for crew`() {
        val officer = createOfficer(ships = 500, supplies = 100)
        val trigger = TroopConsumptionTrigger(officer)
        val env = TriggerEnv(worldId = 1, year = 200, month = 6, generalId = 1)

        trigger.action(env)

        // 500 / 100 = 5 rice consumed
        assertEquals(95, officer.supplies)
        assertNull(env.vars["troopStarving"])
    }

    @Test
    fun `TroopConsumptionTrigger drops morale when no rice`() {
        val officer = createOfficer(ships = 500, supplies = 0, morale = 80)
        val trigger = TroopConsumptionTrigger(officer)
        val env = TriggerEnv(worldId = 1, year = 200, month = 6, generalId = 1)

        trigger.action(env)

        assertEquals(0, officer.supplies)
        assertEquals(75.toShort(), officer.morale, "Atmos should drop by 5")
        assertTrue(env.vars["troopStarving"] as Boolean)
    }

    @Test
    fun `TroopConsumptionTrigger does nothing when crew is 0`() {
        val officer = createOfficer(ships = 0, supplies = 100)
        val trigger = TroopConsumptionTrigger(officer)
        val env = TriggerEnv(worldId = 1, year = 200, month = 6, generalId = 1)

        trigger.action(env)

        assertEquals(100, officer.supplies, "Rice should not change with zero crew")
    }

    // ========== MedicineHealTrigger ==========

    @Test
    fun `MedicineHealTrigger heals injury at or above threshold`() {
        val officer = createOfficer(injury = 15, accessoryCode = "medicine_pill")
        val trigger = MedicineHealTrigger(officer, injuryTarget = 10)
        val env = TriggerEnv(worldId = 1, year = 200, month = 6, generalId = 1)

        trigger.action(env)

        assertEquals(0.toShort(), officer.injury)
        assertTrue(env.vars["medicineHealed"] as Boolean)
    }

    @Test
    fun `MedicineHealTrigger does not heal when injury below threshold`() {
        val officer = createOfficer(injury = 5, accessoryCode = "medicine_pill")
        val trigger = MedicineHealTrigger(officer, injuryTarget = 10)
        val env = TriggerEnv(worldId = 1, year = 200, month = 6, generalId = 1)

        trigger.action(env)

        assertEquals(5.toShort(), officer.injury, "Injury should remain unchanged")
        assertNull(env.vars["medicineHealed"])
    }
}
