package com.openlogh.engine.turn.cqrs.persist

import com.openlogh.engine.turn.cqrs.memory.CitySnapshot
import com.openlogh.engine.turn.cqrs.memory.DiplomacySnapshot
import com.openlogh.engine.turn.cqrs.memory.GeneralSnapshot
import com.openlogh.engine.turn.cqrs.memory.GeneralTurnSnapshot
import com.openlogh.engine.turn.cqrs.memory.NationSnapshot
import com.openlogh.engine.turn.cqrs.memory.NationTurnSnapshot
import com.openlogh.engine.turn.cqrs.memory.TroopSnapshot
import com.openlogh.engine.turn.cqrs.port.WorldReadPort
import com.openlogh.engine.turn.cqrs.port.WorldWritePort
import com.openlogh.entity.City
import com.openlogh.entity.Diplomacy
import com.openlogh.entity.General
import com.openlogh.entity.GeneralTurn
import com.openlogh.entity.Nation
import com.openlogh.entity.NationTurn
import com.openlogh.entity.Troop
import com.openlogh.repository.CityRepository
import com.openlogh.repository.DiplomacyRepository
import com.openlogh.repository.GeneralRepository
import com.openlogh.repository.GeneralTurnRepository
import com.openlogh.repository.NationRepository
import com.openlogh.repository.NationTurnRepository
import com.openlogh.repository.TroopRepository

class JpaWorldPorts(
    private val worldId: Long,
    private val generalRepository: GeneralRepository,
    private val cityRepository: CityRepository,
    private val nationRepository: NationRepository,
    private val troopRepository: TroopRepository,
    private val diplomacyRepository: DiplomacyRepository,
    private val generalTurnRepository: GeneralTurnRepository,
    private val nationTurnRepository: NationTurnRepository,
) : WorldPorts {

    override fun general(id: Long): GeneralSnapshot? =
        generalRepository.findById(id)
            .orElse(null)
            ?.takeIf { it.worldId == worldId }
            ?.let(::toGeneralSnapshot)

    override fun city(id: Long): CitySnapshot? =
        cityRepository.findById(id)
            .orElse(null)
            ?.takeIf { it.worldId == worldId }
            ?.let(::toCitySnapshot)

    override fun nation(id: Long): NationSnapshot? =
        nationRepository.findById(id)
            .orElse(null)
            ?.takeIf { it.worldId == worldId }
            ?.let(::toNationSnapshot)

    override fun troop(id: Long): TroopSnapshot? =
        troopRepository.findById(id)
            .orElse(null)
            ?.takeIf { it.worldId == worldId }
            ?.let(::toTroopSnapshot)

    override fun diplomacy(id: Long): DiplomacySnapshot? =
        diplomacyRepository.findById(id)
            .orElse(null)
            ?.takeIf { it.worldId == worldId }
            ?.let(::toDiplomacySnapshot)

    override fun allGenerals(): Collection<GeneralSnapshot> =
        generalRepository.findByWorldId(worldId).map(::toGeneralSnapshot)

    override fun allCities(): Collection<CitySnapshot> =
        cityRepository.findByWorldId(worldId).map(::toCitySnapshot)

    override fun allNations(): Collection<NationSnapshot> =
        nationRepository.findByWorldId(worldId).map(::toNationSnapshot)

    override fun allTroops(): Collection<TroopSnapshot> =
        troopRepository.findByWorldId(worldId).map(::toTroopSnapshot)

    override fun allDiplomacies(): Collection<DiplomacySnapshot> =
        diplomacyRepository.findByWorldId(worldId).map(::toDiplomacySnapshot)

    override fun generalsByNation(nationId: Long): List<GeneralSnapshot> =
        generalRepository.findByWorldIdAndNationId(worldId, nationId).map(::toGeneralSnapshot)

    override fun generalsByCity(cityId: Long): List<GeneralSnapshot> =
        generalRepository.findByCityId(cityId)
            .asSequence()
            .filter { it.worldId == worldId }
            .map(::toGeneralSnapshot)
            .toList()

    override fun citiesByNation(nationId: Long): List<CitySnapshot> =
        cityRepository.findByNationId(nationId)
            .asSequence()
            .filter { it.worldId == worldId }
            .map(::toCitySnapshot)
            .toList()

    override fun diplomaciesByNation(nationId: Long): List<DiplomacySnapshot> =
        diplomacyRepository.findByWorldIdAndSrcNationIdOrDestNationId(worldId, nationId, nationId)
            .asSequence()
            .filter { it.worldId == worldId }
            .map(::toDiplomacySnapshot)
            .toList()

    override fun activeDiplomacies(): List<DiplomacySnapshot> =
        diplomacyRepository.findByWorldIdAndIsDeadFalse(worldId).map(::toDiplomacySnapshot)

    override fun generalTurns(generalId: Long): List<GeneralTurnSnapshot> =
        generalTurnRepository.findByGeneralIdOrderByTurnIdx(generalId)
            .asSequence()
            .filter { it.worldId == worldId }
            .map(::toGeneralTurnSnapshot)
            .toList()

    override fun nationTurns(nationId: Long, officerLevel: Short): List<NationTurnSnapshot> =
        nationTurnRepository.findByNationIdAndOfficerLevelOrderByTurnIdx(nationId, officerLevel)
            .asSequence()
            .filter { it.worldId == worldId }
            .map(::toNationTurnSnapshot)
            .toList()

    override fun putGeneral(snapshot: GeneralSnapshot) {
        val entity = generalRepository.findById(snapshot.id).orElse(General())
        entity.applySnapshot(snapshot)
        generalRepository.save(entity)
    }

    override fun putCity(snapshot: CitySnapshot) {
        val entity = cityRepository.findById(snapshot.id).orElse(City())
        entity.applySnapshot(snapshot)
        cityRepository.save(entity)
    }

    override fun putNation(snapshot: NationSnapshot) {
        val entity = nationRepository.findById(snapshot.id).orElse(Nation())
        entity.applySnapshot(snapshot)
        nationRepository.save(entity)
    }

    override fun putTroop(snapshot: TroopSnapshot) {
        val entity = troopRepository.findById(snapshot.id).orElse(Troop())
        entity.applySnapshot(snapshot)
        troopRepository.save(entity)
    }

    override fun putDiplomacy(snapshot: DiplomacySnapshot) {
        val entity = diplomacyRepository.findById(snapshot.id).orElse(Diplomacy())
        entity.applySnapshot(snapshot)
        diplomacyRepository.save(entity)
    }

    override fun deleteGeneral(id: Long) {
        generalRepository.deleteById(id)
    }

    override fun deleteCity(id: Long) {
        cityRepository.deleteById(id)
    }

    override fun deleteNation(id: Long) {
        nationRepository.deleteById(id)
    }

    override fun deleteTroop(id: Long) {
        troopRepository.deleteById(id)
    }

    override fun deleteDiplomacy(id: Long) {
        diplomacyRepository.deleteById(id)
    }

    override fun setGeneralTurns(generalId: Long, turns: List<GeneralTurnSnapshot>) {
        generalTurnRepository.deleteByGeneralId(generalId)
        val entities = turns
            .sortedBy { it.turnIdx }
            .map { it.toEntityWithGeneratedId() }
        generalTurnRepository.saveAll(entities)
    }

    override fun setNationTurns(nationId: Long, officerLevel: Short, turns: List<NationTurnSnapshot>) {
        nationTurnRepository.deleteByNationIdAndOfficerLevel(nationId, officerLevel)
        val entities = turns
            .sortedBy { it.turnIdx }
            .map { it.toEntityWithGeneratedId() }
        nationTurnRepository.saveAll(entities)
    }

    override fun removeGeneralTurns(generalId: Long) {
        generalTurnRepository.deleteByGeneralId(generalId)
    }

    override fun removeNationTurns(nationId: Long, officerLevel: Short) {
        nationTurnRepository.deleteByNationIdAndOfficerLevel(nationId, officerLevel)
    }

    private fun toGeneralSnapshot(entity: General): GeneralSnapshot = GeneralSnapshot(
        id = entity.id,
        worldId = entity.worldId,
        userId = entity.userId,
        name = entity.name,
        nationId = entity.nationId,
        cityId = entity.cityId,
        troopId = entity.troopId,
        npcState = entity.npcState,
        npcOrg = entity.npcOrg,
        affinity = entity.affinity,
        bornYear = entity.bornYear,
        deadYear = entity.deadYear,
        picture = entity.picture,
        imageServer = entity.imageServer,
        leadership = entity.leadership,
        leadershipExp = entity.leadershipExp,
        strength = entity.strength,
        strengthExp = entity.strengthExp,
        intel = entity.intel,
        intelExp = entity.intelExp,
        politics = entity.politics,
        charm = entity.charm,
        dex1 = entity.dex1,
        dex2 = entity.dex2,
        dex3 = entity.dex3,
        dex4 = entity.dex4,
        dex5 = entity.dex5,
        injury = entity.injury,
        experience = entity.experience,
        dedication = entity.dedication,
        officerLevel = entity.officerLevel,
        officerCity = entity.officerCity,
        permission = entity.permission,
        gold = entity.gold,
        rice = entity.rice,
        crew = entity.crew,
        crewType = entity.crewType,
        train = entity.train,
        atmos = entity.atmos,
        weaponCode = entity.weaponCode,
        bookCode = entity.bookCode,
        horseCode = entity.horseCode,
        itemCode = entity.itemCode,
        ownerName = entity.ownerName,
        newmsg = entity.newmsg,
        turnTime = entity.turnTime,
        recentWarTime = entity.recentWarTime,
        makeLimit = entity.makeLimit,
        killTurn = entity.killTurn,
        blockState = entity.blockState,
        dedLevel = entity.dedLevel,
        expLevel = entity.expLevel,
        age = entity.age,
        startAge = entity.startAge,
        belong = entity.belong,
        betray = entity.betray,
        personalCode = entity.personalCode,
        specialCode = entity.specialCode,
        specAge = entity.specAge,
        special2Code = entity.special2Code,
        spec2Age = entity.spec2Age,
        defenceTrain = entity.defenceTrain,
        tournamentState = entity.tournamentState,
        commandPoints = entity.commandPoints,
        commandEndTime = entity.commandEndTime,
        lastTurn = entity.lastTurn.toMutableMap(),
        meta = entity.meta.toMutableMap(),
        penalty = entity.penalty.toMutableMap(),
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
    )

    private fun General.applySnapshot(snapshot: GeneralSnapshot) {
        id = snapshot.id
        worldId = snapshot.worldId
        userId = snapshot.userId
        name = snapshot.name
        nationId = snapshot.nationId
        cityId = snapshot.cityId
        troopId = snapshot.troopId
        npcState = snapshot.npcState
        npcOrg = snapshot.npcOrg
        affinity = snapshot.affinity
        bornYear = snapshot.bornYear
        deadYear = snapshot.deadYear
        picture = snapshot.picture
        imageServer = snapshot.imageServer
        leadership = snapshot.leadership
        leadershipExp = snapshot.leadershipExp
        strength = snapshot.strength
        strengthExp = snapshot.strengthExp
        intel = snapshot.intel
        intelExp = snapshot.intelExp
        politics = snapshot.politics
        charm = snapshot.charm
        dex1 = snapshot.dex1
        dex2 = snapshot.dex2
        dex3 = snapshot.dex3
        dex4 = snapshot.dex4
        dex5 = snapshot.dex5
        injury = snapshot.injury
        experience = snapshot.experience
        dedication = snapshot.dedication
        officerLevel = snapshot.officerLevel
        officerCity = snapshot.officerCity
        permission = snapshot.permission
        gold = snapshot.gold
        rice = snapshot.rice
        crew = snapshot.crew
        crewType = snapshot.crewType
        train = snapshot.train
        atmos = snapshot.atmos
        weaponCode = snapshot.weaponCode
        bookCode = snapshot.bookCode
        horseCode = snapshot.horseCode
        itemCode = snapshot.itemCode
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
        lastTurn = snapshot.lastTurn.toMutableMap()
        meta = snapshot.meta.toMutableMap()
        penalty = snapshot.penalty.toMutableMap()
        createdAt = snapshot.createdAt
        updatedAt = snapshot.updatedAt
    }

    private fun toCitySnapshot(entity: City): CitySnapshot = CitySnapshot(
        id = entity.id,
        worldId = entity.worldId,
        name = entity.name,
        mapCityId = entity.mapCityId,
        level = entity.level,
        nationId = entity.nationId,
        supplyState = entity.supplyState,
        frontState = entity.frontState,
        pop = entity.pop,
        popMax = entity.popMax,
        agri = entity.agri,
        agriMax = entity.agriMax,
        comm = entity.comm,
        commMax = entity.commMax,
        secu = entity.secu,
        secuMax = entity.secuMax,
        trust = entity.trust,
        trade = entity.trade,
        dead = entity.dead,
        def = entity.def,
        defMax = entity.defMax,
        wall = entity.wall,
        wallMax = entity.wallMax,
        officerSet = entity.officerSet,
        state = entity.state,
        region = entity.region,
        term = entity.term,
        conflict = entity.conflict.toMutableMap(),
        meta = entity.meta.toMutableMap(),
    )

    private fun City.applySnapshot(snapshot: CitySnapshot) {
        id = snapshot.id
        worldId = snapshot.worldId
        name = snapshot.name
        mapCityId = snapshot.mapCityId
        level = snapshot.level
        nationId = snapshot.nationId
        supplyState = snapshot.supplyState
        frontState = snapshot.frontState
        pop = snapshot.pop
        popMax = snapshot.popMax
        agri = snapshot.agri
        agriMax = snapshot.agriMax
        comm = snapshot.comm
        commMax = snapshot.commMax
        secu = snapshot.secu
        secuMax = snapshot.secuMax
        trust = snapshot.trust
        trade = snapshot.trade
        dead = snapshot.dead
        def = snapshot.def
        defMax = snapshot.defMax
        wall = snapshot.wall
        wallMax = snapshot.wallMax
        officerSet = snapshot.officerSet
        state = snapshot.state
        region = snapshot.region
        term = snapshot.term
        conflict = snapshot.conflict.toMutableMap()
        meta = snapshot.meta.toMutableMap()
    }

    private fun toNationSnapshot(entity: Nation): NationSnapshot = NationSnapshot(
        id = entity.id,
        worldId = entity.worldId,
        name = entity.name,
        color = entity.color,
        capitalCityId = entity.capitalCityId,
        gold = entity.gold,
        rice = entity.rice,
        bill = entity.bill,
        rate = entity.rate,
        rateTmp = entity.rateTmp,
        secretLimit = entity.secretLimit,
        chiefGeneralId = entity.chiefGeneralId,
        scoutLevel = entity.scoutLevel,
        warState = entity.warState,
        strategicCmdLimit = entity.strategicCmdLimit,
        surrenderLimit = entity.surrenderLimit,
        tech = entity.tech,
        power = entity.power,
        level = entity.level,
        typeCode = entity.typeCode,
        spy = entity.spy.toMutableMap(),
        meta = entity.meta.toMutableMap(),
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
    )

    private fun Nation.applySnapshot(snapshot: NationSnapshot) {
        id = snapshot.id
        worldId = snapshot.worldId
        name = snapshot.name
        color = snapshot.color
        capitalCityId = snapshot.capitalCityId
        gold = snapshot.gold
        rice = snapshot.rice
        bill = snapshot.bill
        rate = snapshot.rate
        rateTmp = snapshot.rateTmp
        secretLimit = snapshot.secretLimit
        chiefGeneralId = snapshot.chiefGeneralId
        scoutLevel = snapshot.scoutLevel
        warState = snapshot.warState
        strategicCmdLimit = snapshot.strategicCmdLimit
        surrenderLimit = snapshot.surrenderLimit
        tech = snapshot.tech
        power = snapshot.power
        level = snapshot.level
        typeCode = snapshot.typeCode
        spy = snapshot.spy.toMutableMap()
        meta = snapshot.meta.toMutableMap()
        createdAt = snapshot.createdAt
        updatedAt = snapshot.updatedAt
    }

    private fun toTroopSnapshot(entity: Troop): TroopSnapshot = TroopSnapshot(
        id = entity.id,
        worldId = entity.worldId,
        leaderGeneralId = entity.leaderGeneralId,
        nationId = entity.nationId,
        name = entity.name,
        meta = entity.meta.toMutableMap(),
        createdAt = entity.createdAt,
    )

    private fun Troop.applySnapshot(snapshot: TroopSnapshot) {
        id = snapshot.id
        worldId = snapshot.worldId
        leaderGeneralId = snapshot.leaderGeneralId
        nationId = snapshot.nationId
        name = snapshot.name
        meta = snapshot.meta.toMutableMap()
        createdAt = snapshot.createdAt
    }

    private fun toDiplomacySnapshot(entity: Diplomacy): DiplomacySnapshot = DiplomacySnapshot(
        id = entity.id,
        worldId = entity.worldId,
        srcNationId = entity.srcNationId,
        destNationId = entity.destNationId,
        stateCode = entity.stateCode,
        term = entity.term,
        isDead = entity.isDead,
        isShowing = entity.isShowing,
        meta = entity.meta.toMutableMap(),
        createdAt = entity.createdAt,
    )

    private fun Diplomacy.applySnapshot(snapshot: DiplomacySnapshot) {
        id = snapshot.id
        worldId = snapshot.worldId
        srcNationId = snapshot.srcNationId
        destNationId = snapshot.destNationId
        stateCode = snapshot.stateCode
        term = snapshot.term
        isDead = snapshot.isDead
        isShowing = snapshot.isShowing
        meta = snapshot.meta.toMutableMap()
        createdAt = snapshot.createdAt
    }

    private fun toGeneralTurnSnapshot(entity: GeneralTurn): GeneralTurnSnapshot = GeneralTurnSnapshot(
        id = entity.id,
        worldId = entity.worldId,
        generalId = entity.generalId,
        turnIdx = entity.turnIdx,
        actionCode = entity.actionCode,
        arg = entity.arg.toMutableMap(),
        brief = entity.brief,
        createdAt = entity.createdAt,
    )

    private fun GeneralTurnSnapshot.toEntityWithGeneratedId(): GeneralTurn = GeneralTurn(
        id = 0,
        worldId = worldId,
        generalId = generalId,
        turnIdx = turnIdx,
        actionCode = actionCode,
        arg = arg.toMutableMap(),
        brief = brief,
        createdAt = createdAt,
    )

    private fun toNationTurnSnapshot(entity: NationTurn): NationTurnSnapshot = NationTurnSnapshot(
        id = entity.id,
        worldId = entity.worldId,
        nationId = entity.nationId,
        officerLevel = entity.officerLevel,
        turnIdx = entity.turnIdx,
        actionCode = entity.actionCode,
        arg = entity.arg.toMutableMap(),
        brief = entity.brief,
        createdAt = entity.createdAt,
    )

    private fun NationTurnSnapshot.toEntityWithGeneratedId(): NationTurn = NationTurn(
        id = 0,
        worldId = worldId,
        nationId = nationId,
        officerLevel = officerLevel,
        turnIdx = turnIdx,
        actionCode = actionCode,
        arg = arg.toMutableMap(),
        brief = brief,
        createdAt = createdAt,
    )
}
