package com.openlogh.service

import com.openlogh.dto.*
import com.openlogh.engine.tactical.*
import com.openlogh.entity.TacticalBattle
import com.openlogh.model.BattlePhase
import com.openlogh.model.EnergyAllocation
import com.openlogh.model.Formation
import com.openlogh.model.InjuryEvent
import com.openlogh.model.UnitStance
import com.openlogh.repository.FleetRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.TacticalBattleRepository
import kotlin.math.sqrt
import org.slf4j.LoggerFactory
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages tactical battle lifecycle: creation, tick processing, player commands, and completion.
 * Active battle states are held in-memory for fast tick processing.
 */
@Service
class TacticalBattleService(
    private val tacticalBattleRepository: TacticalBattleRepository,
    private val fleetRepository: FleetRepository,
    private val officerRepository: OfficerRepository,
    private val battleTriggerService: BattleTriggerService,
    private val gameEventService: GameEventService,
    private val messagingTemplate: SimpMessagingTemplate,
    private val shipStatRegistry: ShipStatRegistry,
) {
    private val log = LoggerFactory.getLogger(TacticalBattleService::class.java)
    private val engine = TacticalBattleEngine(shipStatRegistry = shipStatRegistry)
    private val planetConquestService = PlanetConquestService()

    /** In-memory active battle states keyed by battleId */
    private val activeBattles = ConcurrentHashMap<Long, TacticalBattleState>()

    // ── Battle Lifecycle ──

    /**
     * Start a new tactical battle. Creates DB record and initializes in-memory state.
     */
    @Transactional
    fun startBattle(
        sessionId: Long,
        starSystemId: Long,
        attackerFleetIds: List<Long>,
        defenderFleetIds: List<Long>,
    ): TacticalBattle {
        val attackerFleets = attackerFleetIds.mapNotNull { fleetRepository.findById(it).orElse(null) }
        val defenderFleets = defenderFleetIds.mapNotNull { fleetRepository.findById(it).orElse(null) }

        require(attackerFleets.isNotEmpty()) { "At least one attacker fleet required" }
        require(defenderFleets.isNotEmpty()) { "At least one defender fleet required" }

        val battle = battleTriggerService.createBattle(
            sessionId, starSystemId,
            attackerFleets.first().factionId, defenderFleets.first().factionId,
            attackerFleets, defenderFleets,
        ) ?: throw IllegalStateException("Failed to create battle")

        // Build initial state and activate
        val state = battleTriggerService.buildInitialState(battle)
        activeBattles[battle.id] = state

        battle.phase = BattlePhase.ACTIVE.name
        tacticalBattleRepository.save(battle)

        log.info("Tactical battle {} started at star system {} with {} vs {} units",
            battle.id, starSystemId, attackerFleets.size, defenderFleets.size)

        // Broadcast battle start
        broadcastBattleState(battle.sessionId, state, battle)

        return battle
    }

    /**
     * Process one tick for all active battles in a session.
     * Called by the game tick engine.
     * Also checks for new battles via BattleTriggerService and auto-registers them.
     */
    fun processSessionBattles(sessionId: Long) {
        // 1. 새 전투 감지 및 등록
        val newBattles = battleTriggerService.checkForBattles(sessionId)
        newBattles.forEach { registerNewBattle(it) }

        // 2. 기존 활성 전투 틱 처리
        val activeBattleIds = tacticalBattleRepository.findBySessionIdAndPhase(sessionId, BattlePhase.ACTIVE.name)
            .map { it.id }

        for (battleId in activeBattleIds) {
            processBattleTick(battleId)
        }
    }

    /**
     * Register a newly detected battle (from BattleTriggerService) into active in-memory state.
     */
    fun registerNewBattle(battle: TacticalBattle) {
        val state = battleTriggerService.buildInitialState(battle)
        activeBattles[battle.id] = state

        battle.phase = BattlePhase.ACTIVE.name
        tacticalBattleRepository.save(battle)

        broadcastBattleState(battle.sessionId, state, battle)
        log.info("New battle {} auto-registered from trigger at star system {}", battle.id, battle.starSystemId)
    }

    /**
     * Process a single tick for a battle.
     */
    fun processBattleTick(battleId: Long) {
        val state = activeBattles[battleId] ?: return
        val battle = tacticalBattleRepository.findById(battleId).orElse(null) ?: return

        // Process tick (drains command buffer as step 0)
        engine.processTick(state)

        // Process any pending conquest commands (require service-level logic)
        val pendingConquests = state.pendingConquestCommands.toList()
        state.pendingConquestCommands.clear()
        for (cmd in pendingConquests) {
            try {
                @Suppress("DEPRECATION")
                executeConquest(battleId, cmd.officerId, cmd.request)
            } catch (e: Exception) {
                log.warn("Failed to process conquest command in battle {}: {}", battleId, e.message)
            }
        }

        // Check for battle end
        val outcome = engine.checkBattleEnd(state)
        if (outcome != null) {
            endBattle(battle, state, outcome)
            return
        }

        // Update tick count in DB periodically (every 10 ticks to reduce DB writes)
        if (state.tickCount % 10 == 0) {
            battle.tickCount = state.tickCount
            tacticalBattleRepository.save(battle)
        }

        // Broadcast state to all participants
        broadcastBattleState(battle.sessionId, state, battle)
    }

    // ── Player Commands ──

    /**
     * Enqueue a tactical command for processing at next tick start.
     * Thread-safe: ConcurrentLinkedQueue.offer() is lock-free.
     */
    fun enqueueCommand(battleId: Long, command: TacticalCommand) {
        activeBattles[battleId]?.commandBuffer?.offer(command)
            ?: log.warn("Cannot enqueue command: battle {} not found", battleId)
    }

    /**
     * Set energy allocation for a unit in battle.
     */
    @Deprecated("Use enqueueCommand instead", ReplaceWith("enqueueCommand(battleId, TacticalCommand.SetEnergy(battleId, officerId, allocation))"))
    fun setEnergyAllocation(battleId: Long, officerId: Long, allocation: EnergyAllocation) {
        val state = activeBattles[battleId] ?: throw IllegalStateException("Battle $battleId not active")
        val unit = state.units.find { it.officerId == officerId && it.isAlive }
            ?: throw IllegalArgumentException("Officer $officerId not found in battle $battleId")

        unit.energy = allocation
        unit.commandRange = unit.commandRange.resetOnCommand()  // Reset command range on new order

        log.debug("Officer {} set energy allocation in battle {}: B={} G={} S={} E={} W={} SE={}",
            officerId, battleId, allocation.beam, allocation.gun, allocation.shield,
            allocation.engine, allocation.warp, allocation.sensor)
    }

    /**
     * Set formation for a unit in battle.
     */
    @Deprecated("Use enqueueCommand instead")
    fun setFormation(battleId: Long, officerId: Long, formation: Formation) {
        val state = activeBattles[battleId] ?: throw IllegalStateException("Battle $battleId not active")
        val unit = state.units.find { it.officerId == officerId && it.isAlive }
            ?: throw IllegalArgumentException("Officer $officerId not found in battle $battleId")

        unit.formation = formation
        unit.commandRange = unit.commandRange.resetOnCommand()

        log.debug("Officer {} set formation to {} in battle {}", officerId, formation.name, battleId)
    }

    /**
     * Set stance for a unit in battle.
     * gin7 rule: 태세 변경은 stanceChangeTicksRemaining <= 0 일 때만 가능 (10틱 쿨다운).
     */
    @Deprecated("Use enqueueCommand instead")
    fun setStance(battleId: Long, officerId: Long, stance: UnitStance) {
        val state = activeBattles[battleId] ?: throw IllegalStateException("Battle $battleId not active")
        val unit = state.units.find { it.officerId == officerId && it.isAlive }
            ?: throw IllegalArgumentException("Officer $officerId not found in battle $battleId")

        require(unit.stanceChangeTicksRemaining <= 0) {
            "태세 변경 쿨다운 중 (잔여 ${unit.stanceChangeTicksRemaining}틱)"
        }

        unit.stance = stance
        unit.stanceChangeTicksRemaining = 10
        unit.commandRange = unit.commandRange.resetOnCommand()

        log.debug("Officer {} changed stance to {} in battle {}", officerId, stance.name, battleId)
    }

    /**
     * Set a specific attack target for a unit.
     * gin7 rule: 플레이어가 공격 대상 함대를 지정하면, 지정 대상이 살아있는 한 우선 공격.
     */
    @Deprecated("Use enqueueCommand instead")
    fun setAttackTarget(battleId: Long, officerId: Long, targetFleetId: Long) {
        val state = activeBattles[battleId] ?: throw IllegalStateException("Battle $battleId not active")
        val unit = state.units.find { it.officerId == officerId && it.isAlive }
            ?: throw IllegalArgumentException("Officer $officerId not found in battle $battleId")

        unit.targetFleetId = targetFleetId
        unit.commandRange = unit.commandRange.resetOnCommand()

        log.debug("Officer {} set attack target fleet {} in battle {}", officerId, targetFleetId, battleId)
    }

    /**
     * Initiate retreat for a unit. Requires WARP energy >= 50%.
     */
    @Deprecated("Use enqueueCommand instead")
    fun retreat(battleId: Long, officerId: Long) {
        val state = activeBattles[battleId] ?: throw IllegalStateException("Battle $battleId not active")
        val unit = state.units.find { it.officerId == officerId && it.isAlive }
            ?: throw IllegalArgumentException("Officer $officerId not found in battle $battleId")

        require(unit.energy.warpReadiness() >= 0.5) {
            "WARP energy insufficient for retreat (need 50%, have ${(unit.energy.warpReadiness() * 100).toInt()}%)"
        }

        unit.isRetreating = true
        unit.retreatProgress = 0.0
        log.info("Officer {} initiating retreat in battle {}", officerId, battleId)
    }

    /**
     * 행성 점령 커맨드 실행.
     *
     * gin7 6종: 항복권고/정밀폭격/무차별폭격/육전대강하/점거/선동
     * - GROUND_ASSAULT: groundUnitsEmbark > 0 확인 후 GroundBattleState 초기화
     * - 정밀폭격/무차별폭격: missileCount 소모 반영
     */
    @Deprecated("Use enqueueCommand instead")
    fun executeConquest(battleId: Long, officerId: Long, request: ConquestRequest): ConquestResult {
        val state = activeBattles[battleId]
            ?: throw IllegalStateException("Battle $battleId not active")
        val unit = state.units.find { it.officerId == officerId && it.isAlive }
            ?: throw IllegalArgumentException("Officer $officerId not found in battle $battleId")

        // 육전대강하: groundUnitsEmbark > 0 확인 + GroundBattleState 초기화
        if (request.command == ConquestCommand.GROUND_ASSAULT) {
            require(unit.groundUnitsEmbark > 0) { "탑재 육전대 없음" }
            if (state.groundBattleState == null) {
                state.groundBattleState = GroundBattleState(
                    planetId = request.planetId,
                    attackerFactionId = request.attackerFactionId,
                    defenderFactionId = request.defenderFactionId,
                )
            }
            val groundEngine = GroundBattleEngine()
            val attackers = (1..unit.groundUnitsEmbark).map { i ->
                GroundUnit(
                    unitId = unit.fleetId * 100 + i,
                    factionId = unit.factionId,
                    groundUnitType = "ARMORED_INFANTRY",
                    count = 100,
                    maxCount = 100,
                )
            }
            groundEngine.addAttackers(state.groundBattleState!!, attackers)
        }

        val result = planetConquestService.executeConquest(request)
        unit.commandRange = unit.commandRange.resetOnCommand()

        // 미사일 소모 반영
        if (result.missilesConsumed > 0) {
            unit.missileCount = (unit.missileCount - result.missilesConsumed).coerceAtLeast(0)
        }

        state.tickEvents.add(BattleTickEvent("conquest", sourceUnitId = unit.fleetId,
            detail = "${request.command.displayNameKo}: ${result.reason}"))

        log.info("Conquest {} by officer {}: {}", request.command, officerId, result.reason)
        return result
    }

    // ── Battle End ──

    @Transactional
    fun endBattle(battle: TacticalBattle, state: TacticalBattleState, outcome: BattleOutcome) {
        battle.phase = BattlePhase.ENDED.name
        battle.endedAt = OffsetDateTime.now()
        battle.tickCount = state.tickCount
        battle.result = when (outcome.winner) {
            BattleSide.ATTACKER -> "attacker_win"
            BattleSide.DEFENDER -> "defender_win"
            null -> "draw"
        }
        tacticalBattleRepository.save(battle)
        activeBattles.remove(battle.id)

        // Process flagship destructions (injury + warp to return planet)
        if (state.pendingInjuryEvents.isNotEmpty()) {
            processFlagshipDestructions(battle.sessionId, state)
        }

        // Update fleet ships based on battle results
        for (unit in state.units) {
            val fleet = fleetRepository.findById(unit.fleetId).orElse(null) ?: continue
            val officer = officerRepository.findById(unit.officerId).orElse(null) ?: continue

            // Update officer ships to surviving count
            officer.ships = unit.ships.coerceAtLeast(0)
            if (unit.morale < officer.morale.toInt()) {
                officer.morale = unit.morale.toShort()
            }
            officerRepository.save(officer)
        }

        // Fire battle event through existing event system
        gameEventService.fireBattle(
            worldId = battle.sessionId,
            year = 0, month = 0,  // Will be filled by caller with current game time
            attackerGeneralId = state.units.firstOrNull { it.side == BattleSide.ATTACKER }?.officerId ?: 0,
            defenderGeneralId = state.units.firstOrNull { it.side == BattleSide.DEFENDER }?.officerId ?: 0,
            attackerNationId = battle.attackerFactionId,
            defenderNationId = battle.defenderFactionId,
            cityId = battle.starSystemId,
            result = battle.result ?: "draw",
            detail = mapOf("reason" to outcome.reason, "ticks" to state.tickCount),
        )

        // Broadcast final state
        broadcastBattleState(battle.sessionId, state, battle)

        log.info("Tactical battle {} ended: {} ({})", battle.id, battle.result, outcome.reason)
    }

    /**
     * 전술 유닛 커맨드 11종 처리.
     * MOVE/TURN/STRAFE/REVERSE/ATTACK/FIRE/ORBIT/FORMATION_CHANGE/REPAIR/RESUPPLY/SORTIE
     */
    @Deprecated("Use enqueueCommand instead")
    fun executeUnitCommand(battleId: Long, cmd: UnitCommandRequest) {
        val state = activeBattles[battleId] ?: throw IllegalStateException("Battle $battleId not active")
        val unit = state.units.find { it.officerId == cmd.officerId && it.isAlive }
            ?: throw IllegalArgumentException("Officer ${cmd.officerId} not found in battle $battleId")

        // 모든 커맨드는 commandRange 리셋
        unit.commandRange = unit.commandRange.resetOnCommand()

        when (cmd.command.uppercase()) {
            "MOVE" -> {
                val norm = sqrt(cmd.dirX * cmd.dirX + cmd.dirY * cmd.dirY).coerceAtLeast(0.001)
                val spd = TacticalBattleEngine.BASE_SPEED * cmd.speed.coerceIn(0.0, 2.0)
                unit.velX = (cmd.dirX / norm) * spd
                unit.velY = (cmd.dirY / norm) * spd
            }
            "TURN" -> {
                val currentSpeed = sqrt(unit.velX * unit.velX + unit.velY * unit.velY)
                val norm = sqrt(cmd.dirX * cmd.dirX + cmd.dirY * cmd.dirY).coerceAtLeast(0.001)
                unit.velX = (cmd.dirX / norm) * currentSpeed
                unit.velY = (cmd.dirY / norm) * currentSpeed
            }
            "STRAFE" -> {
                // 방향 유지, 횡이동 (현재 속도 유지, dirY 방향으로 벡터 추가)
                unit.velY = cmd.dirY * TacticalBattleEngine.BASE_SPEED
            }
            "REVERSE" -> {
                unit.velX = -unit.velX
                unit.velY = -unit.velY
            }
            "ATTACK", "FIRE" -> {
                if (cmd.targetFleetId != null) unit.targetFleetId = cmd.targetFleetId
            }
            "ORBIT" -> {
                if (cmd.targetFleetId != null) unit.targetFleetId = cmd.targetFleetId
                unit.isOrbiting = true
            }
            "FORMATION_CHANGE" -> {
                val formation = cmd.formation?.let { Formation.fromString(it) } ?: Formation.MIXED
                unit.formation = formation
            }
            "REPAIR" -> {
                // 수리: 사기 5 소모 → 훈련 비례 HP 회복
                if (unit.morale >= 5) {
                    unit.morale -= 5
                    val repairAmount = (unit.training / 100.0 * unit.maxHp * 0.05).toInt().coerceAtLeast(1)
                    unit.hp = (unit.hp + repairAmount).coerceAtMost(unit.maxHp)
                    state.tickEvents.add(BattleTickEvent("repair", sourceUnitId = unit.fleetId,
                        value = repairAmount, detail = "${unit.officerName} 자함 수리 (+$repairAmount HP)"))
                }
            }
            "RESUPPLY" -> {
                // 보급: 행성 인근(100 units) 이내에서만 가능
                val nearPlanet = unit.posX < 100.0 || unit.posX > 900.0
                if (nearPlanet && unit.missileCount < 100) {
                    val resupplyAmount = 20
                    unit.missileCount = (unit.missileCount + resupplyAmount).coerceAtMost(100)
                    state.tickEvents.add(BattleTickEvent("resupply", sourceUnitId = unit.fleetId,
                        value = resupplyAmount, detail = "${unit.officerName} 미사일 보급 (+$resupplyAmount)"))
                }
            }
            "SORTIE" -> {
                // 전투정 발진: 가장 가까운 적에게 전투정 공격
                val enemies = state.units.filter { it.side != unit.side && it.isAlive }
                val target = enemies.minByOrNull {
                    val dx = unit.posX - it.posX
                    val dy = unit.posY - it.posY
                    sqrt(dx * dx + dy * dy)
                }
                if (target != null) {
                    engine.getMissileSystem().processFighterAttack(unit, target, state)
                }
            }
            else -> log.warn("Unknown unit command: {}", cmd.command)
        }
    }

    /**
     * 기함 격침으로 생성된 부상 이벤트를 처리한다.
     * officer.injury 갱신 + officer.planetId = returnPlanetId (귀환성 워프).
     */
    @Transactional
    fun processFlagshipDestructions(sessionId: Long, state: TacticalBattleState) {
        for (injuryEvent in state.pendingInjuryEvents) {
            val officer = officerRepository.findById(injuryEvent.officerId).orElse(null) ?: continue

            // 귀환성 결정: 설정된 귀환성 → 진영 수도(미구현, Phase 4) → 현재 행성
            val returnPlanetId = InjuryEvent.resolveReturnPlanet(
                configuredReturnPlanetId = null,   // officer.returnPlanetId 미구현 — Phase 4에서 추가
                factionCapitalPlanetId = null,     // FactionRepository 수도 조회 — Phase 4에서 구현
                currentPlanetId = officer.planetId ?: 1L,
            )

            // 부상 수치 누적 갱신 (0~80 스케일)
            val newInjury = (officer.injury.toInt() + injuryEvent.severity).coerceAtMost(InjuryEvent.MAX_INJURY)
            officer.injury = newInjury.toShort()

            // 귀환성 워프
            officer.planetId = returnPlanetId

            officerRepository.save(officer)

            log.info("Officer {} injured (severity={}) → warped to planet {}",
                officer.id, injuryEvent.severity, returnPlanetId)

            messagingTemplate.convertAndSend("/topic/world/$sessionId/events", mapOf(
                "type" to "officer_injured",
                "officerId" to officer.id,
                "officerName" to officer.name,
                "severity" to newInjury,
                "returnPlanetId" to returnPlanetId,
            ))
        }
        state.pendingInjuryEvents.clear()
    }

    // ── Player Connection Tracking (Phase 9 Plan 03) ──

    /**
     * Track player connection for priority-based hierarchy updates.
     * Called from BattleWebSocketController on connect/disconnect events.
     * Phase 14 frontend integration will wire the actual WebSocket handlers.
     */
    fun onPlayerConnected(battleId: Long, officerId: Long) {
        val state = activeBattles[battleId] ?: return
        state.connectedPlayerOfficerIds.add(officerId)
        log.debug("Player {} connected to battle {}", officerId, battleId)
    }

    fun onPlayerDisconnected(battleId: Long, officerId: Long) {
        val state = activeBattles[battleId] ?: return
        state.connectedPlayerOfficerIds.remove(officerId)
        log.debug("Player {} disconnected from battle {}", officerId, battleId)
        // D-10: priority recalculation triggered on disconnect
        // Succession queue will be re-evaluated at next hierarchy rebuild (flagship destruction, etc.)
    }

    // ── Query Methods ──

    fun getActiveBattles(sessionId: Long): List<TacticalBattleDto> {
        return tacticalBattleRepository.findBySessionIdAndPhase(sessionId, BattlePhase.ACTIVE.name)
            .map { toDto(it) }
    }

    fun getBattleState(sessionId: Long, battleId: Long): TacticalBattleDto? {
        val battle = tacticalBattleRepository.findById(battleId).orElse(null) ?: return null
        if (battle.sessionId != sessionId) return null
        return toDto(battle)
    }

    /**
     * Get battle history including persisted battle state snapshot.
     * Returns null if battle not found or session mismatch.
     */
    fun getBattleHistory(sessionId: Long, battleId: Long): TacticalBattleHistoryDto? {
        val battle = tacticalBattleRepository.findById(battleId).orElse(null) ?: return null
        if (battle.sessionId != sessionId) return null
        return toHistoryDto(battle)
    }

    // ── DTO Conversion ──

    private fun toDto(battle: TacticalBattle): TacticalBattleDto {
        val state = activeBattles[battle.id]
        @Suppress("UNCHECKED_CAST")
        val attackerIds = (battle.participants["attackers"] as? List<*>)?.mapNotNull { (it as? Number)?.toLong() } ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val defenderIds = (battle.participants["defenders"] as? List<*>)?.mapNotNull { (it as? Number)?.toLong() } ?: emptyList()

        return TacticalBattleDto(
            id = battle.id,
            sessionId = battle.sessionId,
            starSystemId = battle.starSystemId,
            attackerFactionId = battle.attackerFactionId,
            defenderFactionId = battle.defenderFactionId,
            phase = battle.phase,
            startedAt = battle.startedAt.toString(),
            endedAt = battle.endedAt?.toString(),
            result = battle.result,
            tickCount = state?.tickCount ?: battle.tickCount,
            attackerFleetIds = attackerIds,
            defenderFleetIds = defenderIds,
            units = state?.units?.map { toUnitDto(it) } ?: emptyList(),
        )
    }

    private fun toUnitDto(unit: TacticalUnit): TacticalUnitDto = TacticalUnitDto(
        fleetId = unit.fleetId,
        officerId = unit.officerId,
        officerName = unit.officerName,
        factionId = unit.factionId,
        side = unit.side.name,
        posX = unit.posX,
        posY = unit.posY,
        hp = unit.hp,
        maxHp = unit.maxHp,
        ships = unit.ships,
        maxShips = unit.maxShips,
        training = unit.training,
        morale = unit.morale,
        energy = EnergyAllocation.toMap(unit.energy),
        formation = unit.formation.name,
        commandRange = unit.commandRange.currentRange,
        isAlive = unit.isAlive,
        isRetreating = unit.isRetreating,
        retreatProgress = unit.retreatProgress,
        unitType = unit.unitType,
    )

    private fun toHistoryDto(battle: TacticalBattle): TacticalBattleHistoryDto {
        @Suppress("UNCHECKED_CAST")
        val attackerIds = (battle.participants["attackers"] as? List<*>)?.mapNotNull { (it as? Number)?.toLong() } ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val defenderIds = (battle.participants["defenders"] as? List<*>)?.mapNotNull { (it as? Number)?.toLong() } ?: emptyList()

        return TacticalBattleHistoryDto(
            id = battle.id,
            sessionId = battle.sessionId,
            starSystemId = battle.starSystemId,
            attackerFactionId = battle.attackerFactionId,
            defenderFactionId = battle.defenderFactionId,
            phase = battle.phase,
            startedAt = battle.startedAt.toString(),
            endedAt = battle.endedAt?.toString(),
            result = battle.result,
            tickCount = battle.tickCount,
            attackerFleetIds = attackerIds,
            defenderFleetIds = defenderIds,
            battleState = battle.battleState,
        )
    }

    private fun broadcastBattleState(
        sessionId: Long,
        state: TacticalBattleState,
        battle: TacticalBattle,
    ) {
        val broadcast = BattleTickBroadcast(
            battleId = battle.id,
            tickCount = state.tickCount,
            phase = battle.phase,
            currentPhase = state.currentPhase,
            units = state.units.map { toUnitDto(it) },
            events = state.tickEvents.map { BattleTickEventDto(it.type, it.sourceUnitId, it.targetUnitId, it.value, it.detail) },
            result = battle.result,
        )
        messagingTemplate.convertAndSend("/topic/world/$sessionId/tactical-battle/${battle.id}", broadcast)
    }
}

/**
 * 전술 유닛 커맨드 11종 요청 DTO.
 * command: MOVE/TURN/STRAFE/REVERSE/ATTACK/FIRE/ORBIT/FORMATION_CHANGE/REPAIR/RESUPPLY/SORTIE
 */
data class UnitCommandRequest(
    val officerId: Long,
    val command: String,
    val dirX: Double = 0.0,
    val dirY: Double = 0.0,
    val speed: Double = 1.0,
    val targetFleetId: Long? = null,
    val formation: String? = null,
)
