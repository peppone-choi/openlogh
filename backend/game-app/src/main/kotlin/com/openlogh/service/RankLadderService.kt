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
 * Manages the rank ladder ordering and auto-promotion system.
 *
 * gin7 rules (p.33-36):
 * - Officers within the same rank are ordered by: merit > fame > total stats
 * - Auto-promotion: below tier 4 (Captain), every 30 game days, top of ladder promoted
 * - On promotion: merit -> 0, position cards revoked except PERSONAL/CAPTAIN/FIEF
 * - Headcount limits enforced for tier 5+
 */
@Service
class RankLadderService(
    private val officerRepository: OfficerRepository,
    private val factionRepository: FactionRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Builds the rank ladder for a faction, ordered by rank tier desc, then merit desc,
     * fame desc, total stats desc.
     */
    fun buildLadder(sessionId: Long, factionId: Long): List<Officer> {
        val officers = officerRepository.findBySessionIdAndFactionId(sessionId, factionId)
            .filter { it.npcState < 5 && it.factionId > 0 }

        return officers.sortedWith(
            compareByDescending<Officer> { it.officerLevel.toInt() }
                .thenByDescending { it.meritPoints }
                .thenByDescending { it.famePoints }
                .thenByDescending { totalStats(it) }
        )
    }

    /**
     * Gets promotion candidates: top officer at each tier <= AUTO_PROMOTION_MAX_TIER.
     * Only returns candidates where the next tier has headcount capacity.
     */
    fun getPromotionCandidates(sessionId: Long, factionId: Long): List<Officer> {
        val ladder = buildLadder(sessionId, factionId)
        val candidates = mutableListOf<Officer>()

        for (tier in 0..RankHeadcount.AUTO_PROMOTION_MAX_TIER) {
            val officersAtTier = ladder.filter { it.officerLevel.toInt() == tier }
            if (officersAtTier.isEmpty()) continue

            val topOfficer = officersAtTier.first() // already sorted by merit desc
            val nextTier = tier + 1

            if (checkHeadcount(sessionId, factionId, nextTier, ladder)) {
                candidates.add(topOfficer)
            }
        }

        return candidates
    }

    /**
     * Auto-promote: called by tick engine every 30 game days.
     * Promotes the top officer at each tier <= 4 (Captain) where next tier has capacity.
     */
    @Transactional
    fun autoPromote(sessionId: Long): List<Officer> {
        val factions = factionRepository.findBySessionId(sessionId)
        val promoted = mutableListOf<Officer>()

        for (faction in factions) {
            val candidates = getPromotionCandidates(sessionId, faction.id)
            for (officer in candidates) {
                applyPromotion(officer)
                officerRepository.save(officer)
                promoted.add(officer)
                log.info(
                    "Auto-promoted officer {} ({}) from tier {} to tier {} in faction {}",
                    officer.id, officer.name, officer.officerLevel - 1, officer.officerLevel, faction.id,
                )
            }
        }

        return promoted
    }

    /**
     * Checks whether there's room at the target rank tier for a faction.
     */
    fun checkHeadcount(sessionId: Long, factionId: Long, targetTier: Int): Boolean {
        val officers = officerRepository.findBySessionIdAndFactionId(sessionId, factionId)
            .filter { it.npcState < 5 }
        return checkHeadcount(sessionId, factionId, targetTier, officers)
    }

    private fun checkHeadcount(
        sessionId: Long,
        factionId: Long,
        targetTier: Int,
        officers: List<Officer>,
    ): Boolean {
        if (!RankHeadcount.hasLimit(targetTier)) return true
        val currentCount = officers.count { it.officerLevel.toInt() == targetTier }
        return currentCount < RankHeadcount.getLimit(targetTier)
    }

    /**
     * Apply promotion effects to an officer:
     * - Increment rank tier
     * - Reset merit to 0
     * - Revoke all position cards except PERSONAL, CAPTAIN, FIEF
     */
    fun applyPromotion(officer: Officer) {
        val oldTier = officer.officerLevel.toInt()
        require(oldTier < 10) { "Cannot promote beyond tier 10" }

        officer.officerLevel = (oldTier + 1).toShort()
        officer.meritPoints = RankHeadcount.MERIT_AFTER_PROMOTION

        // Revoke position cards except retained ones
        officer.positionCards = officer.positionCards
            .filter { it in RankHeadcount.RETAINED_CARDS }
            .toMutableList()
    }

    /**
     * Apply demotion effects to an officer:
     * - Decrement rank tier
     * - Reset merit to 100
     * - Revoke all position cards except PERSONAL, CAPTAIN, FIEF
     */
    fun applyDemotion(officer: Officer) {
        val oldTier = officer.officerLevel.toInt()
        require(oldTier > 0) { "Cannot demote below tier 0" }

        officer.officerLevel = (oldTier - 1).toShort()
        officer.meritPoints = RankHeadcount.MERIT_AFTER_DEMOTION

        // Revoke position cards except retained ones
        officer.positionCards = officer.positionCards
            .filter { it in RankHeadcount.RETAINED_CARDS }
            .toMutableList()
    }

    /**
     * Calculates total stats for an officer (all 8 stats summed).
     */
    private fun totalStats(officer: Officer): Int {
        return officer.leadership + officer.command + officer.intelligence +
            officer.politics + officer.administration + officer.mobility +
            officer.attack + officer.defense
    }
}
