package com.openlogh.engine.turn.cqrs.memory

import com.openlogh.entity.Faction
import com.openlogh.entity.Officer
import com.openlogh.entity.Planet

/**
 * In-memory snapshot of world state for CQRS turn processing.
 *
 * All mutations happen in-memory first, then DirtyTracker records
 * which entities were modified for batch persistence.
 */
data class InMemoryWorldState(
    val worldId: Long,
    val officers: MutableList<Officer> = mutableListOf(),
    val planets: MutableList<Planet> = mutableListOf(),
    val factions: MutableList<Faction> = mutableListOf(),
) {
    /** Index: officer by id */
    val officerById: Map<Long, Officer> get() = officers.associateBy { it.id }

    /** Index: officers by faction */
    val officersByFaction: Map<Long, List<Officer>>
        get() = officers.filter { it.factionId != 0L }.groupBy { it.factionId }

    /** Index: planet by id */
    val planetById: Map<Long, Planet> get() = planets.associateBy { it.id }

    /** Index: faction by id */
    val factionById: Map<Long, Faction> get() = factions.associateBy { it.id }
}
