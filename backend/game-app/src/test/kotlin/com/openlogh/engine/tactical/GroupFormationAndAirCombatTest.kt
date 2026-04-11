package com.openlogh.engine.tactical

import com.openlogh.model.EnergyAllocation
import com.openlogh.model.Formation
import com.openlogh.model.UnitStance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * Phase 24-19 coverage:
 *   - Gap C11 (gin7 manual p52): 대형 일괄 변경 — group formation command that changes
 *     the formation of many subordinate units in a single dispatch.
 *   - Gap C13 (gin7 manual p49): 공중전 명령 — SORTIE/AIR_COMBAT alias with automatic
 *     대함전/요격전 판정 driven by the target unit type.
 */
class GroupFormationAndAirCombatTest {

    private val engine = TacticalBattleEngine()

    private fun makeUnit(
        fleetId: Long,
        side: BattleSide,
        posX: Double = 100.0,
        posY: Double = 100.0,
        unitType: String = "FLEET",
        supplies: Int = 1000,
        formation: Formation = Formation.MIXED,
    ) = TacticalUnit(
        fleetId = fleetId,
        officerId = fleetId,
        officerName = "Officer $fleetId",
        factionId = if (side == BattleSide.ATTACKER) 1L else 2L,
        side = side,
        posX = posX,
        posY = posY,
        hp = 1000,
        maxHp = 1000,
        ships = 300,
        maxShips = 300,
        training = 80,
        morale = 80,
        leadership = 50,
        command = 50,
        intelligence = 50,
        mobility = 50,
        attack = 50,
        defense = 50,
        supplies = supplies,
        unitType = unitType,
        formation = formation,
        stance = UnitStance.NAVIGATION,
    )

    private fun makeState(vararg units: TacticalUnit) = TacticalBattleState(
        battleId = 1L,
        starSystemId = 1L,
        units = units.toMutableList(),
    )

    // ── C11: Group Formation command ──

    @Test
    fun `group formation changes the formation of every listed subordinate`() {
        val commander = makeUnit(1L, BattleSide.ATTACKER, posX = 0.0, posY = 0.0)
        val sub1 = makeUnit(2L, BattleSide.ATTACKER, posX = 50.0, posY = 0.0)
        val sub2 = makeUnit(3L, BattleSide.ATTACKER, posX = 100.0, posY = 50.0)
        val state = makeState(commander, sub1, sub2)

        val cmd = TacticalCommand.GroupFormationChange(
            battleId = 1L,
            officerId = commander.officerId,
            targetOfficerIds = listOf(sub1.officerId, sub2.officerId),
            formation = Formation.WEDGE,
        )
        state.commandBuffer.add(cmd)
        engine.processTick(state, Random(42))

        assertEquals(Formation.WEDGE, sub1.formation, "sub1 must adopt spindle formation")
        assertEquals(Formation.WEDGE, sub2.formation, "sub2 must adopt spindle formation")
    }

    @Test
    fun `group formation emits a group_formation tick event with applied count`() {
        val commander = makeUnit(1L, BattleSide.ATTACKER, posX = 0.0, posY = 0.0)
        val sub1 = makeUnit(2L, BattleSide.ATTACKER, posX = 50.0, posY = 0.0)
        val sub2 = makeUnit(3L, BattleSide.ATTACKER, posX = 100.0, posY = 0.0)
        val state = makeState(commander, sub1, sub2)

        state.commandBuffer.add(
            TacticalCommand.GroupFormationChange(
                battleId = 1L,
                officerId = commander.officerId,
                targetOfficerIds = listOf(sub1.officerId, sub2.officerId),
                formation = Formation.THREE_COLUMN,
            )
        )
        engine.processTick(state, Random(42))

        val evt = state.tickEvents.firstOrNull { it.type == "group_formation" }
        assertTrue(evt != null, "group_formation tick event must be emitted")
        assertEquals(2, evt!!.value, "applied count must equal the number of subordinates updated")
    }

    @Test
    fun `group formation ignores enemy-side units`() {
        val commander = makeUnit(1L, BattleSide.ATTACKER, posX = 0.0, posY = 0.0)
        val friend = makeUnit(2L, BattleSide.ATTACKER, posX = 50.0, posY = 0.0, formation = Formation.MIXED)
        val enemy = makeUnit(3L, BattleSide.DEFENDER, posX = 30.0, posY = 0.0, formation = Formation.MIXED)
        val state = makeState(commander, friend, enemy)

        state.commandBuffer.add(
            TacticalCommand.GroupFormationChange(
                battleId = 1L,
                officerId = commander.officerId,
                targetOfficerIds = listOf(friend.officerId, enemy.officerId),
                formation = Formation.WEDGE,
            )
        )
        engine.processTick(state, Random(42))

        assertEquals(Formation.WEDGE, friend.formation, "friendly subordinate switches")
        assertNotEquals(Formation.WEDGE, enemy.formation, "enemy unit must not be affected")
    }

    @Test
    fun `group formation skips officers not included in target list`() {
        val commander = makeUnit(1L, BattleSide.ATTACKER, posX = 0.0, posY = 0.0)
        val included = makeUnit(2L, BattleSide.ATTACKER, posX = 50.0, posY = 0.0, formation = Formation.MIXED)
        val excluded = makeUnit(3L, BattleSide.ATTACKER, posX = 60.0, posY = 0.0, formation = Formation.MIXED)
        val state = makeState(commander, included, excluded)

        state.commandBuffer.add(
            TacticalCommand.GroupFormationChange(
                battleId = 1L,
                officerId = commander.officerId,
                targetOfficerIds = listOf(included.officerId),
                formation = Formation.WEDGE,
            )
        )
        engine.processTick(state, Random(42))

        assertEquals(Formation.WEDGE, included.formation)
        assertEquals(Formation.MIXED, excluded.formation, "untargeted subordinate keeps its formation")
    }

    // ── C13: SORTIE / AIR_COMBAT alias ──

    @Test
    fun `AIR_COMBAT alias routes to the same fighter sortie handler as SORTIE`() {
        val carrier = makeUnit(1L, BattleSide.ATTACKER, posX = 0.0, posY = 0.0,
            unitType = "CARRIER", supplies = 100)
        val target = makeUnit(2L, BattleSide.DEFENDER, posX = 200.0, posY = 0.0, unitType = "FLEET")
        val state = makeState(carrier, target)

        state.commandBuffer.add(
            TacticalCommand.UnitCommand(
                battleId = 1L,
                officerId = carrier.officerId,
                command = "AIR_COMBAT",
            )
        )
        engine.processTick(state, Random(42))

        // Supply was deducted regardless of hit outcome (sortie rule).
        assertTrue(carrier.supplies <= 90,
            "AIR_COMBAT must launch a spartanian sortie and pay the 10 군수물자 cost")
    }

    @Test
    fun `SORTIE command still works as legacy alias`() {
        val carrier = makeUnit(1L, BattleSide.ATTACKER, posX = 0.0, posY = 0.0,
            unitType = "CARRIER", supplies = 100)
        val target = makeUnit(2L, BattleSide.DEFENDER, posX = 200.0, posY = 0.0, unitType = "FLEET")
        val state = makeState(carrier, target)

        state.commandBuffer.add(
            TacticalCommand.UnitCommand(
                battleId = 1L,
                officerId = carrier.officerId,
                command = "SORTIE",
            )
        )
        engine.processTick(state, Random(42))

        assertTrue(carrier.supplies <= 90, "SORTIE must still pay supplies and work as before")
    }

    @Test
    fun `air combat auto-detects intercept vs anti-ship via target type`() {
        val alwaysHitRng = object : Random() {
            override fun nextBits(bitCount: Int): Int = 0
            override fun nextDouble(): Double = 0.0
        }
        val system = MissileWeaponSystem()

        val carrier = makeUnit(1L, BattleSide.ATTACKER, posX = 0.0, posY = 0.0,
            unitType = "CARRIER", supplies = 100)
        val carrierTarget = makeUnit(2L, BattleSide.DEFENDER, posX = 200.0, posY = 0.0, unitType = "CARRIER")
        val state1 = makeState(carrier, carrierTarget)
        val interceptEvent = system.processFighterAttack(carrier, carrierTarget, state1, alwaysHitRng)
        assertTrue(interceptEvent != null && interceptEvent.isIntercept,
            "CARRIER target triggers 요격전 automatically")

        val carrier2 = makeUnit(3L, BattleSide.ATTACKER, posX = 0.0, posY = 0.0,
            unitType = "CARRIER", supplies = 100)
        val fleetTarget = makeUnit(4L, BattleSide.DEFENDER, posX = 200.0, posY = 0.0, unitType = "FLEET")
        val state2 = makeState(carrier2, fleetTarget)
        val antiEvent = system.processFighterAttack(carrier2, fleetTarget, state2, alwaysHitRng)
        assertTrue(antiEvent != null && !antiEvent.isIntercept,
            "non-CARRIER target triggers 대함전 automatically")
    }
}
