package com.openlogh.repository

import com.openlogh.entity.Tournament
import org.springframework.data.jpa.repository.JpaRepository

interface TournamentRepository : JpaRepository<Tournament, Long> {
    fun findBySessionId(sessionId: Long): List<Tournament>
    fun findBySessionIdOrderByRoundAscBracketPositionAsc(sessionId: Long): List<Tournament>
    fun findByOfficerId(officerId: Long): List<Tournament>
    fun findBySessionIdAndRoundOrderByBracketPositionAsc(sessionId: Long, round: Short): List<Tournament>
    fun findBySessionIdAndRound(sessionId: Long, round: Short): List<Tournament>
    fun deleteBySessionId(sessionId: Long): Long
}
