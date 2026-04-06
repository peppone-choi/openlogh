package com.openlogh.model

/**
 * Rank headcount limits per faction per session.
 *
 * Based on gin7 manual (p.33-36):
 * - Tier 10 (원수/Reichsmarschall/Fleet Admiral): 5
 * - Tier 9 (상급대장/Fleet Admiral/Admiral of the Fleet): 5 (Empire only; Alliance uses tier 10 name differently)
 * - Tier 8 (대장/Admiral): 10
 * - Tier 7 (중장/Vice Admiral): 20
 * - Tier 6 (소장/Rear Admiral): 40
 * - Tier 5 (준장/Commodore): 80
 * - Tier 0-4 (대좌 이하/Captain and below): Unlimited
 */
object RankHeadcount {

    private val limitByTier = mapOf(
        10 to 5,
        9 to 5,
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
     * Returns the headcount limit for the given rank tier.
     * Returns [Int.MAX_VALUE] for tiers 0-4 (unlimited).
     */
    fun getLimit(tier: Int): Int {
        return limitByTier[tier] ?: Int.MAX_VALUE
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
}
