package com.openlogh.repository

import com.openlogh.entity.MessengerConnection
import org.springframework.data.jpa.repository.JpaRepository

interface MessengerConnectionRepository : JpaRepository<MessengerConnection, Long> {
    fun findBySessionIdAndCalleeIdAndStatus(sessionId: Long, calleeId: Long, status: String): List<MessengerConnection>
    fun findBySessionIdAndCallerIdAndStatus(sessionId: Long, callerId: Long, status: String): List<MessengerConnection>
    fun findBySessionIdAndCallerIdOrCalleeIdAndStatus(
        sessionId: Long,
        callerId: Long,
        calleeId: Long,
        status: String,
    ): List<MessengerConnection>
}
