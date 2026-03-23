package com.openlogh.repository

import com.openlogh.entity.OfficerTurn
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface OfficerTurnRepository : JpaRepository<OfficerTurn, Long> {
    fun findBySessionId(sessionId: Long): List<OfficerTurn>
    fun findByOfficerIdOrderByTurnIdx(officerId: Long): List<OfficerTurn>

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM OfficerTurn g WHERE g.sessionId = :sessionId")
    fun deleteByWorldId(sessionId: Long)

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM OfficerTurn g WHERE g.officerId = :officerId")
    fun deleteByOfficerId(officerId: Long)

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM OfficerTurn g WHERE g.officerId = :officerId AND g.turnIdx IN :turnIdxList")
    fun deleteByOfficerIdAndTurnIdxIn(officerId: Long, turnIdxList: Collection<Short>)

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OfficerTurn g SET g.turnIdx = g.turnIdx - 1 WHERE g.officerId = :officerId AND g.turnIdx > :consumedIdx")
    fun shiftTurnsDown(officerId: Long, consumedIdx: Short)

    @Query("SELECT g FROM OfficerTurn g WHERE g.officerId = :generalId ORDER BY g.turnIdx")
    fun findByGeneralIdOrderByTurnIdx(@Param("generalId") generalId: Long): List<OfficerTurn>

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM OfficerTurn g WHERE g.officerId = :generalId")
    fun deleteByGeneralId(@Param("generalId") generalId: Long)
}
