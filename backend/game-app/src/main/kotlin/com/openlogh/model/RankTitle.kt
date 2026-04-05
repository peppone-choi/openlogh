package com.openlogh.model

data class RankTitle(
    val tier: Int,
    val title: String,
    val korean: String,
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
        RankTitle(9, "Admiral of the Fleet", "상급대장"),
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
}
