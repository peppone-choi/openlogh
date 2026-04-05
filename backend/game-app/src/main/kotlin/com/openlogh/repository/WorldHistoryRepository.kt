package com.openlogh.repository

import com.openlogh.entity.WorldHistory
import org.springframework.data.jpa.repository.JpaRepository

interface WorldHistoryRepository : JpaRepository<WorldHistory, Long> {
    fun findBySessionId(sessionId: Long): List<WorldHistory>
    fun findBySessionIdAndYearAndMonth(sessionId: Long, year: Short, month: Short): List<WorldHistory>
    fun findBySessionIdOrderByCreatedAtDesc(sessionId: Long): List<WorldHistory>
    fun findBySessionIdAndEventType(sessionId: Long, eventType: String): List<WorldHistory>
}
