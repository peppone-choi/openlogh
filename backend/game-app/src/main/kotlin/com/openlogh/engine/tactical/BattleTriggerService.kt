package com.openlogh.engine.tactical

import com.openlogh.entity.Fleet
import com.openlogh.entity.TacticalBattle
import com.openlogh.model.EnergyAllocation
import com.openlogh.model.Formation
import com.openlogh.repository.FleetRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.StarSystemRepository
import com.openlogh.repository.TacticalBattleRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Detects when opposing fleets occupy the same star system and triggers
 * tactical battle instances.
 */
@Service
class BattleTriggerService(
    private val fleetRepository: FleetRepository,
    private val officerRepository: OfficerRepository,
    private val starSystemRepository: StarSystemRepository,
    private val tacticalBattleRepository: TacticalBattleRepository,
) {
    private val log = LoggerFactory.getLogger(BattleTriggerService::class.java)

    /**
     * Check for new battles in a session. Called each tick.
     * Returns list of newly created battles.
     */
    @Transactional
    fun checkForBattles(sessionId: Long): List<TacticalBattle> {
        val newBattles = mutableListOf<TacticalBattle>()

        // Get all fleets grouped by planet (star system)
        val allFleets = fleetRepository.findBySessionId(sessionId)
        val fleetsByPlanet = allFleets.filter { it.planetId != null }.groupBy { it.planetId!! }

        for ((planetId, fleets) in fleetsByPlanet) {
            if (fleets.size < 2) continue

            // Group by faction
            val factionGroups = fleets.groupBy { it.factionId }
            if (factionGroups.size < 2) continue

            // Check if there's already an active battle at this location
            val existingBattles = tacticalBattleRepository.findBySessionIdAndStarSystemIdAndPhaseNot(
                sessionId, planetId, "ENDED"
            )
            if (existingBattles.isNotEmpty()) continue

            // Create battle between first two opposing factions found
            val factionIds = factionGroups.keys.toList()
            val attackerFactionId = factionIds[0]
            val defenderFactionId = factionIds[1]

            val attackerFleets = factionGroups[attackerFactionId] ?: continue
            val defenderFleets = factionGroups[defenderFactionId] ?: continue

            val battle = createBattle(sessionId, planetId, attackerFactionId, defenderFactionId, attackerFleets, defenderFleets)
            if (battle != null) {
                newBattles.add(battle)
            }
        }

        return newBattles
    }

    /**
     * Create a new tactical battle instance.
     */
    @Transactional
    fun createBattle(
        sessionId: Long,
        starSystemId: Long,
        attackerFactionId: Long,
        defenderFactionId: Long,
        attackerFleets: List<Fleet>,
        defenderFleets: List<Fleet>,
    ): TacticalBattle? {
        if (attackerFleets.isEmpty() || defenderFleets.isEmpty()) return null

        log.info("Creating tactical battle at star system {} between factions {} and {}", starSystemId, attackerFactionId, defenderFactionId)

        val battle = TacticalBattle(
            sessionId = sessionId,
            starSystemId = starSystemId,
            attackerFactionId = attackerFactionId,
            defenderFactionId = defenderFactionId,
            phase = "PREPARING",
            participants = mutableMapOf(
                "attackers" to attackerFleets.map { it.id },
                "defenders" to defenderFleets.map { it.id },
            ),
        )

        return tacticalBattleRepository.save(battle)
    }

    /**
     * Build initial tactical battle state from fleet data.
     */
    fun buildInitialState(battle: TacticalBattle): TacticalBattleState {
        val units = mutableListOf<TacticalUnit>()

        @Suppress("UNCHECKED_CAST")
        val attackerFleetIds = (battle.participants["attackers"] as? List<*>)?.mapNotNull {
            (it as? Number)?.toLong()
        } ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val defenderFleetIds = (battle.participants["defenders"] as? List<*>)?.mapNotNull {
            (it as? Number)?.toLong()
        } ?: emptyList()

        // Build attacker units
        for ((index, fleetId) in attackerFleetIds.withIndex()) {
            val fleet = fleetRepository.findById(fleetId).orElse(null) ?: continue
            val officer = officerRepository.findById(fleet.leaderOfficerId).orElse(null) ?: continue

            units.add(TacticalUnit(
                fleetId = fleet.id,
                officerId = officer.id,
                officerName = officer.name,
                factionId = fleet.factionId,
                side = BattleSide.ATTACKER,
                posX = 100.0,
                posY = 100.0 + index * 80.0,
                hp = officer.ships,
                maxHp = officer.ships,
                ships = officer.ships,
                maxShips = officer.ships,
                training = officer.training.toInt(),
                morale = officer.morale.toInt(),
                leadership = officer.leadership.toInt(),
                command = officer.command.toInt(),
                intelligence = officer.intelligence.toInt(),
                mobility = officer.mobility.toInt(),
                attack = officer.attack.toInt(),
                defense = officer.defense.toInt(),
                unitType = fleet.unitType,
                shipClass = officer.shipClass.toInt(),
            ))
        }

        // Build defender units
        for ((index, fleetId) in defenderFleetIds.withIndex()) {
            val fleet = fleetRepository.findById(fleetId).orElse(null) ?: continue
            val officer = officerRepository.findById(fleet.leaderOfficerId).orElse(null) ?: continue

            units.add(TacticalUnit(
                fleetId = fleet.id,
                officerId = officer.id,
                officerName = officer.name,
                factionId = fleet.factionId,
                side = BattleSide.DEFENDER,
                posX = 900.0,
                posY = 100.0 + index * 80.0,
                hp = officer.ships,
                maxHp = officer.ships,
                ships = officer.ships,
                maxShips = officer.ships,
                training = officer.training.toInt(),
                morale = officer.morale.toInt(),
                leadership = officer.leadership.toInt(),
                command = officer.command.toInt(),
                intelligence = officer.intelligence.toInt(),
                mobility = officer.mobility.toInt(),
                attack = officer.attack.toInt(),
                defense = officer.defense.toInt(),
                unitType = fleet.unitType,
                shipClass = officer.shipClass.toInt(),
            ))
        }

        // Check for fortress at this star system
        val starSystem = starSystemRepository.findById(battle.starSystemId).orElse(null)
        val fortressGunPower = starSystem?.fortressGunPower ?: 0
        val fortressGunRange = starSystem?.fortressGunRange ?: 0
        val fortressGunCooldown = starSystem?.fortressGunCooldown ?: 0
        val fortressFactionId = starSystem?.factionId ?: 0

        return TacticalBattleState(
            battleId = battle.id,
            starSystemId = battle.starSystemId,
            units = units,
            fortressGunPower = fortressGunPower,
            fortressGunRange = fortressGunRange,
            fortressGunCooldown = fortressGunCooldown,
            fortressFactionId = fortressFactionId,
        )
    }
}
