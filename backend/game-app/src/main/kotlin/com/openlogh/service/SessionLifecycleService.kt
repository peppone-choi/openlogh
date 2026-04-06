package com.openlogh.service

import com.openlogh.entity.SessionRanking
import com.openlogh.repository.FactionRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.PlanetRepository
import com.openlogh.repository.SessionRankingRepository
import com.openlogh.repository.SessionStateRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

/**
 * Manages session lifecycle: end sequence, rankings, and restart.
 */
@Service
class SessionLifecycleService(
    private val sessionStateRepository: SessionStateRepository,
    private val officerRepository: OfficerRepository,
    private val factionRepository: FactionRepository,
    private val planetRepository: PlanetRepository,
    private val sessionRankingRepository: SessionRankingRepository,
    private val victoryService: VictoryService,
    private val gameEventService: GameEventService,
) {
    private val logger = LoggerFactory.getLogger(SessionLifecycleService::class.java)

    /**
     * End a session: freeze game state, calculate rankings, record hall of fame.
     */
    @Transactional
    fun endSession(sessionId: Long, victoryResult: VictoryResult? = null) {
        val world = sessionStateRepository.findById(sessionId.toShort()).orElseThrow {
            IllegalArgumentException("Session not found: $sessionId")
        }

        if (world.meta["status"] == "ENDED") {
            throw IllegalStateException("Session already ended: $sessionId")
        }

        // Process victory if provided
        if (victoryResult != null) {
            victoryService.processVictory(sessionId, victoryResult)
        } else {
            world.meta["status"] = "ENDED"
            sessionStateRepository.save(world)
        }

        // Calculate and persist rankings
        val rankings = calculateRankings(sessionId)
        persistRankings(sessionId, rankings)

        logger.info("Session {} ended with {} ranked officers", sessionId, rankings.size)
    }

    /**
     * Calculate final rankings for all officers in the session.
     * Score = meritPoints * 2 + rank * 1000 + kills * 50 + territoryCaptured * 200
     */
    fun calculateRankings(sessionId: Long): List<SessionRankingData> {
        val officers = officerRepository.findBySessionId(sessionId)
        val factions = factionRepository.findBySessionId(sessionId).associateBy { it.id }

        return officers.map { officer ->
            val kills = (officer.meta["kills"] as? Number)?.toInt() ?: 0
            val territoryCaptured = (officer.meta["territoryCaptured"] as? Number)?.toInt() ?: 0
            val rankLevel = officer.officerLevel.toInt()

            val score = officer.meritPoints * 2 +
                rankLevel * 1000 +
                kills * 50 +
                territoryCaptured * 200

            SessionRankingData(
                officerId = officer.id,
                officerName = officer.name,
                factionId = officer.factionId,
                factionName = factions[officer.factionId]?.name ?: "재야",
                rankLevel = rankLevel,
                score = score,
                meritPoints = officer.meritPoints,
                kills = kills,
                territoryCaptured = territoryCaptured,
                isPlayer = officer.npcState.toInt() == 0,
            )
        }.sortedByDescending { it.score }
    }

    private fun persistRankings(sessionId: Long, rankings: List<SessionRankingData>) {
        // Clear existing rankings for this session
        val existing = sessionRankingRepository.findBySessionId(sessionId)
        if (existing.isNotEmpty()) {
            sessionRankingRepository.deleteAll(existing)
        }

        val entities = rankings.mapIndexed { index, data ->
            SessionRanking(
                sessionId = sessionId,
                officerId = data.officerId,
                officerName = data.officerName,
                factionId = data.factionId,
                finalRank = index + 1,
                score = data.score,
                meritPoints = data.meritPoints,
                stats = mutableMapOf(
                    "rankLevel" to data.rankLevel,
                    "kills" to data.kills,
                    "territoryCaptured" to data.territoryCaptured,
                    "factionName" to data.factionName,
                    "isPlayer" to data.isPlayer,
                ),
            )
        }

        sessionRankingRepository.saveAll(entities)
    }

    /**
     * Get rankings for a completed session.
     */
    fun getRankings(sessionId: Long): List<SessionRanking> {
        return sessionRankingRepository.findBySessionIdOrderByScoreDesc(sessionId)
    }

    /**
     * Restart a session with a new scenario.
     * Preserves player accounts but resets all game state.
     */
    @Transactional
    fun restartSession(sessionId: Long, scenarioCode: String) {
        val world = sessionStateRepository.findById(sessionId.toShort()).orElseThrow {
            IllegalArgumentException("Session not found: $sessionId")
        }

        if (world.meta["status"] != "ENDED") {
            throw IllegalStateException("Can only restart ended sessions")
        }

        // Reset session state
        world.meta["status"] = "ACTIVE"
        world.meta.remove("victoryResult")
        world.victoryResult = null
        world.status = "ACTIVE"
        world.scenarioCode = scenarioCode
        world.tickCount = 0
        world.gameTimeSec = 0
        world.updatedAt = OffsetDateTime.now()
        sessionStateRepository.save(world)

        // Note: Full game reset (officers, planets, factions, fleets) would be handled
        // by ScenarioService.initializeWorld() called after this reset.
        // The gateway orchestrator handles spawning the game-app with the new scenario.

        logger.info("Session {} restarted with scenario {}", sessionId, scenarioCode)

        gameEventService.broadcastWorldUpdate(sessionId.toLong(), mapOf(
            "type" to "SESSION_RESTART",
            "scenarioCode" to scenarioCode,
        ))
    }
}

/**
 * Data class for ranking calculation (not persisted directly).
 */
data class SessionRankingData(
    val officerId: Long,
    val officerName: String,
    val factionId: Long,
    val factionName: String,
    val rankLevel: Int,
    val score: Int,
    val meritPoints: Int,
    val kills: Int,
    val territoryCaptured: Int,
    val isPlayer: Boolean,
)
