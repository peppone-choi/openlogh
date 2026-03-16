package com.opensam.service

import com.opensam.entity.Record
import com.opensam.repository.RecordRepository
import org.springframework.stereotype.Service

@Service
class CommandLogDispatcher(
    private val recordRepository: RecordRepository,
) {
    fun dispatchLogs(
        worldId: Long,
        generalId: Long,
        nationId: Long?,
        year: Int,
        month: Int,
        logs: List<String>,
    ) {
        if (logs.isEmpty()) return

        val records = mutableListOf<Record>()

        for (log in logs) {
            when {
                log.startsWith("_history:") -> {
                    val text = log.removePrefix("_history:")
                    records += createRecord(
                        worldId,
                        RecordService.GENERAL_RECORD,
                        generalId,
                        generalId,
                        text,
                        year,
                        month
                    )
                }
                log.startsWith("_globalHistory:") -> {
                    val text = log.removePrefix("_globalHistory:")
                    records += createRecord(worldId, RecordService.WORLD_HISTORY, generalId, null, text, year, month)
                }
                log.startsWith("_globalAction:") -> {
                    val text = log.removePrefix("_globalAction:")
                    records += createRecord(worldId, RecordService.WORLD_RECORD, generalId, null, text, year, month)
                }
                log.startsWith("_global:") -> {
                    val text = log.removePrefix("_global:")
                    records += createRecord(worldId, RecordService.WORLD_RECORD, generalId, null, text, year, month)
                }
                log.startsWith("_nationalHistory:") -> {
                    val text = log.removePrefix("_nationalHistory:")
                    if (nationId != null) {
                        records += createRecord(
                            worldId,
                            RecordService.NATION_HISTORY,
                            generalId,
                            nationId,
                            text,
                            year,
                            month
                        )
                    }
                }
                log.startsWith("_destNationalHistory:") -> {
                    val rest = log.removePrefix("_destNationalHistory:")
                    val colonIdx = rest.indexOf(':')
                    if (colonIdx > 0) {
                        val destNationId = rest.substring(0, colonIdx).toLongOrNull()
                        val text = rest.substring(colonIdx + 1)
                        if (destNationId != null && text.isNotBlank()) {
                            records += createRecord(
                                worldId,
                                RecordService.NATION_HISTORY,
                                generalId,
                                destNationId,
                                text,
                                year,
                                month
                            )
                        }
                    }
                }
                log.startsWith("_destGeneralLog:") -> {
                    val rest = log.removePrefix("_destGeneralLog:")
                    val colonIdx = rest.indexOf(':')
                    if (colonIdx > 0) {
                        val destGenId = rest.substring(0, colonIdx).toLongOrNull()
                        val text = rest.substring(colonIdx + 1)
                        if (destGenId != null && destGenId > 0L && text.isNotBlank()) {
                            records += createRecord(
                                worldId,
                                RecordService.GENERAL_ACTION,
                                generalId,
                                destGenId,
                                text,
                                year,
                                month
                            )
                        }
                    }
                }
                log.startsWith("_destGeneralHistory:") -> {
                    val rest = log.removePrefix("_destGeneralHistory:")
                    val colonIdx = rest.indexOf(':')
                    if (colonIdx > 0) {
                        val destGenId = rest.substring(0, colonIdx).toLongOrNull()
                        val text = rest.substring(colonIdx + 1)
                        if (destGenId != null && destGenId > 0L && text.isNotBlank()) {
                            records += createRecord(
                                worldId,
                                RecordService.GENERAL_RECORD,
                                generalId,
                                destGenId,
                                text,
                                year,
                                month
                            )
                        }
                    }
                }
                log.startsWith("_broadcast:") -> {
                    val rest = log.removePrefix("_broadcast:")
                    val parts = rest.split(":", limit = 3)
                    if (parts.size == 3) {
                        val text = parts[2]
                        if (text.isNotBlank()) {
                            records += createRecord(
                                worldId,
                                RecordService.WORLD_RECORD,
                                generalId,
                                null,
                                text,
                                year,
                                month
                            )
                        }
                    }
                }
                log.startsWith("_") -> {}
                else -> {
                    if (log.isNotBlank()) {
                        records += createRecord(
                            worldId,
                            RecordService.GENERAL_ACTION,
                            generalId,
                            generalId,
                            log,
                            year,
                            month
                        )
                    }
                }
            }
        }

        if (records.isNotEmpty()) {
            recordRepository.saveAll(records)
        }
    }

    private fun createRecord(
        worldId: Long,
        recordType: String,
        srcId: Long,
        destId: Long?,
        text: String,
        year: Int,
        month: Int,
    ): Record {
        return Record(
            worldId = worldId,
            recordType = recordType,
            srcId = srcId,
            destId = destId,
            year = year,
            month = month,
            payload = mutableMapOf(
                "message" to text,
            ),
        )
    }
}
