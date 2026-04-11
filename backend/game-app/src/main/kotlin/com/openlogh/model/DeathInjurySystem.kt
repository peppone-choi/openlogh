package com.openlogh.model

/**
 * Death and Injury system (전사) for tactical combat.
 *
 * - Flagship destroyed -> character injury (not death by default)
 * - Injury -> instant warp to return planet (귀환성)
 * - Return planet configurable in system settings
 * - Death is optional in gin7; we implement as injury by default
 */
data class InjuryEvent(
    /** The injured officer's ID */
    val officerId: Long,
    /** The officer's name */
    val officerName: String,
    /** Injury severity (0 = none, 1-80 scale from Officer entity) */
    val severity: Int,
    /** The return planet ID (귀환성) where the officer warps to */
    val returnPlanetId: Long,
    /** Whether this was a death event (false = injury only, default behavior) */
    val isDeath: Boolean = false,
    /** Tick number when injury occurred */
    val tick: Int = 0,
) {
    companion object {
        /** Default injury severity when flagship is destroyed */
        const val DEFAULT_INJURY_SEVERITY = 20

        /** Maximum injury value */
        const val MAX_INJURY = 80

        /**
         * Calculate injury severity based on damage taken.
         * Higher damage = more severe injury, capped at MAX_INJURY.
         */
        fun calculateSeverity(currentInjury: Int, damageRatio: Double): Int {
            val additionalInjury = (DEFAULT_INJURY_SEVERITY * damageRatio).toInt().coerceAtLeast(5)
            return (currentInjury + additionalInjury).coerceAtMost(MAX_INJURY)
        }

        /**
         * Resolve return planet for the injured officer.
         * Priority: officer's configured return planet > faction capital > current planet
         */
        fun resolveReturnPlanet(
            configuredReturnPlanetId: Long?,
            factionCapitalPlanetId: Long?,
            currentPlanetId: Long,
        ): Long = configuredReturnPlanetId
            ?: factionCapitalPlanetId
            ?: currentPlanetId
    }
}
