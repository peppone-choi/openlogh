package com.openlogh.model

/**
 * Crew proficiency levels (승조원 수련도).
 * Green < Normal < Veteran < Elite
 *
 * New production always yields GREEN crew.
 * Training and combat experience increases proficiency.
 * Proficiency affects unit combat effectiveness.
 */
enum class CrewProficiency(
    val level: Int,
    val displayName: String,
    val warehouseColumn: String,
    val combatMultiplier: Double,
) {
    GREEN(0, "신병", "crew_green", 0.7),
    NORMAL(1, "일반", "crew_normal", 1.0),
    VETERAN(2, "숙련", "crew_veteran", 1.2),
    ELITE(3, "정예", "crew_elite", 1.5),
    ;

    /** Returns the next proficiency level, or null if already elite. */
    fun promote(): CrewProficiency? = when (this) {
        GREEN -> NORMAL
        NORMAL -> VETERAN
        VETERAN -> ELITE
        ELITE -> null
    }

    companion object {
        private val byLevel = entries.associateBy { it.level }

        fun fromLevel(level: Int): CrewProficiency? = byLevel[level]
    }
}
