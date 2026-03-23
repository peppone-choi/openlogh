package com.openlogh.repository

import com.openlogh.entity.Officer
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.OffsetDateTime

interface OfficerRepository : JpaRepository<Officer, Long> {
    fun findBySessionId(sessionId: Long): List<Officer>
    @Query("SELECT g FROM Officer g WHERE g.factionId = :factionId")
    fun findByNationId(@Param("factionId") factionId: Long): List<Officer>
    fun findByFactionId(factionId: Long): List<Officer>
    @Query("SELECT g FROM Officer g WHERE g.planetId = :planetId")
    fun findByCityId(@Param("planetId") planetId: Long): List<Officer>
    fun findByUserId(userId: Long): List<Officer>
    fun findBySessionIdAndUserId(sessionId: Long, userId: Long): List<Officer>
    @Query("SELECT g FROM Officer g WHERE g.sessionId = :sessionId AND g.planetId IN :planetIds")
    fun findBySessionIdAndCityIdIn(@Param("sessionId") sessionId: Long, @Param("planetIds") planetIds: List<Long>): List<Officer>
    fun findBySessionIdAndCommandEndTimeBefore(sessionId: Long, time: OffsetDateTime): List<Officer>
    @Query("SELECT g FROM Officer g WHERE g.fleetId = :fleetId")
    fun findByTroopId(@Param("fleetId") fleetId: Long): List<Officer>
    @Query("SELECT g FROM Officer g WHERE g.sessionId = :sessionId AND g.factionId = :factionId")
    fun findBySessionIdAndNationId(@Param("sessionId") sessionId: Long, @Param("factionId") factionId: Long): List<Officer>
    @Query("SELECT g FROM Officer g WHERE g.name = :name AND g.sessionId = :worldId")
    fun findByNameAndWorldId(@Param("name") name: String, @Param("worldId") worldId: Long): Officer?

    @Query("SELECT g FROM Officer g WHERE g.sessionId = :worldId")
    fun findByWorldId(@Param("worldId") worldId: Long): List<Officer>

    @Query("SELECT g FROM Officer g WHERE g.sessionId = :worldId AND g.factionId = :nationId")
    fun findByWorldIdAndNationId(@Param("worldId") worldId: Long, @Param("nationId") nationId: Long): List<Officer>

    @Query(
        """
        select new com.openlogh.repository.OfficerAverageStats(
            coalesce(cast(avg(g.experience) as integer), 0),
            coalesce(cast(avg(g.dedication) as integer), 0)
        )
        from Officer g
        where g.sessionId = :sessionId and g.factionId = :factionId
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
