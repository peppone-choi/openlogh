package com.openlogh.repository

import com.openlogh.entity.OfficerRecord
import org.springframework.data.jpa.repository.JpaRepository

interface OfficerRecordRepository : JpaRepository<OfficerRecord, Long> {
    fun findByOfficerId(officerId: Long): List<OfficerRecord>
    fun findBySessionId(sessionId: Long): List<OfficerRecord>
    fun findByOfficerIdAndRecordType(officerId: Long, recordType: String): List<OfficerRecord>
    fun findByOfficerIdOrderByCreatedAtDesc(officerId: Long): List<OfficerRecord>
}
