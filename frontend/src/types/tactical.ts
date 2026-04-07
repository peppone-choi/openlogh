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
}

/** Unit stance (v2.1 Phase 8) */
export type UnitStance = 'AGGRESSIVE' | 'DEFENSIVE' | 'EVASIVE' | 'HOLD';

/** Battle tick event */
export interface BattleTickEvent {
    type: string;
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
