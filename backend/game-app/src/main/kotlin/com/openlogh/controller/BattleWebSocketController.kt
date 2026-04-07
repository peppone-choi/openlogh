package com.openlogh.controller

import com.openlogh.engine.tactical.ConquestCommand
import com.openlogh.engine.tactical.ConquestRequest
import com.openlogh.engine.tactical.TacticalCommand
import com.openlogh.model.EnergyAllocation
import com.openlogh.model.UnitStance
import com.openlogh.service.TacticalBattleService
import com.openlogh.service.UnitCommandRequest
import org.slf4j.LoggerFactory
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Controller

/**
 * WebSocket controller for per-battle energy, stance, retreat, and attack-target commands.
 *
 * Channels (gin7 스펙):
 *   발행: /app/battle/{sessionId}/{battleId}/energy       → 에너지 배분 변경
 *   발행: /app/battle/{sessionId}/{battleId}/stance       → 태세 변경
 *   발행: /app/battle/{sessionId}/{battleId}/retreat      → 퇴각 명령
 *   발행: /app/battle/{sessionId}/{battleId}/attack-target → 공격 대상 지정
 *   구독: /topic/world/{sessionId}/tactical-battle/{battleId}
 *
 * officerId는 payload에 포함 (JWT principal은 String subject — OfficerPrincipal 미구현).
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
            tacticalBattleService.enqueueCommand(battleId, TacticalCommand.SetEnergy(battleId, payload.officerId, allocation))
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
            tacticalBattleService.enqueueCommand(battleId, TacticalCommand.SetStance(battleId, payload.officerId, stance))
        } catch (e: Exception) {
            log.error("Error updating stance for battle {} officer {}: {}", battleId, payload.officerId, e.message)
        }
    }

    /**
     * 퇴각 명령.
     * Channel: /app/battle/{sessionId}/{battleId}/retreat
     *
     * officerId는 payload에 포함 (JWT principal은 String subject).
     * WARP energy >= 50% 미충족 시 IllegalArgumentException — 클라이언트에 에러 로그.
     */
    @MessageMapping("/battle/{sessionId}/{battleId}/retreat")
    fun retreat(
        @DestinationVariable sessionId: Long,
        @DestinationVariable battleId: Long,
        @Payload payload: RetreatRequest,
    ) {
        try {
            tacticalBattleService.enqueueCommand(battleId, TacticalCommand.Retreat(battleId, payload.officerId))
        } catch (e: Exception) {
            log.error("Error processing retreat for battle {} officer {}: {}", battleId, payload.officerId, e.message)
        }
    }

    /**
     * 공격 대상 지정.
     * Channel: /app/battle/{sessionId}/{battleId}/attack-target
     *
     * 지정된 targetFleetId 함대가 살아있는 한 우선 공격. 소멸 시 가장 가까운 적으로 자동 전환.
     */
    @MessageMapping("/battle/{sessionId}/{battleId}/attack-target")
    fun setAttackTarget(
        @DestinationVariable sessionId: Long,
        @DestinationVariable battleId: Long,
        @Payload payload: AttackTargetRequest,
    ) {
        try {
            tacticalBattleService.enqueueCommand(battleId, TacticalCommand.SetAttackTarget(battleId, payload.officerId, payload.targetFleetId))
        } catch (e: Exception) {
            log.error("Error setting attack target for battle {} officer {}: {}", battleId, payload.officerId, e.message)
        }
    }

    /**
     * 행성 점령 커맨드.
     * Channel: /app/battle/{sessionId}/{battleId}/planet-conquest
     *
     * command 필드: SURRENDER_DEMAND/PRECISION_BOMBING/CARPET_BOMBING/GROUND_ASSAULT/INFILTRATION/SUBVERSION
     * officerId는 payload에 포함 (JWT principal은 String subject — OfficerPrincipal 미구현).
     */
    @MessageMapping("/battle/{sessionId}/{battleId}/planet-conquest")
    fun planetConquest(
        @DestinationVariable sessionId: Long,
        @DestinationVariable battleId: Long,
        @Payload payload: PlanetConquestRequest,
    ) {
        try {
            val command = ConquestCommand.valueOf(payload.command.uppercase())
            val req = ConquestRequest(
                command = command,
                attackerOfficerId = payload.officerId,
                attackerFactionId = payload.attackerFactionId,
                attackerFactionType = payload.attackerFactionType,
                defenderFactionId = payload.defenderFactionId,
                planetId = payload.planetId,
                planetName = payload.planetName,
                planetDefense = payload.planetDefense,
                garrisonUnits = payload.garrisonUnits,
                warehouseSupplies = payload.warehouseSupplies,
                shipyardShipsInProgress = 0,
                shipyardShipsStored = 0,
                isFortress = payload.isFortress,
                defeatedOfficerIds = emptyList(),
                planetPositionCards = emptyList(),
                attackerMissileCount = payload.attackerMissileCount,
                militaryWorkPoint = payload.militaryWorkPoint,
                intelWorkPoint = payload.intelWorkPoint,
            )
            tacticalBattleService.enqueueCommand(battleId, TacticalCommand.PlanetConquest(battleId, payload.officerId, req))
        } catch (e: Exception) {
            log.error("Error processing planet conquest for battle {} officer {}: {}",
                battleId, payload.officerId, e.message)
        }
    }

    /**
     * 전술 유닛 커맨드 11종.
     * Channel: /app/battle/{sessionId}/{battleId}/unit-command
     *
     * command 필드: MOVE/TURN/STRAFE/REVERSE/ATTACK/FIRE/ORBIT/FORMATION_CHANGE/REPAIR/RESUPPLY/SORTIE
     * officerId는 payload에 포함 (JWT principal은 String subject — OfficerPrincipal 미구현).
     */
    @MessageMapping("/battle/{sessionId}/{battleId}/unit-command")
    fun unitCommand(
        @DestinationVariable sessionId: Long,
        @DestinationVariable battleId: Long,
        @Payload payload: UnitCommandRequest,
    ) {
        try {
            tacticalBattleService.enqueueCommand(battleId, TacticalCommand.UnitCommand(
                battleId = battleId,
                officerId = payload.officerId,
                command = payload.command,
                dirX = payload.dirX,
                dirY = payload.dirY,
                speed = payload.speed,
                targetFleetId = payload.targetFleetId,
                formation = payload.formation,
            ))
        } catch (e: Exception) {
            log.error("Error executing unit command {} for battle {} officer {}: {}",
                payload.command, battleId, payload.officerId, e.message)
        }
    }
    /**
     * 분함대 배정.
     * Channel: /app/battle/{sessionId}/{battleId}/assign-subfleet
     *
     * 총사령관이 분함대장에게 유닛을 배정한다 (CMD-05).
     * officerId는 payload에 포함 (JWT principal은 String subject — OfficerPrincipal 미구현).
     */
    @MessageMapping("/battle/{sessionId}/{battleId}/assign-subfleet")
    fun assignSubFleet(
        @DestinationVariable sessionId: Long,
        @DestinationVariable battleId: Long,
        @Payload payload: AssignSubFleetRequest,
    ) {
        try {
            tacticalBattleService.enqueueCommand(battleId, TacticalCommand.AssignSubFleet(
                battleId = battleId,
                officerId = payload.officerId,
                subCommanderId = payload.subCommanderId,
                unitIds = payload.unitIds,
            ))
        } catch (e: Exception) {
            log.error("Error assigning sub-fleet for battle {} officer {}: {}", battleId, payload.officerId, e.message)
        }
    }

    /**
     * 유닛 재배정.
     * Channel: /app/battle/{sessionId}/{battleId}/reassign-unit
     *
     * 총사령관이 유닛을 다른 분함대로 이동하거나 직할로 복귀시킨다 (CMD-05).
     * newSubCommanderId가 null이면 총사령관 직할로 복귀.
     */
    @MessageMapping("/battle/{sessionId}/{battleId}/reassign-unit")
    fun reassignUnit(
        @DestinationVariable sessionId: Long,
        @DestinationVariable battleId: Long,
        @Payload payload: ReassignUnitRequest,
    ) {
        try {
            tacticalBattleService.enqueueCommand(battleId, TacticalCommand.ReassignUnit(
                battleId = battleId,
                officerId = payload.officerId,
                unitId = payload.unitId,
                newSubCommanderId = payload.newSubCommanderId,
            ))
        } catch (e: Exception) {
            log.error("Error reassigning unit for battle {} officer {}: {}", battleId, payload.officerId, e.message)
        }
    }

    /**
     * 후계자 지명 (SUCC-01).
     * Channel: /app/battle/{sessionId}/{battleId}/designate-successor
     */
    @MessageMapping("/battle/{sessionId}/{battleId}/designate-successor")
    fun designateSuccessor(
        @DestinationVariable sessionId: Long,
        @DestinationVariable battleId: Long,
        @Payload payload: DesignateSuccessorRequest,
    ) {
        try {
            tacticalBattleService.enqueueCommand(battleId, TacticalCommand.DesignateSuccessor(
                battleId = battleId,
                officerId = payload.officerId,
                successorOfficerId = payload.successorOfficerId,
            ))
        } catch (e: Exception) {
            log.error("Error designating successor for battle {} officer {}: {}", battleId, payload.officerId, e.message)
        }
    }

    /**
     * 지휘권 위임 (SUCC-02).
     * Channel: /app/battle/{sessionId}/{battleId}/delegate-command
     */
    @MessageMapping("/battle/{sessionId}/{battleId}/delegate-command")
    fun delegateCommand(
        @DestinationVariable sessionId: Long,
        @DestinationVariable battleId: Long,
        @Payload payload: DelegateCommandRequest,
    ) {
        try {
            tacticalBattleService.enqueueCommand(battleId, TacticalCommand.DelegateCommand(
                battleId = battleId,
                officerId = payload.officerId,
            ))
        } catch (e: Exception) {
            log.error("Error delegating command for battle {} officer {}: {}", battleId, payload.officerId, e.message)
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

/** 퇴각 명령 요청 DTO */
data class RetreatRequest(
    val officerId: Long,
)

/** 공격 대상 지정 요청 DTO */
data class AttackTargetRequest(
    val officerId: Long,
    val targetFleetId: Long,
)

/** 행성 점령 커맨드 요청 DTO */
data class PlanetConquestRequest(
    val officerId: Long,
    /** ConquestCommand 이름 (SURRENDER_DEMAND/PRECISION_BOMBING/CARPET_BOMBING/GROUND_ASSAULT/INFILTRATION/SUBVERSION) */
    val command: String,
    val attackerFactionId: Long,
    val attackerFactionType: String,
    val defenderFactionId: Long,
    val planetId: Long,
    val planetName: String,
    val planetDefense: Int,
    val garrisonUnits: Int,
    val warehouseSupplies: Int,
    val isFortress: Boolean,
    val attackerMissileCount: Int,
    val militaryWorkPoint: Int = 0,
    val intelWorkPoint: Int = 0,
)

/** 분함대 배정 요청 DTO (CMD-05) */
data class AssignSubFleetRequest(
    val officerId: Long,         // fleet commander issuing the assignment
    val subCommanderId: Long,    // officer to lead the sub-fleet
    val unitIds: List<Long>,     // TacticalUnit.fleetId values to assign
)

/** 유닛 재배정 요청 DTO (CMD-05) */
data class ReassignUnitRequest(
    val officerId: Long,            // fleet commander
    val unitId: Long,               // unit to reassign
    val newSubCommanderId: Long?,   // null = return to fleet commander direct
)

/** 후계자 지명 요청 DTO (SUCC-01) */
data class DesignateSuccessorRequest(
    val officerId: Long,           // fleet commander
    val successorOfficerId: Long,  // officer to designate
)

/** 지휘권 위임 요청 DTO (SUCC-02) */
data class DelegateCommandRequest(
    val officerId: Long,  // commander delegating
)
