package com.openlogh.model

/**
 * Rank headcount limits per faction per session.
 *
 * Based on gin7 manual (p.33-36):
 *
 * **Empire (帝国軍):** tier 10 (元帥) 5 / tier 9 (上級大将) 5 / tier 8 (大将) 10 /
 * tier 7 (中将) 20 / tier 6 (少将) 40 / tier 5 (准将) 80 / tier 0-4 unlimited.
 *
 * **Alliance (自由惑星同盟軍):** tier 10 (元帥) 5 / tier 8 (大将) 10 /
 * tier 7 (中将) 20 / tier 6 (少将) 40 / tier 5 (准将) 80 / tier 0-4 unlimited.
 *
 * Phase 24-11: Alliance has NO tier 9 (상급대장); manual p34 explicitly lists
 * 上級大将 as Empire-only. Alliance officers promote tier 8 → tier 10 directly.
 */
object RankHeadcount {

    /** Empire rank ladder headcount caps (tier → limit). */
    private val empireLimitByTier = mapOf(
        10 to 5,
        9 to 5,
        8 to 10,
        7 to 20,
        6 to 40,
        5 to 80,
    )

    /**
     * Alliance rank ladder headcount caps — tier 9 is omitted (vacant)
     * so auto-promotion and headcount checks never allow an Alliance officer
     * to sit at tier 9.
     */
    private val allianceLimitByTier = mapOf(
        10 to 5,
        9 to 0,   // Phase 24-11: explicitly forbid Alliance tier 9
        8 to 10,
        7 to 20,
        6 to 40,
        5 to 80,
    )

    /** Tier at or below which auto-promotion applies (every 30 game days). */
    const val AUTO_PROMOTION_MAX_TIER = 4

    /** Game days between auto-promotion cycles. */
    const val AUTO_PROMOTION_INTERVAL_DAYS = 30

    /** Merit points reset value after promotion. */
    const val MERIT_AFTER_PROMOTION = 0

    /** Merit points reset value after demotion. */
    const val MERIT_AFTER_DEMOTION = 100

    /** Position cards retained after promotion/demotion. */
    val RETAINED_CARDS = setOf("PERSONAL", "CAPTAIN", "FIEF")

    /**
     * Returns the headcount limit for the given rank tier (Empire semantics,
     * preserved for backward compatibility with call sites that predate
     * Phase 24-11). New code should prefer the faction-aware overload.
     */
    fun getLimit(tier: Int): Int {
        return empireLimitByTier[tier] ?: Int.MAX_VALUE
    }

    /**
     * Faction-aware headcount limit. Phase 24-11: Alliance tier 9 returns 0
     * because 上級大将 does not exist in the Alliance hierarchy.
     */
    fun getLimit(tier: Int, factionType: String): Int {
        val table = when (factionType) {
            "alliance" -> allianceLimitByTier
            else -> empireLimitByTier
        }
        return table[tier] ?: Int.MAX_VALUE
    }

    /**
     * Checks whether the given tier has a headcount limit (tier >= 5).
     */
    fun hasLimit(tier: Int): Boolean {
        return tier >= 5
    }

    /**
     * Checks whether the given tier is eligible for auto-promotion.
     */
    fun isAutoPromotionEligible(tier: Int): Boolean {
        return tier <= AUTO_PROMOTION_MAX_TIER
    }

    /**
     * Phase 24-11: returns the next reachable tier above [currentTier] for a
     * given faction, skipping vacant slots. Used by RankLadderService to
     * bypass Alliance tier 9 and promote tier 8 → tier 10 directly.
     */
    fun nextTier(currentTier: Int, factionType: String): Int {
        var next = currentTier + 1
        while (next <= 10 && !RankTitleResolver.hasTier(next, factionType)) {
            next++
        }
        return next
    }
}
