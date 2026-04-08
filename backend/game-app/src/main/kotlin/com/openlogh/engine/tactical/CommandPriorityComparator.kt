package com.openlogh.engine.tactical

/**
 * Priority value object for command delegation ordering (CMD-02).
 *
 * Implements gin7's priority system for determining which officer gets
 * command authority when multiple candidates exist:
 *
 * 1. Online players always beat offline/NPC (D-11)
 * 2. Higher rank (officerLevel) wins among same online status
 * 3. Higher evaluationPoints wins among same rank (D-10)
 * 4. Higher meritPoints wins among same evaluation
 * 5. Lower officerId wins as tiebreak — older officer has seniority (D-09)
 *
 * Natural ordering: higher priority = greater value in compareTo.
 */
data class CommandPriority(
    val isOnline: Boolean,
    val rank: Int,
    val evaluation: Int,
    val merit: Int,
    val officerId: Long,
) : Comparable<CommandPriority> {

    override fun compareTo(other: CommandPriority): Int {
        // 1. Online beats offline (true > false)
        if (this.isOnline != other.isOnline) {
            return if (this.isOnline) 1 else -1
        }
        // 2. Higher rank wins
        val rankCmp = this.rank.compareTo(other.rank)
        if (rankCmp != 0) return rankCmp
        // 3. Higher evaluation wins
        val evalCmp = this.evaluation.compareTo(other.evaluation)
        if (evalCmp != 0) return evalCmp
        // 4. Higher merit wins
        val meritCmp = this.merit.compareTo(other.merit)
        if (meritCmp != 0) return meritCmp
        // 5. Lower officerId wins (older officer = seniority) — reversed comparison
        return other.officerId.compareTo(this.officerId)
    }
}
