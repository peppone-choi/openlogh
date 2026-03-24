import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';
import type { StarSystem, Faction, Officer, Diplomacy, MapData } from '@/types';
import { planetApi, factionApi, officerApi, diplomacyApi, mapApi } from '@/lib/gameApi';

interface GameStore {
    starSystems: StarSystem[];
    factions: Faction[];
    officers: Officer[];
    diplomacy: Diplomacy[];
    mapData: MapData | null;
    loading: boolean;
    isHydrated: boolean;

    // Deprecated aliases
    /** @deprecated use starSystems */ cities: StarSystem[];
    /** @deprecated use factions */ nations: Faction[];
    /** @deprecated use officers */ generals: Officer[];

    loadAll: (worldId: number) => Promise<void>;
    loadMap: (mapName: string) => Promise<void>;
    clear: () => void;
}

let _inflightLoadAll: { worldId: number; promise: Promise<void> } | null = null;

export const useGameStore = create<GameStore>()(
    persist(
        (set, get) => ({
            starSystems: [],
            factions: [],
            officers: [],
            diplomacy: [],
            mapData: null,
            loading: false,
            isHydrated: false,

            // Deprecated aliases — getters that proxy to new names
            get cities() {
                return get().starSystems;
            },
            get nations() {
                return get().factions;
            },
            get generals() {
                return get().officers;
            },

            loadAll: (worldId) => {
                if (_inflightLoadAll && _inflightLoadAll.worldId === worldId) {
                    return _inflightLoadAll.promise;
                }
                const promise = (async () => {
                    set({ loading: true });
                    const withTimeout = <T>(promise: Promise<T>, ms = 10000): Promise<T> =>
                        Promise.race([
                            promise,
                            new Promise<never>((_, reject) => setTimeout(() => reject(new Error('timeout')), ms)),
                        ]);
                    try {
                        const results = await Promise.allSettled([
                            withTimeout(planetApi.listByWorld(worldId)),
                            withTimeout(factionApi.listByWorld(worldId)),
                            withTimeout(officerApi.listByWorld(worldId)),
                            withTimeout(diplomacyApi.listByWorld(worldId)),
                        ]);
                        const patch: Partial<Pick<GameStore, 'starSystems' | 'factions' | 'officers' | 'diplomacy'>> =
                            {};
                        if (results[0].status === 'fulfilled') patch.starSystems = results[0].value.data;
                        if (results[1].status === 'fulfilled') patch.factions = results[1].value.data;
                        if (results[2].status === 'fulfilled') patch.officers = results[2].value.data;
                        if (results[3].status === 'fulfilled') patch.diplomacy = results[3].value.data;
                        set(patch);
                    } finally {
                        set({ loading: false });
                        _inflightLoadAll = null;
                    }
                })();
                _inflightLoadAll = { worldId, promise };
                return promise;
            },

            loadMap: async (mapName) => {
                const { data } = await mapApi.get(mapName);
                set({ mapData: data });
            },

            clear: () => set({ starSystems: [], factions: [], officers: [], diplomacy: [], mapData: null }),
        }),
        {
            name: 'openlogh:game',
            storage: typeof window !== 'undefined' ? createJSONStorage(() => sessionStorage) : undefined,
            partialize: (state) => ({
                starSystems: state.starSystems,
                factions: state.factions,
                officers: state.officers,
            }),
            onRehydrateStorage: () => (state) => {
                if (state) state.isHydrated = true;
            },
        }
    )
);

// Trigger hydration flag after store initializes
if (typeof window !== 'undefined') {
    const unsub = useGameStore.persist.onFinishHydration((state) => {
        useGameStore.setState({ isHydrated: true });
        void state;
        unsub();
    });
    if (useGameStore.persist.hasHydrated()) {
        useGameStore.setState({ isHydrated: true });
    }
}
