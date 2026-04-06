package com.openlogh.repository

import com.openlogh.entity.CoupEvent
import org.springframework.data.jpa.repository.JpaRepository

interface CoupEventRepository : JpaRepository<CoupEvent, Long> {

    fun findBySessionIdAndFactionIdAndPhaseIn(
        sessionId: Long,
        factionId: Long,
        phases: List<String>,
    ): List<CoupEvent>

    fun findBySessionIdAndPhaseIn(
        sessionId: Long,
        phases: List<String>,
    ): List<CoupEvent>

    fun findBySessionIdAndFactionIdAndPhase(
        sessionId: Long,
        factionId: Long,
        phase: String,
    ): CoupEvent?
}
