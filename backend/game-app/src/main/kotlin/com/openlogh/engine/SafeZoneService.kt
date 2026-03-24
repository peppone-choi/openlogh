package com.openlogh.engine

import com.openlogh.entity.Officer
import org.springframework.stereotype.Service

/**
 * Feature 13.3 - Safe Zone
 *
 * Officers logged out at home (자택) or hotel (호텔) cannot die in combat.
 * Check: if officer.locationState == "planet" && officer is at residential/hotel facility.
 * BattleEngine should skip death roll for safe-zone officers.
 */
@Service
class SafeZoneService {
    companion object {
        private val SAFE_FACILITY_TYPES = setOf("residence", "hotel", "residential", "home")
    }

    /**
     * Returns true if the officer is in a safe zone and should be exempt from death rolls.
     */
    fun isInSafeZone(officer: Officer): Boolean {
        if (officer.locationState != "planet") return false

        val facilityType = officer.meta["facilityType"] as? String ?: return false
        return facilityType in SAFE_FACILITY_TYPES
    }

    /**
     * Returns true if the officer should be protected from death in the current combat context.
     * Used by BattleEngine before processing death rolls.
     */
    fun isProtectedFromDeath(officer: Officer): Boolean {
        // Only player officers (userId != null) benefit from the safe zone
        if (officer.userId == null) return false
        return isInSafeZone(officer)
    }
}
