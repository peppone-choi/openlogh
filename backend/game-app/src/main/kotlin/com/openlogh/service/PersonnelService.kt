package com.openlogh.service

import com.openlogh.entity.Officer
import com.openlogh.model.PositionCard
import com.openlogh.model.RankHeadcount
import com.openlogh.model.RankTitleResolver
import com.openlogh.repository.FactionRepository
import com.openlogh.repository.OfficerRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Handles manual personnel actions: promotion, demotion, and personnel info queries.
 *
 * Personnel authority (gin7 p.33-36):
 * - Tier 10 promotions: Emperor (EMPEROR) / Supreme Council (SUPREME_COUNCIL_CHAIR)
 * - Tier 5-9 promotions: Military Affairs Secretary (MILITARY_AFFAIRS_SECRETARY) /
 *   Defense Committee Chair (COUNCIL_DEFENSE_CHAIR)
 * - Tier 0-4 promotions: HR Bureau Chief (MILITARY_HR_CHIEF) /
 *   Defense Dept Chief (DEFENSE_DEPT_CHIEF)
 */
@Service
class PersonnelService(
    private val officerRepository: OfficerRepository,
    private val factionRepository: FactionRepository,
    private val rankLadderService: RankLadderService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Position cards that grant promotion authority at different rank tiers.
     *
     * For a target rank tier, the promoter must hold one of the listed cards.
     */
    companion object {
        // Cards for promoting to tier 10 (Reichsmarschall / Fleet Admiral)
        private val SOVEREIGN_CARDS = setOf(
            PositionCard.EMPEROR,
        )

        // Cards for promoting to tier 5-9 (Commodore ~ Fleet Admiral tier 9)
        private val MINISTER_CARDS = setOf(
            PositionCard.EMPEROR,
            PositionCard.SUPREME_COMMANDER,
            PositionCard.MILITARY_AFFAIRS_SECRETARY,
            PositionCard.COUNCIL_DEFENSE_CHAIR,
        )

        // Cards for promoting to tier 0-4 (Sub-Lieutenant ~ Captain)
        private val HR_CARDS = setOf(
            PositionCard.EMPEROR,
            PositionCard.SUPREME_COMMANDER,
            PositionCard.MILITARY_AFFAIRS_SECRETARY,
            PositionCard.MILITARY_HR_CHIEF,
            PositionCard.COUNCIL_DEFENSE_CHAIR,
            PositionCard.DEFENSE_DEPT_CHIEF,
        )
    }

    /**
     * Promote an officer. Validates authority of the promoter and headcount at target tier.
     *
     * @return the promoted officer
     * @throws IllegalArgumentException if validation fails
     * @throws IllegalStateException if authority or headcount check fails
     */
    @Transactional
    fun promote(officerId: Long, promoterId: Long, sessionId: Long): Officer {
        val officer = officerRepository.findById(officerId).orElseThrow {
            IllegalArgumentException("Officer not found: $officerId")
        }
        require(officer.sessionId == sessionId) { "Officer not in session $sessionId" }

        val promoter = officerRepository.findById(promoterId).orElseThrow {
            IllegalArgumentException("Promoter not found: $promoterId")
        }
        require(promoter.sessionId == sessionId) { "Promoter not in session $sessionId" }
        require(promoter.factionId == officer.factionId) { "Promoter and officer must be in the same faction" }

        val currentTier = officer.officerLevel.toInt()
        require(currentTier < 10) { "Officer is already at maximum rank" }

        val targetTier = currentTier + 1
        validateAuthority(promoter, targetTier)

        check(rankLadderService.checkHeadcount(sessionId, officer.factionId, targetTier)) {
            val factionType = factionRepository.findById(officer.factionId).orElse(null)?.factionType ?: "empire"
            val rankTitle = RankTitleResolver.resolve(targetTier, factionType)
            "Headcount limit reached for ${rankTitle.korean} (${rankTitle.title}). Max: ${RankHeadcount.getLimit(targetTier)}"
        }

        rankLadderService.applyPromotion(officer)
        val saved = officerRepository.save(officer)

        log.info(
            "Officer {} ({}) promoted to tier {} by {} ({})",
            officer.id, officer.name, targetTier, promoter.id, promoter.name,
        )

        return saved
    }

    /**
     * Demote an officer. Validates authority of the demoter.
     *
     * @return the demoted officer
     * @throws IllegalArgumentException if validation fails
     * @throws IllegalStateException if authority check fails
     */
    @Transactional
    fun demote(officerId: Long, demoterId: Long, sessionId: Long): Officer {
        val officer = officerRepository.findById(officerId).orElseThrow {
            IllegalArgumentException("Officer not found: $officerId")
        }
        require(officer.sessionId == sessionId) { "Officer not in session $sessionId" }

        val demoter = officerRepository.findById(demoterId).orElseThrow {
            IllegalArgumentException("Demoter not found: $demoterId")
        }
        require(demoter.sessionId == sessionId) { "Demoter not in session $sessionId" }
        require(demoter.factionId == officer.factionId) { "Demoter and officer must be in the same faction" }

        val currentTier = officer.officerLevel.toInt()
        require(currentTier > 0) { "Officer is already at minimum rank" }

        // Authority to demote is based on the officer's current tier
        validateAuthority(demoter, currentTier)

        rankLadderService.applyDemotion(officer)
        val saved = officerRepository.save(officer)

        log.info(
            "Officer {} ({}) demoted to tier {} by {} ({})",
            officer.id, officer.name, currentTier - 1, demoter.id, demoter.name,
        )

        return saved
    }

    /**
     * Get personnel info for an officer including promotion eligibility.
     */
    fun getPersonnelInfo(officerId: Long, sessionId: Long): PersonnelInfo {
        val officer = officerRepository.findById(officerId).orElseThrow {
            IllegalArgumentException("Officer not found: $officerId")
        }
        require(officer.sessionId == sessionId) { "Officer not in session $sessionId" }

        val factionType = factionRepository.findById(officer.factionId).orElse(null)?.factionType ?: "empire"
        val currentTier = officer.officerLevel.toInt()
        val currentTitle = RankTitleResolver.resolve(currentTier, factionType)
        val nextTitle = if (currentTier < 10) RankTitleResolver.resolve(currentTier + 1, factionType) else null

        val promotionEligible = if (currentTier < 10) {
            rankLadderService.checkHeadcount(sessionId, officer.factionId, currentTier + 1)
        } else {
            false
        }

        return PersonnelInfo(
            officerId = officer.id,
            name = officer.name,
            rankTier = currentTier,
            rankTitle = currentTitle.title,
            rankTitleKo = currentTitle.korean,
            meritPoints = officer.meritPoints,
            evaluationPoints = officer.evaluationPoints,
            famePoints = officer.famePoints,
            promotionEligible = promotionEligible,
            nextRankTitle = nextTitle?.title,
            nextRankTitleKo = nextTitle?.korean,
        )
    }

    /**
     * Get full rank ladder for a faction.
     */
    fun getRankLadder(sessionId: Long, factionId: Long): List<RankLadderEntry> {
        val factionType = factionRepository.findById(factionId).orElse(null)?.factionType ?: "empire"
        val ladder = rankLadderService.buildLadder(sessionId, factionId)

        return ladder.map { officer ->
            val tier = officer.officerLevel.toInt()
            val title = RankTitleResolver.resolve(tier, factionType)
            RankLadderEntry(
                officerId = officer.id,
                name = officer.name,
                rankTier = tier,
                rankTitle = title.title,
                rankTitleKo = title.korean,
                meritPoints = officer.meritPoints,
                famePoints = officer.famePoints,
                totalStats = officer.leadership + officer.command + officer.intelligence +
                    officer.politics + officer.administration + officer.mobility +
                    officer.attack + officer.defense,
            )
        }
    }

    /**
     * Validates that the promoter/demoter has authority for the given target tier.
     */
    private fun validateAuthority(actor: Officer, targetTier: Int) {
        val requiredCards = when {
            targetTier >= 10 -> SOVEREIGN_CARDS
            targetTier >= 5 -> MINISTER_CARDS
            else -> HR_CARDS
        }

        val hasAuthority = requiredCards.any { card -> actor.hasPositionCard(card) }

        check(hasAuthority) {
            val cardNames = requiredCards.joinToString(", ") { "${it.nameKo} (${it.nameEn})" }
            "Insufficient authority. Required position cards: $cardNames"
        }
    }

    /**
     * Personnel info data holder.
     */
    data class PersonnelInfo(
        val officerId: Long,
        val name: String,
        val rankTier: Int,
        val rankTitle: String,
        val rankTitleKo: String,
        val meritPoints: Int,
        val evaluationPoints: Int,
        val famePoints: Int,
        val promotionEligible: Boolean,
        val nextRankTitle: String?,
        val nextRankTitleKo: String?,
    )

    /**
     * Rank ladder entry data holder.
     */
    data class RankLadderEntry(
        val officerId: Long,
        val name: String,
        val rankTier: Int,
        val rankTitle: String,
        val rankTitleKo: String,
        val meritPoints: Int,
        val famePoints: Int,
        val totalStats: Int,
    )
}
