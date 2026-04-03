import { describe, it, expect } from 'vitest';
import { getLocationConfig, getModelUrl } from './CastleLoader';

describe('CastleLoader', () => {
  describe('getLocationConfig', () => {
    it('returns spot type for levels 1-4', () => {
      for (let level = 1; level <= 4; level++) {
        expect(getLocationConfig(level).type).toBe('spot');
      }
    });

    it('returns city type for levels 5-8', () => {
      for (let level = 5; level <= 8; level++) {
        expect(getLocationConfig(level).type).toBe('city');
      }
    });

    it('maps level 1 to naval', () => {
      const config = getLocationConfig(1);
      expect(config.modelFile).toBe('spot_naval.glb');
    });

    it('maps level 4 to tribal', () => {
      const config = getLocationConfig(4);
      expect(config.modelFile).toBe('spot_tribal.glb');
    });

    it('maps level 8 to grand', () => {
      const config = getLocationConfig(8);
      expect(config.modelFile).toBe('city_grand.glb');
    });

    it('scales up with level', () => {
      const s5 = getLocationConfig(5).targetScale;
      const s8 = getLocationConfig(8).targetScale;
      expect(s8).toBeGreaterThan(s5);
    });

    it('base radius increases with level', () => {
      const r1 = getLocationConfig(1).baseRadius;
      const r8 = getLocationConfig(8).baseRadius;
      expect(r8).toBeGreaterThan(r1);
    });

    it('returns fallback for unknown level', () => {
      const config = getLocationConfig(0);
      expect(config.type).toBe('spot');
    });

    it('each level has unique model file', () => {
      const files = new Set<string>();
      // Lv1-2 share naval, Lv5-6 share city etc — but each level maps to a file
      for (let i = 1; i <= 8; i++) {
        files.add(getLocationConfig(i).modelFile);
      }
      // 8 unique files (each level has its own)
      expect(files.size).toBe(8);
    });
  });

  describe('getModelUrl', () => {
    it('returns CDN URL with correct path', () => {
      const url = getModelUrl('spot_naval.glb');
      expect(url).toContain('/game/3d/spot_naval.glb');
    });
  });
});
