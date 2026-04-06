package com.openlogh.model

/**
 * Types of intelligence available for purchase from Fezzan.
 *
 * @param code String identifier
 * @param nameKo Korean name
 * @param cost Base cost in funds
 * @param duration How many ticks the intelligence remains valid
 */
enum class IntelligenceType(
    val code: String,
    val nameKo: String,
    val cost: Int,
    val duration: Int,
) {
    FLEET_POSITIONS("FLEET_POSITIONS", "함대 위치", 500, 3600),
    PLANET_RESOURCES("PLANET_RESOURCES", "행성 자원", 300, 7200),
    OFFICER_INFO("OFFICER_INFO", "장교 정보", 200, 7200),
    MILITARY_POWER("MILITARY_POWER", "군사력", 400, 3600),
    COUP_INTEL("COUP_INTEL", "쿠데타 정보", 800, 1800);

    companion object {
        fun fromCode(code: String): IntelligenceType? =
            entries.firstOrNull { it.code == code }
    }
}
