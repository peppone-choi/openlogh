package com.openlogh.model

/**
 * Types of elections in the Alliance democratic system.
 */
enum class ElectionType(
    val code: String,
    val nameKo: String,
    val nameEn: String,
) {
    /** Full council re-election */
    COUNCIL_CHAIR("COUNCIL_CHAIR", "의장 선거", "Council Chair Election"),

    /** Fill a single vacancy */
    SINGLE_SEAT("SINGLE_SEAT", "보궐 선거", "By-election"),

    /** Vote of confidence/no-confidence */
    CONFIDENCE_VOTE("CONFIDENCE_VOTE", "신임투표", "Confidence Vote");

    companion object {
        fun fromCode(code: String): ElectionType? =
            entries.firstOrNull { it.code == code }
    }
}
