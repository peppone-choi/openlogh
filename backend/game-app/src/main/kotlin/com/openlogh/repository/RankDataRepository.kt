package com.openlogh.repository

import com.openlogh.entity.RankData
import org.springframework.data.jpa.repository.JpaRepository

interface RankDataRepository : JpaRepository<RankData, Long> {
    fun findBySessionId(sessionId: Long): List<RankData>
    fun findBySessionIdAndCategory(sessionId: Long, category: String): List<RankData>
    fun findBySessionIdAndCategoryOrderByScoreDesc(sessionId: Long, category: String): List<RankData>
    fun findByFactionId(factionId: Long): List<RankData>
}
