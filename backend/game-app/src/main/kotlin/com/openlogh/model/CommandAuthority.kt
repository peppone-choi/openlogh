package com.openlogh.model

/**
 * Command Authority Transfer system (지휘권) for tactical combat.
 *
 * Auto-assignment priority when entering tactical battle:
 *   1. Online players first
 *   2. Higher rank
 *   3. Higher evaluation points
 *   4. Higher merit points
 *
 * Manual transfer rules:
 * - Target must be outside other flagship's command circle
 * - Target must be fully stopped
 */
data class CommandAuthority(
    /** Officer ID of the current commander */
    val commanderId: Long,
    /** Fleet ID under this command */
    val fleetId: Long,
    /** Faction ID */
    val factionId: Long,
    /** Whether commander is an online player */
    val isOnline: Boolean,
    /** Officer rank level (higher = more authority) */
    val rankLevel: Int,
    /** Evaluation points (평가점) */
    val evaluationPoints: Int,
    /** Merit points (공적점) */
    val meritPoints: Int,
) : Comparable<CommandAuthority> {

    /**
     * Natural ordering: highest priority first.
     * 1. Online > Offline
     * 2. Higher rank
     * 3. Higher evaluation points
     * 4. Higher merit points
     */
    override fun compareTo(other: CommandAuthority): Int {
        // Online first (true > false in descending)
        val onlineCmp = other.isOnline.compareTo(this.isOnline)
        if (onlineCmp != 0) return onlineCmp

        val rankCmp = other.rankLevel.compareTo(this.rankLevel)
        if (rankCmp != 0) return rankCmp

        val evalCmp = other.evaluationPoints.compareTo(this.evaluationPoints)
        if (evalCmp != 0) return evalCmp

        return other.meritPoints.compareTo(this.meritPoints)
    }

    companion object {
        /**
         * Determine the commander from a list of candidates.
         * Returns the highest-priority authority.
         */
        fun resolveCommander(candidates: List<CommandAuthority>): CommandAuthority? =
            candidates.minOrNull()

        /**
         * Check if manual transfer is valid.
         * @param targetIsStopped Whether the target officer's fleet is fully stopped
         * @param targetIsInOtherCommandCircle Whether the target is inside another flagship's command circle
         */
        fun canManualTransfer(targetIsStopped: Boolean, targetIsInOtherCommandCircle: Boolean): Boolean =
            targetIsStopped && !targetIsInOtherCommandCircle
    }
}
