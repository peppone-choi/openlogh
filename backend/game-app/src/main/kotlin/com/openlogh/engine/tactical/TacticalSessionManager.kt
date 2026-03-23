package com.openlogh.engine.tactical

import com.openlogh.entity.Fleet
import com.openlogh.entity.Officer
import com.openlogh.repository.FleetRepository
import com.openlogh.repository.OfficerRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

@Service
class TacticalSessionManager(
    private val officerRepository: OfficerRepository,
    private val fleetRepository: FleetRepository,
) {
    companion object {
        private val log = LoggerFactory.getLogger(TacticalSessionManager::class.java)
        private const val FIELD_SIZE = 1000.0
        private const val OBSTACLE_MIN = 5
        private const val OBSTACLE_MAX = 15
        private const val OBSTACLE_RADIUS_MIN = 20.0
        private const val OBSTACLE_RADIUS_MAX = 60.0
        /** 배치 금지 구역: 양 끝 200 유닛 안쪽에 장애물 배치 안 함 */
        private const val OBSTACLE_MARGIN = 200.0
        /** 공격측 배치 Y 범위 */
        private const val ATTACKER_Y_MIN = 50.0
        private const val ATTACKER_Y_MAX = 200.0
        /** 방어측 배치 Y 범위 */
        private const val DEFENDER_Y_MIN = 800.0
        private const val DEFENDER_Y_MAX = 950.0
        /** 유닛 간 최소 간격 */
        private const val UNIT_SPACING = 30.0
    }

    private val activeSessions = ConcurrentHashMap<String, TacticalGameSession>()

    fun createSession(
        gameSessionId: Long,
        attackerOfficerIds: List<Long>,
        defenderOfficerIds: List<Long>,
        planetId: Long,
    ): String {
        val rng = Random(System.currentTimeMillis())
        val unitIdCounter = UnitIdCounter()

        val attackerFleets = buildFleets(attackerOfficerIds, unitIdCounter)
        val defenderFleets = buildFleets(defenderOfficerIds, unitIdCounter)

        if (attackerFleets.isEmpty() || defenderFleets.isEmpty()) {
            log.warn("Cannot create tactical session: empty fleets. attackers={}, defenders={}",
                attackerOfficerIds, defenderOfficerIds)
            throw IllegalArgumentException("Both sides must have at least one fleet")
        }

        val grid = TacticalGrid(FIELD_SIZE)
        placeRandomObstacles(grid, rng)
        placeUnitsOnGrid(attackerFleets, grid, ATTACKER_Y_MIN, ATTACKER_Y_MAX, rng)
        placeUnitsOnGrid(defenderFleets, grid, DEFENDER_Y_MIN, DEFENDER_Y_MAX, rng)

        val battleSession = TacticalBattleSession(
            sessionId = gameSessionId,
            planetId = planetId,
            attackerFleets = attackerFleets.toMutableList(),
            defenderFleets = defenderFleets.toMutableList(),
            grid = grid,
        )

        val allOfficerIds = (attackerOfficerIds + defenderOfficerIds).toSet()
        val attackerFactionId = attackerFleets.first().factionId
        val defenderFactionId = defenderFleets.first().factionId

        val gameSession = TacticalGameSession(
            battleSession = battleSession,
            allOfficerIds = allOfficerIds,
            attackerFactionId = attackerFactionId,
            defenderFactionId = defenderFactionId,
        )

        activeSessions[gameSession.sessionCode] = gameSession
        log.info("Tactical session created: code={}, planet={}, attackers={}, defenders={}",
            gameSession.sessionCode, planetId, attackerOfficerIds, defenderOfficerIds)
        return gameSession.sessionCode
    }

    fun joinSession(sessionCode: String, officerId: Long): TacticalGameSession? {
        val session = activeSessions[sessionCode] ?: return null
        if (officerId !in session.allOfficerIds) {
            log.warn("Officer {} attempted to join session {} but is not a participant", officerId, sessionCode)
            return null
        }
        session.joinedOfficers.add(officerId)
        log.debug("Officer {} joined tactical session {}", officerId, sessionCode)
        return session
    }

    fun submitOrder(sessionCode: String, order: TacticalOrder) {
        val session = activeSessions[sessionCode] ?: return
        session.battleSession.pendingOrders
            .getOrPut(order.officerId) { mutableListOf() }
            .add(order)
    }

    fun submitSetup(sessionCode: String, officerId: Long, formation: Formation?, energy: EnergyAllocation?) {
        val session = activeSessions[sessionCode] ?: return
        val fleet = session.findFleetByOfficer(officerId) ?: return

        if (formation != null) fleet.formation = formation
        if (energy != null) fleet.energy = energy

        log.debug("Setup applied: session={}, officer={}, formation={}, energy={}",
            sessionCode, officerId, formation?.code, energy)
    }

    fun markReady(sessionCode: String, officerId: Long) {
        val session = activeSessions[sessionCode] ?: return
        session.readyOfficers.add(officerId)
        log.debug("Officer {} marked ready in session {}", officerId, sessionCode)
    }

    fun isAllReady(sessionCode: String): Boolean =
        activeSessions[sessionCode]?.isAllReady() ?: false

    fun getSession(sessionCode: String): TacticalGameSession? = activeSessions[sessionCode]

    fun destroySession(sessionCode: String) {
        activeSessions.remove(sessionCode)
        log.info("Tactical session destroyed: code={}", sessionCode)
    }

    fun getActiveSessionCount(): Int = activeSessions.size

    // ===== Internal helpers =====

    private class UnitIdCounter {
        private var next = 0
        fun nextId(): Int = next++
    }

    private fun buildFleets(officerIds: List<Long>, unitIdCounter: UnitIdCounter): List<TacticalFleet> =
        officerIds.mapNotNull { officerId ->
            val officer = officerRepository.findById(officerId).orElse(null)
            if (officer == null) {
                log.warn("Officer {} not found, skipping fleet creation", officerId)
                return@mapNotNull null
            }

            val fleet = if (officer.fleetId > 0) {
                fleetRepository.findById(officer.fleetId).orElse(null)
            } else null

            val stats = officer.toOfficerStats()
            val units = buildUnits(officer, fleet, unitIdCounter)

            if (units.isEmpty()) {
                log.warn("No units for officer {} (ships={}), skipping", officer.name, officer.ships)
                return@mapNotNull null
            }

            val baseMorale = fleet?.morale?.toInt() ?: officer.morale.toInt()

            // 승무원 등급/서브타입 배율 적용
            val crewGrade = com.openlogh.engine.fleet.CrewGrade.fromCode(fleet?.crewGrade ?: "normal")
            val shipGen = com.openlogh.engine.fleet.ShipSubtype.forGeneration(fleet?.shipGeneration?.toInt() ?: 1)
            // 유닛 HP에 승무원+세대 배율 적용
            for (unit in units) {
                val adjusted = (unit.maxHp * crewGrade.combatMultiplier * shipGen.defenseModifier).toInt()
                unit.hp = adjusted
            }

            TacticalFleet(
                fleetId = fleet?.id ?: 0L,
                officerId = officer.id,
                factionId = officer.factionId,
                officer = stats,
                units = units.toMutableList(),
                formation = if (fleet != null) Formation.fromCode(fleet.formation) else Formation.SPINDLE,
                energy = fleet?.toEnergyAllocation() ?: EnergyAllocation.BALANCED,
                morale = baseMorale,
            )
        }

    private fun buildUnits(officer: Officer, fleet: Fleet?, unitIdCounter: UnitIdCounter): List<TacticalUnit> {
        val units = mutableListOf<TacticalUnit>()

        if (fleet != null) {
            if (fleet.battleships > 0) {
                units.add(createUnit(unitIdCounter.nextId(), fleet.id, officer, TacticalShipClass.BATTLESHIP, fleet.battleships, isFlagship = true))
            }
            if (fleet.fastBattleships > 0) {
                units.add(createUnit(unitIdCounter.nextId(), fleet.id, officer, TacticalShipClass.FAST_BATTLESHIP, fleet.fastBattleships))
            }
            if (fleet.cruisers > 0) {
                units.add(createUnit(unitIdCounter.nextId(), fleet.id, officer, TacticalShipClass.CRUISER, fleet.cruisers))
            }
            if (fleet.strikeCruisers > 0) {
                units.add(createUnit(unitIdCounter.nextId(), fleet.id, officer, TacticalShipClass.STRIKE_CRUISER, fleet.strikeCruisers))
            }
            if (fleet.destroyers > 0) {
                units.add(createUnit(unitIdCounter.nextId(), fleet.id, officer, TacticalShipClass.DESTROYER, fleet.destroyers))
            }
            if (fleet.carriers > 0) {
                units.add(createUnit(unitIdCounter.nextId(), fleet.id, officer, TacticalShipClass.CARRIER, fleet.carriers))
            }
            if (fleet.torpedoCarriers > 0) {
                units.add(createUnit(unitIdCounter.nextId(), fleet.id, officer, TacticalShipClass.TORPEDO_CARRIER, fleet.torpedoCarriers))
            }
            if (fleet.assaultShips > 0) {
                units.add(createUnit(unitIdCounter.nextId(), fleet.id, officer, TacticalShipClass.ASSAULT_SHIP, fleet.assaultShips))
            }
            if (fleet.transports > 0) {
                units.add(createUnit(unitIdCounter.nextId(), fleet.id, officer, TacticalShipClass.TRANSPORT, fleet.transports))
            }
            if (fleet.engineeringShips > 0) {
                units.add(createUnit(unitIdCounter.nextId(), fleet.id, officer, TacticalShipClass.ENGINEERING, fleet.engineeringShips))
            }
            if (fleet.hospitalShips > 0) {
                units.add(createUnit(unitIdCounter.nextId(), fleet.id, officer, TacticalShipClass.HOSPITAL, fleet.hospitalShips))
            }
        } else if (officer.ships > 0) {
            units.add(createUnit(unitIdCounter.nextId(), 0L, officer, TacticalShipClass.BATTLESHIP, officer.ships, isFlagship = true))
        }

        if (units.isNotEmpty() && units.none { it.isFlagship }) {
            units[0] = units[0].copy(isFlagship = true)
        }

        return units
    }

    private fun createUnit(
        id: Int,
        fleetId: Long,
        officer: Officer,
        shipClass: TacticalShipClass,
        hp: Int,
        isFlagship: Boolean = false,
    ) = TacticalUnit(
        id = id,
        fleetId = fleetId,
        factionId = officer.factionId,
        officerId = officer.id,
        shipClass = shipClass,
        hp = hp,
        isFlagship = isFlagship,
    )

    private fun placeRandomObstacles(grid: TacticalGrid, rng: Random) {
        val count = rng.nextInt(OBSTACLE_MIN, OBSTACLE_MAX + 1)
        var placed = 0
        var attempts = 0
        while (placed < count && attempts < count * 10) {
            val cx = rng.nextDouble() * grid.fieldSize
            val cy = OBSTACLE_MARGIN + rng.nextDouble() * (grid.fieldSize - 2 * OBSTACLE_MARGIN)
            val radius = OBSTACLE_RADIUS_MIN + rng.nextDouble() * (OBSTACLE_RADIUS_MAX - OBSTACLE_RADIUS_MIN)
            val type = if (rng.nextDouble() < 0.3) ObstacleType.NEBULA else ObstacleType.ASTEROID
            val candidate = Obstacle(Position(cx, cy), radius, type)

            // 기존 장애물과 겹치지 않는지 확인
            val overlaps = grid.obstacles.any { existing ->
                existing.center.distanceTo(candidate.center) < existing.radius + candidate.radius + 10.0
            }
            if (!overlaps) {
                grid.addObstacle(candidate)
                placed++
            }
            attempts++
        }
    }

    private fun placeUnitsOnGrid(
        fleets: List<TacticalFleet>,
        grid: TacticalGrid,
        minY: Double,
        maxY: Double,
        rng: Random,
    ) {
        val midY = (minY + maxY) / 2.0
        var nextX = 100.0
        for (fleet in fleets) {
            for (unit in fleet.units) {
                var placed = false
                var attempts = 0
                while (!placed && attempts < 100) {
                    val x = if (attempts < 10) nextX else rng.nextDouble() * grid.fieldSize
                    val y = if (attempts < 10) midY else minY + rng.nextDouble() * (maxY - minY)
                    // 함대 진형 3D 배치: 유닛마다 약간의 z 오프셋
                    val z = (rng.nextDouble() - 0.5) * 100.0 // -50 ~ +50
                    val pos = Position(x, y, z)
                    if (grid.inBounds(pos) && !grid.isBlocked(pos)) {
                        unit.x = x
                        unit.y = y
                        unit.z = z
                        grid.placeUnit(unit.id, pos)
                        placed = true
                        nextX = x + UNIT_SPACING
                    }
                    attempts++
                }
            }
        }
    }
}

// ===== Extension functions for entity → engine conversion =====

private fun Officer.toOfficerStats() = OfficerStats(
    officerId = id,
    name = name,
    leadership = leadership.toInt(),
    command = command.toInt(),
    intelligence = intelligence.toInt(),
    mobility = mobility.toInt(),
    attack = attack.toInt(),
    defense = defense.toInt(),
    fighterSkill = fighterSkill.toInt(),
    groundCombat = groundCombat.toInt(),
)

/** Fleet 에너지 → EnergyAllocation (warp 에너지는 engine에 합산) */
private fun Fleet.toEnergyAllocation(): EnergyAllocation {
    val b = energyBeam.toInt()
    val g = energyGun.toInt()
    val s = energyShield.toInt()
    val e = energyEngine.toInt() + energyWarp.toInt()
    val se = energySensor.toInt()
    val total = b + g + s + e + se
    // Normalize to 100 if needed
    return if (total == 100) {
        EnergyAllocation(beam = b, gun = g, shield = s, engine = e, sensor = se)
    } else {
        EnergyAllocation.BALANCED
    }
}
