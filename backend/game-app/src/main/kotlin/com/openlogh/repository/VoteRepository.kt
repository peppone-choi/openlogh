package com.openlogh.repository

import com.openlogh.entity.Vote
import org.springframework.data.jpa.repository.JpaRepository

interface VoteRepository : JpaRepository<Vote, Long> {
    fun findByWorldId(worldId: Long): List<Vote>
    fun findByNationId(nationId: Long): List<Vote>
    fun findByWorldIdAndStatus(worldId: Long, status: String): List<Vote>
}
