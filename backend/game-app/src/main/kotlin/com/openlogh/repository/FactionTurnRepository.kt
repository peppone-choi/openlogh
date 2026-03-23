package com.openlogh.repository

import com.openlogh.entity.FactionTurn
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface FactionTurnRepository : JpaRepository<FactionTurn, Long> {
    fun findBySessionId(sessionId: Long): List<FactionTurn>
    fun findByFactionIdAndOfficerLevelOrderByTurnIdx(factionId: Long, officerLevel: Short): List<FactionTurn>

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM FactionTurn n WHERE n.sessionId = :sessionId")
    fun deleteByWorldId(sessionId: Long)

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM FactionTurn n WHERE n.factionId = :factionId")
    fun deleteByFactionId(factionId: Long)

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM FactionTurn n WHERE n.factionId = :factionId AND n.officerLevel = :officerLevel")
    fun deleteByFactionIdAndOfficerLevel(factionId: Long, officerLevel: Short)

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM FactionTurn n WHERE n.factionId = :factionId AND n.officerLevel = :officerLevel AND n.turnIdx IN :turnIdxList")
    fun deleteByFactionIdAndOfficerLevelAndTurnIdxIn(factionId: Long, officerLevel: Short, turnIdxList: Collection<Short>)

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE FactionTurn n SET n.turnIdx = n.turnIdx - 1 WHERE n.factionId = :factionId AND n.officerLevel = :officerLevel AND n.turnIdx > :consumedIdx")
    fun shiftTurnsDown(factionId: Long, officerLevel: Short, consumedIdx: Short)

    @Query("SELECT n FROM FactionTurn n WHERE n.factionId = :nationId AND n.officerLevel = :officerLevel ORDER BY n.turnIdx")
    fun findByNationIdAndOfficerLevelOrderByTurnIdx(@Param("nationId") nationId: Long, @Param("officerLevel") officerLevel: Short): List<FactionTurn>

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM FactionTurn n WHERE n.factionId = :nationId AND n.officerLevel = :officerLevel")
    fun deleteByNationIdAndOfficerLevel(@Param("nationId") nationId: Long, @Param("officerLevel") officerLevel: Short)
}
