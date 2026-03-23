// Tactical Battle Type Definitions
// Coordinate system: free coordinates in 0.0–1000.0 range (not grid cells)

export type ShipClass = 'battleship' | 'cruiser' | 'destroyer' | 'carrier' | 'transport' | 'hospital' | 'fortress';
export type TerrainType = 'space' | 'asteroid' | 'nebula' | 'debris';
export type TacticalPhase = 'idle' | 'setup' | 'combat' | 'result';

export interface TacticalUnit {
    id: number;
    fleetId: number;
    factionId: number;
    shipClass: ShipClass;
    hp: number;
    maxHp: number;
    /** X position in logical coordinate space (0.0–1000.0) */
    x?: number;
    /** Y position in logical coordinate space (0.0–1000.0) */
    y?: number;
    /** Grid column position (legacy grid-based coordinates) */
    gridX?: number;
    /** Grid row position (legacy grid-based coordinates) */
    gridY?: number;
}

export interface EnergyConfig {
    beam: number;
    gun: number;
    shield: number;
    engine: number;
    sensor: number;
}

export interface TacticalFleet {
    id: string;
    fleetId: number;
    officerId: number;
    officerName: string;
    factionId: number;
    factionType: 'empire' | 'alliance' | 'fezzan' | 'rebel';
    formation: string;
    energy: EnergyConfig;
    morale: number;
    maxMorale: number;
    units: TacticalUnit[];
}

/** Obstacle region in the tactical space (circular area) */
export interface Obstacle {
    /** Center X in logical coordinates (0–1000) */
    x: number;
    /** Center Y in logical coordinates (0–1000) */
    y: number;
    /** Radius in logical units */
    radius: number;
    type: TerrainType;
}

export interface TacticalOrder {
    unitId?: number;
    type: string;
    targetX?: number;
    targetY?: number;
    targetId?: number;
    formation?: string;
    energy?: Record<string, number>;
    specialCode?: string;
}

export type BattleEventType =
    | 'attack'
    | 'damage'
    | 'move'
    | 'movement'
    | 'morale'
    | 'victory'
    | 'defeat'
    | 'result'
    | 'info';

export interface BattleEvent {
    id: string;
    turn: number;
    type: BattleEventType;
    description: string;
    message: string;
    sourceId?: number;
    targetId?: number;
    damage?: number;
    position?: { x: number; y: number };
}

export interface TacticalBattleResult {
    winner: string;
    merit: number;
    shipsDestroyed: number;
    shipsLost: number;
    planetCaptured: boolean;
    turnCount: number;
}
