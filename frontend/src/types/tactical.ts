/** Energy allocation channels for tactical combat */
export interface EnergyAllocation {
    beam: number;
    gun: number;
    shield: number;
    engine: number;
    warp: number;
    sensor: number;
}

/** Formation types */
export type Formation = 'WEDGE' | 'BY_CLASS' | 'MIXED' | 'THREE_COLUMN';

/** Formation info with display data */
export interface FormationInfo {
    type: Formation;
    nameKo: string;
    description: string;
    attackMod: number;
    defenseMod: number;
    speedMod: number;
}

export const FORMATIONS: FormationInfo[] = [
    {
        type: 'WEDGE',
        nameKo: '방추진형',
        description: '공격 +30%, 방어 -30%, 속도 +10%',
        attackMod: 1.3,
        defenseMod: 0.7,
        speedMod: 1.1,
    },
    {
        type: 'BY_CLASS',
        nameKo: '함종별진형',
        description: '공격 +10%, 방어 +10%',
        attackMod: 1.1,
        defenseMod: 1.1,
        speedMod: 1.0,
    },
    {
        type: 'MIXED',
        nameKo: '혼성진형',
        description: '균형잡힌 능력치',
        attackMod: 1.0,
        defenseMod: 1.0,
        speedMod: 1.0,
    },
    {
        type: 'THREE_COLUMN',
        nameKo: '삼열종심진형',
        description: '공격 -20%, 방어 +40%, 속도 -10%',
        attackMod: 0.8,
        defenseMod: 1.4,
        speedMod: 0.9,
    },
];

/** Battle lifecycle phase */
export type BattlePhase = 'PREPARING' | 'ACTIVE' | 'PAUSED' | 'ENDED';

/** Battle side */
export type BattleSide = 'ATTACKER' | 'DEFENDER';

/**
 * Mirror of backend `SubFleetDto` (Phase 14 D-21).
 *
 * Source of truth: `backend/game-app/src/main/kotlin/com/openlogh/dto/TacticalBattleDtos.kt`.
 * The field names intentionally match the DTO contract (`commanderOfficerId`,
 * `memberFleetIds`) rather than the engine's internal names (`commanderId`,
 * `unitIds`) — `CommandHierarchyDto.fromEngine()` does the rename on the server.
 */
export interface SubFleetDto {
    commanderOfficerId: number;
    commanderName: string;
    memberFleetIds: number[];
    commanderRank: number;
}

/**
 * Mirror of backend `CommandHierarchyDto` (Phase 14 D-21).
 *
 * Carries everything the frontend needs to compute:
 *  - FE-03 UI gating ("is this unit inside my command chain?")
 *  - FE-04 succession feedback (vacancyStartTick + 30-tick countdown)
 *  - FE-05 fog-of-war sharing across the command network
 *  - CRC multi-render (self + sub-fleet commanders)
 */
export interface CommandHierarchyDto {
    /** Fleet commander officer ID (원수 / 사령관). */
    fleetCommander: number;
    /** Sub-fleet commanders and their assigned fleet IDs. */
    subFleets: SubFleetDto[];
    /** Ordered officer IDs for succession (rank desc). */
    successionQueue: number[];
    /** Pre-designated successor officer ID (SUCC-01). */
    designatedSuccessor?: number | null;
    /** Tick when command vacancy started (-1 = no vacancy, SUCC-03). */
    vacancyStartTick: number;
    /** Communication jamming active flag. */
    commJammed: boolean;
    /** Remaining ticks of jamming. */
    jammingTicksRemaining: number;
    /** Current active commander after delegation / succession (null = original fleetCommander). */
    activeCommander?: number | null;
}

/** Per-unit state in a tactical battle */
export interface TacticalUnit {
    fleetId: number;
    officerId: number;
    officerName: string;
    factionId: number;
    side: BattleSide;
    posX: number;
    posY: number;
    hp: number;
    maxHp: number;
    ships: number;
    maxShips: number;
    training: number;
    morale: number;
    energy: EnergyAllocation;
    formation: Formation;
    commandRange: number;
    isAlive: boolean;
    isRetreating: boolean;
    retreatProgress: number;
    unitType: string;
    /** 기함부대 여부 — true면 △ 삼각형으로 표시, 없으면 unitType==='flagship'으로 판단 */
    isFlagship?: boolean;
    // ── Phase 14 D-22 / D-24 / D-37 ──
    /** D-19: 색적 범위 — derived server-side from DetectionCapability.baseRange × SENSOR energy. */
    sensorRange?: number;
    /** D-22: Owning sub-fleet commander officerId (null = 사령관 직할). */
    subFleetCommanderId?: number | null;
    /** D-22: "PENDING_SUCCESSION" while the 30-tick vacancy countdown runs, else null. */
    successionState?: 'PENDING_SUCCESSION' | null;
    /** D-22: Ticks left before successor auto-assigned (max 30, SUCC-03). */
    successionTicksRemaining?: number | null;
    /** D-22, D-35: Whether the officer currently has an active WebSocket session. */
    isOnline?: boolean;
    /** D-22, D-35: Whether this unit is NPC-controlled (Officer.npcState != 0). */
    isNpc?: boolean;
    /** D-37: Current mission objective string from OperationPlan (CONQUEST / DEFENSE / SWEEP / null). */
    missionObjective?: string | null;
    /**
     * D-37: Star system ID the NPC unit is currently targeting (from
     * OperationPlan.targetStarSystemId or internal AI target). Optional —
     * the backend TacticalUnitDto does not currently surface this field;
     * the frontend renders the "목표: {systemName}" InfoPanel row and the
     * BattleMap dashed mission line only when this value is populated.
     *
     * Tracked as a 14-16 deferred-item — a future backend plan should
     * add `targetStarSystemId: Long?` to TacticalUnitDto and wire it
     * through `TacticalBattleService.toUnitDto`.
     */
    targetStarSystemId?: number | null;
    /** FE-01 / D-24: Maximum CRC radius (outer dashed stroke on the tactical map). */
    maxCommandRange?: number;
}

/** Tactical battle state */
export interface TacticalBattle {
    id: number;
    sessionId: number;
    starSystemId: number;
    attackerFactionId: number;
    defenderFactionId: number;
    phase: BattlePhase;
    startedAt: string;
    endedAt?: string;
    result?: string;
    tickCount: number;
    attackerFleetIds: number[];
    defenderFleetIds: number[];
    units: TacticalUnit[];
    /** Phase 14 D-21 — initial-fetch command hierarchy (attacker side). */
    attackerHierarchy?: CommandHierarchyDto | null;
    /** Phase 14 D-21 — initial-fetch command hierarchy (defender side). */
    defenderHierarchy?: CommandHierarchyDto | null;
}

/** Unit stance (v2.1 Phase 8) */
export type UnitStance = 'AGGRESSIVE' | 'DEFENSIVE' | 'EVASIVE' | 'HOLD';

/**
 * Per-tick battle event.
 *
 * The four Phase 14 D-23 values drive one-shot UI effects (toasts, flashes,
 * sounds); persistent state (countdown, jamming flag) lives on
 * {@link CommandHierarchyDto} / {@link TacticalUnit}.
 *
 *  - `FLAGSHIP_DESTROYED` — sourceUnitId = affected fleetId
 *  - `SUCCESSION_STARTED` — sourceUnitId = affected fleetId, value = ticksRemaining
 *  - `SUCCESSION_COMPLETED` — sourceUnitId = new commander fleetId, targetUnitId = old commander fleetId
 *  - `JAMMING_ACTIVE` — sourceUnitId = jammer fleetId, value = ticksRemaining
 */
export interface BattleTickEvent {
    type:
        | 'DAMAGE'
        | 'HEAL'
        | 'DESTROY'
        | 'RETREAT'
        // ── Phase 14 D-23 ──
        | 'FLAGSHIP_DESTROYED'
        | 'SUCCESSION_STARTED'
        | 'SUCCESSION_COMPLETED'
        | 'JAMMING_ACTIVE'
        // String fallback keeps older/unknown event codes compile-compatible
        // without losing literal inference on the known values above.
        | (string & {});
    sourceUnitId: number;
    targetUnitId: number;
    value: number;
    detail: string;
}

/** WebSocket broadcast from server each tick */
export interface BattleTickBroadcast {
    battleId: number;
    tickCount: number;
    phase: BattlePhase;
    /** Current tick phase within battle processing (e.g. MOVEMENT, COMBAT) */
    currentPhase?: string;
    units: TacticalUnit[];
    events: BattleTickEvent[];
    result?: string;
    /** Phase 14 D-21 — per-tick attacker hierarchy snapshot. */
    attackerHierarchy?: CommandHierarchyDto | null;
    /** Phase 14 D-21 — per-tick defender hierarchy snapshot. */
    defenderHierarchy?: CommandHierarchyDto | null;
}

/** Battle command sent by player */
export interface BattleCommand {
    battleId: number;
    officerId: number;
    commandType: 'energy' | 'formation' | 'retreat' | 'stance' | 'attack-target' | 'unit-command';
    energy?: EnergyAllocation;
    formation?: Formation;
    stance?: UnitStance;
    targetFleetId?: number;
    /** Unit command fields (v2.1) */
    unitCommand?: string;
    dirX?: number;
    dirY?: number;
    speed?: number;
}

/** Default balanced energy allocation */
export const DEFAULT_ENERGY: EnergyAllocation = {
    beam: 20,
    gun: 20,
    shield: 20,
    engine: 20,
    warp: 10,
    sensor: 10,
};

// ── Phase 14 D-32..D-34 — end-of-battle summary modal ──
// Mirror of backend `BattleSummaryRow` / `BattleSummaryDto` from
// `backend/game-app/src/main/kotlin/com/openlogh/dto/TacticalBattleDtos.kt`.

/**
 * Single row of a battle summary, one per participating fleet.
 *
 * `operationMultiplier` is 1.5 when the fleet was an active operation
 * participant AND `baseMerit > 0`, else 1.0. `totalMerit` equals
 * `floor(baseMerit * operationMultiplier)` so the UI can render
 * "기본 X + 작전 +Y = 총 Z".
 */
export interface BattleSummaryRow {
    fleetId: number;
    officerId: number;
    officerName: string;
    side: BattleSide;
    survivingShips: number;
    maxShips: number;
    baseMerit: number;
    operationMultiplier: number;
    totalMerit: number;
    isOperationParticipant: boolean;
}

/**
 * Per-battle merit breakdown response for the end-of-battle modal (D-32..D-34).
 *
 * `winner` is the lowercase result string matching `TacticalBattle.result`:
 * `"attacker_win" | "defender_win" | "draw" | null` (null = ongoing).
 */
export interface BattleSummaryDto {
    battleId: number;
    winner: string | null;
    durationTicks: number;
    rows: BattleSummaryRow[];
}

// ── Phase 14 D-31 — OperationPlan WebSocket events ──

/** Possible operation objectives (mirror of backend OperationObjective). */
export type OperationObjective = 'CONQUEST' | 'DEFENSE' | 'SWEEP';

/** Possible operation lifecycle statuses (mirror of backend OperationStatus). */
export type OperationStatus = 'PENDING' | 'ACTIVE' | 'COMPLETED' | 'CANCELLED';

/**
 * Phase 14 D-31 — WebSocket event emitted on
 * `/topic/world/{sessionId}/operations` whenever an OperationPlan changes state.
 */
export interface OperationEventDto {
    type: 'OPERATION_PLANNED' | 'OPERATION_STARTED' | 'OPERATION_COMPLETED' | 'OPERATION_CANCELLED';
    operationId: number;
    sessionId: number;
    factionId: number;
    objective: OperationObjective;
    targetStarSystemId: number;
    participantFleetIds: number[];
    status: OperationStatus;
    timestamp: number;
}
