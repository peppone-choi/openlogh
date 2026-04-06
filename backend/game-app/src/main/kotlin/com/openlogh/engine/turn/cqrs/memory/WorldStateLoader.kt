package com.openlogh.engine.turn.cqrs.memory

import com.openlogh.engine.turn.cqrs.persist.toSnapshot
import com.openlogh.repository.PlanetRepository
import com.openlogh.repository.DiplomacyRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.OfficerTurnRepository
import com.openlogh.repository.FactionRepository
import com.openlogh.repository.FactionTurnRepository
import com.openlogh.repository.FleetRepository
import com.openlogh.repository.UnitCrewRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class WorldStateLoader(
    private val officerRepository: OfficerRepository,
    private val planetRepository: PlanetRepository,
    private val factionRepository: FactionRepository,
    private val fleetRepository: FleetRepository,
    private val unitCrewRepository: UnitCrewRepository,
    private val diplomacyRepository: DiplomacyRepository,
    private val officerTurnRepository: OfficerTurnRepository,
    private val factionTurnRepository: FactionTurnRepository,
) {
    @Transactional(readOnly = true)
    fun loadWorldState(sessionId: Long): InMemoryWorldState {
        val state = InMemoryWorldState(sessionId = sessionId)

        officerRepository.findBySessionId(sessionId).forEach { officer ->
            state.officers[officer.id] = officer.toSnapshot()
        }

        planetRepository.findBySessionId(sessionId).forEach { planet ->
            state.planets[planet.id] = planet.toSnapshot()
        }

        factionRepository.findBySessionId(sessionId).forEach { faction ->
            state.factions[faction.id] = faction.toSnapshot()
        }

        fleetRepository.findBySessionId(sessionId).forEach { fleet ->
            state.fleets[fleet.id] = fleet.toSnapshot()
        }

        unitCrewRepository.findBySessionId(sessionId).forEach { unitCrew ->
            state.unitCrews[unitCrew.id] = unitCrew.toSnapshot()
        }

        diplomacyRepository.findBySessionId(sessionId).forEach { diplomacy ->
            state.diplomacies[diplomacy.id] = diplomacy.toSnapshot()
        }

        officerTurnRepository.findBySessionId(sessionId)
            .groupBy { it.officerId }
            .forEach { (officerId, turns) ->
                state.officerTurnsByOfficerId[officerId] = turns
                    .sortedBy { it.turnIdx }
                    .map { turn ->
                        OfficerTurnSnapshot(
                            id = turn.id,
                            sessionId = turn.sessionId,
                            officerId = turn.officerId,
                            turnIdx = turn.turnIdx,
                            actionCode = turn.actionCode,
                            arg = turn.arg.toMutableMap(),
                            brief = turn.brief,
                            createdAt = turn.createdAt,
                        )
                    }
                    .toMutableList()
            }

        factionTurnRepository.findBySessionId(sessionId)
            .groupBy { FactionTurnKey(it.factionId, it.officerLevel) }
            .forEach { (key, turns) ->
                state.factionTurnsByFactionAndLevel[key] = turns
                    .sortedBy { it.turnIdx }
                    .map { turn ->
                        FactionTurnSnapshot(
                            id = turn.id,
                            sessionId = turn.sessionId,
                            factionId = turn.factionId,
                            officerLevel = turn.officerLevel,
                            turnIdx = turn.turnIdx,
                            actionCode = turn.actionCode,
                            arg = turn.arg.toMutableMap(),
                            brief = turn.brief,
                            createdAt = turn.createdAt,
                        )
                    }
                    .toMutableList()
            }

        return state
    }
}
