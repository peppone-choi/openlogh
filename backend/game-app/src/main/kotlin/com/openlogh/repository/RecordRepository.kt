package com.openlogh.repository

import com.openlogh.entity.Record
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface RecordRepository : JpaRepository<Record, Long> {
    // === Old field name compat aliases ===
    @Query("SELECT r FROM Record r WHERE r.sessionId = :worldId AND r.recordType = :recordType ORDER BY r.createdAt DESC")
    fun findByWorldIdAndRecordTypeOrderByCreatedAtDesc(
        @Param("worldId") worldId: Long,
        @Param("recordType") recordType: String,
    ): List<Record>

    @Query("SELECT r FROM Record r WHERE r.sessionId = :worldId AND r.year = :year AND r.month = :month")
    fun findByWorldIdAndYearAndMonth(
        @Param("worldId") worldId: Long,
        @Param("year") year: Int,
        @Param("month") month: Int,
    ): List<Record>
    fun findBySessionIdAndRecordTypeOrderByCreatedAtDesc(sessionId: Long, recordType: String): List<Record>

    fun findByDestIdAndRecordTypeOrderByCreatedAtDesc(destId: Long, recordType: String): List<Record>

    fun findBySessionIdAndRecordTypeAndIdLessThanOrderByCreatedAtDesc(
        sessionId: Long,
        recordType: String,
        beforeId: Long
    ): List<Record>

    fun findByDestIdAndRecordTypeAndIdLessThanOrderByCreatedAtDesc(
        destId: Long,
        recordType: String,
        beforeId: Long
    ): List<Record>

    fun findBySessionIdAndRecordTypeAndIdGreaterThanOrderByCreatedAtDesc(
        sessionId: Long,
        recordType: String,
        sinceId: Long
    ): List<Record>

    fun findByDestIdAndRecordTypeAndIdGreaterThanOrderByCreatedAtDesc(
        destId: Long,
        recordType: String,
        sinceId: Long
    ): List<Record>

    fun findBySessionIdAndYearAndMonth(
        sessionId: Long,
        year: Int,
        month: Int
    ): List<Record>

    @Query(
        """
        SELECT r FROM Record r 
        WHERE r.sessionId = :sessionId 
        AND r.recordType IN :recordTypes 
        AND (:beforeId IS NULL OR r.id < :beforeId)
        ORDER BY r.createdAt DESC, r.id DESC
        """
    )
    fun findBySessionIdAndRecordTypesWithPagination(
        sessionId: Long,
        recordTypes: List<String>,
        beforeId: Long?
    ): List<Record>

    @Query(
        """
        SELECT r FROM Record r 
        WHERE r.destId = :destId 
        AND r.recordType IN :recordTypes 
        AND (:beforeId IS NULL OR r.id < :beforeId)
        ORDER BY r.createdAt DESC, r.id DESC
        """
    )
    fun findByDestIdAndRecordTypesWithPagination(
        destId: Long,
        recordTypes: List<String>,
        beforeId: Long?
    ): List<Record>
}
