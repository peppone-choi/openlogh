package com.openlogh.service

import com.openlogh.repository.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SessionRestartService(
    private val scenarioService: ScenarioService,
    private val sessionStateRepository: SessionStateRepository,
    private val officerRepository: OfficerRepository,
    private val fleetRepository: FleetRepository,
    private val planetRepository: PlanetRepository,
    private val factionRepository: FactionRepository,
    private val diplomacyRepository: DiplomacyRepository,
    private val selectPoolRepository: SelectPoolRepository,
) {
    companion object {
        private val log = LoggerFactory.getLogger(SessionRestartService::class.java)
    }

    @Transactional
    fun restartSession(sessionId: Long) {
        val world = sessionStateRepository.findById(sessionId.toShort()).orElse(null) ?: run {
            log.warn("Session {} not found for restart", sessionId)
            return
        }

        // Delete all game entities for this session
        val officers = officerRepository.findBySessionId(sessionId)
        officerRepository.deleteAll(officers)

        val fleets = fleetRepository.findBySessionId(sessionId)
        fleetRepository.deleteAll(fleets)

        val planets = planetRepository.findBySessionId(sessionId)
        planetRepository.deleteAll(planets)

        val factions = factionRepository.findBySessionId(sessionId)
        factionRepository.deleteAll(factions)

        val diplomacies = diplomacyRepository.findBySessionId(sessionId)
        diplomacyRepository.deleteAll(diplomacies)

        selectPoolRepository.deleteBySessionId(sessionId)

        // Reset session state to initial values
        world.currentYear = (world.config["startYear"] as? Number)?.toShort() ?: 796
        world.currentMonth = 1
        world.config.remove("hasCoup")
        world.config.remove("isUnited")
        world.config.remove("locked")
        world.meta.remove("openKillTurnReset")
        sessionStateRepository.save(world)

        // Re-initialize scenario
        try {
            scenarioService.initializeWorld(world.scenarioCode, world.tickSeconds)
        } catch (e: Exception) {
            log.error("Failed to re-initialize scenario for session {}: {}", sessionId, e.message)
        }

        log.info("Session {} restarted to initial conditions (scenario={})", sessionId, world.scenarioCode)
    }
}
