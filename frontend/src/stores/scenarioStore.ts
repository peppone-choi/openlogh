import { create } from 'zustand';
import { scenarioApi } from '@/lib/gameApi';
import type { Scenario, ScenarioDetailResponse, SelectPoolEntry } from '@/types';

interface ScenarioState {
    scenarios: Scenario[];
    loghScenarios: Scenario[];
    selectedScenario: ScenarioDetailResponse | null;
    selectPool: SelectPoolEntry[];
    loading: boolean;
    error: string | null;

    fetchScenarios: () => Promise<void>;
    fetchLoghScenarios: () => Promise<void>;
    fetchScenarioDetail: (code: string) => Promise<void>;
    fetchSelectPool: (worldId: number) => Promise<void>;
    clearSelection: () => void;
}

export const useScenarioStore = create<ScenarioState>((set) => ({
    scenarios: [],
    loghScenarios: [],
    selectedScenario: null,
    selectPool: [],
    loading: false,
    error: null,

    fetchScenarios: async () => {
        set({ loading: true, error: null });
        try {
            const { data } = await scenarioApi.list();
            set({ scenarios: data, loading: false });
        } catch (e) {
            set({ error: (e as Error).message, loading: false });
        }
    },

    fetchLoghScenarios: async () => {
        set({ loading: true, error: null });
        try {
            const { data } = await scenarioApi.listLogh();
            set({ loghScenarios: data, loading: false });
        } catch (e) {
            set({ error: (e as Error).message, loading: false });
        }
    },

    fetchScenarioDetail: async (code: string) => {
        set({ loading: true, error: null });
        try {
            const { data } = await scenarioApi.detail(code);
            set({ selectedScenario: data, loading: false });
        } catch (e) {
            set({ error: (e as Error).message, loading: false });
        }
    },

    fetchSelectPool: async (worldId: number) => {
        try {
            const { data } = await scenarioApi.selectPool(worldId);
            set({ selectPool: data });
        } catch (e) {
            set({ error: (e as Error).message });
        }
    },

    clearSelection: () => set({ selectedScenario: null, selectPool: [] }),
}));
