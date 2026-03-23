package com.openlogh.engine.tactical

enum class Formation(
    val code: String,
    val displayName: String,
    val attackBonus: Double,
    val defenseBonus: Double,
    val mobilityBonus: Double,
    val flankAttackBonus: Double,
    val flankDefenseBonus: Double,
    val specialBonus: Double,
    val specialDesc: String,
) {
    SPINDLE(
        "spindle", "방추형",
        attackBonus = 1.3,
        defenseBonus = 1.0,
        mobilityBonus = 1.0,
        flankAttackBonus = 1.0,
        flankDefenseBonus = 0.8,
        specialBonus = 1.3,
        specialDesc = "돌파력 +30%, 측면방어 -20%",
    ),
    CRANE_WING(
        "crane_wing", "학익진",
        attackBonus = 1.0,
        defenseBonus = 0.85,
        mobilityBonus = 1.0,
        flankAttackBonus = 1.25,
        flankDefenseBonus = 1.0,
        specialBonus = 1.25,
        specialDesc = "포위력 +25%, 중앙방어 -15%",
    ),
    WHEEL(
        "wheel", "차륜진",
        attackBonus = 1.0,
        defenseBonus = 1.0,
        mobilityBonus = 1.15,
        flankAttackBonus = 1.0,
        flankDefenseBonus = 1.0,
        specialBonus = 1.15,
        specialDesc = "균형형, 회전기동 +15%",
    ),
    ECHELON(
        "echelon", "사선진",
        attackBonus = 0.9,
        defenseBonus = 1.0,
        mobilityBonus = 1.1,
        flankAttackBonus = 1.25,
        flankDefenseBonus = 1.0,
        specialBonus = 1.25,
        specialDesc = "우회공격 +25%, 정면공격 -10%",
    ),
    SQUARE(
        "square", "방진",
        attackBonus = 1.0,
        defenseBonus = 1.3,
        mobilityBonus = 0.75,
        flankAttackBonus = 1.0,
        flankDefenseBonus = 1.3,
        specialBonus = 1.3,
        specialDesc = "방어력 +30%, 기동력 -25%",
    ),
    DISPERSED(
        "dispersed", "산개진",
        attackBonus = 0.85,
        defenseBonus = 1.0,
        mobilityBonus = 1.1,
        flankAttackBonus = 0.85,
        flankDefenseBonus = 1.0,
        specialBonus = 1.2,
        specialDesc = "피해분산 +20%, 공격력 -15%",
    ),
    ;

    companion object {
        private val byCode = entries.associateBy { it.code }
        fun fromCode(code: String): Formation = byCode[code] ?: SPINDLE
    }
}
