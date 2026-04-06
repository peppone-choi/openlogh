package com.openlogh.model

/**
 * Rank-based command point pool size configuration.
 *
 * Both PCP (Political Command Points) and MCP (Military Command Points)
 * share the same max pool size per rank level. Higher ranks can accumulate
 * more command points.
 */
object CpPoolConfig {

    /**
     * Max pool size indexed by rank level (0..10).
     * rank 0 (Sub-Lieutenant) = 5, rank 10 (Reichsmarschall/Fleet Admiral) = 35
     */
    private val maxPoolByRank = intArrayOf(5, 6, 7, 8, 10, 12, 15, 18, 22, 27, 35)

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
