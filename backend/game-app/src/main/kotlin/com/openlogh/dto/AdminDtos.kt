package com.openlogh.dto

import java.time.OffsetDateTime

data class AdminOfficerAction(val type: String)

data class AdminUserAction(
    val type: String,
    val grade: Int? = null,
)

data class ResourceDistributionRequest(
    val funds: Int = 0,
    val supplies: Int = 0,
    val target: String = "all",
)

data class TimeControlRequest(
    val year: Int? = null,
    val month: Int? = null,
    val startYear: Int? = null,
    val locked: Boolean? = null,
    val turnTerm: Int? = null,
    val distribute: ResourceDistributionRequest? = null,
    val auctionSync: Boolean? = null,
    val auctionCloseMinutes: Int? = null,
    val opentime: String? = null,
    val startTime: String? = null,
    val reserveOpen: String? = null,
    val preReserveOpen: String? = null,
)

data class FactionStatistic(
    val factionId: Long, val name: String, val color: String, val factionRank: Int,
    val funds: Int, val supplies: Int, val techLevel: Float, val militaryPower: Int,
    val officerCount: Int, val planetCount: Int, val totalShips: Int, val totalPopulation: Int,
)

data class AdminDashboard(
    val worldCount: Int,
    val currentWorld: AdminWorldInfo?,
)

data class AdminWorldInfo(
    val id: Short,
    val year: Short,
    val month: Short,
    val scenarioCode: String,
    val realtimeMode: Boolean,
    val tickSeconds: Int,
    val commandPointRegenRate: Int,
    val config: MutableMap<String, Any>,
)

data class AdminOfficerSummary(
    val id: Long,
    val name: String,
    val factionId: Long,
    val ships: Int,
    val experience: Int,
    val npcState: Int,
    val blockState: Int,
    val killTurn: Int?,
)

data class AdminUserSummary(
    val id: Long,
    val loginId: String,
    val displayName: String,
    val role: String,
    val grade: Int,
    val createdAt: OffsetDateTime,
    val lastLoginAt: OffsetDateTime?,
)

data class BroadcastRequest(
    val sessionId: Long,
    val officerIds: List<Long>,
    val message: String,
)

data class GameVersionInfo(
    val commitSha: String,
    val gameVersion: String,
    val jarPath: String,
    val port: Int,
    val sessionIds: List<Int>,
    val alive: Boolean,
    val pid: Long,
    val baseUrl: String,
    val containerId: String?,
    val imageTag: String?,
)
