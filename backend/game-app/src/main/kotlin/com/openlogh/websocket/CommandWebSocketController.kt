package com.openlogh.websocket

import com.openlogh.service.GameEventService
import org.slf4j.LoggerFactory
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Controller

data class CommandExecuteMessage(
    val officerId: Long = 0,
    val commandCode: String = "",
    val arg: String = "",
)

data class CommandResultResponse(
    val type: String = "command_result",
    val officerId: Long = 0,
    val commandCode: String = "",
    val success: Boolean = false,
    val message: String = "",
)

@Controller
class CommandWebSocketController(
    private val messagingTemplate: SimpMessagingTemplate?,
    private val gameEventService: GameEventService,
) {
    companion object {
        private val log = LoggerFactory.getLogger(CommandWebSocketController::class.java)
    }

    @MessageMapping("/command/{sessionId}/execute")
    fun handleCommandExecution(
        @DestinationVariable sessionId: Long,
        command: CommandExecuteMessage,
    ) {
        log.info("Realtime command: session={}, officer={}, command={}", sessionId, command.officerId, command.commandCode)

        // Realtime commands are acknowledged immediately and processed asynchronously.
        // The actual command execution happens through the turn system (OfficerTurn).
        // This handler provides instant feedback that the command was received.
        gameEventService.broadcastCommandResult(
            worldId = sessionId,
            officerId = command.officerId,
            commandCode = command.commandCode,
            success = true,
            message = "커맨드가 접수되었습니다: ${command.commandCode}",
        )
    }
}
