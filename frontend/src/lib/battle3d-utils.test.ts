import { describe, it, expect } from 'vitest';
import { easeInOutQuad, lerp, lerpVec3, getVisibleUnitCount, isWebGLSupported } from './battle3d-utils';

describe('easeInOutQuad', () => {
    it('0 → 0', () => {
        expect(easeInOutQuad(0)).toBe(0);
    });

    it('0.5 → 0.5', () => {
        expect(easeInOutQuad(0.5)).toBeCloseTo(0.5);
    });

    it('1 → 1', () => {
        expect(easeInOutQuad(1)).toBe(1);
    });
});

describe('lerp', () => {
    it('lerp(0, 10, 0.5) → 5', () => {
        expect(lerp(0, 10, 0.5)).toBe(5);
    });

    it('lerp(0, 10, 0) → 0', () => {
        expect(lerp(0, 10, 0)).toBe(0);
    });

    it('lerp(0, 10, 1) → 10', () => {
        expect(lerp(0, 10, 1)).toBe(10);
    });
});

describe('lerpVec3', () => {
    it('interpolates each component', () => {
        const a: [number, number, number] = [0, 0, 0];
        const b: [number, number, number] = [10, 20, 30];
        const result = lerpVec3(a, b, 0.5);
        expect(result[0]).toBeCloseTo(5);
        expect(result[1]).toBeCloseTo(10);
        expect(result[2]).toBeCloseTo(15);
    });
});

describe('getVisibleUnitCount', () => {
    it('crew 50 → 3', () => {
        expect(getVisibleUnitCount(50)).toBe(3);
    });

    it('crew 300 → 6', () => {
        expect(getVisibleUnitCount(300)).toBe(6);
    });

    it('crew 2000 → 20', () => {
        expect(getVisibleUnitCount(2000)).toBe(20);
    });
});

describe('isWebGLSupported', () => {
    it('returns a boolean', () => {
        const result = isWebGLSupported();
        expect(typeof result).toBe('boolean');
    });
});
