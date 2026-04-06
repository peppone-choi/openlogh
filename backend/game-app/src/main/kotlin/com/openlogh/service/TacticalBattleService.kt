package com.openlogh.service

import com.openlogh.dto.*
import com.openlogh.engine.tactical.*
import com.openlogh.entity.TacticalBattle
import com.openlogh.model.BattlePhase
import com.openlogh.model.EnergyAllocation
import com.openlogh.model.Formation
import com.openlogh.repository.FleetRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.TacticalBattleRepository
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
) {
    private val log = LoggerFactory.getLogger(TacticalBattleService::class.java)
    private val engine = TacticalBattleEngine()

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
     */
    fun processSessionBattles(sessionId: Long) {
        val activeBattleIds = tacticalBattleRepository.findBySessionIdAndPhase(sessionId, BattlePhase.ACTIVE.name)
            .map { it.id }

        for (battleId in activeBattleIds) {
            processBattleTick(battleId)
        }
    }

    /**
     * Process a single tick for a battle.
     */
    fun processBattleTick(battleId: Long) {
        val state = activeBattles[battleId] ?: return
        val battle = tacticalBattleRepository.findById(battleId).orElse(null) ?: return

        // Process tick
        engine.processTick(state)

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
     * Set energy allocation for a unit in battle.
     */
    fun setEnergyAllocation(battleId: Long, officerId: Long, allocation: EnergyAllocation) {
        val state = activeBattles[battleId] ?: throw IllegalStateException("Battle $battleId not active")
        val unit = state.units.find { it.officerId == officerId && it.isAlive }
            ?: throw IllegalArgumentException("Officer $officerId not found in battle $battleId")

        unit.energy = allocation
        unit.commandRange = 0.0  // Reset command range on new order
        unit.ticksSinceLastOrder = 0

        log.debug("Officer {} set energy allocation in battle {}: B={} G={} S={} E={} W={} SE={}",
            officerId, battleId, allocation.beam, allocation.gun, allocation.shield,
            allocation.engine, allocation.warp, allocation.sensor)
    }

    /**
     * Set formation for a unit in battle.
     */
    fun setFormation(battleId: Long, officerId: Long, formation: Formation) {
        val state = activeBattles[battleId] ?: throw IllegalStateException("Battle $battleId not active")
        val unit = state.units.find { it.officerId == officerId && it.isAlive }
            ?: throw IllegalArgumentException("Officer $officerId not found in battle $battleId")

        unit.formation = formation
        unit.commandRange = 0.0
        unit.ticksSinceLastOrder = 0

        log.debug("Officer {} set formation to {} in battle {}", officerId, formation.name, battleId)
    }

    /**
     * Initiate retreat for a unit. Requires WARP energy >= 50%.
     */
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
        commandRange = unit.commandRange,
        isAlive = unit.isAlive,
        isRetreating = unit.isRetreating,
        retreatProgress = unit.retreatProgress,
        unitType = unit.unitType,
    )

    private fun broadcastBattleState(sessionId: Long, state: TacticalBattleState, battle: TacticalBattle) {
        val broadcast = BattleTickBroadcast(
            battleId = battle.id,
            tickCount = state.tickCount,
            phase = battle.phase,
            units = state.units.map { toUnitDto(it) },
            events = state.tickEvents.map { BattleTickEventDto(it.type, it.sourceUnitId, it.targetUnitId, it.value, it.detail) },
            result = battle.result,
        )
        messagingTemplate.convertAndSend("/topic/world/$sessionId/tactical-battle/${battle.id}", broadcast)
    }
}
