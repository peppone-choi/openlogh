package com.openlogh.repository

import com.openlogh.entity.Diplomacy
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface DiplomacyRepository : JpaRepository<Diplomacy, Long> {
    fun findBySessionId(sessionId: Long): List<Diplomacy>
    fun findBySessionIdAndIsDeadFalse(sessionId: Long): List<Diplomacy>
    fun findBySessionIdAndSrcNationIdOrDestFactionId(sessionId: Long, srcNationId: Long, destFactionId: Long): List<Diplomacy>

    @Query("SELECT d FROM Diplomacy d WHERE d.sessionId = :worldId")
    fun findByWorldId(@Param("worldId") worldId: Long): List<Diplomacy>

    @Query("SELECT d FROM Diplomacy d WHERE d.sessionId = :worldId AND d.isDead = false")
    fun findByWorldIdAndIsDeadFalse(@Param("worldId") worldId: Long): List<Diplomacy>

    @Query("SELECT d FROM Diplomacy d WHERE d.sessionId = :worldId AND (d.srcNationId = :srcNationId OR d.destFactionId = :destNationId)")
    fun findByWorldIdAndSrcNationIdOrDestNationId(
        @Param("worldId") worldId: Long,
        @Param("srcNationId") srcNationId: Long,
        @Param("destNationId") destNationId: Long,
    ): List<Diplomacy>

    @Query("""
        SELECT d FROM Diplomacy d
        WHERE d.sessionId = :sessionId AND d.isDead = false
          AND ((d.srcNationId = :nationA AND d.destFactionId = :nationB)
            OR (d.srcNationId = :nationB AND d.destFactionId = :nationA))
          AND d.stateCode = :stateCode
    """)
    fun findActiveRelation(
        @Param("sessionId") sessionId: Long,
        @Param("nationA") nationA: Long,
        @Param("nationB") nationB: Long,
        @Param("stateCode") stateCode: String,
    ): Diplomacy?

    @Query("""
        SELECT d FROM Diplomacy d
        WHERE d.sessionId = :sessionId AND d.isDead = false
          AND ((d.srcNationId = :nationA AND d.destFactionId = :nationB)
            OR (d.srcNationId = :nationB AND d.destFactionId = :nationA))
    """)
    fun findActiveRelationsBetween(
        @Param("sessionId") sessionId: Long,
        @Param("nationA") nationA: Long,
        @Param("nationB") nationB: Long,
    ): List<Diplomacy>
}
