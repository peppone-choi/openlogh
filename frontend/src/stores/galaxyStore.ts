import { create } from 'zustand';
import type { StarSystem, StarRoute, FleetPosition } from '@/types/galaxy';
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
    /** Fleet positions keyed by mapStarId */
    fleetPositions: Record<number, FleetPosition[]>;
    /** Currently selected fleet id for movement range highlight */
    selectedFleetId: number | null;
}

interface GalaxyActions {
    fetchGalaxyMap: (sessionId: number) => Promise<void>;
    fetchFleetPositions: (sessionId: number) => Promise<void>;
    selectSystem: (mapStarId: number | null) => void;
    hoverSystem: (mapStarId: number | null) => void;
    selectFleet: (fleetId: number | null) => void;
    getSystem: (mapStarId: number) => StarSystem | undefined;
    getConnectedSystems: (mapStarId: number) => StarSystem[];
    getFortresses: () => StarSystem[];
    /** Get all mapStarIds reachable within `hops` hops from a given star */
    getReachableStars: (mapStarId: number, hops: number) => Set<number>;
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
        fleetPositions: {},
        selectedFleetId: null,

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
                // Fetch fleet positions after map loads
                await get().fetchFleetPositions(sessionId);
            } catch (err) {
                const message =
                    err instanceof Error ? err.message : 'Failed to load galaxy map';
                set({ error: message, isLoading: false });
            }
        },

        fetchFleetPositions: async (sessionId: number) => {
            try {
                const res = await fetch(`/api/${sessionId}/fleets/positions`);
                if (!res.ok) return; // Non-blocking: map works without fleet data
                const data = (await res.json()) as Array<{
                    mapStarId: number;
                    fleets: FleetPosition[];
                }>;
                const fleetPositions: Record<number, FleetPosition[]> = {};
                for (const entry of data) {
                    fleetPositions[entry.mapStarId] = entry.fleets;
                }
                set({ fleetPositions });
            } catch {
                // Non-blocking: fleet positions are optional overlay data
            }
        },

        selectSystem: (mapStarId: number | null) => {
            set({ selectedSystemId: mapStarId });
        },

        hoverSystem: (mapStarId: number | null) => {
            set({ hoveredSystemId: mapStarId });
        },

        selectFleet: (fleetId: number | null) => {
            set({ selectedFleetId: fleetId });
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

        getReachableStars: (mapStarId: number, hops: number) => {
            const { systemsById } = get();
            const visited = new Set<number>();
            const queue: Array<{ id: number; depth: number }> = [
                { id: mapStarId, depth: 0 },
            ];
            while (queue.length > 0) {
                const current = queue.shift()!;
                if (visited.has(current.id)) continue;
                visited.add(current.id);
                if (current.depth >= hops) continue;
                const sys = systemsById[current.id];
                if (!sys) continue;
                for (const connId of sys.connections) {
                    if (!visited.has(connId)) {
                        queue.push({ id: connId, depth: current.depth + 1 });
                    }
                }
            }
            // Exclude the origin star itself from the reachable set
            visited.delete(mapStarId);
            return visited;
        },
    })
);
