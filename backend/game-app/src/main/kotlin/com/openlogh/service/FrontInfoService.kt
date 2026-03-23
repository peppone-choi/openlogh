package com.openlogh.service

import com.openlogh.dto.*
import com.openlogh.entity.Officer
import com.openlogh.model.ScenarioData
import com.openlogh.repository.*
import org.springframework.stereotype.Service

@Service
class FrontInfoService(
    private val sessionStateRepository: SessionStateRepository,
    private val officerRepository: OfficerRepository,
    private val factionRepository: FactionRepository,
    private val planetRepository: PlanetRepository,
    private val messageRepository: MessageRepository,
    private val recordRepository: RecordRepository,
    private val appUserRepository: AppUserRepository,
    private val fleetRepository: FleetRepository,
    private val officerRankService: OfficerRankService,
    private val scenarioService: ScenarioService,
    private val planetService: PlanetService,
) {
    fun getFrontInfo(
        worldId: Short,
        loginId: String,
        lastRecordId: Long?,
        lastHistoryId: Long?,
    ): FrontInfoResponse {
        val world = sessionStateRepository.findById(worldId).orElse(null)
            ?: throw IllegalArgumentException("세계를 찾을 수 없습니다.")
        val user = appUserRepository.findByLoginId(loginId)
            ?: throw IllegalArgumentException("유저를 찾을 수 없습니다.")

        val sessionId = world.id.toLong()
        val allOfficers = officerRepository.findBySessionId(sessionId)
        val factions = factionRepository.findBySessionId(sessionId)
        val myOfficer = allOfficers.find { it.userId == user.id }

        val scenario = scenarioService.getScenario(world.scenarioCode) ?: ScenarioData(title = "")
        val startYear = (world.config["startyear"] as? Number)?.toInt() ?: world.currentYear.toInt()

        val officerCountByFaction = factions.map { faction ->
            listOf(faction.id.toInt(), allOfficers.count { it.factionId == faction.id })
        }

        val onlineFactions = factions.map { faction ->
            OnlineFactionInfo(
                id = faction.id,
                name = faction.name,
                color = faction.color,
                officerCount = allOfficers.count { it.factionId == faction.id },
            )
        }

        val globalInfo = GlobalInfo(
            year = world.currentYear.toInt(),
            month = world.currentMonth.toInt(),
            turnTerm = world.tickSeconds / 60,
            startyear = startYear,
            officerCount = officerCountByFaction,
            onlineFactions = onlineFactions,
            onlineUserCnt = allOfficers.count { it.userId != null },
            auctionCount = 0,
            tournamentState = 0,
            tournamentType = null,
            tournamentTime = null,
            isTournamentActive = false,
            isTournamentApplicationOpen = false,
            isBettingActive = false,
            lastExecuted = world.updatedAt.toString(),
            isLocked = false,
            scenarioText = scenario.title,
            realtimeMode = world.realtimeMode,
            extendedOfficer = (world.config["extend"] as? Number)?.toInt() ?: 0,
            isFiction = (world.config["fiction"] as? Number)?.toInt() ?: 0,
            npcMode = (world.config["npcMode"] as? Number)?.toInt() ?: 0,
            joinMode = (world.config["joinMode"] as? String) ?: "normal",
            develCost = 0,
            noticeMsg = 0,
            apiLimit = (world.config["apiLimit"] as? Number)?.toInt() ?: 300,
            officerCntLimit = (world.config["maxGeneral"] as? Number)?.toInt() ?: 500,
            serverCnt = 1,
            lastVoteID = 0,
            lastVote = null,
        )

        val officerFrontInfo = myOfficer?.let { buildOfficerFrontInfo(it, world) }
        val factionFrontInfo = myOfficer?.let { officer ->
            factions.find { it.id == officer.factionId }?.let { buildFactionFrontInfo(it, allOfficers) }
        }
        val planetFrontInfo = myOfficer?.let { officer ->
            planetRepository.findById(officer.planetId).orElse(null)?.let { planet ->
                buildPlanetFrontInfo(planet, allOfficers, factions)
            }
        }

        val sinceRecordId = lastRecordId ?: 0L
        val sinceHistoryId = lastHistoryId ?: 0L

        val officerRecords = myOfficer?.let {
            messageRepository.findByDestIdAndMailboxCodeAndIdGreaterThanOrderBySentAtDesc(
                it.id, "general_action", sinceRecordId
            )
        } ?: emptyList()

        val worldRecords = messageRepository
            .findBySessionIdAndMailboxCodeAndIdGreaterThanOrderBySentAtDesc(
                sessionId, "world_record", sinceRecordId
            )

        val historyRecords = messageRepository
            .findBySessionIdAndMailboxCodeAndIdGreaterThanOrderBySentAtDesc(
                sessionId, "world_history", sinceHistoryId
            )

        val recentRecordInfo = RecentRecordInfo(
            flushOfficer = sinceRecordId == 0L,
            flushGlobal = sinceRecordId == 0L,
            flushHistory = sinceHistoryId == 0L,
            officer = officerRecords.take(30).map { msg ->
                RecordEntry(
                    id = msg.id,
                    message = (msg.payload["message"] as? String) ?: "",
                    date = msg.sentAt.toString(),
                )
            },
            global = worldRecords.take(30).map { msg ->
                RecordEntry(
                    id = msg.id,
                    message = (msg.payload["message"] as? String) ?: "",
                    date = msg.sentAt.toString(),
                )
            },
            history = historyRecords.take(30).map { msg ->
                RecordEntry(
                    id = msg.id,
                    message = (msg.payload["message"] as? String) ?: "",
                    date = msg.sentAt.toString(),
                )
            },
        )

        return FrontInfoResponse(
            global = globalInfo,
            officer = officerFrontInfo,
            faction = factionFrontInfo,
            planet = planetFrontInfo,
            recentRecord = recentRecordInfo,
            aux = AuxInfo(),
        )
    }

    private fun buildOfficerFrontInfo(officer: Officer, world: com.openlogh.entity.SessionState): OfficerFrontInfo {
        val rankText = officerRankService.getRankTitle(officer.rank.toInt(), 1)
        val turnTerm = world.tickSeconds / 60

        @Suppress("UNCHECKED_CAST")
        val warStats = officer.meta["warStats"] as? Map<String, Any> ?: emptyMap()

        return OfficerFrontInfo(
            no = officer.id,
            name = officer.name,
            picture = officer.picture,
            imgsvr = officer.imageServer.toInt(),
            faction = officer.factionId,
            npc = officer.npcState.toInt(),
            planet = officer.planetId,
            troop = officer.fleetId,
            rank = officer.rank.toInt(),
            rankText = rankText,
            stationedSystem = officer.stationedSystem,
            permission = permissionToInt(officer.permission),
            lbonus = 0,
            leadership = officer.leadership.toInt(),
            leadershipExp = officer.leadershipExp.toInt(),
            command = officer.command.toInt(),
            commandExp = officer.commandExp.toInt(),
            intelligence = officer.intelligence.toInt(),
            intelligenceExp = officer.intelligenceExp.toInt(),
            politics = officer.politics.toInt(),
            politicsExp = officer.politicsExp.toInt(),
            administration = officer.administration.toInt(),
            administrationExp = officer.administrationExp.toInt(),
            experience = officer.experience,
            dedication = officer.dedication,
            explevel = officer.expLevel.toInt(),
            dedlevel = officer.dedLevel.toInt(),
            honorText = "",
            dedLevelText = "",
            taxRate = 0,
            funds = officer.funds,
            supplies = officer.supplies,
            ships = officer.ships,
            shipClass = officer.shipClass.toString(),
            training = officer.training.toInt(),
            morale = officer.morale.toInt(),
            flagship = resolveCode(officer.flagshipCode),
            equipment = resolveCode(officer.equipCode),
            engine = resolveCode(officer.engineCode),
            accessory = resolveCode(officer.accessoryCode),
            personal = resolveCode(officer.personalCode),
            specialDomestic = resolveCode(officer.specialCode),
            specialWar = resolveCode(officer.special2Code),
            specage = officer.specAge.toInt(),
            specage2 = officer.spec2Age.toInt(),
            age = officer.age.toInt(),
            injury = officer.injury.toInt(),
            killturn = officer.killTurn?.toInt(),
            belong = officer.belong.toInt(),
            betray = officer.betray.toInt(),
            blockState = officer.blockState.toInt(),
            defenceTrain = officer.defenceTrain.toInt(),
            turntime = officer.turnTime.toString(),
            recentWar = officer.recentWarTime?.toString(),
            commandPoints = officer.commandPoints,
            commandEndTime = officer.commandEndTime?.toString(),
            ownerName = officer.ownerName,
            refreshScoreTotal = null,
            refreshScore = null,
            autorunLimit = (officer.meta["autorunLimit"] as? Number)?.toInt() ?: 0,
            reservedCommand = null,
            fleetInfo = null,
            dex1 = officer.dex1,
            dex2 = officer.dex2,
            dex3 = officer.dex3,
            dex4 = officer.dex4,
            dex5 = officer.dex5,
            warnum = (warStats["warnum"] as? Number)?.toInt() ?: 0,
            killnum = (warStats["killnum"] as? Number)?.toInt() ?: 0,
            deathnum = (warStats["deathnum"] as? Number)?.toInt() ?: 0,
            killships = (warStats["killships"] as? Number)?.toInt() ?: 0,
            deathships = (warStats["deathships"] as? Number)?.toInt() ?: 0,
            firenum = (warStats["firenum"] as? Number)?.toInt() ?: 0,
        )
    }

    private fun buildFactionFrontInfo(
        faction: com.openlogh.entity.Faction,
        allOfficers: List<Officer>,
    ): FactionFrontInfo {
        val factionOfficers = allOfficers.filter { it.factionId == faction.id }
        val planets = planetRepository.findByFactionId(faction.id)

        return FactionFrontInfo(
            id = faction.id,
            full = false,
            name = faction.name,
            color = faction.color,
            factionRank = faction.factionRank.toInt(),
            type = FactionTypeInfo(raw = faction.factionType, name = faction.factionType, pros = "", cons = ""),
            funds = faction.funds,
            supplies = faction.supplies,
            techLevel = faction.techLevel,
            militaryPower = faction.militaryPower,
            officerCount = factionOfficers.size,
            capital = faction.capitalPlanetId,
            conscriptionRate = faction.conscriptionRate.toInt(),
            taxRate = faction.taxRate.toInt(),
            population = FactionPopulationInfo(
                planetCnt = planets.size,
                now = planets.sumOf { it.population },
                max = planets.sumOf { it.populationMax },
            ),
            ships = FactionShipInfo(
                officerCnt = factionOfficers.size,
                now = factionOfficers.sumOf { it.ships },
                max = 0,
            ),
            onlineGen = "",
            notice = null,
            topChiefs = emptyMap(),
            diplomaticLimit = faction.secretLimit.toInt(),
            strategicCmdLimit = faction.strategicCmdLimit.toInt(),
            impossibleStrategicCommand = emptyList(),
            prohibitScout = faction.scoutLevel.toInt(),
            prohibitWar = faction.warState.toInt(),
        )
    }

    private fun buildPlanetFrontInfo(
        planet: com.openlogh.entity.Planet,
        allOfficers: List<Officer>,
        factions: List<com.openlogh.entity.Faction>,
    ): PlanetFrontInfo {
        val faction = factions.find { it.id == planet.factionId }
        val region = planetService.canonicalRegionForDisplay(planet)
        val planetOfficers = allOfficers.filter { it.planetId == planet.id }

        val officerMap = planetOfficers.mapIndexed { idx, officer ->
            idx to PlanetOfficerInfo(
                rank = officer.rank.toInt(),
                name = officer.name,
                npc = officer.npcState.toInt(),
            )
        }.toMap()

        return PlanetFrontInfo(
            id = planet.id,
            name = planet.name,
            level = planet.level.toInt(),
            region = region.toInt(),
            factionInfo = PlanetFactionInfo(
                id = faction?.id ?: 0,
                name = faction?.name ?: "",
                color = faction?.color ?: "",
            ),
            approval = planet.approval.toInt(),
            population = listOf(planet.population, planet.populationMax),
            production = listOf(planet.production, planet.productionMax),
            commerce = listOf(planet.commerce, planet.commerceMax),
            security = listOf(planet.security, planet.securityMax),
            orbitalDefense = listOf(planet.orbitalDefense, planet.orbitalDefenseMax),
            fortress = listOf(planet.fortress, planet.fortressMax),
            tradeRoute = planet.tradeRoute,
            officerList = officerMap,
        )
    }

    companion object {
        fun resolveCode(code: String): String {
            if (!code.contains("_")) return code
            return code.substringAfterLast("_")
        }

        fun permissionToInt(permission: String): Int {
            return when (permission) {
                "normal" -> 0
                "auditor" -> 1
                "ambassador" -> 2
                else -> 0
            }
        }
    }
}
