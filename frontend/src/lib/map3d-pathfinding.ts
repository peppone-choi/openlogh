import type { CityConst } from '@/types';

/** Build adjacency map from CityConst[] */
export function buildAdjacencyMap(cities: CityConst[]): Map<number, number[]> {
    const map = new Map<number, number[]>();
    for (const city of cities) {
        map.set(city.id, [...city.connections]);
    }
    return map;
}

/** BFS shortest path between two cities. Returns array of city IDs [from, ..., to].
 *  Returns [fromId, toId] if no path found (fallback straight line). */
export function findPath(adjacency: Map<number, number[]>, fromId: number, toId: number): number[] {
    if (fromId === toId) return [fromId];

    const visited = new Set<number>();
    const queue: number[][] = [[fromId]];
    visited.add(fromId);

    while (queue.length > 0) {
        const path = queue.shift()!;
        const current = path[path.length - 1]!;

        const neighbors = adjacency.get(current) ?? [];
        for (const neighbor of neighbors) {
            if (neighbor === toId) {
                return [...path, neighbor];
            }
            if (!visited.has(neighbor)) {
                visited.add(neighbor);
                queue.push([...path, neighbor]);
            }
        }
    }

    // No path found — return direct [from, to] as fallback
    return [fromId, toId];
}

/** Given a path of city IDs and their 3D positions, compute position at progress (0-1).
 *  Each segment gets equal weight. */
export function interpolateAlongPath(
    path: number[],
    positions: Map<number, [number, number, number]>,
    progress: number
): [number, number, number] {
    if (path.length === 0) return [0, 0, 0];
    if (path.length === 1) {
        return positions.get(path[0]!) ?? [0, 0, 0];
    }

    const clamped = Math.max(0, Math.min(1, progress));
    const segmentCount = path.length - 1;
    const segmentProgress = clamped * segmentCount;
    const segmentIndex = Math.min(Math.floor(segmentProgress), segmentCount - 1);
    const t = segmentProgress - segmentIndex;

    const fromPos = positions.get(path[segmentIndex]!) ?? [0, 0, 0];
    const toPos = positions.get(path[segmentIndex + 1]!) ?? [0, 0, 0];

    return [
        fromPos[0] + (toPos[0] - fromPos[0]) * t,
        fromPos[1] + (toPos[1] - fromPos[1]) * t,
        fromPos[2] + (toPos[2] - fromPos[2]) * t,
    ];
}
