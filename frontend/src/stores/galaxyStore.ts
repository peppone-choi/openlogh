import { create } from 'zustand';
import type { StarSystem, StarRoute, FleetPosition } from '@/types/galaxy';
import type { OperationEventDto } from '@/types/tactical';
import { fetchGalaxyMap, fetchPublicCachedGalaxy } from '@/lib/api/galaxy';
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
    /** Player's current star system (mapStarId) — renders white hexagon overlay */
    currentSystemId: number | null;
    /** mapStarIds with an active battle — renders red hexagon overlay */
    battleSystemIds: Set<number>;
    /**
     * Phase 14 D-30/D-31 — active OperationPlan events consumed from the
     * `/topic/world/{sessionId}/operations` WebSocket channel (14-04).
     *
     * Populated by `handleOperationEvent` routed from a WS subscription in
     * the galaxy map host (see GalaxyMap.tsx). Used by the F1 operations
     * overlay + right-edge side panel to render per-operation badges and
     * focus targets without polling.
     */
    activeOperations: OperationEventDto[];
    /** Monotonic fetch token used to drop stale responses across overlapping requests */
    requestToken: number;
}

interface GalaxyActions {
    fetchGalaxyMap: (sessionId: number) => Promise<void>;
    /** Unauthenticated public fetch — used by lobby/login previews. */
    fetchPublicGalaxyMap: (worldId?: number) => Promise<void>;
    fetchFleetPositions: (sessionId: number) => Promise<void>;
    selectSystem: (mapStarId: number | null) => void;
    hoverSystem: (mapStarId: number | null) => void;
    selectFleet: (fleetId: number | null) => void;
    setCurrentSystem: (mapStarId: number | null) => void;
    setBattleSystems: (mapStarIds: Iterable<number>) => void;
    getSystem: (mapStarId: number) => StarSystem | undefined;
    getConnectedSystems: (mapStarId: number) => StarSystem[];
    getFortresses: () => StarSystem[];
    /** Get all mapStarIds reachable within `hops` hops from a given star */
    getReachableStars: (mapStarId: number, hops: number) => Set<number>;
    // ── Phase 14 D-30/D-31 — OperationPlan WebSocket reducer ──
    /** Upsert (replace-by-operationId or append) an operation entry. */
    upsertOperation: (evt: OperationEventDto) => void;
    /** Remove an operation by operationId (COMPLETED / CANCELLED). */
    removeOperation: (operationId: number) => void;
    /**
     * Router for `/topic/world/{sessionId}/operations` events:
     *   - OPERATION_PLANNED / OPERATION_STARTED → upsert
     *   - OPERATION_COMPLETED / OPERATION_CANCELLED → remove
     */
    handleOperationEvent: (evt: OperationEventDto) => void;
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
        currentSystemId: null,
        battleSystemIds: new Set<number>(),
        activeOperations: [],
        requestToken: 0,

        fetchGalaxyMap: async (sessionId: number) => {
            const token = get().requestToken + 1;
            set({ isLoading: true, error: null, requestToken: token });
            try {
                const data = await fetchGalaxyMap(sessionId);
                // Drop stale responses: another fetch started after we began
                if (get().requestToken !== token) return;

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
                if (get().requestToken !== token) return;
                const message =
                    err instanceof Error ? err.message : 'Failed to load galaxy map';
                set({ error: message, isLoading: false });
            }
        },

        fetchPublicGalaxyMap: async (worldId?: number) => {
            const token = get().requestToken + 1;
            set({ isLoading: true, error: null, requestToken: token });
            try {
                const data = await fetchPublicCachedGalaxy(worldId);
                if (get().requestToken !== token) return;

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
                    fleetPositions: {}, // No fleet data in public preview
                    isLoading: false,
                });
            } catch (err) {
                if (get().requestToken !== token) return;
                const message =
                    err instanceof Error ? err.message : 'Failed to load public galaxy map';
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

        setCurrentSystem: (mapStarId: number | null) => {
            set({ currentSystemId: mapStarId });
        },

        setBattleSystems: (mapStarIds: Iterable<number>) => {
            set({ battleSystemIds: new Set(mapStarIds) });
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

        upsertOperation: (evt: OperationEventDto) => {
            set((state) => {
                const idx = state.activeOperations.findIndex(
                    (op) => op.operationId === evt.operationId,
                );
                if (idx >= 0) {
                    const next = [...state.activeOperations];
                    next[idx] = evt;
                    return { activeOperations: next };
                }
                return { activeOperations: [...state.activeOperations, evt] };
            });
        },

        removeOperation: (operationId: number) => {
            set((state) => ({
                activeOperations: state.activeOperations.filter(
                    (op) => op.operationId !== operationId,
                ),
            }));
        },

        handleOperationEvent: (evt: OperationEventDto) => {
            // D-31 — PLANNED / STARTED upsert; COMPLETED / CANCELLED remove.
            if (
                evt.type === 'OPERATION_COMPLETED' ||
                evt.type === 'OPERATION_CANCELLED'
            ) {
                get().removeOperation(evt.operationId);
                return;
            }
            get().upsertOperation(evt);
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
