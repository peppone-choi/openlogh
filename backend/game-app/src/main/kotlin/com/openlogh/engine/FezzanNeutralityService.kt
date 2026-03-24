package com.openlogh.engine

import com.openlogh.entity.Faction
import com.openlogh.repository.DiplomacyRepository
import com.openlogh.repository.FactionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Feature 5.12 - Fezzan Neutrality Penalty
 *
 * When a faction attacks or enters Fezzan territory:
 *   - Diplomatic penalty with both factions
 *   - Economic sanctions: trade income -50% for 6 months
 *   - Stored in faction.meta["fezzanPenalty"]
 */
@Service
class FezzanNeutralityService(
    private val factionRepository: FactionRepository,
    private val diplomacyRepository: DiplomacyRepository,
) {
    companion object {
        private val log = LoggerFactory.getLogger(FezzanNeutralityService::class.java)
        private const val PENALTY_DURATION_MONTHS = 6
        private const val TRADE_PENALTY_RATE = 0.50  // -50%
        private const val DIPLOMACY_PENALTY = -30
    }

    /**
     * Apply Fezzan neutrality violation penalty to the aggressor faction.
     * @param sessionId game session
     * @param aggressorFactionId the faction that violated Fezzan neutrality
     * @param fezzanFactionId Fezzan's faction id
     * @param currentMonth current game month (for duration tracking)
     * @param currentYear current game year
     */
    @Transactional
    fun applyNeutralityViolation(
        sessionId: Long,
        aggressorFactionId: Long,
        fezzanFactionId: Long,
        currentYear: Short,
        currentMonth: Short,
    ) {
        val aggressor = factionRepository.findById(aggressorFactionId).orElse(null) ?: return
        val fezzan = factionRepository.findById(fezzanFactionId).orElse(null) ?: return

        // Calculate penalty expiry: currentMonth + PENALTY_DURATION_MONTHS
        val expiryMonth = currentMonth + PENALTY_DURATION_MONTHS
        val expiryYear = if (expiryMonth > 12) currentYear + 1 else currentYear.toInt()
        val normalizedExpiryMonth = if (expiryMonth > 12) expiryMonth - 12 else expiryMonth

        // Store penalty in faction meta
        @Suppress("UNCHECKED_CAST")
        val penalties = (aggressor.meta["fezzanPenalty"] as? MutableMap<String, Any>) ?: mutableMapOf()
        penalties["active"] = true
        penalties["tradeRatePenalty"] = TRADE_PENALTY_RATE
        penalties["expiryYear"] = expiryYear
        penalties["expiryMonth"] = normalizedExpiryMonth
        penalties["diplomacyPenalty"] = DIPLOMACY_PENALTY
        aggressor.meta["fezzanPenalty"] = penalties

        factionRepository.save(aggressor)

        // Apply diplomatic penalty: reduce affinity in diplomacy records
        applyDiplomaticPenalty(sessionId, aggressorFactionId, fezzanFactionId)
        // Also penalize relations with all other active factions
        val allFactions = factionRepository.findBySessionId(sessionId)
        for (faction in allFactions) {
            if (faction.id != aggressorFactionId && faction.id != fezzanFactionId) {
                applyDiplomaticPenalty(sessionId, aggressorFactionId, faction.id)
            }
        }

        log.info(
            "Fezzan neutrality violation: aggressor={} penalty applied, expires year={} month={}",
            aggressorFactionId, expiryYear, normalizedExpiryMonth,
        )
    }

    /**
     * Called monthly during turn processing to decay/expire penalties.
     */
    @Transactional
    fun processPenaltyDecay(sessionId: Long, currentYear: Int, currentMonth: Int) {
        val factions = factionRepository.findBySessionId(sessionId)
        for (faction in factions) {
            val penalty = faction.meta["fezzanPenalty"] as? MutableMap<*, *> ?: continue
            val active = penalty["active"] as? Boolean ?: false
            if (!active) continue

            val expiryYear = (penalty["expiryYear"] as? Number)?.toInt() ?: 0
            val expiryMonth = (penalty["expiryMonth"] as? Number)?.toInt() ?: 0

            val expired = currentYear > expiryYear ||
                (currentYear == expiryYear && currentMonth >= expiryMonth)

            if (expired) {
                @Suppress("UNCHECKED_CAST")
                val mutablePenalty = faction.meta["fezzanPenalty"] as? MutableMap<String, Any> ?: continue
                mutablePenalty["active"] = false
                mutablePenalty.remove("tradeRatePenalty")
                faction.meta["fezzanPenalty"] = mutablePenalty
                factionRepository.save(faction)
                log.info("Fezzan penalty expired for faction {}", faction.id)
            }
        }
    }

    /**
     * Check if a faction currently has an active Fezzan penalty.
     */
    fun hasFezzanPenalty(faction: Faction): Boolean {
        val penalty = faction.meta["fezzanPenalty"] as? Map<*, *> ?: return false
        return penalty["active"] as? Boolean ?: false
    }

    /**
     * Get the trade penalty multiplier (1.0 = no penalty, 0.5 = 50% reduction).
     */
    fun getTradeMultiplier(faction: Faction): Double {
        if (!hasFezzanPenalty(faction)) return 1.0
        val penalty = faction.meta["fezzanPenalty"] as? Map<*, *> ?: return 1.0
        val rate = (penalty["tradeRatePenalty"] as? Number)?.toDouble() ?: TRADE_PENALTY_RATE
        return 1.0 - rate
    }

    private fun applyDiplomaticPenalty(sessionId: Long, factionA: Long, factionB: Long) {
        val relations = diplomacyRepository.findActiveRelationsBetween(sessionId, factionA, factionB)
        for (relation in relations) {
            val currentPenalty = (relation.meta["diplomacyModifier"] as? Number)?.toInt() ?: 0
            relation.meta["diplomacyModifier"] = currentPenalty + DIPLOMACY_PENALTY
            diplomacyRepository.save(relation)
        }
    }
}
