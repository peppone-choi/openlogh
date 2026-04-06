package com.openlogh.engine.modifier

/**
 * Officer level stat/domestic modifier (legacy parity: TriggerOfficerLevel.php).
 *
 * Legacy used 13 grades (0-12); OpenSamguk uses 21 grades (0-20).
 * Max bonus values are preserved; intermediate levels are proportionally scaled
 * using the mapping: new_level = round(legacy_level * 20 / 12).
 *
 * Three effects from legacy:
 *   1. Leadership bonus: level 20 → nationLevel*2, level 8-19 → nationLevel*1, else 0
 *      (applied externally via calcLeadershipBonus; this modifier handles stat context)
 *      Legacy threshold (삼국지 원본): level 5/12 → scaled to 8/20 in OpenLOGH
 *   2. Domestic score ×1.05 for specific officer levels per command type
 *   3. War power multiplier / opponent war power multiplier per level bracket
 *
 * Score bonus level sets (scaled from legacy):
 *   농업/상업 : 5, 8, 12, 15, 18, 20
 *   기술      : 8, 12, 15, 18, 20
 *   민심/인구 : 3, 18, 20
 *   수비/성벽/치안 : 7, 10, 13, 17, 18, 20
 *
 * War power brackets (scaled from legacy):
 *   level 20        : self ×1.07, opponent ×0.93
 *   level 18        : self ×1.05, opponent ×0.95
 *   levels 10,13,17 : self ×1.10
 *   levels  8,12,15 : opponent ×0.90
 *   levels  3, 5, 7 : self ×1.05, opponent ×0.95
 */
class OfficerLevelModifier(
    private val officerLevel: Int,
    private val nationLevel: Int,
) : ActionModifier {
    override val code = "officer_level"
    override val name = "관직"

    // ── Leadership bonus (onCalcStat) ─────────────────────────────────────
    private val leadershipBonus: Double = when {
        officerLevel == 20 -> (nationLevel * 2).toDouble()
        officerLevel >= 8  -> nationLevel.toDouble()
        else               -> 0.0
    }

    override fun onCalcStat(stat: StatContext): StatContext {
        if (leadershipBonus == 0.0) return stat
        return stat.copy(leadership = stat.leadership + leadershipBonus)
    }

    // ── Domestic score ×1.05 (onCalcDomestic) ────────────────────────────
    override fun onCalcDomestic(ctx: DomesticContext): DomesticContext {
        val bonus = when (ctx.actionCode) {
            "농업", "상업" -> officerLevel in AGR_COM_LEVELS
            "기술"         -> officerLevel in TECH_LEVELS
            "민심", "인구" -> officerLevel in POP_LEVELS
            "수비", "성벽", "치안" -> officerLevel in DEF_LEVELS
            else           -> false
        }
        return if (bonus) ctx.copy(scoreMultiplier = ctx.scoreMultiplier * 1.05) else ctx
    }

    // ── War power (getWarPowerMultiplier / onCalcOpposeStat) ──────────────
    override fun getWarPowerMultiplier(): Double = when (officerLevel) {
        20           -> 1.07
        18           -> 1.05
        10, 13, 17   -> 1.10
        3, 5, 7      -> 1.05
        else         -> 1.0
    }

    override fun onCalcOpposeStat(stat: StatContext): StatContext {
        val multiplier = when (officerLevel) {
            20         -> 0.93
            18         -> 0.95
            8, 12, 15  -> 0.90
            3, 5, 7    -> 0.95
            else       -> 1.0
        }
        return if (multiplier != 1.0) stat.copy(warPower = stat.warPower * multiplier) else stat
    }

    companion object {
        // Scaled from legacy (round(legacy * 20/12))
        private val AGR_COM_LEVELS = setOf(5, 8, 12, 15, 18, 20)
        private val TECH_LEVELS    = setOf(8, 12, 15, 18, 20)
        private val POP_LEVELS     = setOf(3, 18, 20)
        private val DEF_LEVELS     = setOf(7, 10, 13, 17, 18, 20)
    }
}
