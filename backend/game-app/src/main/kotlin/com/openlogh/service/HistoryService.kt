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
    fun logWorldHistory(worldId: Long, message: String, year: Int, month: Int, scenarioInit: Boolean = false) {
        val payload = mutableMapOf<String, Any>("message" to message)
        if (scenarioInit) payload["scenarioInit"] = true
        recordRepository.save(
            Record(
                sessionId = worldId,
                recordType = "world_history",
                year = year,
                month = month,
                payload = payload,
            )
        )
    }

    @Transactional
    fun logNationHistory(worldId: Long, nationId: Long, message: String, year: Int, month: Int) {
        recordRepository.save(
            Record(
                sessionId = worldId,
                recordType = "nation_history",
                destId = nationId,
                year = year,
                month = month,
                payload = mutableMapOf(
                    "message" to message,
                ),
            )
        )
    }

    fun getWorldHistory(worldId: Long): List<Record> {
        return recordRepository.findBySessionIdAndRecordTypeOrderByCreatedAtDesc(worldId, "world_history")
    }

    fun getWorldRecords(worldId: Long): List<Record> {
        return recordRepository.findBySessionIdAndRecordTypeOrderByCreatedAtDesc(worldId, "world_record")
    }

    fun getGeneralRecords(generalId: Long): List<Record> {
        return recordRepository.findByDestIdAndRecordTypeOrderByCreatedAtDesc(generalId, "general_action")
    }

    fun getByYearMonth(worldId: Long, year: Int, month: Int): List<Record> {
        return recordRepository.findBySessionIdAndRecordTypeInAndYearAndMonthOrderByCreatedAtDesc(
            worldId, listOf("world_history", "world_record"), year, month
        )
    }

    fun getYearbook(worldId: Long, year: Int): YearbookHistory? {
        val yearShort = year.toShort()
        for (month in 12 downTo 1) {
            val snapshot = yearbookHistoryRepository.findBySessionIdAndYearAndMonth(worldId, yearShort, month.toShort())
            if (snapshot != null) {
                return snapshot
            }
        }
        return null
    }
}
