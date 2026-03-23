package com.openlogh.service

import org.slf4j.LoggerFactory
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service

@Service
class GameEventService(
    private val messagingTemplate: SimpMessagingTemplate?,
) {
    companion object {
        private val log = LoggerFactory.getLogger(GameEventService::class.java)
    }

    fun broadcastTurnAdvance(worldId: Long, year: Int, month: Int) {
        val payload = mapOf("worldId" to worldId, "year" to year, "month" to month)
        log.debug("Turn advance: world={}, year={}, month={}", worldId, year, month)
        messagingTemplate?.convertAndSend("/topic/world/$worldId/turn", payload)
    }

    fun broadcastEvent(worldId: Long, eventType: String, payload: Map<String, Any>) {
        log.debug("Game event: world={}, type={}", worldId, eventType)
        messagingTemplate?.convertAndSend("/topic/world/$worldId/event", mapOf("type" to eventType) + payload)
    }

    fun broadcastBattleResult(
        worldId: Long,
        attackerName: String,
        planetName: String,
        attackerWon: Boolean,
        cityOccupied: Boolean,
        attackerDamageDealt: Int,
        defenderDamageDealt: Int,
    ) {
        val payload = mapOf(
            "attackerName" to attackerName,
            "planetName" to planetName,
            "attackerWon" to attackerWon,
            "cityOccupied" to cityOccupied,
            "attackerDamageDealt" to attackerDamageDealt,
            "defenderDamageDealt" to defenderDamageDealt,
            "message" to if (cityOccupied) "${attackerName}이(가) ${planetName}을(를) 점령했습니다!"
                else "${planetName}에서 전투가 발생했습니다.",
        )
        log.info("Battle result: world={}, attacker={}, planet={}, won={}", worldId, attackerName, planetName, attackerWon)
        messagingTemplate?.convertAndSend("/topic/world/$worldId/battle", payload)
    }

    fun broadcastCommandResult(worldId: Long, officerId: Long, commandCode: String, success: Boolean, message: String) {
        val payload = mapOf(
            "officerId" to officerId,
            "commandCode" to commandCode,
            "success" to success,
            "message" to message,
        )
        log.debug("Command result: world={}, officer={}, command={}", worldId, officerId, commandCode)
        messagingTemplate?.convertAndSend("/topic/world/$worldId/event", mapOf("type" to "command_result") + payload)
    }

    fun broadcastDiplomacyEvent(worldId: Long, srcFactionName: String, destFactionName: String, action: String) {
        val payload = mapOf(
            "srcFaction" to srcFactionName,
            "destFaction" to destFactionName,
            "action" to action,
            "message" to "$srcFactionName → $destFactionName: $action",
        )
        log.info("Diplomacy event: world={}, {} -> {} ({})", worldId, srcFactionName, destFactionName, action)
        messagingTemplate?.convertAndSend("/topic/world/$worldId/diplomacy", payload)
    }
}
