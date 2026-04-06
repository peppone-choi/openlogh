package com.openlogh.repository

import com.openlogh.entity.Election
import org.springframework.data.jpa.repository.JpaRepository

interface ElectionRepository : JpaRepository<Election, Long> {

    fun findBySessionIdAndFactionIdAndIsCompleted(
        sessionId: Long,
        factionId: Long,
        isCompleted: Boolean,
    ): List<Election>

    fun findBySessionIdAndIsCompleted(
        sessionId: Long,
        isCompleted: Boolean,
    ): List<Election>
}
