import { create } from 'zustand';
import type { City, Nation, General, Diplomacy, MapData } from '@/types';
import { cityApi, nationApi, generalApi, diplomacyApi, mapApi } from '@/lib/gameApi';

interface GameStore {
    cities: City[];
    nations: Nation[];
    generals: General[];
    diplomacy: Diplomacy[];
    mapData: MapData | null;
    loading: boolean;

    loadAll: (worldId: number) => Promise<void>;
    loadMap: (mapName: string) => Promise<void>;
    clear: () => void;
}

export const useGameStore = create<GameStore>((set) => ({
    cities: [],
    nations: [],
    generals: [],
    diplomacy: [],
    mapData: null,
    loading: false,

    loadAll: async (worldId) => {
        set({ loading: true });
        const withTimeout = <T>(promise: Promise<T>, ms = 10000): Promise<T> =>
            Promise.race([promise, new Promise<never>((_, reject) => setTimeout(() => reject(new Error('timeout')), ms))]);
        try {
            const results = await Promise.allSettled([
                withTimeout(cityApi.listByWorld(worldId)),
                withTimeout(nationApi.listByWorld(worldId)),
                withTimeout(generalApi.listByWorld(worldId)),
                withTimeout(diplomacyApi.listByWorld(worldId)),
            ]);
            const patch: Partial<GameStore> = {};
            if (results[0].status === 'fulfilled') patch.cities = results[0].value.data;
            if (results[1].status === 'fulfilled') patch.nations = results[1].value.data;
            if (results[2].status === 'fulfilled') patch.generals = results[2].value.data;
            if (results[3].status === 'fulfilled') patch.diplomacy = results[3].value.data;
            set(patch);
        } finally {
            set({ loading: false });
        }
    },

    loadMap: async (mapName) => {
        const { data } = await mapApi.get(mapName);
        set({ mapData: data });
    },

    clear: () => set({ cities: [], nations: [], generals: [], diplomacy: [], mapData: null }),
}));
