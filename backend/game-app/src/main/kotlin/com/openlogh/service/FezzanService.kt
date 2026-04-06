package com.openlogh.service

import com.openlogh.entity.FezzanLoan
import com.openlogh.model.IntelligenceType
import com.openlogh.repository.FactionRepository
import com.openlogh.repository.FezzanLoanRepository
import com.openlogh.repository.FleetRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.PlanetRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

/**
 * Manages Fezzan NPC faction systems:
 * - Loan system with interest and default mechanics
 * - Intelligence market
 * - Trade route bonuses
 * - Fezzan ending (debt domination)
 */
@Service
class FezzanService(
    private val fezzanLoanRepository: FezzanLoanRepository,
    private val factionRepository: FactionRepository,
    private val planetRepository: PlanetRepository,
    private val officerRepository: OfficerRepository,
    private val fleetRepository: FleetRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        /** Base interest rate for first loan */
        const val BASE_INTEREST_RATE = 0.05f
        /** Additional interest per existing unpaid loan */
        const val INTEREST_PER_EXISTING_LOAN = 0.02f
        /** Loan due period in seconds (approx 30 game-days at 24x speed) */
        const val LOAN_DUE_SECONDS = 108000L
        /** Max total debt multiplier relative to faction funds */
        const val MAX_DEBT_MULTIPLIER = 3
        /** Number of defaulted loans that triggers Fezzan ending */
        const val FEZZAN_ENDING_DEFAULT_COUNT = 3
        /** Trade route commerce bonus */
        const val TRADE_ROUTE_BONUS = 0.20f
        /** Approval penalty per defaulted loan per tick */
        const val DEFAULT_APPROVAL_PENALTY = 1
    }

    // ========== Loan System ==========

    /**
     * Issue a loan from Fezzan to a faction.
     */
    @Transactional
    fun offerLoan(sessionId: Long, borrowerFactionId: Long, amount: Int): FezzanLoan {
        val faction = factionRepository.findById(borrowerFactionId).orElseThrow {
            IllegalArgumentException("Faction not found: $borrowerFactionId")
        }

        // Calculate interest rate based on existing loans
        val existingLoans = fezzanLoanRepository.findBySessionIdAndBorrowerFactionIdAndRepaidAtIsNull(
            sessionId, borrowerFactionId
        )
        val interestRate = BASE_INTEREST_RATE + (existingLoans.size * INTEREST_PER_EXISTING_LOAN)

        // Check max debt
        val currentDebt = existingLoans.sumOf { it.remainingDebt }
        val maxDebt = faction.funds * MAX_DEBT_MULTIPLIER
        if (currentDebt + amount > maxDebt) {
            throw IllegalStateException("Loan would exceed maximum debt limit ($maxDebt)")
        }

        val loan = FezzanLoan(
            sessionId = sessionId,
            borrowerFactionId = borrowerFactionId,
            principal = amount,
            interestRate = interestRate,
            remainingDebt = amount,
            dueAt = OffsetDateTime.now().plusSeconds(LOAN_DUE_SECONDS),
        )

        // Credit the faction
        faction.funds += amount
        factionRepository.save(faction)

        val saved = fezzanLoanRepository.save(loan)
        log.info("[Session {}] Loan issued: {} funds to faction {} at {}% interest",
            sessionId, amount, borrowerFactionId, (interestRate * 100).toInt())
        return saved
    }

    /**
     * Repay a loan (partial or full).
     */
    @Transactional
    fun repayLoan(sessionId: Long, loanId: Long, amount: Int) {
        val loan = fezzanLoanRepository.findById(loanId).orElseThrow {
            IllegalArgumentException("Loan not found: $loanId")
        }
        if (loan.repaidAt != null) throw IllegalStateException("Loan already repaid")

        val faction = factionRepository.findById(loan.borrowerFactionId).orElseThrow {
            IllegalArgumentException("Faction not found: ${loan.borrowerFactionId}")
        }

        val repayAmount = amount.coerceAtMost(loan.remainingDebt).coerceAtMost(faction.funds)
        if (repayAmount <= 0) throw IllegalStateException("Insufficient funds to repay")

        faction.funds -= repayAmount
        loan.remainingDebt -= repayAmount

        if (loan.remainingDebt <= 0) {
            loan.repaidAt = OffsetDateTime.now()
            loan.isDefaulted = false
            log.info("[Session {}] Loan {} fully repaid by faction {}", sessionId, loanId, loan.borrowerFactionId)
        }

        factionRepository.save(faction)
        fezzanLoanRepository.save(loan)
    }

    /**
     * Process loan interest and default checks each tick.
     */
    @Transactional
    fun processLoanTick(sessionId: Long) {
        val activeLoans = fezzanLoanRepository.findBySessionIdAndRepaidAtIsNull(sessionId)
        val now = OffsetDateTime.now()

        for (loan in activeLoans) {
            // Apply daily interest (spread across ticks approximately)
            // Interest is applied once per ~3600 ticks (1 game-day)
            val daysSinceIssue = java.time.Duration.between(loan.issuedAt, now).seconds / 3600
            val expectedInterest = (loan.principal * loan.interestRate * daysSinceIssue / 30).toInt()
            val currentInterest = loan.remainingDebt - loan.principal
            if (expectedInterest > currentInterest) {
                loan.remainingDebt = loan.principal + expectedInterest
            }

            // Check default
            if (now.isAfter(loan.dueAt) && !loan.isDefaulted) {
                loan.isDefaulted = true
                log.info("[Session {}] Loan {} defaulted! Faction {} owes {}",
                    sessionId, loan.id, loan.borrowerFactionId, loan.remainingDebt)

                // Apply penalty: decrease approval on all faction planets
                val planets = planetRepository.findBySessionIdAndFactionId(sessionId, loan.borrowerFactionId)
                for (planet in planets) {
                    planet.approval = (planet.approval - DEFAULT_APPROVAL_PENALTY).coerceAtLeast(0f)
                }
                planetRepository.saveAll(planets)
            }

            fezzanLoanRepository.save(loan)
        }
    }

    /**
     * Check if Fezzan ending should trigger.
     */
    fun checkFezzanEnding(sessionId: Long): FezzanEndingResult {
        val allFactions = factionRepository.findBySessionId(sessionId)
        for (faction in allFactions) {
            if (faction.factionType == "fezzan") continue
            val defaults = fezzanLoanRepository.findBySessionIdAndBorrowerFactionIdAndIsDefaulted(
                sessionId, faction.id, true
            )
            if (defaults.size >= FEZZAN_ENDING_DEFAULT_COUNT) {
                return FezzanEndingResult(triggered = true, dominatedFactionId = faction.id)
            }
        }
        return FezzanEndingResult(triggered = false, dominatedFactionId = null)
    }

    /**
     * Get total outstanding debt for a faction.
     */
    fun getTotalDebt(sessionId: Long, factionId: Long): Int {
        return fezzanLoanRepository.findBySessionIdAndBorrowerFactionIdAndRepaidAtIsNull(sessionId, factionId)
            .sumOf { it.remainingDebt }
    }

    /**
     * Get active (unpaid) loans for a faction.
     */
    fun getActiveLoans(sessionId: Long, factionId: Long): List<FezzanLoan> {
        return fezzanLoanRepository.findBySessionIdAndBorrowerFactionIdAndRepaidAtIsNull(sessionId, factionId)
    }

    // ========== Intelligence Market ==========

    /**
     * Purchase intelligence from Fezzan.
     */
    @Transactional
    fun purchaseIntelligence(
        sessionId: Long,
        buyerFactionId: Long,
        targetFactionId: Long,
        type: IntelligenceType,
    ): Map<String, Any> {
        if (!isFezzanOperational(sessionId)) {
            throw IllegalStateException("Fezzan is no longer operational")
        }

        val faction = factionRepository.findById(buyerFactionId).orElseThrow {
            IllegalArgumentException("Faction not found: $buyerFactionId")
        }
        if (faction.funds < type.cost) {
            throw IllegalStateException("Insufficient funds (need ${type.cost}, have ${faction.funds})")
        }

        faction.funds -= type.cost
        factionRepository.save(faction)

        // Gather intelligence data
        val intel = when (type) {
            IntelligenceType.FLEET_POSITIONS -> {
                val fleets = fleetRepository.findBySessionIdAndFactionId(sessionId, targetFactionId)
                mapOf("type" to type.code, "data" to fleets.map {
                    mapOf("fleetId" to it.id, "planetId" to it.planetId, "units" to it.currentUnits)
                })
            }
            IntelligenceType.PLANET_RESOURCES -> {
                val planets = planetRepository.findBySessionIdAndFactionId(sessionId, targetFactionId)
                mapOf("type" to type.code, "data" to planets.map {
                    mapOf("planetId" to it.id, "name" to it.name, "population" to it.population, "production" to it.production, "commerce" to it.commerce)
                })
            }
            IntelligenceType.OFFICER_INFO -> {
                val officers = officerRepository.findBySessionIdAndFactionId(sessionId, targetFactionId)
                mapOf("type" to type.code, "data" to officers.take(20).map {
                    mapOf("officerId" to it.id, "name" to it.name, "rank" to it.officerLevel, "ships" to it.ships)
                })
            }
            IntelligenceType.MILITARY_POWER -> {
                val targetFaction = factionRepository.findById(targetFactionId).orElse(null)
                mapOf("type" to type.code, "data" to mapOf(
                    "militaryPower" to (targetFaction?.militaryPower ?: 0),
                    "officerCount" to (targetFaction?.officerCount ?: 0),
                ))
            }
            IntelligenceType.COUP_INTEL -> {
                mapOf("type" to type.code, "data" to mapOf("message" to "No active intelligence available"))
            }
        }

        log.info("[Session {}] Intelligence purchased: {} by faction {} against faction {}",
            sessionId, type.code, buyerFactionId, targetFactionId)
        return intel
    }

    /**
     * Check if Fezzan faction is still operational (not absorbed by Empire).
     */
    fun isFezzanOperational(sessionId: Long): Boolean {
        val factions = factionRepository.findBySessionId(sessionId)
        return factions.any { it.factionType == "fezzan" }
    }

    /**
     * Calculate trade route bonus for planets connected to Fezzan.
     * Returns the commerce bonus multiplier for a faction.
     */
    fun calculateTradeBonus(sessionId: Long, factionId: Long): Float {
        if (!isFezzanOperational(sessionId)) return 0f
        // Planets with tradeRoute > 0 connected to Fezzan get bonus
        val planets = planetRepository.findBySessionIdAndFactionId(sessionId, factionId)
        val connectedCount = planets.count { it.tradeRoute > 0 }
        return if (connectedCount > 0) TRADE_ROUTE_BONUS else 0f
    }
}

/**
 * Result of Fezzan ending check.
 */
data class FezzanEndingResult(
    val triggered: Boolean,
    val dominatedFactionId: Long?,
)
