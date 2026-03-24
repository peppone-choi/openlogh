import { describe, it, expect } from 'vitest';
import { getHighQuality, getLowQuality, detectQuality } from './map3d-mobile';
import type { Map3DQuality } from './map3d-mobile';

function isValidQuality(q: Map3DQuality): boolean {
    return (
        Array.isArray(q.dpr) &&
        q.dpr.length === 2 &&
        typeof q.shadows === 'boolean' &&
        typeof q.terrainSegments === 'number' &&
        typeof q.maxVisibleLabels === 'number' &&
        typeof q.showNationTerritory === 'boolean' &&
        typeof q.showWarEffects === 'boolean' &&
        typeof q.showTroopMarkers === 'boolean' &&
        typeof q.antialias === 'boolean' &&
        typeof q.pixelRatio === 'number'
    );
}

describe('map3d-mobile', () => {
    describe('getHighQuality', () => {
        it('returns expected high quality values', () => {
            const q = getHighQuality();
            expect(q.dpr).toEqual([1, 2]);
            expect(q.shadows).toBe(true);
            expect(q.terrainSegments).toBe(50);
            expect(q.maxVisibleLabels).toBe(Infinity);
            expect(q.showNationTerritory).toBe(true);
            expect(q.showWarEffects).toBe(true);
            expect(q.showTroopMarkers).toBe(true);
            expect(q.antialias).toBe(true);
            expect(q.pixelRatio).toBe(2);
        });
    });

    describe('getLowQuality', () => {
        it('returns expected low quality values', () => {
            const q = getLowQuality();
            expect(q.dpr).toEqual([1, 1]);
            expect(q.shadows).toBe(false);
            expect(q.terrainSegments).toBe(25);
            expect(q.maxVisibleLabels).toBe(20);
            expect(q.showNationTerritory).toBe(false);
            expect(q.showWarEffects).toBe(false);
            expect(q.showTroopMarkers).toBe(false);
            expect(q.antialias).toBe(false);
            expect(q.pixelRatio).toBe(1);
        });
    });

    describe('detectQuality', () => {
        it('returns a valid Map3DQuality object', () => {
            const q = detectQuality();
            expect(isValidQuality(q)).toBe(true);
        });
    });
});
