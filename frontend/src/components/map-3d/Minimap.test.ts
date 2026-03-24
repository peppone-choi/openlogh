import { describe, it, expect } from 'vitest';
import { mapToMinimap, minimapToMap, worldToMinimap, minimapToWorld } from './Minimap';
import { MAP_WIDTH, MAP_HEIGHT } from '@/lib/map-constants';
import { WORLD_WIDTH, WORLD_DEPTH } from '@/lib/map3d-utils';

const SIZE = 180;
const MINIMAP_H = SIZE * (MAP_HEIGHT / MAP_WIDTH);

describe('mapToMinimap', () => {
    it('maps top-left corner to (0, 0)', () => {
        const { px, py } = mapToMinimap(0, 0, SIZE);
        expect(px).toBeCloseTo(0);
        expect(py).toBeCloseTo(0);
    });

    it('maps bottom-right corner to (size, minimapH)', () => {
        const { px, py } = mapToMinimap(MAP_WIDTH, MAP_HEIGHT, SIZE);
        expect(px).toBeCloseTo(SIZE);
        expect(py).toBeCloseTo(MINIMAP_H);
    });

    it('maps center to (size/2, minimapH/2)', () => {
        const { px, py } = mapToMinimap(MAP_WIDTH / 2, MAP_HEIGHT / 2, SIZE);
        expect(px).toBeCloseTo(SIZE / 2);
        expect(py).toBeCloseTo(MINIMAP_H / 2);
    });
});

describe('minimapToMap', () => {
    it('maps (0, 0) back to map origin', () => {
        const { mapX, mapY } = minimapToMap(0, 0, SIZE);
        expect(mapX).toBeCloseTo(0);
        expect(mapY).toBeCloseTo(0);
    });

    it('maps (size, minimapH) back to bottom-right corner', () => {
        const { mapX, mapY } = minimapToMap(SIZE, MINIMAP_H, SIZE);
        expect(mapX).toBeCloseTo(MAP_WIDTH);
        expect(mapY).toBeCloseTo(MAP_HEIGHT);
    });

    it('round-trips through mapToMinimap', () => {
        const origX = 350;
        const origY = 250;
        const { px, py } = mapToMinimap(origX, origY, SIZE);
        const { mapX, mapY } = minimapToMap(px, py, SIZE);
        expect(mapX).toBeCloseTo(origX);
        expect(mapY).toBeCloseTo(origY);
    });
});

describe('worldToMinimap', () => {
    it('maps world origin to minimap center', () => {
        const { px, py } = worldToMinimap(0, 0, SIZE);
        expect(px).toBeCloseTo(SIZE / 2);
        expect(py).toBeCloseTo(MINIMAP_H / 2);
    });

    it('maps world top-left corner to minimap (0, 0)', () => {
        const { px, py } = worldToMinimap(-WORLD_WIDTH / 2, -WORLD_DEPTH / 2, SIZE);
        expect(px).toBeCloseTo(0);
        expect(py).toBeCloseTo(0);
    });

    it('maps world bottom-right corner to minimap (size, minimapH)', () => {
        const { px, py } = worldToMinimap(WORLD_WIDTH / 2, WORLD_DEPTH / 2, SIZE);
        expect(px).toBeCloseTo(SIZE);
        expect(py).toBeCloseTo(MINIMAP_H);
    });
});

describe('minimapToWorld', () => {
    it('maps minimap center to world origin', () => {
        const { worldX, worldZ } = minimapToWorld(SIZE / 2, MINIMAP_H / 2, SIZE);
        expect(worldX).toBeCloseTo(0);
        expect(worldZ).toBeCloseTo(0);
    });

    it('round-trips through worldToMinimap', () => {
        const origX = 15;
        const origZ = -10;
        const { px, py } = worldToMinimap(origX, origZ, SIZE);
        const { worldX, worldZ } = minimapToWorld(px, py, SIZE);
        expect(worldX).toBeCloseTo(origX);
        expect(worldZ).toBeCloseTo(origZ);
    });
});
