package com.openlogh.repository

import com.openlogh.entity.Event
import org.springframework.data.jpa.repository.JpaRepository

interface EventRepository : JpaRepository<Event, Long> {
    fun findBySessionId(sessionId: Long): List<Event>
    fun findBySessionIdAndTargetCodeOrderByPriorityDescIdAsc(sessionId: Long, targetCode: String): List<Event>
}
