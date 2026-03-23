package com.openlogh.dto

import com.openlogh.entity.Fleet

data class CreateFleetRequest(
    val sessionId: Long,
    val leaderOfficerId: Long,
    val factionId: Long,
    val name: String,
    val fleetType: String = "fleet",
)

data class FleetActionRequest(val officerId: Long)

data class RenameFleetRequest(val name: String)

data class FleetMemberInfo(val id: Long, val name: String, val picture: String, val rank: Short)

data class FleetWithMembers(val fleet: FleetResponse, val members: List<FleetMemberInfo>)

data class FleetResponse(
    val id: Long,
    val sessionId: Long,
    val leaderOfficerId: Long,
    val factionId: Long,
    val name: String,
    val fleetType: String,
    val planetId: Long?,
    val gridX: Int?,
    val gridY: Int?,

    // 기함
    val flagshipCode: String,

    // 전투 함선
    val battleships: Int,
    val cruisers: Int,
    val destroyers: Int,
    val carriers: Int,

    // 지상 부대
    val groundTroops: Int,
    val assaultShips: Int,

    // 지원 부대
    val transports: Int,
    val hospitalShips: Int,

    // 함대 상태
    val morale: Short,
    val training: Short,
    val supplies: Int,
    val formation: String,
    val fleetState: Short,

    // 에너지 분배
    val energyBeam: Short,
    val energyGun: Short,
    val energyShield: Short,
    val energyEngine: Short,
    val energySensor: Short,

    // 계산값
    val totalCombatShips: Int,
    val totalShips: Int,
    val combatPower: Int,
) {
    companion object {
        fun from(fleet: Fleet) = FleetResponse(
            id = fleet.id,
            sessionId = fleet.sessionId,
            leaderOfficerId = fleet.leaderOfficerId,
            factionId = fleet.factionId,
            name = fleet.name,
            fleetType = fleet.fleetType,
            planetId = fleet.planetId,
            gridX = fleet.gridX,
            gridY = fleet.gridY,
            flagshipCode = fleet.flagshipCode,
            battleships = fleet.battleships,
            cruisers = fleet.cruisers,
            destroyers = fleet.destroyers,
            carriers = fleet.carriers,
            groundTroops = fleet.groundTroops,
            assaultShips = fleet.assaultShips,
            transports = fleet.transports,
            hospitalShips = fleet.hospitalShips,
            morale = fleet.morale,
            training = fleet.training,
            supplies = fleet.supplies,
            formation = fleet.formation,
            fleetState = fleet.fleetState,
            energyBeam = fleet.energyBeam,
            energyGun = fleet.energyGun,
            energyShield = fleet.energyShield,
            energyEngine = fleet.energyEngine,
            energySensor = fleet.energySensor,
            totalCombatShips = fleet.totalCombatShips(),
            totalShips = fleet.totalShips(),
            combatPower = fleet.combatPower(),
        )
    }
}

data class FleetCompositionRequest(
    val battleships: Int?,
    val cruisers: Int?,
    val destroyers: Int?,
    val carriers: Int?,
    val groundTroops: Int?,
    val assaultShips: Int?,
    val transports: Int?,
    val hospitalShips: Int?,
)

data class EnergyAllocationRequest(
    val beam: Short,
    val gun: Short,
    val shield: Short,
    val engine: Short,
    val sensor: Short,
)

data class FormationChangeRequest(
    val formation: String,
)
