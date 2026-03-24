import { describe, it, expect } from 'vitest';
import { convexHull2D, expandHull } from './NationTerritory';

describe('convexHull2D', () => {
    it('returns same points for 0-1 points', () => {
        expect(convexHull2D([])).toEqual([]);
        expect(convexHull2D([[1, 2]])).toEqual([[1, 2]]);
    });

    it('computes hull for a square', () => {
        const points: [number, number][] = [
            [0, 0],
            [10, 0],
            [10, 10],
            [0, 10],
            [5, 5], // interior point
        ];
        const hull = convexHull2D(points);
        expect(hull.length).toBe(4);
        // Interior point should not be in the hull
        expect(hull.find((p) => p[0] === 5 && p[1] === 5)).toBeUndefined();
    });

    it('computes hull for collinear points', () => {
        const points: [number, number][] = [
            [0, 0],
            [5, 0],
            [10, 0],
        ];
        const hull = convexHull2D(points);
        expect(hull.length).toBe(2); // just endpoints
    });

    it('computes hull for triangle', () => {
        const points: [number, number][] = [
            [0, 0],
            [10, 0],
            [5, 10],
        ];
        const hull = convexHull2D(points);
        expect(hull.length).toBe(3);
    });
});

describe('expandHull', () => {
    it('expands hull outward', () => {
        const hull: [number, number][] = [
            [0, 0],
            [10, 0],
            [10, 10],
            [0, 10],
        ];
        const expanded = expandHull(hull, 2);
        expect(expanded.length).toBe(4);
        // Each point should be further from centroid (5,5) than original
        const centroid = [5, 5];
        for (let i = 0; i < hull.length; i++) {
            const origDist = Math.sqrt((hull[i][0] - centroid[0]) ** 2 + (hull[i][1] - centroid[1]) ** 2);
            const expandDist = Math.sqrt((expanded[i][0] - centroid[0]) ** 2 + (expanded[i][1] - centroid[1]) ** 2);
            expect(expandDist).toBeGreaterThan(origDist);
        }
    });

    it('returns empty for empty hull', () => {
        expect(expandHull([], 2)).toEqual([]);
    });
});
