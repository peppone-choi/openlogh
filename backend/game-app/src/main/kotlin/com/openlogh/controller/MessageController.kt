package com.openlogh.controller

import com.openlogh.dto.ContactInfo
import com.openlogh.dto.DiplomacyRespondRequest
import com.openlogh.dto.MessageResponse
import com.openlogh.dto.SendMessageRequest
import com.openlogh.service.MessageService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/messages")
class MessageController(
    private val messageService: MessageService,
) {
    @GetMapping
    fun getMessages(
        @RequestParam(required = false) type: String?,
        @RequestParam(required = false) sessionId: Long?,
        @RequestParam(required = false) factionId: Long?,
        @RequestParam(required = false) officerId: Long?,
        @RequestParam(required = false, defaultValue = "0") rank: Short,
        @RequestParam(required = false) beforeId: Long?,
        @RequestParam(required = false) sinceId: Long?,
        @RequestParam(required = false) limit: Int?,
    ): ResponseEntity<List<MessageResponse>> {
        val messages = when (type?.lowercase()) {
            "public" -> messageService.getPublicMessages(requireParam(sessionId, "sessionId"), beforeId, limit)
            "national" -> messageService.getNationalMessages(requireParam(factionId, "factionId"), beforeId, limit)
            "private" -> messageService.getPrivateMessages(requireParam(officerId, "officerId"), beforeId, limit)
            "diplomacy" -> messageService.getDiplomacyMessages(requireParam(factionId, "factionId"), rank, beforeId, limit)
            null -> {
                val targetOfficerId = requireParam(officerId, "officerId")
                messageService.getMessages(targetOfficerId, sinceId, limit)
            }
            else -> throw IllegalArgumentException("Unsupported mailbox type: $type")
        }

        return ResponseEntity.ok(messages.map { MessageResponse.from(it) })
    }

    @GetMapping("/board")
    fun getBoardMessages(
        @RequestParam sessionId: Long,
        @RequestParam factionId: Long,
    ): ResponseEntity<List<MessageResponse>> {
        return ResponseEntity.ok(messageService.getBoardMessages(sessionId, factionId).map { MessageResponse.from(it) })
    }

    @GetMapping("/secret-board")
    fun getSecretBoardMessages(
        @RequestParam sessionId: Long,
        @RequestParam factionId: Long,
    ): ResponseEntity<List<MessageResponse>> {
        return ResponseEntity.ok(messageService.getSecretBoardMessages(sessionId, factionId).map { MessageResponse.from(it) })
    }

    @PostMapping
    fun sendMessage(@RequestBody request: SendMessageRequest): ResponseEntity<MessageResponse> {
        val message = messageService.sendMessage(
            sessionId = request.sessionId,
            mailboxCode = request.mailboxCode,
            mailboxType = request.mailboxType,
            messageType = request.messageType,
            srcId = request.srcId,
            destId = request.destId,
            payload = request.payload,
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(MessageResponse.from(message))
    }

    @DeleteMapping("/{id}")
    fun deleteMessage(@PathVariable id: Long): ResponseEntity<Void> {
        messageService.deleteMessage(id)
        return ResponseEntity.noContent().build()
    }

    @PatchMapping("/{id}/read")
    fun markAsRead(@PathVariable id: Long): ResponseEntity<Void> {
        messageService.markAsRead(id)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/recent")
    fun getRecentMessages(@RequestParam sequence: Long): ResponseEntity<List<MessageResponse>> {
        return ResponseEntity.ok(messageService.getRecentMessages(sequence).map { MessageResponse.from(it) })
    }

    @PostMapping("/{id}/diplomacy-respond")
    fun respondDiplomacy(
        @PathVariable id: Long,
        @RequestBody request: DiplomacyRespondRequest,
    ): ResponseEntity<Void> {
        messageService.respondDiplomacy(id, request.accept)
        return ResponseEntity.ok().build()
    }

    @PostMapping("/{id}/accept-recruitment")
    fun acceptRecruitment(
        @PathVariable id: Long,
        @RequestParam officerId: Long,
    ): ResponseEntity<Map<String, String>> {
        val nationName = messageService.acceptRecruitment(id, officerId)
        return ResponseEntity.ok(mapOf("nationName" to nationName))
    }

    @PostMapping("/{id}/decline-recruitment")
    fun declineRecruitment(
        @PathVariable id: Long,
        @RequestParam officerId: Long,
    ): ResponseEntity<Void> {
        messageService.declineRecruitment(id, officerId)
        return ResponseEntity.ok().build()
    }

    private fun <T> requireParam(value: T?, name: String): T {
        return value ?: throw IllegalArgumentException("$name is required")
    }
}

@RestController
@RequestMapping("/api")
class ContactController(
    private val messageService: MessageService,
) {
    @GetMapping("/worlds/{sessionId}/contacts")
    fun getContacts(@PathVariable sessionId: Long): ResponseEntity<List<ContactInfo>> {
        return ResponseEntity.ok(messageService.getContacts(sessionId))
    }
}
