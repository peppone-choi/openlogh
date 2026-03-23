package com.openlogh.service

import com.openlogh.entity.Record
import com.openlogh.repository.RecordRepository
import org.springframework.stereotype.Service

@Service
class CommandLogDispatcher(
    private val recordRepository: RecordRepository,
) {
    fun dispatch(sessionId: Long, officerId: Long, commandName: String, logs: List<String>) {
        dispatchLogs(sessionId, officerId, 0L, 0, 0, logs)
    }

    fun dispatchLogs(
        worldId: Long,
        generalId: Long,
        nationId: Long,
        year: Int,
        month: Int,
        logs: List<String>,
    ) {
        val records = logs.map { log ->
            when {
                log.startsWith("_globalHistory:") -> Record(
                    sessionId = worldId,
                    recordType = RecordService.WORLD_HISTORY,
                    srcId = generalId,
                    destId = null,
                    year = year,
                    month = month,
                    payload = mutableMapOf("message" to log.removePrefix("_globalHistory:")),
                )
                log.startsWith("_history:") -> Record(
                    sessionId = worldId,
                    recordType = RecordService.GENERAL_RECORD,
                    srcId = generalId,
                    destId = generalId,
                    year = year,
                    month = month,
                    payload = mutableMapOf("message" to log.removePrefix("_history:")),
                )
                else -> Record(
                    sessionId = worldId,
                    recordType = RecordService.GENERAL_ACTION,
                    srcId = generalId,
                    destId = generalId,
                    year = year,
                    month = month,
                    payload = mutableMapOf("message" to log),
                )
            }
        }
        recordRepository.saveAll(records)
    }
}
