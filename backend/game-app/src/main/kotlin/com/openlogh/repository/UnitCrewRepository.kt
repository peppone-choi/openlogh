package com.openlogh.repository

import com.openlogh.entity.UnitCrew
import org.springframework.data.jpa.repository.JpaRepository

interface UnitCrewRepository : JpaRepository<UnitCrew, Long> {
    fun findBySessionId(sessionId: Long): List<UnitCrew>
    fun findByFleetId(fleetId: Long): List<UnitCrew>
    fun findByOfficerId(officerId: Long): List<UnitCrew>
    fun findByFleetIdAndSlotRole(fleetId: Long, slotRole: String): UnitCrew?
    fun deleteByFleetIdAndOfficerId(fleetId: Long, officerId: Long)
    fun deleteBySessionId(sessionId: Long)
    fun countByFleetId(fleetId: Long): Long
}
