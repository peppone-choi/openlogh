package com.openlogh.repository

import com.openlogh.entity.Planet
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface PlanetRepository : JpaRepository<Planet, Long> {
    fun findBySessionId(sessionId: Long): List<Planet>
    fun findByFactionId(factionId: Long): List<Planet>

    @Query("SELECT p FROM Planet p WHERE p.sessionId = :worldId")
    fun findByWorldId(@Param("worldId") worldId: Long): List<Planet>

    @Query("SELECT p FROM Planet p WHERE p.factionId = :nationId")
    fun findByNationId(@Param("nationId") nationId: Long): List<Planet>
}
