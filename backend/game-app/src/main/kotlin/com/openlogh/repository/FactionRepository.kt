package com.openlogh.repository

import com.openlogh.entity.Faction
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface FactionRepository : JpaRepository<Faction, Long> {
    fun findBySessionId(sessionId: Long): List<Faction>
    fun findBySessionIdAndName(sessionId: Long, name: String): Faction?

    @Query("SELECT COALESCE(AVG(f.officerCount), 0) FROM Faction f WHERE f.sessionId = :sessionId AND f.factionRank > 0")
    fun getAverageGennum(sessionId: Long): Double
}
