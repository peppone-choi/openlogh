package com.openlogh.controller

import com.openlogh.dto.RespondLetterRequest
import com.openlogh.dto.SendLetterRequest
import com.openlogh.entity.Message
import com.openlogh.repository.MessageRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.OffsetDateTime

@RestController
@RequestMapping("/api")
class DiplomacyLetterController(
    private val messageRepository: MessageRepository,
) {
    @GetMapping("/nations/{nationId}/diplomacy-letters")
    fun list(@PathVariable nationId: Long): ResponseEntity<List<Message>> {
        val letters = messageRepository.findBySessionIdAndMailboxCodeOrderBySentAtDesc(nationId, "diplomacy")
        return ResponseEntity.ok(letters)
    }

    @PostMapping("/nations/{nationId}/diplomacy-letters")
    fun send(
        @PathVariable nationId: Long,
        @RequestBody req: SendLetterRequest,
    ): ResponseEntity<Message> {
        val letter = Message(
            sessionId = req.sessionId,
            mailboxCode = "diplomacy",
            mailboxType = "DIPLOMACY",
            messageType = req.type,
            srcId = nationId,
            destId = req.destFactionId,
            sentAt = OffsetDateTime.now(),
            payload = mutableMapOf<String, Any>().apply {
                if (req.content != null) put("content", req.content)
                put("type", req.type)
            },
        )
        val saved = messageRepository.save(letter)
        return ResponseEntity.ok(saved)
    }

    @PostMapping("/diplomacy-letters/{letterId}/respond")
    fun respond(
        @PathVariable letterId: Long,
        @RequestBody req: RespondLetterRequest,
    ): ResponseEntity<Map<String, Boolean>> {
        val letter = messageRepository.findById(letterId).orElse(null)
            ?: return ResponseEntity.notFound().build()
        letter.meta["responded"] = true
        letter.meta["accepted"] = req.accept
        messageRepository.save(letter)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @PostMapping("/diplomacy-letters/{letterId}/execute")
    fun execute(@PathVariable letterId: Long): ResponseEntity<Map<String, Boolean>> {
        val letter = messageRepository.findById(letterId).orElse(null)
            ?: return ResponseEntity.notFound().build()
        letter.meta["executed"] = true
        messageRepository.save(letter)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @PostMapping("/diplomacy-letters/{letterId}/rollback")
    fun rollback(@PathVariable letterId: Long): ResponseEntity<Map<String, Boolean>> {
        val letter = messageRepository.findById(letterId).orElse(null)
            ?: return ResponseEntity.notFound().build()
        letter.meta["rolledBack"] = true
        messageRepository.save(letter)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @PostMapping("/diplomacy-letters/{letterId}/destroy")
    fun destroy(@PathVariable letterId: Long): ResponseEntity<Void> {
        if (!messageRepository.existsById(letterId)) return ResponseEntity.notFound().build()
        messageRepository.deleteById(letterId)
        return ResponseEntity.ok().build()
    }
}
