package com.openlogh.repository

import com.openlogh.entity.Diplomacy
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface DiplomacyRepository : JpaRepository<Diplomacy, Long> {
    fun findBySessionId(sessionId: Long): List<Diplomacy>
    fun findBySessionIdAndIsDeadFalse(sessionId: Long): List<Diplomacy>
    fun findBySessionIdAndSrcFactionIdOrDestFactionId(sessionId: Long, srcFactionId: Long, destFactionId: Long): List<Diplomacy>

    @Query("""
        SELECT d FROM Diplomacy d
        WHERE d.sessionId = :sessionId AND d.isDead = false
          AND ((d.srcFactionId = :factionA AND d.destFactionId = :factionB)
            OR (d.srcFactionId = :factionB AND d.destFactionId = :factionA))
          AND d.stateCode = :stateCode
    """)
    fun findActiveRelation(
        @Param("sessionId") sessionId: Long,
        @Param("factionA") factionA: Long,
        @Param("factionB") factionB: Long,
        @Param("stateCode") stateCode: String,
    ): Diplomacy?

    @Query("""
        SELECT d FROM Diplomacy d
        WHERE d.sessionId = :sessionId AND d.isDead = false
          AND ((d.srcFactionId = :factionA AND d.destFactionId = :factionB)
            OR (d.srcFactionId = :factionB AND d.destFactionId = :factionA))
    """)
    fun findActiveRelationsBetween(
        @Param("sessionId") sessionId: Long,
        @Param("factionA") factionA: Long,
        @Param("factionB") factionB: Long,
    ): List<Diplomacy>
}
