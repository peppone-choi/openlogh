package com.openlogh.controller

import com.openlogh.dto.BattleCommandRequest
import com.openlogh.model.EnergyAllocation
import com.openlogh.model.Formation
import com.openlogh.service.TacticalBattleService
import org.slf4j.LoggerFactory
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Controller

/**
 * WebSocket controller for real-time tactical battle commands.
 * Players send commands via /app/battle/{sessionId}/command
 * and receive updates on /topic/world/{sessionId}/tactical-battle/{battleId}
 */
@Controller
class TacticalBattleController(
    private val tacticalBattleService: TacticalBattleService,
) {
    private val log = LoggerFactory.getLogger(TacticalBattleController::class.java)

    @MessageMapping("/battle/{sessionId}/command")
    fun handleBattleCommand(
        @DestinationVariable sessionId: Long,
        @Payload command: BattleCommandRequest,
    ) {
        try {
            when (command.commandType) {
                "energy" -> {
                    val energyMap = command.energy ?: throw IllegalArgumentException("Energy allocation required")
                    val allocation = EnergyAllocation.fromMap(energyMap)
                    tacticalBattleService.setEnergyAllocation(command.battleId, command.officerId, allocation)
                }
                "formation" -> {
                    val formation = Formation.fromString(command.formation ?: throw IllegalArgumentException("Formation required"))
                    tacticalBattleService.setFormation(command.battleId, command.officerId, formation)
                }
                "retreat" -> {
                    tacticalBattleService.retreat(command.battleId, command.officerId)
                }
                else -> {
                    log.warn("Unknown battle command type: {}", command.commandType)
                }
            }
        } catch (e: Exception) {
            log.error("Error processing battle command: {}", e.message, e)
        }
    }
}
