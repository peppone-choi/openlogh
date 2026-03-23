package com.openlogh.repository

import com.openlogh.entity.Board
import org.springframework.data.jpa.repository.JpaRepository

interface BoardRepository : JpaRepository<Board, Long> {
    fun findBySessionId(sessionId: Long): List<Board>
    fun findByFactionIdOrderByCreatedAtDesc(factionId: Long): List<Board>
    fun findBySessionIdAndFactionIdIsNullOrderByCreatedAtDesc(sessionId: Long): List<Board>
    fun findByAuthorGeneralId(authorGeneralId: Long): List<Board>
}
