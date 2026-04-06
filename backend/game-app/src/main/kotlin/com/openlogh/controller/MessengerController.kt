package com.openlogh.controller

import com.openlogh.service.MessengerService
import org.springframework.http.ResponseEntity
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*

// -- DTOs --

data class MessengerCallRequest(
    val callerId: Long,
    val calleeId: Long,
)

data class MessengerAcceptRequest(
    val calleeId: Long,
)

data class MessengerDeclineRequest(
    val calleeId: Long,
)

data class MessengerDisconnectRequest(
    val officerId: Long,
)

data class MessengerMessagePayload(
    val connectionId: Long,
    val senderId: Long,
    val content: String,
)

// -- REST Controller --

@RestController
@RequestMapping("/api/v1/world/{sessionId}/messenger")
class MessengerRestController(
    private val messengerService: MessengerService,
) {
    @PostMapping("/call")
    fun requestCall(
        @PathVariable sessionId: Long,
        @RequestBody request: MessengerCallRequest,
    ): ResponseEntity<Map<String, Any>> {
        val connection = messengerService.requestConnection(sessionId, request.callerId, request.calleeId)
        return ResponseEntity.ok(
            mapOf(
                "connectionId" to connection.id,
                "status" to connection.status,
            )
        )
    }

    @PostMapping("/connection/{connectionId}/accept")
    fun acceptCall(
        @PathVariable sessionId: Long,
        @PathVariable connectionId: Long,
        @RequestBody request: MessengerAcceptRequest,
    ): ResponseEntity<Map<String, Any>> {
        val connection = messengerService.acceptConnection(sessionId, connectionId, request.calleeId)
        return ResponseEntity.ok(
            mapOf(
                "connectionId" to connection.id,
                "status" to connection.status,
                "callerId" to connection.callerId,
            )
        )
    }

    @PostMapping("/connection/{connectionId}/decline")
    fun declineCall(
        @PathVariable sessionId: Long,
        @PathVariable connectionId: Long,
        @RequestBody request: MessengerDeclineRequest,
    ): ResponseEntity<Void> {
        messengerService.declineConnection(sessionId, connectionId, request.calleeId)
        return ResponseEntity.ok().build()
    }

    @PostMapping("/connection/{connectionId}/disconnect")
    fun disconnect(
        @PathVariable sessionId: Long,
        @PathVariable connectionId: Long,
        @RequestBody request: MessengerDisconnectRequest,
    ): ResponseEntity<Void> {
        messengerService.disconnect(sessionId, connectionId, request.officerId)
        return ResponseEntity.ok().build()
    }

    @GetMapping("/pending")
    fun getPendingCalls(
        @PathVariable sessionId: Long,
        @RequestParam officerId: Long,
    ): ResponseEntity<List<Map<String, Any>>> {
        return ResponseEntity.ok(messengerService.getPendingCalls(sessionId, officerId))
    }
}

// -- WebSocket Controller for real-time messenger messages --

@Controller
class MessengerWebSocketController(
    private val messengerService: MessengerService,
) {
    @MessageMapping("/messenger/{sessionId}/send")
    fun handleMessengerMessage(
        @DestinationVariable sessionId: Long,
        @Payload payload: MessengerMessagePayload,
    ) {
        messengerService.sendMessage(sessionId, payload.connectionId, payload.senderId, payload.content)
    }
}
