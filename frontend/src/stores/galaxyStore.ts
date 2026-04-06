import { create } from 'zustand';
import type { StarSystem, StarRoute } from '@/types/galaxy';
import { fetchGalaxyMap } from '@/lib/api/galaxy';
import { STAR_SYSTEM_PLANETS } from '@/data/star-system-planets';

interface GalaxyState {
    systems: StarSystem[];
    routes: StarRoute[];
    factionTerritories: Record<number, number[]>;
    selectedSystemId: number | null;
    hoveredSystemId: number | null;
    isLoading: boolean;
    error: string | null;
    systemsById: Record<number, StarSystem>;
}

interface GalaxyActions {
    fetchGalaxyMap: (sessionId: number) => Promise<void>;
    selectSystem: (mapStarId: number | null) => void;
    hoverSystem: (mapStarId: number | null) => void;
    getSystem: (mapStarId: number) => StarSystem | undefined;
    getConnectedSystems: (mapStarId: number) => StarSystem[];
    getFortresses: () => StarSystem[];
}

export const useGalaxyStore = create<GalaxyState & GalaxyActions>()(
    (set, get) => ({
        systems: [],
        routes: [],
        factionTerritories: {},
        selectedSystemId: null,
        hoveredSystemId: null,
        isLoading: false,
        error: null,
        systemsById: {},

        fetchGalaxyMap: async (sessionId: number) => {
            set({ isLoading: true, error: null });
            try {
                const data = await fetchGalaxyMap(sessionId);
                // Merge static planet names if API doesn't provide them
                for (const sys of data.systems) {
                    if (!sys.planets || sys.planets.length === 0) {
                        sys.planets = STAR_SYSTEM_PLANETS[sys.mapStarId] ?? [];
                    }
                    if (sys.planetCount === 0 && sys.planets.length > 0) {
                        sys.planetCount = sys.planets.length;
                    }
                }
                const systemsById: Record<number, StarSystem> = {};
                for (const sys of data.systems) {
                    systemsById[sys.mapStarId] = sys;
                }
                set({
                    systems: data.systems,
                    routes: data.routes,
                    factionTerritories: data.factionTerritories,
                    systemsById,
                    isLoading: false,
                });
            } catch (err) {
                const message =
                    err instanceof Error ? err.message : 'Failed to load galaxy map';
                set({ error: message, isLoading: false });
            }
        },

        selectSystem: (mapStarId: number | null) => {
            set({ selectedSystemId: mapStarId });
        },

        hoverSystem: (mapStarId: number | null) => {
            set({ hoveredSystemId: mapStarId });
        },

        getSystem: (mapStarId: number) => {
            return get().systemsById[mapStarId];
        },

        getConnectedSystems: (mapStarId: number) => {
            const { systemsById } = get();
            const system = systemsById[mapStarId];
            if (!system) return [];
            return system.connections
                .map((connId) => systemsById[connId])
                .filter(Boolean);
        },

        getFortresses: () => {
            return get().systems.filter((s) => s.fortressType !== 'NONE');
        },
    })
);
