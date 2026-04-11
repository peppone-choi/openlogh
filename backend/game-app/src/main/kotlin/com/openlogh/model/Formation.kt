package com.openlogh.model

/**
 * Fleet formation types for tactical combat.
 *
 * Phase 24-13 (gap C16, docs/03-analysis/gin7-manual-complete-gap.analysis.md §8.2):
 * Extended from 4 formations to 8 to match the keyboard shortcut lineup on
 * gin7 manual p25 (隊列変更 submenu):
 *
 *   X = 防御       (DEFENSIVE — maximum defense posture)
 *   C = 紡錘       (WEDGE — offensive spindle)
 *   V = 艦種1      (BY_CLASS — type grouping, primary variant)
 *   B = 艦種2      (BY_CLASS_2 — type grouping, defensive variant)
 *   N = 混成1      (MIXED — balanced, primary variant)
 *   M = 混成2      (MIXED_2 — balanced, mobile variant)
 *   , = 三列       (THREE_COLUMN — three-column defense)
 *   Z = 隊列解除   (NONE — no formation, default)
 */
enum class Formation(
    val attackModifier: Double,
    val defenseModifier: Double,
    val speedModifier: Double,
    val displayNameKo: String,
    val description: String,
) {
    /** 무편성 — 隊列解除. Formation disbanded, no modifier. */
    NONE(
        attackModifier = 1.0,
        defenseModifier = 1.0,
        speedModifier = 1.0,
        displayNameKo = "무편성",
        description = "진형 해제 상태. 수정치 없음.",
    ),

    /** 방어진형 — Pure defense formation (X key). */
    DEFENSIVE(
        attackModifier = 0.6,
        defenseModifier = 1.6,
        speedModifier = 0.8,
        displayNameKo = "방어진형",
        description = "완전 방어 태세. 공격력 -40%, 방어력 +60%, 속도 -20%",
    ),

    /** 방추진형 — Offensive wedge formation (C key). High attack, low defense. */
    WEDGE(
        attackModifier = 1.3,
        defenseModifier = 0.7,
        speedModifier = 1.1,
        displayNameKo = "방추진형",
        description = "공격 특화 진형. 공격력 +30%, 방어력 -30%, 속도 +10%",
    ),

    /** 함종별진형 — Ships grouped by class, primary (V key). Balanced modifiers. */
    BY_CLASS(
        attackModifier = 1.1,
        defenseModifier = 1.1,
        speedModifier = 1.0,
        displayNameKo = "함종별진형 I",
        description = "함종 특화 진형. 공격력 +10%, 방어력 +10%",
    ),

    /** 함종별진형 II — defensive variant of BY_CLASS (B key). */
    BY_CLASS_2(
        attackModifier = 1.0,
        defenseModifier = 1.2,
        speedModifier = 0.95,
        displayNameKo = "함종별진형 II",
        description = "함종 방어 진형. 방어력 +20%, 속도 -5%",
    ),

    /** 혼성진형 — Mixed formation, primary (N key). Moderate all-around. */
    MIXED(
        attackModifier = 1.0,
        defenseModifier = 1.0,
        speedModifier = 1.0,
        displayNameKo = "혼성진형 I",
        description = "범용 진형. 균형잡힌 능력치",
    ),

    /** 혼성진형 II — mobile variant of MIXED (M key). */
    MIXED_2(
        attackModifier = 1.05,
        defenseModifier = 0.95,
        speedModifier = 1.1,
        displayNameKo = "혼성진형 II",
        description = "기동 혼성 진형. 공격력 +5%, 방어력 -5%, 속도 +10%",
    ),

    /** 삼열종심진형 — Three-column defense (, key). High defense, low attack. */
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
