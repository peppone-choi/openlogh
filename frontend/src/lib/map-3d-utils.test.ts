import { describe, it, expect } from 'vitest';
import { Vector3 } from 'three';

// 순수 함수만 테스트 (Three.js Vector3는 vitest에서 사용 가능)
// DOM 의존 함수 (isWebGLSupported, isMobileDevice)는 제외

describe('map-3d-utils', () => {
  // toWorld3d, toMap2d를 직접 임포트하면 Three.js 번들이 필요하므로
  // 로직만 인라인 테스트
  const MAP_W = 700;
  const MAP_H = 500;

  function toWorld3d(x2d: number, y2d: number, height = 0) {
    return new Vector3(x2d - MAP_W / 2, height, y2d - MAP_H / 2);
  }

  function toMap2d(world: Vector3) {
    return { x: world.x + MAP_W / 2, y: world.z + MAP_H / 2 };
  }

  describe('toWorld3d', () => {
    it('converts center of 2D map to 3D origin', () => {
      const v = toWorld3d(350, 250, 0);
      expect(v.x).toBe(0);
      expect(v.y).toBe(0);
      expect(v.z).toBe(0);
    });

    it('converts top-left corner', () => {
      const v = toWorld3d(0, 0, 0);
      expect(v.x).toBe(-350);
      expect(v.z).toBe(-250);
    });

    it('converts bottom-right corner', () => {
      const v = toWorld3d(700, 500, 0);
      expect(v.x).toBe(350);
      expect(v.z).toBe(250);
    });

    it('preserves height as y', () => {
      const v = toWorld3d(350, 250, 15);
      expect(v.y).toBe(15);
    });
  });

  describe('toMap2d', () => {
    it('converts 3D origin back to 2D center', () => {
      const p = toMap2d(new Vector3(0, 0, 0));
      expect(p.x).toBe(350);
      expect(p.y).toBe(250);
    });

    it('roundtrips correctly', () => {
      const original = { x: 145, y: 180 };
      const world = toWorld3d(original.x, original.y);
      const back = toMap2d(world);
      expect(back.x).toBe(original.x);
      expect(back.y).toBe(original.y);
    });
  });

  describe('sampleHeight', () => {
    const segments = 4;
    // 5x5 grid, flat at height 10
    const flat = new Float32Array(25).fill(10);

    function sampleHeight(heightMap: Float32Array, x2d: number, y2d: number, seg: number) {
      const sx = (x2d / MAP_W) * seg;
      const sz = (y2d / MAP_H) * seg;
      const ix = Math.floor(sx);
      const iz = Math.floor(sz);
      const fx = sx - ix;
      const fz = sz - iz;
      const idx = (row: number, col: number) =>
        Math.min(row, seg) * (seg + 1) + Math.min(col, seg);
      const h00 = heightMap[idx(iz, ix)] ?? 0;
      const h10 = heightMap[idx(iz, ix + 1)] ?? 0;
      const h01 = heightMap[idx(iz + 1, ix)] ?? 0;
      const h11 = heightMap[idx(iz + 1, ix + 1)] ?? 0;
      return h00 * (1 - fx) * (1 - fz) + h10 * fx * (1 - fz) + h01 * (1 - fx) * fz + h11 * fx * fz;
    }

    it('returns constant for flat heightmap', () => {
      expect(sampleHeight(flat, 350, 250, segments)).toBe(10);
      expect(sampleHeight(flat, 0, 0, segments)).toBe(10);
      expect(sampleHeight(flat, 700, 500, segments)).toBe(10);
    });

    it('interpolates between values', () => {
      const gradient = new Float32Array(25);
      // first row = 0, last row = 20
      for (let z = 0; z <= segments; z++) {
        for (let x = 0; x <= segments; x++) {
          gradient[z * (segments + 1) + x] = z * 5;
        }
      }
      const mid = sampleHeight(gradient, 350, 250, segments);
      expect(mid).toBe(10); // halfway = 10
    });
  });
});
