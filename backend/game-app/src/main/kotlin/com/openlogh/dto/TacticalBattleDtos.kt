package com.openlogh.dto

import com.openlogh.model.EnergyAllocation
import com.openlogh.model.Formation

// ── Request DTOs ──

data class BattleCommandRequest(
    val battleId: Long,
    val officerId: Long,
    val commandType: String,  // "energy", "formation", "retreat"
    val energy: Map<String, Int>? = null,
    val formation: String? = null,
)

// ── Response DTOs ──

/**
 * Phase 14 D-21: Sub-fleet projection of [com.openlogh.engine.tactical.SubFleet].
 *
 * Frontend uses this to render분함대 배정 드로어 (FE-02) and to derive
 * "본인의 지휘권 하 유닛입니다" badges (FE-03 / D-11).
 *
 * Field naming: `commanderOfficerId` / `memberFleetIds` are kept as the DTO
 * contract names for frontend stability even though the engine model uses
 * `commanderId` / `unitIds` internally — see [CommandHierarchyDto.fromEngine].
 */
data class SubFleetDto(
    val commanderOfficerId: Long,
    val commanderName: String,
    val memberFleetIds: List<Long>,
    val commanderRank: Int,
)

/**
 * Phase 14 D-21: Frontend-facing projection of
 * [com.openlogh.engine.tactical.CommandHierarchy].
 *
 * Carries everything the FE needs to compute:
 *   - FE-03 UI gating ("는 내 지휘 체인에 있는가")
 *   - FE-04 승계 피드백 (vacancyStartTick + 30틱 카운트다운)
 *   - FE-05 안개 공유 (같은 지휘 네트워크 내 색적 공유)
 *   - CRC 다중 렌더 (본인 + 하위 분함대장)
 */
data class CommandHierarchyDto(
    /** Fleet commander officer ID (원수 / 사령관) */
    val fleetCommander: Long,
    /** Sub-fleet commanders and their assigned fleet IDs. */
    val subFleets: List<SubFleetDto>,
    /** Ordered officer IDs for succession (rank desc). */
    val successionQueue: List<Long>,
    /** Pre-designated successor officer ID (SUCC-01). */
    val designatedSuccessor: Long? = null,
    /** Tick when command vacancy started (-1 = no vacancy, SUCC-03). */
    val vacancyStartTick: Int = -1,
    /** Communication jamming active flag. */
    val commJammed: Boolean = false,
    /** Remaining ticks of jamming. */
    val jammingTicksRemaining: Int = 0,
    /** Current active commander after delegation / succession (null = original fleetCommander). */
    val activeCommander: Long? = null,
) {
    companion object {
        /**
         * Phase 14 D-21 / D-37: Map engine [CommandHierarchy] to frontend DTO.
         *
         * The engine `SubFleet` stores `commanderId` / `unitIds` and already
         * carries the display name. We just rename fields to the frontend
         * contract (`commanderOfficerId` / `memberFleetIds`).
         *
         * `officerNameFallback` is only consulted when the engine SubFleet's
         * `commanderName` is blank — normally that never happens because
         * BattleTriggerService sets it at battle init.
         */
        @JvmStatic
        fun fromEngine(
            h: com.openlogh.engine.tactical.CommandHierarchy,
            officerNameFallback: (Long) -> String = { "" },
        ): CommandHierarchyDto = CommandHierarchyDto(
            fleetCommander = h.fleetCommander,
            subFleets = h.subCommanders.values.map { sf ->
                SubFleetDto(
                    commanderOfficerId = sf.commanderId,
                    commanderName = sf.commanderName.ifBlank { officerNameFallback(sf.commanderId) },
                    memberFleetIds = sf.unitIds.toList(),
                    commanderRank = sf.commanderRank,
                )
            },
            successionQueue = h.successionQueue.toList(),
            designatedSuccessor = h.designatedSuccessor,
            vacancyStartTick = h.vacancyStartTick,
            commJammed = h.commJammed,
            jammingTicksRemaining = h.jammingTicksRemaining,
            activeCommander = h.activeCommander,
        )
    }
}

data class TacticalBattleDto(
    val id: Long,
    val sessionId: Long,
    val starSystemId: Long,
    val attackerFactionId: Long,
    val defenderFactionId: Long,
    val phase: String,
    val startedAt: String,
    val endedAt: String? = null,
    val result: String? = null,
    val tickCount: Int,
    val attackerFleetIds: List<Long>,
    val defenderFleetIds: List<Long>,
    val units: List<TacticalUnitDto>,
    // ── Phase 14 D-21 ──
    val attackerHierarchy: CommandHierarchyDto? = null,
    val defenderHierarchy: CommandHierarchyDto? = null,
)

data class TacticalUnitDto(
    val fleetId: Long,
    val officerId: Long,
    val officerName: String,
    val factionId: Long,
    val side: String,
    val posX: Double,
    val posY: Double,
    val hp: Int,
    val maxHp: Int,
    val ships: Int,
    val maxShips: Int,
    val training: Int,
    val morale: Int,
    val energy: Map<String, Int>,
    val formation: String,
    /** Current command communication range (expanding toward maxCommandRange). */
    val commandRange: Double,
    val isAlive: Boolean,
    val isRetreating: Boolean,
    val retreatProgress: Double,
    val unitType: String,
    // ── Phase 14 D-22 / D-24 / D-37 ──
    /** D-19: Separate sensor range (derived from SENSOR energy) — distinct from commandRange. */
    val sensorRange: Double = 0.0,
    /** D-22: Owning sub-fleet commander officerId (null = 사령관 직할). */
    val subFleetCommanderId: Long? = null,
    /** D-22: "PENDING_SUCCESSION" while vacancy countdown runs, else null. */
    val successionState: String? = null,
    /** D-22: Ticks left before successor auto-assigned (max 30, SUCC-03). */
    val successionTicksRemaining: Int? = null,
    /** D-22: Whether the officer currently has an active WebSocket session. */
    val isOnline: Boolean = true,
    /** D-22: Whether this unit is NPC-controlled (Officer.npcState != 0). */
    val isNpc: Boolean = false,
    /** D-37: Current mission objective string (CONQUEST/DEFENSE/SWEEP/…) or null. */
    val missionObjective: String? = null,
    /**
     * FE-01 / D-24: Maximum CRC radius (outer dashed stroke on the tactical map).
     * Distinct from `commandRange` which is the current expanding radius.
     */
    val maxCommandRange: Double = 0.0,
)

data class BattleTickBroadcast(
    val battleId: Long,
    val tickCount: Int,
    val phase: String,
    val currentPhase: String = "MOVEMENT",
    val units: List<TacticalUnitDto>,
    val events: List<BattleTickEventDto>,
    val result: String? = null,
    // ── Phase 14 D-21: per-tick hierarchy propagation ──
    val attackerHierarchy: CommandHierarchyDto? = null,
    val defenderHierarchy: CommandHierarchyDto? = null,
)

/**
 * Per-tick transient event emitted by the tactical battle engine.
 *
 * Phase 14 D-23 adds the following `type` values (no structural change — just
 * string conventions the frontend dispatches on):
 *  - `FLAGSHIP_DESTROYED` — sourceUnitId = affected fleetId
 *  - `SUCCESSION_STARTED` — sourceUnitId = affected fleetId, value = ticksRemaining
 *  - `SUCCESSION_COMPLETED` — sourceUnitId = new commander fleetId, targetUnitId = old commander fleetId
 *  - `JAMMING_ACTIVE` — sourceUnitId = jammer fleetId, value = ticksRemaining
 *
 * Persistent state (succession countdown, jamming flag) lives on
 * [CommandHierarchyDto] / [TacticalUnitDto] — these events only drive one-shot
 * UI effects (토스트 / 플래시 / 사운드).
 */
data class BattleTickEventDto(
    val type: String,
    val sourceUnitId: Long = 0,
    val targetUnitId: Long = 0,
    val value: Int = 0,
    val detail: String = "",
)

data class ActiveBattlesResponse(
    val battles: List<TacticalBattleDto>,
)

/**
 * Battle history DTO for completed battles.
 * Includes result, tick count, and participating unit summaries.
 */
data class TacticalBattleHistoryDto(
    val id: Long,
    val sessionId: Long,
    val starSystemId: Long,
    val attackerFactionId: Long,
    val defenderFactionId: Long,
    val phase: String,
    val startedAt: String,
    val endedAt: String? = null,
    val result: String? = null,
    val tickCount: Int,
    val attackerFleetIds: List<Long>,
    val defenderFleetIds: List<Long>,
    val battleState: Map<String, Any>,
)

// ── Phase 14 Plan 14-02: Battle Summary DTOs ──
// End-of-battle modal (D-32..D-34) renders per-unit merit breakdown
// "기본 X + 작전 +Y = 총 Z" so Phase 12 OPS-02 (×1.5 operation
// multiplier) is visually verifiable. Breakdown is computed from the
// persisted TacticalBattle.battleState JSONB snapshot at read-time — no
// new DB column: unit snapshots live under the existing `battleState` map
// keys (`unitSnapshots`, `operationParticipantFleetIds`) captured during
// TacticalBattleService.endBattle().

/**
 * Single row of a battle summary, one per participating fleet.
 *
 * @param baseMerit Phase 12 D-16 heuristic: winning side = (100 * ships/maxShips).coerceAtLeast(10); losing/draw side = 0.
 * @param operationMultiplier 1.5 iff this fleet was an active operation participant AND baseMerit > 0, else 1.0.
 * @param totalMerit (baseMerit * operationMultiplier).toInt() — the exact value credited to Officer.meritPoints by endBattle.
 */
data class BattleSummaryRow(
    val fleetId: Long,
    val officerId: Long,
    val officerName: String,
    val side: String,                   // "ATTACKER" | "DEFENDER"
    val survivingShips: Int,
    val maxShips: Int,
    val baseMerit: Int,
    val operationMultiplier: Double,
    val totalMerit: Int,
    val isOperationParticipant: Boolean,
)

/**
 * Per-battle merit breakdown response for the end-of-battle modal (D-32..D-34).
 *
 * @param winner lowercase result string matching TacticalBattle.result:
 *               "attacker_win" | "defender_win" | "draw" | null (ongoing).
 */
data class BattleSummaryDto(
    val battleId: Long,
    val winner: String?,
    val durationTicks: Int,
    val rows: List<BattleSummaryRow>,
)
