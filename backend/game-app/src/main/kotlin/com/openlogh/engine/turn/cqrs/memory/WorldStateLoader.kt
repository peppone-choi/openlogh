package com.openlogh.engine.turn.cqrs.memory

import com.openlogh.repository.FactionRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.PlanetRepository
import org.springframework.stereotype.Service

/**
 * Loads world state from DB into in-memory snapshot for CQRS processing.
 */
@Service
class WorldStateLoader(
    private val officerRepository: OfficerRepository,
    private val planetRepository: PlanetRepository,
    private val factionRepository: FactionRepository,
) {
    fun loadWorldState(worldId: Long): InMemoryWorldState {
        return InMemoryWorldState(
            worldId = worldId,
            officers = officerRepository.findBySessionId(worldId).toMutableList(),
            planets = planetRepository.findBySessionId(worldId).toMutableList(),
            factions = factionRepository.findBySessionId(worldId).toMutableList(),
        )
    }
}
