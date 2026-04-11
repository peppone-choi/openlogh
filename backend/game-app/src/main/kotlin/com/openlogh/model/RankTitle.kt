package com.openlogh.model

/**
 * Represents a rank at a given tier for a specific faction.
 *
 * Phase 24-11 (docs/03-analysis/gin7-manual-complete-gap.analysis.md §D1):
 * gin7 manual p34 explicitly lists separate rank structures — the Galactic
 * Empire has a 上級大将 (Fleet Admiral) tier above 大将 that the Free Planets
 * Alliance does not. Alliance officers progress 大将(tier 8) → 元帥(tier 10)
 * directly, with tier 9 left permanently vacant.
 *
 * [isVacant] flags those faction-specific holes so headcount logic and the
 * auto-promotion walker can skip them cleanly instead of fabricating a rank.
 */
data class RankTitle(
    val tier: Int,
    val title: String,
    val korean: String,
    /** True when this tier does not exist in this faction's hierarchy. */
    val isVacant: Boolean = false,
)

object RankTitleResolver {

    private val empireTitles = listOf(
        RankTitle(0, "Sub-Lieutenant", "소위"),
        RankTitle(1, "Lieutenant", "대위"),
        RankTitle(2, "Lieutenant Commander", "소령"),
        RankTitle(3, "Commander", "중령"),
        RankTitle(4, "Captain", "대령"),
        RankTitle(5, "Commodore", "준장"),
        RankTitle(6, "Rear Admiral", "소장"),
        RankTitle(7, "Vice Admiral", "중장"),
        RankTitle(8, "Admiral", "대장"),
        RankTitle(9, "Fleet Admiral", "상급대장"),
        RankTitle(10, "Reichsmarschall", "원수"),
    )

    /**
     * Alliance rank ladder — gin7 manual p34 explicitly shows Alliance has
     * NO 上級大将 tier; Admiral goes straight to Fleet Admiral. Tier 9 is
     * therefore marked vacant and must never accept an officer.
     */
    private val allianceTitles = listOf(
        RankTitle(0, "Sub-Lieutenant", "소위"),
        RankTitle(1, "Lieutenant", "대위"),
        RankTitle(2, "Lieutenant Commander", "소령"),
        RankTitle(3, "Commander", "중령"),
        RankTitle(4, "Captain", "대령"),
        RankTitle(5, "Commodore", "준장"),
        RankTitle(6, "Rear Admiral", "소장"),
        RankTitle(7, "Vice Admiral", "중장"),
        RankTitle(8, "Admiral", "대장"),
        RankTitle(9, "(vacant)", "(결원)", isVacant = true),
        RankTitle(10, "Fleet Admiral", "원수"),
    )

    fun resolve(tier: Int, factionType: String): RankTitle {
        require(tier in 0..10) { "Rank tier must be between 0 and 10, got $tier" }
        val titles = when (factionType) {
            "alliance" -> allianceTitles
            else -> empireTitles
        }
        return titles[tier]
    }

    /**
     * Returns true when the given tier exists in this faction's rank structure.
     * Phase 24-11: Alliance tier 9 (상급대장) does NOT exist per gin7 manual p34.
     */
    fun hasTier(tier: Int, factionType: String): Boolean {
        if (tier !in 0..10) return false
        return !resolve(tier, factionType).isVacant
    }
}
