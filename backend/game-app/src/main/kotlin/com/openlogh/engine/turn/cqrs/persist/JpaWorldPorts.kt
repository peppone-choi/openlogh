package com.openlogh.engine.turn.cqrs.persist

import com.openlogh.engine.turn.cqrs.memory.DiplomacySnapshot
import com.openlogh.engine.turn.cqrs.memory.FactionSnapshot
import com.openlogh.engine.turn.cqrs.memory.FactionTurnSnapshot
import com.openlogh.engine.turn.cqrs.memory.FleetSnapshot
import com.openlogh.engine.turn.cqrs.memory.OfficerSnapshot
import com.openlogh.engine.turn.cqrs.memory.OfficerTurnSnapshot
import com.openlogh.engine.turn.cqrs.memory.PlanetSnapshot
import com.openlogh.engine.turn.cqrs.port.WorldReadPort
import com.openlogh.engine.turn.cqrs.port.WorldWritePort
import com.openlogh.entity.Diplomacy
import com.openlogh.entity.Faction
import com.openlogh.entity.Fleet
import com.openlogh.entity.Officer
import com.openlogh.entity.OfficerTurn
import com.openlogh.entity.FactionTurn
import com.openlogh.entity.Planet
import com.openlogh.repository.CityRepository
import com.openlogh.repository.DiplomacyRepository
import com.openlogh.repository.GeneralRepository
import com.openlogh.repository.GeneralTurnRepository
import com.openlogh.repository.NationRepository
import com.openlogh.repository.NationTurnRepository
import com.openlogh.repository.TroopRepository

class JpaWorldPorts(
    private val sessionId: Long,
    private val officerRepository: GeneralRepository,
    private val planetRepository: CityRepository,
    private val factionRepository: NationRepository,
    private val fleetRepository: TroopRepository,
    private val diplomacyRepository: DiplomacyRepository,
    private val officerTurnRepository: GeneralTurnRepository,
    private val factionTurnRepository: NationTurnRepository,
) : WorldPorts {

    override fun officer(id: Long): OfficerSnapshot? =
        officerRepository.findById(id)
            .orElse(null)
            ?.takeIf { it.sessionId == sessionId }
            ?.let(::toOfficerSnapshot)

    override fun planet(id: Long): PlanetSnapshot? =
        planetRepository.findById(id)
            .orElse(null)
            ?.takeIf { it.sessionId == sessionId }
            ?.let(::toPlanetSnapshot)

    override fun faction(id: Long): FactionSnapshot? =
        factionRepository.findById(id)
            .orElse(null)
            ?.takeIf { it.sessionId == sessionId }
            ?.let(::toFactionSnapshot)

    override fun fleet(id: Long): FleetSnapshot? =
        fleetRepository.findById(id)
            .orElse(null)
            ?.takeIf { it.sessionId == sessionId }
            ?.let(::toFleetSnapshot)

    override fun diplomacy(id: Long): DiplomacySnapshot? =
        diplomacyRepository.findById(id)
            .orElse(null)
            ?.takeIf { it.sessionId == sessionId }
            ?.let(::toDiplomacySnapshot)

    override fun allOfficers(): Collection<OfficerSnapshot> =
        officerRepository.findByWorldId(sessionId).map(::toOfficerSnapshot)

    override fun allPlanets(): Collection<PlanetSnapshot> =
        planetRepository.findByWorldId(sessionId).map(::toPlanetSnapshot)

    override fun allFactions(): Collection<FactionSnapshot> =
        factionRepository.findByWorldId(sessionId).map(::toFactionSnapshot)

    override fun allFleets(): Collection<FleetSnapshot> =
        fleetRepository.findByWorldId(sessionId).map(::toFleetSnapshot)

    override fun allDiplomacies(): Collection<DiplomacySnapshot> =
        diplomacyRepository.findByWorldId(sessionId).map(::toDiplomacySnapshot)

    override fun officersByFaction(factionId: Long): List<OfficerSnapshot> =
        officerRepository.findByWorldIdAndNationId(sessionId, factionId).map(::toOfficerSnapshot)

    override fun officersByPlanet(planetId: Long): List<OfficerSnapshot> =
        officerRepository.findByCityId(planetId)
            .asSequence()
            .filter { it.sessionId == sessionId }
            .map(::toOfficerSnapshot)
            .toList()

    override fun planetsByFaction(factionId: Long): List<PlanetSnapshot> =
        planetRepository.findByNationId(factionId)
            .asSequence()
            .filter { it.sessionId == sessionId }
            .map(::toPlanetSnapshot)
            .toList()

    override fun diplomaciesByFaction(factionId: Long): List<DiplomacySnapshot> =
        diplomacyRepository.findByWorldIdAndSrcNationIdOrDestNationId(sessionId, factionId, factionId)
            .asSequence()
            .filter { it.sessionId == sessionId }
            .map(::toDiplomacySnapshot)
            .toList()

    override fun activeDiplomacies(): List<DiplomacySnapshot> =
        diplomacyRepository.findByWorldIdAndIsDeadFalse(sessionId).map(::toDiplomacySnapshot)

    override fun officerTurns(officerId: Long): List<OfficerTurnSnapshot> =
        officerTurnRepository.findByGeneralIdOrderByTurnIdx(officerId)
            .asSequence()
            .filter { it.sessionId == sessionId }
            .map(::toOfficerTurnSnapshot)
            .toList()

    override fun factionTurns(factionId: Long, officerLevel: Short): List<FactionTurnSnapshot> =
        factionTurnRepository.findByNationIdAndOfficerLevelOrderByTurnIdx(factionId, officerLevel)
            .asSequence()
            .filter { it.sessionId == sessionId }
            .map(::toFactionTurnSnapshot)
            .toList()

    override fun putOfficer(snapshot: OfficerSnapshot) {
        val entity = officerRepository.findById(snapshot.id).orElse(Officer())
        entity.applySnapshot(snapshot)
        officerRepository.save(entity)
    }

    override fun putPlanet(snapshot: PlanetSnapshot) {
        val entity = planetRepository.findById(snapshot.id).orElse(Planet())
        entity.applySnapshot(snapshot)
        planetRepository.save(entity)
    }

    override fun putFaction(snapshot: FactionSnapshot) {
        val entity = factionRepository.findById(snapshot.id).orElse(Faction())
        entity.applySnapshot(snapshot)
        factionRepository.save(entity)
    }

    override fun putFleet(snapshot: FleetSnapshot) {
        val entity = fleetRepository.findById(snapshot.id).orElse(Fleet())
        entity.applySnapshot(snapshot)
        fleetRepository.save(entity)
    }

    override fun putDiplomacy(snapshot: DiplomacySnapshot) {
        val entity = diplomacyRepository.findById(snapshot.id).orElse(Diplomacy())
        entity.applySnapshot(snapshot)
        diplomacyRepository.save(entity)
    }

    override fun deleteOfficer(id: Long) {
        officerRepository.deleteById(id)
    }

    override fun deletePlanet(id: Long) {
        planetRepository.deleteById(id)
    }

    override fun deleteFaction(id: Long) {
        factionRepository.deleteById(id)
    }

    override fun deleteFleet(id: Long) {
        fleetRepository.deleteById(id)
    }

    override fun deleteDiplomacy(id: Long) {
        diplomacyRepository.deleteById(id)
    }

    override fun setOfficerTurns(officerId: Long, turns: List<OfficerTurnSnapshot>) {
        officerTurnRepository.deleteByGeneralId(officerId)
        val entities = turns
            .sortedBy { it.turnIdx }
            .map { it.toEntityWithGeneratedId() }
        officerTurnRepository.saveAll(entities)
    }

    override fun setFactionTurns(factionId: Long, officerLevel: Short, turns: List<FactionTurnSnapshot>) {
        factionTurnRepository.deleteByNationIdAndOfficerLevel(factionId, officerLevel)
        val entities = turns
            .sortedBy { it.turnIdx }
            .map { it.toEntityWithGeneratedId() }
        factionTurnRepository.saveAll(entities)
    }

    override fun removeOfficerTurns(officerId: Long) {
        officerTurnRepository.deleteByGeneralId(officerId)
    }

    override fun removeFactionTurns(factionId: Long, officerLevel: Short) {
        factionTurnRepository.deleteByNationIdAndOfficerLevel(factionId, officerLevel)
    }

    private fun toOfficerSnapshot(entity: Officer): OfficerSnapshot = entity.toSnapshot()

    private fun Officer.applySnapshot(snapshot: OfficerSnapshot) {
        id = snapshot.id
        sessionId = snapshot.sessionId
        userId = snapshot.userId
        name = snapshot.name
        factionId = snapshot.factionId
        planetId = snapshot.planetId
        fleetId = snapshot.fleetId
        npcState = snapshot.npcState
        npcOrg = snapshot.npcOrg
        affinity = snapshot.affinity
        bornYear = snapshot.bornYear
        deadYear = snapshot.deadYear
        picture = snapshot.picture
        imageServer = snapshot.imageServer
        leadership = snapshot.leadership
        leadershipExp = snapshot.leadershipExp
        command = snapshot.command
        commandExp = snapshot.commandExp
        intelligence = snapshot.intelligence
        intelligenceExp = snapshot.intelligenceExp
        politics = snapshot.politics
        politicsExp = snapshot.politicsExp
        administration = snapshot.administration
        administrationExp = snapshot.administrationExp
        mobility = snapshot.mobility
        mobilityExp = snapshot.mobilityExp
        attack = snapshot.attack
        attackExp = snapshot.attackExp
        defense = snapshot.defense
        defenseExp = snapshot.defenseExp
        dex1 = snapshot.dex1
        dex2 = snapshot.dex2
        dex3 = snapshot.dex3
        dex4 = snapshot.dex4
        dex5 = snapshot.dex5
        dex6 = snapshot.dex6
        dex7 = snapshot.dex7
        dex8 = snapshot.dex8
        injury = snapshot.injury
        experience = snapshot.experience
        dedication = snapshot.dedication
        officerLevel = snapshot.officerLevel
        officerCity = snapshot.officerCity
        permission = snapshot.permission
        funds = snapshot.funds
        supplies = snapshot.supplies
        ships = snapshot.ships
        shipClass = snapshot.shipClass
        training = snapshot.training
        morale = snapshot.morale
        flagshipCode = snapshot.flagshipCode
        equipCode = snapshot.equipCode
        engineCode = snapshot.engineCode
        accessoryCode = snapshot.accessoryCode
        ownerName = snapshot.ownerName
        newmsg = snapshot.newmsg
        turnTime = snapshot.turnTime
        recentWarTime = snapshot.recentWarTime
        makeLimit = snapshot.makeLimit
        killTurn = snapshot.killTurn
        blockState = snapshot.blockState
        dedLevel = snapshot.dedLevel
        expLevel = snapshot.expLevel
        age = snapshot.age
        startAge = snapshot.startAge
        belong = snapshot.belong
        betray = snapshot.betray
        personalCode = snapshot.personalCode
        specialCode = snapshot.specialCode
        specAge = snapshot.specAge
        special2Code = snapshot.special2Code
        spec2Age = snapshot.spec2Age
        defenceTrain = snapshot.defenceTrain
        tournamentState = snapshot.tournamentState
        commandPoints = snapshot.commandPoints
        commandEndTime = snapshot.commandEndTime
        posX = snapshot.posX
        posY = snapshot.posY
        destX = snapshot.destX
        destY = snapshot.destY
        lastTurn = snapshot.lastTurn.toMutableMap()
        meta = snapshot.meta.toMutableMap()
        penalty = snapshot.penalty.toMutableMap()
        createdAt = snapshot.createdAt
        updatedAt = snapshot.updatedAt
    }

    private fun toPlanetSnapshot(entity: Planet): PlanetSnapshot = entity.toSnapshot()

    private fun Planet.applySnapshot(snapshot: PlanetSnapshot) {
        id = snapshot.id
        sessionId = snapshot.sessionId
        name = snapshot.name
        mapCityId = snapshot.mapCityId
        level = snapshot.level
        factionId = snapshot.factionId
        supplyState = snapshot.supplyState
        frontState = snapshot.frontState
        population = snapshot.population
        populationMax = snapshot.populationMax
        production = snapshot.production
        productionMax = snapshot.productionMax
        commerce = snapshot.commerce
        commerceMax = snapshot.commerceMax
        security = snapshot.security
        securityMax = snapshot.securityMax
        approval = snapshot.approval
        tradeRoute = snapshot.tradeRoute
        dead = snapshot.dead
        orbitalDefense = snapshot.orbitalDefense
        orbitalDefenseMax = snapshot.orbitalDefenseMax
        fortress = snapshot.fortress
        fortressMax = snapshot.fortressMax
        officerSet = snapshot.officerSet
        state = snapshot.state
        region = snapshot.region
        term = snapshot.term
        conflict = snapshot.conflict.toMutableMap()
        meta = snapshot.meta.toMutableMap()
    }

    private fun toFactionSnapshot(entity: Faction): FactionSnapshot = entity.toSnapshot()

    private fun Faction.applySnapshot(snapshot: FactionSnapshot) {
        id = snapshot.id
        sessionId = snapshot.sessionId
        name = snapshot.name
        abbreviation = snapshot.abbreviation
        color = snapshot.color
        capitalPlanetId = snapshot.capitalPlanetId
        funds = snapshot.funds
        supplies = snapshot.supplies
        taxRate = snapshot.taxRate
        conscriptionRate = snapshot.conscriptionRate
        conscriptionRateTmp = snapshot.conscriptionRateTmp
        secretLimit = snapshot.secretLimit
        chiefOfficerId = snapshot.chiefOfficerId
        scoutLevel = snapshot.scoutLevel
        warState = snapshot.warState
        strategicCmdLimit = snapshot.strategicCmdLimit
        surrenderLimit = snapshot.surrenderLimit
        techLevel = snapshot.techLevel
        militaryPower = snapshot.militaryPower
        officerCount = snapshot.officerCount
        factionRank = snapshot.factionRank
        factionType = snapshot.factionType
        spy = snapshot.spy.toMutableMap()
        meta = snapshot.meta.toMutableMap()
        createdAt = snapshot.createdAt
        updatedAt = snapshot.updatedAt
    }

    private fun toFleetSnapshot(entity: Fleet): FleetSnapshot = entity.toSnapshot()

    private fun Fleet.applySnapshot(snapshot: FleetSnapshot) {
        id = snapshot.id
        sessionId = snapshot.sessionId
        leaderOfficerId = snapshot.leaderOfficerId
        factionId = snapshot.factionId
        name = snapshot.name
        meta = snapshot.meta.toMutableMap()
        createdAt = snapshot.createdAt
    }

    private fun toDiplomacySnapshot(entity: Diplomacy): DiplomacySnapshot = entity.toSnapshot()

    private fun Diplomacy.applySnapshot(snapshot: DiplomacySnapshot) {
        id = snapshot.id
        sessionId = snapshot.sessionId
        srcFactionId = snapshot.srcFactionId
        destFactionId = snapshot.destFactionId
        stateCode = snapshot.stateCode
        term = snapshot.term
        isDead = snapshot.isDead
        isShowing = snapshot.isShowing
        meta = snapshot.meta.toMutableMap()
        createdAt = snapshot.createdAt
    }

    private fun toOfficerTurnSnapshot(entity: OfficerTurn): OfficerTurnSnapshot = OfficerTurnSnapshot(
        id = entity.id,
        sessionId = entity.sessionId,
        officerId = entity.officerId,
        turnIdx = entity.turnIdx,
        actionCode = entity.actionCode,
        arg = entity.arg.toMutableMap(),
        brief = entity.brief,
        createdAt = entity.createdAt,
    )

    private fun OfficerTurnSnapshot.toEntityWithGeneratedId(): OfficerTurn = OfficerTurn(
        id = 0,
        sessionId = sessionId,
        officerId = officerId,
        turnIdx = turnIdx,
        actionCode = actionCode,
        arg = arg.toMutableMap(),
        brief = brief,
        createdAt = createdAt,
    )

    private fun toFactionTurnSnapshot(entity: FactionTurn): FactionTurnSnapshot = FactionTurnSnapshot(
        id = entity.id,
        sessionId = entity.sessionId,
        factionId = entity.factionId,
        officerLevel = entity.officerLevel,
        turnIdx = entity.turnIdx,
        actionCode = entity.actionCode,
        arg = entity.arg.toMutableMap(),
        brief = entity.brief,
        createdAt = entity.createdAt,
    )

    private fun FactionTurnSnapshot.toEntityWithGeneratedId(): FactionTurn = FactionTurn(
        id = 0,
        sessionId = sessionId,
        factionId = factionId,
        officerLevel = officerLevel,
        turnIdx = turnIdx,
        actionCode = actionCode,
        arg = arg.toMutableMap(),
        brief = brief,
        createdAt = createdAt,
    )
}
