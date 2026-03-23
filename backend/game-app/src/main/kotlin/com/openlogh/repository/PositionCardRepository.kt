package com.openlogh.repository

import com.openlogh.entity.PositionCard
import org.springframework.data.jpa.repository.JpaRepository

interface PositionCardRepository : JpaRepository<PositionCard, Long> {
    fun findBySessionId(sessionId: Long): List<PositionCard>
    fun findByOfficerId(officerId: Long): List<PositionCard>
    fun findBySessionIdAndOfficerId(sessionId: Long, officerId: Long): List<PositionCard>
    fun findBySessionIdAndPositionType(sessionId: Long, positionType: String): List<PositionCard>
}
