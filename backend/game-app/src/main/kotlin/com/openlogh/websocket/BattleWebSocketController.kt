package com.openlogh.websocket

import com.openlogh.engine.war.BattleService
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.PlanetRepository
import com.openlogh.repository.SessionStateRepository
import com.openlogh.service.GameEventService
import org.slf4j.LoggerFactory
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.SendTo
import org.springframework.stereotype.Controller

data class BattleCommandMessage(
    val officerId: Long = 0,
    val targetPlanetId: Long = 0,
    val action: String = "attack",
)

data class BattleEventResponse(
    val type: String,
    val attackerName: String = "",
    val planetName: String = "",
    val attackerWon: Boolean = false,
    val cityOccupied: Boolean = false,
    val attackerDamageDealt: Int = 0,
    val defenderDamageDealt: Int = 0,
    val message: String = "",
)

@Controller
class BattleWebSocketController(
    private val battleService: BattleService,
    private val officerRepository: OfficerRepository,
    private val planetRepository: PlanetRepository,
    private val sessionStateRepository: SessionStateRepository,
    private val gameEventService: GameEventService,
) {
    companion object {
        private val log = LoggerFactory.getLogger(BattleWebSocketController::class.java)
    }

    @MessageMapping("/battle/{sessionId}/command")
    @SendTo("/topic/world/{sessionId}/battle")
    fun handleBattleCommand(
        @DestinationVariable sessionId: Long,
        command: BattleCommandMessage,
    ): BattleEventResponse {
        log.info("Battle command: session={}, officer={}, target={}", sessionId, command.officerId, command.targetPlanetId)

        val officer = officerRepository.findById(command.officerId).orElse(null)
            ?: return errorResponse("장교를 찾을 수 없습니다.")

        if (officer.sessionId != sessionId) {
            return errorResponse("잘못된 세션입니다.")
        }
        if (officer.ships <= 0) {
            return errorResponse("함선이 없어 전투할 수 없습니다.")
        }

        val planet = planetRepository.findById(command.targetPlanetId).orElse(null)
            ?: return errorResponse("행성을 찾을 수 없습니다.")

        if (officer.factionId == planet.factionId) {
            return errorResponse("아군 행성을 공격할 수 없습니다.")
        }

        val world = sessionStateRepository.findById(sessionId.toShort()).orElse(null)
            ?: return errorResponse("세션을 찾을 수 없습니다.")

        val result = battleService.executeBattle(officer, planet, world)

        val response = BattleEventResponse(
            type = "battle_result",
            attackerName = officer.name,
            planetName = planet.name,
            attackerWon = result.attackerWon,
            cityOccupied = result.cityOccupied,
            attackerDamageDealt = result.attackerDamageDealt,
            defenderDamageDealt = result.defenderDamageDealt,
            message = if (result.cityOccupied) "${officer.name}이(가) ${planet.name}을(를) 점령했습니다!"
                else if (result.attackerWon) "${officer.name}이(가) ${planet.name} 전투에서 승리했습니다."
                else "${planet.name} 전투에서 방어에 성공했습니다.",
        )

        return response
    }

    private fun errorResponse(message: String) = BattleEventResponse(
        type = "battle_error",
        message = message,
    )
}
