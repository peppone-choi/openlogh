// ─── 전투 애니메이션 시퀀스 ───

export interface AnimationSequence {
    terrain: TerrainType;
    weather: WeatherType;
    attacker: UnitConfig;
    defenders: UnitConfig[];
    city?: CityConfig;
    phases: BattlePhase[];
    result: BattleOutcome;
}

export interface UnitConfig {
    name: string;
    nationId: number;
    nationColor: string;
    crewType: number;
    initialCrew: number;
    leadership: number;
    strength: number;
    intel: number;
}

export interface CityConfig {
    name: string;
    level: number;
    def: number;
    wall: number;
    nationId: number;
}

export interface BattlePhase {
    phaseNumber: number;
    attackerHpBefore: number;
    attackerHpAfter: number;
    defenderHpBefore: number;
    defenderHpAfter: number;
    attackerDamage: number;
    defenderDamage: number;
    activeDefenderIndex: number;
    log: string;
    events: PhaseEvent[];
}

export type PhaseEvent =
    | 'critical'
    | 'dodge'
    | 'trigger_fire'
    | 'trigger_ice'
    | 'trigger_lightning'
    | 'injury'
    | 'rice_shortage'
    | 'retreat'
    | 'city_occupied'
    | 'defender_switch';

export interface BattleOutcome {
    winner: 'attacker' | 'defender';
    cityOccupied: boolean;
    attackerRemaining: number;
    defenderRemaining: number;
}

export type TerrainType = 'plain' | 'mountain' | 'forest' | 'river' | 'castle';
export type WeatherType = 'clear' | 'rain' | 'snow' | 'fog';

// ─── 3D 씬 상태 ───

export type BattleViewMode = '3d' | '2d';
export type BattlePlayState = 'idle' | 'loading' | 'playing' | 'paused' | 'finished';

export interface Battle3DState {
    viewMode: BattleViewMode;
    playState: BattlePlayState;
    sequence: AnimationSequence | null;
    currentPhase: number;
    playbackSpeed: number;
    showEffects: boolean;
    showHUD: boolean;
}

// ─── 유닛 렌더링 ───

export interface UnitRenderState {
    position: [number, number, number];
    rotation: [number, number, number];
    scale: number;
    hp: number;
    maxHp: number;
    isAttacking: boolean;
    isHit: boolean;
    isRetreating: boolean;
    opacity: number;
}

// ─── 카메라 프리셋 ───

export interface CameraPreset {
    position: [number, number, number];
    target: [number, number, number];
    fov: number;
    label: string;
}
