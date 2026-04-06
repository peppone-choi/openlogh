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
}
