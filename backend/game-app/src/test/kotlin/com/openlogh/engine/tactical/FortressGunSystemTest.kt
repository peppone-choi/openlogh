package com.openlogh.engine.tactical

import com.openlogh.model.EnergyAllocation
import com.openlogh.model.Formation
import com.openlogh.model.UnitStance
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * Tests for FortressGunSystem — gin7 요새포 4종 스펙:
 * - 토르해머: 10000, 120틱 쿨다운 (이제르론)
 * - 가이에스하켄: 7000, 90틱 쿨다운 (가이에스부르크)
 * - 아르테미스: 3000, 60틱 쿨다운
 * - 경X선: 1500, 60틱 쿨다운
 *
 * 사선 통과 유닛 각각 전체 위력 피해 (분산 아님).
 * 아군 피격 시 "[아군 피해!]" 포함 이벤트.
 */
class FortressGunSystemTest {

    private val system = FortressGunSystem()

    private fun makeUnit(
        fleetId: Long,
        factionId: Long,
        posX: Double,
        posY: Double,
        hp: Int = 20000,
        ships: Int = 1000,
    ) = TacticalUnit(
        fleetId = fleetId,
        officerId = fleetId,
        officerName = "Officer $fleetId",
        factionId = factionId,
        side = if (factionId == 1L) BattleSide.DEFENDER else BattleSide.ATTACKER,
        posX = posX,
        posY = posY,
        hp = hp,
        maxHp = hp,
        ships = ships,
        maxShips = ships,
        energy = EnergyAllocation(beam = 20, gun = 20, shield = 0, engine = 20, warp = 30, sensor = 10),
    )

    private fun makeState(
        fortressGunPower: Int,
        fortressGunCooldown: Int,
        fortressFactionId: Long,
        tickCount: Int = 9999,
        vararg units: TacticalUnit,
    ) = TacticalBattleState(
        battleId = 1L,
        starSystemId = 1L,
        units = units.toMutableList(),
        tickCount = tickCount,
        fortressGunPower = fortressGunPower,
        fortressGunCooldown = fortressGunCooldown,
        fortressGunLastFired = -9999,
        fortressGunRange = 10,
        fortressFactionId = fortressFactionId,
        battleBoundsX = 1000.0,
        battleBoundsY = 600.0,
    )

    // ── FortressGunType enum tests ──

    @Test
    fun `FortressGunType fromPower 10000 returns THOR_HAMMER`() {
        assertEquals(FortressGunType.THOR_HAMMER, FortressGunType.fromPower(10_000))
    }

    @Test
    fun `FortressGunType fromPower 7000 returns GAIESBURGHER`() {
        assertEquals(FortressGunType.GAIESBURGHER, FortressGunType.fromPower(7_000))
    }

    @Test
    fun `FortressGunType fromPower 3000 returns ARTEMIS`() {
        assertEquals(FortressGunType.ARTEMIS, FortressGunType.fromPower(3_000))
    }

    @Test
    fun `FortressGunType fromPower 1000 returns LIGHT_XRAY`() {
        assertEquals(FortressGunType.LIGHT_XRAY, FortressGunType.fromPower(1_000))
    }

    @Test
    fun `FortressGunType THOR_HAMMER has power 10000 and cooldown 120`() {
        assertEquals(10_000, FortressGunType.THOR_HAMMER.power)
        assertEquals(120, FortressGunType.THOR_HAMMER.cooldownTicks)
    }

    @Test
    fun `FortressGunType GAIESBURGHER has power 7000 and cooldown 90`() {
        assertEquals(7_000, FortressGunType.GAIESBURGHER.power)
        assertEquals(90, FortressGunType.GAIESBURGHER.cooldownTicks)
    }

    @Test
    fun `FortressGunType ARTEMIS has power 3000 and cooldown 60`() {
        assertEquals(3_000, FortressGunType.ARTEMIS.power)
        assertEquals(60, FortressGunType.ARTEMIS.cooldownTicks)
    }

    @Test
    fun `FortressGunType LIGHT_XRAY has power 1500 and cooldown 60`() {
        assertEquals(1_500, FortressGunType.LIGHT_XRAY.power)
        assertEquals(60, FortressGunType.LIGHT_XRAY.cooldownTicks)
    }

    // ── Cooldown tests ──

    @Test
    fun `fortress gun does not fire when cooldown not elapsed`() {
        // tickCount=5, lastFired=0, cooldown=120 → difference=5 < 120 → no fire
        val enemy = makeUnit(fleetId = 2L, factionId = 2L, posX = 500.0, posY = 300.0)
        val state = makeState(
            fortressGunPower = 10_000,
            fortressGunCooldown = 120,
            fortressFactionId = 1L,
            tickCount = 5,
            enemy,
        )
        state.fortressGunLastFired = 0

        system.processFortressGunFire(state)

        val fireEvents = state.tickEvents.filter { it.type == "fortress_fire" }
        assertTrue(fireEvents.isEmpty(), "Should not fire when cooldown not elapsed, got: $fireEvents")
    }

    @Test
    fun `fortress gun fires when cooldown elapsed`() {
        val enemy = makeUnit(fleetId = 2L, factionId = 2L, posX = 500.0, posY = 300.0)
        val state = makeState(
            fortressGunPower = 10_000,
            fortressGunCooldown = 120,
            fortressFactionId = 1L,
            tickCount = 9999,
            enemy,
        )

        system.processFortressGunFire(state)

        val fireEvents = state.tickEvents.filter { it.type == "fortress_fire" }
        assertTrue(fireEvents.isNotEmpty(), "Should fire when cooldown elapsed")
    }

    // ── Full damage (not split) tests ──

    @Test
    fun `two units in line of fire each receive full THOR_HAMMER damage`() {
        // Gun fires from (500, 0) toward target near (500, 300)
        // Place two enemy units along the same vertical line — both in sightline
        val unitA = makeUnit(fleetId = 2L, factionId = 2L, posX = 500.0, posY = 150.0, hp = 50_000, ships = 1000)
        val unitB = makeUnit(fleetId = 3L, factionId = 2L, posX = 500.0, posY = 300.0, hp = 50_000, ships = 1000)

        val state = makeState(
            fortressGunPower = 10_000,
            fortressGunCooldown = 120,
            fortressFactionId = 1L,
            tickCount = 9999,
            unitA, unitB,
        )

        // Shield=0 so absorbed = 0, finalDmg = power = 10000
        system.processFortressGunFire(state)

        val hitEvents = state.tickEvents.filter { it.type == "fortress_fire" && it.targetUnitId != 0L }
        // Both units should be hit
        assertTrue(hitEvents.size >= 1, "At least one unit must be hit, events: $hitEvents")

        // Each hit event should carry full power (10000), not half
        val damageValues = hitEvents.map { it.value }
        for (dmg in damageValues) {
            assertEquals(10_000, dmg,
                "Each unit should receive FULL fortress gun power (10000), not split. Got: $damageValues")
        }
    }

    // ── Friendly fire tests ──

    @Test
    fun `friendly unit in line of fire generates agun pihae event`() {
        // fortressFactionId=1, friendly unit also in sightline
        val friendly = makeUnit(fleetId = 1L, factionId = 1L, posX = 500.0, posY = 150.0, hp = 50_000, ships = 1000)
        val enemy = makeUnit(fleetId = 2L, factionId = 2L, posX = 500.0, posY = 400.0, hp = 50_000, ships = 1000)

        val state = makeState(
            fortressGunPower = 10_000,
            fortressGunCooldown = 120,
            fortressFactionId = 1L,
            tickCount = 9999,
            friendly, enemy,
        )

        system.processFortressGunFire(state)

        val friendlyHitEvent = state.tickEvents.find {
            it.type == "fortress_fire" && it.targetUnitId == 1L
        }

        assertNotNull(friendlyHitEvent, "Friendly unit in line of fire must be hit")
        assertTrue(
            friendlyHitEvent!!.detail.contains("[아군 피해!]"),
            "Friendly fire event must contain '[아군 피해!]', got: '${friendlyHitEvent.detail}'"
        )
    }

    @Test
    fun `enemy unit in line of fire does NOT generate agun pihae event`() {
        val enemy = makeUnit(fleetId = 2L, factionId = 2L, posX = 500.0, posY = 300.0, hp = 50_000, ships = 1000)

        val state = makeState(
            fortressGunPower = 10_000,
            fortressGunCooldown = 120,
            fortressFactionId = 1L,
            tickCount = 9999,
            enemy,
        )

        system.processFortressGunFire(state)

        val enemyHitEvent = state.tickEvents.find {
            it.type == "fortress_fire" && it.targetUnitId == 2L
        }

        assertNotNull(enemyHitEvent, "Enemy must be hit")
        assertFalse(
            enemyHitEvent!!.detail.contains("[아군 피해!]"),
            "Enemy hit should NOT contain '[아군 피해!]'"
        )
    }

    // ── fortressGunLastFired update ──

    @Test
    fun `fortressGunLastFired is updated after firing`() {
        val enemy = makeUnit(fleetId = 2L, factionId = 2L, posX = 500.0, posY = 300.0)
        val state = makeState(
            fortressGunPower = 10_000,
            fortressGunCooldown = 120,
            fortressFactionId = 1L,
            tickCount = 200,
            enemy,
        )

        system.processFortressGunFire(state)

        assertEquals(200, state.fortressGunLastFired, "fortressGunLastFired must be updated to tickCount after firing")
    }

    // ── No fire when no enemies ──

    @Test
    fun `fortress gun does not fire when no enemies present`() {
        val friendly = makeUnit(fleetId = 1L, factionId = 1L, posX = 500.0, posY = 300.0)

        val state = makeState(
            fortressGunPower = 10_000,
            fortressGunCooldown = 120,
            fortressFactionId = 1L,
            tickCount = 9999,
            friendly,
        )

        system.processFortressGunFire(state)

        val fireEvents = state.tickEvents.filter { it.type == "fortress_fire" }
        assertTrue(fireEvents.isEmpty(), "Should not fire when no enemies present")
    }

    // ── fortressGunPower = 0 means no gun ──

    @Test
    fun `fortress gun does not fire when power is 0`() {
        val enemy = makeUnit(fleetId = 2L, factionId = 2L, posX = 500.0, posY = 300.0)

        val state = makeState(
            fortressGunPower = 0,
            fortressGunCooldown = 60,
            fortressFactionId = 1L,
            tickCount = 9999,
            enemy,
        )

        system.processFortressGunFire(state)

        val fireEvents = state.tickEvents.filter { it.type == "fortress_fire" }
        assertTrue(fireEvents.isEmpty(), "Should not fire when fortressGunPower is 0")
    }
}
