package com.openlogh.controller

import com.openlogh.model.EnergyAllocation
import com.openlogh.model.UnitStance
import com.openlogh.service.TacticalBattleService
import org.slf4j.LoggerFactory
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Controller

/**
 * WebSocket controller for per-battle energy and stance commands.
 *
 * Channels (gin7 스펙):
 *   발행: /app/battle/{sessionId}/{battleId}/energy  → 에너지 배분 변경
 *   발행: /app/battle/{sessionId}/{battleId}/stance  → 태세 변경
 *   구독: /topic/world/{sessionId}/tactical-battle/{battleId}
 *
 * officerId는 payload에 포함 (JWT principal 은 String subject — OfficerPrincipal 미구현).
 */
@Controller
class BattleWebSocketController(
    private val tacticalBattleService: TacticalBattleService,
) {
    private val log = LoggerFactory.getLogger(BattleWebSocketController::class.java)

    /**
     * 에너지 배분 변경.
     * Channel: /app/battle/{sessionId}/{battleId}/energy
     *
     * BEAM+GUN+SHIELD+ENGINE+WARP+SENSOR == 100 검증은 EnergyAllocation init 블록에서 처리.
     */
    @MessageMapping("/battle/{sessionId}/{battleId}/energy")
    fun updateEnergy(
        @DestinationVariable sessionId: Long,
        @DestinationVariable battleId: Long,
        @Payload payload: EnergyAllocationRequest,
    ) {
        try {
            val allocation = EnergyAllocation(
                beam = payload.beam,
                gun = payload.gun,
                shield = payload.shield,
                engine = payload.engine,
                warp = payload.warp,
                sensor = payload.sensor,
            )
            tacticalBattleService.setEnergyAllocation(battleId, payload.officerId, allocation)
        } catch (e: Exception) {
            log.error("Error updating energy for battle {} officer {}: {}", battleId, payload.officerId, e.message)
        }
    }

    /**
     * 태세 변경.
     * Channel: /app/battle/{sessionId}/{battleId}/stance
     *
     * 10틱 쿨다운 위반 시 IllegalArgumentException — 클라이언트에 에러 로그.
     */
    @MessageMapping("/battle/{sessionId}/{battleId}/stance")
    fun updateStance(
        @DestinationVariable sessionId: Long,
        @DestinationVariable battleId: Long,
        @Payload payload: StanceChangeRequest,
    ) {
        try {
            val stance = UnitStance.fromString(payload.stance)
            tacticalBattleService.setStance(battleId, payload.officerId, stance)
        } catch (e: Exception) {
            log.error("Error updating stance for battle {} officer {}: {}", battleId, payload.officerId, e.message)
        }
    }
}

/** 에너지 배분 변경 요청 DTO */
data class EnergyAllocationRequest(
    val officerId: Long,
    val beam: Int,
    val gun: Int,
    val shield: Int,
    val engine: Int,
    val warp: Int,
    val sensor: Int,
)

/** 태세 변경 요청 DTO */
data class StanceChangeRequest(
    val officerId: Long,
    val stance: String,
)
