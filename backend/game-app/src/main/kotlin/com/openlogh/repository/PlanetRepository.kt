package com.openlogh.repository

import com.openlogh.entity.Planet
import org.springframework.data.jpa.repository.JpaRepository

interface PlanetRepository : JpaRepository<Planet, Long> {
    fun findBySessionId(sessionId: Long): List<Planet>
    fun findByFactionId(factionId: Long): List<Planet>
}
