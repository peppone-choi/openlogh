package com.openlogh.repository

import com.openlogh.entity.BetEntry
import org.springframework.data.jpa.repository.JpaRepository

interface BetEntryRepository : JpaRepository<BetEntry, Long> {
    fun findByBettingId(bettingId: Long): List<BetEntry>
    fun findByOfficerId(officerId: Long): List<BetEntry>
    fun findByBettingIdAndOfficerId(bettingId: Long, officerId: Long): BetEntry?
}
