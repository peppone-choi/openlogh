package com.openlogh.dto

data class FrontInfoResponse(
    val global: GlobalInfo,
    val officer: OfficerFrontInfo?,
    val faction: FactionFrontInfo?,
    val planet: PlanetFrontInfo?,
    val recentRecord: RecentRecordInfo,
    val aux: AuxInfo,
)

data class AuxInfo(
    val myLastVote: Long? = null,
)

data class GlobalInfo(
    val year: Int,
    val month: Int,
    val turnTerm: Int,
    val startyear: Int,
    val officerCount: List<List<Int>>,
    val onlineFactions: List<OnlineFactionInfo>,
    val onlineUserCnt: Int,
    val auctionCount: Int,
    val tournamentState: Int,
    val tournamentType: Int?,
    val tournamentTime: String?,
    val isTournamentActive: Boolean,
    val isTournamentApplicationOpen: Boolean,
    val isBettingActive: Boolean,
    val lastExecuted: String?,
    val isLocked: Boolean,
    val scenarioText: String,
    val realtimeMode: Boolean,
    val extendedOfficer: Int,
    val isFiction: Int,
    val npcMode: Int,
    val joinMode: String,
    val develCost: Int,
    val noticeMsg: Int,
    val apiLimit: Int,
    val officerCntLimit: Int,
    val serverCnt: Int,
    val lastVoteID: Int,
    val lastVote: Map<String, Any>?,
)

data class OnlineFactionInfo(
    val id: Long,
    val name: String,
    val color: String,
    val officerCount: Int,
)

data class FactionTypeInfo(
    val raw: String,
    val name: String,
    val pros: String,
    val cons: String,
)

data class FactionPopulationInfo(
    val planetCnt: Int,
    val now: Int,
    val max: Int,
)

data class FactionShipInfo(
    val officerCnt: Int,
    val now: Int,
    val max: Int,
)

data class TopChiefInfo(
    val rank: Int,
    val no: Long,
    val name: String,
    val npc: Int,
)

data class OfficerFrontInfo(
    val no: Long,
    val name: String,
    val picture: String,
    val imgsvr: Int,
    val faction: Long,
    val npc: Int,
    val planet: Long,
    val troop: Long,
    val rank: Int,
    val rankText: String,
    val stationedSystem: Int,
    val permission: Int,
    val lbonus: Int,
    val leadership: Int,
    val leadershipExp: Int,
    val command: Int,
    val commandExp: Int,
    val intelligence: Int,
    val intelligenceExp: Int,
    val politics: Int,
    val politicsExp: Int,
    val administration: Int,
    val administrationExp: Int,
    val experience: Int,
    val dedication: Int,
    val explevel: Int,
    val dedlevel: Int,
    val honorText: String,
    val dedLevelText: String,
    val taxRate: Int,
    val funds: Int,
    val supplies: Int,
    val ships: Int,
    val shipClass: String,
    val training: Int,
    val morale: Int,
    val flagship: String,
    val equipment: String,
    val engine: String,
    val accessory: String,
    val personal: String,
    val specialDomestic: String,
    val specialWar: String,
    val specage: Int,
    val specage2: Int,
    val age: Int,
    val injury: Int,
    val killturn: Int?,
    val belong: Int,
    val betray: Int,
    val blockState: Int,
    val defenceTrain: Int,
    val turntime: String,
    val recentWar: String?,
    val commandPoints: Int,
    val commandEndTime: String?,
    val ownerName: String?,
    val refreshScoreTotal: Int?,
    val refreshScore: Int?,
    val autorunLimit: Int,
    val reservedCommand: Map<String, Any>?,
    val fleetInfo: FleetInfo?,
    val dex1: Int,
    val dex2: Int,
    val dex3: Int,
    val dex4: Int,
    val dex5: Int,
    val warnum: Int,
    val killnum: Int,
    val deathnum: Int,
    val killships: Int,
    val deathships: Int,
    val firenum: Int,
)

data class FleetInfo(
    val leader: FleetLeaderInfo,
    val name: String,
)

data class FleetLeaderInfo(
    val planet: Long,
    val reservedCommand: Map<String, Any>?,
)

data class FactionFrontInfo(
    val id: Long,
    val full: Boolean,
    val name: String,
    val color: String,
    val factionRank: Int,
    val type: FactionTypeInfo,
    val funds: Int,
    val supplies: Int,
    val techLevel: Float,
    val militaryPower: Int,
    val officerCount: Int,
    val capital: Long?,
    val conscriptionRate: Int,
    val taxRate: Int,
    val population: FactionPopulationInfo,
    val ships: FactionShipInfo,
    val onlineGen: String,
    val notice: FactionNoticeInfo?,
    val topChiefs: Map<Int, TopChiefInfo?>,
    val diplomaticLimit: Int,
    val strategicCmdLimit: Int,
    val impossibleStrategicCommand: List<String>,
    val prohibitScout: Int,
    val prohibitWar: Int,
)

data class FactionNoticeInfo(
    val date: String,
    val msg: String,
    val author: String,
    val authorID: Long,
)

data class PlanetFactionInfo(
    val id: Long,
    val name: String,
    val color: String,
)

data class PlanetFrontInfo(
    val id: Long,
    val name: String,
    val level: Int,
    val region: Int,
    val factionInfo: PlanetFactionInfo,
    val approval: Int,
    val population: List<Int>,
    val production: List<Int>,
    val commerce: List<Int>,
    val security: List<Int>,
    val orbitalDefense: List<Int>,
    val fortress: List<Int>,
    val tradeRoute: Int?,
    val officerList: Map<Int, PlanetOfficerInfo?>,
)

data class PlanetOfficerInfo(
    val rank: Int,
    val name: String,
    val npc: Int,
)

data class RecentRecordInfo(
    val flushOfficer: Boolean,
    val flushGlobal: Boolean,
    val flushHistory: Boolean,
    val officer: List<RecordEntry>,
    val global: List<RecordEntry>,
    val history: List<RecordEntry>,
)

data class RecordEntry(
    val id: Long,
    val message: String,
    val date: String,
)
