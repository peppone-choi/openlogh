import { describe, it, expect } from 'vitest';
import { shouldShowMyLocationRing, calcPulseOpacity } from './CityModel';

describe('CityModel — MyLocationRing logic', () => {
  describe('shouldShowMyLocationRing', () => {
    it('returns true when isMyCity is true', () => {
      expect(shouldShowMyLocationRing(true)).toBe(true);
    });

    it('returns false when isMyCity is false', () => {
      expect(shouldShowMyLocationRing(false)).toBe(false);
    });
  });

  describe('calcPulseOpacity', () => {
    it('returns value between 0.3 and 0.9', () => {
      // 여러 시간값에서 범위 확인
      for (let t = 0; t < 10; t += 0.1) {
        const opacity = calcPulseOpacity(t);
        expect(opacity).toBeGreaterThanOrEqual(0.3);
        expect(opacity).toBeLessThanOrEqual(0.9);
      }
    });

    it('produces different values at different times (animation)', () => {
      const v1 = calcPulseOpacity(0);
      const v2 = calcPulseOpacity(0.5);
      expect(v1).not.toBe(v2);
    });
  });
});
