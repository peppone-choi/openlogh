package com.openlogh.controller

import com.openlogh.service.ChatScope
import com.openlogh.service.ChatService
import org.springframework.http.ResponseEntity
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*

/**
 * WebSocket chat message payload.
 */
data class ChatMessageRequest(
    val officerId: Long,
    val content: String,
    val scope: String = "GLOBAL",
)

/**
 * WebSocket controller for real-time chat.
 */
@Controller
class ChatWebSocketController(
    private val chatService: ChatService,
) {
    @MessageMapping("/chat/{sessionId}/send")
    fun handleChatMessage(
        @DestinationVariable sessionId: Long,
        @Payload request: ChatMessageRequest,
    ) {
        val scope = try {
            ChatScope.valueOf(request.scope.uppercase())
        } catch (e: IllegalArgumentException) {
            ChatScope.GLOBAL
        }

        chatService.sendChat(sessionId, request.officerId, request.content, scope)
    }
}

/**
 * REST controller for chat history and management.
 */
@RestController
@RequestMapping("/api/v1/world/{sessionId}/chat")
class ChatRestController(
    private val chatService: ChatService,
) {
    @GetMapping("/history")
    fun getChatHistory(
        @PathVariable sessionId: Long,
        @RequestParam scope: String,
        @RequestParam scopeId: Long,
        @RequestParam(defaultValue = "50") limit: Int,
    ): ResponseEntity<List<Map<String, Any>>> {
        val chatScope = try {
            ChatScope.valueOf(scope.uppercase())
        } catch (e: IllegalArgumentException) {
            return ResponseEntity.badRequest().build()
        }

        val history = chatService.getChatHistory(sessionId, chatScope, scopeId, limit)
        return ResponseEntity.ok(history)
    }
}
