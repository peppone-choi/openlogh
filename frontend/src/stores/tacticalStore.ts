import { create } from 'zustand';
import { toast } from 'sonner';
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
import { playSoundEffect } from '@/hooks/useSoundEffects';

// Phase 14 FE-04 / D-14 — how long a flagship-destroyed ring flash stays on
// the Konva layer (wall-clock millis, pruned on the next onBattleTick).
const FLAGSHIP_FLASH_DURATION_MS = 500;

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

            // ── Phase 14 FE-04 (14-15) — succession feedback event processing ──
            //
            // Start from the previous state (prune expired flashes by
            // wall-clock), then walk this tick's events to register new flashes,
            // toggle succession bookkeeping, fire sounds + Sonner toasts.
            //
            // Per D-13..D-16 / UI-SPEC Section D:
            //   * FLAGSHIP_DESTROYED  → 0.5s ring flash + 'flagshipDestroyed' sfx
            //   * SUCCESSION_STARTED  → bookkeeping add + 'successionStart' sfx
            //                           + destructive toast ("기함 격침 …")
            //   * SUCCESSION_COMPLETED → bookkeeping remove + success toast
            //                            ("지휘 인수 …")
            //   * JAMMING_ACTIVE      → (state-only; CommandHierarchyDto
            //                           carries the persistent flag. 14-15 does
            //                           not fire a toast for this; 14-17's
            //                           comm-jam indicator handles it.)
            const flashExpiry = now + FLAGSHIP_FLASH_DURATION_MS;
            const prunedFlashes = state.activeFlagshipDestroyedFleetIds.filter(
                (entry) => entry.expiresAt > now,
            );
            const newFlashes = [...prunedFlashes];
            let newSuccessions = [...state.activeSuccessionFleetIds];

            for (const ev of data.events) {
                if (ev.type === 'FLAGSHIP_DESTROYED' && ev.sourceUnitId) {
                    // De-dup by fleetId + keep only the newest expiry.
                    const existingIdx = newFlashes.findIndex(
                        (f) => f.fleetId === ev.sourceUnitId,
                    );
                    if (existingIdx >= 0) {
                        newFlashes[existingIdx] = {
                            fleetId: ev.sourceUnitId,
                            expiresAt: flashExpiry,
                        };
                    } else {
                        newFlashes.push({
                            fleetId: ev.sourceUnitId,
                            expiresAt: flashExpiry,
                        });
                    }
                    playSoundEffect('flagshipDestroyed');
                    continue;
                }
                if (ev.type === 'SUCCESSION_STARTED' && ev.sourceUnitId) {
                    if (!newSuccessions.includes(ev.sourceUnitId)) {
                        newSuccessions.push(ev.sourceUnitId);
                    }
                    playSoundEffect('successionStart');
                    // D-13 Korean copy from UI-SPEC Section D Phase 1c.
                    const detail = ev.detail && ev.detail.length > 0
                        ? ev.detail
                        : '사령관 전사';
                    toast.warning('기함 격침 — ' + detail + ', 30틱 후 지휘권이 승계됩니다.', {
                        id: 'succ-' + ev.sourceUnitId,
                        duration: 6000,
                    });
                    continue;
                }
                if (ev.type === 'SUCCESSION_COMPLETED') {
                    // targetUnitId = old commander fleetId (removed from active list)
                    // sourceUnitId = new commander fleetId (future FlagshipFlash target)
                    if (ev.targetUnitId) {
                        newSuccessions = newSuccessions.filter(
                            (id) => id !== ev.targetUnitId,
                        );
                    }
                    // D-16 Korean copy from UI-SPEC Section D Phase 3b.
                    const detail = ev.detail && ev.detail.length > 0
                        ? ev.detail
                        : '새 사령관';
                    toast.success('지휘 인수 — ' + detail + '가 지휘권을 인수했습니다.', {
                        duration: 4000,
                    });
                    continue;
                }
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
                // Phase 14 FE-04 (14-15) — succession feedback bookkeeping.
                activeSuccessionFleetIds: newSuccessions,
                activeFlagshipDestroyedFleetIds: newFlashes,
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
