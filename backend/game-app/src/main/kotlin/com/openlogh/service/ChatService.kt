package com.openlogh.service

import com.openlogh.entity.Message
import com.openlogh.repository.MessageRepository
import com.openlogh.repository.OfficerRepository
import org.slf4j.LoggerFactory
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentHashMap

/**
 * Chat scope determines who can see the message.
 */
enum class ChatScope(val mailboxCode: String) {
    PLANET("chat_planet"),
    FACTION("chat_faction"),
    GLOBAL("chat_global"),
}

/**
 * Real-time chat service with location-scoped messaging.
 * - PLANET: only officers on the same planet can see the message
 * - FACTION: all officers in the same faction can see the message
 * - GLOBAL: all officers in the session can see the message
 */
@Service
class ChatService(
    private val messageRepository: MessageRepository,
    private val officerRepository: OfficerRepository,
    private val messagingTemplate: SimpMessagingTemplate,
) {
    private val logger = LoggerFactory.getLogger(ChatService::class.java)

    /** Rate limiting: officerId -> last message timestamp */
    private val lastMessageTime = ConcurrentHashMap<Long, Long>()

    companion object {
        /** Minimum interval between messages per officer (milliseconds) */
        const val RATE_LIMIT_MS = 2000L
        /** Maximum chat history to return */
        const val MAX_HISTORY = 50
    }

    /**
     * Send a chat message with scope-based distribution.
     */
    @Transactional
    fun sendChat(
        sessionId: Long,
        officerId: Long,
        content: String,
        scope: ChatScope,
    ): Map<String, Any>? {
        // Rate limiting
        val now = System.currentTimeMillis()
        val lastTime = lastMessageTime[officerId]
        if (lastTime != null && now - lastTime < RATE_LIMIT_MS) {
            logger.debug("Rate limited: officer {} ({}ms since last)", officerId, now - lastTime)
            return null
        }
        lastMessageTime[officerId] = now

        val officer = officerRepository.findById(officerId).orElse(null) ?: return null

        // Determine destination ID based on scope
        val destId = when (scope) {
            ChatScope.PLANET -> officer.planetId
            ChatScope.FACTION -> officer.factionId
            ChatScope.GLOBAL -> sessionId
        }

        // Persist the chat message
        val message = messageRepository.save(
            Message(
                sessionId = sessionId,
                mailboxCode = scope.mailboxCode,
                mailboxType = "PUBLIC",
                messageType = "chat",
                srcId = officerId,
                destId = destId,
                payload = mutableMapOf(
                    "content" to content,
                    "senderName" to officer.name,
                    "factionId" to officer.factionId,
                    "scope" to scope.name,
                ),
            )
        )

        // Build broadcast payload
        val chatPayload = mapOf(
            "id" to message.id,
            "senderId" to officerId,
            "senderName" to officer.name,
            "factionId" to officer.factionId,
            "content" to content,
            "scope" to scope.name,
            "timestamp" to message.sentAt.toString(),
        )

        // Broadcast via WebSocket based on scope
        val topic = when (scope) {
            ChatScope.PLANET -> "/topic/chat/$sessionId/planet/${officer.planetId}"
            ChatScope.FACTION -> "/topic/chat/$sessionId/faction/${officer.factionId}"
            ChatScope.GLOBAL -> "/topic/chat/$sessionId/global"
        }

        messagingTemplate.convertAndSend(topic, chatPayload)
        return chatPayload
    }

    /**
     * Get chat history for a scope.
     */
    fun getChatHistory(
        sessionId: Long,
        scope: ChatScope,
        scopeId: Long,
        limit: Int = MAX_HISTORY,
    ): List<Map<String, Any>> {
        val messages = messageRepository.findBySessionIdAndMailboxCodeAndDestIdOrderBySentAtDesc(
            sessionId, scope.mailboxCode, scopeId
        ).take(limit.coerceIn(1, MAX_HISTORY))

        return messages.map { msg ->
            mapOf(
                "id" to msg.id,
                "senderId" to (msg.srcId ?: 0L),
                "senderName" to (msg.payload["senderName"] ?: ""),
                "factionId" to (msg.payload["factionId"] ?: 0L),
                "content" to (msg.payload["content"] ?: ""),
                "scope" to scope.name,
                "timestamp" to msg.sentAt.toString(),
            )
        }
    }
}
