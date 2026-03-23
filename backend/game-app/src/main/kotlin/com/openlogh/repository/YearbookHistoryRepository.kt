package com.openlogh.repository

import com.openlogh.entity.YearbookHistory
import org.springframework.data.jpa.repository.JpaRepository

interface YearbookHistoryRepository : JpaRepository<YearbookHistory, Long> {
    fun findBySessionIdAndYearAndMonth(sessionId: Long, year: Short, month: Short): YearbookHistory?
}
