package com.openlogh.repository

import com.openlogh.entity.PlanetWarehouse
import org.springframework.data.jpa.repository.JpaRepository

interface PlanetWarehouseRepository : JpaRepository<PlanetWarehouse, Long> {
    fun findBySessionIdAndPlanetId(sessionId: Long, planetId: Long): PlanetWarehouse?
    fun findBySessionId(sessionId: Long): List<PlanetWarehouse>
    fun findByPlanetId(planetId: Long): PlanetWarehouse?
    fun findBySessionIdAndHasShipyardTrue(sessionId: Long): List<PlanetWarehouse>
}
