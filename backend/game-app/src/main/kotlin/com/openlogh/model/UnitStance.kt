package com.openlogh.model

/**
 * Unit stance system (태세 4종) from gin7 manual.
 *
 * Each stance affects combat stats and operational capability:
 * - NAVIGATION: normal transit mode
 * - ANCHORING: in satellite orbit around planet/fortress
 * - STATIONED: landed inside planet/fortress
 * - COMBAT: active combat mode with stat modifiers
 *
 * Stance change requires 10 second wait time (STANCE_CHANGE_DELAY_MS).
 */
enum class UnitStance(
    val displayNameKo: String,
    val displayNameJa: String,
    val attackModifier: Double,
    val defenseModifier: Double,
    val sensorRangeModifier: Double,
    val moraleDecayRate: Double,
    val canMove: Boolean,
    val canDock: Boolean,
    val description: String,
) {
    /** 航行 - Navigation mode. Normal transit. */
    NAVIGATION(
        displayNameKo = "항행",
        displayNameJa = "航行",
        attackModifier = 1.0,
        defenseModifier = 1.0,
        sensorRangeModifier = 1.0,
        moraleDecayRate = 0.0,
        canMove = true,
        canDock = false,
        description = "통상 항행 모드. 이동 가능.",
    ),

    /** 碇泊 - Anchoring in satellite orbit. */
    ANCHORING(
        displayNameKo = "정박",
        displayNameJa = "碇泊",
        attackModifier = 0.8,
        defenseModifier = 1.1,
        sensorRangeModifier = 1.2,
        moraleDecayRate = 0.0,
        canMove = false,
        canDock = true,
        description = "행성/요새 위성궤도 정박. 이동 불가, 보급 가능.",
    ),

    /** 駐留 - Stationed inside planet/fortress. */
    STATIONED(
        displayNameKo = "주류",
        displayNameJa = "駐留",
        attackModifier = 0.5,
        defenseModifier = 1.3,
        sensorRangeModifier = 0.3,
        moraleDecayRate = 0.0,
        canMove = false,
        canDock = true,
        description = "행성/요새 내부 주둔. 보급/수리 가능, 전투력 감소.",
    ),

    /** 戦闘 - Combat mode. Attack up, sensor down, morale decays. */
    COMBAT(
        displayNameKo = "전투",
        displayNameJa = "戦闘",
        attackModifier = 1.3,
        defenseModifier = 0.9,
        sensorRangeModifier = 0.6,
        moraleDecayRate = 0.002,
        canMove = true,
        canDock = false,
        description = "전투 태세. 공격력 +30%, 색적범위 -40%, 사기 감소.",
    );

    companion object {
        /** Stance change delay in milliseconds (10 seconds). */
        const val STANCE_CHANGE_DELAY_MS = 10_000L

        fun fromString(value: String): UnitStance = try {
            valueOf(value.uppercase())
        } catch (_: IllegalArgumentException) {
            NAVIGATION
        }
    }
}
