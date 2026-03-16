package com.opensam.service

import com.opensam.entity.Record
import com.opensam.repository.GeneralRepository
import com.opensam.repository.RecordRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RecordService(
    private val recordRepository: RecordRepository,
    private val generalRepository: GeneralRepository,
) {
    companion object {
        const val GENERAL_ACTION = "general_action"
        const val GENERAL_RECORD = "general_record"
        const val WORLD_RECORD = "world_record"
        const val WORLD_HISTORY = "world_history"
        const val NATION_HISTORY = "nation_history"
        const val BATTLE_RESULT = "battle_result"
        const val BATTLE_DETAIL = "battle_detail"

        private const val DEFAULT_PAGE_SIZE = 30
        private const val MAX_PAGE_SIZE = 100
    }

    @Transactional
    fun saveRecord(
        worldId: Long,
        recordType: String,
        srcId: Long?,
        destId: Long?,
        year: Int,
        month: Int,
        payload: Map<String, Any>,
    ): Record {
        return recordRepository.save(
            Record(
                worldId = worldId,
                recordType = recordType,
                srcId = srcId,
                destId = destId,
                year = year,
                month = month,
                payload = payload.toMutableMap(),
            )
        )
    }

    fun getGeneralActions(generalId: Long, beforeId: Long? = null, limit: Int? = null): List<Record> {
        val records = if (beforeId != null) {
            recordRepository.findByDestIdAndRecordTypeAndIdLessThanOrderByCreatedAtDesc(
                generalId,
                GENERAL_ACTION,
                beforeId
            )
        } else {
            recordRepository.findByDestIdAndRecordTypeOrderByCreatedAtDesc(generalId, GENERAL_ACTION)
        }
        return applyLimit(records, limit)
    }

    fun getGeneralRecords(generalId: Long, beforeId: Long? = null, limit: Int? = null): List<Record> {
        val records = if (beforeId != null) {
            recordRepository.findByDestIdAndRecordTypeAndIdLessThanOrderByCreatedAtDesc(
                generalId,
                GENERAL_RECORD,
                beforeId
            )
        } else {
            recordRepository.findByDestIdAndRecordTypeOrderByCreatedAtDesc(generalId, GENERAL_RECORD)
        }
        return applyLimit(records, limit)
    }

    fun getWorldRecords(worldId: Long, beforeId: Long? = null, limit: Int? = null): List<Record> {
        val records = if (beforeId != null) {
            recordRepository.findByWorldIdAndRecordTypeAndIdLessThanOrderByCreatedAtDesc(
                worldId,
                WORLD_RECORD,
                beforeId
            )
        } else {
            recordRepository.findByWorldIdAndRecordTypeOrderByCreatedAtDesc(worldId, WORLD_RECORD)
        }
        return applyLimit(records, limit)
    }

    fun getWorldHistory(worldId: Long, beforeId: Long? = null, limit: Int? = null): List<Record> {
        val records = if (beforeId != null) {
            recordRepository.findByWorldIdAndRecordTypeAndIdLessThanOrderByCreatedAtDesc(
                worldId,
                WORLD_HISTORY,
                beforeId
            )
        } else {
            recordRepository.findByWorldIdAndRecordTypeOrderByCreatedAtDesc(worldId, WORLD_HISTORY)
        }
        return applyLimit(records, limit)
    }

    fun getNationHistory(nationId: Long, beforeId: Long? = null, limit: Int? = null): List<Record> {
        val records = if (beforeId != null) {
            recordRepository.findByDestIdAndRecordTypeAndIdLessThanOrderByCreatedAtDesc(
                nationId,
                NATION_HISTORY,
                beforeId
            )
        } else {
            recordRepository.findByDestIdAndRecordTypeOrderByCreatedAtDesc(nationId, NATION_HISTORY)
        }
        return applyLimit(records, limit)
    }

    fun getBattleResults(generalId: Long, beforeId: Long? = null, limit: Int? = null): List<Record> {
        val records = if (beforeId != null) {
            recordRepository.findByDestIdAndRecordTypeAndIdLessThanOrderByCreatedAtDesc(
                generalId,
                BATTLE_RESULT,
                beforeId
            )
        } else {
            recordRepository.findByDestIdAndRecordTypeOrderByCreatedAtDesc(generalId, BATTLE_RESULT)
        }
        return applyLimit(records, limit)
    }

    fun getBattleDetails(generalId: Long, beforeId: Long? = null, limit: Int? = null): List<Record> {
        val records = if (beforeId != null) {
            recordRepository.findByDestIdAndRecordTypeAndIdLessThanOrderByCreatedAtDesc(
                generalId,
                BATTLE_DETAIL,
                beforeId
            )
        } else {
            recordRepository.findByDestIdAndRecordTypeOrderByCreatedAtDesc(generalId, BATTLE_DETAIL)
        }
        return applyLimit(records, limit)
    }

    data class LogEntry(
        val id: Long,
        val message: String,
        val date: String,
    )

    data class LogResult(
        val result: Boolean,
        val reason: String? = null,
        val logs: List<LogEntry> = emptyList(),
    )

    fun getOldLogs(generalId: Long, targetId: Long, type: String, toId: Long): LogResult {
        if (type !in listOf("generalAction", "battleResult", "battleDetail")) {
            return LogResult(false, "요청 타입이 올바르지 않습니다.")
        }
        if (targetId <= 0 || toId <= 0) {
            return LogResult(false, "요청 대상이 올바르지 않습니다.")
        }

        val recordType = when (type) {
            "generalAction" -> GENERAL_ACTION
            "battleResult" -> BATTLE_RESULT
            "battleDetail" -> BATTLE_DETAIL
            else -> return LogResult(false, "잘못된 타입")
        }

        val requester = generalRepository.findById(generalId).orElse(null)
            ?: return LogResult(false, "장수를 찾을 수 없습니다.")
        val target = generalRepository.findById(targetId).orElse(null)
            ?: return LogResult(false, "대상 장수를 찾을 수 없습니다.")

        if (requester.nationId != target.nationId && requester.id != target.id) {
            return LogResult(false, "같은 국가의 장수만 조회할 수 있습니다.")
        }

        val records = recordRepository.findByDestIdAndRecordTypeAndIdLessThanOrderByCreatedAtDesc(
            targetId,
            recordType,
            toId
        ).take(30)

        val logs = records.map { r ->
            LogEntry(
                id = r.id,
                message = (r.payload["message"] as? String) ?: "",
                date = r.createdAt.toString(),
            )
        }

        return LogResult(true, logs = logs)
    }

    private fun applyLimit(records: List<Record>, limit: Int?): List<Record> {
        val normalizedLimit = (limit ?: DEFAULT_PAGE_SIZE).coerceIn(1, MAX_PAGE_SIZE)
        return records.take(normalizedLimit)
    }
}
