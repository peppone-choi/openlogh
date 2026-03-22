import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';
import type { WorldState } from '@/types';
import { worldApi } from '@/lib/gameApi';
import { useGameStore } from './gameStore';

interface WorldStore {
    worlds: WorldState[];
    currentWorld: WorldState | null;
    loading: boolean;
    isHydrated: boolean;
    fetchWorlds: () => Promise<void>;
    setCurrentWorld: (world: WorldState) => void;
    createWorld: (payload: {
        scenarioCode: string;
        name?: string;
        tickSeconds?: number;
        commitSha?: string;
        gameVersion?: string;
    }) => Promise<WorldState>;
    deleteWorld: (id: number) => Promise<void>;
    resetWorld: (id: number, scenarioCode?: string) => Promise<WorldState>;
    activateWorld: (
        id: number,
        payload?: {
            commitSha?: string;
            gameVersion?: string;
            jarPath?: string;
            port?: number;
            javaCommand?: string;
        }
    ) => Promise<void>;
    deactivateWorld: (id: number) => Promise<void>;
    fetchWorld: (id: number) => Promise<void>;
    updateWorldTime: (year: number, month: number) => void;
}

export const useWorldStore = create<WorldStore>()(
    persist(
        (set) => ({
            worlds: [],
            currentWorld: null,
            loading: false,
            isHydrated: false,

            fetchWorlds: async () => {
                set({ loading: true });
                try {
                    const { data } = await worldApi.list();
                    set({ worlds: data });
                } finally {
                    set({ loading: false });
                }
            },

            setCurrentWorld: (world) => set({ currentWorld: world }),

            createWorld: async (payload) => {
                const { data } = await worldApi.create(payload);
                set((state) => ({ worlds: [...state.worlds, data], currentWorld: data }));
                return data;
            },

            deleteWorld: async (id) => {
                await worldApi.delete(id);
                set((state) => ({
                    worlds: state.worlds.filter((w) => w.id !== id),
                    currentWorld: state.currentWorld?.id === id ? null : state.currentWorld,
                }));
            },

            resetWorld: async (id, scenarioCode) => {
                const { data } = await worldApi.reset(id, scenarioCode);
                useGameStore.getState().clear();
                set((state) => ({
                    worlds: state.worlds
                        .map((w) => (w.id === id ? data : w))
                        .concat(state.worlds.some((w) => w.id === id) ? [] : [data]),
                    currentWorld: state.currentWorld?.id === id ? data : state.currentWorld,
                }));
                return data;
            },

            activateWorld: async (id, payload) => {
                await worldApi.activate(id, payload);
                const { data } = await worldApi.get(id);
                set((state) => ({
                    worlds: state.worlds.map((w) => (w.id === id ? data : w)),
                    currentWorld: state.currentWorld?.id === id ? data : state.currentWorld,
                }));
            },

            deactivateWorld: async (id) => {
                await worldApi.deactivate(id);
                const { data } = await worldApi.get(id);
                set((state) => ({
                    worlds: state.worlds.map((w) => (w.id === id ? data : w)),
                    currentWorld: state.currentWorld?.id === id ? data : state.currentWorld,
                }));
            },

            fetchWorld: async (id) => {
                const { data } = await worldApi.get(id);
                set((state) => ({
                    worlds: state.worlds.map((w) => (w.id === id ? data : w)),
                    currentWorld: state.currentWorld?.id === id ? data : state.currentWorld,
                }));
            },

            updateWorldTime: (year, month) =>
                set((state) => {
                    if (!state.currentWorld) return state;
                    if (state.currentWorld.currentYear === year && state.currentWorld.currentMonth === month)
                        return state;
                    return { currentWorld: { ...state.currentWorld, currentYear: year, currentMonth: month } };
                }),
        }),
        {
            name: 'world-store',
            storage: createJSONStorage(() => sessionStorage),
            partialize: (state) => ({ currentWorld: state.currentWorld }),
            onRehydrateStorage: () => () => {
                useWorldStore.setState({ isHydrated: true });
            },
        }
    )
);
