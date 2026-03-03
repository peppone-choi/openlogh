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
}
