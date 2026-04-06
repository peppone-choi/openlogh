package com.openlogh.repository

import com.openlogh.entity.StarSystem
import org.springframework.data.jpa.repository.JpaRepository

interface StarSystemRepository : JpaRepository<StarSystem, Long> {
    fun findBySessionId(sessionId: Long): List<StarSystem>
    fun findBySessionIdAndMapStarId(sessionId: Long, mapStarId: Int): StarSystem?
    fun findBySessionIdAndFactionId(sessionId: Long, factionId: Long): List<StarSystem>
}
