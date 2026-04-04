package com.openlogh.repository

import com.openlogh.entity.Board
import org.springframework.data.jpa.repository.JpaRepository

interface BoardRepository : JpaRepository<Board, Long> {
    fun findByWorldId(worldId: Long): List<Board>
    fun findByNationIdOrderByCreatedAtDesc(nationId: Long): List<Board>
    fun findByWorldIdAndNationIdIsNullOrderByCreatedAtDesc(worldId: Long): List<Board>
    fun findByAuthorGeneralId(authorGeneralId: Long): List<Board>
}
