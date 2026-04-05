package com.openlogh.repository

import com.openlogh.entity.Fleet
import org.springframework.data.jpa.repository.JpaRepository

interface FleetRepository : JpaRepository<Fleet, Long> {
    fun findBySessionId(sessionId: Long): List<Fleet>
    fun findByFactionId(factionId: Long): List<Fleet>
}
