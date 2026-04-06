package com.openlogh.repository

import com.openlogh.entity.Officer
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.OffsetDateTime

interface OfficerRepository : JpaRepository<Officer, Long> {
    fun findBySessionId(sessionId: Long): List<Officer>
    fun findByFactionId(factionId: Long): List<Officer>
    fun findByPlanetId(planetId: Long): List<Officer>
    fun findByUserId(userId: Long): List<Officer>
    fun findBySessionIdAndUserId(sessionId: Long, userId: Long): List<Officer>
    fun findBySessionIdAndPlanetIdIn(sessionId: Long, planetIds: List<Long>): List<Officer>
    fun findBySessionIdAndCommandEndTimeBefore(sessionId: Long, time: OffsetDateTime): List<Officer>
    fun findByFleetId(fleetId: Long): List<Officer>
    fun findBySessionIdAndFactionId(sessionId: Long, factionId: Long): List<Officer>
    fun findBySessionIdAndPlanetId(sessionId: Long, planetId: Long): List<Officer>
    fun findByNameAndSessionId(name: String, sessionId: Long): Officer?

    /**
     * Get average stats for officers in a faction.
     */
    @Query(
        """
        select new com.openlogh.repository.OfficerAverageStats(
            coalesce(cast(avg(o.experience) as integer), 0),
            coalesce(cast(avg(o.dedication) as integer), 0)
        )
        from Officer o
        where o.sessionId = :sessionId and o.factionId = :factionId
        """,
    )
    fun getAverageStats(
        @Param("sessionId") sessionId: Long,
        @Param("factionId") factionId: Long,
    ): OfficerAverageStats
}

data class OfficerAverageStats(
    val experience: Int = 0,
    val dedication: Int = 0,
)
