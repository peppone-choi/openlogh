package com.openlogh.repository

import com.openlogh.entity.Betting
import org.springframework.data.jpa.repository.JpaRepository

interface BettingRepository : JpaRepository<Betting, Long> {
    fun findByWorldId(worldId: Long): List<Betting>
    fun findByWorldIdAndStatus(worldId: Long, status: String): List<Betting>
}
