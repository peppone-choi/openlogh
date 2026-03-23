package com.openlogh.engine.tactical

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested
import kotlin.random.Random

class TacticalBattleEngineTest {

    // ===== Turn Resolution =====

    @Nested
    inner class TurnResolution {

        @Test
        fun `resolveTurn increments turn counter`() {
            val session = TacticalTestFixtures.createSession()
            session.phase = BattlePhase.COMBAT
            val engine = TacticalTestFixtures.seededEngine()

            val result = engine.resolveTurn(session)
            assertEquals(1, result.turn)
            assertEquals(1, session.currentTurn)
        }

        @Test
        fun `resolveTurn without orders produces no movement or attack events`() {
            val session = TacticalTestFixtures.createSession()
            session.phase = BattlePhase.COMBAT
            val engine = TacticalTestFixtures.seededEngine()

            val result = engine.resolveTurn(session)
            val moveEvents = result.events.filterIsInstance<BattleEvent.MoveEvent>()
            val attackEvents = result.events.filterIsInstance<BattleEvent.AttackEvent>()
            assertTrue(moveEvents.isEmpty())
            assertTrue(attackEvents.isEmpty())
        }

        @Test
        fun `resolveTurn clears pending orders after processing`() {
            val session = TacticalTestFixtures.createSession()
            session.phase = BattlePhase.COMBAT
            session.submitOrders(1L, listOf(
                TacticalOrder(officerId = 1L, unitId = 0, type = OrderType.MOVE, targetX = 200.0, targetY = 200.0)
            ))
            val engine = TacticalTestFixtures.seededEngine()

            engine.resolveTurn(session)
            assertTrue(session.pendingOrders.isEmpty())
        }
    }

    // ===== Movement =====

    @Nested
    inner class Movement {

        @Test
        fun `unit moves toward target within movement range`() {
            val session = TacticalTestFixtures.createSession()
            session.phase = BattlePhase.COMBAT
            val unit = session.attackerFleets[0].units[0]
            val startX = unit.x
            val startY = unit.y

            session.submitOrders(1L, listOf(
                TacticalOrder(officerId = 1L, unitId = unit.id, type = OrderType.MOVE, targetX = 200.0, targetY = 200.0)
            ))

            val engine = TacticalTestFixtures.seededEngine()
            val result = engine.resolveTurn(session)

            val moveEvents = result.events.filterIsInstance<BattleEvent.MoveEvent>()
            assertEquals(1, moveEvents.size)

            // 유닛이 이동했는지 확인
            assertTrue(unit.x != startX || unit.y != startY)
            // 그리드 위치도 갱신 확인
            val gridPos = session.grid.getUnitPosition(unit.id)
            assertNotNull(gridPos)
            assertEquals(unit.x, gridPos!!.x, 0.001)
            assertEquals(unit.y, gridPos.y, 0.001)
        }

        @Test
        fun `unit does not exceed movement range`() {
            val unit = TacticalTestFixtures.createUnit(0, x = 100.0, y = 100.0, shipClass = TacticalShipClass.BATTLESHIP)
            val fleet = TacticalTestFixtures.createFleet(units = listOf(unit))
            val session = TacticalTestFixtures.createSession(attackerFleets = listOf(fleet))
            session.phase = BattlePhase.COMBAT

            // 매우 먼 목표
            session.submitOrders(1L, listOf(
                TacticalOrder(officerId = 1L, unitId = 0, type = OrderType.MOVE, targetX = 999.0, targetY = 999.0)
            ))

            val engine = TacticalTestFixtures.seededEngine()
            val result = engine.resolveTurn(session)

            val moveEvent = result.events.filterIsInstance<BattleEvent.MoveEvent>().first()
            val maxRange = unit.getMovementRange(fleet.energy, fleet.formation)
            assertTrue(moveEvent.distance <= maxRange + 0.01, "distance ${moveEvent.distance} should be <= $maxRange")
        }

        @Test
        fun `obstacle blocks movement path`() {
            val grid = TacticalGrid()
            // 이동 경로 중간에 소행성대
            grid.addObstacle(Obstacle(Position(150.0, 150.0), 40.0, ObstacleType.ASTEROID))

            val unit = TacticalTestFixtures.createUnit(0, x = 100.0, y = 100.0)
            val fleet = TacticalTestFixtures.createFleet(units = listOf(unit))
            val session = TacticalTestFixtures.createSession(attackerFleets = listOf(fleet), grid = grid)
            session.phase = BattlePhase.COMBAT

            session.submitOrders(1L, listOf(
                TacticalOrder(officerId = 1L, unitId = 0, type = OrderType.MOVE, targetX = 200.0, targetY = 200.0)
            ))

            val engine = TacticalTestFixtures.seededEngine()
            engine.resolveTurn(session)

            // 유닛이 소행성 내부에 있으면 안 됨
            val pos = unit.position()
            val distToObstacle = pos.distanceTo(Position(150.0, 150.0))
            assertTrue(distToObstacle >= 39.0, "Unit should not be inside asteroid (dist=$distToObstacle)")
        }
    }

    // ===== Attack =====

    @Nested
    inner class Attack {

        @Test
        fun `attack hits target in range`() {
            // 공격 유닛과 타겟을 공격 범위 내에 배치
            val attacker = TacticalTestFixtures.createUnit(0, factionId = 1L, officerId = 1L, x = 100.0, y = 100.0,
                shipClass = TacticalShipClass.BATTLESHIP, isFlagship = true)
            val target = TacticalTestFixtures.createUnit(10, fleetId = 2L, factionId = 2L, officerId = 2L,
                x = 200.0, y = 100.0, shipClass = TacticalShipClass.BATTLESHIP, isFlagship = true)

            val attackerFleet = TacticalTestFixtures.createFleet(fleetId = 1L, officerId = 1L, factionId = 1L, units = listOf(attacker))
            val defenderFleet = TacticalTestFixtures.createFleet(fleetId = 2L, officerId = 2L, factionId = 2L,
                officer = TacticalTestFixtures.defaultOfficerStats(officerId = 2L, name = "방어측"),
                units = listOf(target))

            val session = TacticalTestFixtures.createSession(
                attackerFleets = listOf(attackerFleet),
                defenderFleets = listOf(defenderFleet),
            )
            session.phase = BattlePhase.COMBAT

            // 거리 100 → BEAM 범위 내 (battleship beamRange = 300 + sensor bonus)
            session.submitOrders(1L, listOf(
                TacticalOrder(officerId = 1L, unitId = 0, type = OrderType.ATTACK, targetUnitId = 10)
            ))

            val engine = TacticalTestFixtures.seededEngine()
            val result = engine.resolveTurn(session)

            val attackEvents = result.events.filterIsInstance<BattleEvent.AttackEvent>()
            assertEquals(1, attackEvents.size)
            assertEquals(0, attackEvents[0].attackerUnitId)
            assertEquals(10, attackEvents[0].targetUnitId)
        }

        @Test
        fun `attack out of range does not hit`() {
            // 타겟을 사거리 밖에 배치 (beamRange ~360 for balanced battleship)
            val attacker = TacticalTestFixtures.createUnit(0, factionId = 1L, officerId = 1L, x = 50.0, y = 50.0,
                shipClass = TacticalShipClass.DESTROYER, isFlagship = true) // destroyer beamRange = 200 + 60 = 260
            val target = TacticalTestFixtures.createUnit(10, fleetId = 2L, factionId = 2L, officerId = 2L,
                x = 50.0, y = 500.0, shipClass = TacticalShipClass.BATTLESHIP, isFlagship = true)

            val attackerFleet = TacticalTestFixtures.createFleet(fleetId = 1L, officerId = 1L, factionId = 1L, units = listOf(attacker))
            val defenderFleet = TacticalTestFixtures.createFleet(fleetId = 2L, officerId = 2L, factionId = 2L,
                officer = TacticalTestFixtures.defaultOfficerStats(officerId = 2L, name = "방어측"),
                units = listOf(target))

            val session = TacticalTestFixtures.createSession(
                attackerFleets = listOf(attackerFleet),
                defenderFleets = listOf(defenderFleet),
            )
            session.phase = BattlePhase.COMBAT

            session.submitOrders(1L, listOf(
                TacticalOrder(officerId = 1L, unitId = 0, type = OrderType.ATTACK, targetUnitId = 10)
            ))

            val engine = TacticalTestFixtures.seededEngine()
            val result = engine.resolveTurn(session)

            val attackEvents = result.events.filterIsInstance<BattleEvent.AttackEvent>()
            assertTrue(attackEvents.isEmpty(), "Attack should not occur when out of range")
        }

        @Test
        fun `nebula blocks line of fire`() {
            val grid = TacticalGrid()
            grid.addObstacle(Obstacle(Position(150.0, 100.0), 30.0, ObstacleType.NEBULA))

            val attacker = TacticalTestFixtures.createUnit(0, factionId = 1L, officerId = 1L, x = 100.0, y = 100.0,
                shipClass = TacticalShipClass.BATTLESHIP, isFlagship = true)
            val target = TacticalTestFixtures.createUnit(10, fleetId = 2L, factionId = 2L, officerId = 2L,
                x = 200.0, y = 100.0, shipClass = TacticalShipClass.BATTLESHIP, isFlagship = true)

            val attackerFleet = TacticalTestFixtures.createFleet(fleetId = 1L, officerId = 1L, factionId = 1L, units = listOf(attacker))
            val defenderFleet = TacticalTestFixtures.createFleet(fleetId = 2L, officerId = 2L, factionId = 2L,
                officer = TacticalTestFixtures.defaultOfficerStats(officerId = 2L, name = "방어측"),
                units = listOf(target))

            val session = TacticalTestFixtures.createSession(
                attackerFleets = listOf(attackerFleet),
                defenderFleets = listOf(defenderFleet),
                grid = grid,
            )
            session.phase = BattlePhase.COMBAT

            session.submitOrders(1L, listOf(
                TacticalOrder(officerId = 1L, unitId = 0, type = OrderType.ATTACK, targetUnitId = 10)
            ))

            val engine = TacticalTestFixtures.seededEngine()
            val result = engine.resolveTurn(session)

            val attackEvents = result.events.filterIsInstance<BattleEvent.AttackEvent>()
            assertTrue(attackEvents.isEmpty(), "Nebula should block line of fire")
        }

        @Test
        fun `simultaneous damage resolution - both units take damage`() {
            val attacker = TacticalTestFixtures.createUnit(0, factionId = 1L, officerId = 1L, x = 100.0, y = 500.0,
                shipClass = TacticalShipClass.BATTLESHIP, hp = 300, isFlagship = true)
            val target = TacticalTestFixtures.createUnit(10, fleetId = 2L, factionId = 2L, officerId = 2L,
                x = 200.0, y = 500.0, shipClass = TacticalShipClass.BATTLESHIP, hp = 300, isFlagship = true)

            val attackerFleet = TacticalTestFixtures.createFleet(fleetId = 1L, officerId = 1L, factionId = 1L, units = listOf(attacker))
            val defenderFleet = TacticalTestFixtures.createFleet(fleetId = 2L, officerId = 2L, factionId = 2L,
                officer = TacticalTestFixtures.defaultOfficerStats(officerId = 2L, name = "방어측"),
                units = listOf(target))

            val session = TacticalTestFixtures.createSession(
                attackerFleets = listOf(attackerFleet),
                defenderFleets = listOf(defenderFleet),
            )
            session.phase = BattlePhase.COMBAT

            // 양측 동시 공격
            session.pendingOrders[1L] = mutableListOf(
                TacticalOrder(officerId = 1L, unitId = 0, type = OrderType.ATTACK, targetUnitId = 10)
            )
            session.pendingOrders[2L] = mutableListOf(
                TacticalOrder(officerId = 2L, unitId = 10, type = OrderType.ATTACK, targetUnitId = 0)
            )

            val engine = TacticalTestFixtures.seededEngine()
            val result = engine.resolveTurn(session)

            val attackEvents = result.events.filterIsInstance<BattleEvent.AttackEvent>()
            assertEquals(2, attackEvents.size, "Both sides should attack")
        }
    }

    // ===== Formation & Energy =====

    @Nested
    inner class ConfigOrders {

        @Test
        fun `formation change order updates fleet formation`() {
            val session = TacticalTestFixtures.createSession()
            session.phase = BattlePhase.COMBAT

            session.submitOrders(1L, listOf(
                TacticalOrder(officerId = 1L, type = OrderType.FORMATION_CHANGE, formation = Formation.SQUARE)
            ))

            val engine = TacticalTestFixtures.seededEngine()
            val result = engine.resolveTurn(session)

            assertEquals(Formation.SQUARE, session.attackerFleets[0].formation)
            val formEvents = result.events.filterIsInstance<BattleEvent.FormationChangeEvent>()
            assertEquals(1, formEvents.size)
            assertEquals(Formation.SPINDLE, formEvents[0].oldFormation)
            assertEquals(Formation.SQUARE, formEvents[0].newFormation)
        }

        @Test
        fun `energy change order updates fleet energy`() {
            val session = TacticalTestFixtures.createSession()
            session.phase = BattlePhase.COMBAT

            session.submitOrders(1L, listOf(
                TacticalOrder(officerId = 1L, type = OrderType.ENERGY_CHANGE, energy = EnergyAllocation.AGGRESSIVE)
            ))

            val engine = TacticalTestFixtures.seededEngine()
            val result = engine.resolveTurn(session)

            assertEquals(EnergyAllocation.AGGRESSIVE, session.attackerFleets[0].energy)
            val energyEvents = result.events.filterIsInstance<BattleEvent.EnergyChangeEvent>()
            assertEquals(1, energyEvents.size)
        }
    }

    // ===== Morale =====

    @Nested
    inner class Morale {

        @Test
        fun `destroying units reduces fleet morale`() {
            // 1 HP 유닛으로 즉사 보장
            val target = TacticalTestFixtures.createUnit(10, fleetId = 2L, factionId = 2L, officerId = 2L,
                x = 200.0, y = 100.0, shipClass = TacticalShipClass.BATTLESHIP, hp = 1, isFlagship = true)
            val attacker = TacticalTestFixtures.createUnit(0, factionId = 1L, officerId = 1L, x = 100.0, y = 100.0,
                shipClass = TacticalShipClass.BATTLESHIP, isFlagship = true)

            val attackerFleet = TacticalTestFixtures.createFleet(fleetId = 1L, officerId = 1L, factionId = 1L, units = listOf(attacker))
            val defenderFleet = TacticalTestFixtures.createFleet(fleetId = 2L, officerId = 2L, factionId = 2L,
                officer = TacticalTestFixtures.defaultOfficerStats(officerId = 2L, name = "방어측"),
                units = listOf(target), morale = 50)

            val session = TacticalTestFixtures.createSession(
                attackerFleets = listOf(attackerFleet),
                defenderFleets = listOf(defenderFleet),
            )
            session.phase = BattlePhase.COMBAT

            session.submitOrders(1L, listOf(
                TacticalOrder(officerId = 1L, unitId = 0, type = OrderType.ATTACK, targetUnitId = 10)
            ))

            val engine = TacticalTestFixtures.seededEngine()
            val result = engine.resolveTurn(session)

            val destroyEvents = result.events.filterIsInstance<BattleEvent.DestroyEvent>()
            val moraleEvents = result.events.filterIsInstance<BattleEvent.MoraleChangeEvent>()

            if (destroyEvents.isNotEmpty()) {
                assertTrue(moraleEvents.isNotEmpty(), "Destroy should trigger morale loss")
                assertTrue(defenderFleet.morale < 50, "Morale should decrease")
            }
        }

        @Test
        fun `rally special restores morale`() {
            val session = TacticalTestFixtures.createSession()
            session.phase = BattlePhase.COMBAT
            session.attackerFleets[0].morale = 50

            session.submitOrders(1L, listOf(
                TacticalOrder(officerId = 1L, type = OrderType.SPECIAL, specialCode = "rally")
            ))

            val engine = TacticalTestFixtures.seededEngine()
            engine.resolveTurn(session)

            assertEquals(65, session.attackerFleets[0].morale)
        }
    }

    // ===== Victory Conditions =====

    @Nested
    inner class VictoryConditions {

        @Test
        fun `annihilation when all defender units destroyed`() {
            val target = TacticalTestFixtures.createUnit(10, fleetId = 2L, factionId = 2L, officerId = 2L,
                x = 200.0, y = 100.0, shipClass = TacticalShipClass.BATTLESHIP, hp = 1, isFlagship = true)
            val attacker = TacticalTestFixtures.createUnit(0, factionId = 1L, officerId = 1L, x = 100.0, y = 100.0,
                shipClass = TacticalShipClass.BATTLESHIP, hp = 300, isFlagship = true)

            val attackerFleet = TacticalTestFixtures.createFleet(fleetId = 1L, officerId = 1L, factionId = 1L,
                units = listOf(attacker), morale = 100)
            val defenderFleet = TacticalTestFixtures.createFleet(fleetId = 2L, officerId = 2L, factionId = 2L,
                officer = TacticalTestFixtures.defaultOfficerStats(officerId = 2L, name = "방어측"),
                units = listOf(target), morale = 100)

            val session = TacticalTestFixtures.createSession(
                attackerFleets = listOf(attackerFleet),
                defenderFleets = listOf(defenderFleet),
            )
            session.phase = BattlePhase.COMBAT

            // 직접 유닛 파괴
            target.takeDamage(1)

            val engine = TacticalTestFixtures.seededEngine()
            val victory = engine.checkVictory(session, 1)

            assertNotNull(victory)
            assertEquals(VictoryType.ANNIHILATION, victory!!.victoryType)
            assertEquals(1L, victory.winnerFactionId)
        }

        @Test
        fun `time limit resolves by HP comparison`() {
            val session = TacticalTestFixtures.createSession()
            // 공격측 HP 더 많게 설정
            session.attackerFleets[0].units.forEach { it.hp = 300 }
            session.defenderFleets[0].units.forEach { it.hp = 100 }

            val engine = TacticalTestFixtures.seededEngine()
            val victory = engine.checkVictory(session, session.maxTurns)

            assertNotNull(victory)
            assertEquals(VictoryType.TIME_LIMIT, victory!!.victoryType)
            assertEquals(1L, victory.winnerFactionId)
        }

        @Test
        fun `no victory when both sides alive and turn limit not reached`() {
            val session = TacticalTestFixtures.createSession()
            val engine = TacticalTestFixtures.seededEngine()
            val victory = engine.checkVictory(session, 5)
            assertNull(victory)
        }

        @Test
        fun `forced retreat when morale drops to 20 or below`() {
            val session = TacticalTestFixtures.createSession()
            session.phase = BattlePhase.COMBAT
            session.defenderFleets[0].morale = 18 // 사기 붕괴

            val engine = TacticalTestFixtures.seededEngine()
            val result = engine.resolveTurn(session)

            val retreatEvents = result.events.filterIsInstance<BattleEvent.RetreatEvent>()
            assertTrue(retreatEvents.any { it.forced }, "Low morale should trigger forced retreat")
        }
    }

    // ===== Retreat =====

    @Nested
    inner class Retreat {

        @Test
        fun `voluntary retreat removes units from grid`() {
            val session = TacticalTestFixtures.createSession()
            session.phase = BattlePhase.COMBAT

            session.submitOrders(1L, listOf(
                TacticalOrder(officerId = 1L, type = OrderType.RETREAT)
            ))

            val engine = TacticalTestFixtures.seededEngine()
            val result = engine.resolveTurn(session)

            val retreatEvents = result.events.filterIsInstance<BattleEvent.RetreatEvent>()
            assertEquals(1, retreatEvents.size)
            assertFalse(retreatEvents[0].forced)

            // 퇴각한 유닛은 그리드에서 제거
            for (unit in session.attackerFleets[0].units) {
                assertNull(session.grid.getUnitPosition(unit.id))
            }
        }
    }

    // ===== Fortress Cannon =====

    @Nested
    inner class FortressCannon {

        @Test
        fun `fortress cannon deals AoE damage`() {
            val fortress = TacticalTestFixtures.createUnit(0, factionId = 1L, officerId = 1L,
                shipClass = TacticalShipClass.FORTRESS, hp = 1000, x = 500.0, y = 100.0, isFlagship = true)
            val target1 = TacticalTestFixtures.createUnit(10, fleetId = 2L, factionId = 2L, officerId = 2L,
                x = 500.0, y = 800.0, shipClass = TacticalShipClass.BATTLESHIP, hp = 300, isFlagship = true)
            val target2 = TacticalTestFixtures.createUnit(11, fleetId = 2L, factionId = 2L, officerId = 2L,
                x = 530.0, y = 820.0, shipClass = TacticalShipClass.CRUISER, hp = 300)

            val attackerFleet = TacticalTestFixtures.createFleet(fleetId = 1L, officerId = 1L, factionId = 1L, units = listOf(fortress))
            val defenderFleet = TacticalTestFixtures.createFleet(fleetId = 2L, officerId = 2L, factionId = 2L,
                officer = TacticalTestFixtures.defaultOfficerStats(officerId = 2L, name = "방어측"),
                units = listOf(target1, target2))

            val session = TacticalTestFixtures.createSession(
                attackerFleets = listOf(attackerFleet),
                defenderFleets = listOf(defenderFleet),
            )
            session.phase = BattlePhase.COMBAT

            // 타겟 근처에 요새포 발사
            session.submitOrders(1L, listOf(
                TacticalOrder(officerId = 1L, type = OrderType.SPECIAL, specialCode = "fortress_cannon",
                    targetX = 510.0, targetY = 810.0)
            ))

            val engine = TacticalTestFixtures.seededEngine()
            val result = engine.resolveTurn(session)

            val attackEvents = result.events.filterIsInstance<BattleEvent.AttackEvent>()
                .filter { it.weaponType == "FORTRESS_CANNON" }

            assertTrue(attackEvents.isNotEmpty(), "Fortress cannon should hit nearby targets")
            assertTrue(attackEvents.all { it.damage > 0 }, "All hits should deal damage")
        }
    }

    // ===== Grid =====

    @Nested
    inner class GridTests {

        @Test
        fun `position distance calculation`() {
            val a = Position(0.0, 0.0)
            val b = Position(3.0, 4.0)
            assertEquals(5.0, a.distanceTo(b), 0.001)
        }

        @Test
        fun `computeMoveTo respects max distance`() {
            val grid = TacticalGrid()
            val from = Position(100.0, 100.0)
            val target = Position(500.0, 100.0)
            val result = grid.computeMoveTo(from, target, 50.0)
            assertEquals(50.0, from.distanceTo(result), 0.1)
        }

        @Test
        fun `hasLineOfFire returns false through nebula`() {
            val grid = TacticalGrid()
            grid.addObstacle(Obstacle(Position(200.0, 100.0), 30.0, ObstacleType.NEBULA))
            assertFalse(grid.hasLineOfFire(Position(100.0, 100.0), Position(300.0, 100.0)))
        }

        @Test
        fun `hasLineOfFire returns true through debris`() {
            val grid = TacticalGrid()
            grid.addObstacle(Obstacle(Position(200.0, 100.0), 30.0, ObstacleType.DEBRIS))
            assertTrue(grid.hasLineOfFire(Position(100.0, 100.0), Position(300.0, 100.0)))
        }

        @Test
        fun `isBlocked returns true inside asteroid`() {
            val grid = TacticalGrid()
            grid.addObstacle(Obstacle(Position(200.0, 200.0), 50.0, ObstacleType.ASTEROID))
            assertTrue(grid.isBlocked(Position(200.0, 200.0)))
            assertFalse(grid.isBlocked(Position(300.0, 300.0)))
        }
    }

    // ===== Unit Stats =====

    @Nested
    inner class UnitStats {

        @Test
        fun `getAttackRange uses weapon system max range plus sensor bonus`() {
            val unit = TacticalTestFixtures.createUnit(0, shipClass = TacticalShipClass.BATTLESHIP)
            val energy = EnergyAllocation.BALANCED
            val sensorBonus = energy.sensorRangeBonus()
            // Battleship 최대 무기 사거리: NEUTRON_BEAM 350 + sensor bonus
            val expected = 350.0 + sensorBonus
            assertEquals(expected, unit.getAttackRange(energy))
        }

        @Test
        fun `aggressive energy increases damage multipliers`() {
            val balanced = EnergyAllocation.BALANCED
            val aggressive = EnergyAllocation.AGGRESSIVE
            assertTrue(aggressive.beamDamageMultiplier() > balanced.beamDamageMultiplier())
            assertTrue(aggressive.gunDamageMultiplier() > balanced.gunDamageMultiplier())
        }

        @Test
        fun `takeDamage does not go below zero`() {
            val unit = TacticalTestFixtures.createUnit(0, hp = 10)
            unit.takeDamage(100)
            assertEquals(0, unit.hp)
            assertFalse(unit.isAlive())
        }
    }
}
