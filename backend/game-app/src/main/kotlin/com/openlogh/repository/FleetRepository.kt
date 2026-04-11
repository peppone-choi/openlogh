package com.openlogh.repository

import com.openlogh.entity.Fleet
import org.springframework.data.jpa.repository.JpaRepository

interface FleetRepository : JpaRepository<Fleet, Long> {
    fun findBySessionId(sessionId: Long): List<Fleet>
    fun findByFactionId(factionId: Long): List<Fleet>
    fun findBySessionIdAndFactionId(sessionId: Long, factionId: Long): List<Fleet>
    fun findByFactionIdAndUnitType(factionId: Long, unitType: String): List<Fleet>
    fun findByPlanetId(planetId: Long): List<Fleet>
    fun countByFactionIdAndUnitType(factionId: Long, unitType: String): Long

    /**
     * Phase 24-06 (gin7 manual p30): 300-unit/faction grid capacity check.
     * Returns all fleets at the given planet (grid cell) belonging to a faction
     * so the caller can sum currentUnits across them and decide whether a new
     * fleet can enter the grid.
     */
    fun findBySessionIdAndPlanetIdAndFactionId(
        sessionId: Long,
        planetId: Long,
        factionId: Long,
    ): List<Fleet>
}
