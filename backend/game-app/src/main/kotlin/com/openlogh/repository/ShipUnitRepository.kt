package com.openlogh.repository

import com.openlogh.entity.ShipUnit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ShipUnitRepository : JpaRepository<ShipUnit, Long> {

    fun findByFleetId(fleetId: Long): List<ShipUnit>

    fun findByFleetIdAndSlotIndex(fleetId: Long, slotIndex: Int): ShipUnit?

    fun findBySessionId(sessionId: Long): List<ShipUnit>

    fun findByFleetIdAndIsFlagshipTrue(fleetId: Long): ShipUnit?

    fun countByFleetId(fleetId: Long): Long

    fun deleteByFleetId(fleetId: Long)
}
