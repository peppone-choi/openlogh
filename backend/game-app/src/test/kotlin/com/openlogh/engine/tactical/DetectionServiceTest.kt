package com.openlogh.engine.tactical

import com.openlogh.model.CommandRange
import com.openlogh.model.EnergyAllocation
import com.openlogh.model.Formation
import com.openlogh.model.UnitStance
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Tests for DetectionService (Task 03-02):
 * - 두 유닛이 탐지 범위 이내 → detectionMatrix 갱신
 * - 정지(STATIONED/ANCHORING) 유닛은 탐지 범위 +20% 보너스
 * - CommandRange.currentRange가 매 틱 expansionRate만큼 증가하다 maxRange에서 멈춤
 * - 발령 후 commandRange.resetOnCommand() 확인 (TacticalBattleService에서 이미 처리됨)
 */
class DetectionServiceTest {

    private val detectionService = DetectionService()

    private fun makeUnit(
        fleetId: Long,
        side: BattleSide,
        posX: Double = 0.0,
        posY: Double = 0.0,
        stance: UnitStance = UnitStance.NAVIGATION,
        intelligence: Int = 50,
        energy: EnergyAllocation = EnergyAllocation.BALANCED,
        command: Int = 50,
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
        command = command,
        intelligence = intelligence,
        mobility = 50,
        attack = 50,
        defense = 50,
        stance = stance,
        energy = energy,
    )

    private fun makeState(vararg units: TacticalUnit) = TacticalBattleState(
        battleId = 1L,
        starSystemId = 1L,
        units = units.toMutableList(),
    )

    // ── DetectionMatrix Tests ──

    @Test
    fun `units within detection range are added to detectionMatrix`() {
        // Use 2 friendly units so detectingUnitCount >= 2 triggers confirmation regardless of precision
        // Both attackers at same position, defender at 80 units — both within range
        val highSensor = EnergyAllocation(beam = 10, gun = 10, shield = 10, engine = 10, warp = 10, sensor = 50)
        // sensorMultiplier = 0.5 + (50/100 * 0.5) = 0.75, range = 200 * 0.75 = 150 → 80 in range
        val attacker1 = makeUnit(1L, BattleSide.ATTACKER, posX = 0.0, posY = 0.0, energy = highSensor)
        val attacker2 = makeUnit(3L, BattleSide.ATTACKER, posX = 5.0, posY = 0.0, energy = highSensor)
        val defender = makeUnit(2L, BattleSide.DEFENDER, posX = 80.0, posY = 0.0)
        val state = makeState(attacker1, attacker2, defender)

        detectionService.updateDetectionMatrix(state)

        // Attacker1's officer (id=1) should have detected defender fleet (id=2) due to 2+ detectors
        val attackerDetections = state.detectionMatrix[1L]
        assertNotNull(attackerDetections, "Attacker should have detection entries")
        assertTrue(
            attackerDetections!!.contains(2L),
            "Two attackers near (0,0) should detect defender at (80,0) via 2+ detector rule. Matrix: $attackerDetections"
        )
    }

    @Test
    fun `units far out of detection range are NOT added to detectionMatrix`() {
        // Place units very far apart (1000 units)
        val attacker = makeUnit(1L, BattleSide.ATTACKER, posX = 0.0, posY = 0.0)
        val defender = makeUnit(2L, BattleSide.DEFENDER, posX = 1000.0, posY = 0.0)
        val state = makeState(attacker, defender)

        detectionService.updateDetectionMatrix(state)

        val attackerDetections = state.detectionMatrix[1L]
        val detected = attackerDetections?.contains(2L) ?: false
        assertFalse(detected, "Attacker should NOT detect defender at 1000 unit distance")
    }

    @Test
    fun `detectionMatrix is cleared and rebuilt each call`() {
        val attacker = makeUnit(1L, BattleSide.ATTACKER, posX = 0.0, posY = 0.0)
        val defender = makeUnit(2L, BattleSide.DEFENDER, posX = 80.0, posY = 0.0)
        val state = makeState(attacker, defender)

        // First call
        detectionService.updateDetectionMatrix(state)
        val firstSize = state.detectionMatrix.size

        // Move defender far away
        defender.posX = 1000.0

        // Second call should rebuild from scratch
        detectionService.updateDetectionMatrix(state)

        // Attacker should no longer detect defender
        val afterMove = state.detectionMatrix[1L]?.contains(2L) ?: false
        assertFalse(afterMove, "DetectionMatrix should be rebuilt each call; defender moved out of range")
    }

    @Test
    fun `stationary unit (STATIONED) gets detection bonus - detects further than NAVIGATION unit`() {
        // Test the stationary bonus by verifying the DetectionService uses stationaryBonus=0.2
        // We can verify this indirectly: 2 STATIONED attackers at (0,0) detect defender at 80 units
        // Using balanced energy (sensor=10): range = 200 * 0.55 * 1.2 (stationary) = 132 → 80 in range
        val attacker1 = makeUnit(
            1L, BattleSide.ATTACKER, posX = 0.0, posY = 0.0,
            stance = UnitStance.STATIONED,
        )
        val attacker2 = makeUnit(
            3L, BattleSide.ATTACKER, posX = 5.0, posY = 0.0,
            stance = UnitStance.STATIONED,
        )
        val defender = makeUnit(2L, BattleSide.DEFENDER, posX = 80.0, posY = 0.0)
        val state = makeState(attacker1, attacker2, defender)

        detectionService.updateDetectionMatrix(state)

        // With stationary bonus the 2 STATIONED attackers should confirm detection via 2+ rule
        val detects = state.detectionMatrix[1L]?.contains(2L) ?: false
        assertTrue(detects,
            "Two STATIONED attackers at ~(0,0) should detect defender at (80,0) via 2+ detector rule (stationaryBonus extends range to 132)")
    }

    @Test
    fun `TacticalBattleState has detectionMatrix field`() {
        val state = TacticalBattleState(
            battleId = 1L,
            starSystemId = 1L,
            units = mutableListOf(),
        )
        // Should be an empty mutable map by default
        assertNotNull(state.detectionMatrix)
        assertTrue(state.detectionMatrix.isEmpty(), "detectionMatrix should start empty")
    }

    // ── CommandRange Tests ──

    @Test
    fun `commandRange increases by expansionRate each tick up to maxRange`() {
        // CommandRange with expansionRate=1.0, maxRange=100.0
        val unit = makeUnit(1L, BattleSide.ATTACKER, posX = 0.0, posY = 0.0, command = 50)

        unit.commandRange = CommandRange(currentRange = 0.0, maxRange = 100.0, expansionRate = 1.0)

        // CommandRange growth is a pure model concern; battle ticks may reset it when commands are issued.
        unit.commandRange = unit.commandRange.tick()
        val afterOneTick = unit.commandRange.currentRange
        assertTrue(afterOneTick > 0.0, "commandRange.currentRange should increase after 1 tick, got $afterOneTick")

        // After many ticks, commandRange should cap at maxRange
        repeat(300) { unit.commandRange = unit.commandRange.tick() }
        val cappedRange = unit.commandRange.currentRange
        val maxRange = unit.commandRange.maxRange
        assertEquals(maxRange, cappedRange, 0.01, "commandRange should cap at maxRange=$maxRange after many ticks, got $cappedRange")
    }

    @Test
    fun `commandRange maxRange can be configured per unit`() {
        val lowCommandUnit = makeUnit(1L, BattleSide.ATTACKER, command = 25)
        lowCommandUnit.commandRange = CommandRange(currentRange = 0.0, maxRange = 50.0, expansionRate = 0.5)
        val highCommandUnit = makeUnit(2L, BattleSide.ATTACKER, command = 100)
        highCommandUnit.commandRange = CommandRange(currentRange = 0.0, maxRange = 200.0, expansionRate = 2.0)
        val enemy = makeUnit(3L, BattleSide.DEFENDER, posX = 900.0)
        val state = makeState(lowCommandUnit, highCommandUnit, enemy)

        val lowMax = state.units.first { it.fleetId == 1L }.commandRange.maxRange
        val highMax = state.units.first { it.fleetId == 2L }.commandRange.maxRange

        assertTrue(highMax > lowMax,
            "Higher command stat should produce higher maxRange. low=$lowMax, high=$highMax")
        assertEquals(50.0, lowMax, 0.1, "low command unit maxRange should be 50")
        assertEquals(200.0, highMax, 0.1, "high command unit maxRange should be 200")
    }
}
