package com.openlogh.engine

import com.openlogh.entity.Faction
import com.openlogh.entity.Officer
import com.openlogh.repository.FactionRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.PlanetRepository
import com.openlogh.repository.SessionStateRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Feature 9.8.5 - Coup Execution
 *
 * When a rebellion succeeds:
 *   - All planets in the star system switch to rebel faction
 *   - Officers in system must choose: join rebels or flee
 *   - Loyal officers teleported to nearest friendly planet
 *   - Coup leader becomes faction leader of rebel faction
 */
@Service
class CoupExecutionService(
    private val officerRepository: OfficerRepository,
    private val planetRepository: PlanetRepository,
    private val factionRepository: FactionRepository,
    private val sessionStateRepository: SessionStateRepository,
) {
    companion object {
        private val log = LoggerFactory.getLogger(CoupExecutionService::class.java)
        private const val REBEL_FACTION_TYPE = "rebel"
    }

    /**
     * Execute a successful coup.
     * @param sessionId game session
     * @param coupLeaderOfficerId the officer who led the coup
     * @param targetFactionId the faction being overthrown
     * @param rebelFactionId the rebel faction receiving the planets (created beforehand)
     * @param stationedSystem the star system where the coup occurred
     */
    @Transactional
    fun executeCoup(
        sessionId: Long,
        coupLeaderOfficerId: Long,
        targetFactionId: Long,
        rebelFactionId: Long,
        stationedSystem: Int,
    ) {
        val coupLeader = officerRepository.findById(coupLeaderOfficerId).orElse(null) ?: run {
            log.error("Coup leader {} not found", coupLeaderOfficerId)
            return
        }

        val rebelFaction = factionRepository.findById(rebelFactionId).orElse(null) ?: run {
            log.error("Rebel faction {} not found", rebelFactionId)
            return
        }

        // 1. Switch all planets in the star system to rebel faction
        val systemPlanets = planetRepository.findBySessionId(sessionId)
            .filter { it.stationedSystem() == stationedSystem }

        for (planet in systemPlanets) {
            val prevFactionId = planet.factionId
            planet.factionId = rebelFactionId
            planetRepository.save(planet)
            log.info("Planet {} switched from faction {} to rebel faction {}", planet.id, prevFactionId, rebelFactionId)
        }

        // 2. Set coup leader as faction leader of the rebel faction
        rebelFaction.supremeCommanderId = coupLeaderOfficerId
        coupLeader.factionId = rebelFactionId
        coupLeader.meta["isCoupLeader"] = true
        officerRepository.save(coupLeader)
        factionRepository.save(rebelFaction)
        log.info("Coup leader {} set as supreme commander of rebel faction {}", coupLeaderOfficerId, rebelFactionId)

        // 3. Process officers in the star system
        val officersInSystem = officerRepository.findBySessionId(sessionId)
            .filter { it.stationedSystem == stationedSystem }

        for (officer in officersInSystem) {
            if (officer.id == coupLeaderOfficerId) continue
            processOfficerChoice(sessionId, officer, targetFactionId, rebelFactionId, systemPlanets.firstOrNull()?.id)
        }

        // 4. Record coup in world config
        val world = sessionStateRepository.findById(sessionId.toShort()).orElse(null)
        if (world != null) {
            world.config["hasCoup"] = true
            world.config["lastCoupLeader"] = coupLeaderOfficerId
            world.config["lastCoupFaction"] = rebelFactionId
            sessionStateRepository.save(world)
        }

        log.info(
            "Coup executed: leader={} targetFaction={} rebelFaction={} system={} planetsConverted={}",
            coupLeaderOfficerId, targetFactionId, rebelFactionId, stationedSystem, systemPlanets.size,
        )
    }

    private fun processOfficerChoice(
        sessionId: Long,
        officer: Officer,
        targetFactionId: Long,
        rebelFactionId: Long,
        rebelPlanetId: Long?,
    ) {
        val loyalToTarget = officer.factionId == targetFactionId

        // NPC officers: loyal officers flee, others may join rebels
        if (officer.userId == null) {
            if (loyalToTarget) {
                teleportToFriendlyPlanet(sessionId, officer, targetFactionId)
            } else {
                // Join rebels
                officer.factionId = rebelFactionId
                if (rebelPlanetId != null) officer.planetId = rebelPlanetId
                officerRepository.save(officer)
            }
            return
        }

        // Player officers: mark as "pending coup choice"; they decide on next login
        officer.meta["pendingCoupChoice"] = true
        officer.meta["coupRebelFactionId"] = rebelFactionId
        officer.meta["coupTargetFactionId"] = targetFactionId
        officerRepository.save(officer)
        log.info("Officer {} flagged for pending coup choice", officer.id)
    }

    private fun teleportToFriendlyPlanet(sessionId: Long, officer: Officer, factionId: Long) {
        val friendlyPlanets = planetRepository.findBySessionId(sessionId)
            .filter { it.factionId == factionId }

        val destination = friendlyPlanets.minByOrNull { planet ->
            // Prefer planets in a different system from the coup
            val dist = Math.abs(planet.id - officer.planetId)
            dist
        }

        if (destination != null) {
            officer.planetId = destination.id
            officer.locationState = "planet"
            officer.meta["teleportedByCoup"] = true
        }
        officerRepository.save(officer)
        log.info("Loyal officer {} teleported to planet {}", officer.id, destination?.id)
    }

    // Extension to get stationed system from planet (uses region as proxy for star system)
    private fun Planet.stationedSystem(): Int = this.region.toInt()
}

// Import for Planet used in extension function
private typealias Planet = com.openlogh.entity.Planet
