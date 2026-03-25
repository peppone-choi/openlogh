import { describe, it, expect } from 'vitest';
import { buildAdjacencyMap, findPath, interpolateAlongPath } from './map3d-pathfinding';
import type { CityConst } from '@/types';

const makeCities = (): CityConst[] => [
    { id: 1, name: 'A', level: 1, region: 0, x: 0, y: 0, connections: [2, 3] },
    { id: 2, name: 'B', level: 1, region: 0, x: 100, y: 0, connections: [1, 4] },
    { id: 3, name: 'C', level: 1, region: 0, x: 0, y: 100, connections: [1, 4] },
    { id: 4, name: 'D', level: 1, region: 0, x: 100, y: 100, connections: [2, 3] },
];

describe('buildAdjacencyMap', () => {
    it('creates correct adjacency for each city', () => {
        const cities = makeCities();
        const adj = buildAdjacencyMap(cities);

        expect(adj.get(1)).toEqual([2, 3]);
        expect(adj.get(2)).toEqual([1, 4]);
        expect(adj.get(3)).toEqual([1, 4]);
        expect(adj.get(4)).toEqual([2, 3]);
    });

    it('returns empty adjacency list for city with no connections', () => {
        const cities: CityConst[] = [{ id: 5, name: 'E', level: 1, region: 0, x: 0, y: 0, connections: [] }];
        const adj = buildAdjacencyMap(cities);
        expect(adj.get(5)).toEqual([]);
    });

    it('has an entry for every city', () => {
        const cities = makeCities();
        const adj = buildAdjacencyMap(cities);
        expect(adj.size).toBe(4);
    });
});

describe('findPath', () => {
    it('returns [from, to] for adjacent cities', () => {
        const adj = buildAdjacencyMap(makeCities());
        expect(findPath(adj, 1, 2)).toEqual([1, 2]);
        expect(findPath(adj, 1, 3)).toEqual([1, 3]);
    });

    it('finds shortest path across 2 hops', () => {
        const adj = buildAdjacencyMap(makeCities());
        // 1 → 2 → 4  or  1 → 3 → 4  (both length 3, BFS picks whichever comes first)
        const path = findPath(adj, 1, 4);
        expect(path[0]).toBe(1);
        expect(path[path.length - 1]).toBe(4);
        expect(path.length).toBe(3);
    });

    it('returns [from] when from === to', () => {
        const adj = buildAdjacencyMap(makeCities());
        expect(findPath(adj, 2, 2)).toEqual([2]);
    });

    it('returns [from, to] fallback when no path exists', () => {
        // Island city with no connections
        const cities: CityConst[] = [
            { id: 1, name: 'A', level: 1, region: 0, x: 0, y: 0, connections: [] },
            { id: 2, name: 'B', level: 1, region: 0, x: 100, y: 0, connections: [] },
        ];
        const adj = buildAdjacencyMap(cities);
        expect(findPath(adj, 1, 2)).toEqual([1, 2]);
    });
});

describe('interpolateAlongPath', () => {
    const positions = new Map<number, [number, number, number]>([
        [1, [0, 0, 0]],
        [2, [10, 0, 0]],
        [3, [20, 0, 0]],
    ]);

    it('returns from position at progress 0', () => {
        const result = interpolateAlongPath([1, 2, 3], positions, 0);
        expect(result).toEqual([0, 0, 0]);
    });

    it('returns to position at progress 1', () => {
        const result = interpolateAlongPath([1, 2, 3], positions, 1);
        expect(result).toEqual([20, 0, 0]);
    });

    it('returns midpoint correctly at progress 0.5', () => {
        const result = interpolateAlongPath([1, 2, 3], positions, 0.5);
        expect(result[0]).toBeCloseTo(10);
        expect(result[1]).toBeCloseTo(0);
        expect(result[2]).toBeCloseTo(0);
    });

    it('returns midpoint of first segment at progress 0.25', () => {
        const result = interpolateAlongPath([1, 2, 3], positions, 0.25);
        expect(result[0]).toBeCloseTo(5);
    });

    it('returns midpoint of second segment at progress 0.75', () => {
        const result = interpolateAlongPath([1, 2, 3], positions, 0.75);
        expect(result[0]).toBeCloseTo(15);
    });

    it('handles single-city path', () => {
        const result = interpolateAlongPath([1], positions, 0.5);
        expect(result).toEqual([0, 0, 0]);
    });

    it('handles empty path', () => {
        const result = interpolateAlongPath([], positions, 0.5);
        expect(result).toEqual([0, 0, 0]);
    });

    it('clamps progress outside [0,1]', () => {
        const atStart = interpolateAlongPath([1, 2], positions, -0.5);
        expect(atStart[0]).toBeCloseTo(0);

        const atEnd = interpolateAlongPath([1, 2], positions, 1.5);
        expect(atEnd[0]).toBeCloseTo(10);
    });
});
