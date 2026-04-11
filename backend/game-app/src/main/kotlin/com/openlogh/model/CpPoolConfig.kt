package com.openlogh.model

/**
 * Rank-based command point pool size configuration.
 *
 * Phase 24-05 (docs/03-analysis/gin7-manual-complete-gap.analysis.md §B):
 * Pool sizes are scaled to accommodate the real gin7 command costs (manual
 * p69-78, ranging 5 CP for 근거리이동 up to 800 CP for 체포허가/집행명령). The
 * pre-24-05 defaults (5..35) were meaningless because the runtime always
 * deducted exactly 1 CP regardless of command, so no pool ever drained.
 *
 * Now that [com.openlogh.command.CommandCostTable] feeds the real manual costs
 * into [com.openlogh.command.BaseCommand.getCommandPointCost], the pool sizes
 * must keep pace:
 *   - Rank 0 (Sub-Lieutenant) can afford small-scale personal actions
 *     (회견 10, 원거리이동 10, 근거리이동 5) but not 반란 / 체포허가.
 *   - Mid-tier officers (Captain..Commodore) can afford most 160-CP ops.
 *   - Senior officers (Admiral+) can sustain sequences of 640-CP commands.
 *   - Top tier (Reichsmarschall / Fleet Admiral) handles 800-CP intel ops
 *     comfortably — still not infinite, so timing matters.
 *
 * Regen rate (real-time 5 min per tick) is unchanged; only the cap scales.
 *
 * Both PCP (Political) and MCP (Military) share the same max pool per rank.
 */
object CpPoolConfig {

    /**
     * Max pool size indexed by rank level (0..10).
     *
     * Derived so that the top rank can execute one 800 CP command plus
     * several supporting actions before draining, matching how gin7 plays.
     *
     * - rank 0 (Sub-Lieutenant):   200  → movement, 회견, training
     * - rank 1 (Lieutenant):       300
     * - rank 2 (Lt. Commander):    400
     * - rank 3 (Commander):        500
     * - rank 4 (Captain):          700
     * - rank 5 (Commodore):        900  → 수강 / 징발 chain feasible
     * - rank 6 (Rear Admiral):    1200  → 반란 (640) feasible
     * - rank 7 (Vice Admiral):    1500
     * - rank 8 (Admiral):         1800
     * - rank 9 (Fleet Admiral):   2200
     * - rank 10 (Reichsmarschall):2600  → 체포허가 (800) + cushion
     */
    private val maxPoolByRank = intArrayOf(
        200,   // 0
        300,   // 1
        400,   // 2
        500,   // 3
        700,   // 4
        900,   // 5
        1200,  // 6
        1500,  // 7
        1800,  // 8
        2200,  // 9
        2600,  // 10
    )

    /**
     * Returns the maximum CP pool size for the given rank level.
     * Values outside 0..10 are clamped to the valid range.
     */
    fun getMaxPool(rankLevel: Int): Int {
        val clamped = rankLevel.coerceIn(0, maxPoolByRank.lastIndex)
        return maxPoolByRank[clamped]
    }

    /**
     * Convenience overload accepting Short, matching Officer.officerLevel type.
     */
    fun getMaxPool(rankLevel: Short): Int = getMaxPool(rankLevel.toInt())
}
