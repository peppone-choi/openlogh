'use client';

import { useRef, useMemo } from 'react';
import { useFrame } from '@react-three/fiber';
import type { General, Nation, CityConst } from '@/types';
import { buildAdjacencyMap, findPath, interpolateAlongPath } from '@/lib/map3d-pathfinding';
import { TroopMarker } from './TroopMarker';

interface TroopMarkersProps {
    generals: General[];
    cityPositions: Map<number, [number, number, number]>;
    nations: Nation[];
    mapData?: { cities: CityConst[] };
    /** ID of the player's own nation — troops of other nations shown as enemies */
    myNationId?: number;
}

interface MarkerState {
    generalId: number;
    progress: number;
    /** path length used to scale speed: longer paths animate at same per-segment rate */
    segmentCount: number;
}

const SPEED_PER_SEGMENT = 0.12; // progress units per second per segment

export function TroopMarkers({ generals, cityPositions, nations, mapData, myNationId }: TroopMarkersProps) {
    // Build nation color lookup
    const nationColorMap = useMemo(() => {
        const m = new Map<number, string>();
        for (const nation of nations) {
            m.set(nation.id, nation.color);
        }
        return m;
    }, [nations]);

    // Build adjacency map once
    const adjacency = useMemo(() => {
        if (!mapData?.cities || mapData.cities.length === 0) return null;
        return buildAdjacencyMap(mapData.cities);
    }, [mapData]);

    // Filter moving generals
    const movingGenerals = useMemo(
        () => generals.filter((g) => g.destCityId != null && g.destCityId !== g.cityId),
        [generals]
    );

    // Compute BFS paths for each moving general
    const pathMap = useMemo(() => {
        const m = new Map<number, number[]>();
        for (const g of movingGenerals) {
            if (adjacency) {
                m.set(g.id, findPath(adjacency, g.cityId, g.destCityId!));
            } else {
                m.set(g.id, [g.cityId, g.destCityId!]);
            }
        }
        return m;
    }, [movingGenerals, adjacency]);

    // Per-general progress refs
    const progressRefs = useRef<Map<number, MarkerState>>(new Map());

    // Initialize or reset states when generals/paths change
    const prevPathMap = useRef<Map<number, number[]>>(new Map());
    for (const g of movingGenerals) {
        const path = pathMap.get(g.id) ?? [g.cityId, g.destCityId!];
        const prevPath = prevPathMap.current.get(g.id);
        const pathChanged = !prevPath || prevPath.length !== path.length || prevPath.some((id, i) => id !== path[i]);

        if (!progressRefs.current.has(g.id) || pathChanged) {
            progressRefs.current.set(g.id, {
                generalId: g.id,
                progress: 0,
                segmentCount: Math.max(1, path.length - 1),
            });
        }
        prevPathMap.current.set(g.id, path);
    }

    // Clean up stale states
    const movingIds = new Set(movingGenerals.map((g) => g.id));
    for (const id of progressRefs.current.keys()) {
        if (!movingIds.has(id)) {
            progressRefs.current.delete(id);
            prevPathMap.current.delete(id);
        }
    }

    useFrame((_, delta) => {
        for (const state of progressRefs.current.values()) {
            if (state.progress >= 1) continue;
            // Per-segment speed: total progress increment = SPEED_PER_SEGMENT / segmentCount
            const inc = (delta * SPEED_PER_SEGMENT * state.segmentCount) / state.segmentCount;
            state.progress = Math.min(1, state.progress + inc);
        }
    });

    return (
        <>
            {movingGenerals.map((general, idx) => {
                const path = pathMap.get(general.id) ?? [general.cityId, general.destCityId!];
                const state = progressRefs.current.get(general.id);
                const progress = state?.progress ?? 0;
                const nationColor = nationColorMap.get(general.nationId) ?? '#ffffff';
                const isEnemy = myNationId != null && general.nationId !== myNationId;

                // Interpolate position along path
                // Offset z slightly when multiple generals share same path segment
                const zOffset = (idx % 3) * 0.25 - 0.25;
                const rawPos = interpolateAlongPath(path, cityPositions, progress);
                const pos: [number, number, number] = [rawPos[0], rawPos[1] + 1, rawPos[2] + zOffset];

                // Compute direction vector from current to next segment position
                const nextProgress = Math.min(1, progress + 0.05);
                const nextRaw = interpolateAlongPath(path, cityPositions, nextProgress);
                const dir: [number, number, number] = [
                    nextRaw[0] - rawPos[0],
                    nextRaw[1] - rawPos[1],
                    nextRaw[2] - rawPos[2],
                ];

                return (
                    <TroopMarker
                        key={general.id}
                        generalName={general.name}
                        nationColor={nationColor}
                        position={pos}
                        direction={dir}
                        crew={general.crew}
                        isEnemy={isEnemy}
                    />
                );
            })}
        </>
    );
}
