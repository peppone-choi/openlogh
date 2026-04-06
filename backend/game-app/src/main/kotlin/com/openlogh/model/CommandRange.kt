package com.openlogh.model

/**
 * Command Range Circle (指揮範囲) details for tactical combat.
 *
 * - Expands over time from 0 to max value (based on flagship command stat)
 * - Resets to 0 on every command issue, then re-expands
 * - Expansion rate = 指揮 (command) stat value per tick
 * - Max range = flagship capability
 * - Units outside max range: continue current command, stop when command ends
 * - Solo ships (独行艦) have no command range circle concept
 */
data class CommandRange(
    /** Current command range radius */
    val currentRange: Double = 0.0,
    /** Maximum command range (determined by flagship capability) */
    val maxRange: Double = 100.0,
    /** Expansion rate per tick (based on command stat) */
    val expansionRate: Double = 1.0,
    /** Whether this unit has command range (solo ships do not) */
    val hasCommandRange: Boolean = true,
) {
    /**
     * Advance one tick: expand range toward max.
     */
    fun tick(): CommandRange {
        if (!hasCommandRange) return this
        val newRange = (currentRange + expansionRate).coerceAtMost(maxRange)
        return copy(currentRange = newRange)
    }

    /**
     * Reset range to 0 (called on every command issue).
     */
    fun resetOnCommand(): CommandRange {
        if (!hasCommandRange) return this
        return copy(currentRange = 0.0)
    }

    /**
     * Check if a position is within command range.
     */
    fun isInRange(distance: Double): Boolean {
        if (!hasCommandRange) return false
        return distance <= currentRange
    }

    /**
     * Check if a position is within max range.
     * Units outside max range will continue their current command but stop when it ends.
     */
    fun isInMaxRange(distance: Double): Boolean {
        if (!hasCommandRange) return false
        return distance <= maxRange
    }

    companion object {
        /**
         * Create CommandRange from officer stats and flagship.
         * @param commandStat Officer's command (指揮) stat value
         * @param flagshipMaxRange Maximum range from flagship hardware
         * @param isSolo Whether this is a solo ship (no command circle)
         */
        fun create(commandStat: Int, flagshipMaxRange: Double, isSolo: Boolean = false): CommandRange {
            if (isSolo) return CommandRange(hasCommandRange = false)
            return CommandRange(
                currentRange = 0.0,
                maxRange = flagshipMaxRange,
                expansionRate = commandStat / 100.0,
                hasCommandRange = true,
            )
        }
    }
}
