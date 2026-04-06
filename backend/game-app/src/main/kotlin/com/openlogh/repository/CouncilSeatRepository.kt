package com.openlogh.repository

import com.openlogh.entity.CouncilSeat
import org.springframework.data.jpa.repository.JpaRepository

interface CouncilSeatRepository : JpaRepository<CouncilSeat, Long> {

    fun findBySessionIdAndFactionId(sessionId: Long, factionId: Long): List<CouncilSeat>

    fun findBySessionIdAndFactionIdAndSeatCode(
        sessionId: Long,
        factionId: Long,
        seatCode: String,
    ): CouncilSeat?
}
