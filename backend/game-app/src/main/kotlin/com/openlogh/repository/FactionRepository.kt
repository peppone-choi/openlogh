package com.openlogh.repository

import com.openlogh.entity.Faction
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface FactionRepository : JpaRepository<Faction, Long> {
    fun findBySessionId(sessionId: Long): List<Faction>
    fun findBySessionIdAndName(sessionId: Long, name: String): Faction?

    @Query("SELECT f FROM Faction f WHERE f.sessionId = :worldId")
    fun findByWorldId(@Param("worldId") worldId: Long): List<Faction>

    @Query("SELECT COALESCE(AVG(f.officerCount), 0) FROM Faction f WHERE f.sessionId = :sessionId AND f.factionRank > 0")
    fun getAverageOfficerCount(sessionId: Long): Double
}
