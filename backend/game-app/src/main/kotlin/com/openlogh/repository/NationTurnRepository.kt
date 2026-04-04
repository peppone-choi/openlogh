package com.openlogh.repository

import com.openlogh.entity.NationTurn
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface NationTurnRepository : JpaRepository<NationTurn, Long> {
    fun findByWorldId(worldId: Long): List<NationTurn>
    fun findByNationIdAndOfficerLevelOrderByTurnIdx(nationId: Long, officerLevel: Short): List<NationTurn>

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM NationTurn n WHERE n.worldId = :worldId")
    fun deleteByWorldId(worldId: Long)

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM NationTurn n WHERE n.nationId = :nationId")
    fun deleteByNationId(nationId: Long)

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM NationTurn n WHERE n.nationId = :nationId AND n.officerLevel = :officerLevel")
    fun deleteByNationIdAndOfficerLevel(nationId: Long, officerLevel: Short)

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM NationTurn n WHERE n.nationId = :nationId AND n.officerLevel = :officerLevel AND n.turnIdx IN :turnIdxList")
    fun deleteByNationIdAndOfficerLevelAndTurnIdxIn(nationId: Long, officerLevel: Short, turnIdxList: Collection<Short>)

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE NationTurn n SET n.turnIdx = n.turnIdx - 1 WHERE n.nationId = :nationId AND n.officerLevel = :officerLevel AND n.turnIdx > :consumedIdx")
    fun shiftTurnsDown(nationId: Long, officerLevel: Short, consumedIdx: Short)
}
