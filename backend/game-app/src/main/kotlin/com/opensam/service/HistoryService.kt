package com.opensam.service

import com.opensam.entity.Record
import com.opensam.entity.YearbookHistory
import com.opensam.repository.RecordRepository
import com.opensam.repository.YearbookHistoryRepository
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
                worldId = worldId,
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
                worldId = worldId,
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
        return recordRepository.findByWorldIdAndRecordTypeOrderByCreatedAtDesc(worldId, "world_history")
    }

    fun getWorldRecords(worldId: Long): List<Record> {
        return recordRepository.findByWorldIdAndRecordTypeOrderByCreatedAtDesc(worldId, "world_record")
    }

    fun getGeneralRecords(generalId: Long): List<Record> {
        return recordRepository.findByDestIdAndRecordTypeOrderByCreatedAtDesc(generalId, "general_action")
    }

    fun getByYearMonth(worldId: Long, year: Int, month: Int): List<Record> {
        return recordRepository.findByWorldIdAndRecordTypeInAndYearAndMonthOrderByCreatedAtDesc(
            worldId, listOf("world_history", "world_record"), year, month
        )
    }

    fun getYearbook(worldId: Long, year: Int): YearbookHistory? {
        val yearShort = year.toShort()
        for (month in 12 downTo 1) {
            val snapshot = yearbookHistoryRepository.findByWorldIdAndYearAndMonth(worldId, yearShort, month.toShort())
            if (snapshot != null) {
                return snapshot
            }
        }
        return null
    }
}
