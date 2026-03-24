import { create } from 'zustand';
import type { TacticalFleet, BattleEvent, TacticalBattleResult, Obstacle } from '@/types/tactical';

// ─── Type exports (used by battle UI components & canvas) ────────────────────

export type Formation = 'spindle' | 'crane_wing' | 'wheel' | 'echelon' | 'square' | 'dispersed' | 'dispersed_release';
export type BattleOrder = 'breakthrough' | 'pin_down' | 'flank' | 'retreat' | 'hold' | 'pursue';
export type FactionType = 'empire' | 'alliance' | 'fezzan' | 'rebel';
export type ShipClass = 'battleship' | 'cruiser' | 'destroyer' | 'carrier' | 'transport';
export type TerrainType = 'space' | 'asteroid' | 'nebula' | 'debris';

export interface GridCell {
    x: number;
    y: number;
    terrain: TerrainType;
}

export interface EnergyAllocation {
    beam: number;
    gun: number;
    shield: number;
    engine: number;
    warp: number;
    sensor: number;
}

export interface BattleFleet {
    id: string;
    name: string;
    commanderName: string;
    ships: number;
    maxShips: number;
    morale: number;
    x: number;
    y: number;
    faction: FactionType;
    formation: Formation;
    energy: EnergyAllocation;
    isMyFleet: boolean;
    order?: BattleOrder;
}

export interface BattleLogEntry {
    id: string;
    turn: number;
    message: string;
    type: 'attack' | 'movement' | 'morale' | 'result' | 'info';
}

export interface BattleResultData {
    winner: 'my_side' | 'enemy_side' | 'draw';
    shipsDestroyed: number;
    shipsLost: number;
    prisoners: number;
    meritPoints: number;
    planetCaptured?: string;
    turnCount: number;
}

/** Canvas-compatible tactical unit (flat shape for rendering) */
export interface TacticalUnit {
    id: number;
    fleetId: string;
    shipClass: ShipClass;
    ships: number;
    maxShips: number;
    morale: number;
    gridX: number;
    gridY: number;
    faction: FactionType;
    isMyUnit: boolean;
    commanderName?: string;
}

/** Canvas-compatible tactical order */
export interface TacticalOrder {
    unitId: number;
    type: 'move' | 'attack';
    targetX: number;
    targetY: number;
}

/** Canvas attack effect animation */
export interface AttackEffect {
    id: string;
    fromX: number;
    fromY: number;
    toX: number;
    toY: number;
    attackType: 'beam' | 'gun';
}

// Re-export tactical types used by page (not TacticalUnit/TacticalOrder — those are defined locally above)
export type { TacticalFleet, BattleEvent, TacticalBattleResult, Obstacle };

// ─── Conversion helpers ──────────────────────────────────────────────────────

const GRID_SIZE = 20;
const CELL_PX = 40;

function createEmptyGrid(): GridCell[][] {
    const grid: GridCell[][] = [];
    for (let y = 0; y < GRID_SIZE; y++) {
        const row: GridCell[] = [];
        for (let x = 0; x < GRID_SIZE; x++) {
            row.push({ x, y, terrain: 'space' });
        }
        grid.push(row);
    }
    return grid;
}

// Logical coords are 0-1000; grid cells are 0-19 (50 units per cell)
const LOGICAL_SCALE = GRID_SIZE / 1000; // 0.02

function applyObstacles(grid: GridCell[][], obstacles: Obstacle[]): GridCell[][] {
    const copy = grid.map((row) => row.map((cell) => ({ ...cell })));
    for (const obs of obstacles) {
        const cx = obs.x * LOGICAL_SCALE;
        const cy = obs.y * LOGICAL_SCALE;
        const r = obs.radius * LOGICAL_SCALE;
        const x0 = Math.max(0, Math.floor(cx - r));
        const x1 = Math.min(GRID_SIZE - 1, Math.ceil(cx + r));
        const y0 = Math.max(0, Math.floor(cy - r));
        const y1 = Math.min(GRID_SIZE - 1, Math.ceil(cy + r));
        for (let gy = y0; gy <= y1; gy++) {
            for (let gx = x0; gx <= x1; gx++) {
                const dx = gx - cx,
                    dy = gy - cy;
                if (Math.sqrt(dx * dx + dy * dy) <= r) copy[gy][gx].terrain = obs.type;
            }
        }
    }
    return copy;
}

function fleetTotalShips(fleet: TacticalFleet): number {
    return fleet.units.reduce((sum, u) => sum + u.hp, 0);
}

function fleetMaxShips(fleet: TacticalFleet): number {
    return fleet.units.reduce((sum, u) => sum + u.maxHp, 0);
}

function fleetCenterX(fleet: TacticalFleet): number {
    if (fleet.units.length === 0) return CELL_PX * 2;
    const avg = fleet.units.reduce((s, u) => s + (u.x ?? 0), 0) / fleet.units.length;
    return Math.round((avg * CELL_PX) / 50);
}

function fleetCenterY(fleet: TacticalFleet): number {
    if (fleet.units.length === 0) return CELL_PX * 2;
    const avg = fleet.units.reduce((s, u) => s + (u.y ?? 0), 0) / fleet.units.length;
    return Math.round((avg * CELL_PX) / 50);
}

/** Convert TacticalFleet to legacy BattleFleet for older components */
export function toLegacyFleet(fleet: TacticalFleet, isMyFleet: boolean): BattleFleet {
    return {
        id: `fleet-${fleet.fleetId}`,
        name: `제${fleet.fleetId}함대`,
        commanderName: fleet.officerName,
        ships: fleetTotalShips(fleet),
        maxShips: fleetMaxShips(fleet),
        morale: fleet.morale,
        x: fleetCenterX(fleet),
        y: fleetCenterY(fleet),
        faction: fleet.factionType,
        formation: (fleet.formation as Formation) ?? 'spindle',
        energy: { ...fleet.energy, warp: fleet.energy?.warp ?? 0 } as EnergyAllocation,
        isMyFleet,
    };
}

/** Convert TacticalFleet units to canvas-compatible TacticalUnit[] */
function toCanvasUnits(fleet: TacticalFleet, isMyUnit: boolean): TacticalUnit[] {
    return fleet.units.map((u, i) => ({
        id: u.id,
        fleetId: `fleet-${fleet.fleetId}`,
        shipClass: u.shipClass as ShipClass,
        ships: u.hp,
        maxShips: u.maxHp,
        morale: fleet.morale,
        gridX: u.gridX ?? 0,
        gridY: u.gridY ?? 0,
        faction: fleet.factionType,
        isMyUnit,
        commanderName: i === 0 ? fleet.officerName : undefined,
    }));
}

/** Convert TacticalBattleResult to legacy BattleResultData */
export function toLegacyResult(result: TacticalBattleResult): BattleResultData {
    let winner: BattleResultData['winner'];
    if (result.winner === 'my_side') winner = 'my_side';
    else if (result.winner === 'draw') winner = 'draw';
    else winner = 'enemy_side';

    return {
        winner,
        shipsDestroyed: result.shipsDestroyed,
        shipsLost: result.shipsLost,
        prisoners: 0,
        meritPoints: result.merit,
        planetCaptured: result.planetCaptured ? '행성 점령' : undefined,
        turnCount: result.turnCount,
    };
}

/** Convert BattleEvent[] to legacy BattleLogEntry[] */
export function toLegacyLog(events: BattleEvent[]): BattleLogEntry[] {
    return events.map((ev, i) => {
        let logType: BattleLogEntry['type'] = 'info';
        if (ev.type === 'attack' || ev.type === 'damage') logType = 'attack';
        else if (ev.type === 'move') logType = 'movement';
        else if (ev.type === 'morale') logType = 'morale';
        else if (ev.type === 'victory' || ev.type === 'defeat') logType = 'result';
        return { id: `log-${ev.turn}-${i}`, turn: ev.turn, message: ev.description, type: logType };
    });
}

// ─── Default values ──────────────────────────────────────────────────────────

const DEFAULT_ENERGY: EnergyAllocation = { beam: 20, gun: 15, shield: 15, engine: 15, warp: 15, sensor: 20 };

// ─── Store interface ─────────────────────────────────────────────────────────

interface BattleStore {
    // Session
    sessionCode: string | null;
    phase: 'idle' | 'setup' | 'combat' | 'result';
    turn: number;
    maxTurns: number;
    timer: number;

    // Fleets & Units
    myFleets: TacticalFleet[];
    enemyFleets: TacticalFleet[];

    // Canvas-compatible tactical units (flat array of all units)
    tacticalUnits: TacticalUnit[];
    attackEffects: AttackEffect[];

    // Grid
    grid: GridCell[][];
    obstacles: Obstacle[];

    // Selection & Orders
    selectedUnitIds: number[];
    pendingOrders: TacticalOrder[];
    isReady: boolean;

    // Formation/Energy for active fleet (compat with existing UI components)
    pendingFormation: Formation;
    pendingEnergy: EnergyAllocation;
    pendingOrder: BattleOrder | null;
    commandSubmitted: boolean;

    // Results
    battleLog: BattleEvent[];
    result: TacticalBattleResult | null;

    // View
    viewportX: number;
    viewportY: number;
    zoom: number;

    // Legacy compat aliases (synced manually)
    battlePhase: 'idle' | 'setup' | 'combat' | 'result';
    sessionId: string | null;
    myFleet: BattleFleet | null;
    alliedFleets: BattleFleet[];
    turnTimer: number;
    currentTurn: number;
    battleResult: BattleResultData | null;

    // Core actions
    joinBattle: (sessionCode: string) => void;
    startDemo: () => void;
    selectUnit: (id: number, multi?: boolean) => void;
    deselectAll: () => void;
    issueOrder: (order: TacticalOrder) => void;
    cancelOrder: (index: number) => void;
    setFormation: (fleetIdOrFormation: number | Formation, formation?: string) => void;
    setEnergy: (
        fleetIdOrKey: number | keyof Omit<EnergyAllocation, 'sensor'>,
        energyOrValue?: Record<string, number> | number
    ) => void;
    submitReady: () => void;
    leaveBattle: () => void;
    tickTimer: () => void;

    // Canvas actions
    addTacticalOrder: (order: TacticalOrder) => void;
    clearOrders: () => void;
    submitTacticalOrders: () => void;
    setViewport: (x: number, y: number, zoom: number) => void;
    addAttackEffect: (effect: AttackEffect) => void;
    removeAttackEffect: (id: string) => void;

    // Legacy compat actions
    setOrder: (o: BattleOrder | null) => void;
    submitCommand: () => void;
    resetBattle: () => void;
    startDemoBattle: () => void;
    handleBattleEvent: (event: unknown) => void;

    // WebSocket handlers
    onStateUpdate: (state: Record<string, unknown>) => void;
    onTurnResult: (turnResult: Record<string, unknown>) => void;
    onTimerUpdate: (newTimer: number) => void;
    onVictory: (victoryResult: Record<string, unknown>) => void;
}

// ─── Store implementation ────────────────────────────────────────────────────

export const useBattleStore = create<BattleStore>((set, get) => ({
    // Session
    sessionCode: null,
    phase: 'idle',
    turn: 0,
    maxTurns: 50,
    timer: 30,

    // Fleets & Units
    myFleets: [],
    enemyFleets: [],

    // Canvas
    tacticalUnits: [],
    attackEffects: [],

    // Grid
    grid: createEmptyGrid(),
    obstacles: [],

    // Selection & Orders
    selectedUnitIds: [],
    pendingOrders: [],
    isReady: false,

    // Formation/Energy compat
    pendingFormation: 'spindle',
    pendingEnergy: { ...DEFAULT_ENERGY },
    pendingOrder: null,
    commandSubmitted: false,

    // Results
    battleLog: [],
    result: null,

    // View
    viewportX: 0,
    viewportY: 0,
    zoom: 1,

    // Legacy compat aliases
    battlePhase: 'idle',
    sessionId: null,
    myFleet: null,
    alliedFleets: [],
    turnTimer: 30,
    currentTurn: 0,
    battleResult: null,

    // ─── Core actions ────────────────────────────────────────────────────

    joinBattle: (sessionCode) => {
        set({
            sessionCode,
            sessionId: sessionCode,
            phase: 'setup',
            battlePhase: 'setup',
            turn: 0,
            currentTurn: 0,
            timer: 30,
            turnTimer: 30,
            battleLog: [],
            result: null,
            battleResult: null,
            selectedUnitIds: [],
            pendingOrders: [],
            isReady: false,
            commandSubmitted: false,
            tacticalUnits: [],
            attackEffects: [],
        });
    },

    startDemo: () => {
        const obstacles: Obstacle[] = [
            { x: 9, y: 8, radius: 20, type: 'asteroid' },
            { x: 10, y: 8, radius: 20, type: 'asteroid' },
            { x: 9, y: 9, radius: 20, type: 'asteroid' },
            { x: 10, y: 9, radius: 20, type: 'asteroid' },
            { x: 11, y: 9, radius: 20, type: 'asteroid' },
            { x: 9, y: 10, radius: 20, type: 'asteroid' },
            { x: 10, y: 10, radius: 20, type: 'asteroid' },
            { x: 10, y: 11, radius: 20, type: 'nebula' },
            { x: 11, y: 11, radius: 20, type: 'nebula' },
            { x: 10, y: 12, radius: 20, type: 'nebula' },
        ];
        const grid = applyObstacles(createEmptyGrid(), obstacles);

        const myFleetData: TacticalFleet = {
            id: 'fleet-1',
            fleetId: 1,
            officerId: 1,
            officerName: '라인하르트 폰 뮤젤',
            factionId: 1,
            factionType: 'empire',
            formation: 'spindle',
            energy: { beam: 25, gun: 20, shield: 15, engine: 15, warp: 15, sensor: 10 },
            morale: 95,
            maxMorale: 100,
            units: [
                { id: 1, fleetId: 1, factionId: 1, shipClass: 'battleship', hp: 2400, maxHp: 3000, gridX: 3, gridY: 4 },
                { id: 2, fleetId: 1, factionId: 1, shipClass: 'cruiser', hp: 1800, maxHp: 2100, gridX: 2, gridY: 6 },
                { id: 3, fleetId: 1, factionId: 1, shipClass: 'destroyer', hp: 1500, maxHp: 1800, gridX: 4, gridY: 2 },
                { id: 4, fleetId: 1, factionId: 1, shipClass: 'carrier', hp: 900, maxHp: 900, gridX: 1, gridY: 9 },
                { id: 5, fleetId: 1, factionId: 1, shipClass: 'battleship', hp: 2700, maxHp: 3000, gridX: 5, gridY: 7 },
                {
                    id: 6,
                    fleetId: 1,
                    factionId: 1,
                    shipClass: 'battleship',
                    hp: 2850,
                    maxHp: 3000,
                    gridX: 2,
                    gridY: 13,
                },
            ],
        };

        const enemyFleetData: TacticalFleet = {
            id: 'fleet-13',
            fleetId: 13,
            officerId: 13,
            officerName: '양 웬리',
            factionId: 2,
            factionType: 'alliance',
            formation: 'crane_wing',
            energy: { beam: 20, gun: 20, shield: 20, engine: 15, warp: 15, sensor: 10 },
            morale: 88,
            maxMorale: 100,
            units: [
                {
                    id: 9,
                    fleetId: 13,
                    factionId: 2,
                    shipClass: 'battleship',
                    hp: 2100,
                    maxHp: 3000,
                    gridX: 16,
                    gridY: 4,
                },
                { id: 10, fleetId: 13, factionId: 2, shipClass: 'cruiser', hp: 1650, maxHp: 2100, gridX: 17, gridY: 6 },
                {
                    id: 11,
                    fleetId: 13,
                    factionId: 2,
                    shipClass: 'destroyer',
                    hp: 1200,
                    maxHp: 1500,
                    gridX: 15,
                    gridY: 2,
                },
                { id: 12, fleetId: 13, factionId: 2, shipClass: 'carrier', hp: 600, maxHp: 900, gridX: 18, gridY: 8 },
                {
                    id: 13,
                    fleetId: 13,
                    factionId: 2,
                    shipClass: 'battleship',
                    hp: 1500,
                    maxHp: 3000,
                    gridX: 17,
                    gridY: 13,
                },
                { id: 14, fleetId: 13, factionId: 2, shipClass: 'cruiser', hp: 900, maxHp: 1500, gridX: 16, gridY: 17 },
            ],
        };

        const tacticalUnits = [...toCanvasUnits(myFleetData, true), ...toCanvasUnits(enemyFleetData, false)];

        const legacyMyFleet = toLegacyFleet(myFleetData, true);

        const initLog: BattleEvent[] = [
            {
                id: 'init-0',
                turn: 0,
                type: 'info',
                description: '이제르론 회랑 제3구역 — 제국군과 자유행성동맹군이 조우하였다. 전투 개시.',
                message: '이제르론 회랑 제3구역 — 제국군과 자유행성동맹군이 조우하였다. 전투 개시.',
            },
            {
                id: 'init-1',
                turn: 1,
                type: 'move',
                description: '제국군 제1함대 전함대 — 돌파 기동 개시. 좌익 전개.',
                message: '제국군 제1함대 전함대 — 돌파 기동 개시. 좌익 전개.',
            },
        ];

        set({
            sessionCode: 'demo-001',
            sessionId: 'demo-001',
            phase: 'setup',
            battlePhase: 'setup',
            turn: 1,
            currentTurn: 1,
            maxTurns: 50,
            timer: 30,
            turnTimer: 30,
            myFleets: [myFleetData],
            enemyFleets: [enemyFleetData],
            tacticalUnits,
            attackEffects: [],
            grid,
            obstacles,
            selectedUnitIds: [],
            pendingOrders: [],
            isReady: false,
            pendingFormation: 'spindle',
            pendingEnergy: { beam: 25, gun: 20, shield: 15, engine: 15, warp: 15, sensor: 10 },
            pendingOrder: null,
            commandSubmitted: false,
            battleLog: initLog,
            result: null,
            battleResult: null,
            viewportX: 0,
            viewportY: 0,
            zoom: 1,
            myFleet: legacyMyFleet,
            alliedFleets: [],
        });
    },

    selectUnit: (id, multi = false) => {
        const { selectedUnitIds } = get();
        if (multi) {
            if (selectedUnitIds.includes(id)) {
                set({ selectedUnitIds: selectedUnitIds.filter((x) => x !== id) });
            } else {
                set({ selectedUnitIds: [...selectedUnitIds, id] });
            }
        } else {
            set({ selectedUnitIds: selectedUnitIds.includes(id) && selectedUnitIds.length === 1 ? [] : [id] });
        }
    },

    deselectAll: () => set({ selectedUnitIds: [] }),

    issueOrder: (order) => {
        const { pendingOrders } = get();
        const without = pendingOrders.filter((o) => o.unitId !== order.unitId);
        set({ pendingOrders: [...without, order] });
    },

    cancelOrder: (index) => {
        set({ pendingOrders: get().pendingOrders.filter((_, i) => i !== index) });
    },

    setFormation: (fleetIdOrFormation, formation?) => {
        if (typeof fleetIdOrFormation === 'string') {
            const f = fleetIdOrFormation as Formation;
            const { myFleets } = get();
            set({ pendingFormation: f });
            if (myFleets.length > 0) {
                set({
                    myFleets: myFleets.map((fleet, i) => (i === 0 ? { ...fleet, formation: f } : fleet)),
                    myFleet: toLegacyFleet({ ...myFleets[0], formation: f }, true),
                });
            }
        } else if (formation) {
            const { myFleets } = get();
            set({
                myFleets: myFleets.map((fleet) =>
                    fleet.fleetId === fleetIdOrFormation ? { ...fleet, formation } : fleet
                ),
                pendingFormation: formation as Formation,
            });
            const updated = myFleets.find((f) => f.fleetId === fleetIdOrFormation);
            if (updated) set({ myFleet: toLegacyFleet({ ...updated, formation }, true) });
        }
    },

    setEnergy: (fleetIdOrKey, energyOrValue?) => {
        if (typeof fleetIdOrKey === 'string') {
            const key = fleetIdOrKey as keyof Omit<EnergyAllocation, 'sensor'>;
            const value = energyOrValue as number;
            const cur = get().pendingEnergy;
            const clamped = Math.max(0, Math.min(80, Math.round(value)));
            const next = { ...cur, [key]: clamped };
            const manual = next.beam + next.gun + next.shield + next.engine + next.warp;
            if (manual > 100) {
                const excess = manual - 100;
                const otherKeys = (['beam', 'gun', 'shield', 'engine', 'warp'] as const).filter((k) => k !== key);
                const othersTotal = otherKeys.reduce((s, k) => s + next[k], 0);
                if (othersTotal > 0) {
                    for (const k of otherKeys) {
                        next[k] = Math.max(0, Math.round(next[k] - (next[k] / othersTotal) * excess));
                    }
                }
                next.sensor = 0;
            } else {
                next.sensor = Math.max(0, 100 - manual);
            }
            set({ pendingEnergy: next });
            const { myFleets } = get();
            if (myFleets.length > 0) {
                set({
                    myFleets: myFleets.map((fleet, i) => (i === 0 ? { ...fleet, energy: next } : fleet)),
                    myFleet: toLegacyFleet({ ...myFleets[0], energy: next }, true),
                });
            }
        } else {
            const fleetId = fleetIdOrKey;
            const energy = energyOrValue as Record<string, number>;
            const { myFleets } = get();
            set({
                myFleets: myFleets.map((f) =>
                    f.fleetId === fleetId
                        ? {
                              ...f,
                              energy: {
                                  beam: energy.beam ?? f.energy.beam,
                                  gun: energy.gun ?? f.energy.gun,
                                  shield: energy.shield ?? f.energy.shield,
                                  engine: energy.engine ?? f.energy.engine,
                                  warp: energy.warp ?? f.energy.warp,
                                  sensor: energy.sensor ?? f.energy.sensor,
                              },
                          }
                        : f
                ),
            });
        }
    },

    submitReady: () => set({ isReady: true, commandSubmitted: true }),

    leaveBattle: () => {
        set({
            sessionCode: null,
            sessionId: null,
            phase: 'idle',
            battlePhase: 'idle',
            turn: 0,
            currentTurn: 0,
            timer: 30,
            turnTimer: 30,
            myFleets: [],
            enemyFleets: [],
            tacticalUnits: [],
            attackEffects: [],
            grid: createEmptyGrid(),
            obstacles: [],
            selectedUnitIds: [],
            pendingOrders: [],
            isReady: false,
            pendingFormation: 'spindle',
            pendingEnergy: { ...DEFAULT_ENERGY },
            pendingOrder: null,
            commandSubmitted: false,
            battleLog: [],
            result: null,
            battleResult: null,
            myFleet: null,
            alliedFleets: [],
            viewportX: 0,
            viewportY: 0,
            zoom: 1,
        });
    },

    tickTimer: () => {
        const { timer, phase } = get();
        if (phase !== 'setup' && phase !== 'combat') return;
        if (timer > 0) set({ timer: timer - 1, turnTimer: timer - 1 });
    },

    // ─── Canvas actions ──────────────────────────────────────────────────

    addTacticalOrder: (order) => {
        const { pendingOrders } = get();
        const without = pendingOrders.filter((o) => o.unitId !== order.unitId);
        set({ pendingOrders: [...without, order] });
    },

    clearOrders: () => set({ pendingOrders: [] }),

    submitTacticalOrders: () => set({ commandSubmitted: true }),

    setViewport: (x, y, zoom) => set({ viewportX: x, viewportY: y, zoom }),

    addAttackEffect: (effect) => {
        set({ attackEffects: [...get().attackEffects, effect] });
    },

    removeAttackEffect: (id) => {
        set({ attackEffects: get().attackEffects.filter((e) => e.id !== id) });
    },

    // ─── Legacy compat actions ───────────────────────────────────────────

    setOrder: (o) => set({ pendingOrder: o }),

    submitCommand: () => set({ commandSubmitted: true, isReady: true }),

    resetBattle: () => get().leaveBattle(),

    startDemoBattle: () => get().startDemo(),

    handleBattleEvent: (event) => {
        const ev = event as Record<string, unknown>;
        const type = ev.type as string;
        if (type === 'BATTLE_START') {
            const sessionId = (ev.sessionId as string) ?? null;
            if (sessionId) get().joinBattle(sessionId);
            if (ev.myFleet) set({ myFleet: ev.myFleet as BattleFleet });
            if (ev.alliedFleets) set({ alliedFleets: (ev.alliedFleets as BattleFleet[]) ?? [] });
            if (ev.tacticalUnits) set({ tacticalUnits: (ev.tacticalUnits as TacticalUnit[]) ?? [] });
        } else if (type === 'TURN_START') {
            const newTurn = (ev.turn as number) ?? get().turn + 1;
            set({
                phase: 'combat',
                battlePhase: 'combat',
                turn: newTurn,
                currentTurn: newTurn,
                timer: 30,
                turnTimer: 30,
                commandSubmitted: false,
                isReady: false,
            });
        } else if (type === 'TURN_RESULT') {
            get().onTurnResult(ev);
        } else if (type === 'BATTLE_END') {
            const legacyResult = (ev.result as BattleResultData) ?? null;
            set({
                phase: 'result',
                battlePhase: 'result',
                battleResult: legacyResult,
            });
        }
    },

    // ─── WebSocket handlers ──────────────────────────────────────────────

    onStateUpdate: (state) => {
        const myFleets = (state.myFleets as TacticalFleet[]) ?? get().myFleets;
        const enemyFleets = (state.enemyFleets as TacticalFleet[]) ?? get().enemyFleets;
        const grid = (state.grid as GridCell[][]) ?? get().grid;
        const newPhase = (state.phase as BattleStore['phase']) ?? get().phase;
        const tacticalUnits = [
            ...myFleets.flatMap((f) => toCanvasUnits(f, true)),
            ...enemyFleets.flatMap((f) => toCanvasUnits(f, false)),
        ];
        set({
            myFleets,
            enemyFleets,
            grid,
            phase: newPhase,
            battlePhase: newPhase,
            tacticalUnits,
            myFleet: myFleets.length > 0 ? toLegacyFleet(myFleets[0], true) : null,
        });
    },

    onTurnResult: (turnResult) => {
        const { battleLog, myFleets, enemyFleets } = get();
        const newEvents = (turnResult.events as BattleEvent[]) ?? (turnResult.logs as BattleEvent[]) ?? [];
        const fleetUpdates = (turnResult.fleets as TacticalFleet[]) ?? [];
        const unitUpdates = (turnResult.units as TacticalUnit[]) ?? [];
        const nextTurn = (turnResult.turn as number) ?? get().turn + 1;

        const updatedMyFleets = myFleets.map((f) => {
            const update = fleetUpdates.find((u) => u.fleetId === f.fleetId);
            return update ?? f;
        });
        const updatedEnemyFleets = enemyFleets.map((f) => {
            const update = fleetUpdates.find((u) => u.fleetId === f.fleetId);
            return update ?? f;
        });

        // Update tactical units from unit-level updates or re-derive from fleets
        let { tacticalUnits } = get();
        if (unitUpdates.length > 0) {
            tacticalUnits = tacticalUnits
                .map((tu) => unitUpdates.find((u) => u.id === tu.id) ?? tu)
                .filter((tu) => tu.ships > 0);
        } else {
            tacticalUnits = [
                ...updatedMyFleets.flatMap((f) => toCanvasUnits(f, true)),
                ...updatedEnemyFleets.flatMap((f) => toCanvasUnits(f, false)),
            ];
        }

        set({
            phase: 'combat',
            battlePhase: 'combat',
            turn: nextTurn,
            currentTurn: nextTurn,
            timer: 30,
            turnTimer: 30,
            commandSubmitted: false,
            isReady: false,
            pendingOrders: [],
            myFleets: updatedMyFleets,
            enemyFleets: updatedEnemyFleets,
            tacticalUnits,
            battleLog: [...battleLog, ...newEvents],
            myFleet: updatedMyFleets.length > 0 ? toLegacyFleet(updatedMyFleets[0], true) : null,
        });
    },

    onTimerUpdate: (newTimer) => set({ timer: newTimer, turnTimer: newTimer }),

    onVictory: (victoryResult) => {
        const tacticalResult = victoryResult as unknown as TacticalBattleResult;
        set({
            phase: 'result',
            battlePhase: 'result',
            result: tacticalResult,
            battleResult: toLegacyResult(tacticalResult),
        });
    },
}));
