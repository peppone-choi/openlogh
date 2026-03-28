package com.openlogh.service

import com.openlogh.repository.FactionRepository
import com.openlogh.repository.OfficerRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional

/**
 * D-01: 3:2 faction ratio hard cap enforcement.
 * No single faction can exceed 60% of total player-controlled officers.
 */
@Service
class FactionJoinService(
    private val officerRepository: OfficerRepository,
    private val factionRepository: FactionRepository,
) {
    companion object {
        private val log = LoggerFactory.getLogger(FactionJoinService::class.java)
        /** 3:2 ratio = max 60% in one faction */
        const val MAX_FACTION_RATIO_NUMERATOR = 3
        const val MAX_FACTION_RATIO_DENOMINATOR = 5
    }

    /**
     * Check if a player can join the target faction.
     * Uses SERIALIZABLE isolation to prevent TOCTOU race on concurrent joins.
     */
    @Transactional(isolation = Isolation.SERIALIZABLE, readOnly = true)
    fun canJoinFaction(sessionId: Long, targetFactionId: Long): FactionJoinResult {
        val playerOfficers = officerRepository.findBySessionId(sessionId)
            .filter { it.userId != null }
        val totalPlayers = playerOfficers.size
        val targetCount = playerOfficers.count { it.factionId == targetFactionId }

        // First player or solo player can always join any faction
        if (totalPlayers <= 1) {
            return FactionJoinResult(allowed = true)
        }

        // After join, target faction would have (targetCount + 1) out of (totalPlayers + 1)
        // Check: (targetCount + 1) * DENOMINATOR <= (totalPlayers + 1) * NUMERATOR
        val wouldExceed = (targetCount + 1) * MAX_FACTION_RATIO_DENOMINATOR >
            (totalPlayers + 1) * MAX_FACTION_RATIO_NUMERATOR
        if (wouldExceed) {
            val faction = factionRepository.findById(targetFactionId).orElse(null)
            val name = faction?.name ?: "해당 진영"
            log.info(
                "Faction join blocked: session={}, faction={}, targetCount={}, totalPlayers={}",
                sessionId, name, targetCount, totalPlayers,
            )
            return FactionJoinResult(
                allowed = false,
                reason = "${name} 인원이 가득 찼습니다 -- 다른 진영에 참가하거나 자리가 날 때까지 기다려주세요",
            )
        }
        return FactionJoinResult(allowed = true)
    }

    /**
     * Get current faction counts for display in lobby UI (D-04).
     */
    @Transactional(readOnly = true)
    fun getFactionCounts(sessionId: Long): Map<Long, Int> {
        val playerOfficers = officerRepository.findBySessionId(sessionId)
            .filter { it.userId != null }
        return playerOfficers.groupBy { it.factionId }
            .mapValues { (_, officers) -> officers.size }
    }

    data class FactionJoinResult(val allowed: Boolean, val reason: String? = null)
}
