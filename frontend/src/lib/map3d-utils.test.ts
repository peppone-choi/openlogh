import { describe, it, expect } from 'vitest';
import {
    colorToHeight,
    cityToWorld,
    getCityScale,
    getCityHeight,
    buildCityPositions,
    WORLD_WIDTH,
    WORLD_DEPTH,
} from './map3d-utils';

describe('colorToHeight', () => {
    it('returns negative for blue (water)', () => {
        expect(colorToHeight(0, 0, 255)).toBe(-0.2);
    });

    it('returns low value for green (plain)', () => {
        expect(colorToHeight(0, 200, 0)).toBe(0.5);
    });

    it('returns high value for brown (mountain)', () => {
        const h = colorToHeight(180, 170, 80);
        expect(h).toBeGreaterThan(1.0);
    });
});

describe('cityToWorld', () => {
    it('maps center coordinates (350, 250) to [0, 0.5, 0]', () => {
        const [x, y, z] = cityToWorld(350, 250);
        expect(x).toBeCloseTo(0);
        expect(y).toBeCloseTo(0.5);
        expect(z).toBeCloseTo(0);
    });

    it('maps (0, 0) to [-WORLD_WIDTH/2, 0.5, -WORLD_DEPTH/2]', () => {
        const [x, y, z] = cityToWorld(0, 0);
        expect(x).toBeCloseTo(-WORLD_WIDTH / 2);
        expect(y).toBeCloseTo(0.5);
        expect(z).toBeCloseTo(-WORLD_DEPTH / 2);
    });

    it('uses getHeight callback when provided', () => {
        const getHeight = () => 2.0;
        const [, y] = cityToWorld(350, 250, getHeight);
        expect(y).toBeCloseTo(2.5);
    });
});

describe('getCityScale', () => {
    it('level 1 → 0.6', () => {
        expect(getCityScale(1)).toBe(0.6);
    });

    it('level 5 → 1.0', () => {
        expect(getCityScale(5)).toBe(1.0);
    });

    it('level 8 → 1.3', () => {
        expect(getCityScale(8)).toBe(1.3);
    });
});

describe('getCityHeight', () => {
    it('level 1 → 0.8', () => {
        expect(getCityHeight(1)).toBe(0.8);
    });

    it('level 7 → 2.0', () => {
        expect(getCityHeight(7)).toBe(2.0);
    });
});

describe('buildCityPositions', () => {
    it('returns Map with correct city ids', () => {
        const cities = [
            { id: 1, x: 350, y: 250, name: 'A', level: 1, nationId: 1 },
            { id: 2, x: 0, y: 0, name: 'B', level: 2, nationId: 2 },
        ] as unknown as Parameters<typeof buildCityPositions>[0];
        const result = buildCityPositions(cities);
        expect(result.has(1)).toBe(true);
        expect(result.has(2)).toBe(true);
        expect(result.size).toBe(2);

        const [x1, , z1] = result.get(1)!;
        expect(x1).toBeCloseTo(0);
        expect(z1).toBeCloseTo(0);
    });
});
