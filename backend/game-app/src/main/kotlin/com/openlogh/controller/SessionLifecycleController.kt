package com.openlogh.controller

import com.openlogh.service.SessionLifecycleService
import com.openlogh.service.VictoryService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

data class SessionEndRequest(
    val reason: String? = null,
)

data class SessionRestartRequest(
    val scenarioCode: String,
)

data class SessionRankingDto(
    val rank: Int,
    val officerId: Long,
    val officerName: String,
    val factionId: Long,
    val factionName: String,
    val score: Int,
    val meritPoints: Int,
    val rankLevel: Int,
    val kills: Int,
    val territoryCaptured: Int,
    val isPlayer: Boolean,
)

@RestController
@RequestMapping("/api/v1/world/{sessionId}")
class SessionLifecycleController(
    private val sessionLifecycleService: SessionLifecycleService,
    private val victoryService: VictoryService,
) {
    /**
     * Admin-triggered session end.
     */
    @PostMapping("/end")
    fun endSession(
        @PathVariable sessionId: Long,
        @RequestBody(required = false) request: SessionEndRequest?,
    ): ResponseEntity<Any> {
        // Check for natural victory first
        val victoryResult = victoryService.checkVictoryConditions(sessionId)
        sessionLifecycleService.endSession(sessionId, victoryResult)
        return ResponseEntity.ok(mapOf(
            "ended" to true,
            "hasVictory" to (victoryResult != null),
        ))
    }

    /**
     * Get final rankings for a completed session.
     */
    @GetMapping("/rankings")
    fun getRankings(@PathVariable sessionId: Long): ResponseEntity<List<SessionRankingDto>> {
        val rankings = sessionLifecycleService.getRankings(sessionId)
        val dtos = rankings.map { ranking ->
            val stats = ranking.stats
            SessionRankingDto(
                rank = ranking.finalRank,
                officerId = ranking.officerId,
                officerName = ranking.officerName,
                factionId = ranking.factionId,
                factionName = (stats["factionName"] as? String) ?: "",
                score = ranking.score,
                meritPoints = ranking.meritPoints,
                rankLevel = (stats["rankLevel"] as? Number)?.toInt() ?: 0,
                kills = (stats["kills"] as? Number)?.toInt() ?: 0,
                territoryCaptured = (stats["territoryCaptured"] as? Number)?.toInt() ?: 0,
                isPlayer = (stats["isPlayer"] as? Boolean) ?: false,
            )
        }
        return ResponseEntity.ok(dtos)
    }

    /**
     * Restart session with new scenario. Only for ended sessions.
     */
    @PostMapping("/restart")
    fun restartSession(
        @PathVariable sessionId: Long,
        @RequestBody request: SessionRestartRequest,
    ): ResponseEntity<Any> {
        sessionLifecycleService.restartSession(sessionId, request.scenarioCode)
        return ResponseEntity.ok(mapOf(
            "restarted" to true,
            "scenarioCode" to request.scenarioCode,
        ))
    }
}
