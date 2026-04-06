package com.openlogh.model

/**
 * Fleet formation types for tactical combat.
 * Based on gin7 manual Chapter 4:
 * - 紡錘 (wedge/spindle): offensive formation, high attack
 * - 艦種別 (by-class): ships grouped by type, balanced
 * - 混成 (mixed): general purpose, moderate all
 * - 三列 (three-column): defensive formation, high defense
 */
enum class Formation(
    val attackModifier: Double,
    val defenseModifier: Double,
    val speedModifier: Double,
    val displayNameKo: String,
    val description: String,
) {
    /** 방추진형 — Offensive wedge formation. High attack, low defense. */
    WEDGE(
        attackModifier = 1.3,
        defenseModifier = 0.7,
        speedModifier = 1.1,
        displayNameKo = "방추진형",
        description = "공격 특화 진형. 공격력 +30%, 방어력 -30%, 속도 +10%",
    ),

    /** 함종별진형 — Ships grouped by class. Balanced modifiers. */
    BY_CLASS(
        attackModifier = 1.1,
        defenseModifier = 1.1,
        speedModifier = 1.0,
        displayNameKo = "함종별진형",
        description = "함종 특화 진형. 공격력 +10%, 방어력 +10%",
    ),

    /** 혼성진형 — Mixed formation. Moderate all-around. */
    MIXED(
        attackModifier = 1.0,
        defenseModifier = 1.0,
        speedModifier = 1.0,
        displayNameKo = "혼성진형",
        description = "범용 진형. 균형잡힌 능력치",
    ),

    /** 삼열종심진형 — Three-column defense. High defense, low attack. */
    THREE_COLUMN(
        attackModifier = 0.8,
        defenseModifier = 1.4,
        speedModifier = 0.9,
        displayNameKo = "삼열종심진형",
        description = "방어 특화 진형. 공격력 -20%, 방어력 +40%, 속도 -10%",
    );

    companion object {
        fun fromString(value: String): Formation = try {
            valueOf(value.uppercase())
        } catch (_: IllegalArgumentException) {
            MIXED
        }
    }
}
