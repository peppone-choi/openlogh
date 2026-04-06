package com.openlogh.service

import com.openlogh.repository.FactionRepository
import com.openlogh.repository.OfficerRepository
import org.slf4j.LoggerFactory
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service

/**
 * Chat command handler for special slash-commands in chat.
 *
 * Supported commands:
 * - 명함교환 (Name card exchange): transfers personal + position card mail addresses to chat partner
 * - 캐릭터정보취득 (Character info): view chat partner's character info
 */
@Service
class ChatCommandService(
    private val officerRepository: OfficerRepository,
    private val factionRepository: FactionRepository,
    private val addressBookService: AddressBookService,
    private val messagingTemplate: SimpMessagingTemplate,
) {
    private val logger = LoggerFactory.getLogger(ChatCommandService::class.java)

    companion object {
        val CHAT_COMMANDS = setOf("명함교환", "캐릭터정보취득")
        private val COMMAND_PATTERN = Regex("^/(명함교환|캐릭터정보취득)\\s*(\\d+)?$")
    }

    /**
     * Check if a message is a chat command.
     */
    fun isChatCommand(content: String): Boolean {
        return content.startsWith("/") && COMMAND_PATTERN.matches(content.trim())
    }

    /**
     * Execute a chat command. Returns true if handled.
     */
    fun executeCommand(
        sessionId: Long,
        senderId: Long,
        content: String,
        targetOfficerId: Long?,
    ): Boolean {
        val match = COMMAND_PATTERN.matchEntire(content.trim()) ?: return false
        val commandName = match.groupValues[1]
        val argId = match.groupValues[2].toLongOrNull() ?: targetOfficerId

        if (argId == null) {
            sendSystemMessage(sessionId, senderId, "대상 장교 ID를 지정해주세요.")
            return true
        }

        return when (commandName) {
            "명함교환" -> handleNameCardExchange(sessionId, senderId, argId)
            "캐릭터정보취득" -> handleCharacterInfo(sessionId, senderId, argId)
            else -> false
        }
    }

    /**
     * 명함교환 (Name card exchange):
     * Exchange personal + position card mail addresses with the target officer.
     */
    private fun handleNameCardExchange(sessionId: Long, senderId: Long, targetId: Long): Boolean {
        if (senderId == targetId) {
            sendSystemMessage(sessionId, senderId, "자신과는 명함교환을 할 수 없습니다.")
            return true
        }

        val sender = officerRepository.findById(senderId).orElse(null)
        val target = officerRepository.findById(targetId).orElse(null)

        if (sender == null) {
            sendSystemMessage(sessionId, senderId, "장교 정보를 찾을 수 없습니다.")
            return true
        }
        if (target == null) {
            sendSystemMessage(sessionId, senderId, "대상 장교를 찾을 수 없습니다: $targetId")
            return true
        }

        try {
            val added = addressBookService.exchangeNameCards(sessionId, senderId, targetId)
            sendSystemMessage(
                sessionId, senderId,
                "${target.name}와(과) 명함을 교환했습니다. 새로운 주소 ${added}개가 추가되었습니다."
            )
            sendSystemMessage(
                sessionId, targetId,
                "${sender.name}와(과) 명함을 교환했습니다."
            )
        } catch (e: Exception) {
            sendSystemMessage(sessionId, senderId, "명함교환 실패: ${e.message}")
        }

        return true
    }

    /**
     * 캐릭터정보취득 (Character info):
     * View chat partner's public character info.
     */
    private fun handleCharacterInfo(sessionId: Long, senderId: Long, targetId: Long): Boolean {
        val target = officerRepository.findById(targetId).orElse(null)
        if (target == null) {
            sendSystemMessage(sessionId, senderId, "대상 장교를 찾을 수 없습니다: $targetId")
            return true
        }

        val factionName = if (target.factionId != 0L) {
            factionRepository.findById(target.factionId).orElse(null)?.name ?: "재야"
        } else {
            "재야"
        }

        val positionCardNames = target.positionCards.mapNotNull { cardName ->
            runCatching { com.openlogh.model.PositionCard.valueOf(cardName) }.getOrNull()
        }.filter { it != com.openlogh.model.PositionCard.PERSONAL && it != com.openlogh.model.PositionCard.CAPTAIN }
            .joinToString(", ") { it.nameKo }

        val info = buildString {
            appendLine("=== ${target.name} 캐릭터 정보 ===")
            appendLine("소속: $factionName")
            appendLine("계급: ${target.officerLevel}")
            appendLine("통솔: ${target.leadership} / 지휘: ${target.command}")
            appendLine("정보: ${target.intelligence} / 정치: ${target.politics}")
            appendLine("운영: ${target.administration} / 기동: ${target.mobility}")
            appendLine("공격: ${target.attack} / 방어: ${target.defense}")
            if (positionCardNames.isNotBlank()) {
                appendLine("직무: $positionCardNames")
            }
        }

        sendSystemMessage(sessionId, senderId, info)
        return true
    }

    /**
     * Send a system message to an officer via WebSocket.
     */
    private fun sendSystemMessage(sessionId: Long, officerId: Long, message: String) {
        messagingTemplate.convertAndSend(
            "/topic/chat/$sessionId/system/$officerId",
            mapOf(
                "type" to "SYSTEM",
                "content" to message,
                "timestamp" to java.time.OffsetDateTime.now().toString(),
            )
        )
    }
}
