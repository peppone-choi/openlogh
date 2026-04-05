package com.openlogh.repository

import com.openlogh.entity.BoardComment
import org.springframework.data.jpa.repository.JpaRepository

interface BoardCommentRepository : JpaRepository<BoardComment, Long> {
    fun findByBoardIdOrderByCreatedAtAsc(boardId: Long): List<BoardComment>
    fun findByAuthorOfficerId(authorOfficerId: Long): List<BoardComment>
}
