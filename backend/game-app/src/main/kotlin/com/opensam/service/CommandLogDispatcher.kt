package com.opensam.service

import com.opensam.entity.Message
import com.opensam.repository.MessageRepository
import org.springframework.stereotype.Service

/**
 * 커맨드 실행 후 태그된 로그를 파싱하여 적절한 Message 레코드로 저장한다.
 *
 * BaseCommand의 pushLog 메서드들이 생성하는 태그:
 * - (no prefix)        → general_action: 개인 동향 (destId = generalId)
 * - _history:          → general_record: 장수 기록 (destId = generalId)
 * - _global:           → world_record:   장수 동향 전체 (no destId)
 * - _globalAction:     → world_record:   장수 동향 전체 (no destId)
 * - _globalHistory:    → world_history:  중원 정세 (no destId)
 * - _nationalHistory:  → nation_history: 아국 기록 (destId = srcNationId)
 * - _destNationalHistory:{nationId}: → nation_history (destId = parsed nationId)
 * - _destGeneralLog:{generalId}:    → general_action (destId = parsed generalId)
 * - _destGeneralHistory:{generalId}: → general_record (destId = parsed generalId)
 * - _broadcast:{nationId}:{excludeId}: → general_action broadcast to nation generals
 */
@Service
class CommandLogDispatcher(
    private val messageRepository: MessageRepository,
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

        val messages = mutableListOf<Message>()

        for (log in logs) {
            when {
                log.startsWith("_history:") -> {
                    val text = log.removePrefix("_history:")
                    messages += createMessage(worldId, "general_record", generalId, generalId, text, year, month)
                }
                log.startsWith("_globalHistory:") -> {
                    val text = log.removePrefix("_globalHistory:")
                    messages += createMessage(worldId, "world_history", generalId, null, text, year, month)
                }
                log.startsWith("_globalAction:") -> {
                    val text = log.removePrefix("_globalAction:")
                    messages += createMessage(worldId, "world_record", generalId, null, text, year, month)
                }
                log.startsWith("_global:") -> {
                    val text = log.removePrefix("_global:")
                    messages += createMessage(worldId, "world_record", generalId, null, text, year, month)
                }
                log.startsWith("_nationalHistory:") -> {
                    val text = log.removePrefix("_nationalHistory:")
                    if (nationId != null) {
                        messages += createMessage(worldId, "nation_history", generalId, nationId, text, year, month)
                    }
                }
                log.startsWith("_destNationalHistory:") -> {
                    val rest = log.removePrefix("_destNationalHistory:")
                    val colonIdx = rest.indexOf(':')
                    if (colonIdx > 0) {
                        val destNationId = rest.substring(0, colonIdx).toLongOrNull()
                        val text = rest.substring(colonIdx + 1)
                        if (destNationId != null && text.isNotBlank()) {
                            messages += createMessage(worldId, "nation_history", generalId, destNationId, text, year, month)
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
                            messages += createMessage(worldId, "general_action", generalId, destGenId, text, year, month)
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
                            messages += createMessage(worldId, "general_record", generalId, destGenId, text, year, month)
                        }
                    }
                }
                log.startsWith("_broadcast:") -> {
                    val rest = log.removePrefix("_broadcast:")
                    val parts = rest.split(":", limit = 3)
                    if (parts.size == 3) {
                        val text = parts[2]
                        if (text.isNotBlank()) {
                            messages += createMessage(worldId, "world_record", generalId, null, text, year, month)
                        }
                    }
                }
                log.startsWith("_") -> { }
                else -> {
                    if (log.isNotBlank()) {
                        messages += createMessage(worldId, "general_action", generalId, generalId, log, year, month)
                    }
                }
            }
        }

        if (messages.isNotEmpty()) {
            messageRepository.saveAll(messages)
        }
    }

    private fun createMessage(
        worldId: Long,
        mailboxCode: String,
        srcId: Long,
        destId: Long?,
        text: String,
        year: Int,
        month: Int,
    ): Message {
        val mailboxType = when (mailboxCode) {
            "general_action", "general_record" -> "PRIVATE"
            "nation_history" -> "NATIONAL"
            "world_record", "world_history" -> "PUBLIC"
            else -> "PUBLIC"
        }
        
        return Message(
            worldId = worldId,
            mailboxCode = mailboxCode,
            mailboxType = mailboxType,
            messageType = "log",
            srcId = srcId,
            destId = destId,
            payload = mutableMapOf(
                "message" to text,
                "year" to year,
                "month" to month,
            ),
        )
    }
}
