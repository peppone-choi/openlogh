package com.opensam.dto

import java.time.OffsetDateTime

data class AdminGeneralAction(val type: String)

data class AdminUserAction(
    val type: String,
    val grade: Int? = null,
)

data class ResourceDistributionRequest(
    val gold: Int = 0,
    val rice: Int = 0,
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
)

data class NationStatistic(
    val nationId: Long, val name: String, val color: String, val level: Int,
    val gold: Int, val rice: Int, val tech: Float, val power: Int,
    val genCount: Int, val cityCount: Int, val totalCrew: Int, val totalPop: Int,
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

data class AdminGeneralSummary(
    val id: Long,
    val name: String,
    val nationId: Long,
    val crew: Int,
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
    val worldId: Long,
    val generalIds: List<Long>,
    val message: String,
)

data class GameVersionInfo(
    val commitSha: String,
    val gameVersion: String,
    val jarPath: String,
    val port: Int,
    val worldIds: List<Int>,
    val alive: Boolean,
    val pid: Long,
    val baseUrl: String,
    val containerId: String?,
    val imageTag: String?,
)
