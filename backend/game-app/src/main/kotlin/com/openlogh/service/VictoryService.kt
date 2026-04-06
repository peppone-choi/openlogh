package com.openlogh.service

import com.openlogh.repository.FactionRepository
import com.openlogh.repository.PlanetRepository
import com.openlogh.repository.SessionStateRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Victory condition enumeration.
 * - CAPITAL_CAPTURE: Enemy faction's capital planet captured by another faction
 * - SYSTEM_THRESHOLD: Enemy faction holds 3 or fewer star systems
 * - TIME_LIMIT: Game date reaches UC801.7.27, winner determined by population comparison
 */
enum class VictoryCondition(val korean: String) {
    CAPITAL_CAPTURE("수도 함락"),
    SYSTEM_THRESHOLD("성계 열세"),
    TIME_LIMIT("시간 제한"),
}

/**
 * 4-tier victory evaluation.
 * - DECISIVE: Complete domination (capital captured or enemy at 0 systems)
 * - LIMITED: Clear advantage (enemy at 1-3 systems)
 * - LOCAL: Marginal advantage (time limit with >2x population)
 * - DEFEAT: No clear winner from losing side's perspective
 */
enum class VictoryTier(val korean: String, val score: Int) {
    DECISIVE("결정적 승리", 100),
    LIMITED("한정적 승리", 70),
    LOCAL("국지적 승리", 40),
    DEFEAT("패배", 0),
}

/**
 * Result of victory condition evaluation.
 */
data class VictoryResult(
    val condition: VictoryCondition,
    val tier: VictoryTier,
    val winnerFactionId: Long,
    val loserFactionId: Long,
    val winnerName: String = "",
    val loserName: String = "",
    val stats: Map<String, Any> = emptyMap(),
)

@Service
class VictoryService(
    private val sessionStateRepository: SessionStateRepository,
    private val factionRepository: FactionRepository,
    private val planetRepository: PlanetRepository,
    private val gameEventService: GameEventService,
) {
    private val logger = LoggerFactory.getLogger(VictoryService::class.java)

    /**
     * Check all victory conditions for a session.
     * Returns VictoryResult if a victory condition is met, null otherwise.
     * Called periodically by the tick engine (every 60 ticks = 1 minute).
     */
    fun checkVictoryConditions(sessionId: Long): VictoryResult? {
        val world = sessionStateRepository.findById(sessionId.toShort()).orElse(null) ?: return null

        // Skip if session already ended
        if (world.meta["status"] == "ENDED") return null

        val factions = factionRepository.findBySessionId(sessionId)
            .filter { it.factionRank > 0 } // Only active factions (not wanderers/rebels with rank 0)

        if (factions.size < 2) return null // Need at least 2 factions

        // Check conditions in priority order
        checkCapitalCapture(sessionId, factions)?.let { return it }
        checkSystemThreshold(sessionId, factions)?.let { return it }
        checkTimeLimit(world, sessionId, factions)?.let { return it }

        return null
    }

    /**
     * VIC-01: Enemy capital planet captured by another faction.
     */
    private fun checkCapitalCapture(sessionId: Long, factions: List<com.openlogh.entity.Faction>): VictoryResult? {
        val planets = planetRepository.findBySessionId(sessionId)
        val planetOwnership = planets.associateBy { it.id }

        for (faction in factions) {
            val capitalId = faction.capitalPlanetId ?: continue
            val capitalPlanet = planetOwnership[capitalId] ?: continue

            // Capital is owned by a different faction
            if (capitalPlanet.factionId != faction.id && capitalPlanet.factionId != 0L) {
                val winner = factions.find { it.id == capitalPlanet.factionId } ?: continue
                logger.info("Victory! Capital capture: {} captured {}'s capital",
                    winner.name, faction.name)

                return VictoryResult(
                    condition = VictoryCondition.CAPITAL_CAPTURE,
                    tier = VictoryTier.DECISIVE,
                    winnerFactionId = winner.id,
                    loserFactionId = faction.id,
                    winnerName = winner.name,
                    loserName = faction.name,
                    stats = mapOf(
                        "capturedCapitalId" to capitalId,
                        "capturedCapitalName" to capitalPlanet.name,
                    ),
                )
            }
        }
        return null
    }

    /**
     * VIC-02: Enemy faction holds 3 or fewer star systems (including capital).
     */
    private fun checkSystemThreshold(sessionId: Long, factions: List<com.openlogh.entity.Faction>): VictoryResult? {
        val planets = planetRepository.findBySessionId(sessionId)

        for (faction in factions) {
            val ownedPlanets = planets.count { it.factionId == faction.id }

            if (ownedPlanets <= 3) {
                // Find the strongest other faction as winner
                val otherFactions = factions.filter { it.id != faction.id }
                val winner = otherFactions.maxByOrNull { f ->
                    planets.count { it.factionId == f.id }
                } ?: continue

                val tier = if (ownedPlanets == 0) VictoryTier.DECISIVE else VictoryTier.LIMITED

                logger.info("Victory! System threshold: {} has only {} systems",
                    faction.name, ownedPlanets)

                return VictoryResult(
                    condition = VictoryCondition.SYSTEM_THRESHOLD,
                    tier = tier,
                    winnerFactionId = winner.id,
                    loserFactionId = faction.id,
                    winnerName = winner.name,
                    loserName = faction.name,
                    stats = mapOf(
                        "loserSystemCount" to ownedPlanets,
                        "winnerSystemCount" to planets.count { it.factionId == winner.id },
                    ),
                )
            }
        }
        return null
    }

    /**
     * VIC-03: Time limit reached (UC801.7.27) - compare total population.
     */
    private fun checkTimeLimit(
        world: com.openlogh.entity.SessionState,
        sessionId: Long,
        factions: List<com.openlogh.entity.Faction>,
    ): VictoryResult? {
        val year = world.currentYear.toInt()
        val month = world.currentMonth.toInt()

        // UC801.7.27 = year 801, month 7
        if (year < 801 || (year == 801 && month < 7)) return null

        val planets = planetRepository.findBySessionId(sessionId)

        // Calculate total population per faction
        val populationByFaction = factions.associate { faction ->
            faction.id to planets.filter { it.factionId == faction.id }.sumOf { it.population.toLong() }
        }

        val sorted = populationByFaction.entries.sortedByDescending { it.value }
        if (sorted.size < 2) return null

        val winnerId = sorted[0].key
        val loserId = sorted[1].key
        val winnerPop = sorted[0].value
        val loserPop = sorted[1].value

        val winner = factions.find { it.id == winnerId } ?: return null
        val loser = factions.find { it.id == loserId } ?: return null

        val ratio = if (loserPop > 0) winnerPop.toDouble() / loserPop else Double.MAX_VALUE
        val tier = if (ratio > 2.0) VictoryTier.LOCAL else VictoryTier.DEFEAT

        logger.info("Victory! Time limit: {} pop={} vs {} pop={} (ratio={})",
            winner.name, winnerPop, loser.name, loserPop, ratio)

        return VictoryResult(
            condition = VictoryCondition.TIME_LIMIT,
            tier = tier,
            winnerFactionId = winnerId,
            loserFactionId = loserId,
            winnerName = winner.name,
            loserName = loser.name,
            stats = mapOf(
                "winnerPopulation" to winnerPop,
                "loserPopulation" to loserPop,
                "populationRatio" to ratio,
                "gameYear" to year,
                "gameMonth" to month,
            ),
        )
    }

    /**
     * Process victory: end session and broadcast result.
     */
    @Transactional
    fun processVictory(sessionId: Long, result: VictoryResult) {
        val world = sessionStateRepository.findById(sessionId.toShort()).orElse(null) ?: return

        // Store victory result
        world.meta["status"] = "ENDED"
        world.meta["victoryResult"] = mapOf(
            "condition" to result.condition.name,
            "tier" to result.tier.name,
            "tierKorean" to result.tier.korean,
            "conditionKorean" to result.condition.korean,
            "winnerFactionId" to result.winnerFactionId,
            "loserFactionId" to result.loserFactionId,
            "winnerName" to result.winnerName,
            "loserName" to result.loserName,
            "stats" to result.stats,
        )
        sessionStateRepository.save(world)

        // Broadcast victory event via WebSocket
        gameEventService.broadcastWorldUpdate(sessionId, mapOf(
            "type" to "VICTORY",
            "condition" to result.condition.name,
            "tier" to result.tier.name,
            "tierKorean" to result.tier.korean,
            "conditionKorean" to result.condition.korean,
            "winnerFactionId" to result.winnerFactionId,
            "loserFactionId" to result.loserFactionId,
            "winnerName" to result.winnerName,
            "loserName" to result.loserName,
        ))

        logger.info("Session {} ended: {} - {} ({} defeated {})",
            sessionId, result.condition.korean, result.tier.korean,
            result.winnerName, result.loserName)
    }
}
