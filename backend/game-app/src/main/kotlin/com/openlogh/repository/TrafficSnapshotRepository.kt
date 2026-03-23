package com.openlogh.repository

import com.openlogh.entity.TrafficSnapshot
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface TrafficSnapshotRepository : JpaRepository<TrafficSnapshot, Long> {
    @Query("SELECT t FROM TrafficSnapshot t WHERE t.sessionId = :sessionId ORDER BY t.recordedAt DESC LIMIT 30")
    fun findTop30ByWorldIdOrderByRecordedAtDesc(@org.springframework.data.repository.query.Param("sessionId") sessionId: Long): List<TrafficSnapshot>

    @Query("SELECT COALESCE(MAX(t.refresh), 0) FROM TrafficSnapshot t WHERE t.sessionId = :sessionId")
    fun findMaxRefresh(sessionId: Long): Int

    @Query("SELECT COALESCE(MAX(t.online), 0) FROM TrafficSnapshot t WHERE t.sessionId = :sessionId")
    fun findMaxOnline(sessionId: Long): Int
}
