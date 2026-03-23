package com.openlogh.repository

import com.openlogh.entity.OfficerAccessLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface OfficerAccessLogRepository : JpaRepository<OfficerAccessLog, Long> {
    fun findByOfficerId(officerId: Long): List<OfficerAccessLog>
    fun findBySessionId(sessionId: Long): List<OfficerAccessLog>
    fun findByOfficerIdOrderByAccessedAtDesc(officerId: Long): List<OfficerAccessLog>

    @Query(
        """
        SELECT l FROM OfficerAccessLog l
        JOIN Officer g ON l.officerId = g.id
        WHERE l.sessionId = :sessionId
        ORDER BY l.refresh DESC
        """
    )
    fun findTopRefreshersByWorldId(sessionId: Long): List<OfficerAccessLog>

    @Query("SELECT COALESCE(SUM(l.refresh), 0) FROM OfficerAccessLog l WHERE l.sessionId = :sessionId")
    fun sumRefreshByWorldId(sessionId: Long): Long

    @Query("SELECT COALESCE(SUM(l.refreshScoreTotal), 0) FROM OfficerAccessLog l WHERE l.sessionId = :sessionId")
    fun sumRefreshScoreTotalByWorldId(sessionId: Long): Long
}
