package com.openlogh.repository

import com.openlogh.entity.Vote
import org.springframework.data.jpa.repository.JpaRepository

interface VoteRepository : JpaRepository<Vote, Long> {
    fun findBySessionId(sessionId: Long): List<Vote>
    fun findByFactionId(factionId: Long): List<Vote>
    fun findBySessionIdAndStatus(sessionId: Long, status: String): List<Vote>
}
