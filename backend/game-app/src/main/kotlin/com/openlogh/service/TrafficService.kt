package com.openlogh.service

import com.openlogh.repository.OfficerAccessLogRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.TrafficSnapshotRepository
import org.springframework.stereotype.Service

@Service
class TrafficService(
    private val trafficSnapshotRepo: TrafficSnapshotRepository,
    private val accessLogRepo: OfficerAccessLogRepository,
    private val officerRepo: OfficerRepository,
) {
    data class TrafficResponse(
        val recentTraffic: List<TrafficEntry>,
        val maxRefresh: Int,
        val maxOnline: Int,
        val topRefreshers: List<TopRefresher>,
        val totalRefresh: Long,
        val totalRefreshScoreTotal: Long,
    )

    data class TrafficEntry(
        val year: Int,
        val month: Int,
        val refresh: Int,
        val online: Int,
        val date: String,
    )

    data class TopRefresher(
        val name: String,
        val refresh: Int,
        val refreshScoreTotal: Int,
    )

    fun getTraffic(sessionId: Long): TrafficResponse {
        val snapshots = trafficSnapshotRepo.findTop30BySessionIdOrderByRecordedAtDesc(sessionId).reversed()
        val maxRefresh = trafficSnapshotRepo.findMaxRefresh(sessionId).coerceAtLeast(1)
        val maxOnline = trafficSnapshotRepo.findMaxOnline(sessionId).coerceAtLeast(1)

        val recentTraffic = snapshots.map { s ->
            TrafficEntry(
                year = s.year.toInt(),
                month = s.month.toInt(),
                refresh = s.refresh,
                online = s.online,
                date = s.recordedAt.toString(),
            )
        }

        // Top refreshers from officer_access_log
        val topLogs = accessLogRepo.findTopRefreshersBySessionId(sessionId).take(5)
        val officerIds = topLogs.map { it.officerId }.toSet()
        val officerNames = officerRepo.findAllById(officerIds).associate { it.id to it.name }

        val topRefreshers = topLogs.map { log ->
            TopRefresher(
                name = officerNames[log.officerId] ?: "#${log.officerId}",
                refresh = log.refresh,
                refreshScoreTotal = log.refreshScoreTotal,
            )
        }

        val totalRefresh = accessLogRepo.sumRefreshBySessionId(sessionId)
        val totalRefreshScoreTotal = accessLogRepo.sumRefreshScoreTotalBySessionId(sessionId)

        return TrafficResponse(
            recentTraffic = recentTraffic,
            maxRefresh = maxRefresh,
            maxOnline = maxOnline,
            topRefreshers = topRefreshers,
            totalRefresh = totalRefresh,
            totalRefreshScoreTotal = totalRefreshScoreTotal,
        )
    }
}
