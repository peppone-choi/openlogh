import { create } from 'zustand';
import type {
    TacticalBattle,
    TacticalUnit,
    EnergyAllocation,
    Formation,
    BattleTickBroadcast,
    BattleTickEvent,
    BattleSide,
} from '@/types/tactical';
import { tacticalApi } from '@/lib/tacticalApi';
import { DEFAULT_ENERGY } from '@/types/tactical';
import {
    computeVisibleEnemies,
    updateLastSeenEnemyPositions,
} from '@/lib/fogOfWar';

/**
 * Phase 14 D-20 — last-seen ghost record for the fog-of-war layer (14-11).
 *
 * Populated by 14-11's fog reducer once an enemy fleet leaves every friendly
 * sensorRange; read by FogLayer/EnemyGhostIcon. 14-10 just initialises the
 * field to `{}` and preserves it verbatim across onBattleTick calls so 14-11
 * can plug its update logic in without racing on this slot.
 */
export interface LastSeenEnemyRecord {
    x: number;
    y: number;
    tick: number;
    ships: number;
    unitType: string;
    side: BattleSide;
}

/**
 * Phase 14 D-14 — one-shot flagship-destroyed flash bookkeeping (14-14).
 *
 * `expiresAt` is a wall-clock millisecond deadline so 14-10's reducer can
 * prune expired entries on every tick without needing the tick number.
 */
export interface FlagshipDestroyedRecord {
    fleetId: number;
    expiresAt: number;
}

interface TacticalState {
    // Current battle state
    currentBattle: TacticalBattle | null;
    units: TacticalUnit[];
    recentEvents: BattleTickEvent[];
    activeBattles: TacticalBattle[];
    loading: boolean;
    error: string | null;

    // Player's unit
    myOfficerId: number | null;
    myEnergy: EnergyAllocation;
    myFormation: Formation;

    // ── Phase 14 ── bookkeeping for fog-of-war (14-11) + succession FX (14-14)
    /** D-20 — stale enemy positions keyed by fleetId. 14-11 fills, 14-10 preserves. */
    lastSeenEnemyPositions: Record<number, LastSeenEnemyRecord>;
    /** D-13 — fleet ids currently under PENDING_SUCCESSION. 14-14 fills, 14-10 preserves. */
    activeSuccessionFleetIds: number[];
    /** D-14 — active FLAGSHIP_DESTROYED flashes. 14-10 prunes expired entries per tick. */
    activeFlagshipDestroyedFleetIds: FlagshipDestroyedRecord[];

    // Actions
    loadActiveBattles: (sessionId: number) => Promise<void>;
    loadBattle: (sessionId: number, battleId: number) => Promise<void>;
    onBattleTick: (data: BattleTickBroadcast) => void;
    setMyOfficerId: (officerId: number) => void;
    setEnergy: (energy: EnergyAllocation) => void;
    setFormation: (formation: Formation) => void;
    clearBattle: () => void;
}

export const useTacticalStore = create<TacticalState>((set, get) => ({
    currentBattle: null,
    units: [],
    recentEvents: [],
    activeBattles: [],
    loading: false,
    error: null,
    myOfficerId: null,
    myEnergy: DEFAULT_ENERGY,
    myFormation: 'MIXED',
    // Phase 14 fog/succession bookkeeping — 14-11 / 14-14 plug into these slots.
    lastSeenEnemyPositions: {},
    activeSuccessionFleetIds: [],
    activeFlagshipDestroyedFleetIds: [],

    loadActiveBattles: async (sessionId: number) => {
        set({ loading: true, error: null });
        try {
            const { data } = await tacticalApi.getActiveBattles(sessionId);
            set({ activeBattles: data.battles, loading: false });
        } catch {
            set({ error: 'Failed to load active battles', loading: false });
        }
    },

    loadBattle: async (sessionId: number, battleId: number) => {
        set({ loading: true, error: null });
        try {
            const { data } = await tacticalApi.getBattleState(sessionId, battleId);
            const myId = get().myOfficerId;
            const myUnit = myId ? data.units.find((u) => u.officerId === myId) : undefined;
            set({
                currentBattle: data,
                units: data.units,
                loading: false,
                myEnergy: myUnit?.energy ?? DEFAULT_ENERGY,
                myFormation: myUnit?.formation ?? 'MIXED',
            });
        } catch {
            set({ error: 'Failed to load battle', loading: false });
        }
    },

    onBattleTick: (data: BattleTickBroadcast) => {
        const myId = get().myOfficerId;
        const myUnit = myId ? data.units.find((u) => u.officerId === myId) : undefined;
        const mySide = myUnit?.side;
        const now = Date.now();

        set((state) => {
            const prevBattle = state.currentBattle;
            // Resolve the hierarchy first so fog computation + nextBattle share it.
            const attackerHierarchy =
                data.attackerHierarchy ?? prevBattle?.attackerHierarchy ?? null;
            const defenderHierarchy =
                data.defenderHierarchy ?? prevBattle?.defenderHierarchy ?? null;

            // Phase 14 FE-05 (14-11) — fog-of-war update.
            // Only compute when we know which side the viewer is on. An
            // officer with no unit on the field (spectator / admin) gets the
            // previous fog state verbatim.
            let lastSeen = state.lastSeenEnemyPositions;
            if (mySide && myId != null) {
                const myHierarchy =
                    mySide === 'ATTACKER' ? attackerHierarchy : defenderHierarchy;
                const visibleEnemies = computeVisibleEnemies(
                    data.units,
                    mySide,
                    myId,
                    myHierarchy,
                );
                lastSeen = updateLastSeenEnemyPositions(
                    state.lastSeenEnemyPositions,
                    data.units,
                    visibleEnemies,
                    data.tickCount,
                    mySide,
                );
            }

            const nextBattle: TacticalBattle | null = prevBattle
                ? {
                      ...prevBattle,
                      tickCount: data.tickCount,
                      phase: data.phase,
                      result: data.result ?? prevBattle.result,
                      units: data.units,
                      // Phase 14 D-21 — merge per-tick hierarchy snapshots. A
                      // null broadcast value means "unchanged" (not "cleared")
                      // so we fall through to the previous cached value.
                      attackerHierarchy,
                      defenderHierarchy,
                  }
                : null;

            return {
                units: data.units,
                recentEvents: [...data.events, ...state.recentEvents].slice(0, 50),
                currentBattle: nextBattle,
                // Update local energy/formation from server state (reconciliation)
                myEnergy: myUnit?.energy ?? state.myEnergy,
                myFormation: myUnit?.formation ?? state.myFormation,
                // Phase 14 FE-05 (14-11) — fog ghosts computed above.
                lastSeenEnemyPositions: lastSeen,
                // 14-14 still owns succession bookkeeping.
                activeSuccessionFleetIds: state.activeSuccessionFleetIds,
                activeFlagshipDestroyedFleetIds:
                    state.activeFlagshipDestroyedFleetIds.filter(
                        (entry) => entry.expiresAt > now,
                    ),
            };
        });
    },

    setMyOfficerId: (officerId: number) => {
        set({ myOfficerId: officerId });
    },

    setEnergy: (energy: EnergyAllocation) => {
        set({ myEnergy: energy });
    },

    setFormation: (formation: Formation) => {
        set({ myFormation: formation });
    },

    clearBattle: () => {
        set({
            currentBattle: null,
            units: [],
            recentEvents: [],
            myEnergy: DEFAULT_ENERGY,
            myFormation: 'MIXED',
            // Phase 14 — reset fog/succession bookkeeping when leaving a battle
            // so the next battle starts with a clean slate.
            lastSeenEnemyPositions: {},
            activeSuccessionFleetIds: [],
            activeFlagshipDestroyedFleetIds: [],
        });
    },
}));
