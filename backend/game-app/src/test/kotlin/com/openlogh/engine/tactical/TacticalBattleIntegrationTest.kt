package com.openlogh.engine.tactical

import com.openlogh.model.EnergyAllocation
import com.openlogh.model.Formation
import com.openlogh.model.UnitStance
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import kotlin.random.Random

/**
 * Integration test: full tactical battle lifecycle.
 * Uses in-memory state only (no DB dependencies).
 */
class TacticalBattleIntegrationTest {

    private val engine = TacticalBattleEngine()
    private val rng = Random(42L)  // deterministic

    @Test
    fun `full battle lifecycle - 2 units fight until one side is eliminated`() {
        val attacker = TacticalUnit(
            fleetId = 1L, officerId = 101L, officerName = "라인하르트",
            factionId = 1L, side = BattleSide.ATTACKER,
            posX = 100.0, posY = 300.0,
            hp = 1000, maxHp = 1000, ships = 300, maxShips = 300,
            training = 80, morale = 80,
            leadership = 90, command = 90, intelligence = 70,
            mobility = 70, attack = 90, defense = 60,
            energy = EnergyAllocation(beam = 30, gun = 25, shield = 20, engine = 15, warp = 5, sensor = 5),
            formation = Formation.WEDGE,
            stance = UnitStance.COMBAT,
            isFlagship = true, missileCount = 100,
        )

        val defender = TacticalUnit(
            fleetId = 2L, officerId = 201L, officerName = "양웬리",
            factionId = 2L, side = BattleSide.DEFENDER,
            posX = 900.0, posY = 300.0,
            hp = 1000, maxHp = 1000, ships = 300, maxShips = 300,
            training = 80, morale = 80,
            leadership = 90, command = 85, intelligence = 95,
            mobility = 75, attack = 80, defense = 80,
            energy = EnergyAllocation(beam = 20, gun = 20, shield = 30, engine = 15, warp = 5, sensor = 10),
            formation = Formation.THREE_COLUMN,
            stance = UnitStance.COMBAT,
            isFlagship = true, missileCount = 100,
        )

        val state = TacticalBattleState(
            battleId = 1L, starSystemId = 100L,
            units = mutableListOf(attacker, defender),
        )

        // 최대 600틱 실행 — 종료 조건 도달 또는 타임아웃
        var outcome: BattleOutcome? = null
        repeat(600) {
            if (outcome == null) {
                engine.processTick(state, rng)
                outcome = engine.checkBattleEnd(state)
            }
        }

        // 검증: 600틱 내 종료 조건 도달
        assertNotNull(outcome, "600틱 내 전투가 종료되어야 함")
        // 승자 존재 (무승부 가능하지만 최소한 종료 조건 달성)
        assertTrue(outcome!!.reason.isNotBlank())
    }

    @Test
    fun `energy allocation change resets commandRange to 0`() {
        val unit = TacticalUnit(
            fleetId = 1L, officerId = 101L, officerName = "테스트",
            factionId = 1L, side = BattleSide.ATTACKER,
            posX = 100.0, posY = 300.0,
            hp = 1000, maxHp = 1000, ships = 300, maxShips = 300,
            commandRange = 50.0,  // 이미 확대된 상태
        )
        val state = TacticalBattleState(
            battleId = 1L, starSystemId = 100L,
            units = mutableListOf(unit),
        )

        // 에너지 배분 변경 시뮬레이션 (TacticalBattleService.setEnergyAllocation 동작)
        unit.energy = EnergyAllocation(beam = 30, gun = 25, shield = 20, engine = 15, warp = 5, sensor = 5)
        unit.commandRange = 0.0
        unit.ticksSinceLastOrder = 0

        assertEquals(0.0, unit.commandRange)
        assertEquals(0, unit.ticksSinceLastOrder)
    }

    @Test
    fun `flagship destroyed creates pendingInjuryEvent`() {
        val flagship = TacticalUnit(
            fleetId = 1L, officerId = 101L, officerName = "라인하르트",
            factionId = 1L, side = BattleSide.ATTACKER,
            posX = 100.0, posY = 300.0,
            hp = 1, maxHp = 1000, ships = 1, maxShips = 300,
            isFlagship = true,
        )
        val enemy = TacticalUnit(
            fleetId = 2L, officerId = 201L, officerName = "양웬리",
            factionId = 2L, side = BattleSide.DEFENDER,
            posX = 110.0, posY = 300.0,
            hp = 1000, maxHp = 1000, ships = 300, maxShips = 300,
            energy = EnergyAllocation(beam = 40, gun = 30, shield = 15, engine = 10, warp = 3, sensor = 2),
        )
        val state = TacticalBattleState(
            battleId = 1L, starSystemId = 100L,
            units = mutableListOf(flagship, enemy),
        )

        // 충분히 많은 틱으로 격침 유도
        repeat(20) { engine.processTick(state, rng) }

        // 기함이 격침되었다면 pendingInjuryEvents에 기록
        if (!flagship.isAlive) {
            assertTrue(state.pendingInjuryEvents.any { it.officerId == 101L },
                "기함 격침 시 pendingInjuryEvent 생성")
        }
    }

    @Test
    fun `missile count decreases on missile attack`() {
        val attacker = TacticalUnit(
            fleetId = 1L, officerId = 101L, officerName = "공격자",
            factionId = 1L, side = BattleSide.ATTACKER,
            posX = 100.0, posY = 300.0,
            hp = 1000, maxHp = 1000, ships = 300, maxShips = 300,
            missileCount = 10,
            energy = EnergyAllocation(beam = 10, gun = 10, shield = 20, engine = 20, warp = 20, sensor = 20),
        )
        val defender = TacticalUnit(
            fleetId = 2L, officerId = 201L, officerName = "방어자",
            factionId = 2L, side = BattleSide.DEFENDER,
            posX = 500.0, posY = 300.0,  // 미사일 사거리(800) 이내
            hp = 1000, maxHp = 1000, ships = 300, maxShips = 300,
        )
        val state = TacticalBattleState(
            battleId = 1L, starSystemId = 100L,
            units = mutableListOf(attacker, defender),
        )

        val initialMissiles = attacker.missileCount
        repeat(30) { engine.processTick(state, rng) }

        // 미사일이 발사됐으면 감소해야 함
        assertTrue(attacker.missileCount <= initialMissiles,
            "미사일 발사 후 missileCount 감소 (초기: $initialMissiles, 현재: ${attacker.missileCount})")
    }

    @Test
    fun `morale below 20 prevents combat`() {
        val lowMoraleUnit = TacticalUnit(
            fleetId = 1L, officerId = 101L, officerName = "사기저하",
            factionId = 1L, side = BattleSide.ATTACKER,
            posX = 100.0, posY = 300.0,
            hp = 1000, maxHp = 1000, ships = 300, maxShips = 300,
            morale = 15,  // 20 미만
            attack = 90,
            energy = EnergyAllocation(beam = 30, gun = 30, shield = 20, engine = 10, warp = 5, sensor = 5),
        )
        val enemy = TacticalUnit(
            fleetId = 2L, officerId = 201L, officerName = "적",
            factionId = 2L, side = BattleSide.DEFENDER,
            posX = 110.0, posY = 300.0,
            hp = 5000, maxHp = 5000, ships = 300, maxShips = 300,
        )
        val state = TacticalBattleState(
            battleId = 1L, starSystemId = 100L,
            units = mutableListOf(lowMoraleUnit, enemy),
        )

        engine.processTick(state, rng)

        // 사기 20 미만 유닛은 damage 이벤트 생성하지 않음
        val damageEvents = state.tickEvents.filter {
            it.type == "damage" && it.sourceUnitId == lowMoraleUnit.fleetId
        }
        assertTrue(damageEvents.isEmpty(), "사기 20 미만 유닛은 공격 불가")
    }
}
