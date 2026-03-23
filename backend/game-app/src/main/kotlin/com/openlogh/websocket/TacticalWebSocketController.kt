package com.openlogh.websocket

import com.openlogh.engine.tactical.*
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseBody
import kotlin.random.Random

// ===== REST request/response DTOs =====

data class CreateTacticalRequest(
    val gameSessionId: Long = 0,
    val attackerOfficerIds: List<Long> = emptyList(),
    val defenderOfficerIds: List<Long> = emptyList(),
    val planetId: Long = 0,
)

data class CreateTacticalResponse(
    val sessionCode: String,
    val message: String,
)

data class TacticalErrorResponse(
    val type: String = "tactical_error",
    val message: String,
)

@Controller
class TacticalWebSocketController(
    private val sessionManager: TacticalSessionManager,
    private val turnScheduler: TacticalTurnScheduler,
    private val resultWriteback: TacticalResultWriteback,
    private val tacticalAI: TacticalAI,
    private val messagingTemplate: SimpMessagingTemplate?,
) {
    companion object {
        private val log = LoggerFactory.getLogger(TacticalWebSocketController::class.java)
    }

    // ===== WebSocket Handlers (클라이언트 → 서버) =====

    @MessageMapping("/tactical/{sessionCode}/join")
    fun joinBattle(
        @DestinationVariable sessionCode: String,
        @Payload msg: JoinMessage,
    ) {
        log.info("Tactical join: session={}, officer={}", sessionCode, msg.officerId)

        val session = sessionManager.joinSession(sessionCode, msg.officerId)
        if (session == null) {
            sendError(sessionCode, "세션을 찾을 수 없거나 참가 권한이 없습니다.")
            return
        }

        // 참가 알림 브로드캐스트
        broadcastState(session)

        // 전원 참가 시 전투 시작
        if (session.isAllJoined()) {
            log.info("All officers joined session {}, starting setup phase", sessionCode)
            session.battleSession.phase = BattlePhase.SETUP
            broadcastState(session)
        }
    }

    @MessageMapping("/tactical/{sessionCode}/setup")
    fun submitSetup(
        @DestinationVariable sessionCode: String,
        @Payload setup: SetupMessage,
    ) {
        log.debug("Tactical setup: session={}, officer={}", sessionCode, setup.officerId)

        val formation = setup.formation?.let { Formation.fromCode(it) }
        val energy = setup.energy?.let { parseEnergy(it) }

        sessionManager.submitSetup(sessionCode, setup.officerId, formation, energy)
    }

    @MessageMapping("/tactical/{sessionCode}/order")
    fun submitOrder(
        @DestinationVariable sessionCode: String,
        @Payload order: TacticalOrder,
    ) {
        log.debug("Tactical order: session={}, officer={}, type={}", sessionCode, order.officerId, order.type)
        sessionManager.submitOrder(sessionCode, order)
    }

    @MessageMapping("/tactical/{sessionCode}/ready")
    fun markReady(
        @DestinationVariable sessionCode: String,
        @Payload msg: ReadyMessage,
    ) {
        log.debug("Tactical ready: session={}, officer={}", sessionCode, msg.officerId)

        val session = sessionManager.getSession(sessionCode) ?: return
        sessionManager.markReady(sessionCode, msg.officerId)

        // 셋업 단계에서 전원 준비 → 전투 시작
        if (session.battleSession.phase == BattlePhase.SETUP && session.isAllReady()) {
            startCombat(session)
            return
        }

        // 전투 단계에서 전원 준비 → 즉시 턴 진행
        if (session.battleSession.phase == BattlePhase.COMBAT && session.isAllReady()) {
            turnScheduler.onAllReady(sessionCode)
        }
    }

    // ===== REST Endpoints (테스트/디버그용) =====

    @PostMapping("/api/tactical/create")
    @ResponseBody
    fun createSession(@RequestBody request: CreateTacticalRequest): ResponseEntity<Any> {
        return try {
            val sessionCode = sessionManager.createSession(
                gameSessionId = request.gameSessionId,
                attackerOfficerIds = request.attackerOfficerIds,
                defenderOfficerIds = request.defenderOfficerIds,
                planetId = request.planetId,
            )
            ResponseEntity.ok(CreateTacticalResponse(sessionCode, "전술 전투 세션이 생성되었습니다."))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(TacticalErrorResponse(message = e.message ?: "세션 생성 실패"))
        }
    }

    @GetMapping("/api/tactical/{sessionCode}")
    @ResponseBody
    fun getSessionState(@PathVariable sessionCode: String): ResponseEntity<Any> {
        val session = sessionManager.getSession(sessionCode)
            ?: return ResponseEntity.notFound().build()

        val timer = turnScheduler.getRemainingSeconds(sessionCode)
        return ResponseEntity.ok(session.toStateDto(timer))
    }

    // ===== Turn Processing =====

    private fun startCombat(session: TacticalGameSession) {
        val sessionCode = session.sessionCode
        log.info("Starting combat for session {}", sessionCode)

        session.battleSession.phase = BattlePhase.COMBAT
        session.readyOfficers.clear()
        broadcastState(session)

        // 첫 턴 타이머 시작
        startTurnTimer(session)
    }

    private fun startTurnTimer(session: TacticalGameSession) {
        turnScheduler.startTimer(session.sessionCode) {
            processTurn(session)
        }
    }

    private fun processTurn(session: TacticalGameSession) {
        val sessionCode = session.sessionCode
        val battleSession = session.battleSession

        if (battleSession.isFinished()) return

        // 미입력 장교에 이전 턴 명령 적용
        session.applyPreviousOrdersForMissing()

        // NPC 장교에 AI 명령 생성
        val aiRng = Random(System.currentTimeMillis() + battleSession.currentTurn)
        for (officerId in session.allOfficerIds) {
            if (officerId !in session.joinedOfficers && officerId !in battleSession.pendingOrders) {
                val fleet = session.findFleetByOfficer(officerId) ?: continue
                if (fleet.isDefeated()) continue
                val aiOrders = tacticalAI.generateOrders(battleSession, fleet, aiRng)
                battleSession.submitOrders(officerId, aiOrders)
            }
        }

        // 엔진으로 턴 처리
        val engine = TacticalBattleEngine(Random(System.currentTimeMillis() + battleSession.currentTurn))
        val result = engine.resolveTurn(battleSession)

        log.info("Turn {} resolved for session {}: {} events, victory={}",
            result.turn, sessionCode, result.events.size, result.victory?.victoryType)

        // 턴 결과 브로드캐스트
        broadcastTurnResult(session, result)
        broadcastState(session)

        if (result.victory != null) {
            // 전투 종료
            val victoryDto = session.toVictoryDto(result.victory)
            messagingTemplate?.convertAndSend("/topic/tactical/$sessionCode/victory", victoryDto)
            log.info("Battle ended: session={}, winner={}, type={}",
                sessionCode, victoryDto.winnerSide, victoryDto.victoryType)

            // 전투 결과 DB 반영
            try {
                resultWriteback.applyResult(
                    battleSession, result.victory,
                    session.attackerFactionId, session.defenderFactionId,
                )
            } catch (e: Exception) {
                log.error("Failed to write back tactical result for session {}", sessionCode, e)
            }

            // 세션 정리 (약간 지연 후)
            turnScheduler.cancelTimer(sessionCode)
            // 클라이언트에 결과 확인 시간 부여 후 정리
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor().schedule({
                sessionManager.destroySession(sessionCode)
            }, 30, java.util.concurrent.TimeUnit.SECONDS)
        } else {
            // 다음 턴 준비
            session.prepareNextTurn()
            broadcastState(session)
            startTurnTimer(session)
        }
    }

    // ===== Broadcasting =====

    private fun broadcastState(session: TacticalGameSession) {
        val timer = turnScheduler.getRemainingSeconds(session.sessionCode)
        val stateDto = session.toStateDto(timer)
        messagingTemplate?.convertAndSend("/topic/tactical/${session.sessionCode}/state", stateDto)
    }

    private fun broadcastTurnResult(session: TacticalGameSession, result: TurnResult) {
        val events = result.events.map { it.toDto() }
        val payload = mapOf(
            "turn" to result.turn,
            "events" to events,
            "attackerFleetSummaries" to result.attackerFleetSummaries,
            "defenderFleetSummaries" to result.defenderFleetSummaries,
        )
        messagingTemplate?.convertAndSend("/topic/tactical/${session.sessionCode}/turn-result", payload)
    }

    private fun sendError(sessionCode: String, message: String) {
        messagingTemplate?.convertAndSend(
            "/topic/tactical/$sessionCode/state",
            TacticalErrorResponse(message = message),
        )
    }

    // ===== Helpers =====

    private fun parseEnergy(energyMap: Map<String, Int>): EnergyAllocation? {
        return try {
            EnergyAllocation(
                beam = energyMap["beam"] ?: return null,
                gun = energyMap["gun"] ?: return null,
                shield = energyMap["shield"] ?: return null,
                engine = energyMap["engine"] ?: return null,
                sensor = energyMap["sensor"] ?: return null,
            )
        } catch (e: IllegalArgumentException) {
            log.warn("Invalid energy allocation: {}", e.message)
            null
        }
    }
}
