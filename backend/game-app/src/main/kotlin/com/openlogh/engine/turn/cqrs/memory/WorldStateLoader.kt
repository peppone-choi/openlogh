package com.openlogh.engine.turn.cqrs.memory

import com.openlogh.engine.turn.cqrs.persist.toSnapshot
import com.openlogh.repository.CityRepository
import com.openlogh.repository.DiplomacyRepository
import com.openlogh.repository.GeneralRepository
import com.openlogh.repository.GeneralTurnRepository
import com.openlogh.repository.NationRepository
import com.openlogh.repository.NationTurnRepository
import com.openlogh.repository.TroopRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class WorldStateLoader(
    private val officerRepository: GeneralRepository,
    private val planetRepository: CityRepository,
    private val factionRepository: NationRepository,
    private val fleetRepository: TroopRepository,
    private val diplomacyRepository: DiplomacyRepository,
    private val officerTurnRepository: GeneralTurnRepository,
    private val factionTurnRepository: NationTurnRepository,
) {
    @Transactional(readOnly = true)
    fun loadWorldState(sessionId: Long): InMemoryWorldState {
        val state = InMemoryWorldState(sessionId = sessionId)

        officerRepository.findByWorldId(sessionId).forEach { officer ->
            state.officers[officer.id] = officer.toSnapshot()
        }

        planetRepository.findByWorldId(sessionId).forEach { planet ->
            state.planets[planet.id] = planet.toSnapshot()
        }

        factionRepository.findByWorldId(sessionId).forEach { faction ->
            state.factions[faction.id] = faction.toSnapshot()
        }

        fleetRepository.findByWorldId(sessionId).forEach { fleet ->
            state.fleets[fleet.id] = fleet.toSnapshot()
        }

        diplomacyRepository.findByWorldId(sessionId).forEach { diplomacy ->
            state.diplomacies[diplomacy.id] = diplomacy.toSnapshot()
        }

        officerTurnRepository.findByWorldId(sessionId)
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

        factionTurnRepository.findByWorldId(sessionId)
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
