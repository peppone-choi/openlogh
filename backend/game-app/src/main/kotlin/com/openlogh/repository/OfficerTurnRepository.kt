package com.openlogh.repository

import com.openlogh.entity.OfficerTurn
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

/**
 * DEPRECATED: Legacy turn-based queue repository.
 * Not used in real-time mode — commands execute immediately with cooldowns.
 * Kept for CQRS layer compatibility only.
 */
interface OfficerTurnRepository : JpaRepository<OfficerTurn, Long> {
    fun findBySessionId(sessionId: Long): List<OfficerTurn>
    fun findByOfficerIdOrderByTurnIdx(officerId: Long): List<OfficerTurn>

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM OfficerTurn o WHERE o.sessionId = :sessionId")
    fun deleteBySessionId(sessionId: Long)

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM OfficerTurn o WHERE o.officerId = :officerId")
    fun deleteByOfficerId(officerId: Long)

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM OfficerTurn o WHERE o.officerId = :officerId AND o.turnIdx IN :turnIdxList")
    fun deleteByOfficerIdAndTurnIdxIn(officerId: Long, turnIdxList: Collection<Short>)

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OfficerTurn o SET o.turnIdx = o.turnIdx - 1 WHERE o.officerId = :officerId AND o.turnIdx > :consumedIdx")
    fun shiftTurnsDown(officerId: Long, consumedIdx: Short)
}
