package com.openlogh.engine.turn.cqrs.persist

import com.openlogh.engine.turn.cqrs.memory.DirtyTracker
import com.openlogh.engine.turn.cqrs.memory.InMemoryWorldState
import com.openlogh.entity.OfficerTurn
import com.openlogh.entity.FactionTurn
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
class WorldStatePersister(
    private val officerRepository: OfficerRepository,
    private val planetRepository: PlanetRepository,
    private val factionRepository: FactionRepository,
    private val fleetRepository: FleetRepository,
    private val unitCrewRepository: UnitCrewRepository,
    private val diplomacyRepository: DiplomacyRepository,
    private val officerTurnRepository: OfficerTurnRepository,
    private val factionTurnRepository: FactionTurnRepository,
    private val jpaBulkWriter: JpaBulkWriter,
) {
    @Transactional
    fun persist(state: InMemoryWorldState, tracker: DirtyTracker, sessionId: Long) {
        val changes = tracker.consumeAll()

        val officerUpserts = (changes.dirtyOfficerIds + changes.createdOfficerIds)
            .mapNotNull { id ->
                val snapshot = state.officers[id] ?: return@mapNotNull null
                officerRepository.findById(id).orElse(null)?.also { entity ->
                    entity.sessionId = sessionId
                    entity.userId = snapshot.userId
                    entity.name = snapshot.name
                    entity.factionId = snapshot.factionId
                    entity.planetId = snapshot.planetId
                    entity.fleetId = snapshot.fleetId
                    entity.npcState = snapshot.npcState
                    entity.npcOrg = snapshot.npcOrg
                    entity.affinity = snapshot.affinity
                    entity.bornYear = snapshot.bornYear
                    entity.deadYear = snapshot.deadYear
                    entity.picture = snapshot.picture
                    entity.imageServer = snapshot.imageServer
                    entity.leadership = snapshot.leadership
                    entity.leadershipExp = snapshot.leadershipExp
                    entity.command = snapshot.command
                    entity.commandExp = snapshot.commandExp
                    entity.intelligence = snapshot.intelligence
                    entity.intelligenceExp = snapshot.intelligenceExp
                    entity.politics = snapshot.politics
                    entity.politicsExp = snapshot.politicsExp
                    entity.administration = snapshot.administration
                    entity.administrationExp = snapshot.administrationExp
                    entity.mobility = snapshot.mobility
                    entity.mobilityExp = snapshot.mobilityExp
                    entity.attack = snapshot.attack
                    entity.attackExp = snapshot.attackExp
                    entity.defense = snapshot.defense
                    entity.defenseExp = snapshot.defenseExp
                    entity.dex1 = snapshot.dex1
                    entity.dex2 = snapshot.dex2
                    entity.dex3 = snapshot.dex3
                    entity.dex4 = snapshot.dex4
                    entity.dex5 = snapshot.dex5
                    entity.dex6 = snapshot.dex6
                    entity.dex7 = snapshot.dex7
                    entity.dex8 = snapshot.dex8
                    entity.injury = snapshot.injury
                    entity.experience = snapshot.experience
                    entity.dedication = snapshot.dedication
                    entity.officerLevel = snapshot.officerLevel
                    entity.officerPlanet = snapshot.officerPlanet
                    entity.permission = snapshot.permission
                    entity.funds = snapshot.funds
                    entity.supplies = snapshot.supplies
                    entity.ships = snapshot.ships
                    entity.shipClass = snapshot.shipClass
                    entity.training = snapshot.training
                    entity.morale = snapshot.morale
                    entity.flagshipCode = snapshot.flagshipCode
                    entity.equipCode = snapshot.equipCode
                    entity.engineCode = snapshot.engineCode
                    entity.accessoryCode = snapshot.accessoryCode
                    entity.ownerName = snapshot.ownerName
                    entity.newmsg = snapshot.newmsg
                    entity.turnTime = snapshot.turnTime
                    entity.recentWarTime = snapshot.recentWarTime
                    entity.makeLimit = snapshot.makeLimit
                    entity.killTurn = snapshot.killTurn
                    entity.blockState = snapshot.blockState
                    entity.dedLevel = snapshot.dedLevel
                    entity.expLevel = snapshot.expLevel
                    entity.age = snapshot.age
                    entity.startAge = snapshot.startAge
                    entity.belong = snapshot.belong
                    entity.betray = snapshot.betray
                    entity.personalCode = snapshot.personalCode
                    entity.specialCode = snapshot.specialCode
                    entity.specAge = snapshot.specAge
                    entity.special2Code = snapshot.special2Code
                    entity.spec2Age = snapshot.spec2Age
                    entity.defenceTrain = snapshot.defenceTrain
                    entity.tournamentState = snapshot.tournamentState
                    entity.commandPoints = snapshot.commandPoints
                    entity.commandEndTime = snapshot.commandEndTime
                    entity.posX = snapshot.posX
                    entity.posY = snapshot.posY
                    entity.destX = snapshot.destX
                    entity.destY = snapshot.destY
                    entity.lastTurn = snapshot.lastTurn.toMutableMap()
                    entity.meta = snapshot.meta.toMutableMap()
                    entity.penalty = snapshot.penalty.toMutableMap()
                    entity.createdAt = snapshot.createdAt
                    entity.updatedAt = snapshot.updatedAt
                }
            }
        jpaBulkWriter.saveAll(officerRepository, officerUpserts)
        jpaBulkWriter.deleteAllById(officerRepository, changes.deletedOfficerIds)

        val planetUpserts = (changes.dirtyPlanetIds + changes.createdPlanetIds)
            .mapNotNull { id ->
                val snapshot = state.planets[id] ?: return@mapNotNull null
                planetRepository.findById(id).orElse(null)?.also { entity ->
                    entity.sessionId = sessionId
                    entity.name = snapshot.name
                    entity.level = snapshot.level
                    entity.factionId = snapshot.factionId
                    entity.supplyState = snapshot.supplyState
                    entity.frontState = snapshot.frontState
                    entity.population = snapshot.population
                    entity.populationMax = snapshot.populationMax
                    entity.production = snapshot.production
                    entity.productionMax = snapshot.productionMax
                    entity.commerce = snapshot.commerce
                    entity.commerceMax = snapshot.commerceMax
                    entity.security = snapshot.security
                    entity.securityMax = snapshot.securityMax
                    entity.approval = snapshot.approval
                    entity.tradeRoute = snapshot.tradeRoute
                    entity.dead = snapshot.dead
                    entity.orbitalDefense = snapshot.orbitalDefense
                    entity.orbitalDefenseMax = snapshot.orbitalDefenseMax
                    entity.fortress = snapshot.fortress
                    entity.fortressMax = snapshot.fortressMax
                    entity.officerSet = snapshot.officerSet
                    entity.state = snapshot.state
                    entity.region = snapshot.region
                    entity.term = snapshot.term
                    entity.conflict = snapshot.conflict.toMutableMap()
                    entity.meta = snapshot.meta.toMutableMap()
                }
            }
        jpaBulkWriter.saveAll(planetRepository, planetUpserts)
        jpaBulkWriter.deleteAllById(planetRepository, changes.deletedPlanetIds)

        val factionUpserts = (changes.dirtyFactionIds + changes.createdFactionIds)
            .mapNotNull { id ->
                val snapshot = state.factions[id] ?: return@mapNotNull null
                factionRepository.findById(id).orElse(null)?.also { entity ->
                    entity.sessionId = sessionId
                    entity.name = snapshot.name
                    entity.abbreviation = snapshot.abbreviation
                    entity.color = snapshot.color
                    entity.capitalPlanetId = snapshot.capitalPlanetId
                    entity.funds = snapshot.funds
                    entity.supplies = snapshot.supplies
                    entity.taxRate = snapshot.taxRate
                    entity.conscriptionRate = snapshot.conscriptionRate
                    entity.conscriptionRateTmp = snapshot.conscriptionRateTmp
                    entity.secretLimit = snapshot.secretLimit
                    entity.chiefOfficerId = snapshot.chiefOfficerId
                    entity.scoutLevel = snapshot.scoutLevel
                    entity.warState = snapshot.warState
                    entity.strategicCmdLimit = snapshot.strategicCmdLimit
                    entity.surrenderLimit = snapshot.surrenderLimit
                    entity.techLevel = snapshot.techLevel
                    entity.militaryPower = snapshot.militaryPower
                    entity.officerCount = snapshot.officerCount
                    entity.factionRank = snapshot.factionRank
                    entity.factionType = snapshot.factionType
                    entity.spy = snapshot.spy.toMutableMap()
                    entity.meta = snapshot.meta.toMutableMap()
                    entity.createdAt = snapshot.createdAt
                    entity.updatedAt = snapshot.updatedAt
                }
            }
        jpaBulkWriter.saveAll(factionRepository, factionUpserts)
        jpaBulkWriter.deleteAllById(factionRepository, changes.deletedFactionIds)

        val fleetUpserts = (changes.dirtyFleetIds + changes.createdFleetIds)
            .mapNotNull { id ->
                val snapshot = state.fleets[id] ?: return@mapNotNull null
                fleetRepository.findById(id).orElse(null)?.also { entity ->
                    entity.sessionId = sessionId
                    entity.leaderOfficerId = snapshot.leaderOfficerId
                    entity.factionId = snapshot.factionId
                    entity.name = snapshot.name
                    entity.meta = snapshot.meta.toMutableMap()
                    entity.createdAt = snapshot.createdAt
                }
            }
        jpaBulkWriter.saveAll(fleetRepository, fleetUpserts)
        jpaBulkWriter.deleteAllById(fleetRepository, changes.deletedFleetIds)

        val unitCrewUpserts = (changes.dirtyUnitCrewIds + changes.createdUnitCrewIds)
            .mapNotNull { id ->
                val snapshot = state.unitCrews[id] ?: return@mapNotNull null
                unitCrewRepository.findById(id).orElse(null)?.also { entity ->
                    entity.sessionId = sessionId
                    entity.fleetId = snapshot.fleetId
                    entity.officerId = snapshot.officerId
                    entity.slotRole = snapshot.slotRole
                    entity.assignedAt = snapshot.assignedAt
                }
            }
        jpaBulkWriter.saveAll(unitCrewRepository, unitCrewUpserts)
        jpaBulkWriter.deleteAllById(unitCrewRepository, changes.deletedUnitCrewIds)

        val diplomacyUpserts = (changes.dirtyDiplomacyIds + changes.createdDiplomacyIds)
            .mapNotNull { id ->
                val snapshot = state.diplomacies[id] ?: return@mapNotNull null
                diplomacyRepository.findById(id).orElse(null)?.also { entity ->
                    entity.sessionId = sessionId
                    entity.srcFactionId = snapshot.srcFactionId
                    entity.destFactionId = snapshot.destFactionId
                    entity.stateCode = snapshot.stateCode
                    entity.term = snapshot.term
                    entity.isDead = snapshot.isDead
                    entity.isShowing = snapshot.isShowing
                    entity.meta = snapshot.meta.toMutableMap()
                    entity.createdAt = snapshot.createdAt
                }
            }
        jpaBulkWriter.saveAll(diplomacyRepository, diplomacyUpserts)
        jpaBulkWriter.deleteAllById(diplomacyRepository, changes.deletedDiplomacyIds)

        officerTurnRepository.deleteBySessionId(sessionId)
        val officerTurns = state.officerTurnsByOfficerId
            .values
            .flatten()
            .sortedWith(compareBy({ it.officerId }, { it.turnIdx }))
            .map {
                OfficerTurn(
                    id = 0,
                    sessionId = sessionId,
                    officerId = it.officerId,
                    turnIdx = it.turnIdx,
                    actionCode = it.actionCode,
                    arg = it.arg.toMutableMap(),
                    brief = it.brief,
                    createdAt = it.createdAt,
                )
            }
        jpaBulkWriter.saveAll(officerTurnRepository, officerTurns)

        factionTurnRepository.deleteBySessionId(sessionId)
        val factionTurns = state.factionTurnsByFactionAndLevel
            .values
            .flatten()
            .sortedWith(compareBy({ it.factionId }, { it.officerLevel }, { it.turnIdx }))
            .map {
                FactionTurn(
                    id = 0,
                    sessionId = sessionId,
                    factionId = it.factionId,
                    officerLevel = it.officerLevel,
                    turnIdx = it.turnIdx,
                    actionCode = it.actionCode,
                    arg = it.arg.toMutableMap(),
                    brief = it.brief,
                    createdAt = it.createdAt,
                )
            }
        jpaBulkWriter.saveAll(factionTurnRepository, factionTurns)
    }
}
