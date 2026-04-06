package com.openlogh.repository

import com.openlogh.entity.SessionRanking
import org.springframework.data.jpa.repository.JpaRepository

interface SessionRankingRepository : JpaRepository<SessionRanking, Long> {
    fun findBySessionIdOrderByScoreDesc(sessionId: Long): List<SessionRanking>
    fun findBySessionId(sessionId: Long): List<SessionRanking>
}
