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
 * - Officers within the same rank are ordered by (in order, Phase 24-09 A3):
 *     제1법칙: 공적포인트 (meritPoints) — desc
 *     제3법칙: 서훈최고위 (medalRank), then 서훈횟수 (medalCount) — desc
 *     명성 (famePoints) — desc, pragma: gin7 does not list this but the
 *       existing codebase uses it as a continuous-career signal between
 *       the (skipped) 제2법칙 (작위) and 제4법칙 (영향력).
 *     제5법칙: 전체 파라미터 값 합계 — desc
 * - 제2법칙 (작위) and 제4법칙 (영향력) remain out of scope for Phase 24
 *   (no fields available on Officer today; see gap analysis §D1).
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
     * Builds the rank ladder for a faction, ordered by:
     *   rank tier desc (outer segmentation),
     *   meritPoints desc (제1법칙),
     *   medalRank desc, medalCount desc (제3법칙, Phase 24-09 A3),
     *   famePoints desc,
     *   totalStats desc (제5법칙).
     */
    fun buildLadder(sessionId: Long, factionId: Long): List<Officer> {
        val officers = officerRepository.findBySessionIdAndFactionId(sessionId, factionId)
            .filter { it.npcState < 5 && it.factionId > 0 }

        return officers.sortedWith(
            compareByDescending<Officer> { it.officerLevel.toInt() }
                .thenByDescending { it.meritPoints }
                .thenByDescending { it.medalRank.toInt() }
                .thenByDescending { it.medalCount.toInt() }
                .thenByDescending { it.famePoints }
                .thenByDescending { totalStats(it) }
        )
    }

    /**
     * Gets promotion candidates: top officer at each tier <= AUTO_PROMOTION_MAX_TIER.
     * Only returns candidates where the next tier has headcount capacity.
     *
     * Phase 24-11: Alliance officers at tier 8 promote directly to tier 10 because
     * tier 9 (상급대장) is Empire-only per gin7 manual p34.
     */
    fun getPromotionCandidates(sessionId: Long, factionId: Long): List<Officer> {
        val ladder = buildLadder(sessionId, factionId)
        val faction = factionRepository.findById(factionId).orElse(null)
        val factionType = faction?.factionType ?: "empire"
        val candidates = mutableListOf<Officer>()

        for (tier in 0..RankHeadcount.AUTO_PROMOTION_MAX_TIER) {
            val officersAtTier = ladder.filter { it.officerLevel.toInt() == tier }
            if (officersAtTier.isEmpty()) continue

            val topOfficer = officersAtTier.first() // already sorted by merit desc
            val nextTier = RankHeadcount.nextTier(tier, factionType)

            if (nextTier > 10) continue // already at the top of this faction's ladder

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
     * - Advance rank tier (Phase 24-11: skipping vacant tiers per faction)
     * - Reset merit to 0
     * - Revoke all position cards except PERSONAL, CAPTAIN, FIEF
     */
    fun applyPromotion(officer: Officer) {
        val oldTier = officer.officerLevel.toInt()
        require(oldTier < 10) { "Cannot promote beyond tier 10" }

        // Phase 24-11: resolve next tier respecting faction ladder shape
        // (Alliance tier 8 → tier 10, skipping the vacant 상급대장 slot).
        val faction = factionRepository.findById(officer.factionId).orElse(null)
        val factionType = faction?.factionType ?: "empire"
        val newTier = RankHeadcount.nextTier(oldTier, factionType)
        require(newTier <= 10) { "Cannot promote beyond tier 10 for faction $factionType" }

        officer.officerLevel = newTier.toShort()
        officer.meritPoints = RankHeadcount.MERIT_AFTER_PROMOTION

        // Revoke position cards except retained ones
        officer.positionCards = officer.positionCards
            .filter { it in RankHeadcount.RETAINED_CARDS }
            .toMutableList()
    }

    /**
     * Apply demotion effects to an officer:
     * - Decrement rank tier (Phase 24-11: skipping vacant tiers per faction,
     *   so an Alliance 원수 demotes tier 10 → tier 8 directly)
     * - Reset merit to 100
     * - Revoke all position cards except PERSONAL, CAPTAIN, FIEF
     */
    fun applyDemotion(officer: Officer) {
        val oldTier = officer.officerLevel.toInt()
        require(oldTier > 0) { "Cannot demote below tier 0" }

        // Phase 24-11: walk down past vacant tiers (Alliance tier 10 → 8).
        val faction = factionRepository.findById(officer.factionId).orElse(null)
        val factionType = faction?.factionType ?: "empire"
        var newTier = oldTier - 1
        while (newTier > 0 && !RankTitleResolver.hasTier(newTier, factionType)) {
            newTier--
        }

        officer.officerLevel = newTier.toShort()
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
