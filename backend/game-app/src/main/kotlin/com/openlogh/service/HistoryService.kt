package com.openlogh.service

import com.openlogh.entity.Record
import com.openlogh.entity.YearbookHistory
import com.openlogh.repository.RecordRepository
import com.openlogh.repository.YearbookHistoryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class HistoryService(
    private val recordRepository: RecordRepository,
    private val yearbookHistoryRepository: YearbookHistoryRepository,
) {
    @Transactional
    fun logWorldHistory(sessionId: Long, message: String, year: Int, month: Int) {
        recordRepository.save(
            Record(
                sessionId = sessionId,
                recordType = "world_history",
                year = year,
                month = month,
                payload = mutableMapOf(
                    "message" to message,
                ),
            )
        )
    }

    @Transactional
    fun logNationHistory(sessionId: Long, factionId: Long, message: String, year: Int, month: Int) {
        recordRepository.save(
            Record(
                sessionId = sessionId,
                recordType = "nation_history",
                destId = factionId,
                year = year,
                month = month,
                payload = mutableMapOf(
                    "message" to message,
                ),
            )
        )
    }

    fun getWorldHistory(sessionId: Long): List<Record> {
        return recordRepository.findBySessionIdAndRecordTypeOrderByCreatedAtDesc(sessionId, "world_history")
    }

    fun getWorldRecords(sessionId: Long): List<Record> {
        return recordRepository.findBySessionIdAndRecordTypeOrderByCreatedAtDesc(sessionId, "world_record")
    }

    fun getOfficerRecords(officerId: Long): List<Record> {
        return recordRepository.findByDestIdAndRecordTypeOrderByCreatedAtDesc(officerId, "general_action")
    }

    /** Alias for [getOfficerRecords] (old field name compat). */
    fun getGeneralRecords(officerId: Long): List<Record> = getOfficerRecords(officerId)

    fun getByYearMonth(sessionId: Long, year: Int, month: Int): List<Record> {
        return recordRepository.findBySessionIdAndYearAndMonth(sessionId, year, month)
    }

    fun getYearbook(sessionId: Long, year: Int): YearbookHistory? {
        val yearShort = year.toShort()
        for (month in 12 downTo 1) {
            val snapshot = yearbookHistoryRepository.findBySessionIdAndYearAndMonth(sessionId, yearShort, month.toShort())
            if (snapshot != null) {
                return snapshot
            }
        }
        return null
    }
}
