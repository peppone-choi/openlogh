package com.openlogh.engine

import com.openlogh.repository.FactionRepository
import com.openlogh.service.FezzanService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * AI controller for the Fezzan NPC faction.
 * Evaluates loan offers, adjusts intelligence pricing, and manages Fezzan economy.
 */
@Service
class FezzanAiService(
    private val fezzanService: FezzanService,
    private val factionRepository: FactionRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        /** Evaluate loan offers every N ticks */
        const val LOAN_EVAL_INTERVAL = 100
        /** Loan amount as percentage of Fezzan's funds */
        const val LOAN_AMOUNT_MIN_PERCENT = 0.20f
        const val LOAN_AMOUNT_MAX_PERCENT = 0.40f
        /** Faction funds threshold for loan offer (below average) */
        const val FUNDS_THRESHOLD_PERCENT = 0.50f
    }

    /**
     * Main AI tick processor. Called every tick by TickEngine.
     * @param sessionId the game session
     * @param tickCount current tick number for interval checks
     */
    @Transactional
    fun processFezzanTick(sessionId: Long, tickCount: Long) {
        if (!fezzanService.isFezzanOperational(sessionId)) return

        // Evaluate loan offers periodically
        if (tickCount % LOAN_EVAL_INTERVAL == 0L) {
            evaluateLoanOffers(sessionId)
        }
    }

    /**
     * Evaluate which factions could benefit from a loan offer.
     */
    private fun evaluateLoanOffers(sessionId: Long) {
        val factions = factionRepository.findBySessionId(sessionId)
        val fezzanFaction = factions.find { it.factionType == "fezzan" } ?: return
        val playerFactions = factions.filter { it.factionType != "fezzan" }

        if (playerFactions.isEmpty()) return

        val avgFunds = playerFactions.map { it.funds.toLong() }.average()

        for (faction in playerFactions) {
            // Offer loans to factions below average funds
            if (faction.funds < avgFunds * FUNDS_THRESHOLD_PERCENT) {
                val loanAmount = (fezzanFaction.funds * LOAN_AMOUNT_MIN_PERCENT).toInt()
                    .coerceAtLeast(100)

                // Check if loan would be within limits
                val currentDebt = fezzanService.getTotalDebt(sessionId, faction.id)
                val maxDebt = faction.funds * FezzanService.MAX_DEBT_MULTIPLIER
                if (currentDebt + loanAmount <= maxDebt && loanAmount > 0) {
                    try {
                        fezzanService.offerLoan(sessionId, faction.id, loanAmount)
                        log.info("[Session {}] Fezzan AI offered loan of {} to faction {}",
                            sessionId, loanAmount, faction.id)
                    } catch (e: Exception) {
                        log.debug("[Session {}] Fezzan AI loan offer failed for faction {}: {}",
                            sessionId, faction.id, e.message)
                    }
                }
            }
        }
    }
}
