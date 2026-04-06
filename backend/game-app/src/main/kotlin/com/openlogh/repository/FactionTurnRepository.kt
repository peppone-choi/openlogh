package com.openlogh.repository

import com.openlogh.entity.FactionTurn
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

/**
 * DEPRECATED: Legacy turn-based queue repository.
 * Not used in real-time mode — commands execute immediately with cooldowns.
 * Kept for CQRS layer compatibility only.
 */
interface FactionTurnRepository : JpaRepository<FactionTurn, Long> {
    fun findBySessionId(sessionId: Long): List<FactionTurn>
    fun findByFactionIdAndOfficerLevelOrderByTurnIdx(factionId: Long, officerLevel: Short): List<FactionTurn>

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM FactionTurn f WHERE f.sessionId = :sessionId")
    fun deleteBySessionId(sessionId: Long)

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM FactionTurn f WHERE f.factionId = :factionId")
    fun deleteByFactionId(factionId: Long)

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM FactionTurn f WHERE f.factionId = :factionId AND f.officerLevel = :officerLevel")
    fun deleteByFactionIdAndOfficerLevel(factionId: Long, officerLevel: Short)

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM FactionTurn f WHERE f.factionId = :factionId AND f.officerLevel = :officerLevel AND f.turnIdx IN :turnIdxList")
    fun deleteByFactionIdAndOfficerLevelAndTurnIdxIn(factionId: Long, officerLevel: Short, turnIdxList: Collection<Short>)

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE FactionTurn f SET f.turnIdx = f.turnIdx - 1 WHERE f.factionId = :factionId AND f.officerLevel = :officerLevel AND f.turnIdx > :consumedIdx")
    fun shiftTurnsDown(factionId: Long, officerLevel: Short, consumedIdx: Short)
}
