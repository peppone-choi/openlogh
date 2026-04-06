package com.openlogh.service

import com.openlogh.entity.MessengerConnection
import com.openlogh.repository.MessengerConnectionRepository
import com.openlogh.repository.OfficerRepository
import org.slf4j.LoggerFactory
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

/**
 * 1-to-1 messenger (메신저 1:1 통화) service.
 *
 * Rules:
 * - Only 1 active connection at a time per officer
 * - New request cancels old active connection
 * - Connection flow: select online user -> request -> wait for acceptance
 * - Multiple incoming calls: accepting one cancels others
 */
@Service
class MessengerService(
    private val messengerConnectionRepository: MessengerConnectionRepository,
    private val officerRepository: OfficerRepository,
    private val messagingTemplate: SimpMessagingTemplate,
) {
    private val logger = LoggerFactory.getLogger(MessengerService::class.java)

    /**
     * Request a 1:1 messenger connection.
     * Cancels any existing active/pending connections for the caller.
     */
    @Transactional
    fun requestConnection(sessionId: Long, callerId: Long, calleeId: Long): MessengerConnection {
        require(callerId != calleeId) { "Cannot call yourself" }

        val caller = officerRepository.findById(callerId).orElseThrow {
            IllegalArgumentException("Caller officer not found: $callerId")
        }
        val callee = officerRepository.findById(calleeId).orElseThrow {
            IllegalArgumentException("Callee officer not found: $calleeId")
        }

        // Cancel any existing active/pending connections for the caller
        cancelExistingConnections(sessionId, callerId)

        val connection = messengerConnectionRepository.save(
            MessengerConnection(
                sessionId = sessionId,
                callerId = callerId,
                calleeId = calleeId,
                status = "PENDING",
            )
        )

        // Notify callee via WebSocket
        messagingTemplate.convertAndSend(
            "/topic/messenger/$sessionId/officer/$calleeId",
            mapOf(
                "type" to "INCOMING_CALL",
                "connectionId" to connection.id,
                "callerId" to callerId,
                "callerName" to caller.name,
                "callerFactionId" to caller.factionId,
            )
        )

        return connection
    }

    /**
     * Accept an incoming call. Cancels all other pending calls for the callee.
     */
    @Transactional
    fun acceptConnection(sessionId: Long, connectionId: Long, calleeId: Long): MessengerConnection {
        val connection = messengerConnectionRepository.findById(connectionId).orElseThrow {
            IllegalArgumentException("Connection not found: $connectionId")
        }
        require(connection.calleeId == calleeId) { "Not the callee of this connection" }
        require(connection.status == "PENDING") { "Connection is not pending" }

        // Cancel callee's existing active connections
        cancelExistingConnections(sessionId, calleeId)

        // Cancel all other pending calls for this callee
        val otherPending = messengerConnectionRepository
            .findBySessionIdAndCalleeIdAndStatus(sessionId, calleeId, "PENDING")
            .filter { it.id != connectionId }
        for (other in otherPending) {
            other.status = "CANCELLED"
            other.resolvedAt = OffsetDateTime.now()
            messengerConnectionRepository.save(other)

            // Notify the other callers that their call was cancelled
            messagingTemplate.convertAndSend(
                "/topic/messenger/$sessionId/officer/${other.callerId}",
                mapOf(
                    "type" to "CALL_CANCELLED",
                    "connectionId" to other.id,
                    "reason" to "callee_accepted_another",
                )
            )
        }

        // Accept this connection
        connection.status = "ACTIVE"
        connection.resolvedAt = OffsetDateTime.now()
        messengerConnectionRepository.save(connection)

        val callee = officerRepository.findById(calleeId).orElse(null)

        // Notify caller that call was accepted
        messagingTemplate.convertAndSend(
            "/topic/messenger/$sessionId/officer/${connection.callerId}",
            mapOf(
                "type" to "CALL_ACCEPTED",
                "connectionId" to connection.id,
                "calleeId" to calleeId,
                "calleeName" to (callee?.name ?: ""),
            )
        )

        return connection
    }

    /**
     * Decline an incoming call.
     */
    @Transactional
    fun declineConnection(sessionId: Long, connectionId: Long, calleeId: Long) {
        val connection = messengerConnectionRepository.findById(connectionId).orElseThrow {
            IllegalArgumentException("Connection not found: $connectionId")
        }
        require(connection.calleeId == calleeId) { "Not the callee of this connection" }
        require(connection.status == "PENDING") { "Connection is not pending" }

        connection.status = "DECLINED"
        connection.resolvedAt = OffsetDateTime.now()
        messengerConnectionRepository.save(connection)

        messagingTemplate.convertAndSend(
            "/topic/messenger/$sessionId/officer/${connection.callerId}",
            mapOf(
                "type" to "CALL_DECLINED",
                "connectionId" to connection.id,
            )
        )
    }

    /**
     * Disconnect an active messenger connection.
     * Either party can disconnect.
     */
    @Transactional
    fun disconnect(sessionId: Long, connectionId: Long, officerId: Long) {
        val connection = messengerConnectionRepository.findById(connectionId).orElseThrow {
            IllegalArgumentException("Connection not found: $connectionId")
        }
        require(connection.callerId == officerId || connection.calleeId == officerId) {
            "Not a party of this connection"
        }
        require(connection.status == "ACTIVE") { "Connection is not active" }

        connection.status = "CANCELLED"
        connection.resolvedAt = OffsetDateTime.now()
        messengerConnectionRepository.save(connection)

        // Notify the other party
        val otherId = if (connection.callerId == officerId) connection.calleeId else connection.callerId
        messagingTemplate.convertAndSend(
            "/topic/messenger/$sessionId/officer/$otherId",
            mapOf(
                "type" to "CALL_DISCONNECTED",
                "connectionId" to connection.id,
                "disconnectedBy" to officerId,
            )
        )
    }

    /**
     * Send a messenger text message within an active connection.
     */
    fun sendMessage(sessionId: Long, connectionId: Long, senderId: Long, content: String) {
        val connection = messengerConnectionRepository.findById(connectionId).orElse(null)
            ?: throw IllegalArgumentException("Connection not found: $connectionId")
        require(connection.status == "ACTIVE") { "Connection is not active" }
        require(connection.callerId == senderId || connection.calleeId == senderId) {
            "Not a party of this connection"
        }

        val recipientId = if (connection.callerId == senderId) connection.calleeId else connection.callerId
        val sender = officerRepository.findById(senderId).orElse(null)

        val payload = mapOf(
            "type" to "MESSAGE",
            "connectionId" to connectionId,
            "senderId" to senderId,
            "senderName" to (sender?.name ?: ""),
            "content" to content,
            "timestamp" to OffsetDateTime.now().toString(),
        )

        // Send to both parties
        messagingTemplate.convertAndSend("/topic/messenger/$sessionId/officer/$recipientId", payload)
        messagingTemplate.convertAndSend("/topic/messenger/$sessionId/officer/$senderId", payload)
    }

    /**
     * Get pending incoming calls for an officer.
     */
    fun getPendingCalls(sessionId: Long, officerId: Long): List<Map<String, Any>> {
        val pending = messengerConnectionRepository
            .findBySessionIdAndCalleeIdAndStatus(sessionId, officerId, "PENDING")
        return pending.map { conn ->
            val caller = officerRepository.findById(conn.callerId).orElse(null)
            mapOf(
                "connectionId" to conn.id as Any,
                "callerId" to conn.callerId as Any,
                "callerName" to (caller?.name ?: "") as Any,
                "callerFactionId" to (caller?.factionId ?: 0L) as Any,
                "createdAt" to conn.createdAt.toString() as Any,
            )
        }
    }

    /**
     * Cancel all active/pending connections involving an officer.
     */
    @Transactional
    fun cancelExistingConnections(sessionId: Long, officerId: Long) {
        val now = OffsetDateTime.now()

        // Cancel as caller
        val asCallerPending = messengerConnectionRepository
            .findBySessionIdAndCallerIdAndStatus(sessionId, officerId, "PENDING")
        val asCallerActive = messengerConnectionRepository
            .findBySessionIdAndCallerIdAndStatus(sessionId, officerId, "ACTIVE")

        for (conn in asCallerPending + asCallerActive) {
            conn.status = "CANCELLED"
            conn.resolvedAt = now
            messengerConnectionRepository.save(conn)

            val otherId = if (conn.callerId == officerId) conn.calleeId else conn.callerId
            messagingTemplate.convertAndSend(
                "/topic/messenger/$sessionId/officer/$otherId",
                mapOf(
                    "type" to "CALL_CANCELLED",
                    "connectionId" to conn.id,
                    "reason" to "caller_new_request",
                )
            )
        }

        // Cancel as callee (active only)
        val asCalleeActive = messengerConnectionRepository
            .findBySessionIdAndCalleeIdAndStatus(sessionId, officerId, "ACTIVE")
        for (conn in asCalleeActive) {
            conn.status = "CANCELLED"
            conn.resolvedAt = now
            messengerConnectionRepository.save(conn)

            messagingTemplate.convertAndSend(
                "/topic/messenger/$sessionId/officer/${conn.callerId}",
                mapOf(
                    "type" to "CALL_CANCELLED",
                    "connectionId" to conn.id,
                    "reason" to "callee_new_connection",
                )
            )
        }
    }
}
