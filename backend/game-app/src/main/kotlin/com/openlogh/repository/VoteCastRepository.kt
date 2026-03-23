package com.openlogh.repository

import com.openlogh.entity.VoteCast
import org.springframework.data.jpa.repository.JpaRepository

interface VoteCastRepository : JpaRepository<VoteCast, Long> {
    fun findByVoteId(voteId: Long): List<VoteCast>
    fun findByOfficerId(officerId: Long): List<VoteCast>
    fun findByVoteIdAndOfficerId(voteId: Long, officerId: Long): VoteCast?
}
