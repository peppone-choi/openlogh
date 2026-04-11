package com.openlogh.engine.tactical

import com.openlogh.model.EnergyAllocation
import com.openlogh.model.Formation
import com.openlogh.model.UnitStance
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * Phase 24-20 (gap E42, gin7 manual p46-47):
 * 전술전 맵의 지형 장애물(플라즈마 폭풍 / 사르가소 / 소행성대) 가 사선을 차단하는지를
 * 검증한다. 엔진은 pure 하게 테스트되며, 장애물이 경로 위에 있으면 BEAM/GUN/MISSILE
 * 사격이 차단되어 적 HP 가 유지되어야 한다.
 *
 * 검증 관점:
 *   1. 장애물이 없으면 타격이 발생한다(대조군).
 *   2. 장애물이 경로 정중앙에 놓이면 타격이 발생하지 않는다.
 *   3. 장애물이 경로 밖 (perpendicular distance > radius) 이면 차단 안 됨.
 *   4. 장애물이 사격자 뒤쪽(음수 projection) 이면 차단 안 됨.
 *   5. 장애물이 타겟 너머(projection > totalLen) 이면 차단 안 됨.
 *   6. blocksLineOfSight=false 타입 장애물은 무시.
 */
class TerrainObstacleTest {

    private val engine = TacticalBattleEngine()

    private fun makeUnit(
        fleetId: Long,
        side: BattleSide,
        posX: Double,
        posY: Double,
        unitType: String = "FLEET",
    ) = TacticalUnit(
        fleetId = fleetId,
        officerId = fleetId,
        officerName = "Officer $fleetId",
        factionId = if (side == BattleSide.ATTACKER) 1L else 2L,
        side = side,
        posX = posX,
        posY = posY,
        hp = 2000,
        maxHp = 2000,
        ships = 300,
        maxShips = 300,
        training = 80,
        morale = 80,
        leadership = 80,
        command = 80,
        intelligence = 60,
        mobility = 50,
        attack = 80,
        defense = 40,
        energy = EnergyAllocation(beam = 60, gun = 0, shield = 10, engine = 10, warp = 10, sensor = 10),
        missileCount = 0,
        unitType = unitType,
        formation = Formation.MIXED,
        stance = UnitStance.COMBAT,
    )

    private fun baseState(obstacles: List<TerrainObstacle> = emptyList()): TacticalBattleState {
        val attacker = makeUnit(1L, BattleSide.ATTACKER, posX = 0.0, posY = 0.0)
        val defender = makeUnit(2L, BattleSide.DEFENDER,
            posX = TacticalBattleEngine.BEAM_RANGE * 0.7, posY = 0.0)
        val state = TacticalBattleState(
            battleId = 1L,
            starSystemId = 1L,
            units = mutableListOf(attacker, defender),
            obstacles = obstacles.toMutableList(),
        )
        return state
    }

    private fun runNTicks(state: TacticalBattleState, ticks: Int = 10, seed: Long = 42L) {
        val rng = Random(seed)
        repeat(ticks) { engine.processTick(state, rng) }
    }

    @Test
    fun `control case - no obstacle means beam fire connects`() {
        val state = baseState()
        val defender = state.units.first { it.side == BattleSide.DEFENDER }
        val initialHp = defender.hp

        runNTicks(state)

        assertTrue(defender.hp < initialHp,
            "without obstacles, beams must connect and reduce defender HP (initial=$initialHp, now=${defender.hp})")
    }

    @Test
    fun `plasma storm on the line of fire blocks beams entirely`() {
        val midX = TacticalBattleEngine.BEAM_RANGE * 0.35
        val state = baseState(
            obstacles = listOf(
                TerrainObstacle(posX = midX, posY = 0.0, radius = 40.0, type = TerrainObstacleType.PLASMA_STORM)
            )
        )
        val defender = state.units.first { it.side == BattleSide.DEFENDER }
        val initialHp = defender.hp

        runNTicks(state)

        assertTrue(defender.hp == initialHp,
            "plasma storm straddling the line of fire must block all beams (hp=${defender.hp})")
    }

    @Test
    fun `sargasso off to the side does not block beams`() {
        val midX = TacticalBattleEngine.BEAM_RANGE * 0.35
        val state = baseState(
            obstacles = listOf(
                TerrainObstacle(posX = midX, posY = 200.0, radius = 40.0, type = TerrainObstacleType.SARGASSO)
            )
        )
        val defender = state.units.first { it.side == BattleSide.DEFENDER }
        val initialHp = defender.hp

        runNTicks(state)

        assertTrue(defender.hp < initialHp,
            "obstacle far from the shot line must not block (hp=${defender.hp})")
    }

    @Test
    fun `obstacle behind the shooter does not block`() {
        val state = baseState(
            obstacles = listOf(
                // Sits strictly behind the attacker (negative projection).
                TerrainObstacle(posX = -50.0, posY = 0.0, radius = 30.0, type = TerrainObstacleType.PLASMA_STORM)
            )
        )
        val defender = state.units.first { it.side == BattleSide.DEFENDER }
        val initialHp = defender.hp

        runNTicks(state)

        assertTrue(defender.hp < initialHp,
            "obstacle behind the shooter must not block (hp=${defender.hp})")
    }

    @Test
    fun `obstacle past the target does not block`() {
        val past = TacticalBattleEngine.BEAM_RANGE * 0.9
        val state = baseState(
            obstacles = listOf(
                TerrainObstacle(posX = past, posY = 0.0, radius = 20.0, type = TerrainObstacleType.PLASMA_STORM)
            )
        )
        val defender = state.units.first { it.side == BattleSide.DEFENDER }
        val initialHp = defender.hp

        runNTicks(state)

        assertTrue(defender.hp < initialHp,
            "obstacle past the target must not block (hp=${defender.hp})")
    }

    @Test
    fun `asteroid field blocks and also marks warp as blocked`() {
        assertTrue(TerrainObstacleType.ASTEROID_FIELD.blocksLineOfSight)
        assertTrue(TerrainObstacleType.ASTEROID_FIELD.blocksWarp,
            "소행성대 is the one obstacle type that also blocks warp entry per gin7 p47")
    }
}
