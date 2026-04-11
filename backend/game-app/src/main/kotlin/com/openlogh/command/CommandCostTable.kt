package com.openlogh.command

/**
 * Phase 24-05 (Gap B*, docs/03-analysis/gin7-manual-complete-gap.analysis.md §A/B).
 *
 * Authoritative command-point-cost lookup table for all 81 gin7 strategic commands.
 *
 * ## Why this exists
 *
 * Before Phase 24-05, [BaseCommand.getCommandPointCost] defaulted to `1` and only a
 * handful of commands overrode it individually (introduced during 24-01-04 quick
 * wins). That meant `RealtimeService.scheduleCommand` deducted exactly 1 CP for
 * virtually every strategic action — a project-wide regression hidden since the
 * CP fields were first introduced. commands.json held the manual-correct values
 * (40 / 80 / 160 / 320 / 640 / 800) but no runtime path consulted it.
 *
 * This table closes that gap without touching 81 individual command class files:
 *   - The default implementation of [BaseCommand.getCommandPointCost] now
 *     delegates to [CommandCostTable.get] keyed on the command's `actionName`.
 *   - Individual commands may still override `getCommandPointCost` directly when
 *     dynamic scaling is required (e.g. 작전계획 10~1280 based on execution time).
 *
 * ## Source of truth
 *
 * Values come from `backend/shared/src/main/resources/data/commands.json`,
 * which was generated from the gin7 manual appendix 戦略コマンド一覧表
 * (manual p69-78). When commands.json changes, update this table in lockstep —
 * both files are derived from the manual and must stay in sync.
 *
 * Unknown `actionName`s fall back to `1` so that unknown commands still execute
 * without becoming completely free, matching the pre-24-05 default.
 */
object CommandCostTable {

    /** Fallback CP cost for any command not listed below (legacy behavior). */
    const val DEFAULT_COST: Int = 1

    /**
     * actionName → CP cost.
     * Action names are the Korean labels declared in each command class's
     * `override val actionName` and registered in [Gin7CommandRegistry].
     */
    private val costs: Map<String, Int> = mapOf(
        // ---------- 작전커맨드 (Operations, MCP, 16) ----------
        "워프항행" to 40,          // manual p69 — variable by distance, base 40
        "연료보급" to 160,
        "성계내항행" to 160,
        "군기유지" to 80,
        "항공훈련" to 80,
        "육전훈련" to 80,
        "공전훈련" to 80,
        "육전전술훈련" to 80,
        "공전전술훈련" to 80,
        "경계출동" to 160,
        "무력진압" to 160,
        "분열행진" to 160,
        "징발" to 160,
        "특별경비" to 160,
        "육전대출격" to 80,
        "육전대철수" to 80,

        // ---------- 개인커맨드 (Personal, PCP, 15) ----------
        "원거리이동" to 10,
        "근거리이동" to 5,
        "퇴역" to 160,
        "지원" to 160,
        "망명" to 320,
        "회견" to 10,
        "수강" to 160,
        "병기연습" to 10,
        "반의" to 640,
        "모의" to 640,
        "설득" to 640,
        "반란" to 640,
        "참가" to 160,
        "자금투입" to 80,
        "기함구매" to 80,

        // ---------- 지휘커맨드 (Commander, MCP, 8) ----------
        // 작전계획/철회/발령 are variable (10~1280, 5~320, 1~320 per manual).
        // Their Kotlin classes should override getCommandPointCost for precise
        // scaling; we list the minimum floor here so static checks still work.
        "작전계획" to 10,
        "작전철회" to 5,
        "발령" to 1,
        "부대결성" to 320,
        "부대해산" to 160,
        "강의" to 160,
        "수송계획" to 80,
        "수송중지" to 80,

        // ---------- 병참커맨드 (Logistics, MCP, 6) ----------
        "완전수리" to 160,
        "완전보급" to 160,
        "재편성" to 160,
        "보충" to 160,
        "반출입" to 160,
        "할당" to 160,

        // ---------- 인사커맨드 (Personnel, PCP, 10) ----------
        "승진" to 160,
        "발탁" to 640,
        "강등" to 320,
        "서작" to 160,
        "서훈" to 160,   // also overridden in AwardDecorationCommand to keep CP literal near its effect
        "임명" to 160,
        "파면" to 160,
        "사임" to 80,
        "봉토수여" to 640,
        "봉토직할" to 640,

        // ---------- 정치커맨드 (Politics, PCP, 12) ----------
        "야회" to 320,
        "수렵" to 320,
        "회담" to 320,
        "담화" to 320,
        "연설" to 320,
        "국가목표" to 320,
        "납입률변경" to 320,
        "관세율변경" to 320,
        "분배" to 320,
        "처단" to 320,
        "외교" to 320,
        "통치목표" to 80,   // manual p72 — corrected from prior 320 (Gap B1)

        // ---------- 첩보커맨드 (Intelligence, MCP, 14) ----------
        "일제수색" to 160,
        "체포허가" to 800,   // manual p76 — corrected from 160 (Gap B2)
        "집행명령" to 800,   // manual p76 — corrected from 160 (Gap B3)
        "체포명령" to 160,
        "사열" to 160,
        "습격" to 160,
        "감시" to 160,
        "잠입공작" to 160,
        "탈출공작" to 160,
        "정보공작" to 160,
        "파괴공작" to 160,
        "선동공작" to 160,
        "침입공작" to 320,   // manual p77 — corrected from 160 (Gap B4)
        "귀환공작" to 320,   // manual p77 — corrected from 160 (Gap B5)

        // ---------- 공통 ----------
        "대기" to 0,
    )

    /**
     * Looks up the CP cost for a command by its action name.
     * Returns [DEFAULT_COST] (1) for unknown names.
     */
    fun get(actionName: String): Int = costs[actionName] ?: DEFAULT_COST

    /** Exposes the full table for diagnostics / tests. */
    fun snapshot(): Map<String, Int> = costs
}
