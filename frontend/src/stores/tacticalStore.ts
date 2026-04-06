import { create } from 'zustand';
import type {
    TacticalBattle,
    TacticalUnit,
    EnergyAllocation,
    Formation,
    BattleTickBroadcast,
    BattleTickEvent,
} from '@/types/tactical';
import { tacticalApi } from '@/lib/tacticalApi';
import { DEFAULT_ENERGY } from '@/types/tactical';

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

        set((state) => ({
            units: data.units,
            recentEvents: [...data.events, ...state.recentEvents].slice(0, 50),
            currentBattle: state.currentBattle
                ? {
                      ...state.currentBattle,
                      tickCount: data.tickCount,
                      phase: data.phase,
                      result: data.result ?? state.currentBattle.result,
                      units: data.units,
                  }
                : null,
            // Update local energy/formation from server state (reconciliation)
            myEnergy: myUnit?.energy ?? state.myEnergy,
            myFormation: myUnit?.formation ?? state.myFormation,
        }));
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
        });
    },
}));
