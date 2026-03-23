package com.openlogh.repository

import com.openlogh.entity.Betting
import org.springframework.data.jpa.repository.JpaRepository

interface BettingRepository : JpaRepository<Betting, Long> {
    fun findBySessionId(sessionId: Long): List<Betting>
    fun findBySessionIdAndStatus(sessionId: Long, status: String): List<Betting>
}
