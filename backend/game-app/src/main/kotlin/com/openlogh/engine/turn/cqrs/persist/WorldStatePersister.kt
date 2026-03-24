package com.openlogh.engine.turn.cqrs.persist

import com.openlogh.engine.turn.cqrs.memory.DirtyTracker
import com.openlogh.engine.turn.cqrs.memory.InMemoryWorldState
import com.openlogh.entity.Faction
import com.openlogh.entity.Officer
import com.openlogh.entity.Planet
import com.openlogh.entity.SessionState
import com.openlogh.repository.FactionRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.PlanetRepository
import com.openlogh.repository.SessionStateRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Persists dirty entities from in-memory world state back to DB.
 * Only entities marked in the DirtyTracker are saved, minimizing DB writes.
 */
@Service
class WorldStatePersister(
    private val officerRepository: OfficerRepository,
    private val planetRepository: PlanetRepository,
    private val factionRepository: FactionRepository,
    private val sessionStateRepository: SessionStateRepository,
) {
    companion object {
        private val log = LoggerFactory.getLogger(WorldStatePersister::class.java)
    }

    fun persist(state: InMemoryWorldState, tracker: DirtyTracker) {
        val dirty = tracker.getDirtyEntities()
        if (dirty.isEmpty()) return

        val officers = dirty.filterIsInstance<Officer>()
        val planets = dirty.filterIsInstance<Planet>()
        val factions = dirty.filterIsInstance<Faction>()
        val sessions = dirty.filterIsInstance<SessionState>()

        if (officers.isNotEmpty()) {
            officerRepository.saveAll(officers)
        }
        if (planets.isNotEmpty()) {
            planetRepository.saveAll(planets)
        }
        if (factions.isNotEmpty()) {
            factionRepository.saveAll(factions)
        }
        for (session in sessions) {
            sessionStateRepository.save(session)
        }

        log.debug(
            "Persisted world {}: {} officers, {} planets, {} factions",
            state.worldId, officers.size, planets.size, factions.size,
        )

        tracker.clear()
    }
}
