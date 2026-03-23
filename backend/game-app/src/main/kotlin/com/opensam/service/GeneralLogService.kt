package com.opensam.service

import com.opensam.repository.GeneralRepository
import com.opensam.repository.MessageRepository
import com.opensam.repository.RecordRepository
import org.springframework.stereotype.Service

/**
 * Retrieves old/paginated general logs (action, battle result, battle detail).
 * Legacy: j_general_log_old.php
 */
@Service
class GeneralLogService(
    private val generalRepository: GeneralRepository,
    private val messageRepository: MessageRepository,
    private val recordRepository: RecordRepository,
) {
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

    /**
     * @param generalId the general requesting logs
     * @param targetId the general whose logs to fetch
     * @param type one of "generalAction", "battleResult", "battleDetail"
     * @param toId fetch logs with id < toId (pagination)
     */
    fun getOldLogs(generalId: Long, targetId: Long, type: String, toId: Long): LogResult {
        if (type !in listOf("generalAction", "battleResult", "battleDetail", "generalHistory")) {
            return LogResult(false, "요청 타입이 올바르지 않습니다.")
        }
        if (targetId <= 0 || toId <= 0) {
            return LogResult(false, "요청 대상이 올바르지 않습니다.")
        }

        // Check permission: same nation or own logs
        val requester = generalRepository.findById(generalId).orElse(null)
            ?: return LogResult(false, "장수를 찾을 수 없습니다.")
        val target = generalRepository.findById(targetId).orElse(null)
            ?: return LogResult(false, "대상 장수를 찾을 수 없습니다.")

        if (requester.nationId != target.nationId && requester.id != target.id) {
            return LogResult(false, "같은 국가의 장수만 조회할 수 있습니다.")
        }

        if (type == "generalHistory") {
            val records = recordRepository.findByDestIdAndRecordTypeAndIdLessThanOrderByCreatedAtDesc(
                targetId, "general_record", toId
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

        val mailboxCode = when (type) {
            "generalAction" -> "general_action"
            "battleResult" -> "battle_result"
            "battleDetail" -> "battle_detail"
            else -> return LogResult(false, "잘못된 타입")
        }

        val messages = messageRepository.findByDestIdAndMailboxCodeAndIdLessThanOrderByIdDesc(
            targetId, mailboxCode, toId
        ).take(30)

        val logs = messages.map { m ->
            LogEntry(
                id = m.id,
                message = (m.payload["message"] as? String) ?: "",
                date = m.sentAt.toString(),
            )
        }

        return LogResult(true, logs = logs)
    }
}
