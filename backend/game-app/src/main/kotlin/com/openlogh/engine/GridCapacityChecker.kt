package com.openlogh.engine

import com.openlogh.entity.Fleet
import com.openlogh.repository.FleetRepository

/**
 * Phase 24-06 — gin7 manual p30: 300-unit/faction grid capacity enforcement.
 *
 * Gap: docs/03-analysis/gin7-manual-complete-gap.analysis.md §E39
 *
 * Manual spec (gin7 p30):
 *   한 그리드에 진입할 수 있는 함선 유닛 수는 1 진영당 300 유닛 이하로 제한된다.
 *   따라서 이미 같은 진영의 300 유닛이 배치된 그리드에는 추가 진입이 불가능하다.
 *
 * Interpretation for OpenLOGH:
 *   - "Grid" maps to the `planet_id` column (each planet is a discrete grid cell).
 *   - "Unit" = Fleet.currentUnits (one "unit" in gin7 represents 300 ships).
 *   - A faction can have at most 300 units (currentUnits summed across all fleets)
 *     at a single planet.
 *   - A fleet cannot enter the grid if its currentUnits + sum(existing units of
 *     same faction at that planet, excluding itself) > 300.
 *
 * This is stateless — call [canEnterGrid] before mutating Officer.planetId /
 * Fleet.planetId inside movement commands (WarpNavigation, IntraSystemNavigation).
 */
object GridCapacityChecker {

    /** gin7 manual p30: per-faction per-grid hard cap. */
    const val MAX_UNITS_PER_GRID_PER_FACTION: Int = 300

    /**
     * Checks whether [movingFleet] can enter [destPlanetId] without breaching
     * the 300-unit/faction cap. The moving fleet is excluded from the existing
     * sum (it may already be staged at the destination in edge cases).
     *
     * @return the number of units that would remain under the cap as a signed
     * value: positive = allowed, zero or negative = rejected.
     */
    fun availableCapacity(
        fleetRepository: FleetRepository,
        sessionId: Long,
        factionId: Long,
        destPlanetId: Long,
        movingFleetId: Long?,
    ): Int {
        val existing = fleetRepository
            .findBySessionIdAndPlanetIdAndFactionId(sessionId, destPlanetId, factionId)
            .filter { it.id != movingFleetId }
        val usedUnits = existing.sumOf { it.currentUnits.coerceAtLeast(0) }
        return MAX_UNITS_PER_GRID_PER_FACTION - usedUnits
    }

    /**
     * True iff [movingFleet] (or a solo-ship officer if movingFleet is null)
     * can fit into the destination grid under the 300-unit faction cap.
     */
    fun canEnterGrid(
        fleetRepository: FleetRepository,
        sessionId: Long,
        factionId: Long,
        destPlanetId: Long,
        movingFleet: Fleet?,
    ): Boolean {
        val available = availableCapacity(
            fleetRepository, sessionId, factionId, destPlanetId, movingFleet?.id
        )
        val incoming = movingFleet?.currentUnits?.coerceAtLeast(1) ?: 1
        return incoming <= available
    }
}
