package com.openlogh.service

import com.openlogh.dto.*
import com.openlogh.engine.tactical.*
import com.openlogh.engine.tactical.ai.MissionObjective
import com.openlogh.entity.OperationPlan
import com.openlogh.entity.TacticalBattle
import com.openlogh.model.BattlePhase
import com.openlogh.model.EnergyAllocation
import com.openlogh.model.Formation
import com.openlogh.model.InjuryEvent
import com.openlogh.model.OperationStatus
import com.openlogh.model.UnitStance
import com.openlogh.repository.DiplomacyRepository
import com.openlogh.repository.FactionRepository
import com.openlogh.repository.FleetRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.PlanetRepository
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
    private val factionRepository: FactionRepository,
    private val planetRepository: PlanetRepository,
    private val diplomacyRepository: DiplomacyRepository,
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
     * Phase 12 D-08: Direct-call sync channel. Invoked by OperationPlanCommand /
     * OperationCancelCommand / OperationLifecycleService whenever an OperationPlan
     * is created, updated, cancelled, or transitions state. Updates every
     * currently-active TacticalBattleState in place so in-flight battles see the
     * new mission objective (or removal on CANCELLED) without needing a DB
     * round-trip from the tick loop.
     *
     * Thread safety: both TacticalBattleState.missionObjectiveByFleetId and
     * operationParticipantFleetIds are ConcurrentHashMap-backed — the tick loop
     * reads at Step 0.6 while this method writes from command / scheduler threads.
     *
     * COMPLETED intentionally leaves the map untouched: a completed operation
     * only affects future battles and merit credit filtering; current battles
     * continue to respect the objective until they end naturally.
     */
    fun syncOperationToActiveBattles(operation: OperationPlan) {
        for ((_, state) in activeBattles) {
            when (operation.status) {
                OperationStatus.CANCELLED -> {
                    for (fleetId in operation.participantFleetIds) {
                        state.missionObjectiveByFleetId.remove(fleetId)
                        state.operationParticipantFleetIds.remove(fleetId)
                    }
                }
                OperationStatus.PENDING, OperationStatus.ACTIVE -> {
                    for (fleetId in operation.participantFleetIds) {
                        state.missionObjectiveByFleetId[fleetId] = operation.objective
                        state.operationParticipantFleetIds.add(fleetId)
                    }
                }
                OperationStatus.COMPLETED -> {
                    // Leave the mission in place until the battle ends naturally;
                    // COMPLETED only affects future battles and merit credit filtering.
                }
            }
        }
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

        // Phase 24-14 (gap C20, gin7 manual p50):
        // "職務権限カードの喪失 — 敗北した陣営のキャラクターは、管轄していた惑星/要塞固有の
        //  職務権限カード(惑星総督や惑星守備隊指揮官、封土カード等)が直ちに失われます."
        //
        // PlanetCaptureProcessor returns the list of cards that should be
        // stripped from each defeated officer; here we actually apply that
        // mutation to the Officer rows so downstream systems (rank ladder,
        // mail routing, command authority) see the new state.
        val captureResult = result.captureResult
        if (result.success && captureResult != null) {
            val removedCards = captureResult.removedPositionCards.toSet()
            if (removedCards.isNotEmpty() && request.defeatedOfficerIds.isNotEmpty()) {
                for (defeatedOfficerId in request.defeatedOfficerIds) {
                    val officer = officerRepository.findById(defeatedOfficerId).orElse(null)
                        ?: continue
                    val changed = officer.positionCards.removeAll(removedCards)
                    if (changed) {
                        officerRepository.save(officer)
                        log.info(
                            "Officer {} lost {} position cards on planet {} capture: {}",
                            officer.id, removedCards.size, request.planetId, removedCards
                        )
                    }
                }
            }

            // Phase 24-16 (gap A4, gin7 manual p40): 페잔 자치령 점령 페널티 적용.
            //
            // 페잔 통상망 붕괴로 가해 진영이 입는 실효 타격:
            //   1. 모든 행성 민심 -10 (자본 이탈 / 페잔 교역 중단)
            //   2. 진영 tech_level -0.5 (기술 교류 단절)
            //   3. 진영 military_power × 0.95 (페잔 경유 물자·기술 수입 중단)
            //   4. 해당 진영이 체결한 불가침 조약 전부 파기 (타 진영 반발)
            //   5. faction.meta["neutralityViolations"] 카운터 누적
            captureResult.neutralityViolation?.let { penalty ->
                applyFezzanNeutralityPenalty(penalty)
            }
        }

        state.tickEvents.add(BattleTickEvent("conquest", sourceUnitId = unit.fleetId,
            detail = "${request.command.displayNameKo}: ${result.reason}"))

        log.info("Conquest {} by officer {}: {}", request.command, officerId, result.reason)
        return result
    }

    /**
     * Phase 24-16 (gap A4, gin7 manual p40): 페잔 자치령 점령 페널티 DB 적용.
     *
     * 엔진(PlanetCaptureProcessor)이 중립 위반을 감지하면 [NeutralityViolationPenalty]
     * 객체만 반환한다. 실제 진영/행성/외교 행 갱신은 서비스 계층인 여기에서 수행한다.
     *
     * 적용 효과:
     *   - 위반 진영의 모든 행성: approval -= approvalPenalty
     *   - 위반 진영의 tech_level -= techLevelPenalty (0 이하로 내려가지 않음)
     *   - 위반 진영의 military_power *= militaryPowerMultiplier
     *   - faction.meta["neutralityViolations"] 카운터 += 1
     *   - 해당 진영이 체결한 활성 불가침 조약 전부 파기 (상대 진영 반발 시뮬레이션)
     */
    private fun applyFezzanNeutralityPenalty(penalty: NeutralityViolationPenalty) {
        val violator = factionRepository.findById(penalty.violatorFactionId).orElse(null)
            ?: run {
                log.warn("Fezzan neutrality penalty target faction {} not found", penalty.violatorFactionId)
                return
            }

        // 1. 진영 전체 행성 지지율 하락 — 자본 이탈 / 페잔 교역 중단 효과.
        val planets = planetRepository.findBySessionIdAndFactionId(violator.sessionId, violator.id)
        for (planet in planets) {
            planet.approval = (planet.approval - penalty.approvalPenalty.toFloat()).coerceAtLeast(0f)
        }
        if (planets.isNotEmpty()) planetRepository.saveAll(planets)

        // 2. 기술 교류 단절 / 군사력 저하.
        violator.techLevel = (violator.techLevel - penalty.techLevelPenalty).coerceAtLeast(0f)
        violator.militaryPower =
            (violator.militaryPower * penalty.militaryPowerMultiplier).toInt().coerceAtLeast(0)

        // 3. 중립 위반 이력 누적 (후속 페널티 스케일링 포인트).
        val prior = (violator.meta["neutralityViolations"] as? Number)?.toInt() ?: 0
        violator.meta["neutralityViolations"] = prior + 1

        factionRepository.save(violator)

        // 4. 활성 불가침 조약 파기 — 페잔 점령 소식에 상대 진영이 반발.
        if (penalty.breakNonAggressionPacts) {
            val relations = diplomacyRepository.findAll()
                .filter { !it.isDead && it.sessionId == violator.sessionId }
                .filter { it.srcFactionId == violator.id || it.destFactionId == violator.id }
                .filter { it.stateCode == "불가침" }
            for (rel in relations) {
                rel.isDead = true
            }
            if (relations.isNotEmpty()) diplomacyRepository.saveAll(relations)
            log.info(
                "Fezzan neutrality violation by faction {}: broke {} non-aggression pacts",
                violator.id, relations.size
            )
        }

        log.info(
            "Fezzan neutrality penalty applied to faction {}: approval -{} on {} planets, " +
                "techLevel -{}, militaryPower *{}, violation count now {}",
            violator.id, penalty.approvalPenalty, planets.size,
            penalty.techLevelPenalty, penalty.militaryPowerMultiplier, prior + 1
        )
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

        // Phase 12 Blocker 4 guard (D-11/D-14): snapshot the participant set
        // BEFORE the unit loop. A concurrent syncOperationToActiveBattles(CANCELLED)
        // arriving between iterations must not produce non-deterministic bonus
        // assignment. The snapshot captures the authoritative membership at the
        // moment endBattle started.
        val participantSnapshot: Set<Long> = state.operationParticipantFleetIds.toSet()

        // Phase 14 Plan 14-02 D-33: persist a per-unit snapshot into the existing
        // battleState JSONB map so buildBattleSummary() can reconstruct the merit
        // breakdown (기본 X + 작전 +Y = 총 Z) for the end-of-battle modal WITHOUT
        // a new DB column / Flyway migration. Captured BEFORE the officer-update
        // loop so the values mirror exactly what computeBaseMerit() sees.
        val unitSnapshotList: List<Map<String, Any>> = state.units.map { u ->
            mapOf(
                "fleetId" to u.fleetId,
                "officerId" to u.officerId,
                "officerName" to u.officerName,
                "side" to u.side.name,
                "survivingShips" to u.ships.coerceAtLeast(0),
                "maxShips" to u.maxShips.coerceAtLeast(1),
            )
        }
        battle.battleState["unitSnapshots"] = unitSnapshotList
        battle.battleState["operationParticipantFleetIds"] = participantSnapshot.toList()

        // Update fleet ships based on battle results
        for (unit in state.units) {
            val fleet = fleetRepository.findById(unit.fleetId).orElse(null) ?: continue
            val officer = officerRepository.findById(unit.officerId).orElse(null) ?: continue

            // Update officer ships to surviving count
            officer.ships = unit.ships.coerceAtLeast(0)
            if (unit.morale < officer.morale.toInt()) {
                officer.morale = unit.morale.toShort()
            }

            // Phase 12 D-11/D-12/D-14: Merit bonus for operation participants.
            // FIRST merit-from-battle accumulation path in the codebase
            // (RankLadderService.kt:124/143 are RESETS, not accumulations).
            //
            // Base merit: proportional to ship survival on the winning side
            // (0 for losers). Multiplier: ×1.5 iff this unit's fleetId was in
            // the snapshot taken above.
            val baseMerit = computeBaseMerit(unit, outcome)
            if (baseMerit > 0) {
                val isOperationParticipant = participantSnapshot.contains(unit.fleetId)
                val multiplier = if (isOperationParticipant) 1.5 else 1.0
                val awarded = (baseMerit * multiplier).toInt()
                officer.meritPoints += awarded
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
            // Phase 24-19 (gap C13, gin7 manual p49): 空戰命令 — SORTIE는 gin7 원문의
            // "戦闘艇 空戰命令"에 해당하며, 対艦戦 vs 迎撃戦 판정은 이미
            // MissileWeaponSystem.processFighterAttack(대상이 CARRIER인지)에서 자동
            // 수행된다. 매뉴얼 친화적 명칭 AIR_COMBAT 을 alias 로 추가하여
            // frontend / 외부 클라이언트가 gin7 표기로 호출할 수 있게 한다.
            "SORTIE", "AIR_COMBAT" -> {
                // 전투정 발진: 가장 가까운 적에게 스파르타니안 발진.
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

            // 귀환성 결정 (Phase 24-07, gin7 manual p51):
            //   officer.returnPlanetId → faction.capitalPlanetId → currentPlanetId
            val factionCapital = factionRepository.findById(officer.factionId)
                .orElse(null)?.capitalPlanetId
            val returnPlanetId = InjuryEvent.resolveReturnPlanet(
                configuredReturnPlanetId = officer.returnPlanetId,
                factionCapitalPlanetId = factionCapital,
                currentPlanetId = officer.planetId,
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

        val nameLookup: (Long) -> String = { id -> officerNameFallback(id) }

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
            units = state?.units?.map { toUnitDto(it, state) } ?: emptyList(),
            // Phase 14 D-21: expose hierarchy to the frontend.
            attackerHierarchy = state?.attackerHierarchy?.let { CommandHierarchyDto.fromEngine(it, nameLookup) },
            defenderHierarchy = state?.defenderHierarchy?.let { CommandHierarchyDto.fromEngine(it, nameLookup) },
        )
    }

    /**
     * Phase 14 D-21 helper: resolve officer display name for SubFleetDto fallback.
     * Normally the engine SubFleet already carries `commanderName` — this lookup
     * only fires when the field is blank (defensive fallback).
     */
    private fun officerNameFallback(officerId: Long): String =
        officerRepository.findById(officerId).orElse(null)?.name ?: ""

    /**
     * Phase 14 D-22 / D-37: derive per-unit hierarchy-dependent fields from
     * the live [TacticalBattleState]. Called by [toDto] and [broadcastBattleState]
     * once per unit per tick.
     */
    private fun toUnitDto(
        unit: TacticalUnit,
        state: com.openlogh.engine.tactical.TacticalBattleState,
    ): TacticalUnitDto {
        // Pick the hierarchy for this unit's side.
        val hierarchy = when (unit.side) {
            com.openlogh.engine.tactical.BattleSide.ATTACKER -> state.attackerHierarchy
            com.openlogh.engine.tactical.BattleSide.DEFENDER -> state.defenderHierarchy
        }

        // D-22: Which sub-fleet (if any) does this unit belong to?
        // Prefer the engine field (populated by CommandHierarchyService) with a
        // fallback scan of subCommanders for robustness during reassignment races.
        val subFleetCommanderId: Long? = unit.subFleetCommanderId
            ?: hierarchy?.subCommanders?.values
                ?.firstOrNull { sf -> sf.unitIds.contains(unit.fleetId) }
                ?.commanderId

        // D-22 / SUCC-03: 30-tick succession countdown surfaced per-unit.
        // The vacancy flag lives on the hierarchy (keyed to fleetCommander's
        // officerId), so every unit whose officerId == fleetCommander during a
        // vacancy gets the pending state.
        val isVacantCommander = hierarchy != null
            && hierarchy.vacancyStartTick >= 0
            && unit.officerId == hierarchy.fleetCommander
        val successionState: String? = if (isVacantCommander) "PENDING_SUCCESSION" else null
        val successionTicksRemaining: Int? = if (isVacantCommander) {
            val elapsed = state.currentTick - hierarchy!!.vacancyStartTick
            (SUCCESSION_VACANCY_TICKS - elapsed).coerceAtLeast(0)
        } else null

        // D-22: online = has an active WebSocket session.
        // Default to true for officers that were never tracked (backwards compat
        // before the ws handler lands in 14-08).
        val isOnline = unit.officerId in state.connectedPlayerOfficerIds
            || state.connectedPlayerOfficerIds.isEmpty()

        // D-22 / D-35: isNpc from the state-level NPC set (populated at battle init).
        val isNpc = unit.officerId in state.npcOfficerIds

        // D-37: mission objective string (CONQUEST/DEFENSE/SWEEP/…).
        val missionObjective = state.missionObjectiveByFleetId[unit.fleetId]?.name

        // D-19 / FE-05: sensorRange is cached on TacticalUnit by the engine
        // tick loop (SensorRangeFormula, Phase 14 Plan 03). Reading the field
        // here means the DTO builder never re-derives the formula — the
        // engine is the single source of truth per D-19.

        return TacticalUnitDto(
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
            // Phase 14 D-22 / D-24 / D-37
            sensorRange = unit.sensorRange,
            subFleetCommanderId = subFleetCommanderId,
            successionState = successionState,
            successionTicksRemaining = successionTicksRemaining,
            isOnline = isOnline,
            isNpc = isNpc,
            missionObjective = missionObjective,
            maxCommandRange = unit.commandRange.maxRange,
        )
    }

    private companion object {
        /** SUCC-03 (Phase 10): 30-tick command vacancy countdown. */
        const val SUCCESSION_VACANCY_TICKS = 30
    }

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
        val nameLookup: (Long) -> String = { id -> officerNameFallback(id) }
        val broadcast = BattleTickBroadcast(
            battleId = battle.id,
            tickCount = state.tickCount,
            phase = battle.phase,
            currentPhase = state.currentPhase,
            units = state.units.map { toUnitDto(it, state) },
            events = state.tickEvents.map { BattleTickEventDto(it.type, it.sourceUnitId, it.targetUnitId, it.value, it.detail) },
            result = battle.result,
            // Phase 14 D-21: per-tick hierarchy propagation.
            attackerHierarchy = state.attackerHierarchy?.let { CommandHierarchyDto.fromEngine(it, nameLookup) },
            defenderHierarchy = state.defenderHierarchy?.let { CommandHierarchyDto.fromEngine(it, nameLookup) },
        )
        messagingTemplate.convertAndSend("/topic/world/$sessionId/tactical-battle/${battle.id}", broadcast)
    }

    // ── Phase 14 Plan 14-02: Battle Summary (D-32..D-34) ──

    /**
     * Phase 14 Plan 14-02: reconstruct the per-unit merit breakdown for the
     * end-of-battle modal (D-32..D-34). Uses the persistent `battleState` JSONB
     * snapshot captured by [endBattle] — no new DB column / Flyway migration.
     *
     * Algorithm mirrors [endBattle] exactly so the UI shows what was actually
     * credited to Officer.meritPoints:
     *   - `baseMerit` via the Phase 12 [computeBaseMerit] heuristic
     *     (winning side = (100 * ships/maxShips).coerceAtLeast(10); else 0)
     *   - `operationMultiplier` = 1.5 iff the fleetId was in the participant
     *     snapshot AND baseMerit > 0, else 1.0
     *   - `totalMerit` = (baseMerit * multiplier).toInt()
     *
     * @throws NoSuchElementException if the battle does not exist (→ 404)
     * @throws IllegalStateException  if phase != ENDED (→ 409)
     * @throws IllegalArgumentException if sessionId does not match (treated as
     *   not-found by the controller, mirroring [getBattleState]/[getBattleHistory])
     */
    fun buildBattleSummary(sessionId: Long, battleId: Long): com.openlogh.dto.BattleSummaryDto {
        val battle = tacticalBattleRepository.findById(battleId).orElseThrow {
            NoSuchElementException("Battle $battleId not found")
        }
        require(battle.sessionId == sessionId) { "Battle $battleId not in session $sessionId" }
        check(battle.phase == BattlePhase.ENDED.name) { "전투가 아직 종료되지 않았습니다" }

        @Suppress("UNCHECKED_CAST")
        val rawSnapshots = (battle.battleState["unitSnapshots"] as? List<Map<String, Any>>).orEmpty()
        @Suppress("UNCHECKED_CAST")
        val rawParticipants = (battle.battleState["operationParticipantFleetIds"] as? List<*>)
            ?.mapNotNull { (it as? Number)?.toLong() }
            ?.toSet()
            ?: emptySet()

        // Map TacticalBattle.result ("attacker_win" / "defender_win" / "draw")
        // back to a BattleSide for computeBaseMerit-parity scoring.
        val winningSide: BattleSide? = when (battle.result) {
            "attacker_win" -> BattleSide.ATTACKER
            "defender_win" -> BattleSide.DEFENDER
            else -> null
        }

        val rows = rawSnapshots.map { snap ->
            val fleetId = (snap["fleetId"] as? Number)?.toLong() ?: 0L
            val officerId = (snap["officerId"] as? Number)?.toLong() ?: 0L
            val officerName = snap["officerName"] as? String ?: "?"
            val sideStr = snap["side"] as? String ?: "ATTACKER"
            val survivingShips = (snap["survivingShips"] as? Number)?.toInt() ?: 0
            val maxShips = ((snap["maxShips"] as? Number)?.toInt() ?: 1).coerceAtLeast(1)

            val onWinningSide = (winningSide != null) && (sideStr == winningSide.name)
            val baseMerit = if (onWinningSide) {
                ((100.0 * survivingShips / maxShips).toInt()).coerceAtLeast(10)
            } else 0

            val isParticipant = rawParticipants.contains(fleetId)
            val multiplier = if (isParticipant && baseMerit > 0) 1.5 else 1.0
            val totalMerit = (baseMerit * multiplier).toInt()

            com.openlogh.dto.BattleSummaryRow(
                fleetId = fleetId,
                officerId = officerId,
                officerName = officerName,
                side = sideStr,
                survivingShips = survivingShips,
                maxShips = maxShips,
                baseMerit = baseMerit,
                operationMultiplier = multiplier,
                totalMerit = totalMerit,
                isOperationParticipant = isParticipant,
            )
        }

        return com.openlogh.dto.BattleSummaryDto(
            battleId = battle.id,
            winner = battle.result,
            durationTicks = battle.tickCount,
            rows = rows,
        )
    }

    /**
     * Phase 12 D-11: base merit computation for endBattle.
     *
     * Heuristic for the first Phase 12 iteration:
     *   - 0 merit for losing / drawing sides
     *   - baseMerit = (100 * survivalRatio).toInt().coerceAtLeast(10) on winning side
     *
     * Full-ship survival on the winning side yields exactly 100 base merit
     * (OperationMeritBonusTest fixtures depend on this exact value to verify
     * the ×1.5 multiplier applies to baseMerit, not `> 0`).
     */
    private fun computeBaseMerit(unit: TacticalUnit, outcome: BattleOutcome): Int {
        val winningSide = outcome.winner ?: return 0
        if (unit.side != winningSide) return 0
        val maxShips = unit.maxShips.coerceAtLeast(1)
        val survivalRatio = unit.ships.toDouble() / maxShips.toDouble()
        return (100.0 * survivalRatio).toInt().coerceAtLeast(10)
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
