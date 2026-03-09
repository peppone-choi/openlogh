package com.opensam.repository

import com.opensam.entity.GeneralTurn
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface GeneralTurnRepository : JpaRepository<GeneralTurn, Long> {
    fun findByWorldId(worldId: Long): List<GeneralTurn>
    fun findByGeneralIdOrderByTurnIdx(generalId: Long): List<GeneralTurn>

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM GeneralTurn g WHERE g.worldId = :worldId")
    fun deleteByWorldId(worldId: Long)

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM GeneralTurn g WHERE g.generalId = :generalId")
    fun deleteByGeneralId(generalId: Long)

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM GeneralTurn g WHERE g.generalId = :generalId AND g.turnIdx IN :turnIdxList")
    fun deleteByGeneralIdAndTurnIdxIn(generalId: Long, turnIdxList: Collection<Short>)

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE GeneralTurn g SET g.turnIdx = g.turnIdx - 1 WHERE g.generalId = :generalId AND g.turnIdx > :consumedIdx")
    fun shiftTurnsDown(generalId: Long, consumedIdx: Short)
}
