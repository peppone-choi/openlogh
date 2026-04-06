package com.openlogh.model

/**
 * Empire nobility hierarchy - 5 peerage levels.
 * Nobility rank affects political influence bonus for Empire officers.
 *
 * @param code String identifier
 * @param nameKo Korean name
 * @param nameEn English name
 * @param politicsBonus Politics influence multiplier bonus (e.g., 0.05 = +5%)
 * @param minRank Minimum officer rank required to hold this peerage
 */
enum class NobilityRank(
    val code: String,
    val nameKo: String,
    val nameEn: String,
    val politicsBonus: Float,
    val minRank: Int,
) {
    COMMONER("COMMONER", "평민", "Commoner", 0.0f, 0),
    BARON("BARON", "남작", "Baron", 0.05f, 2),
    COUNT("COUNT", "백작", "Count", 0.10f, 4),
    MARQUIS("MARQUIS", "후작", "Marquis", 0.15f, 6),
    DUKE("DUKE", "공작", "Duke", 0.20f, 8);

    companion object {
        fun fromCode(code: String): NobilityRank =
            entries.firstOrNull { it.code == code } ?: COMMONER
    }
}
