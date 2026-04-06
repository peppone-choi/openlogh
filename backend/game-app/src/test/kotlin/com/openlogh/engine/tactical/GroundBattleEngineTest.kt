package com.openlogh.engine.tactical

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.random.Random

class GroundBattleEngineTest {

    private lateinit var engine: GroundBattleEngine

    private fun makeState(planetId: Long = 1L): GroundBattleState =
        GroundBattleState(planetId = planetId, attackerFactionId = 1L, defenderFactionId = 2L)

    private fun makeAttacker(id: Long, type: String = "ARMORED_INFANTRY", count: Int = 100): GroundUnit =
        GroundUnit(unitId = id, factionId = 1L, groundUnitType = type, count = count, maxCount = count, morale = 80)

    private fun makeDefender(id: Long, type: String = "ARMORED_INFANTRY", count: Int = 100): GroundUnit =
        GroundUnit(unitId = id, factionId = 2L, groundUnitType = type, count = count, maxCount = count, morale = 80)

    @BeforeEach
    fun setUp() {
        engine = GroundBattleEngine()
    }

    @Test
    fun `addAttackers - 30 units fit exactly in the box`() {
        val state = makeState()
        val units = (1L..30L).map { makeAttacker(it) }
        engine.addAttackers(state, units)
        assertEquals(30, state.attackers.size)
        assertEquals(0, state.waitingAttackers.size)
    }

    @Test
    fun `addAttackers - 31 units puts 30 in box and 1 in waitingAttackers`() {
        val state = makeState()
        val units = (1L..31L).map { makeAttacker(it) }
        engine.addAttackers(state, units)
        assertEquals(30, state.attackers.size)
        assertEquals(1, state.waitingAttackers.size)
    }

    @Test
    fun `addAttackers - overflow units all go to waitingAttackers`() {
        val state = makeState()
        // Fill box with 15 defenders first
        engine.initDefenders(state, (100L..114L).map { makeDefender(it) })
        // Now add 20 attackers — only 15 slots remain
        val units = (1L..20L).map { makeAttacker(it) }
        engine.addAttackers(state, units)
        assertEquals(15, state.attackers.size)
        assertEquals(5, state.waitingAttackers.size)
    }

    @Test
    fun `isConquestComplete - true when all defenders have count 0`() {
        val state = makeState()
        engine.initDefenders(state, listOf(makeDefender(1L, count = 0)))
        assertTrue(state.isConquestComplete)
    }

    @Test
    fun `isConquestComplete - false when defenders still have count`() {
        val state = makeState()
        engine.initDefenders(state, listOf(makeDefender(1L, count = 10)))
        assertFalse(state.isConquestComplete)
    }

    @Test
    fun `processTick - defenders are eliminated after enough ticks`() {
        val state = makeState()
        engine.addAttackers(state, listOf(makeAttacker(1L, "ARMORED_GRENADIER", count = 100)))
        engine.initDefenders(state, listOf(makeDefender(2L, "LIGHT_MARINE", count = 1)))
        val rng = Random(42)
        // One tick should be enough to kill a defender with count=1
        engine.processTick(state, rng)
        assertTrue(state.isConquestComplete)
    }

    @Test
    fun `processTick - replenishFromQueue fills attacker slots on death`() {
        val state = makeState()
        // 1 attacker in box (very weak), 1 waiting
        val weakAttacker = GroundUnit(unitId = 1L, factionId = 1L, groundUnitType = "LIGHT_MARINE",
            count = 1, maxCount = 100, morale = 80)
        val waitingAttacker = GroundUnit(unitId = 2L, factionId = 1L, groundUnitType = "ARMORED_INFANTRY",
            count = 50, maxCount = 100, morale = 80)
        state.attackers.add(weakAttacker)
        state.waitingAttackers.add(waitingAttacker)
        // Strong defender to kill the weak attacker
        val strongDefender = GroundUnit(unitId = 3L, factionId = 2L, groundUnitType = "ARMORED_GRENADIER",
            count = 100, maxCount = 100, morale = 100)
        state.defenders.add(strongDefender)
        val rng = Random(42)
        engine.processTick(state, rng)
        // Waiting unit should have been promoted to attackers
        assertEquals(0, state.waitingAttackers.size)
    }

    @Test
    fun `ARMORED_GRENADIER deals more damage than LIGHT_MARINE per tick`() {
        // Test type modifier: ARMORED_GRENADIER 1.3x vs LIGHT_MARINE 0.8x
        val grenadierUnit = GroundUnit(1L, 1L, "ARMORED_GRENADIER", 100, 100, morale = 100)
        val marineUnit = GroundUnit(2L, 1L, "LIGHT_MARINE", 100, 100, morale = 100)

        val stateA = makeState(1L)
        stateA.attackers.add(grenadierUnit.copy())
        stateA.defenders.add(GroundUnit(10L, 2L, "ARMORED_INFANTRY", 1000, 1000, morale = 100))

        val stateB = makeState(2L)
        stateB.attackers.add(marineUnit.copy())
        stateB.defenders.add(GroundUnit(11L, 2L, "ARMORED_INFANTRY", 1000, 1000, morale = 100))

        val rng = Random(0)
        engine.processTick(stateA, rng)
        val grenadierDmg = 1000 - (stateA.defenders.first().count)

        val rng2 = Random(0)
        engine.processTick(stateB, rng2)
        val marineDmg = 1000 - (stateB.defenders.first().count)

        assertTrue(grenadierDmg > marineDmg,
            "Grenadier damage ($grenadierDmg) should exceed marine damage ($marineDmg)")
    }

    @Test
    fun `totalUnitsInBox counts attackers plus defenders`() {
        val state = makeState()
        engine.addAttackers(state, listOf(makeAttacker(1L), makeAttacker(2L)))
        engine.initDefenders(state, listOf(makeDefender(3L)))
        assertEquals(3, state.totalUnitsInBox)
    }

    @Test
    fun `isAttackerDefeated when all attackers dead and no waiting`() {
        val state = makeState()
        state.attackers.add(GroundUnit(1L, 1L, "ARMORED_INFANTRY", count = 0, maxCount = 100))
        assertTrue(state.isAttackerDefeated)
    }

    @Test
    fun `isAttackerDefeated false when waiting attackers remain`() {
        val state = makeState()
        state.attackers.add(GroundUnit(1L, 1L, "ARMORED_INFANTRY", count = 0, maxCount = 100))
        state.waitingAttackers.add(GroundUnit(2L, 1L, "ARMORED_INFANTRY", count = 50, maxCount = 100))
        assertFalse(state.isAttackerDefeated)
    }
}
