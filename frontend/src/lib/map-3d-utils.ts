// Design Ref: §4 — 2D↔3D 좌표 변환 시스템
import { Vector3 } from 'three';

const MAP_W = 700;
const MAP_H = 500;

/** 2D 맵 좌표 → 3D 월드 좌표 */
export function toWorld3d(x2d: number, y2d: number, height = 0): Vector3 {
  return new Vector3(x2d - MAP_W / 2, height, y2d - MAP_H / 2);
}

/** 3D 월드 좌표 → 2D 맵 좌표 */
export function toMap2d(world: Vector3): { x: number; y: number } {
  return {
    x: world.x + MAP_W / 2,
    y: world.z + MAP_H / 2,
  };
}

/** 높이맵에서 바이리니어 보간으로 높이 샘플링 */
export function sampleHeight(
  heightMap: Float32Array,
  x2d: number,
  y2d: number,
  segments: number,
): number {
  const sx = (x2d / MAP_W) * segments;
  const sz = (y2d / MAP_H) * segments;

  const ix = Math.floor(sx);
  const iz = Math.floor(sz);
  const fx = sx - ix;
  const fz = sz - iz;

  const idx = (row: number, col: number) =>
    Math.min(row, segments) * (segments + 1) + Math.min(col, segments);

  const h00 = heightMap[idx(iz, ix)] ?? 0;
  const h10 = heightMap[idx(iz, ix + 1)] ?? 0;
  const h01 = heightMap[idx(iz + 1, ix)] ?? 0;
  const h11 = heightMap[idx(iz + 1, ix + 1)] ?? 0;

  return h00 * (1 - fx) * (1 - fz) + h10 * fx * (1 - fz) + h01 * (1 - fx) * fz + h11 * fx * fz;
}

/** WebGL 지원 여부 감지 */
export function isWebGLSupported(): boolean {
  if (typeof window === 'undefined') return false;
  try {
    const canvas = document.createElement('canvas');
    return !!(canvas.getContext('webgl2') || canvas.getContext('webgl'));
  } catch {
    return false;
  }
}

/** 모바일 기기 감지 */
export function isMobileDevice(): boolean {
  if (typeof navigator === 'undefined') return false;
  return /Android|iPhone|iPad|iPod/i.test(navigator.userAgent);
}
