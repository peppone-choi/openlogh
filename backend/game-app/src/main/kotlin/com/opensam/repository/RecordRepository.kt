package com.opensam.repository

import com.opensam.entity.Record
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface RecordRepository : JpaRepository<Record, Long> {
    fun findByWorldIdAndRecordTypeOrderByCreatedAtDesc(worldId: Long, recordType: String): List<Record>

    fun findByDestIdAndRecordTypeOrderByCreatedAtDesc(destId: Long, recordType: String): List<Record>

    fun findByWorldIdAndRecordTypeAndIdLessThanOrderByCreatedAtDesc(
        worldId: Long,
        recordType: String,
        beforeId: Long
    ): List<Record>

    fun findByDestIdAndRecordTypeAndIdLessThanOrderByCreatedAtDesc(
        destId: Long,
        recordType: String,
        beforeId: Long
    ): List<Record>

    fun findByWorldIdAndRecordTypeAndIdGreaterThanOrderByCreatedAtDesc(
        worldId: Long,
        recordType: String,
        sinceId: Long
    ): List<Record>

    fun findByDestIdAndRecordTypeAndIdGreaterThanOrderByCreatedAtDesc(
        destId: Long,
        recordType: String,
        sinceId: Long
    ): List<Record>

    fun findByWorldIdAndYearAndMonth(
        worldId: Long,
        year: Int,
        month: Int
    ): List<Record>

    fun findByWorldIdAndRecordTypeInAndYearAndMonthOrderByCreatedAtDesc(
        worldId: Long,
        recordType: List<String>,
        year: Int,
        month: Int
    ): List<Record>

    @Query(
        """
        SELECT r FROM Record r 
        WHERE r.worldId = :worldId 
        AND r.recordType IN :recordTypes 
        AND (:beforeId IS NULL OR r.id < :beforeId)
        ORDER BY r.createdAt DESC, r.id DESC
        """
    )
    fun findByWorldIdAndRecordTypesWithPagination(
        worldId: Long,
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
