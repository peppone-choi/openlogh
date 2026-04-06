package com.openlogh.model

/**
 * Alliance Supreme Council - 11 seats.
 * Each seat corresponds to a government department with a committee chair.
 * Maps to existing PositionCard entries where available.
 *
 * @param code String identifier
 * @param nameKo Korean name
 * @param nameEn English name
 * @param relatedPositionCard Optional PositionCard granted to the seat holder
 * @param minRank Minimum officer rank required to hold this seat
 */
enum class CouncilSeatCode(
    val code: String,
    val nameKo: String,
    val nameEn: String,
    val relatedPositionCard: PositionCard?,
    val minRank: Int,
) {
    COUNCIL_CHAIRMAN(
        "COUNCIL_CHAIRMAN", "최고평의회 의장", "Council Chairman",
        PositionCard.COUNCIL_CHAIRMAN, 10,
    ),
    COUNCIL_VICE_CHAIRMAN(
        "COUNCIL_VICE_CHAIRMAN", "부의장", "Council Vice Chairman",
        PositionCard.COUNCIL_VICE_CHAIRMAN, 9,
    ),
    COUNCIL_STATE_CHAIR(
        "COUNCIL_STATE_CHAIR", "국무위원장", "State Committee Chair",
        PositionCard.COUNCIL_STATE_CHAIR, 8,
    ),
    DEFENSE_COMMITTEE_CHAIR(
        "DEFENSE_COMMITTEE_CHAIR", "국방위원장", "Defense Committee Chair",
        PositionCard.COUNCIL_DEFENSE_CHAIR, 8,
    ),
    FINANCE_COMMITTEE_CHAIR(
        "FINANCE_COMMITTEE_CHAIR", "재정위원장", "Finance Committee Chair",
        PositionCard.COUNCIL_FINANCE_CHAIR, 8,
    ),
    FOREIGN_AFFAIRS_CHAIR(
        "FOREIGN_AFFAIRS_CHAIR", "외교위원장", "Foreign Affairs Chair",
        null, 7,
    ),
    INTELLIGENCE_CHAIR(
        "INTELLIGENCE_CHAIR", "정보위원장", "Intelligence Chair",
        null, 7,
    ),
    LOGISTICS_CHAIR(
        "LOGISTICS_CHAIR", "군수위원장", "Logistics Chair",
        null, 7,
    ),
    PERSONNEL_CHAIR(
        "PERSONNEL_CHAIR", "인사위원장", "Personnel Chair",
        null, 7,
    ),
    COUNCIL_MEMBER(
        "COUNCIL_MEMBER", "평의회위원", "Council Member",
        PositionCard.COUNCIL_MEMBERS, 7,
    ),
    COUNCIL_SECRETARY(
        "COUNCIL_SECRETARY", "서기", "Council Secretary",
        PositionCard.COUNCIL_SECRETARY, 7,
    );

    companion object {
        fun fromCode(code: String): CouncilSeatCode? =
            entries.firstOrNull { it.code == code }
    }
}
