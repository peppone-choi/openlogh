package com.openlogh.engine.tactical

// ===== WebSocket DTO types (JSON 직렬화용, 엔진 타입과 분리) =====

/** 함대 상태 DTO (매 턴 브로드캐스트) */
data class FleetStateDto(
    val fleetId: Long,
    val officerId: Long,
    val officerName: String,
    val factionId: Long,
    val side: String,
    val units: List<UnitStateDto>,
    val morale: Int,
    val maxMorale: Int,
    val formation: String,
    val energy: Map<String, Int>,
    val isDefeated: Boolean,
)

/** 유닛 상태 DTO */
data class UnitStateDto(
    val unitId: Int,
    val shipClass: String,
    val hp: Int,
    val maxHp: Int,
    val x: Double,
    val y: Double,
    val z: Double,
    val isAlive: Boolean,
    val isFlagship: Boolean,
)

/** 전투 이벤트 DTO (WS 직렬화용 flat 구조) */
data class TacticalEventDto(
    val type: String,
    val turn: Int,
    val data: Map<String, Any?> = emptyMap(),
)

/** 전체 전투 상태 DTO (매 턴 브로드캐스트) */
data class TacticalStateDto(
    val sessionCode: String,
    val turn: Int,
    val phase: String,
    val attackerFleets: List<FleetStateDto>,
    val defenderFleets: List<FleetStateDto>,
    val grid: GridStateDto,
    val events: List<TacticalEventDto>,
    val timer: Int,
)

/** 그리드 상태 DTO */
data class GridStateDto(
    val fieldSize: Double,
    val obstacles: List<ObstacleDto>,
)

/** 장애물 DTO */
data class ObstacleDto(
    val centerX: Double,
    val centerY: Double,
    val centerZ: Double,
    val radius: Double,
    val type: String,
)

/** 전투 종료/승리 DTO */
data class TacticalVictoryDto(
    val sessionCode: String,
    val winnerFactionId: Long,
    val winnerSide: String,
    val victoryType: String,
    val description: String,
    val turns: Int,
)

// ===== WebSocket inbound messages =====

/** 전투 참가 메시지 */
data class JoinMessage(val officerId: Long = 0)

/** 턴 준비 완료 메시지 */
data class ReadyMessage(val officerId: Long = 0)

/** 초기 배치/진형/에너지 설정 메시지 */
data class SetupMessage(
    val officerId: Long = 0,
    val formation: String? = null,
    val energy: Map<String, Int>? = null,
)

/** 타이머 갱신 메시지 */
data class TimerUpdate(val sessionCode: String, val remaining: Int)

// ===== DTO 변환 확장 함수 =====

fun TacticalFleet.toDto(side: String): FleetStateDto = FleetStateDto(
    fleetId = fleetId,
    officerId = officerId,
    officerName = officer.name,
    factionId = factionId,
    side = side,
    units = units.map { it.toDto() },
    morale = morale,
    maxMorale = maxMorale,
    formation = formation.code,
    energy = mapOf(
        "beam" to energy.beam,
        "gun" to energy.gun,
        "shield" to energy.shield,
        "engine" to energy.engine,
        "sensor" to energy.sensor,
    ),
    isDefeated = isDefeated(),
)

fun TacticalUnit.toDto(): UnitStateDto = UnitStateDto(
    unitId = id,
    shipClass = shipClass.code,
    hp = hp,
    maxHp = maxHp,
    x = x,
    y = y,
    z = z,
    isAlive = isAlive(),
    isFlagship = isFlagship,
)

fun BattleEvent.toDto(): TacticalEventDto = when (this) {
    is BattleEvent.MoveEvent -> TacticalEventDto("MOVE", eventTurn, mapOf(
        "unitId" to unitId, "fleetId" to fleetId,
        "fromX" to fromX, "fromY" to fromY, "fromZ" to fromZ,
        "toX" to toX, "toY" to toY, "toZ" to toZ,
    ))
    is BattleEvent.AttackEvent -> TacticalEventDto("ATTACK", eventTurn, mapOf(
        "attackerUnitId" to attackerUnitId, "attackerFleetId" to attackerFleetId,
        "targetUnitId" to targetUnitId, "targetFleetId" to targetFleetId,
        "weaponType" to weaponType, "damage" to damage,
        "hit" to hit, "critical" to critical,
        "targetHpBefore" to targetHpBefore, "targetHpAfter" to targetHpAfter,
    ))
    is BattleEvent.DestroyEvent -> TacticalEventDto("DESTROY", eventTurn, mapOf(
        "unitId" to unitId, "fleetId" to fleetId, "destroyedBy" to destroyedBy,
    ))
    is BattleEvent.MoraleChangeEvent -> TacticalEventDto("MORALE", eventTurn, mapOf(
        "fleetId" to fleetId, "oldMorale" to oldMorale, "newMorale" to newMorale, "reason" to reason,
    ))
    is BattleEvent.FormationChangeEvent -> TacticalEventDto("FORMATION", eventTurn, mapOf(
        "fleetId" to fleetId, "oldFormation" to oldFormation.code, "newFormation" to newFormation.code,
    ))
    is BattleEvent.EnergyChangeEvent -> TacticalEventDto("ENERGY", eventTurn, mapOf(
        "fleetId" to fleetId,
    ))
    is BattleEvent.RetreatEvent -> TacticalEventDto("RETREAT", eventTurn, mapOf(
        "fleetId" to fleetId, "forced" to forced,
    ))
    is BattleEvent.SpecialEvent -> TacticalEventDto("SPECIAL", eventTurn, mapOf(
        "fleetId" to fleetId, "specialCode" to specialCode, "description" to description,
    ))
}
