package com.openlogh.repository

import com.openlogh.entity.FleetWarehouse
import org.springframework.data.jpa.repository.JpaRepository

interface FleetWarehouseRepository : JpaRepository<FleetWarehouse, Long> {
    fun findBySessionIdAndFleetId(sessionId: Long, fleetId: Long): FleetWarehouse?
    fun findBySessionId(sessionId: Long): List<FleetWarehouse>
    fun findByFleetId(fleetId: Long): FleetWarehouse?
}
